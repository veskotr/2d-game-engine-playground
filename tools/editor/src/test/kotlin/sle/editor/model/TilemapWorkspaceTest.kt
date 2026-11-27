package sle.editor.model

import kotlinx.serialization.json.*
import sle.editor.viewport.Vec2
import java.nio.file.Files
import kotlin.test.*

class TilemapWorkspaceTest {
    private val set = "project-asset://00000003-1111-4111-8111-111111111111"
    private val tile = "00000005-1111-4111-8111-111111111111"
    private fun file() = Files.createTempDirectory("sle-map-workspace-").resolve("World.tilemap.json").also {
        Files.writeString(it, TilemapDocument.empty("World").serialize())
    }

    @Test fun repeatedPlacementsSharePaintAndUndoAcrossSelectionChanges() {
        val path = file(); val scene = SceneWorkspaceState(emptyMap()) { it }
        val first = scene.placeTilemap(path, Vec2(50.0, 70.0))
        val layer = scene.addTileLayer(set)
        assertTrue(scene.paintTiles(layer, mapOf(TileCell(-2, 1) to "0:$tile"), "Paint"))
        val second = scene.placeTilemap(path, Vec2(100.0, 200.0))
        assertEquals("0:$tile", scene.tileLayer(layer)!!.cells[TileCell(-2, 1)])
        assertEquals(Vec2(100.0, 200.0), scene.tileLayer(layer)!!.grid(TilePalette(path.toString(), 16, 16, emptyList())).origin)
        scene.undo() // second placement
        scene.select(first); scene.undo() // paint
        assertTrue(scene.tileLayer(layer)!!.cells.isEmpty())
        scene.redo(); assertEquals("0:$tile", scene.tileLayer(layer)!!.cells[TileCell(-2, 1)])
        scene.saveTilemaps()
        assertFalse(scene.activeTilemap()!!.dirty)
        assertEquals("0:$tile", TileLayerDocument(TilemapDocument.parse(Files.readString(path)).projectedLayers("map").single().jsonObject).cells[TileCell(-2, 1)])
        assertTrue(scene.document().tileLayers.isNullOrEmpty())
        assertEquals(1, scene.entities.size)
        assertNotEquals(first, second)
    }

    @Test fun previewAndScenePersistenceUseDifferentAssetReferences() {
        val path = file(); val scene = SceneWorkspaceState(emptyMap()) { it }
        scene.placeTilemap(path)
        val layer = scene.addTileLayer(set); scene.paintTiles(layer, mapOf(TileCell(0, 0) to "0:$tile"), "Paint")
        val saved = Json.parseToJsonElement(scene.serialize()).jsonObject
        val preview = Json.parseToJsonElement(scene.previewSerialize()).jsonObject
        fun asset(root: JsonObject) = root.getValue("entities").jsonArray.single().jsonObject.getValue("components").jsonObject.getValue("Tilemap").jsonObject.getValue("asset").jsonPrimitive.content
        assertEquals(path.toString(), asset(saved)); assertNotEquals(asset(saved), asset(preview))
        assertTrue(TilemapDocument.parse(Files.readString(path)).source.getValue("layers").jsonArray.isEmpty())
    }

    private fun legacy(origin: Vec2, id: Int = 1) = buildJsonObject {
        put("id", "%08d-1111-4111-8111-111111111111".format(id)); put("name", "Ground $id")
        put("tileset", set); put("drawOrder", id); put("pixelsPerUnit", 1)
        put("origin", JsonArray(listOf(JsonPrimitive(origin.x), JsonPrimitive(origin.y))))
        put("cells", JsonArray(listOf(buildJsonObject { put("x", 0); put("y", 0); put("tile", tile) })))
    }

    @Test fun conversionRebasesIntegralOffsetsOntoOneMapOrigin() {
        val converted = convertLegacyTilemap("World", JsonArray(listOf(legacy(Vec2(50.0, 70.0)), legacy(Vec2(66.0, 70.0), 2))),
            listOf(TilePalette(set, 16, 16, emptyList())))
        assertEquals(Vec2(50.0, 70.0), converted.translation)
        val layers = converted.map.projectedLayers("map").map { TileLayerDocument(it.jsonObject) }
        assertEquals(setOf(TileCell(0, 0)), layers[0].cells.keys)
        assertEquals(setOf(TileCell(1, 0)), layers[1].cells.keys)
        assertTrue(converted.map.source.getValue("layers").jsonArray.all { "origin" !in it.jsonObject })
    }

    @Test fun conversionRejectsMisalignedOriginsWithoutResampling() {
        assertFailsWith<IllegalArgumentException> {
            convertLegacyTilemap("World", JsonArray(listOf(legacy(Vec2(0.0, 0.0)), legacy(Vec2(1.0, 0.0), 2))),
                listOf(TilePalette(set, 16, 16, emptyList())))
        }
    }
}
