package sle.editor.model

import kotlinx.serialization.json.*
import sle.editor.viewport.Vec2
import kotlin.math.round

internal data class LegacyTilemapConversion(val map: TilemapDocument, val translation: Vec2)

/** Rebase cells onto one grid, preserving their world positions without layer transforms. */
internal fun convertLegacyTilemap(name: String, layers: JsonArray, palettes: List<TilePalette>): LegacyTilemapConversion {
    require(layers.isNotEmpty()) { "This scene has no legacy tile layers" }
    val documents = layers.map { TileLayerDocument(it.jsonObject) }
    fun palette(layer: TileLayerDocument) = requireNotNull(palettes.firstOrNull { it.reference == layer.tileset }) { "Missing tileset for ${layer.name}" }
    val first = documents.first(); val grid = first.grid(palette(first))
    val sets = documents.map { it.tileset }.distinct()
    val converted = documents.map { layer ->
        val set = palette(layer)
        require(set.width == palette(first).width && set.height == palette(first).height && layer.pixelsPerUnit == first.pixelsPerUnit) {
            "${layer.name} uses a different grid or pixels-per-unit; it cannot be converted to one map without rescaling"
        }
        val delta = layer.grid(set).origin - grid.origin
        fun offset(value: Double, size: Double): Long {
            val units = value / size
            require(units.isFinite() && units == round(units) && units in Int.MIN_VALUE.toDouble()..Int.MAX_VALUE.toDouble()) {
                "${layer.name} has an origin that does not align with the map grid"
            }
            return units.toLong()
        }
        val dx = offset(delta.x, grid.size.x); val dy = offset(delta.y, grid.size.y)
        buildJsonObject {
            put("id", layer.id); put("name", layer.name); put("drawOrder", layer.drawOrder)
            put("visible", layer.visible); put("locked", layer.locked); put("collision", layer.collisionEnabled)
            put("cells", JsonArray(layer.cells.map { (cell, tile) ->
                val rebased = requireNotNull(offsetCell(cell, dx, dy)) { "${layer.name} exceeds grid coordinates after conversion" }
                buildJsonObject { put("x", rebased.x); put("y", rebased.y); put("set", sets.indexOf(layer.tileset)); put("tile", tile) }
            }))
        }
    }
    val base = TilemapDocument.empty(name, palette(first).width, palette(first).height, first.pixelsPerUnit)
    val map = TilemapDocument.parse(JsonObject(base.source + mapOf(
        "layers" to JsonArray(converted),
        "tilesets" to JsonArray(sets.mapIndexed { index, asset -> buildJsonObject { put("key", index); put("asset", asset) } }),
    )).toString())
    return LegacyTilemapConversion(map, grid.origin)
}
