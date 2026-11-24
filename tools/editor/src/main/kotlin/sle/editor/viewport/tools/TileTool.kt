package sle.editor.viewport.tools

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import sle.editor.model.*
import sle.editor.viewport.Vec2
import java.awt.Cursor

internal class TilePaintingState(private val cancel: () -> Unit) {
    var layerId by mutableStateOf<String?>(null)
        private set
    var palette by mutableStateOf<TilePalette?>(null)
        private set
    var tile by mutableStateOf<String?>(null)
        private set
    var images: Map<String, TileStampImage> = emptyMap()
    var stampTiles by mutableStateOf<List<String>>(emptyList())
        private set
    var clipboard by mutableStateOf<TilePattern?>(null)
        private set
    private var copiedStamp by mutableStateOf<TilePattern?>(null)
    var anchor: TileCell? = null
        private set
    var mode by mutableStateOf(TilePaintMode.Brush)
        private set

    private var document: Pair<java.nio.file.Path?, String>? = null

    fun configure(layer: String?, next: TilePalette?, scene: SceneWorkspaceState? = null) {
        val identity = scene?.let { it.file to it.key }
        if (layerId == layer && palette == next && document == identity) return
        document = identity
        cancel()
        anchor = null
        images = emptyMap()
        layerId = layer
        val sameTileset = palette?.reference == next?.reference
        palette = next
        if (!sameTileset || next?.tiles?.none { it.id == tile } != false)
            tile = next?.tiles?.firstOrNull()?.id
        if (!sameTileset || copiedStamp?.tiles?.values?.all { id -> next?.tiles?.any { it.id == id } == true } == false) copiedStamp = null
        stampTiles = if (sameTileset) stampTiles.filter { id -> next?.tiles?.any { it.id == id } == true } else emptyList()
    }

    fun selectTile(id: String) {
        if ((tile == id && stampTiles.isEmpty() && copiedStamp == null) || palette?.tiles?.none { it.id == id } != false) return
        cancel()
        stampTiles = emptyList()
        copiedStamp = null
        tile = id
    }

    fun toggleStampTile(id: String) {
        if (palette?.tiles?.none { it.id == id } != false) return
        if (id !in stampTiles && stampTiles.size >= 8) return
        cancel()
        copiedStamp = null
        stampTiles = if (id in stampTiles) stampTiles - id else stampTiles + id
    }

    fun useCopiedStamp() {
        val source = clipboard ?: return
        val target = palette ?: return
        require(target.reference == source.tileset) { "Clipboard uses a different tileset" }
        val ids = target.tiles.mapTo(hashSetOf()) { it.id }
        require(source.tiles.values.all { it in ids }) { "Clipboard contains a missing tile" }
        cancel()
        stampTiles = emptyList()
        copiedStamp = source
        selectMode(TilePaintMode.Brush)
    }

    fun brushTileIds() = (copiedStamp?.tiles?.values?.distinct()?.take(8) ?: stampTiles) + listOfNotNull(tile)

    fun brushPattern(): TilePattern? {
        copiedStamp?.let { return it }
        val ids = stampTiles.ifEmpty { listOfNotNull(tile) }
        val reference = palette?.reference ?: return null
        return if (ids.isEmpty()) null else TilePattern(reference,
            ids.mapIndexed { x, id -> TileCell(x, 0) to id }.toMap())
    }

    fun copy(scene: SceneWorkspaceState) {
        val layer = layerId?.let(scene::tileLayer) ?: return
        val bounds = scene.tileSelection(layer.id) ?: return
        val pattern = layer.copy(bounds)
        require(pattern.tiles.isNotEmpty()) { "Select at least one painted tile to copy" }
        clipboard = pattern
    }

    fun paste(scene: SceneWorkspaceState) {
        val id = layerId ?: return
        val source = clipboard ?: return
        val target = palette ?: return
        val cell = anchor ?: scene.tileSelection(id)?.minimum
        requireNotNull(cell) { "Point at the destination cell before pasting" }
        cancel()
        scene.pasteTiles(id, target, source, cell)
    }

    fun pointAt(cell: TileCell?) { anchor = cell }

    fun selectMode(next: TilePaintMode) {
        if (mode == next) return
        cancel()
        mode = next
    }
}

internal class TileTool : ViewportTool {
    override val id = ViewportToolId.Tiles

    private data class Gesture(
        val scene: SceneWorkspaceState,
        val revision: Long,
        val layer: String,
        val grid: TileGrid,
        val mode: TilePaintMode,
        val start: TileCell,
        var end: TileCell = start,
        val stroke: TileStroke? = null,
        val pattern: TilePattern? = null,
        val moving: TileBounds? = null,
        val filled: Map<TileCell, String> = emptyMap(),
    )

    private var pending: Gesture? = null
    private var hover: Vec2? = null

    private fun layer(host: ToolHost): TileLayerDocument? {
        val state = host.state.tilePainting
        return state.layerId?.let { host.scene?.tileLayer(it) }?.takeIf { it.tileset == state.palette?.reference }
    }

    fun clearHover() { hover = null }

    override fun cursor(host: ToolHost, point: Vec2): Int {
        hover = point
        val state = host.state.tilePainting
        val camera = host.camera
        val palette = state.palette
        state.pointAt(if (camera != null && palette != null) layer(host)?.grid(palette)?.cell(camera.world(point)) else null)
        return Cursor.CROSSHAIR_CURSOR
    }

    override fun hint(ctx: ToolHintContext) = "Tiles · Choose a layer and paint mode"

    override fun press(host: ToolHost, pointer: ToolPointer): ToolPress {
        hover = pointer.point
        val scene = host.scene ?: return ToolPress.None
        val camera = host.camera ?: return ToolPress.None
        val state = host.state.tilePainting
        val layer = layer(host)?.takeIf { it.visible } ?: return ToolPress.None
        val palette = state.palette ?: return ToolPress.None
        val grid = layer.grid(palette)
        val position = grid.fractional(camera.world(pointer.point))
        val cell = tileCellAt(position) ?: return ToolPress.None
        state.pointAt(cell)
        if (state.mode == TilePaintMode.Eyedropper) {
            layer.cells[cell]?.let(state::selectTile)
            return ToolPress.None
        }
        val mode = state.mode
        if (layer.locked && mode != TilePaintMode.Select) return ToolPress.None
        if (mode != TilePaintMode.Select && mode != TilePaintMode.Erase && state.tile == null) return ToolPress.None
        val stroke = when (mode) {
            TilePaintMode.Brush, TilePaintMode.Erase, TilePaintMode.Rectangle -> TileStroke(mode, position, state.tile)
            else -> null
        }
        val moving = scene.tileSelection(layer.id)?.takeIf { mode == TilePaintMode.Select && !layer.locked && cell in it }
        val filled = if (mode == TilePaintMode.Fill) {
            try {
                val bounds = requireNotNull(fillBounds(scene, layer, cell)) {
                    "Select a rectangle before filling empty space"
                }
                tileFill(layer.cells, bounds, cell, checkNotNull(state.tile))
            } catch (error: IllegalArgumentException) {
                scene.error = error.message
                return ToolPress.None
            }
        } else emptyMap()
        pending = Gesture(scene, scene.history.revision, layer.id, grid, mode, cell,
            stroke = stroke, pattern = state.brushPattern().takeIf { mode == TilePaintMode.Brush }, moving = moving, filled = filled)
        scene.cancelTileGesture = host::cancel
        return ToolPress.None
    }

    private fun current(host: ToolHost) = pending?.takeIf {
        host.scene === it.scene && it.revision == it.scene.history.revision
    }

    override fun drag(host: ToolHost, pointer: ToolPointer) {
        hover = pointer.point
        val gesture = current(host) ?: return
        val camera = host.camera ?: return
        val position = gesture.grid.fractional(camera.world(pointer.point))
        gesture.end = tileCellAt(position) ?: return
        host.state.tilePainting.pointAt(gesture.end)
        gesture.stroke?.extend(position)
    }

    override fun release(host: ToolHost, pointer: ToolPointer) {
        drag(host, pointer)
        val gesture = current(host)
        clearPending()
        if (gesture == null) return
        val layer = gesture.scene.tileLayer(gesture.layer) ?: return
        runCatching {
            when (gesture.mode) {
                TilePaintMode.Select -> {
                    val moved = gesture.moving
                    if (moved == null) {
                        val bounds = TileBounds.between(gesture.start, gesture.end)
                        require(bounds.editableSize) { "Select at most 65,536 cells" }
                        gesture.scene.selectTiles(layer.id, bounds)
                    } else {
                        val destination = requireNotNull(moveAnchor(gesture)) { "Selection exceeds the tile grid" }
                        gesture.scene.moveTiles(layer.id, moved, destination)
                    }
                }
                TilePaintMode.Fill -> gesture.scene.paintTiles(layer.id, gesture.filled, "Fill Tiles")
                else -> {
                    val label = when (gesture.mode) {
                        TilePaintMode.Erase -> "Erase Tiles"
                        TilePaintMode.Rectangle -> "Paint Tile Rectangle"
                        else -> "Paint Tiles"
                    }
                    gesture.scene.paintTiles(layer.id, strokeCells(gesture), label)
                }
            }
        }.onFailure { gesture.scene.error = it.message ?: "Could not edit tile layer" }
    }

    private fun fillBounds(scene: SceneWorkspaceState, layer: TileLayerDocument, start: TileCell?) =
        scene.tileSelection(layer.id) ?: if (start != null && start in layer.cells) layer.occupiedBounds else null

    private fun moveAnchor(gesture: Gesture) = gesture.moving?.let {
        offsetCell(it.minimum, gesture.end.x.toLong() - gesture.start.x, gesture.end.y.toLong() - gesture.start.y)
    }

    private fun strokeCells(gesture: Gesture): Map<TileCell, String?> {
        val cells = gesture.stroke?.preview.orEmpty()
        val pattern = gesture.pattern ?: return cells
        if (pattern.width == 1L && pattern.height == 1L) return cells.mapValues { pattern.tiles.values.single() }
        val anchors = cells.keys.mapTo(linkedSetOf()) { cell ->
            val x = Math.floorDiv(cell.x.toLong() - gesture.start.x, pattern.width) * pattern.width
            val y = Math.floorDiv(cell.y.toLong() - gesture.start.y, pattern.height) * pattern.height
            requireNotNull(offsetCell(gesture.start, x, y)) { "Stamp exceeds the tile grid" }
        }
        require(anchors.size.toLong() * pattern.tiles.size <= tileEditLimit) { "Stamp stroke exceeds 65,536 cells" }
        return buildMap { anchors.forEach { putAll(pattern.placed(it)) } }
    }

    private fun clearPending() {
        pending?.scene?.cancelTileGesture = null
        pending = null
    }

    override fun cancel(host: ToolHost) {
        clearPending()
        clearHover()
    }

    // Only a pointer release can commit tile cells.
    override fun finish(host: ToolHost) = cancel(host)

    override fun gestureHint(host: ToolHost) = current(host)?.let {
        "${it.mode.label} · Esc to cancel"
    }

    override fun draw(host: ToolHost, overlay: ToolOverlay) {
        val camera = host.camera ?: return
        val layer = layer(host)?.takeIf { it.visible } ?: return
        val state = host.state.tilePainting
        val palette = state.palette ?: return
        val grid = layer.grid(palette)
        val min = grid.cell(camera.origin) ?: return
        val max = grid.cell(camera.origin + camera.size) ?: return
        val lines = buildList {
            if (grid.size.x / camera.size.x * camera.width >= 8) {
                for (x in min.x..max.x) {
                    val screenX = camera.screen(grid.lowerLeft(TileCell(x, min.y))).x
                    add(Vec2(screenX, 0.0)); add(Vec2(screenX, camera.height.toDouble()))
                }
            }
            if (grid.size.y / camera.size.y * camera.height >= 8) {
                for (y in min.y..max.y) {
                    val screenY = camera.screen(grid.lowerLeft(TileCell(min.x, y))).y
                    add(Vec2(0.0, screenY)); add(Vec2(camera.width.toDouble(), screenY))
                }
            }
        }
        val scene = host.scene ?: return
        val gesture = current(host)
        val hoverCell = hover
            ?.takeIf { it.x in 0.0..<camera.width.toDouble() && it.y in 0.0..<camera.height.toDouble() }
            ?.let { grid.cell(camera.world(it)) }
        fun outline(bounds: TileBounds) {
            overlay.marquee(camera.screen(grid.lowerLeft(bounds.minimum)),
                camera.screen(grid.lowerLeft(bounds.maximum) + grid.size))
        }
        val selection = scene.tileSelection(layer.id)
        if (selection != null) outline(selection)
        if (state.mode == TilePaintMode.Fill && selection == null) fillBounds(scene, layer, hoverCell)?.let(::outline)
        if (gesture?.mode == TilePaintMode.Select) {
            val destination = if (gesture.moving == null) TileBounds.between(gesture.start, gesture.end)
                else moveAnchor(gesture)?.let(gesture.moving::at)
            destination?.let(::outline)
        }

        val preview: Map<TileCell, String?> = when {
            layer.locked || state.mode == TilePaintMode.Select -> emptyMap()
            gesture?.mode == TilePaintMode.Fill -> gesture.filled
            gesture != null -> runCatching { strokeCells(gesture) }.getOrDefault(emptyMap())
            hoverCell == null -> emptyMap()
            state.mode == TilePaintMode.Brush ->
                runCatching { state.brushPattern()?.placed(hoverCell).orEmpty() }.getOrDefault(emptyMap())
            else -> mapOf(hoverCell to state.tile)
        }
        val visiblePreview = preview.filterKeys { it.x in min.x..max.x && it.y in min.y..max.y }
        if (state.mode == TilePaintMode.Brush || state.mode == TilePaintMode.Rectangle) {
            for ((tile, cells) in visiblePreview.entries.groupBy { it.value }) {
                val image = state.images[tile] ?: continue
                val size = Vec2(grid.size.x * image.width / palette.width, grid.size.y * image.height / palette.height)
                overlay.tileGhost(image, cells.map {
                    val corner = grid.lowerLeft(it.key)
                    camera.screen(corner) to camera.screen(corner + size)
                })
            }
        }
        overlay.tiles(lines, visiblePreview.keys.map {
            val corner = grid.lowerLeft(it)
            camera.screen(corner) to camera.screen(corner + grid.size)
        }, state.mode == TilePaintMode.Erase)
    }
}
