package sle.editor.project.assets

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import sle.editor.model.TileCollisionPoint
import sle.editor.model.generateTileCollision
import kotlin.test.*

class TilesetCollisionTest {
    @Test fun batchApplyReopenAndUndoRestoreModesAndGeometry() {
        val original = Json.parseToJsonElement("""{"tiles":[{"id":"a","region":"ra"},{"id":"b","region":"rb"}]}""").jsonObject
        val file = java.nio.file.Files.createTempFile("sle-tileset-collision", ".tileset.json")
        try {
            java.nio.file.Files.writeString(file, original.toString())
            val session = TilesetCollisionSession(file, original.toString())
            val shape = TileCollisionShape(TileCollisionMode.Custom, listOf(listOf(
                TileCollisionPoint(0.0, 0.0), TileCollisionPoint(1.0, 0.0), TileCollisionPoint(0.0, 1.0)
            )))
            assertEquals(TileCollisionMode.None, session.shape("a").mode)
            session.apply(mapOf("a" to shape, "b" to TileCollisionShape(TileCollisionMode.FullCell)))
            val reopened = TilesetCollisionSession(file, java.nio.file.Files.readString(file))
            assertEquals(shape, reopened.shape("a"))
            assertEquals(TileCollisionMode.FullCell, reopened.shape("b").mode)
            session.undo()
            assertEquals(TileCollisionMode.None, session.shape("a").mode)
            assertEquals(TileCollisionMode.None, session.shape("b").mode)
            session.redo()
            assertEquals(shape, session.shape("a"))
        } finally {
            java.nio.file.Files.deleteIfExists(file)
        }
    }

    @Test fun rejectsMalformedPersistedPolygons() {
        for (vertices in listOf("[[0,0],[1,0]]", "[[0,0],[0,1],[1,0]]", "[[0,0],[2,0],[0,1]]")) {
            val tile = Json.parseToJsonElement(
                """{"collision":{"mode":"custom","pieces":[$vertices]}}"""
            ).jsonObject
            assertFailsWith<IllegalArgumentException> { readTileCollision(tile, 1, 1) }
        }
    }

    @Test fun reopensSolidSlopeRingAndDecoration() {
        val original = """{"tiles":[{"id":"solid"},{"id":"slope"},{"id":"ring"},{"id":"decoration"}]}"""
        val file = java.nio.file.Files.createTempFile("sle-tileset-overlays", ".tileset.json")
        try {
            java.nio.file.Files.writeString(file, original)
            val session = TilesetCollisionSession(file, original)
            val slope = generateTileCollision(3, 3, intArrayOf(255, 0, 0, 255, 255, 0, 255, 255, 255),
                tolerance = 1.0)
            val ring = generateTileCollision(3, 3, intArrayOf(255, 255, 255, 255, 0, 255, 255, 255, 255))
            val staged = mapOf(
                "solid" to TileCollisionShape(TileCollisionMode.FullCell),
                "slope" to TileCollisionShape(TileCollisionMode.Custom, slope),
                "ring" to TileCollisionShape(TileCollisionMode.Custom, ring),
                "decoration" to TileCollisionShape(TileCollisionMode.None),
            )
            assertEquals(original, java.nio.file.Files.readString(file))
            session.apply(staged)
            val reopened = TilesetCollisionSession(file, java.nio.file.Files.readString(file))
            assertEquals(staged, staged.keys.associateWith(reopened::shape))

            java.nio.file.Files.writeString(file, original + "\n")
            assertFailsWith<IllegalStateException> { session.undo() }
            assertTrue(session.canUndo)
            assertEquals(original + "\n", java.nio.file.Files.readString(file))
        } finally {
            java.nio.file.Files.deleteIfExists(file)
        }
    }
}
