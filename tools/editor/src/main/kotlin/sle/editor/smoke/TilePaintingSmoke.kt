package sle.editor.smoke

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import sle.editor.model.*
import sle.editor.project.*
import sle.editor.viewport.EngineCanvas
import sle.editor.viewport.Vec2
import sle.editor.viewport.tools.ViewportToolId
import sle.editor.viewport.tools.ViewportToolState
import java.awt.Robot
import java.awt.event.InputEvent.BUTTON1_DOWN_MASK
import java.awt.event.KeyEvent
import java.nio.file.Files
import java.nio.file.Path
import kotlin.math.roundToInt

internal suspend fun runTilePaintingSmoke(
    openProject: (Path) -> Unit,
    project: ProjectWorkspaceState,
    scene: SceneWorkspaceState,
    canvas: () -> EngineCanvas?,
    tools: ViewportToolState,
    playback: ScenePlaybackState,
    openPalette: suspend () -> Unit,
) {
    val fixture = Path.of("tests/data/tilemap")
    val root = withContext(Dispatchers.IO) {
        val reports = Path.of(System.getProperty("sle.smokeReport")).parent
        val copy = Files.createTempDirectory(Files.createDirectories(reports), "tile-painting-")
        Files.walk(fixture).use { paths ->
            paths.filter { it != fixture }.forEach { Files.copy(it, copy.resolve(fixture.relativize(it))) }
        }
        copy
    }
    suspend fun await(message: String, condition: () -> Boolean) {
        repeat(300) {
            if (condition()) return
            delay(30)
        }
        error(message)
    }
    openProject(root.resolve("Tiles.sleproject"))
    await("Tile project did not open") { project.opened?.root == root && !project.busy }
    val documents = ProjectDocuments(checkNotNull(project.opened))
    val map = root.resolve("Assets/Two layers.scene.json")
    scene.openProject(documents.open(map))
    val set = scene.tileLayerDocuments().first().tileset
    val layer = scene.addTileLayer(set)
    val painting = tools.tilePainting
    await("Tile palette did not resolve") { painting.layerId == layer && painting.palette?.tiles?.size == 2 }
    await("Scene viewport did not load") { canvas()?.authoringReady() == true }
    val view = checkNotNull(canvas())
    val robot = Robot()
    fun cursor(world: Vec2) {
        val pixel = checkNotNull(view.previewCamera()).screen(world)
        check(pixel.x in 1.0..<view.width.toDouble() && pixel.y in 1.0..<view.height.toDouble()) { "Tile outside viewport: $world → $pixel" }
        val origin = view.locationOnScreen
        robot.mouseMove(origin.x + pixel.x.roundToInt(), origin.y + pixel.y.roundToInt())
    }
    fun key(code: Int) { robot.keyPress(code); robot.keyRelease(code) }
    suspend fun stroke(start: Vec2, end: Vec2, editsCells: Boolean = true) {
        cursor(start)
        robot.mousePress(BUTTON1_DOWN_MASK)
        try {
            if (editsCells) await("${painting.mode.label} press did not start a tile gesture") { scene.gestureActive }
            else delay(100)
            cursor(end)
            delay(150)
            if (editsCells) check(scene.gestureActive) { "${painting.mode.label} gesture canceled before release" }
        } finally {
            robot.mouseRelease(BUTTON1_DOWN_MASK)
        }
    }
    suspend fun zoom(factor: Float) {
        val before = view.previewCamera()?.size
        view.zoomEntity(factor)
        await("Tile viewport did not zoom") { view.previewCamera()?.size?.let { it != before } == true }
    }

    val first = Vec2(-260.0, 20.0)
    val middle = Vec2(-252.0, 20.0)
    val last = Vec2(-244.0, 20.0)
    zoom(1.5f)
    cursor(first)
    robot.mousePress(BUTTON1_DOWN_MASK)
    robot.mouseRelease(BUTTON1_DOWN_MASK)
    await("Scene viewport did not get focus") { view.isFocusOwner }
    key(KeyEvent.VK_T)
    await("Tile shortcut did not activate") { tools.active == ViewportToolId.Tiles }
    stroke(first, last)
    await("Brush did not cross the negative chunk boundary") { scene.tileLayer(layer)?.cells?.size == 3 && view.authoringReady() }
    check(scene.tileLayer(layer)?.cells?.keys == (-33..-31).map { TileCell(it, 2) }.toSet())
    val brush = checkNotNull(painting.tile)
    suspend fun paintedPixel(label: String): Boolean {
        val file = root.resolve("$label.png").toFile()
        view.captureFile = file
        await("Tile viewport frame was not captured") { view.captureFile == null }
        val image = javax.imageio.ImageIO.read(file)
        val camera = checkNotNull(view.previewCamera())
        val sample = camera.screen(middle)
        val x = (sample.x * image.width / camera.width).roundToInt()
        val y = (sample.y * image.height / camera.height).roundToInt()
        return (-2..2).any { dy -> (-2..2).any { dx ->
            val color = java.awt.Color(image.getRGB(x + dx, y + dy))
            color.red > color.green + 40 && color.red > color.blue + 40 && color.blue < 100
        } }
    }
    check(paintedPixel("painted")) { "Painted tile did not appear in the viewport frame" }
    zoom(1.2f)
    painting.selectMode(TilePaintMode.Rectangle)
    stroke(first + Vec2(0.0, 8.0), last + Vec2(0.0, 8.0))
    await("Rectangle release did not finish") { !scene.gestureActive }
    check(scene.tileLayer(layer)?.cells?.size == 6) {
        "Rectangle at the other zoom produced ${scene.tileLayer(layer)?.cells?.keys}; ${scene.error}"
    }
    await("Rectangle preview did not catch up") { view.authoringReady() }
    painting.selectMode(TilePaintMode.Eyedropper)
    painting.selectTile(checkNotNull(painting.palette).tiles.first { it.id != brush }.id)
    stroke(middle, middle, editsCells = false)
    await("Eyedropper did not pick the painted tile") { painting.tile == brush }
    painting.selectMode(TilePaintMode.Erase)
    stroke(middle, middle)
    await("Erase did not reach the viewport") { scene.tileLayer(layer)?.cells?.size == 5 && view.authoringReady() }
    check(!paintedPixel("erased")) { "Erased tile still rendered" }
    scene.undo()
    await("Undo did not reach the viewport") { scene.tileLayer(layer)?.cells?.size == 6 && view.authoringReady() }
    check(paintedPixel("undo")) { "Undo did not restore the tile pixels" }
    scene.redo()
    await("Redo did not reach the viewport") { scene.tileLayer(layer)?.cells?.size == 5 && view.authoringReady() }

    check(!paintedPixel("redo")) { "Redo did not erase the tile pixels" }

    val saved = documents.save(map, ProjectDocumentKind.Scene, scene.serialize(), scene.allAssets(), scene.projectDocument, ::canonicalSceneDocument)
    scene.openProject(saved)
    await("Saved map did not reopen") { scene.tileLayer(layer)?.cells?.size == 5 && view.authoringReady() }
    val before = scene.serialize()
    await("Selected tile palette did not refresh after save") { painting.layerId == layer && painting.palette != null }
    val playRevision = scene.history.revision
    playback.play(scene)
    await("Tile scene did not enter Play") { canvas()?.scenePlayback() == sle.editor.viewport.Playback.Running }
    val game = checkNotNull(canvas())
    val gameOrigin = game.locationOnScreen
    robot.mouseMove(gameOrigin.x + game.width / 2, gameOrigin.y + game.height / 2)
    robot.mousePress(BUTTON1_DOWN_MASK)
    robot.mouseRelease(BUTTON1_DOWN_MASK)
    delay(150)
    check(scene.history.revision == playRevision && scene.serialize() == before) { "Play input edited the tile scene" }
    playback.stop()
    await("Scene viewport did not return from Play") { view.isShowing && view.authoringReady() }
    val second = root.resolve("Assets/Other.scene.json")
    val other = documents.save(second, ProjectDocumentKind.Scene, SceneDocument(name = "Other").serialize(),
        emptyMap(), null, ::canonicalSceneDocument)
    painting.selectMode(TilePaintMode.Brush)
    zoom(1.5f)
    cursor(first + Vec2(0.0, 16.0))
    robot.mousePress(BUTTON1_DOWN_MASK)
    try {
        delay(100)
        cursor(last + Vec2(0.0, 16.0))
        delay(100)
        check(scene.serialize() == before) { "Preview changed the saved document" }
        scene.openProject(other)
    } finally {
        robot.mouseRelease(BUTTON1_DOWN_MASK)
    }
    delay(150)
    check(!scene.dirty && scene.tileLayerDocuments().isEmpty()) { "Replacement document received a partial stroke" }
    scene.openProject(documents.open(map))
    check(scene.serialize() == before) { "Original document received a partial stroke" }
    await("Original scene did not load after document switch") { view.authoringReady() }
    val room = scene.addTileLayer(set)
    scene.activeTileLayer = room
    await("Room layer did not become active") { painting.layerId == room && view.authoringReady() }
    val base = first + Vec2(0.0, 8.0)
    val farCorner = last + Vec2(0.0, 16.0)
    painting.selectMode(TilePaintMode.Eyedropper)
    stroke(base, base, editsCells = false)
    await("Scene viewport did not regain focus") { view.isFocusOwner }
    key(KeyEvent.VK_6)
    await("Select shortcut was not routed") { painting.mode == TilePaintMode.Select }
    stroke(base, farCorner)
    val selected = TileBounds(TileCell(-33, 3), TileCell(-31, 4))
    await("Room selection was not drawn") { scene.tileSelection(room) == selected }
    key(KeyEvent.VK_5)
    await("Fill shortcut was not routed") { painting.mode == TilePaintMode.Fill }
    stroke(base, base)
    await("Bounded fill did not paint the new layer") { scene.tileLayer(room)?.cells?.size == 6 && view.authoringReady() }
    key(KeyEvent.VK_6)
    stroke(base, base + Vec2(8.0, 0.0))
    await("Overlapping move did not reach the viewport") {
        scene.tileSelection(room)?.minimum == TileCell(-32, 3) && view.authoringReady()
    }
    check(scene.tileLayer(room)?.cells?.keys == (-32..-30).flatMap { x -> (3..4).map { y -> TileCell(x, y) } }.toSet())
    robot.keyPress(KeyEvent.VK_CONTROL)
    try { key(KeyEvent.VK_C) } finally { robot.keyRelease(KeyEvent.VK_CONTROL) }
    await("Tile copy shortcut did not capture the room") { painting.clipboard?.tiles?.size == 6 }
    painting.useCopiedStamp()
    val stampBase = base - Vec2(0.0, 16.0)
    stroke(stampBase, stampBase)
    await("Copied stamp did not paint the two rows") { scene.tileLayer(room)?.cells?.size == 12 && view.authoringReady() }
    scene.undo()
    await("Undo did not remove the stamp as one stroke") { scene.tileLayer(room)?.cells?.size == 6 && view.authoringReady() }
    cursor(stampBase)
    robot.keyPress(KeyEvent.VK_CONTROL)
    try { key(KeyEvent.VK_V) } finally { robot.keyRelease(KeyEvent.VK_CONTROL) }
    await("Tile paste did not reach the viewport") { scene.tileLayer(room)?.cells?.size == 12 && view.authoringReady() }
    check(scene.tileLayer(layer)?.cells?.size == 5)
    val editedRoom = scene.serialize()
    val savedRoom = documents.save(map, ProjectDocumentKind.Scene, editedRoom, scene.allAssets(),
        scene.projectDocument, ::canonicalSceneDocument)
    scene.savedProject(savedRoom, editedRoom)
    scene.openProject(documents.open(second))
    val secondLayer = scene.addTileLayer(set)
    val editedSecond = scene.serialize()
    val savedSecond = documents.save(second, ProjectDocumentKind.Scene, editedSecond, scene.allAssets(),
        scene.projectDocument, ::canonicalSceneDocument)
    scene.savedProject(savedSecond, editedSecond)
    scene.openProject(documents.open(map))
    check(scene.activeTileLayer == room && scene.tileSelection(room)?.minimum == TileCell(-32, 3))
    check(scene.history.undoLabel == "Paste Tiles") { "Room undo history was lost when reopening the scene" }
    scene.undo()
    check(scene.tileLayer(room)?.cells?.size == 6)
    scene.redo()
    check(scene.tileLayer(room)?.cells?.size == 12)
    scene.openProject(documents.open(second))
    check(scene.history.undoLabel == "Add Tile Layer" && scene.tileLayer(secondLayer) != null)
    openPalette()
    println("TILE_PAINTING_SMOKE_PASS")
}
