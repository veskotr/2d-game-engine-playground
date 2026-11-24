package sle.editor.model

import kotlinx.serialization.json.*
import sle.editor.viewport.Vec2
import kotlin.math.abs
import kotlin.math.floor

internal data class TileCell(val x: Int, val y: Int)
internal data class TileSwatch(val id: String, val name: String)
internal data class TilePalette(val reference: String, val width: Int, val height: Int, val tiles: List<TileSwatch>)
internal class TileStampImage(
    val width: Int, val height: Int, val argb: IntArray, val imageWidth: Int, val imageHeight: Int,
)

internal enum class TilePaintMode(val label: String, val help: String) {
    Brush("Brush", "Paint with the selected tile or stamp"),
    Erase("Erase", "Drag to remove tiles"),
    Rectangle("Rectangle", "Drag to paint a rectangle with the selected tile"),
    Eyedropper("Eyedropper", "Pick a tile from the active layer"),
    Fill("Fill", "Replace connected matching cells inside the selection or layer bounds"),
    Select("Select", "Drag a rectangle, or drag inside the selection to move its tiles")
}

internal data class TileBounds(val minimum: TileCell, val maximum: TileCell) {
    val width get() = maximum.x.toLong() - minimum.x + 1
    val height get() = maximum.y.toLong() - minimum.y + 1
    val editableSize get() = width in 1..tileEditLimit && height in 1..(tileEditLimit / width)

    operator fun contains(cell: TileCell) =
        cell.x in minimum.x..maximum.x && cell.y in minimum.y..maximum.y

    fun at(anchor: TileCell): TileBounds? =
        offsetCell(anchor, width - 1, height - 1)?.let { TileBounds(anchor, it) }

    companion object {
        fun between(a: TileCell, b: TileCell) = TileBounds(
            TileCell(minOf(a.x, b.x), minOf(a.y, b.y)),
            TileCell(maxOf(a.x, b.x), maxOf(a.y, b.y)),
        )

        fun enclosing(cells: Set<TileCell>): TileBounds? = if (cells.isEmpty()) null else TileBounds(
            TileCell(cells.minOf { it.x }, cells.minOf { it.y }),
            TileCell(cells.maxOf { it.x }, cells.maxOf { it.y }),
        )
    }
}

internal const val tileEditLimit = 65_536L

internal fun offsetCell(cell: TileCell, dx: Long, dy: Long): TileCell? {
    val x = cell.x + dx
    val y = cell.y + dy
    return if (x in Int.MIN_VALUE..Int.MAX_VALUE && y in Int.MIN_VALUE..Int.MAX_VALUE)
        TileCell(x.toInt(), y.toInt()) else null
}

internal data class TilePattern(
    val tileset: String,
    val tiles: Map<TileCell, String>,
    val width: Long = (tiles.keys.maxOfOrNull { it.x } ?: 0).toLong() + 1,
    val height: Long = (tiles.keys.maxOfOrNull { it.y } ?: 0).toLong() + 1,
) {
    fun placed(anchor: TileCell): Map<TileCell, String> = tiles.entries.associate { (offset, tile) ->
        requireNotNull(offsetCell(anchor, offset.x.toLong(), offset.y.toLong())) { "Stamp exceeds the tile grid" } to tile
    }
}

internal fun tileFill(cells: Map<TileCell, String>, bounds: TileBounds, start: TileCell, tile: String): Map<TileCell, String> {
    if (start !in bounds || cells[start] == tile) return emptyMap()
    val target = cells[start]
    val filled = linkedMapOf(start to tile)
    val frontier = ArrayDeque(listOf(start))
    val neighbors = listOf(1L to 0L, -1L to 0L, 0L to 1L, 0L to -1L)
    while (frontier.isNotEmpty()) {
        val cell = frontier.removeFirst()
        for ((dx, dy) in neighbors) {
            val next = offsetCell(cell, dx, dy) ?: continue
            if (next !in bounds || next in filled || cells[next] != target) continue
            require(filled.size < tileEditLimit) { "Fill exceeds 65,536 cells. Select a smaller rectangle." }
            filled[next] = tile
            frontier.addLast(next)
        }
    }
    return filled
}

internal data class TileGrid(val origin: Vec2, val size: Vec2) {
    fun fractional(world: Vec2) = Vec2((world.x - origin.x) / size.x, (world.y - origin.y) / size.y)
    fun cell(world: Vec2) = tileCellAt(fractional(world))

    fun lowerLeft(cell: TileCell) = origin + Vec2(cell.x * size.x, cell.y * size.y)
}

internal fun tileCellAt(position: Vec2): TileCell? {
    val x = floor(position.x)
    val y = floor(position.y)
    return if (x in Int.MIN_VALUE.toDouble()..Int.MAX_VALUE.toDouble() &&
        y in Int.MIN_VALUE.toDouble()..Int.MAX_VALUE.toDouble()) TileCell(x.toInt(), y.toInt()) else null
}

private fun cellOrder(cell: JsonElement) =
    cell.jsonObject.getValue("y").jsonPrimitive.int to cell.jsonObject.getValue("x").jsonPrimitive.int

internal fun orderedTileLayers(layers: JsonArray?) = layers?.let {
    JsonArray(it.map { element ->
        val layer = element.jsonObject
        val cells = layer.getValue("cells").jsonArray.sortedWith(compareBy({ cellOrder(it).first }, { cellOrder(it).second }))
        JsonObject(layer + ("cells" to JsonArray(cells)))
    }.sortedBy { it.getValue("drawOrder").jsonPrimitive.int })
}

internal class TileLayerDocument(val source: JsonObject) {
    val id = source.getValue("id").jsonPrimitive.content
    val name = source.getValue("name").jsonPrimitive.content
    val tileset = source.getValue("tileset").jsonPrimitive.content
    val locked = source["locked"]?.jsonPrimitive?.boolean ?: false
    val collisionEnabled = source["collisionEnabled"]?.jsonPrimitive?.boolean ?: true
    val visible = source["visible"]?.jsonPrimitive?.boolean ?: true
    val drawOrder = source.getValue("drawOrder").jsonPrimitive.int
    val pixelsPerUnit = source["pixelsPerUnit"]?.jsonPrimitive?.double ?: 1.0
    val cells by lazy(LazyThreadSafetyMode.NONE) {
        source.getValue("cells").jsonArray.associate { entry ->
            val cell = entry.jsonObject
            TileCell(cell.getValue("x").jsonPrimitive.int, cell.getValue("y").jsonPrimitive.int) to
                cell.getValue("tile").jsonPrimitive.content
        }
    }

    val occupiedBounds by lazy(LazyThreadSafetyMode.NONE) { TileBounds.enclosing(cells.keys) }

    fun grid(palette: TilePalette): TileGrid {
        val origin = source["origin"]?.jsonArray
        return TileGrid(
            Vec2(origin?.get(0)?.jsonPrimitive?.double ?: 0.0, origin?.get(1)?.jsonPrimitive?.double ?: 0.0),
            Vec2(palette.width / pixelsPerUnit, palette.height / pixelsPerUnit))
    }

    fun copy(bounds: TileBounds): TilePattern {
        require(bounds.editableSize) { "Select at most 65,536 cells" }
        val picked = cells.filterKeys { it in bounds }.mapKeys { (cell, _) ->
            TileCell(cell.x - bounds.minimum.x, cell.y - bounds.minimum.y)
        }
        return TilePattern(tileset, picked, bounds.width, bounds.height)
    }

    fun apply(delta: Map<TileCell, String?>): JsonObject {
        val updated = cells.toMutableMap()
        delta.forEach { (cell, tile) -> if (tile == null) updated.remove(cell) else updated[cell] = tile }
        return JsonObject(source + ("cells" to buildJsonArray {
            updated.entries.sortedWith(compareBy({ it.key.y }, { it.key.x })).forEach { (cell, tile) ->
                add(buildJsonObject { put("x", cell.x); put("y", cell.y); put("tile", tile) })
            }
        }))
    }
}

/** An in-flight stroke owns only pending cells. It never mutates a document. */
internal class TileStroke(private val mode: TilePaintMode, start: Vec2, tile: String?) {
    private val value = if (mode == TilePaintMode.Erase) null else requireNotNull(tile)
    private val cells = linkedMapOf<TileCell, String?>()
    private val first = requireNotNull(tileCellAt(start))
    private var last = start
    private var valid = true
    val preview: Map<TileCell, String?> get() = if (valid) cells else emptyMap()

    init { cells[first] = value }

    fun extend(position: Vec2) {
        val end = tileCellAt(position) ?: return
        if (mode == TilePaintMode.Rectangle) {
            cells.clear()
            val width = abs(end.x.toLong() - first.x) + 1
            val height = abs(end.y.toLong() - first.y) + 1
            valid = width <= tileEditLimit && height <= tileEditLimit / width
            if (valid) {
                for (y in minOf(first.y, end.y)..maxOf(first.y, end.y))
                    for (x in minOf(first.x, end.x)..maxOf(first.x, end.x)) cells[TileCell(x, y)] = value
            }
            return
        }

        if (!valid) return
        val from = checkNotNull(tileCellAt(last))
        val spanX = abs(end.x.toLong() - from.x)
        val spanY = abs(end.y.toLong() - from.y)
        if (maxOf(spanX, spanY) >= tileEditLimit) {
            valid = false
            return
        }
        val delta = position - last
        val stepX = end.x.compareTo(from.x)
        val stepY = end.y.compareTo(from.y)
        var x = from.x
        var y = from.y
        while (x != end.x || y != end.y) {
            // Compare the next vertical and horizontal grid crossings along the pointer segment.
            val crossingX = if (x == end.x) Double.POSITIVE_INFINITY
                else (x.toDouble() + if (stepX > 0) 1.0 else 0.0) - last.x
            val crossingY = if (y == end.y) Double.POSITIVE_INFINITY
                else (y.toDouble() + if (stepY > 0) 1.0 else 0.0) - last.y
            val timeX = if (x == end.x) crossingX else crossingX / delta.x
            val timeY = if (y == end.y) crossingY else crossingY / delta.y
            if (timeX <= timeY) x += stepX
            if (timeY <= timeX) y += stepY
            cells[TileCell(x, y)] = value
            if (cells.size > tileEditLimit) {
                valid = false
                return
            }
        }
        last = position
    }

}
