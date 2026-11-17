package sle.editor.smoke

import androidx.compose.ui.geometry.Rect
import kotlinx.coroutines.delay
import sle.editor.viewport.EngineCanvas
import sle.editor.model.EntityWorkspaceState
import sle.editor.model.SceneWorkspaceState
import sle.editor.model.ScenePose
import sle.editor.viewport.Vec2
import java.awt.Rectangle
import java.awt.Robot
import java.awt.Window
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import java.nio.file.Path
import javax.imageio.ImageIO
import javax.swing.*

internal suspend fun runContextMenuSmoke(
    window: Window,
    canvas: ()->EngineCanvas,
    scene: SceneWorkspaceState,
    bounds: Map<String, Rect>,
    inspectorBounds: () -> Rect?,
    shellBounds: Map<String,Rect>,
    workspace: sle.editor.workspace.EditorWorkspace,
) {
    runAuxiliaryAppearanceSmoke(window)
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
    // Activate the frame as a user click would, then give a Swing component focus:
    // a bare frame focus owner has no key-binding path for menu mnemonics.
    window.toFront()
    robot.mouseMove(window.locationOnScreen.x+window.width/2,window.locationOnScreen.y+12)
    robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);delay(200)
    (window as JFrame).contentPane.requestFocusInWindow()
    delay(200)
    val focusManager=java.awt.KeyboardFocusManager.getCurrentKeyboardFocusManager()
    robot.keyPress(KeyEvent.VK_ALT);delay(120);robot.keyPress(KeyEvent.VK_F);delay(120)
    robot.keyRelease(KeyEvent.VK_F);robot.keyRelease(KeyEvent.VK_ALT)
    await("Alt+F did not open the File menu: owner=${focusManager.focusOwner}") { popup()!=null }
    robot.keyPress(KeyEvent.VK_DOWN);robot.keyRelease(KeyEvent.VK_DOWN)
    await("Down did not select a File menu item") { MenuSelectionManager.defaultManager().selectedPath.any { it is JMenuItem && it !is JMenu } }
    robot.keyPress(KeyEvent.VK_ESCAPE);robot.keyRelease(KeyEvent.VK_ESCAPE)
    robot.keyPress(KeyEvent.VK_ESCAPE);robot.keyRelease(KeyEvent.VK_ESCAPE)
    await("Escape did not close the File menu") { MenuSelectionManager.defaultManager().selectedPath.isEmpty() }
    println("MENU_KEYBOARD_SMOKE_PASS Alt+F/Down/Escape")
    println("MENU_SMOKE_STAGE viewport")
    val canvasOrigin=canvas().locationOnScreen
    val originalCount=scene.entities.size
    val clickedWorld=canvas().previewCamera()!!.world(Vec2(canvas().width-20.0,canvas().height-20.0))
    open(canvasOrigin.x+canvas().width-20,canvasOrigin.y+canvas().height-20)
    check(scene.selectedKey==null) { "Empty viewport right-click did not select scene root" }
    choose("Create","Entity","At Scene Root")
    check(scene.entities.size==originalCount+1)
    val created=scene.selectedKey!!
    val createdPosition=ScenePose.from(scene.selected!!.document).position
    check((createdPosition-clickedWorld).length()<0.01) { "Viewport creation ignored the clicked world position" }
    await("Created entity preview not ready") { canvas().authoringReady() }
    canvas().requestFocusInWindow()
    await("Native canvas did not gain focus") { canvas().isFocusOwner }
    repeat(3) { robot.keyPress(KeyEvent.VK_DELETE);delay(60) }
    robot.keyRelease(KeyEvent.VK_DELETE);delay(160)
    check(scene.entities.size==originalCount && scene.selectedKey==null) { "Canvas Delete/repeat removed the wrong selection" }
    scene.undoStructure();check(scene.selectedKey==created && scene.entities.size==originalCount+1)
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
    robot.mouseMove(origin.x+childPoint.x.toInt(),origin.y+childPoint.y.toInt())
    robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);delay(120)
    repeat(3) { robot.keyPress(KeyEvent.VK_DELETE);delay(60) }
    robot.keyRelease(KeyEvent.VK_DELETE);delay(150)
    check(scene.entities.size==count && scene.selectedKey==null) { "Hierarchy Delete/repeat removed another entity" }
    scene.undoStructure();check(scene.selectedKey==child && scene.parents[child]==parent)
    delay(200) // wait for the restored entity Inspector to replace the scene-root Inspector
    await("Missing scene name input bounds") { "SceneNameField" in shellBounds }
    val namePoint=shellBounds.getValue("SceneNameField").center
    robot.mouseMove(origin.x+namePoint.x.toInt(),origin.y+namePoint.y.toInt())
    robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);delay(160)
    robot.keyPress(KeyEvent.VK_CONTROL);robot.keyPress(KeyEvent.VK_A);robot.keyRelease(KeyEvent.VK_A);robot.keyRelease(KeyEvent.VK_CONTROL);delay(80)
    robot.keyPress(KeyEvent.VK_DELETE);robot.keyRelease(KeyEvent.VK_DELETE);delay(160)
    println("TEXT_DELETE_DIAGNOSTIC selection=${scene.selectedKey} expected=$child count=${scene.entities.size} expectedCount=${count+1} name=${scene.selected?.document?.name} sceneName=${scene.name} focus=${focusManager.focusOwner} bounds=${shellBounds["SceneNameField"]}")
    await("Text Delete failed: selection=${scene.selectedKey} count=${scene.entities.size} name=${scene.selected?.document?.name}") { scene.selectedKey==child && scene.entities.size==count+1 && scene.selected?.document?.name?.isEmpty()==true }
    scene.selected!!.rename("Camera")
    val dialog=JDialog(window,"Delete guard",java.awt.Dialog.ModalityType.APPLICATION_MODAL).apply {
        add(JTextField("Modal text"));setSize(240,100);setLocationRelativeTo(window)
    }
    SwingUtilities.invokeLater { dialog.isVisible=true }
    await("Modal guard dialog not visible") { dialog.isShowing }
    robot.keyPress(KeyEvent.VK_DELETE);robot.keyRelease(KeyEvent.VK_DELETE);delay(100)
    check(scene.entities.size==count+1 && scene.selectedKey==child) { "Modal Delete reached scene deletion" }
    dialog.dispose();delay(100)
    open(origin.x+childPoint.x.toInt(),origin.y+childPoint.y.toInt())
    println("MENU_SMOKE_STAGE duplicate")
    choose("Entity","Duplicate Subtree")
    check(scene.entities.size==count+2)
    // Ordinary entities are edited in the Inspector: the scene context menu adds components directly.
    val editPoint=bounds.getValue(child).center
    open(origin.x+editPoint.x.toInt(),origin.y+editPoint.y.toInt())
    check(popup()!!.components.filterIsInstance<JMenuItem>().none { it.text=="Edit" }) { "Scene entities still open a separate Entity editor" }
    println("MENU_SMOKE_STAGE inspector")
    val state: EntityWorkspaceState=scene.entities.first { it.document.key==child }
    check(scene.selectedKey==child)
    choose("Add Component","Physics","BoxCollider")
    // Adding keeps every component visible (no inspector filter) and expands the new section.
    check("BoxCollider" in state.document.components && state.selectedComponent==null && state.inspectorSections.expanded(state.document.key,"BoxCollider"))
    open(origin.x+editPoint.x.toInt(),origin.y+editPoint.y.toInt())
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
    await("Game tab not applied") { canvas().gameViewActive() }
    clickShell("SceneTab")
    await("Scene tab not applied") { !canvas().gameViewActive() }
    clickShell("GameTab")
    await("Game tab not applied") { canvas().gameViewActive() }
    clickShell("SceneTab")
    clickShell("View");choose("Prefab Workspace")
    await("Prefab workspace not ready") { canvas().authoringReady() }
    open(canvas().locationOnScreen.x+canvas().width-20,canvas().locationOnScreen.y+canvas().height-20)
    check(popup()!!.components.filterIsInstance<JMenuItem>().any { it.text=="New" }) { "Prefab navigation retained the scene entity context" }
    MenuSelectionManager.defaultManager().clearSelectedPath()
    clickShell("View");choose("Scene")
    await("Scene authoring not restored") { canvas().authoringReady() }
    clickShell("File");choose("Save")
    check(!scene.dirty && scene.error==null) { "File menu failed to save scene: ${scene.error}" }
    clickShell("File")
    check(popup()!!.components.filterIsInstance<JMenuItem>().map { it.text }.containsAll(listOf("New","Open","Save","Save As…","Exit")))
    MenuSelectionManager.defaultManager().clearSelectedPath()
    val manager=workspace.windows
    val scenePanel=sle.editor.workspace.PanelId("scene")
    manager.movePanel(scenePanel,sle.editor.workspace.DockTarget.Detached(sle.editor.workspace.LogicalBounds(80f,80f,700f,540f)))
    await("Scene viewport did not detach") { manager.hostFor(scenePanel)?.let { manager.boundWindows()[it]?.isShowing }==true }
    val tool=manager.boundWindows().getValue(manager.hostFor(scenePanel)!!) as JFrame
    fun descendants(component: java.awt.Component): List<java.awt.Component> = listOf(component)+
        (if(component is java.awt.Container) component.components.flatMap(::descendants) else emptyList())
    await("Detached canvas unavailable") { descendants(tool).filterIsInstance<EngineCanvas>().any { it.viewId==sle.editor.viewport.ViewId.Scene && it.authoringReady() } }
    val detached=descendants(tool).filterIsInstance<EngineCanvas>().single { it.viewId==sle.editor.viewport.ViewId.Scene }
    scene.addEntity();val detachedKey=scene.selectedKey!!;val detachedCount=scene.entities.size
    await("Detached creation preview not ready") { detached.authoringReady() }
    tool.toFront();tool.requestFocus();delay(150);detached.requestFocusInWindow()
    await("Detached native canvas did not gain focus") { detached.isFocusOwner }
    robot.keyPress(KeyEvent.VK_DELETE);robot.keyRelease(KeyEvent.VK_DELETE);delay(160)
    check(scene.entities.size==detachedCount-1 && scene.selectedKey==null) { "Detached canvas Delete failed" }
    scene.undoStructure();check(scene.selectedKey==detachedKey)
    await("Detached undo preview not ready") { detached.authoringReady() }
    workspace.scenePlayback.play(scene)
    await("Playback did not start") { detached.scenePlayback()==sle.editor.viewport.Playback.Running }
    for(paused in listOf(false,true)) {
        if(paused) { workspace.scenePlayback.pause();await("Playback did not pause") { detached.scenePlayback()==sle.editor.viewport.Playback.Paused } }
        detached.requestFocusInWindow();delay(80)
        robot.keyPress(KeyEvent.VK_DELETE);robot.keyRelease(KeyEvent.VK_DELETE);delay(120)
        check(scene.entities.size==detachedCount && scene.selectedKey==detachedKey) { "Playback allowed Delete (paused=$paused)" }
        val position=detached.locationOnScreen
        robot.mouseMove(position.x+detached.width-20,position.y+detached.height-20)
        robot.mousePress(InputEvent.BUTTON3_DOWN_MASK);robot.mouseRelease(InputEvent.BUTTON3_DOWN_MASK);delay(100)
        check(popup()==null) { "Playback opened an authoring context menu" }
    }
    workspace.scenePlayback.stop()
    await("Stop did not restore detached authoring") { detached.authoringReady() }
    scene.undoStructure() // remove the temporary entity creation after undoing its deletion
    check(!scene.dirty)
    manager.resetLayout()
    println("DETACHED_DELETE_PLAYBACK_SMOKE_PASS detached-delete/undo/running/paused/no-authoring-menu")
    println("CONTEXT_MENU_SMOKE_PASS positioned-creation/canvas-delete-repeat/hierarchy-delete-repeat/text-delete/modal-delete/viewport/right-click/select/cascading/child-camera/duplicate/component/attached-filter/no-entity-editor/top-menu/save/prefab-navigation/scene-game-tabs")
}
