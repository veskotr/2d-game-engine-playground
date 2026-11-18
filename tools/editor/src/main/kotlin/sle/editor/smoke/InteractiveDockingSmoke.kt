package sle.editor.smoke

import kotlinx.coroutines.delay
import kotlinx.serialization.json.*
import sle.editor.workspace.*
import java.awt.*
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import javax.imageio.ImageIO
import javax.swing.JFrame
import java.nio.file.Path

/** Robot gestures enter through the installed desktop listener, never the controller API. */
internal suspend fun runInteractiveDockingSmoke(home: JFrame, workspace: EditorWorkspace, statusBar: ()->androidx.compose.ui.geometry.Rect?) {
    val manager=workspace.windows
    val robot=Robot().apply { autoDelay=20 }
    val scene=PanelId("scene");val inspector=PanelId("inspector");val assets=PanelId("assets")
    suspend fun await(message: String, condition: ()->Boolean) {
        repeat(150) { if(condition()) return;delay(30) }
        val bounds=GraphicsEnvironment.getLocalGraphicsEnvironment().screenDevices.map { it.defaultConfiguration.bounds }.reduce { a,b -> a.union(b) }
        ImageIO.write(robot.createScreenCapture(bounds),"png",Path.of(System.getProperty("sle.smokeReport")).parent.resolve("session8-interactive-failure.png").toFile())
        error("$message; homeFocused=${home.isFocused}; hosts=${manager.visibleHostBounds()}; layout=${manager.layout}")
    }
    fun area(panel: PanelId)=manager.dropAreas().single { a -> a.tabs.any { it.first==panel } }
    fun tab(panel: PanelId)=area(panel).tabs.single { it.first==panel }.second.let { DockPoint(it.x+it.width/2,it.y+it.height/2) }
    suspend fun drag(from: DockPoint, to: DockPoint, cancel: Boolean=false) {
        robot.mouseMove(from.x.toInt(),from.y.toInt());delay(100)
        robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);delay(100)
        for(step in 1..15) {
            robot.mouseMove((from.x+(to.x-from.x)*step/15).toInt(),(from.y+(to.y-from.y)*step/15).toInt())
            delay(25)
        }
        if(cancel) { robot.keyPress(KeyEvent.VK_ESCAPE);robot.keyRelease(KeyEvent.VK_ESCAPE);delay(100) }
        robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);delay(350)
    }
    fun snapshot(name: String) {
        val bounds=GraphicsEnvironment.getLocalGraphicsEnvironment().screenDevices.map { it.defaultConfiguration.bounds }.reduce { a,b -> a.union(b) }
        ImageIO.write(robot.createScreenCapture(bounds),"png",Path.of(System.getProperty("sle.smokeReport")).parent.resolve("session6-$name.png").toFile())
    }
    manager.resetLayout()
    // WSL/terminal launch can leave native foreground ownership with another app
    // despite AWT focus. Raise only for smoke activation, then test ordinary hosts.
    home.isAlwaysOnTop=true;home.toFront();home.requestFocus();delay(200)
    home.isAlwaysOnTop=false;home.toFront();delay(400)
    check(!home.isAlwaysOnTop)
    val prefab=workspace.authoring.getOrThrow()
    prefab.rename("Dock drag history")
    prefab.editField("Transform",listOf("rotation"),kotlinx.serialization.json.JsonPrimitive(0.0),"-")
    val drafts=prefab.drafts.toMap()
    val trace=java.awt.event.AWTEventListener { event ->
        if(event is java.awt.event.MouseEvent && event.id in listOf(java.awt.event.MouseEvent.MOUSE_PRESSED,java.awt.event.MouseEvent.MOUSE_RELEASED))
            println("DOCK_SMOKE_MOUSE id=${event.id} screen=${event.xOnScreen},${event.yOnScreen} window=${javax.swing.SwingUtilities.getWindowAncestor(event.component)?.name} revision=${manager.revision} active=${manager.drag.active}")
    }
    Toolkit.getDefaultToolkit().addAWTEventListener(trace,AWTEvent.MOUSE_EVENT_MASK)
    snapshot("before-drag")
    // Merge then reorder through actual tab positions.
    drag(tab(inspector),tab(scene))
    await("Details tab merge failed") { manager.layout.tabs().any { inspector in it.panels && scene in it.panels } }
    val sceneTab=area(scene).tabs.single { it.first==scene }.second
    drag(tab(inspector),DockPoint(sceneTab.x+4,sceneTab.y+sceneTab.height/2))
    await("Tab reorder failed") { manager.layout.tabs().single { scene in it.panels }.panels.first()==inspector }
    // Split onto the body edge, over native viewport content after activating Scene.
    manager.showPanel(scene);delay(200)
    val center=area(scene).bounds
    drag(tab(assets),DockPoint(center.x+center.width-8,center.y+center.height/2))
    await("Assets edge split failed") { manager.layout.tabs().single { assets in it.panels }.group!=manager.layout.tabs().single { scene in it.panels }.group }
    snapshot("split")
    // Drag the actual divider to both constraints; no negative/minimum-sized child.
    val left=area(scene).bounds
    val divider=DockPoint(left.x+left.width+2,left.y+left.height/2)
    drag(divider,DockPoint(divider.x-1200,divider.y))
    check(manager.layout.hosts.all { it.bounds.isValid() })
    // Cancel a tab drag over a different host/group without changing layout.
    val before=manager.layout
    drag(tab(inspector),tab(PanelId("hierarchy")),cancel=true)
    check(manager.layout==before) { "Escape mutated layout" }
    // Detach the entire center group with its dedicated grip.
    val group=manager.layout.tabs().single { scene in it.panels }
    val grip=area(scene).bounds.let { DockPoint(it.x+8,it.y+12) }
    val display=home.graphicsConfiguration.bounds
    val outside=DockPoint((display.x+display.width-8).toFloat(),(display.y+40).toFloat())
    check(manager.boundWindows().values.none { it.isShowing && it.bounds.contains(outside.x.toDouble(),outside.y.toDouble()) }) { "No outside-host point on smoke display" }
    drag(grip,outside)
    await("Whole-group detach failed") { manager.hostFor(scene)!=manager.layout.mainHost }
    check(manager.layout.tabs().single { scene in it.panels }.panels==group.panels)
    check(manager.layout.tabs().single { scene in it.panels }.active==group.active)
    snapshot("group-detached")
    val tool=manager.boundWindows().getValue(manager.hostFor(scene)!!)
    tool.toFront();tool.requestFocus();delay(200)
    val min=tool.minimumSize
    tool.setSize(1,1);delay(250)
    check(tool.width>=min.width && tool.height>=min.height) { "Native minimum not enforced" }
    val frame=tool as JFrame
    frame.extendedState=frame.extendedState or Frame.ICONIFIED;delay(200)
    check(manager.layout.minimized.isEmpty()) { "Native minimize changed panel placement" }
    manager.focusPanel(scene);delay(250)
    check(frame.extendedState and Frame.ICONIFIED==0)
    // Redock across native windows with the group grip.
    val toolArea=area(scene).bounds
    val destination=tab(PanelId("hierarchy"))
    drag(DockPoint(toolArea.x+8,toolArea.y+12),destination)
    await("Cross-window group redock failed") { manager.hostFor(scene)==manager.layout.mainHost }
    // Actual minimize button and restore strip (bottom of content surface).
    manager.showPanel(scene);delay(200)
    val a=area(scene).bounds
    // Last three 24dp controls, at the workspace's UI scale in AWT user units.
    val scale=workspace.uiScale
    robot.mouseMove((a.x+a.width-60*scale).toInt(),(a.y+16*scale).toInt())
    robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK)
    await("Panel minimize button failed") { scene in manager.layout.minimized }
    // The 28dp strip ends where the main window's status bar (L1 S4) begins.
    val pane=home.contentPane
    val bar=statusBar() ?: error("Status bar not laid out")
    robot.mouseMove(pane.locationOnScreen.x+60,(pane.locationOnScreen.y+bar.top-14*scale).toInt())
    robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK)
    await("Panel restore strip failed") { scene !in manager.layout.minimized }
    check(prefab.drafts==drafts && prefab.canUndo)
    check(validateLayout(manager.layout,manager.registry.byId.keys).isEmpty())
    snapshot("restored")
    manager.resetLayout();delay(300)
    Toolkit.getDefaultToolkit().removeAWTEventListener(trace)
    println("INTERACTIVE_DOCKING_SMOKE_PASS merge/reorder/edge/splitter/cancel/group-detach/cross-window/minima/native-minimize/panel-restore/drafts")
}

/** Redock an active Game view while its original native canvas has a held key. */
internal suspend fun redockGameDuringPlaySmoke(workspace: EditorWorkspace, inputProbe: String) {
    val manager=workspace.windows;val game=PanelId("game")
    val robot=Robot().apply { autoDelay=20 }
    manager.showPanel(game);delay(300)
    // Game sits alone in its own window, which has no dock header (no tab to drag):
    // re-dock it the way Windows → Game → Move to → (main group) → As tab does.
    val source=manager.dropAreas().single { game in it.panelOrder }
    check(source.host!=manager.layout.mainHost && manager.layout.isSinglePanelWindow(source.host) && source.tabs.isEmpty()) { "Single-panel Game window still shows a dock header" }
    val body=source.bounds
    robot.mouseMove((body.x+body.width/2).toInt(),(body.y+body.height/2).toInt())
    robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK)
    fun probeX()=workspace.engineSession.owner.runtimeTransforms().getValue(inputProbe).jsonObject
        .getValue("position").jsonObject.getValue("x").jsonPrimitive.double
    val initialX=probeX()
    robot.keyPress(KeyEvent.VK_D);delay(250)
    check(probeX()>initialX+5) { "Game did not accept held input before docking" }
    val target=manager.layout.hosts.single { it.id==manager.layout.mainHost }.root!!.tabs().first()
    manager.movePanel(game,DockTarget.Tab(target.group,target.panels.size));delay(350)
    val afterDropX=probeX();delay(250)
    robot.keyRelease(KeyEvent.VK_D)
    check(kotlin.math.abs(probeX()-afterDropX)<.01) { "Game retained held input across docking" }
    check(manager.hostFor(game)==manager.layout.mainHost) { "Game redock during Play failed" }
    check(workspace.engineSession.owner.scenePlayback()==sle.editor.viewport.Playback.Running)
    println("GAME_PLAY_REDOCK_SMOKE_PASS")
}
