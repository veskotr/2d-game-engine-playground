package sle.editor.model

import sle.editor.viewport.PreviewCamera
import sle.editor.viewport.Vec2
import sle.editor.viewport.SnapService
import sle.editor.viewport.tools.*
import kotlin.test.*

class TilePaintingTest {
    private val set = TilePalette("project-asset://00000003-1111-4111-8111-111111111111", 8, 4,
        listOf(TileSwatch("00000005-1111-4111-8111-111111111111", "Red")))
    private val tile = set.tiles.single().id

    @Test fun cellConversionUsesOriginScaleAndFloorAtBothZoomLevels() {
        val grid = TileGrid(Vec2(16.0, 8.0), Vec2(4.0, 2.0))
        for (zoom in listOf(1.0, 2.0)) {
            val camera = PreviewCamera(Vec2(-150.0, -20.0), Vec2(400.0 * zoom, 120.0 * zoom), 800, 240)
            assertEquals(TileCell(-33, -1), grid.cell(camera.world(camera.screen(Vec2(-113.0, 7.0)))))
            assertEquals(TileCell(0, 0), grid.cell(camera.world(camera.screen(Vec2(16.1, 8.1)))))
        }
        assertEquals(TileCell(0, 0), grid.cell(Vec2(16.0, 8.0)))
    }

    @Test fun sparseStrokeEraseUndoRedoAndReopen() {
        val scene = SceneWorkspaceState(emptyMap()) { it }
        val layer = scene.addTileLayer(set.reference)
        val stroke = TileStroke(TilePaintMode.Brush, Vec2(-32.5, 0.5), tile)
        stroke.extend(Vec2(-30.5, 0.5))
        assertTrue(scene.paintTiles(layer, stroke.preview, "Paint Tiles"))
        assertFalse(scene.paintTiles(layer, stroke.preview, "Paint Tiles"))
        assertEquals(3, scene.tileLayer(layer)!!.cells.size)
        val erased = TileCell(-32, 0)
        assertTrue(scene.paintTiles(layer, mapOf(erased to null), "Erase Tiles"))
        assertFalse(erased in scene.tileLayer(layer)!!.cells)
        scene.undo()
        assertEquals(tile, scene.tileLayer(layer)!!.cells[erased])
        scene.redo()
        val reopened = SceneWorkspaceState(emptyMap()) { it }
        reopened.openResolved(scene.serialize(), emptyMap(), java.nio.file.Path.of("map.scene.json"))
        assertEquals(scene.tileLayer(layer)!!.cells, reopened.tileLayer(layer)!!.cells)
        assertEquals(listOf("Add Tile Layer", "Paint Tiles", "Erase Tiles"), scene.history.undoLabels)
    }

    @Test fun undoRestoresCleanStateForAnUnsortedHandAuthoredMap() {
        val fixture = java.nio.file.Path.of("../../tests/data/tilemap/Assets/Two layers.scene.json")
        val envelope = kotlinx.serialization.json.Json.parseToJsonElement(java.nio.file.Files.readString(fixture))
            as kotlinx.serialization.json.JsonObject
        val scene = SceneWorkspaceState(emptyMap()) { it }
        scene.openResolved(envelope.getValue("document").toString(), emptyMap(), fixture)
        val before = scene.serialize()
        val layer = scene.tileLayerDocuments().first()
        val position = TileCell(-32, 0)
        assertTrue(scene.paintTiles(layer.id, mapOf(position to null), "Erase Tiles"))
        scene.undo()
        assertEquals(before, scene.serialize())
        assertFalse(scene.dirty)
    }

    @Test fun dispatchedRectangleEyedropperEraseAndEscape() {
        val scene = SceneWorkspaceState(emptyMap()) { it }
        val layer = scene.addTileLayer(set.reference)
        val other = TileSwatch("00000006-1111-4111-8111-111111111111", "Green")
        val state = ViewportToolState(ViewportToolId.Tiles)
        val camera = PreviewCamera(Vec2(-300.0, -20.0), Vec2(400.0, 100.0), 800, 200)
        val host = ToolHost(state, SnapService()).also { it.scene = scene; it.camera = camera }
        state.tilePainting.configure(layer, set.copy(tiles = set.tiles + other))
        fun point(x: Int, y: Int) = ToolPointer(camera.screen(Vec2(x * 8.0 + 4, y * 4.0 + 2)))
        val start = point(-33, 0)
        val end = point(-31, 1)
        state.tilePainting.selectMode(TilePaintMode.Rectangle)
        state.tilePainting.selectTile(other.id)
        val before = scene.serialize()
        val revision = scene.history.revision
        host.press(start)
        host.drag(end)
        assertEquals(before, scene.serialize())
        val escape = java.awt.event.KeyEvent(java.awt.Canvas(), java.awt.event.KeyEvent.KEY_PRESSED,
            0, 0, java.awt.event.KeyEvent.VK_ESCAPE, java.awt.event.KeyEvent.CHAR_UNDEFINED)
        assertTrue(host.key(escape))
        host.release(end)
        assertEquals(revision, scene.history.revision)
        host.press(start)
        host.release(end)
        assertEquals(revision + 1, scene.history.revision)
        assertEquals(6, scene.tileLayer(layer)!!.cells.size)
        state.tilePainting.selectMode(TilePaintMode.Eyedropper)
        state.tilePainting.selectTile(tile)
        host.press(start)
        host.release(start)
        assertEquals(other.id, state.tilePainting.tile)
        assertEquals(revision + 1, scene.history.revision)
        state.tilePainting.selectMode(TilePaintMode.Erase)
        host.press(point(-33, 1))
        host.release(end)
        assertEquals(3, scene.tileLayer(layer)!!.cells.size)
        scene.undo()
        assertEquals(6, scene.tileLayer(layer)!!.cells.size)
        scene.redo()
        assertEquals(3, scene.tileLayer(layer)!!.cells.size)
    }

    @Test fun fastDragUsesSubcellPositionAtEveryGridCrossing() {
        val stroke = TileStroke(TilePaintMode.Brush, Vec2(0.1, 0.8), tile)
        stroke.extend(Vec2(2.9, 1.8))
        assertEquals(listOf(TileCell(0, 0), TileCell(0, 1), TileCell(1, 1), TileCell(2, 1)), stroke.preview.keys.toList())
    }

    @Test fun lockedLayerAndInterruptedGesturesLeaveDocumentsUntouched() {
        val scene = SceneWorkspaceState(emptyMap()) { it }
        val layer = scene.addTileLayer(set.reference)
        val state = ViewportToolState(ViewportToolId.Tiles)
        val host = ToolHost(state, SnapService()).also {
            it.scene = scene
            it.camera = PreviewCamera(Vec2(0.0, 0.0), Vec2(100.0, 100.0), 100, 100)
        }
        state.tilePainting.configure(layer, set)
        val start = ToolPointer(Vec2(1.0, 99.0))
        val end = ToolPointer(Vec2(20.0, 99.0))
        val revision = scene.history.revision
        host.press(start)
        host.drag(end)
        host.finish()
        host.release(end)
        assertEquals(revision, scene.history.revision)
        host.press(start)
        assertTrue(scene.gestureActive)
        scene.undo()
        assertFalse(scene.gestureActive)
        assertEquals(revision, scene.history.revision)
        assertEquals(listOf("Add Tile Layer"), scene.history.undoLabels)
        host.release(end)
        host.press(start)
        val replacement = SceneWorkspaceState(emptyMap()) { it }
        host.scene = replacement
        host.release(end)
        assertEquals(revision, scene.history.revision)
        assertFalse(replacement.dirty)
        scene.setTileLayerLocked(layer, true)
        val lockedRevision = scene.history.revision
        host.scene = scene
        host.press(start)
        host.release(end)
        assertFalse(scene.paintTiles(layer, mapOf(TileCell(0, 0) to tile), "Paint Tiles"))
        assertEquals(lockedRevision, scene.history.revision)
        assertTrue(scene.tileLayer(layer)!!.cells.isEmpty())
    }
    @Test fun fourWayFillIsBoundedAndCrossesChunkEdges() {
        val bounds = TileBounds(TileCell(30, 0), TileCell(33, 2))
        val walls = mapOf(TileCell(32, 0) to tile, TileCell(31, 1) to tile, TileCell(32, 1) to tile)
        val filled = tileFill(walls, bounds, TileCell(30, 2), tile)
        assertTrue(TileCell(33, 2) in filled)
        assertTrue(TileCell(33, 0) in filled)
        assertFalse(TileCell(34, 2) in filled)
        val corners = TileBounds(TileCell(0, 0), TileCell(1, 1))
        val blockers = mapOf(TileCell(0, 1) to tile, TileCell(1, 0) to tile)
        assertEquals(setOf(TileCell(0, 0)), tileFill(blockers, corners, TileCell(0, 0), tile).keys)
    }

    @Test fun patternsMoveAcrossOverlapAndPasteAtomicallyWithSeparateHistory() {
        val other = TileSwatch("00000006-1111-4111-8111-111111111111", "Green")
        val palette = set.copy(tiles = set.tiles + other)
        val first = SceneWorkspaceState(emptyMap()) { it }
        val second = SceneWorkspaceState(emptyMap()) { it }
        val ground = first.addTileLayer(set.reference)
        val upper = first.addTileLayer(set.reference)
        val destination = second.addTileLayer(set.reference)
        val stamp = TilePattern(set.reference, mapOf(TileCell(0, 0) to tile, TileCell(1, 0) to other.id))
        assertTrue(first.pasteTiles(ground, palette, stamp, TileCell(30, 0)))
        val selected = TileBounds(TileCell(30, 0), TileCell(31, 0))
        assertTrue(first.moveTiles(ground, selected, TileCell(31, 0)))
        val moved = mapOf(TileCell(31, 0) to tile, TileCell(32, 0) to other.id)
        assertEquals(moved, first.tileLayer(ground)!!.cells)
        first.undo()
        assertEquals(selected, first.tileSelection(ground))
        first.redo()
        assertEquals(TileCell(31, 0), first.tileSelection(ground)?.minimum)
        assertTrue(first.tileLayer(upper)!!.cells.isEmpty())
        val revision = second.history.revision
        assertFailsWith<IllegalArgumentException> { second.pasteTiles(destination, set, stamp, TileCell(0, 0)) }
        assertFailsWith<IllegalArgumentException> {
            second.pasteTiles(destination, palette, stamp.copy(tileset = "incompatible"), TileCell(0, 0))
        }
        assertEquals(revision, second.history.revision)
        assertTrue(second.tileLayer(destination)!!.cells.isEmpty())
        assertTrue(second.pasteTiles(destination, palette, stamp, TileCell(0, 0)))
        second.undo()
        assertTrue(second.tileLayer(destination)!!.cells.isEmpty())
        assertEquals(moved, first.tileLayer(ground)!!.cells)
        second.redo()
        second.setTileLayerLocked(destination, true)
        assertFalse(second.pasteTiles(destination, palette, stamp, TileCell(1, 0)))
        second.undo()
        assertTrue(second.pasteTiles(destination, palette, stamp, TileCell(1, 0)))
    }

    @Test fun restoresLayerAndSelectionWhenSwitchingDocumentFiles() {
        val first = SceneWorkspaceState(emptyMap()) { it }
        val back = first.addTileLayer(set.reference)
        first.addTileLayer(set.reference)
        val sceneOne = first.serialize()
        val second = SceneWorkspaceState(emptyMap()) { it }
        val front = second.addTileLayer(set.reference)
        val sceneTwo = second.serialize()
        val pathOne = java.nio.file.Path.of("one.scene.json")
        val pathTwo = java.nio.file.Path.of("two.scene.json")
        val boundsOne = TileBounds(TileCell(0, 0), TileCell(2, 1))
        val boundsTwo = TileBounds(TileCell(5, 1), TileCell(5, 2))
        val workspace = SceneWorkspaceState(emptyMap()) { it }
        workspace.openResolved(sceneOne, emptyMap(), pathOne)
        workspace.activeTileLayer = back
        workspace.selectTiles(back, boundsOne)
        workspace.openResolved(sceneTwo, emptyMap(), pathTwo)
        assertNull(workspace.tileSelection(back))
        workspace.activeTileLayer = front
        workspace.selectTiles(front, boundsTwo)
        workspace.openResolved(sceneOne, emptyMap(), pathOne)
        assertEquals(back, workspace.activeTileLayer)
        assertEquals(boundsOne, workspace.tileSelection(back))
        workspace.openResolved(sceneTwo, emptyMap(), pathTwo)
        assertEquals(front, workspace.activeTileLayer)
        assertEquals(boundsTwo, workspace.tileSelection(front))
    }

    @Test fun switchesSavedScenesWithIndependentUndoAndRedo() {
        val seed = SceneWorkspaceState(emptyMap()) { it }
        val layer = seed.addTileLayer(set.reference)
        val start = seed.serialize()
        val other = SceneWorkspaceState(emptyMap()) { it }
        val otherLayer = other.addTileLayer(set.reference)
        val otherStart = other.serialize()
        val a = java.nio.file.Path.of("a.scene.json")
        val b = java.nio.file.Path.of("b.scene.json")
        val workspace = SceneWorkspaceState(emptyMap()) { it }
        workspace.openResolved(start, emptyMap(), a)
        workspace.paintTiles(layer, mapOf(TileCell(0, 0) to tile), "Paint A")
        workspace.savedResolved(a)
        val writtenA = workspace.serialize()
        workspace.openResolved(otherStart, emptyMap(), b)
        workspace.paintTiles(otherLayer, mapOf(TileCell(1, 0) to tile), "Paint B")
        workspace.savedResolved(b)
        val writtenB = workspace.serialize()
        workspace.openResolved(writtenA, emptyMap(), a)
        assertEquals("Paint A", workspace.history.undoLabel)
        workspace.undo()
        assertTrue(workspace.tileLayer(layer)!!.cells.isEmpty())
        workspace.redo()
        workspace.openResolved(writtenB, emptyMap(), b)
        assertEquals("Paint B", workspace.history.undoLabel)
        workspace.undo()
        assertTrue(workspace.tileLayer(otherLayer)!!.cells.isEmpty())
    }
}
