package sle.editor.smoke

import sle.editor.ui.postedAfter
import androidx.compose.ui.geometry.Rect
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import sle.editor.model.*
import sle.editor.project.*
import sle.editor.ui.EntityDrag
import sle.editor.ui.EntityDragPayload
import sle.editor.viewport.EngineCanvas
import sle.editor.viewport.Vec2
import sle.editor.workspace.*
import java.awt.*
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import java.nio.file.*
import javax.swing.*

/** Session 6 desktop checks: entity drag to Assets (Hierarchy and viewport), cancellation, stale
 * payloads, Apply Changes to Prefab, and the temporary Prefab tab lifecycle. Runs inside the open
 * project left by [runProjectDocumentsSmoke], whose scene holds one linked `shared` instance. */
internal suspend fun runPrefabWorkflowSmoke(window: JFrame,canvas: ()->EngineCanvas,project: ProjectWorkspaceState,
    documents: ()->ProjectDocumentCommands?,assetBounds: Map<String,Rect>,treeBounds: Map<String,Rect>,
    scene: SceneWorkspaceState,prefab: EntityWorkspaceState,workspace: EditorWorkspace) {
    val robot=Robot().apply { autoDelay=10 }
    val manager=workspace.windows
    val prefabPanel=PanelId("entity")
    suspend fun await(message: String,condition: ()->Boolean) {
        repeat(240) { if(condition()) { delay(80);return };delay(25) }
        runCatching { javax.imageio.ImageIO.write(robot.createScreenCapture(Rectangle(Toolkit.getDefaultToolkit().screenSize)),"png",
            Path.of(System.getProperty("sle.smokeReport")).parent.resolve("prefab-workflow-failure.png").toFile()) }
        error("$message; scene=${scene.error}; prefab=${prefab.error}; project=${project.error}")
    }
    fun screen(bounds: Rect): Point { val o=window.contentPane.locationOnScreen;return Point(o.x+bounds.center.x.toInt(),o.y+bounds.center.y.toInt()) }
    suspend fun glide(from: Point,to: Point,steps: Int=14) {
        for(step in 1..steps) { robot.mouseMove(from.x+(to.x-from.x)*step/steps,from.y+(to.y-from.y)*step/steps);delay(25) }
    }
    fun descendants(component: Component): List<Component> = listOf(component)+(if(component is Container) component.components.flatMap(::descendants) else emptyList())
    /** Answers the next discard prompt: true discards, false keeps the draft. */
    fun answer(discard: Boolean) = answerPrompts("Discard", { it.answer(if(discard) 0 else 1) })
    fun popup(): JPopupMenu? = MenuSelectionManager.defaultManager().selectedPath.filterIsInstance<JPopupMenu>().firstOrNull { it.isShowing }
    suspend fun contextChoose(at: Point,label: String) {
        robot.mouseMove(at.x,at.y);robot.mousePress(InputEvent.BUTTON3_DOWN_MASK);robot.mouseRelease(InputEvent.BUTTON3_DOWN_MASK)
        await("Context menu did not open") { popup()!=null }
        val item=popup()!!.components.filterIsInstance<JMenuItem>().single { it.text==label }
        check(item.isEnabled) { "Disabled menu item: $label" }
        val point=item.locationOnScreen;robot.mouseMove(point.x+item.width/2,point.y+item.height/2)
        robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);delay(160)
    }
    val opened=project.opened ?: error("Project closed")
    val assets=opened.root.resolve(opened.document.assetRoot)
    val prefabs=assets.resolve("Prefabs")
    val scenePath=scene.file ?: error("Scene has no file")
    await("Prefabs folder row missing") { "AssetFolder:Prefabs" in assetBounds }
    // The previous smoke ends in the Prefab tab; the scene Hierarchy shows in Scene view.
    manager.showPanel(PanelId("scene"))
    await("Scene view not active") { workspace.selectedTab==2 && canvas().authoringReady() }
    // --- Hierarchy row dragged onto the Prefabs folder: saved, then linked as one undoable change.
    scene.addEntity();val root=scene.selectedKey!!;scene.selected!!.rename("Dragged")
    scene.addChild();val child=scene.selectedKey!!;scene.select(root)
    await("Hierarchy row missing") { root in treeBounds }
    val folder=screen(assetBounds.getValue("AssetFolder:Prefabs"))
    robot.mouseMove(screen(treeBounds.getValue(root)).x,screen(treeBounds.getValue(root)).y)
    robot.mousePress(InputEvent.BUTTON1_DOWN_MASK)
    glide(screen(treeBounds.getValue(root)),folder)
    await("Hierarchy drag gave no valid-target feedback") { EntityDrag.dragging?.entityKey==root && EntityDrag.hovered!=null }
    robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK)
    await("Hierarchy drag did not create a linked entity template") {
        !project.busy && scene.document().prefabInstances.any { it.key==root } && Files.exists(prefabs.resolve("Dragged.prefab.json"))
    }
    check(scene.entities.any { it.document.key=="$root/$child" } && scene.parents["$root/$child"]==root) { "Dragged subtree was not re-keyed as its instance" }
    check(EntityDrag.dragging==null && scene.selectedKey==root)
    scene.undo()
    check(scene.document().prefabInstances.none { it.key==root } && scene.entities.any { it.document.key==child }) { "Undo did not restore the unlinked subtree" }
    scene.redo()
    check(scene.document().prefabInstances.any { it.key==root })
    println("PREFAB_WORKFLOW_STAGE hierarchy-drag")
    // --- Escape cancels; a stale payload is refused at drop time.
    val count=Files.list(prefabs).use { it.count() }
    scene.addEntity();val loose=scene.selectedKey!!
    await("Loose row missing") { loose in treeBounds }
    robot.mouseMove(screen(treeBounds.getValue(loose)).x,screen(treeBounds.getValue(loose)).y)
    robot.mousePress(InputEvent.BUTTON1_DOWN_MASK)
    glide(screen(treeBounds.getValue(loose)),folder)
    await("Second drag did not start") { EntityDrag.dragging?.entityKey==loose }
    robot.keyPress(KeyEvent.VK_ESCAPE);robot.keyRelease(KeyEvent.VK_ESCAPE);delay(120)
    check(EntityDrag.dragging==null) { "Escape did not cancel the entity drag" }
    robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);delay(300)
    check(!project.busy && Files.list(prefabs).use { it.count() }==count && scene.document().prefabInstances.none { it.key==loose }) { "Cancelled drag wrote an entity template" }
    // Refusals are reported as status-bar notices (the scene's error field is consumed by them).
    val beforeStale=workspace.notices.recent.firstOrNull()
    EntityDrag.move(EntityDragPayload("stale-scene",scene.file,loose,"Stale"),folder)
    check(EntityDrag.release(folder))
    await("Stale payload was not refused") { workspace.notices.postedAfter(beforeStale).any { it.level==sle.editor.ui.NoticeLevel.Error && "changed during the drag" in it.text } }
    check(Files.list(prefabs).use { it.count() }==count)
    println("PREFAB_WORKFLOW_STAGE cancel-stale")
    // --- Viewport: drag a picked sprite out of the canvas onto the folder; its pose is unchanged.
    val view=canvas()
    await("Viewport authoring not ready") { view.authoringReady() && view.previewCamera()!=null }
    val camera=view.previewCamera()!!
    val world=camera.world(Vec2(view.width/2.0,view.height/2.0)).let { Vec2(Math.round(it.x).toDouble(),Math.round(it.y).toDouble()) }
    scene.addPreset("SpriteRenderer",position=world);val sprite=scene.selectedKey!!;scene.selected!!.rename("Viewport Sprite")
    await("Sprite preview not ready") { view.authoringReady() }
    val pose=scene.entities.first { it.document.key==sprite }.document.components["Transform"]
    val pixel=view.previewCamera()!!.screen(world);val at=view.locationOnScreen
    val start=Point(at.x+pixel.x.toInt(),at.y+pixel.y.toInt())
    robot.mouseMove(start.x,start.y);robot.mousePress(InputEvent.BUTTON1_DOWN_MASK)
    glide(start,folder,20)
    await("Viewport drag gave no valid-target feedback") { EntityDrag.dragging?.entityKey==sprite && EntityDrag.hovered!=null }
    robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK)
    await("Viewport drag did not create a linked entity template") {
        !project.busy && scene.document().prefabInstances.any { it.key==sprite } && Files.exists(prefabs.resolve("Viewport Sprite.prefab.json"))
    }
    check(scene.entities.first { it.document.key==sprite }.document.components["Transform"]==pose) { "Viewport drop kept the move instead of cancelling it" }
    println("PREFAB_WORKFLOW_STAGE viewport-drag")
    // --- Apply Changes to Prefab from the Hierarchy context menu.
    scene.select(root);scene.selected!!.setValue("Transform",listOf("rotation"),JsonPrimitive(0.0))
    scene.selected!!.document=scene.selected!!.document.addComponent("Audio",buildJsonObject { put("volume",0.3) })
    contextChoose(screen(treeBounds.getValue(root)),"Apply Changes to Entity Template")
    await("Apply did not update the entity template asset") {
        !project.busy && Files.readString(prefabs.resolve("Dragged.prefab.json")).contains("\"Audio\"")
    }
    val link=scene.document().prefabInstances.first { it.key==root }
    check(link.overrides.keys.all { it==link.source.root } && "Audio" in link.source.entities.first { it.key==link.source.root }.components) { "Apply left the edit as an override" }
    check(scene.dirty && scene.error==null)
    println("PREFAB_WORKFLOW_STAGE apply")
    // --- Temporary Prefab tab: a restored layout showing it without a prefab hides it again.
    if(prefabPanel !in manager.layout.hidden) { manager.hidePanel(prefabPanel);await("Clean entity template tab did not close") { prefabPanel in manager.layout.hidden } }
    check(!workspace.prefabSession && workspace.selectedTab==2 && prefab.file==null)
    val center=manager.layout.tabs().single { PanelId("scene") in it.panels }.group
    manager.movePanel(prefabPanel,DockTarget.Tab(center,0))
    check(prefabPanel !in manager.layout.hidden) { "Fixture did not restore the Entity Template tab" }
    await("Restored stale Entity template tab was not hidden") { prefabPanel in manager.layout.hidden }
    // Double-click opens a prefab in the temporary tab.
    val relative="Prefabs/Dragged.prefab.json"
    browserReveal(assetBounds,"AssetFolder:Prefabs",window,robot)
    await("Dragged entity template row missing") { "AssetFile:$relative" in assetBounds }
    val row=screen(assetBounds.getValue("AssetFile:$relative"))
    robot.mouseMove(row.x,row.y)
    repeat(2) { robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);delay(60) }
    await("Double-click did not open the Entity Template tab") {
        !project.busy && workspace.prefabSession && prefabPanel !in manager.layout.hidden && prefab.file?.fileName?.toString()=="Dragged.prefab.json"
    }
    // Closing with unsaved changes asks; No keeps the tab, Yes discards and returns to the scene.
    prefab.rename("Unsaved Entity Template Edit");check(prefab.dirty)
    answer(false);manager.hidePanel(prefabPanel);delay(200)
    check(prefabPanel !in manager.layout.hidden && prefab.dirty && workspace.prefabSession) { "Declined close lost the entity template tab" }
    manager.movePanel(prefabPanel,DockTarget.Detached(LogicalBounds(120f,120f,640f,480f)))
    await("Entity template tab did not detach") { manager.hostFor(prefabPanel)?.let { manager.boundWindows()[it]?.isShowing }==true }
    answer(false);manager.closeHost(manager.hostFor(prefabPanel)!!);delay(200)
    check(prefabPanel !in manager.layout.hidden && prefab.dirty) { "Closing the detached window discarded the entity template" }
    answer(true);manager.closeHost(manager.hostFor(prefabPanel)!!)
    await("Confirmed close did not return to the scene") { prefabPanel in manager.layout.hidden && !workspace.prefabSession && workspace.selectedTab==2 }
    check(!prefab.dirty && prefab.file==null)
    check(!Files.readString(prefabs.resolve("Dragged.prefab.json")).contains("Unsaved Entity Template Edit"))
    manager.resetLayout()
    println("PREFAB_WORKFLOW_STAGE prefab-tab")
    // --- Save and reopen: both new links persist as declarations.
    documents()!!.savePath(ProjectDocumentKind.Scene,scenePath)
    await("Scene save failed") { !project.busy && !scene.dirty }
    documents()!!.openPath(scenePath)
    await("Scene reopen failed") { !project.busy && !scene.dirty && scene.document().prefabInstances.map { it.key }.containsAll(listOf(root,sprite)) }
    check(scene.entities.any { it.document.key=="$root/$child" })
    println("PREFAB_WORKFLOW_SMOKE_PASS hierarchy-drag/undo-redo/escape/stale/viewport-drag/apply/restore-hidden/double-click/close-prompt/detached/reopen")
}

/** Makes sure an Assets folder row is laid out (navigating there lists its files). */
private suspend fun browserReveal(bounds: Map<String,Rect>,id: String,window: JFrame,robot: Robot) {
    val point=bounds.getValue(id).center;val at=window.contentPane.locationOnScreen
    robot.mouseMove(at.x+point.x.toInt(),at.y+point.y.toInt())
    robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);delay(250)
}
