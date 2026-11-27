package sle.editor.smoke

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import sle.editor.model.TilesetGrid
import sle.editor.project.ProjectWorkspaceState
import sle.editor.project.assets.AssetBrowserState
import sle.editor.project.assets.ProjectAssetCommands
import sle.editor.project.assets.SpriteGrid
import sle.editor.project.assets.TileCollisionMode
import sle.editor.project.assets.TilesetEditorWorkspace
import sle.editor.project.assets.readTileCollision
import sle.editor.ui.theme.EditorDensity
import sle.editor.ui.theme.editorMetrics
import sle.editor.viewport.EngineCanvas
import sle.editor.workspace.PanelId
import sle.editor.workspace.WorkspaceWindowManager
import sle.editor.workspace.tabs
import sle.editor.workspace.windowsMenu
import java.awt.Rectangle
import java.awt.Robot
import java.awt.Toolkit
import java.awt.Window
import java.awt.datatransfer.StringSelection
import java.awt.event.InputEvent.BUTTON1_DOWN_MASK
import java.awt.event.KeyEvent
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO
import javax.swing.RootPaneContainer
import kotlin.math.roundToInt

/** Exercises the project asset entry point and the same tileset draft in both workspace hosts. */
internal suspend fun runTilesetEditorSmoke(
    window: Window,
    openProject: (Path) -> Unit,
    project: ProjectWorkspaceState,
    browser: () -> AssetBrowserState?,
    commands: () -> ProjectAssetCommands?,
    workspace: TilesetEditorWorkspace,
    manager: WorkspaceWindowManager,
    canvas: () -> EngineCanvas?,
    bounds: Map<String, Rect>,
) {
    val reports = Path.of(System.getProperty("sle.smokeReport")).toAbsolutePath().parent
    val fixture = Path.of("tests/data/tilemap-fresh")
    val root = withContext(Dispatchers.IO) {
        val copy = Files.createTempDirectory(Files.createDirectories(reports), "tileset-editor-")
        Files.walk(fixture).use { paths ->
            paths.filter { it != fixture }.forEach { Files.copy(it, copy.resolve(fixture.relativize(it))) }
        }
        copy
    }
    val panel = PanelId("tilesetEditor")
    val robot = Robot().apply { autoDelay = 20 }
    fun panelWindow(): Window = manager.hostFor(panel)?.let { manager.boundWindows()[it] } ?: window
    fun capture(host: Window, name: String) {
        val area = Rectangle(host.locationOnScreen, host.size)
            .intersection(host.graphicsConfiguration.bounds)
        ImageIO.write(robot.createScreenCapture(area), "png", reports.resolve(name).toFile())
    }
    suspend fun await(message: String, condition: () -> Boolean) {
        repeat(300) {
            if (condition()) return
            delay(30)
        }
        runCatching { capture(panelWindow(), "tileset-editor-failure.png") }
        error("$message; project=${project.error}; assets=${commands()?.message}; viewport=${canvas()?.authoringReady()}")
    }
    fun cursor(host: Window, point: Offset) {
        val origin = (host as RootPaneContainer).contentPane.locationOnScreen
        val scale = host.graphicsConfiguration.defaultTransform
        robot.mouseMove(origin.x + (point.x / scale.scaleX).roundToInt(), origin.y + (point.y / scale.scaleY).roundToInt())
    }
    suspend fun click(id: String, host: Window = panelWindow(), double: Boolean = false) {
        await("Missing control $id") { bounds[id]?.let { it.width > 0f && it.height > 0f } == true }
        cursor(host, bounds.getValue(id).center)
        repeat(if (double) 2 else 1) {
            robot.mousePress(BUTTON1_DOWN_MASK)
            robot.mouseRelease(BUTTON1_DOWN_MASK)
            delay(65)
        }
    }
    suspend fun drag(host: Window, start: Offset, end: Offset) {
        cursor(host, start)
        robot.mousePress(BUTTON1_DOWN_MASK)
        try {
            delay(60)
            for (step in 1..5) {
                cursor(host, start + (end - start) * (step / 5f))
                delay(50)
            }
        } finally {
            robot.mouseRelease(BUTTON1_DOWN_MASK)
        }
    }
    suspend fun replace(id: String, label: String, value: String) {
        click(id)
        val asset = checkNotNull(workspace.current(root))
        fun chord(key: Int) {
            robot.keyPress(KeyEvent.VK_CONTROL)
            try {
                robot.keyPress(key)
                robot.keyRelease(key)
            } finally {
                robot.keyRelease(KeyEvent.VK_CONTROL)
            }
        }
        val clipboard = Toolkit.getDefaultToolkit().systemClipboard
        val previous = clipboard.getContents(null)
        try {
            chord(KeyEvent.VK_A)
            // Let Compose apply the selection before posting a separate paste chord.
            await("$label did not select its contents") {
                asset.inputs[label]?.let { it.selection.min == 0 && it.selection.max == it.text.length } == true
            }
            clipboard.setContents(StringSelection(value), null)
            chord(KeyEvent.VK_V)
            await("$label did not replace its contents") { asset.inputs[label]?.text == value }
        } finally {
            previous?.let { clipboard.setContents(it, null) }
        }
    }
    fun panelEntry() = windowsMenu(manager) { it == panel }.second
        .single { it.label.startsWith("Tileset Editor (") }.also { check(it.enabled) }

    openProject(root.resolve("Fresh.sleproject"))
    val texture = "Textures/Colors.png"
    await("Tileset fixture did not open") {
        project.opened?.root == root && !project.busy && commands()?.busy == false &&
            browser()?.snapshot?.idsByPath?.containsKey(texture) == true
    }
    val opened = checkNotNull(project.opened)
    val relative = "Textures/Colors.tileset.json"
    val file = opened.assets.resolve(relative)
    val grid = TilesetGrid(0, 0, 0, 0, 1)
    val cells = withContext(Dispatchers.IO) {
        SpriteGrid(8, 8).rectangles(ImageIO.read(opened.assets.resolve(texture).toFile()))
    }
    check(cells.size == 2)
    checkNotNull(commands()?.createTileset)(texture, cells, 8, 8, relative, grid)
    await("Create Tileset did not open a registered asset") {
        commands()?.busy == false && browser()?.snapshot?.idsByPath?.containsKey(relative) == true &&
            workspace.current(root)?.relative == relative && manager.hostFor(panel) == manager.layout.mainHost
    }
    val asset = checkNotNull(workspace.current(root))
    val session = asset.session
    val originalIdOrder = session.parsed.palette.tiles.map { it.id }
    val before = withContext(Dispatchers.IO) { Files.readString(file) }
    check(session.document.getValue("version").jsonPrimitive.int == 2 && session.parsed.grid == grid)
    val originalRegions = asset.liveRegions()

    val assetsPanel = PanelId("assets")
    fun hideTileset() = panelEntry().children.single { it.label == "Hide" }.action()
    suspend fun openFromAssets() {
        manager.showPanel(assetsPanel)
        click("AssetFolder:Textures", window)
        await("Textures folder did not open") { browser()?.folder == "Textures" }
        click("AssetFile:$relative", window, double = true)
        await("Tileset asset did not open its editor") {
            manager.layout.tabs().any { it.active == panel } && workspace.current(root) === asset
        }
    }
    hideTileset()
    openFromAssets()
    manager.hidePanel(assetsPanel)

    // Drag across the atlas to select both cells, then select just the first tile for edits.
    await("Atlas did not render") { bounds["TilesetAtlas"]?.height?.let { it >= 16 } == true }
    val atlas = bounds.getValue("TilesetAtlas")
    val top = Offset(atlas.center.x, atlas.top + atlas.height * .25f)
    val bottom = Offset(atlas.center.x, atlas.top + atlas.height * .75f)
    drag(window, top, bottom)
    await("Atlas rectangle did not select both tiles") { session.selected == originalIdOrder.toSet() }
    cursor(window, top)
    robot.mousePress(BUTTON1_DOWN_MASK)
    robot.mouseRelease(BUTTON1_DOWN_MASK)
    val id = originalIdOrder.first()
    await("Atlas click did not select the first tile") { session.selected == setOf(id) }

    fun editedTile(document: JsonObject = session.document) = document.getValue("tiles").jsonArray
        .map { it.jsonObject }.single { it.getValue("id") == JsonPrimitive(id) }
    fun collision() = readTileCollision(editedTile()).mode
    suspend fun moveCollisionCorner(cellSize: Int) {
        val preview = bounds.getValue("TilesetCollision")
        val inset = editorMetrics(EditorDensity.Compact).panelPadding.value * preview.height / 200f
        val pixel = (minOf(preview.width, preview.height) - 2f * inset) / cellSize
        val upperRight = Offset(preview.left + inset + pixel * cellSize, preview.bottom - inset - pixel * cellSize)
        drag(window, upperRight, upperRight - Offset(pixel, 0f))
        await("Collision handle did not move on the $cellSize-pixel tile") {
            val shape = readTileCollision(editedTile())
            val vertex = shape.pieces.singleOrNull()?.getOrNull(2)
            shape.mode == TileCollisionMode.Custom && vertex != null &&
                vertex.x in (cellSize - 1.5)..(cellSize - .5) && vertex.y in (cellSize - .5)..cellSize.toDouble()
        }
    }
    replace("TilesetName", "Name", "Terrain")
    replace("TilesetTags", "Tags", "solid, probe")
    val properties = """{"friction":0.5,"blocked":true,"surface":"stone"}"""
    replace("TilesetProperties", "Properties", properties)
    click("TilesetMetadata")
    await("Tile metadata did not stage") {
        editedTile()["name"] == JsonPrimitive("Terrain") &&
            editedTile()["tags"]?.jsonArray?.toList() == listOf(JsonPrimitive("solid"), JsonPrimitive("probe")) &&
            editedTile()["properties"] == Json.parseToJsonElement(properties)
    }
    click("TilesetFullCell")
    await("Full Cell did not stage a collision shape") { collision() == TileCollisionMode.FullCell && session.dirty }

    // The fixture's opaque tile generates one rectangle. Drag its top-right handle left by one tile pixel.
    click("TilesetFromAlpha")
    await("From Alpha did not stage a rectangle") {
        val generated = readTileCollision(editedTile())
        generated.mode == TileCollisionMode.Custom && generated.pieces.singleOrNull()?.size == 4
    }
    val generated = readTileCollision(editedTile())
    val previewHeight = bounds.getValue("TilesetCollision").height
    moveCollisionCorner(8)
    click("TilesetUndo")
    await("Undo did not restore the alpha-generated collision") { readTileCollision(editedTile()) == generated }
    click("TilesetUndo")
    await("Undo did not restore the full-cell collision") { collision() == TileCollisionMode.FullCell }
    check(withContext(Dispatchers.IO) { Files.readString(file) } == before) { "Staged edits modified the file" }

    val mainAlwaysOnTop = window.isAlwaysOnTop
    window.isAlwaysOnTop = false
    panelEntry().children.single { it.label == "Detach" }.action()
    await("Tileset Editor did not detach") { panelWindow() !== window && panelWindow().isShowing }
    manager.focusPanel(panel)
    await("Detached collision preview did not render") { bounds["TilesetCollision"]?.width?.let { it > 0 } == true }
    check(workspace.current(root) === asset && session.selected == setOf(id))
    delay(250)
    click("TilesetUndo")
    await("Detached Undo did not restore collision") { collision() == TileCollisionMode.None && session.canRedo }
    capture(panelWindow(), "tileset-editor-detached.png")

    val mainTabs = manager.layout.hosts.single { it.id == manager.layout.mainHost }.root!!.tabs()
        .first { PanelId("scene") in it.panels }
    val title = mainTabs.panels.joinToString(" / ") { manager.registry.byId.getValue(it).title }
    panelEntry().children.single { it.label == "Move to" }.children.single { it.label == title }
        .children.single { it.label == "As tab" }.action()
    await("Tileset Editor did not redock") { manager.hostFor(panel) == manager.layout.mainHost }
    window.isAlwaysOnTop = mainAlwaysOnTop
    manager.focusPanel(panel)
    delay(250)
    click("TilesetRedo")
    await("Redocked Redo did not restore collision") { collision() == TileCollisionMode.FullCell }
    check(workspace.current(root) === asset && session.selected == setOf(id))

    // Both cells still fit after changing the tile size, so their IDs and metadata can be retained.
    fun gridVisible() = listOf("TilesetSize", "TilesetReslice").all { (bounds[it]?.height ?: 0f) >= 16 }
    if (!gridVisible()) {
        cursor(window, bounds.getValue("TilesetCollision").center)
        repeat(8) {
            if (!gridVisible()) {
                robot.mouseWheel(2)
                delay(100)
            }
        }
    }
    await("Grid controls did not scroll into view") { gridVisible() }
    replace("TilesetSize", "Tile size", "6, 6")
    answerPrompts("Reslice Tileset", press("Reslice"))
    click("TilesetReslice")
    await("Reslice did not stage new cell sizes") { session.parsed.palette.width == 6 && session.parsed.palette.height == 6 }
    check(session.parsed.palette.tiles.map { it.id } == originalIdOrder)
    check(editedTile()["name"] == JsonPrimitive("Terrain") && collision() == TileCollisionMode.FullCell)
    check(asset.liveRegions() == originalRegions) { "Reslice wrote the sidecar before Save" }
    // The Full Cell handle must move with the new cell boundary even though its mode did not change.
    fun previewVisible() = (bounds["TilesetCollision"]?.height ?: 0f) >= previewHeight
    if (!previewVisible()) {
        cursor(window, bounds.getValue("TilesetReslice").center)
        repeat(8) {
            if (!previewVisible()) {
                robot.mouseWheel(-1)
                delay(100)
            }
        }
    }
    await("Resliced collision preview is clipped") { previewVisible() }
    moveCollisionCorner(6)
    click("TilesetUndo")
    await("Undo did not restore the resliced full-cell collision") {
        collision() == TileCollisionMode.FullCell && session.parsed.palette.width == 6
    }
    click("TilesetSave")
    await("Tileset Save did not finish") { !session.dirty }
    val saved = withContext(Dispatchers.IO) {
        // Open in a fresh workspace to check the on-disk asset independently of the live draft.
        TilesetEditorWorkspace().open(root, opened.assets, relative, checkNotNull(browser()?.snapshot))
    }
    check(saved.session.parsed.palette.tiles.map { it.id } == originalIdOrder)
    check(saved.session.parsed.palette.width == 6 && saved.session.parsed.palette.height == 6)
    check(editedTile(saved.session.document) == editedTile())
    check(saved.liveRegions().containsAll(originalRegions))
    check(saved.liveRegions().size == originalRegions.size + 2)
    hideTileset()
    check(panelEntry().label == "Tileset Editor (Hidden)")
    panelEntry().children.single { it.label == "Show / Focus" }.action()
    await("Windows menu did not restore the saved session") { manager.layout.tabs().any { it.active == panel } }
    check(workspace.current(root) === asset && !session.dirty)
    delay(250)
    capture(window, "tileset-editor-docked.png")
    println("TILESET_EDITOR_SMOKE_PASS")
}
