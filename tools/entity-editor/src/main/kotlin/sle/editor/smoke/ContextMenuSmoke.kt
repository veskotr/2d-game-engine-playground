package sle.editor.smoke

import androidx.compose.ui.geometry.Rect
import kotlinx.coroutines.delay
import sle.editor.viewport.EngineCanvas
import sle.editor.model.EntityWorkspaceState
import sle.editor.model.SceneWorkspaceState
import java.awt.Rectangle
import java.awt.Robot
import java.awt.Window
import java.awt.event.InputEvent
import java.nio.file.Path
import javax.imageio.ImageIO
import javax.swing.*

internal suspend fun runContextMenuSmoke(
    window: Window,
    canvas: EngineCanvas,
    scene: SceneWorkspaceState,
    bounds: Map<String, Rect>,
    inspectorBounds: () -> Rect?,
    shellBounds: Map<String,Rect>,
) {
    val robot = Robot()
    suspend fun await(message: String, condition: () -> Boolean) {
        repeat(80) { if (condition()) return; delay(40) }
        ImageIO.write(robot.createScreenCapture(Rectangle(window.locationOnScreen,window.size)),"png",Path.of(System.getProperty("sle.smokeReport")).parent.resolve("context-menu-failure.png").toFile())
        error(message)
    }
    fun popup(): JPopupMenu? = MenuSelectionManager.defaultManager().selectedPath
        .filterIsInstance<JPopupMenu>().firstOrNull { it.isShowing }
    suspend fun open(x: Int, y: Int) {
        robot.mouseMove(x,y)
        robot.mousePress(InputEvent.BUTTON3_DOWN_MASK)
        robot.mouseRelease(InputEvent.BUTTON3_DOWN_MASK)
        await("Right-click did not open menu") { popup()!=null }
    }
    suspend fun choose(vararg labels: String) {
        var menu = popup() ?: error("Missing context menu")
        labels.forEachIndexed { index, label ->
            val item = menu.components.filterIsInstance<JMenuItem>().single { it.text==label }
            check(item.isEnabled) { "Disabled menu item: $label" }
            val point = item.locationOnScreen
            robot.mouseMove(point.x+item.width/2,point.y+item.height/2)
            if (index<labels.lastIndex) {
                val child = item as JMenu
                await("Submenu did not open: $label") { child.popupMenu.isShowing }
                menu = child.popupMenu
            } else {
                robot.mousePress(InputEvent.BUTTON1_DOWN_MASK)
                robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK)
                delay(160)
            }
        }
    }
    delay(350)
    ImageIO.write(robot.createScreenCapture(Rectangle(window.locationOnScreen,window.size)),"png",Path.of(System.getProperty("sle.smokeReport")).parent.resolve("editor-menubar.png").toFile())
    val menuBar=(window as JFrame).jMenuBar
    val menuImage=robot.createScreenCapture(Rectangle(menuBar.locationOnScreen,menuBar.size))
    for(x in listOf(menuImage.width/2,menuImage.width*2/3)) {
        val pixel=menuImage.getRGB(x,menuImage.height/2)
        check((pixel shr 16 and 255)<90 && (pixel shr 8 and 255)<90 && (pixel and 255)<90) { "Menu bar filler does not match the dark surface" }
    }
    println("MENU_SMOKE_STAGE viewport")
    val canvasOrigin=canvas.locationOnScreen
    val originalCount=scene.entities.size
    open(canvasOrigin.x+canvas.width-20,canvasOrigin.y+canvas.height-20)
    check(scene.selectedKey==null) { "Empty viewport right-click did not select scene root" }
    choose("Create","Entity","At Scene Root")
    check(scene.entities.size==originalCount+1)
    scene.undoStructure()
    check(scene.entities.size==originalCount)
    val parent=scene.entities.last().document.key
    await("Missing tree node bounds") { parent in bounds }
    scene.select(scene.entities.first().document.key)
    val point=bounds.getValue(parent).center
    val origin=(window as JFrame).contentPane.locationOnScreen
    println("MENU_SMOKE_STAGE tree")
    open(origin.x+point.x.toInt(),origin.y+point.y.toInt())
    check(scene.selectedKey==parent) { "Right-click did not select its tree node" }
    val count=scene.entities.size
    choose("Create","Camera","As Child")
    check(scene.entities.size==count+1 && scene.parents[scene.selectedKey]==parent)
    val child=scene.selectedKey!!
    check("Camera2D" in scene.selected!!.document.components)
    await("New child did not appear") { child in bounds }
    val childPoint=bounds.getValue(child).center
    open(origin.x+childPoint.x.toInt(),origin.y+childPoint.y.toInt())
    println("MENU_SMOKE_STAGE duplicate")
    choose("Entity","Duplicate Subtree")
    check(scene.entities.size==count+2)
    val editPoint=bounds.getValue(child).center
    open(origin.x+editPoint.x.toInt(),origin.y+editPoint.y.toInt())
    choose("Edit")
    await("Entity inspector did not open") { inspectorBounds()!=null }
    delay(300)
    // Inspector is to the right of the native viewport; its header is a context target.
    await("Missing inspector bounds") { inspectorBounds()!=null }
    println("MENU_SMOKE_STAGE inspector")
    open(canvas.locationOnScreen.x+canvas.width-20,canvas.locationOnScreen.y+canvas.height-20)
    val state: EntityWorkspaceState=scene.entities.first { it.document.key==child }
    choose("Add Component","Physics","BoxCollider")
    check("BoxCollider" in state.document.components && state.selectedComponent=="BoxCollider")
    open(canvas.locationOnScreen.x+canvas.width-20,canvas.locationOnScreen.y+canvas.height-20)
    val add=popup()!!.components.filterIsInstance<JMenu>().single { it.text=="Add Component" }
    val physics=add.menuComponents.filterIsInstance<JMenu>().single { it.text=="Physics" }
    check(physics.menuComponents.filterIsInstance<JMenuItem>().none { it.text=="BoxCollider" })
    val addPoint=add.locationOnScreen
    robot.mouseMove(addPoint.x+add.width/2,addPoint.y+add.height/2)
    await("Component categories not visible") { add.popupMenu.isShowing }
    val physicsPoint=physics.locationOnScreen
    robot.mouseMove(physicsPoint.x+physics.width/2,physicsPoint.y+physics.height/2)
    await("Physics submenu not visible") { physics.popupMenu.isShowing }
    delay(100)
    val reports=Path.of(System.getProperty("sle.smokeReport")).parent
    ImageIO.write(robot.createScreenCapture(Rectangle(window.locationOnScreen,window.size)),"png",reports.resolve("context-menu-editor.png").toFile())
    MenuSelectionManager.defaultManager().clearSelectedPath()
    suspend fun clickShell(label: String) {
        val target=shellBounds.getValue(label).center
        robot.mouseMove(origin.x+target.x.toInt(),origin.y+target.y.toInt())
        robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK)
        delay(100)
    }
    clickShell("View");choose("Game")
    await("Game tab not applied") { canvas.gameViewActive() }
    clickShell("SceneTab")
    await("Scene tab not applied") { !canvas.gameViewActive() }
    clickShell("GameTab")
    await("Game tab not applied") { canvas.gameViewActive() }
    clickShell("SceneTab")
    clickShell("View");choose("Prefab Workspace")
    await("Prefab workspace not ready") { canvas.authoringReady() }
    open(canvas.locationOnScreen.x+canvas.width-20,canvas.locationOnScreen.y+canvas.height-20)
    check(popup()!!.components.filterIsInstance<JMenuItem>().any { it.text=="New" }) { "Prefab navigation retained the scene entity context" }
    MenuSelectionManager.defaultManager().clearSelectedPath()
    clickShell("View");choose("Scene")
    await("Scene authoring not restored") { canvas.authoringReady() }
    clickShell("File");choose("Save")
    check(!scene.dirty && scene.error==null) { "File menu failed to save scene: ${scene.error}" }
    clickShell("File")
    check(popup()!!.components.filterIsInstance<JMenuItem>().map { it.text }.containsAll(listOf("New","Open","Save","Save As…","Exit")))
    MenuSelectionManager.defaultManager().clearSelectedPath()
    println("CONTEXT_MENU_SMOKE_PASS viewport/right-click/select/cascading/child-camera/duplicate/component/attached-filter/edit/top-menu/save/prefab-navigation/scene-game-tabs")
}
