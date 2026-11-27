package sle.editor.project.assets

import androidx.compose.runtime.*
import androidx.compose.ui.text.input.TextFieldValue
import kotlinx.serialization.json.*
import sle.editor.model.*
import sle.editor.project.ProjectDocumentKind
import java.awt.image.BufferedImage
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import javax.imageio.ImageIO

/** A source asset, independent of whichever dock/window currently displays it. */
internal class TilesetEditorAsset(
    val project: Path,
    val assets: Path,
    val relative: String,
    val id: String,
    val atlas: String,
    val atlasId: String,
    val image: BufferedImage,
    val session: TilesetEditorSession,
) {
    val inputs = mutableStateMapOf<String, TextFieldValue>()
    private var metadataIdentity: Pair<JsonObject, Set<String>>? = null
    private var gridIdentity: Triple<Int, Int, TilesetGrid?>? = null
    fun syncMetadata() {
        val parsed = session.parsed
        val grid = Triple(parsed.palette.width, parsed.palette.height, parsed.grid)
        if (gridIdentity != grid) {
            gridIdentity = grid
            inputs.remove("Tile size")
            inputs.remove("Margin")
            inputs.remove("Spacing")
        }
        val key = session.document to session.selected
        if (metadataIdentity == key) return
        metadataIdentity = key
        val tile = session.document.getValue("tiles").jsonArray.firstOrNull {
            it.jsonObject.getValue("id").jsonPrimitive.content in session.selected
        }?.jsonObject
        inputs["Name"] = TextFieldValue(tile?.get("name")?.jsonPrimitive?.content.orEmpty())
        inputs["Tags"] = TextFieldValue((tile?.get("tags") ?: JsonArray(emptyList())).toString())
        inputs["Properties"] = TextFieldValue((tile?.get("properties") ?: JsonObject(emptyMap())).toString())
    }

    fun liveRegions(): List<SpriteRegion> = readAtlasMetadata(atlasFile(), atlasId).regions

    fun atlasFile(): Path = assetPath(project, assets, atlas)

    fun slice(width: Int, height: Int, margin: List<Int>, spacing: List<Int>): Pair<List<SpriteRegion>, TilesetGrid> {
        val cells = SpriteGrid(width, height, margin[0], margin[1], spacing[0], spacing[1],
            skipTransparent = false).rectangles(image)
        val regions = appendGridRegions((liveRegions() + session.pendingRegions).distinctBy { it.id }, cells)
        val columns = ((image.width.toLong() - 2L * margin[0] + spacing[0]) / (width.toLong() + spacing[0])).toInt()
        return tilesetRegions(regions, cells) to TilesetGrid(margin[0], margin[1], spacing[0], spacing[1], columns)
    }
}

/** Retains each project's drafts across panel hiding, detaching and project switches. */
internal class TilesetEditorWorkspace {
    private val sessions = mutableStateMapOf<Path, TilesetEditorAsset>()
    private val active = mutableStateMapOf<Path, Path>()

    fun current(projectRoot: Path?): TilesetEditorAsset? = projectRoot?.toAbsolutePath()?.normalize()?.let { root ->
        active[root]?.let(sessions::get)
    }

    val dirtyAssets: List<TilesetEditorAsset> get() = sessions.values.filter { it.session.dirty }

    fun open(project: Path, assetRoot: Path, path: String, snapshot: AssetSnapshot, reload: Boolean = false): TilesetEditorAsset = synchronized(AssetIdentity) {
        val root = project.toAbsolutePath().normalize()
        val assets = assetRoot.toAbsolutePath().normalize()
        val file = assetPath(root, assets, path)
        require(Files.isRegularFile(file, NOFOLLOW_LINKS) && Files.size(file) <= MAX_TILESET_BYTES) { "Missing or oversized tileset" }
        val id = AssetIdentity.read(AssetIdentity.sidecar(file))
        check(id == snapshot.idsByPath[path]) { "The tileset changed on disk. Refresh assets before opening it." }
        val existing = sessions[file]
        if (existing != null && !reload) {
            check(id == existing.id) { "The tileset was replaced on disk. Revert to reload it." }
            readAtlasMetadata(existing.atlasFile(), existing.atlasId)
            active[root] = file
            return@synchronized existing
        }
        val session = TilesetEditorSession(file, Files.readString(file), snapshot.pathsById::get) { atlas ->
            readAtlasMetadata(assetPath(root, assets, atlas), snapshot.idsByPath.getValue(atlas)).regions
        }
        val atlas = session.parsed.atlas
        val image = requireNotNull(ImageIO.read(assetPath(root, assets, atlas).toFile())) { "Unsupported spritesheet image" }
        require(session.parsed.regions.values.all { it.fits(image.width, image.height) }) { "Tile region exceeds spritesheet bounds" }
        session.zoom = 4f
        val asset = TilesetEditorAsset(root, assets, path, id, atlas, snapshot.idsByPath.getValue(atlas), image, session)
        sessions[file] = asset
        active[root] = file
        asset
    }

    fun save(asset: TilesetEditorAsset, scales: List<Double> = emptyList()) = synchronized(AssetIdentity) {
        val file = requireOwner(asset)
        check(AssetIdentity.read(AssetIdentity.sidecar(file)) == asset.id) { "The tileset was replaced on disk. Revert to reload it." }
        val atlas = asset.atlasFile()
        readAtlasMetadata(atlas, asset.atlasId)
        val (width, height) = spriteImageSize(atlas)
        check(width == asset.image.width && height == asset.image.height) { "The spritesheet dimensions changed. Revert to reload it." }
        val parsed = asset.session.parsed
        require(parsed.regions.values.all { it.fits(width, height) }) { "Tile region exceeds spritesheet bounds" }
        val collision = parsed.source.getValue("tiles").jsonArray.associate { element ->
            val tile = element.jsonObject
            val id = tile.getValue("id").jsonPrimitive.content
            val region = parsed.regions.getValue(id)
            id to readTileCollision(tile, region.width, region.height)
        }
        validateCollision(asset, collision, scales)
        asset.session.save { pending ->
            AssetIdentity.editRegions(asset.project, asset.assets, asset.atlas) { current ->
                val byId = current.associateBy { it.id }
                require(pending.all { byId[it.id] == null || byId[it.id] == it }) { "A sprite region changed on disk. Reopen the tileset." }
                (current + pending.filter { it.id !in byId }).also(::validateRegions)
            }
        }
    }

    fun setCollisions(asset: TilesetEditorAsset, updates: Map<String, TileCollisionShape>, scales: List<Double> = emptyList()) {
        requireOwner(asset)
        validateCollision(asset, updates, scales)
        asset.session.setCollisions(updates)
    }

    fun validateCollision(asset: TilesetEditorAsset, updates: Map<String, TileCollisionShape>, scales: List<Double> = emptyList()) {
        val parsed = asset.session.parsed
        require(parsed.regions.keys.containsAll(updates.keys)) { "A selected tile was removed" }
        val resolvedScales = applicableScales(asset, scales)
        for ((id, shape) in updates) {
            require(shape.mode == TileCollisionMode.Custom || shape.pieces.isEmpty()) { "Only Custom can store polygons" }
            val region = parsed.regions.getValue(id)
            val width = if (shape.mode == TileCollisionMode.FullCell) parsed.palette.width else region.width
            val height = if (shape.mode == TileCollisionMode.FullCell) parsed.palette.height else region.height
            val pieces = if (shape.mode == TileCollisionMode.FullCell) listOf(listOf(
                TileCollisionPoint(0.0, 0.0), TileCollisionPoint(width.toDouble(), 0.0),
                TileCollisionPoint(width.toDouble(), height.toDouble()), TileCollisionPoint(0.0, height.toDouble()),
            )) else shape.pieces
            for (scale in resolvedScales) validateTileCollision(pieces, width, height, scale * TILE_PIXELS_PER_METER)
        }
    }

    fun applicableScales(asset: TilesetEditorAsset, extraScales: List<Double> = emptyList()): List<Double> {
        val snapshot = AssetScanner.scan(asset.project, asset.assets, registerMissing = false)
        val reference = JsonPrimitive("project-asset://${asset.id}")
        val diskScales = snapshot.idsByPath.keys.filter { it.endsWith(ProjectDocumentKind.Scene.suffix, ignoreCase = true) }
            .flatMap { sceneScales(assetPath(asset.project, asset.assets, it), reference) }
        return (diskScales + extraScales).distinct().ifEmpty { listOf(1.0) }
    }

    private fun requireOwner(asset: TilesetEditorAsset): Path {
        val file = assetPath(asset.project, asset.assets, asset.relative)
        check(sessions[file] === asset) { "The tileset draft was reloaded. Use the current editor." }
        return file
    }
}

private fun readAtlasMetadata(file: Path, id: String): AssetMetadata {
    check(Files.isRegularFile(file, NOFOLLOW_LINKS)) { "The spritesheet file is missing. Revert to reload it." }
    val metadata = AssetIdentity.readMetadata(AssetIdentity.sidecar(file))
    check(metadata.id.equals(id, ignoreCase = true)) { "The spritesheet was replaced on disk. Revert to reload it." }
    return metadata
}

private fun sceneScales(file: Path, reference: JsonPrimitive): List<Double> {
    val envelope = runCatching {
        if (Files.size(file) > 16 * 1024 * 1024) return emptyList()
        Json.parseToJsonElement(Files.readString(file)) as? JsonObject
    }.getOrNull() ?: return emptyList()
    if (envelope["format"] != JsonPrimitive(ProjectDocumentKind.Scene.format) || envelope["version"] != JsonPrimitive(1)) {
        return emptyList()
    }
    val document = envelope["document"] as? JsonObject ?: return emptyList()
    return (document["tileLayers"] as? JsonArray).orEmpty().mapNotNull { element ->
        val layer = element as? JsonObject ?: return@mapNotNull null
        if (layer["tileset"] != reference || layer["collisionEnabled"] == JsonPrimitive(false)) return@mapNotNull null
        val rawScale = layer["pixelsPerUnit"] ?: return@mapNotNull 1.0
        val scale = (rawScale as? JsonPrimitive)?.takeUnless { it.isString }?.doubleOrNull
        require(scale != null && scale in 0.001..1.0e6) { "Invalid tile layer scale in ${file.fileName}" }
        scale
    }
}
