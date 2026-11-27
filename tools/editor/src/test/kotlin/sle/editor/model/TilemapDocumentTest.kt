package sle.editor.model

import kotlinx.serialization.json.*
import sle.editor.viewport.Vec2
import kotlin.test.*

class TilemapDocumentTest {
    private val tile = "00000005-1111-4111-8111-111111111111"
    private val set = "project-asset://00000003-1111-4111-8111-111111111111"
    private fun source() = Json.parseToJsonElement("""{
      "format":"sle-tilemap","version":1,"name":"World",
      "grid":{"cellWidth":16,"cellHeight":8,"pixelsPerUnit":2},
      "streaming":{"mode":"always","chunkCells":8,"loadRadius":2,"unloadRadius":3},
      "tilesets":[{"key":0,"asset":"$set"},{"key":1,"asset":"$set"}],
      "layers":[{"id":"11111111-1111-4111-8111-111111111111","name":"Ground","drawOrder":0,
        "visible":true,"locked":false,"collision":true,"cells":[
          {"x":1,"y":0,"set":1,"tile":"$tile"},{"x":-9,"y":0,"set":0,"tile":"$tile"}]}]
    }""").jsonObject

    @Test fun roundTripAndProjectionUseOneGridAndQualifiedTiles() {
        val map = TilemapDocument.parse(source().toString())
        assertEquals(map, TilemapDocument.parse(map.serialize()))
        val layer = TileLayerDocument(map.projectedLayers("map", Vec2(50.0, 70.0)).single().jsonObject)
        assertEquals("0:$tile", layer.cells[TileCell(-9, 0)])
        assertEquals("1:$tile", layer.cells[TileCell(1, 0)])
        assertEquals(Vec2(50.0, 70.0), layer.grid(TilePalette("map", 16, 8, emptyList())).origin)
        assertEquals(map, map.withProjectedLayers(JsonArray(listOf(layer.source))))
    }

    @Test fun rejectsLayerOriginsAndUnknownTilesetKeys() {
        val map = source()
        val layer = map.getValue("layers").jsonArray.single().jsonObject
        for (badLayer in listOf(JsonObject(layer + ("origin" to JsonArray(listOf(JsonPrimitive(0), JsonPrimitive(0))))),
            JsonObject(layer + ("cells" to JsonArray(listOf(buildJsonObject { put("x", 0); put("y", 0); put("set", 99); put("tile", tile) })))))) {
            assertFailsWith<IllegalArgumentException> { TilemapDocument.parse(JsonObject(map + ("layers" to JsonArray(listOf(badLayer)))).toString()) }
        }
    }

    @Test fun rejectsDuplicateCoordinatesAndMalformedGrid() {
        val map = source(); val layer = map.getValue("layers").jsonArray.single().jsonObject
        val cell = layer.getValue("cells").jsonArray.first()
        assertFailsWith<IllegalArgumentException> { TilemapDocument.parse(JsonObject(map + ("layers" to JsonArray(listOf(JsonObject(layer + ("cells" to JsonArray(listOf(cell, cell)))))))).toString()) }
        assertFailsWith<IllegalArgumentException> { TilemapDocument.parse(JsonObject(map + ("grid" to buildJsonObject { put("cellWidth", 0); put("cellHeight", 8); put("pixelsPerUnit", 2) })).toString()) }
    }
}
