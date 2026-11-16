package sle.editor.smoke

import kotlinx.coroutines.delay
import kotlinx.serialization.json.JsonPrimitive
import sle.editor.workspace.*
import sle.editor.viewport.*
import java.awt.Robot
import java.awt.Rectangle
import java.awt.event.WindowEvent
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO
import javax.swing.JFrame

/** Exercises the production host registry and close paths against the real native session. */
internal suspend fun runDockingWorkspaceSmoke(
    home: JFrame, workspace: EditorWorkspace, editEntity: (String)->Unit, toolbarBounds: Map<String,androidx.compose.ui.geometry.Rect>, saveActive: ()->Unit,
) {
    suspend fun await(message: String, condition: ()->Boolean) {
        repeat(250) { if(condition()) return;delay(30) }
        val report=Path.of(System.getProperty("sle.smokeReport")).parent.resolve("docking-workspace-failure.png")
        val bounds=java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment().screenDevices.map { it.defaultConfiguration.bounds }.reduce { a,b -> a.union(b) }
        ImageIO.write(Robot().createScreenCapture(bounds),"png",report.toFile())
        error(message)
    }
    val manager=workspace.windows
    val session=workspace.engineSession
    val scene=workspace.scene.getOrThrow()
    val prefab=workspace.authoring.getOrThrow()
    val identity=session.owner.contextIdentity()
    val creates=session.owner.creates
    for(panel in manager.registry.panels) {
        manager.hidePanel(panel.id);delay(80)
        check(panel.id in manager.layout.hidden)
        manager.showPanel(panel.id);delay(100)
        check(manager.layout.tabs().sumOf { group -> group.panels.count { it==panel.id } }==1)
        manager.movePanel(panel.id,DockTarget.Detached(LogicalBounds(100f,100f,700f,540f)))
        val host=manager.hostFor(panel.id)!!
        await("${panel.title} did not detach") { manager.boundWindows()[host]?.isShowing==true }
        val tool=manager.boundWindows().getValue(host) as JFrame
        check(tool.jMenuBar==null && tool.owner==null && !tool.isUndecorated)
        manager.minimizePanel(panel.id);delay(80)
        check(panel.id in manager.layout.minimized)
        manager.showPanel(panel.id);delay(100)
        check(manager.hostFor(panel.id)==host && panel.id !in manager.layout.minimized)
        val target=manager.layout.hosts.single { it.id==manager.layout.mainHost }.root!!.tabs().first().group
        manager.movePanel(panel.id,DockTarget.Tab(target,0));delay(100)
        check(manager.hostFor(panel.id)==manager.layout.mainHost)
        manager.movePanel(panel.id,DockTarget.Split(target,DockEdge.Right));delay(100)
        check(manager.layout.tabs().single { panel.id in it.panels }.group!=target)
        check(session.owner.contextIdentity()==identity && session.owner.creates==creates)
        manager.resetLayout();delay(100)
    }
    // Activating the last tab through a command must reveal it in an overflowing strip.
    val group=manager.layout.tabs().first().group
    for(panel in manager.registry.panels) {
        val index=manager.layout.tabs().single { it.group==group }.panels.count { it!=panel.id }
        manager.movePanel(panel.id,DockTarget.Tab(group,index))
    }
    val originalAppearance=workspace.appearance.value
    workspace.appearance.update(originalAppearance.copy(density=sle.editor.ui.theme.EditorDensity.Comfortable))
    delay(400) // Let native minimum size catch up after merging the split groups.
    home.setSize(360,700);delay(400)
    check(home.width<=400) { "Overflow fixture did not shrink: ${home.width}" }
    manager.focusPanel(PanelId("hierarchy"));delay(200)
    manager.focusPanel(PanelId("entity"))
    await("Active overflow tab did not scroll into view") {
        manager.dropAreas().flatMap { it.tabs }.any { (id,bounds) -> id==PanelId("entity") && bounds.width>=40f }
    }
    println("ACTIVE_DOCK_TAB_VISIBLE_PASS")
    workspace.appearance.update(originalAppearance)
    manager.resetLayout();delay(200)
    runInteractiveDockingSmoke(home,workspace)
    manager.showPanel(PanelId("scene"))
    scene.addEntity()
    scene.selected!!.apply {
        addComponent("Script")
        val script=importAsset(Path.of("tests/data/scripts/embedded_multiview.lua"))
        setValue("Script",listOf("scriptAsset"),JsonPrimitive(script.toString().replace('\\','/')))
    }
    scene.addEntity()
    scene.selected!!.apply {
        rename("Dock input probe")
        addComponent("Script")
        val script=importAsset(Path.of("assets/scripts/editor_camera_demo.lua"))
        setValue("Script",listOf("scriptAsset"),JsonPrimitive(script.toString().replace('\\','/')))
    }
    val inputProbe=scene.selected!!.document.key
    val source=scene.serialize()
    workspace.scenePlayback.play(scene)
    manager.movePanel(PanelId("scene"),DockTarget.Detached(LogicalBounds(20f,80f,650f,520f)))
    manager.movePanel(PanelId("game"),DockTarget.Detached(LogicalBounds(700f,80f,650f,520f)))
    manager.showPanel(PanelId("game"))
    await("Independent Scene/Game views not published") { session.latestFrame(ViewId.Scene)!=null && session.latestFrame(ViewId.Game)!=null }
    check(session.owner.scenePlayback()==Playback.Running)
    val a=session.latestFrame(ViewId.Scene)!!;val b=session.latestFrame(ViewId.Game)!!
    check(a.texture!=b.texture && a.sequence==b.sequence)
    val tools=java.awt.Window.getWindows().filterIsInstance<JFrame>().filter { it.isShowing && it!==home }
    check(tools.size==2 && tools.all { !it.isUndecorated && it.jMenuBar==null && it.owner==null })
    fun descendants(component: java.awt.Component): List<java.awt.Component> = listOf(component) +
        (if(component is java.awt.Container) component.components.flatMap(::descendants) else emptyList())
    val sceneTool=tools.single { tool -> descendants(tool).filterIsInstance<EngineCanvas>().any { it.viewId==ViewId.Scene } }
    val gameTool=tools.single { tool -> descendants(tool).filterIsInstance<EngineCanvas>().any { it.viewId==ViewId.Game } }
    val displays=java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment().screenDevices.map { it.defaultConfiguration.bounds }
    if(displays.size>1) {
        sceneTool.setLocation(displays[0].x+30,displays[0].y+80)
        gameTool.setLocation(displays[1].x+30,displays[1].y+80)
        delay(400)
        check(sceneTool.graphicsConfiguration.device!=gameTool.graphicsConfiguration.device)
        check(session.latestFrame(ViewId.Scene)!=null && session.latestFrame(ViewId.Game)!=null)
        println("SEPARATE_MONITORS_SMOKE_PASS scene=${sceneTool.graphicsConfiguration.device.iDstring} game=${gameTool.graphicsConfiguration.device.iDstring}")
    } else println("SEPARATE_MONITORS_SMOKE_UNAVAILABLE one connected display")
    ImageIO.write(Robot().createScreenCapture(displays.reduce { first,second -> first.union(second) }),"png",Path.of(System.getProperty("sle.smokeReport")).parent.resolve("session8-simultaneous.png").toFile())
    // Focus Game first: Scene's toolbar must use its own presenter, not the last focused one.
    manager.focusPanel(PanelId("game"));delay(150)
    sceneTool.toFront();sceneTool.requestFocus()
    await("Scene tool window did not gain native focus") { sceneTool.isFocused }
    delay(200)
    val cameraBefore=session.latestFrame(ViewId.Scene)!!.camera!!.size
    val zoom=toolbarBounds.getValue("SceneZoomIn").center
    val origin=sceneTool.contentPane.locationOnScreen
    Robot().apply {
        autoDelay=40
        mouseMove(origin.x+zoom.x.toInt(),origin.y+zoom.y.toInt())
        mousePress(java.awt.event.InputEvent.BUTTON1_DOWN_MASK);mouseRelease(java.awt.event.InputEvent.BUTTON1_DOWN_MASK)
    }
    await("Scene toolbar targeted another presenter") { session.latestFrame(ViewId.Scene)?.camera?.size!=cameraBefore }
    redockGameDuringPlaySmoke(workspace,inputProbe)
    check(session.owner.contextIdentity()==identity && session.owner.creates==creates)
    manager.movePanel(PanelId("game"),DockTarget.Detached(LogicalBounds(700f,80f,650f,520f)))
    delay(350)
    for(tool in java.awt.Window.getWindows().filterIsInstance<JFrame>().filter { it.isShowing && it!==home }) tool.dispatchEvent(WindowEvent(tool,WindowEvent.WINDOW_CLOSING))
    await("Tool close did not hide both panels") { listOf("scene","game").all { PanelId(it) in manager.layout.hidden } }
    check(workspace.scenePlayback.playback==Playback.Running && scene.serialize()==source)
    val transforms=session.owner.runtimeTransforms().toString()
    delay(300)
    check(session.owner.runtimeTransforms().toString()!=transforms) { "Closing both presenters stopped scheduling" }
    manager.showPanel(PanelId("scene"));manager.showPanel(PanelId("game"))
    await("Views did not restore") { session.latestFrame(ViewId.Scene)!=null && session.latestFrame(ViewId.Game)!=null }
    check(session.owner.contextIdentity()==identity && session.owner.creates==creates)
    workspace.scenePlayback.stop()
    manager.showPanel(PanelId("scene"))
    await("Stop did not restore authoring") { session.owner.scenePlayback()==Playback.Stopped && session.owner.authoringReady() }
    val target=scene.entities.first().document.key
    editEntity(target);delay(200)
    scene.selected!!.rename("Docked scene-copy edit")
    saveActive()
    check(!scene.dirty && Files.size(scene.file!!)>0)
    // Reopen the saved package: checks that File Save targeted the containing scene.
    val path=scene.file!!
    scene.open(path)
    check(scene.entities.first().document.name=="Docked scene-copy edit")
    prefab.editField("Transform",listOf("rotation"),JsonPrimitive(0.0),"-")
    val draft=prefab.drafts
    val before=manager.layout
    val canceled=java.util.concurrent.atomic.AtomicBoolean(false)
    Thread {
        repeat(250) {
            if(canceled.get()) return@Thread
            javax.swing.SwingUtilities.invokeAndWait {
                val option=java.awt.Window.getWindows().filter { it.isShowing }
                    .flatMap(::descendants).filterIsInstance<javax.swing.JOptionPane>().firstOrNull()
                if(option!=null) { option.value=javax.swing.JOptionPane.NO_OPTION;canceled.set(true) }
            }
            Thread.sleep(20)
        }
    }.apply { isDaemon=true;start() }
    home.dispatchEvent(WindowEvent(home,WindowEvent.WINDOW_CLOSING))
    check(canceled.get()) { "Home exit did not offer the dirty-document confirmation" }
    check(manager.layout==before && !manager.closed && prefab.drafts==draft)
    manager.resetLayout();delay(200)
    check(prefab.drafts==draft && prefab.fieldErrors.isNotEmpty())
    val reports=Path.of(System.getProperty("sle.smokeReport")).parent
    ImageIO.write(Robot().createScreenCapture(Rectangle(home.locationOnScreen,home.size)),"png",reports.resolve("docking-workspace.png").toFile())
    val appearance=workspace.appearance.value
    val currentIdentity=session.owner.contextIdentity()
    val currentCreates=session.owner.creates
    val sourceBeforeAppearance=scene.serialize()
    val prefabBeforeAppearance=prefab.document
    for(density in sle.editor.ui.theme.EditorDensity.entries) for(theme in sle.editor.ui.theme.EditorThemeMode.entries) {
        workspace.appearance.update(appearance.copy(mode=theme,density=density))
        delay(600)
        ImageIO.write(Robot().createScreenCapture(Rectangle(home.locationOnScreen,home.size)),"png",
            reports.resolve("workspace-${theme.name.lowercase()}-${density.name.lowercase()}.png").toFile())
        check(scene.serialize()==sourceBeforeAppearance && prefab.document==prefabBeforeAppearance && prefab.drafts==draft)
        check(session.owner.contextIdentity()==currentIdentity && session.owner.creates==currentCreates)
    }
    workspace.appearance.update(appearance)
    check(manager.requestExit { true })
    workspace.close()
    check(session.owner.contextIdentity()==0L && session.owner.creates==session.owner.destroys)
    println("DOCKING_WORKSPACE_SMOKE_PASS six-panels/native-hosts/multiview/close-play/restore/scene-copy-save/draft/cancel-exit/cleanup")
}
