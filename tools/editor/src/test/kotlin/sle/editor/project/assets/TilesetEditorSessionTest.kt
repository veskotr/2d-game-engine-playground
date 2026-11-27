package sle.editor.project.assets

import kotlinx.serialization.json.*
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import sle.editor.model.TileCollisionPoint
import sle.editor.model.TilesetGrid
import sle.editor.model.writeTileset
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.*

class TilesetEditorSessionTest {
    @get:Rule val temp = TemporaryFolder()

    private val sheet = "00000004-1111-4111-8111-111111111111"
    private val grid = TilesetGrid(0, 0, 0, 0, 2)
    private val original = listOf(region(1, "Grass", 0, 0), region(2, "Water", 16, 0), region(3, "Wall", 16, 16))
    private val file: Path get() = temp.root.toPath().resolve("Terrain.tileset.json")
    private var atlasRegions = original

    @Test fun updatesAreStagedAndUndoAndRedoKeepDiskUnchanged() {
        val session = open()
        val disk = Files.readString(file)
        val id = session.parsed.palette.tiles.first().id
        val metadata = buildJsonObject { put("climbable", true) }
        session.select(setOf(id))
        session.updateTiles(setOf(id), name = "Moss", tags = listOf("forest"), properties = metadata)
        session.setCollision(setOf(id), TileCollisionShape(TileCollisionMode.FullCell))

        assertEquals("Moss", session.parsed.palette.tiles.first().name)
        assertEquals(JsonArray(listOf(JsonPrimitive("forest"))), tile(session, id)["tags"])
        assertEquals(metadata, tile(session, id)["properties"])
        assertEquals(TileCollisionMode.FullCell, readTileCollision(tile(session, id)).mode)
        assertTrue(session.dirty && session.canUndo)
        assertEquals(disk, Files.readString(file))

        session.undo()
        assertEquals(TileCollisionMode.None, readTileCollision(tile(session, id)).mode)
        session.undo()
        assertFalse(session.dirty)
        assertEquals("Grass", session.parsed.palette.tiles.first().name)
        session.redo()
        session.redo()
        assertEquals(disk, Files.readString(file))
        assertEquals(setOf(id), session.selected)

        session.save()
        assertFalse(session.dirty)
        assertEquals(session.document, Json.parseToJsonElement(Files.readString(file)))
        session.undo()
        assertTrue(session.dirty)
        assertEquals("fullCell", Json.parseToJsonElement(Files.readString(file)).jsonObject.getValue("tiles")
            .jsonArray.first().jsonObject.getValue("collision").jsonObject.getValue("mode").jsonPrimitive.content)
    }

    @Test fun saveRejectsExternalEditsAndRevertReloadsTheChangedFile() {
        val session = open()
        val id = session.parsed.palette.tiles.first().id
        session.updateTiles(setOf(id), name = "Mine")
        val external = Files.readString(file).replace("Grass", "External")
        Files.writeString(file, external)

        assertFailsWith<IllegalStateException> { session.save() }
        assertEquals(external, Files.readString(file))
        assertEquals("Mine", session.parsed.palette.tiles.first().name)
        session.revert()

        assertEquals("External", session.parsed.palette.tiles.first().name)
        assertFalse(session.dirty || session.canUndo || session.canRedo)
    }

    @Test fun saveAppendsStagedRegionsOnlyAfterCheckingTheTilesetOnDisk() {
        val session = open()
        val added = region(4, "Extra", 0, 16)
        session.addTiles(listOf(added))
        assertEquals(listOf(added), session.pendingRegions)
        assertEquals(original, atlasRegions)

        val disk = Files.readString(file)
        Files.writeString(file, "$disk\n")
        assertFailsWith<IllegalStateException> { session.save { atlasRegions += it } }
        assertEquals(original, atlasRegions)
        assertTrue(session.dirty)

        Files.writeString(file, disk)
        session.save { atlasRegions += it }
        assertEquals(original + added, atlasRegions)
        assertTrue(session.pendingRegions.isEmpty())
        assertFalse(session.dirty)
    }

    @Test fun saveDetectsAnExternalEditDuringTheSidecarAppend() {
        val session = open()
        val added = region(4, "Extra", 0, 16)
        session.addTiles(listOf(added))
        val external = Files.readString(file).replace("Grass", "External")

        assertFailsWith<IllegalStateException> {
            session.save { pending ->
                atlasRegions += pending
                Files.writeString(file, external)
            }
        }

        assertEquals(external, Files.readString(file))
        assertEquals(original + added, atlasRegions)
        assertTrue(session.dirty)
    }

    @Test fun collisionBatchRejectsAllChangesWhenOneTileIsInvalid() {
        val session = open()
        val (first, second) = session.parsed.palette.tiles.map { it.id }
        val fullCell = TileCollisionShape(TileCollisionMode.FullCell)
        val invalid = TileCollisionShape(TileCollisionMode.Custom, listOf(listOf(
            TileCollisionPoint(0.0, 0.0), TileCollisionPoint(17.0, 0.0), TileCollisionPoint(0.0, 16.0),
        )))
        assertFailsWith<IllegalArgumentException> { session.setCollisions(mapOf(first to fullCell, second to invalid)) }
        assertFalse(session.dirty || session.canUndo)

        session.setCollisions(mapOf(first to fullCell, second to fullCell))
        assertEquals(listOf(TileCollisionMode.FullCell, TileCollisionMode.FullCell),
            listOf(first, second).map { readTileCollision(tile(session, it)).mode })
        session.undo()
        assertFalse(session.dirty || session.canUndo)
    }

    @Test fun reslicePreservesLogicalCellIdsAndMetadataWhenTheGridMoves() {
        val session = open()
        val ids = session.parsed.palette.tiles.map { it.id }
        session.updateTiles(setOf(ids[2]), name = "Crag", tags = listOf("stone"), properties = buildJsonObject { put("solid", true) })
        session.setCollision(setOf(ids[2]), TileCollisionShape(TileCollisionMode.FullCell))
        val movedGrid = TilesetGrid(2, 3, 4, 5, 2)
        val moved = listOf(region(4, "A", 2, 3), region(5, "B", 22, 3), region(6, "Extra", 2, 24), region(7, "D", 22, 24))

        session.reslice(moved, 16, 16, movedGrid)

        val tiles = session.parsed.palette.tiles
        assertEquals(listOf(ids[0], ids[1], ids[2]), listOf(tiles[0].id, tiles[1].id, tiles[3].id))
        assertTrue(tiles[2].id !in ids)
        assertEquals("Crag", tiles[3].name)
        assertEquals(JsonArray(listOf(JsonPrimitive("stone"))), tile(session, ids[2])["tags"])
        assertEquals(buildJsonObject { put("solid", true) }, tile(session, ids[2])["properties"])
        assertEquals(TileCollisionMode.FullCell, readTileCollision(tile(session, ids[2])).mode)
        assertEquals(moved[3], session.parsed.regions[ids[2]])
        session.undo()
        assertEquals(original[2], session.parsed.regions[ids[2]])
        session.redo()
        atlasRegions = original + moved
        session.save()
        assertEquals(movedGrid, session.parsed.grid)
    }

    @Test fun reslicePreservesLegacyIdsAtMatchingTopLefts() {
        val session = open(legacy = true)
        val first = session.parsed.palette.tiles.first().id
        val resized = region(4, "Small", 0, 0, width = 8)
        session.reslice(listOf(resized), 8, 16, TilesetGrid(0, 0, 0, 0, 1))

        assertEquals(first, session.parsed.palette.tiles.single().id)
        assertEquals(resized, session.parsed.regions[first])
        assertEquals(JsonPrimitive(2), session.document["version"])
    }

    @Test fun reslicePreservesLogicalIdsWhenTheLegacyGridMoves() {
        atlasRegions = listOf(region(1, "Grass", 3, 5), region(2, "Water", 23, 5), region(3, "Wall", 23, 27))
        val session = open(legacy = true)
        val ids = session.parsed.palette.tiles.map { it.id }
        session.updateTiles(setOf(ids[2]), name = "Crag")
        val moved = listOf(region(4, "A", 2, 3), region(5, "B", 24, 3), region(6, "D", 24, 24))

        session.reslice(moved, 16, 16, TilesetGrid(2, 3, 6, 5, 2))

        assertEquals(ids, session.parsed.palette.tiles.map { it.id })
        assertEquals("Crag", session.parsed.palette.tiles.last().name)
    }

    @Test fun irregularLegacyRegionsKeepIdsAtMatchingOrigins() {
        atlasRegions = listOf(region(1, "A", 0, 0), region(2, "Offset", 10, 0), region(3, "C", 32, 0))
        val session = open(legacy = true)
        val ids = session.parsed.palette.tiles.map { it.id }
        val cells = listOf(region(4, "First", 0, 0), region(5, "Second", 16, 0), region(6, "Third", 32, 0))

        session.reslice(cells, 16, 16, TilesetGrid(0, 0, 0, 0, 3))

        val updated = session.parsed.palette.tiles.map { it.id }
        assertEquals(ids[0], updated[0])
        assertFalse(updated[1] in ids)
        assertEquals(ids[2], updated[2])
    }

    @Test fun addingLegacyTileMetadataUpgradesTheFileWithoutChangingTileIds() {
        val session = open(legacy = true)
        val ids = session.parsed.palette.tiles.map { it.id }

        session.updateTiles(setOf(ids[0]), tags = listOf("forest"))
        session.save()

        assertEquals(grid, session.parsed.grid)
        assertEquals(ids, session.parsed.palette.tiles.map { it.id })
        assertEquals(JsonArray(listOf(JsonPrimitive("forest"))), tile(session, ids[0])["tags"])
    }

    @Test fun invalidMetadataAndCollisionResizesKeepThePreviousSnapshot() {
        val session = open()
        val first = session.parsed.palette.tiles.first().id
        assertFailsWith<IllegalArgumentException> { session.updateTiles(setOf(first), tags = listOf("stone", "stone")) }
        assertFalse(session.dirty || session.canUndo)

        val collision = TileCollisionShape(TileCollisionMode.Custom, listOf(listOf(
            TileCollisionPoint(0.0, 0.0), TileCollisionPoint(16.0, 0.0), TileCollisionPoint(0.0, 16.0),
        )))
        session.setCollision(setOf(first), collision)
        val before = session.document
        assertFailsWith<IllegalArgumentException> {
            session.reslice(listOf(region(4, "Small", 0, 0, width = 8)), 8, 16, TilesetGrid(0, 0, 0, 0, 1))
        }
        assertEquals(before, session.document)
        assertEquals(collision, readTileCollision(tile(session, first)))
    }

    @Test fun rectangleSelectionAndTileRemovalUseTileIds() {
        val session = open()
        val ids = session.parsed.palette.tiles.map { it.id }
        session.selectRectangle(16, 0, 32, 32)
        assertEquals(setOf(ids[1], ids[2]), session.selected)
        session.removeTiles(session.selected)
        assertEquals(listOf(ids[0]), session.parsed.palette.tiles.map { it.id })
        assertTrue(session.selected.isEmpty())
        session.addTiles(listOf(original[0], original[1]))
        assertEquals(listOf("Grass", "Water"), session.parsed.palette.tiles.map { it.name })
        assertNotEquals(ids[1], session.parsed.palette.tiles[1].id)
    }

    private fun open(legacy: Boolean = false): TilesetEditorSession {
        val source = if (legacy) writeTileset(sheet, 16, 16, atlasRegions) else writeTileset(sheet, 16, 16, atlasRegions, grid)
        Files.write(file, source)
        return TilesetEditorSession(file, source.decodeToString(), { if (it == sheet) "Terrain.png" else null }, { atlasRegions })
    }

    private fun region(index: Int, name: String, x: Int, y: Int, width: Int = 16) =
        SpriteRegion("%08d-2222-4222-8222-222222222222".format(index), name, x, y, width, 16)

    private fun tile(session: TilesetEditorSession, id: String) = session.document.getValue("tiles").jsonArray
        .map { it.jsonObject }.single { it["id"] == JsonPrimitive(id) }
}
