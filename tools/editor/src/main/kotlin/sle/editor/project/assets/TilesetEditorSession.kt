package sle.editor.project.assets

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.serialization.json.*
import sle.editor.model.MAX_TILESET_BYTES
import sle.editor.model.TilesetDocument
import sle.editor.model.TilesetGrid
import sle.editor.model.parseTileset
import sle.editor.model.tilesetTile
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path

/** One editable tileset and its history. Regions introduced by reslicing are staged until Save. */
internal class TilesetEditorSession(
    file: Path,
    source: String,
    private val atlasPath: (String) -> String?,
    private val atlasRegions: (String) -> List<SpriteRegion>,
) {
    private data class Snapshot(val parsed: TilesetDocument, val stagedRegions: List<SpriteRegion> = emptyList())

    private val file = file.toAbsolutePath().normalize()
    private val reference = file.fileName.toString()
    private var diskSource = source
    private var current by mutableStateOf(Snapshot(parseTileset(source, reference, atlasPath, atlasRegions)))
    private var saved by mutableStateOf(current.parsed.source)
    private val past = mutableStateListOf<Snapshot>()
    private val future = mutableStateListOf<Snapshot>()

    val document: JsonObject get() = parsed.source
    val parsed: TilesetDocument get() = current.parsed
    val pendingRegions: List<SpriteRegion> get() = current.stagedRegions
    var selected by mutableStateOf<Set<String>>(emptySet())
        private set
    var zoom by mutableFloatStateOf(1f)
    val dirty: Boolean get() = document != saved
    val canUndo: Boolean get() = past.isNotEmpty()
    val canRedo: Boolean get() = future.isNotEmpty()

    fun select(ids: Set<String>) {
        selected = ids.intersect(parsed.regions.keys)
    }

    fun selectRectangle(left: Int, top: Int, right: Int, bottom: Int, additive: Boolean = false) {
        val x0 = minOf(left, right)
        val y0 = minOf(top, bottom)
        val x1 = maxOf(left, right)
        val y1 = maxOf(top, bottom)
        val ids = if (x0 == x1 || y0 == y1) emptySet() else parsed.regions.filterValues { region ->
            region.x < x1 && x0 < region.x.toLong() + region.width &&
                region.y < y1 && y0 < region.y.toLong() + region.height
        }.keys
        select(if (additive) selected + ids else ids)
    }

    fun updateTiles(ids: Set<String>, name: String? = null, tags: List<String>? = null, properties: JsonObject? = null) {
        requireSelection(ids)
        if (ids.isEmpty()) return
        val updated = rawTiles().map { tile ->
            if (tile.tileId !in ids) tile else buildJsonObject {
                tile.forEach { (key, value) -> put(key, value) }
                name?.let { put("name", it) }
                tags?.let { put("tags", JsonArray(it.map(::JsonPrimitive))) }
                properties?.let { put("properties", it) }
            }
        }
        var next = document.withTiles(updated)
        if (parsed.grid == null && (tags != null || properties != null)) {
            next = JsonObject(next + mapOf("version" to JsonPrimitive(2), "grid" to inferLegacyGrid(parsed).encode()))
        }
        stage(next)
    }

    fun setCollision(ids: Set<String>, shape: TileCollisionShape) = setCollisions(ids.associateWith { shape })

    fun setCollisions(updates: Map<String, TileCollisionShape>) {
        requireSelection(updates.keys)
        val updated = rawTiles().map { tile ->
            val shape = updates[tile.tileId]
            if (shape != null && shape != readTileCollision(tile)) JsonObject(tile + ("collision" to shape.json()))
            else tile
        }
        stage(document.withTiles(updated))
    }

    fun removeTiles(ids: Set<String>) {
        requireSelection(ids)
        stage(document.withTiles(rawTiles().filterNot { it.tileId in ids }))
    }

    fun addTiles(regions: List<SpriteRegion>) {
        validateRegions(regions)
        val represented = parsed.regions.values.mapTo(mutableSetOf()) { it.id }
        val added = regions.sortedWith(regionOrder).filter { represented.add(it.id) }
        stage(document.withTiles(rawTiles() + added.map { tilesetTile(it) }), added)
    }

    fun reslice(regions: List<SpriteRegion>, width: Int, height: Int, grid: TilesetGrid) {
        require(width > 0 && height > 0) { "Tile dimensions must be positive" }
        validateRegions(regions)
        val previousGrid = parsed.grid ?: inferLegacyGrid(parsed).takeIf { inferred ->
            val cells = parsed.regions.values.map { inferred.cell(it, parsed.palette.width, parsed.palette.height) }
            cells.none { it == null } && cells.toSet().size == cells.size
        }
        val previousCells = mutableMapOf<Pair<Long, Long>, JsonObject>()
        val previousOrigins = mutableMapOf<Pair<Int, Int>, JsonObject>()
        for (tile in rawTiles()) {
            val region = parsed.regions.getValue(tile.tileId)
            val cell = previousGrid?.cell(region, parsed.palette.width, parsed.palette.height)
            if (cell == null) previousOrigins.putIfAbsent(region.x to region.y, tile)
            else previousCells.putIfAbsent(cell, tile)
        }

        val cells = mutableSetOf<Pair<Long, Long>>()
        val tiles = regions.sortedWith(regionOrder).map { region ->
            val cell = requireNotNull(grid.cell(region, width, height)) { "Region ${region.name} is outside the grid" }
            require(cells.add(cell)) { "Grid cell has more than one region" }
            val previous = previousCells.remove(cell) ?: previousOrigins.remove(region.x to region.y)
            if (previous == null) tilesetTile(region) else JsonObject(previous + ("region" to JsonPrimitive(region.id)))
        }
        val next = JsonObject(document.withTiles(tiles) + mapOf(
            "version" to JsonPrimitive(2),
            "tileWidth" to JsonPrimitive(width),
            "tileHeight" to JsonPrimitive(height),
            "grid" to grid.encode(),
        ))
        stage(next, regions)
    }

    fun undo() = restore(past, future)
    fun redo() = restore(future, past)

    /** The callback appends regions to the atlas sidecar under the same lock as the conflict check. */
    fun save(commitRegions: (List<SpriteRegion>) -> Unit = {}) = synchronized(AssetIdentity) {
        if (!dirty) return@synchronized
        checkDiskSource()
        val draft = current
        val source = draft.parsed.source
        val text = source.toString() + "\n"
        val bytes = text.encodeToByteArray()
        require(bytes.size <= MAX_TILESET_BYTES) { "Tileset exceeds 4 MiB" }

        val live = atlasRegions(draft.parsed.atlas)
        val liveIds = live.mapTo(mutableSetOf()) { it.id }
        val pending = draft.stagedRegions.filter { it.id !in liveIds }
        val combined = live + pending
        validateRegions(combined)
        verifyRegions(draft.parsed, parseTileset(source, reference, atlasPath) { combined })
        if (pending.isNotEmpty()) commitRegions(pending)
        val persisted = parseTileset(source, reference, atlasPath, atlasRegions)
        verifyRegions(draft.parsed, persisted)
        check(current == draft) { "The tileset draft changed during Save." }
        checkDiskSource()
        atomicAssetWrite(file, bytes, replace = true)
        diskSource = text
        saved = persisted.source
        current = Snapshot(persisted)
    }

    fun revert() = synchronized(AssetIdentity) {
        val source = readSource()
        val next = parseTileset(source, reference, atlasPath, atlasRegions)
        diskSource = source
        saved = next.source
        current = Snapshot(next)
        past.clear()
        future.clear()
        select(selected)
    }

    private fun stage(next: JsonObject, supplied: List<SpriteRegion> = emptyList()) {
        if (next == document) return
        require(next.toString().encodeToByteArray().size < MAX_TILESET_BYTES) { "Tileset exceeds 4 MiB" }
        val live = if (supplied.isEmpty()) emptyMap() else atlasRegions(parsed.atlas).associateBy { it.id }
        val previous = parsed.regions.values.associateBy { it.id }
        for (region in supplied) {
            require(live[region.id]?.sameRectangle(region) != false && previous[region.id]?.sameRectangle(region) != false) {
                "Region ${region.name} changed. Revert before editing the grid."
            }
        }
        val draft = parseTileset(next, reference, atlasPath) { atlas ->
            check(atlas == parsed.atlas) { "The spritesheet moved. Reopen the tileset before editing." }
            previous.values.toList() + supplied
        }
        val used = draft.regions.values.mapTo(mutableSetOf()) { it.id }
        val staged = (pendingRegions + supplied.filterNot { it.id in live }).distinctBy { it.id }.filter { it.id in used }
        if (supplied.isNotEmpty()) validateRegions(live.values + staged.filterNot { it.id in live })
        past.add(current)
        future.clear()
        current = Snapshot(draft, staged)
        select(selected)
    }

    private fun restore(from: MutableList<Snapshot>, to: MutableList<Snapshot>) {
        val next = from.removeLastOrNull() ?: return
        to.add(current)
        current = next
        select(selected)
    }

    private fun verifyRegions(expected: TilesetDocument, candidate: TilesetDocument) {
        check(candidate.atlas == expected.atlas && candidate.regions.all { (id, region) ->
            expected.regions.getValue(id).sameRectangle(region)
        }) { "The spritesheet regions changed on disk. Revert before saving." }
    }

    private fun checkDiskSource() {
        check(readSource() == diskSource) { "The tileset changed on disk. Revert before saving." }
    }

    private fun readSource(): String {
        check(Files.isRegularFile(file, NOFOLLOW_LINKS)) { "The tileset is no longer a regular file." }
        require(Files.size(file) <= MAX_TILESET_BYTES) { "Tileset exceeds 4 MiB" }
        return Files.readString(file)
    }

    private fun requireSelection(ids: Set<String>) {
        require(parsed.regions.keys.containsAll(ids)) { "A selected tile was removed" }
    }

    private fun rawTiles() = document.getValue("tiles").jsonArray.map { it.jsonObject }
}

private val JsonObject.tileId get() = getValue("id").jsonPrimitive.content
private val regionOrder = compareBy<SpriteRegion>({ it.y }, { it.x })
private fun JsonObject.withTiles(tiles: List<JsonObject>) = JsonObject(this + ("tiles" to JsonArray(tiles)))

private fun TilesetGrid.cell(region: SpriteRegion, width: Int, height: Int): Pair<Long, Long>? {
    if (region.width != width || region.height != height) return null
    val dx = region.x.toLong() - marginX
    val dy = region.y.toLong() - marginY
    val stepX = width.toLong() + spacingX
    val stepY = height.toLong() + spacingY
    if (dx < 0 || dy < 0 || dx % stepX != 0L || dy % stepY != 0L || dx / stepX >= columns) return null
    return dx / stepX to dy / stepY
}

private fun inferLegacyGrid(document: TilesetDocument): TilesetGrid {
    val regions = document.regions.values
    fun axis(offsets: List<Int>, cellSize: Int): Triple<Int, Int, Int> {
        val margin = offsets.minOrNull() ?: 0
        val step = offsets.fold(0) { result, offset -> gcd(result, offset - margin) }.coerceAtLeast(cellSize)
        val count = 1L + ((offsets.maxOrNull() ?: margin) - margin) / step
        return Triple(margin, step - cellSize, count.coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
    }
    val (marginX, spacingX, columns) = axis(regions.map { it.x }, document.palette.width)
    val (marginY, spacingY) = axis(regions.map { it.y }, document.palette.height)
    return TilesetGrid(marginX, marginY, spacingX, spacingY, columns)
}

private tailrec fun gcd(left: Int, right: Int): Int = if (right == 0) left else gcd(right, left % right)
