package sle.editor.smoke

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.toArgb
import kotlinx.coroutines.delay
import kotlinx.serialization.json.*
import sle.editor.model.SceneWorkspaceState
import sle.editor.model.ScenePose
import sle.editor.ui.notice
import sle.editor.ui.NoticeLevel
import sle.editor.ui.theme.EditorAppearance
import sle.editor.ui.theme.EditorDensity
import sle.editor.ui.theme.EditorThemeMode
import sle.editor.ui.theme.editorColors
import sle.editor.viewport.DragAxis
import sle.editor.viewport.EngineCanvas
import sle.editor.viewport.GizmoShape
import sle.editor.viewport.Playback
import sle.editor.viewport.SnapSettings
import sle.editor.viewport.Vec2
import sle.editor.viewport.ViewId
import sle.editor.viewport.tools.ToolHandleKind
import sle.editor.viewport.tools.ViewportToolId
import sle.editor.workspace.DockTarget
import sle.editor.workspace.EditorWorkspace
import sle.editor.workspace.LogicalBounds
import sle.editor.workspace.PanelId
import java.awt.Component
import java.awt.Container
import java.awt.Point
import java.awt.Rectangle
import java.awt.Robot
import java.awt.Toolkit
import java.awt.Window
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO
import javax.swing.JFrame
import javax.swing.JMenu
import javax.swing.JMenuItem
import javax.swing.JPopupMenu
import javax.swing.JToolTip
import javax.swing.MenuSelectionManager
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * The L1 manual walk (plan editor-core-l1.md, Session 7) driven by Robot on the camera demo scene:
 * 1 creation, Ctrl/Shift selection and marquee; 2 W/E/R drags with snapping and Ctrl inversion;
 * 3 one timeline over structural and transform steps; 4 Delete and the status-bar Undo button;
 * 5 shortcuts in detached Hierarchy and Scene windows; 6 Game focused while playing (W and Delete
 * reach the game, F5/F6/Shift+F5 still work); 7 every theme and density, with captures of the
 * outlines, active row, status bar, tool handles and the disabled-reason menu tooltip.
 * Captures go to build/reports/l1-walk-<java2d scale>/ for review.
 */
internal suspend fun runL1WalkSmoke(window: JFrame, scene: SceneWorkspaceState, workspace: EditorWorkspace,
    treeBounds: MutableMap<String,Rect>, shellBounds: Map<String,Rect>) =
    L1Walk(window,scene,workspace,treeBounds,shellBounds).run()

private class L1Walk(val window: JFrame, val scene: SceneWorkspaceState, val workspace: EditorWorkspace,
    val treeBounds: MutableMap<String,Rect>, val shellBounds: Map<String,Rect>) {
    val robot=Robot()
    val scaleTag=System.getProperty("sun.java2d.uiScale") ?: "1"
    val folder: Path=Path.of(System.getProperty("sle.smokeReport")).parent.resolve("l1-walk-$scaleTag")
    val manager=workspace.windows
    val tools=workspace.tools
    lateinit var canvas: EngineCanvas
    lateinit var initial: String
    lateinit var edited: String
    lateinit var crateStart: ScenePose
    val created=mutableListOf<String>()
    var base=0
    var count=0
    val player by lazy { id("Player") }
    val crate by lazy { id("Crate B") }
    val origin: Point get()=window.contentPane.locationOnScreen

    suspend fun run() {
        Files.createDirectories(folder)
        // A 1.5 Java2D scale makes the logical screen smaller: keep the whole window on screen.
        val config=window.graphicsConfiguration
        val insets=Toolkit.getDefaultToolkit().getScreenInsets(config)
        val area=config.bounds.let { Rectangle(it.x+insets.left,it.y+insets.top,it.width-insets.left-insets.right,it.height-insets.top-insets.bottom) }
        if(!area.contains(window.bounds)) { window.setBounds(area.x,area.y,minOf(window.width,area.width),minOf(window.height,area.height));delay(600) }
        canvas=sceneCanvas();ready(canvas)
        initial=scene.serialize();base=scene.history.undoLabels.size;count=scene.entities.size
        check(player!=crate)
        capture("walk-0-start")
        step1();step2();step3();step4();step5();step6();step7()
        println("L1_WALK_SMOKE_PASS steps=1-7 java2d-scale=$scaleTag dir=$folder")
    }

    fun id(name: String)=scene.entities.first { it.document.name==name }.document.key
    fun pose(entity: String)=ScenePose.from(scene.worldDocument(entity))
    fun onStep(value: Double,step: Double)=abs(value/step-Math.rint(value/step))<1e-9
    fun descendants(component: Component): List<Component> = listOf(component)+
        (if(component is Container) component.components.flatMap(::descendants) else emptyList())
    fun canvases()=Window.getWindows().filter { it.isShowing }.flatMap(::descendants).filterIsInstance<EngineCanvas>().filter { it.isShowing }
    fun capture(name: String,target: Component=window) =
        ImageIO.write(robot.createScreenCapture(Rectangle(target.locationOnScreen,target.size)),"png",folder.resolve("$name.png").toFile())
    suspend fun await(message: String,condition: ()->Boolean) {
        repeat(150) { if(condition()) return;delay(40) }
        val screens=java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment().screenDevices.map { it.defaultConfiguration.bounds }.reduce { a,b -> a.union(b) }
        ImageIO.write(robot.createScreenCapture(screens),"png",folder.resolve("walk-failure.png").toFile());error(message)
    }
    suspend fun sceneCanvas(): EngineCanvas {
        await("Scene canvas not ready") { canvases().any { it.viewId==ViewId.Scene && it.authoringReady() } }
        return canvases().first { it.viewId==ViewId.Scene && it.authoringReady() }
    }
    suspend fun ready(target: EngineCanvas) { await("Scene preview did not catch up") { target.authoringReady() };delay(100) }
    fun press(codes: List<Int>) = codes.forEach(robot::keyPress)
    fun release(codes: List<Int>) = codes.asReversed().forEach(robot::keyRelease)
    suspend fun key(code: Int,vararg modifiers: Int) {
        press(modifiers.toList());robot.keyPress(code);robot.keyRelease(code);release(modifiers.toList());delay(180)
    }
    suspend fun clickScreen(x: Int,y: Int,vararg modifiers: Int) {
        robot.mouseMove(x,y);press(modifiers.toList())
        robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK)
        release(modifiers.toList());delay(180)
    }
    /** Compose reports bounds in device pixels; AWT and Robot use user-space units (they differ under a Java2D scale). */
    fun Rect.user(host: Window=window)=host.graphicsConfiguration.defaultTransform.let { Rect(left/it.scaleX.toFloat(),top/it.scaleY.toFloat(),right/it.scaleX.toFloat(),bottom/it.scaleY.toFloat()) }
    suspend fun clickWindow(bounds: Rect) = bounds.user().let { clickScreen(origin.x+it.center.x.toInt(),origin.y+it.center.y.toInt()) }
    fun onCanvas(target: EngineCanvas,pixel: Vec2)=target.locationOnScreen.let { Point(it.x+pixel.x.roundToInt(),it.y+pixel.y.roundToInt()) }
    suspend fun click(target: EngineCanvas,pixel: Vec2,vararg modifiers: Int) { val p=onCanvas(target,pixel);clickScreen(p.x,p.y,*modifiers);ready(target) }
    /** [held] modifiers go down after the press, so they shape the drag rather than the click. */
    suspend fun drag(target: EngineCanvas,from: Vec2,path: List<Vec2>,held: List<Int> = emptyList()) {
        onCanvas(target,from).let { robot.mouseMove(it.x,it.y) };robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);delay(80)
        press(held)
        path.forEach { p -> onCanvas(target,p).let { robot.mouseMove(it.x,it.y) };delay(35) }
        robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);release(held);delay(150);ready(target)
    }
    suspend fun clickTree(host: JFrame,key: String,vararg modifiers: Int) {
        await("Hierarchy row not laid out: $key") { key in treeBounds }
        val at=host.contentPane.locationOnScreen;val center=treeBounds.getValue(key).user(host).center
        clickScreen(at.x+center.x.toInt(),at.y+center.y.toInt(),*modifiers)
    }
    fun popup(): JPopupMenu? = MenuSelectionManager.defaultManager().selectedPath.filterIsInstance<JPopupMenu>().firstOrNull { it.isShowing }
    suspend fun choose(vararg labels: String) {
        var menu=popup() ?: error("Missing menu")
        labels.forEachIndexed { index,label ->
            val item=menu.components.filterIsInstance<JMenuItem>().single { it.text==label }
            val point=item.locationOnScreen
            robot.mouseMove(point.x+item.width/2,point.y+item.height/2)
            if(index<labels.lastIndex) {
                val child=item as JMenu
                await("Submenu did not open: $label") { child.popupMenu.isShowing }
                menu=child.popupMenu
            } else { robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);delay(200) }
        }
    }
    fun spriteCenter(target: EngineCanvas,entity: String)=GizmoShape.from(scene.worldDocument(entity),"SpriteRenderer")!!.center.let(target.previewCamera()!!::screen)
    fun screenBounds(target: EngineCanvas,keys: List<String>)=target.previewCamera()!!.let { camera ->
        keys.mapNotNull { sle.editor.viewport.entityScreenBounds(scene.worldDocument(it),camera) }.reduce { a,b -> a.union(b) } }
    /** An empty canvas spot up and left of [keys] (a focus click there clears the selection; a marquee starts there). */
    fun emptySpot(target: EngineCanvas,keys: List<String>): Vec2 {
        val camera=target.previewCamera()!!
        val box=screenBounds(target,keys)
        return (0 until target.width step 12).flatMap { x -> (0 until target.height step 12).map { y -> Vec2(x.toDouble(),y.toDouble()) } }
            .filter { it.x<box.min.x-8 && it.y<box.min.y-8 && it.x>8 && it.y>8 }
            .filter { sle.editor.viewport.ScenePicking.pick(scene,camera,it)==null &&
                sle.editor.viewport.tools.marqueeHits(scene,camera,it-Vec2(4.0,4.0),it+Vec2(4.0,4.0)).isEmpty() }
            .minByOrNull { (it-box.min).length() } ?: error("No empty viewport spot above $keys")
    }
    fun handle(kind: ToolHandleKind,axis: DragAxis?=null)=canvas.toolHandles().firstOrNull { it.kind==kind && (axis==null || it.axis==axis) }
        ?: error("$kind $axis handle missing: ${canvas.toolHandles()}")
    suspend fun oneStep(label: String,action: suspend ()->Unit) {
        val steps=scene.history.undoLabels.size;action()
        check(scene.history.undoLabels.size==steps+1 && scene.history.undoLabel==label) { "Expected one step '$label': ${scene.history.undoLabels.takeLast(2)}" }
    }

    /** 1. Create two entities from the viewport menu; Ctrl and Shift select in the Hierarchy; marquee. */
    suspend fun step1() {
        val ground=id("Ground")
        for(offset in listOf(30,110)) {
            val p=onCanvas(canvas,Vec2(canvas.width-offset.toDouble(),canvas.height-30.0))
            robot.mouseMove(p.x,p.y);robot.mousePress(InputEvent.BUTTON3_DOWN_MASK);robot.mouseRelease(InputEvent.BUTTON3_DOWN_MASK)
            await("Right-click did not open the viewport menu") { popup()!=null }
            choose("Create","Entity","At Scene Root");ready(canvas)
            created+=scene.selectedKey!!
            check(scene.history.undoLabel?.startsWith("Create")==true) { "Creation was not a Create step: ${scene.history.undoLabel}" }
        }
        check(scene.entities.size==count+2) { "Viewport menu did not create two entities" }
        clickTree(window,ground)
        check(scene.selection.keys==listOf(ground)) { "Hierarchy click did not select Ground: ${scene.selection.keys}" }
        clickTree(window,crate,KeyEvent.VK_CONTROL)
        check(scene.selection.keys.toSet()==setOf(ground,crate) && scene.selectedKey==crate) { "Hierarchy Ctrl-click did not add Crate B: ${scene.selection.keys}" }
        clickTree(window,created.last(),KeyEvent.VK_SHIFT)
        check(scene.selection.keys.toSet()==setOf(crate)+created && scene.selectedKey==created.last()) { "Hierarchy Shift-click range is wrong: ${scene.selection.keys}" }
        capture("walk-1-hierarchy-range")
        val spot=emptySpot(canvas,listOf(player,crate))
        click(canvas,spot)
        check(scene.selection.isEmpty) { "Empty viewport click did not clear the selection" }
        key(KeyEvent.VK_Q)
        check(tools.active==ViewportToolId.Select) { "Q did not select the Select tool" }
        val camera=canvas.previewCamera()!!
        val far=screenBounds(canvas,listOf(player,crate)).max+Vec2(10.0,10.0)
        drag(canvas,spot,(1..8).map { spot+(far-spot)*(it/8.0) })
        val expected=sle.editor.viewport.tools.marqueeHits(scene,camera,Vec2(spot.x.roundToInt().toDouble(),spot.y.roundToInt().toDouble()),
            Vec2(far.x.roundToInt().toDouble(),far.y.roundToInt().toDouble())).toSet()
        check(player in expected && crate in expected && scene.selection.keys.toSet()==expected) { "Marquee selected ${scene.selection.keys}, expected $expected" }
        capture("walk-1-marquee")
        println("L1_WALK_STEP 1 create/hierarchy-click/ctrl-click/shift-range/marquee selected=${expected.size}")
    }

    /** 2. W/E/R drags on Crate B: snapped, then with Ctrl held (free), each one step. */
    suspend fun step2() {
        click(canvas,spriteCenter(canvas,crate))
        check(scene.selection.keys==listOf(crate)) { "Click did not select Crate B alone: ${scene.selection.keys}" }
        workspace.snap.update(SnapSettings(enabled=true,move=8.0,rotateDegrees=15.0,scale=0.1));delay(150);ready(canvas)
        crateStart=pose(crate)
        fun held(ctrl: Boolean)=if(ctrl) listOf(KeyEvent.VK_CONTROL) else emptyList()
        key(KeyEvent.VK_W)
        check(tools.active==ViewportToolId.Move) { "W did not select the Move tool" }
        for(ctrl in listOf(false,true)) oneStep("Move Crate B") {
            val arrow=handle(ToolHandleKind.Move,DragAxis.X);val grab=(arrow.from+arrow.to)*0.5
            val before=pose(crate)
            drag(canvas,grab,(1..8).map { grab+Vec2((if(ctrl) 37.0 else 70.0)*it/8,0.0) },held(ctrl))
            val after=pose(crate)
            check(after.position.y==before.position.y && after.position.x>before.position.x) { "X arrow did not move along X: ${before.position} -> ${after.position}" }
            check(onStep(after.position.x,8.0)!=ctrl) { "Move snapping (ctrl=$ctrl) is wrong: x=${after.position.x}" }
        }
        capture("walk-2-move")
        key(KeyEvent.VK_E)
        check(tools.active==ViewportToolId.Rotate) { "E did not select the Rotate tool" }
        for(ctrl in listOf(false,true)) oneStep("Rotate Crate B") {
            val ring=handle(ToolHandleKind.Ring);val radius=(ring.to-ring.from).length()
            val degrees=if(ctrl) 22.0 else 47.0
            val before=pose(crate)
            drag(canvas,ring.to,(1..12).map { i -> Math.toRadians(degrees*i/12).let { ring.from+Vec2(cos(it),-sin(it))*radius } },held(ctrl))
            val after=Math.toDegrees(pose(crate).rotation)
            check(after!=Math.toDegrees(before.rotation)) { "Ring drag did not rotate Crate B" }
            check(onStep(after,15.0)!=ctrl) { "Rotate snapping (ctrl=$ctrl) is wrong: $after°" }
        }
        capture("walk-2-rotate")
        key(KeyEvent.VK_R)
        check(tools.active==ViewportToolId.Scale) { "R did not select the Scale tool" }
        for(ctrl in listOf(false,true)) oneStep("Scale Crate B") {
            val handles=canvas.toolHandles()
            val x=handle(ToolHandleKind.Scale,DragAxis.X);val grab=(x.from+x.to)*0.5
            val direction=(x.to-x.from)*(1/(x.to-x.from).length())
            val before=pose(crate)
            drag(canvas,grab,(1..6).map { grab+direction*((if(ctrl) 13.0 else 24.0)*it/6) },held(ctrl))
            val after=pose(crate)
            check(after.scale.y==before.scale.y && after.scale.x>before.scale.x) {
                "X handle did not scale X only: ${before.scale} -> ${after.scale}; ctrl=$ctrl grab=$grab direction=$direction handles=$handles camera=${canvas.previewCamera()}" }
            check(onStep(after.scale.x,0.1)!=ctrl) { "Scale snapping (ctrl=$ctrl) is wrong: ${after.scale.x}" }
        }
        capture("walk-2-scale")
        workspace.snap.update(SnapSettings());delay(120)
        println("L1_WALK_STEP 2 move/rotate/scale snapped-and-ctrl-free one-step-each end=${pose(crate)}")
    }

    /** 3. One timeline: 2 creates then 6 transform steps undo in reverse order, and redo returns. */
    suspend fun step3() {
        edited=scene.serialize()
        val labels=scene.history.undoLabels.drop(base)
        check(labels.size==8 && labels.take(2).all { it.startsWith("Create") } &&
            labels.drop(2)==listOf("Move Crate B","Move Crate B","Rotate Crate B","Rotate Crate B","Scale Crate B","Scale Crate B")) { "Unexpected timeline: $labels" }
        repeat(6) { key(KeyEvent.VK_Z,KeyEvent.VK_CONTROL) };ready(canvas)
        check(pose(crate)==crateStart && scene.entities.size==count+2) { "Six undos did not restore Crate B before the creations: ${pose(crate)}" }
        check(scene.history.undoLabel?.startsWith("Create")==true) { "Next undo is not the second Create: ${scene.history.undoLabel}" }
        repeat(2) { key(KeyEvent.VK_Z,KeyEvent.VK_CONTROL) };ready(canvas)
        check(scene.serialize()==initial) { "Eight undos did not restore the starting scene" }
        repeat(7) { key(KeyEvent.VK_Y,KeyEvent.VK_CONTROL) };key(KeyEvent.VK_Z,KeyEvent.VK_CONTROL,KeyEvent.VK_SHIFT);ready(canvas)
        check(scene.serialize()==edited) { "Ctrl+Y ×7 and Ctrl+Shift+Z did not redo every step" }
        println("L1_WALK_STEP 3 timeline=${labels.joinToString("|")} undo-order/redo/ctrl-shift-z")
    }

    /** 4. Delete reports "Deleted Crate B" with Undo; a Robot click on that button restores it. */
    suspend fun step4() {
        click(canvas,spriteCenter(canvas,crate))
        key(KeyEvent.VK_DELETE);ready(canvas)
        val notice=workspace.notices.current
        check(scene.entity(crate)==null && notice?.text=="Deleted Crate B" && notice.action?.available()==true) { "Delete notice missing: ${notice?.text}" }
        val barBounds=(shellBounds["StatusBar"] ?: error("Status bar not laid out")).user()
        val bar=Rectangle(origin.x+barBounds.left.toInt(),origin.y+barBounds.top.toInt(),barBounds.width.toInt(),barBounds.height.toInt())
        capture("walk-4-delete-notice")
        val image=robot.createScreenCapture(bar)
        ImageIO.write(image,"png",folder.resolve("walk-4-status-bar.png").toFile())
        val chip=editorColors(workspace.appearance.value.mode).notice(NoticeLevel.Info).second.toArgb()
        fun near(a: Int,b: Int)=listOf(16,8,0).all { abs((a shr it and 255)-(b shr it and 255))<=8 }
        val row=image.height/2+1
        val right=(0 until image.width).lastOrNull { near(image.getRGB(it,row),chip) } ?: error("Notice chip colour not found in the status bar")
        clickScreen(bar.x+right-20,bar.y+row)
        await("Status-bar Undo did not restore Crate B") { scene.entity(crate)!=null }
        check(scene.selection.keys==listOf(crate) && scene.serialize()==edited) { "Status-bar Undo did not restore the scene and selection" }
        println("L1_WALK_STEP 4 delete-notice/status-bar-undo-click x=${right-20}")
    }

    /** 5. Detached Hierarchy and Scene windows: their shortcuts act on the scene. */
    suspend fun step5() {
        window.isAlwaysOnTop=false
        treeBounds.clear() // the detached Hierarchy reports its rows in its own window
        manager.movePanel(PanelId("hierarchy"),DockTarget.Detached(LogicalBounds(40f,60f,320f,460f)))
        manager.movePanel(PanelId("scene"),DockTarget.Detached(LogicalBounds(380f,60f,640f,460f)))
        fun host(panel: String)=manager.hostFor(PanelId(panel))?.let { manager.boundWindows()[it] } as? JFrame
        await("Panels did not detach") { host("hierarchy")?.isShowing==true && host("scene")?.isShowing==true && host("hierarchy")!==window && host("scene")!==window }
        val hierarchyWindow=host("hierarchy")!!;val sceneWindow=host("scene")!!
        hierarchyWindow.isAlwaysOnTop=true;hierarchyWindow.toFront();delay(400)
        clickTree(hierarchyWindow,crate)
        check(scene.selection.keys==listOf(crate)) { "Detached Hierarchy click did not select Crate B" }
        val total=scene.entities.size
        key(KeyEvent.VK_D,KeyEvent.VK_CONTROL)
        check(scene.entities.size==total+1 && scene.history.undoLabel=="Duplicate Crate B") { "Ctrl+D in the detached Hierarchy failed: ${scene.history.undoLabel}" }
        key(KeyEvent.VK_DELETE)
        check(scene.entities.size==total) { "Delete in the detached Hierarchy failed" }
        key(KeyEvent.VK_Z,KeyEvent.VK_CONTROL);key(KeyEvent.VK_Z,KeyEvent.VK_CONTROL)
        check(scene.serialize()==edited) { "Ctrl+Z in the detached Hierarchy did not undo both steps" }
        key(KeyEvent.VK_A,KeyEvent.VK_CONTROL)
        check(scene.selection.size==scene.entities.size) { "Ctrl+A in the detached Hierarchy did not select all" }
        capture("walk-5-detached-hierarchy",hierarchyWindow)
        key(KeyEvent.VK_ESCAPE)
        check(scene.selection.isEmpty) { "Esc in the detached Hierarchy did not clear the selection" }
        hierarchyWindow.isAlwaysOnTop=false
        sceneWindow.isAlwaysOnTop=true;sceneWindow.toFront();delay(400)
        await("Detached Scene canvas not ready") { descendants(sceneWindow).filterIsInstance<EngineCanvas>().any { it.viewId==ViewId.Scene && it.authoringReady() } }
        val detached=descendants(sceneWindow).filterIsInstance<EngineCanvas>().single { it.viewId==ViewId.Scene }
        click(detached,spriteCenter(detached,crate))
        check(scene.selection.keys==listOf(crate)) { "Detached Scene click did not select Crate B" }
        for((code,tool) in listOf(KeyEvent.VK_W to ViewportToolId.Move,KeyEvent.VK_E to ViewportToolId.Rotate,KeyEvent.VK_R to ViewportToolId.Scale,KeyEvent.VK_Q to ViewportToolId.Select)) {
            key(code);check(tools.active==tool) { "Key ${KeyEvent.getKeyText(code)} in the detached Scene did not select $tool" }
        }
        val before=detached.previewCamera()
        key(KeyEvent.VK_F);ready(detached)
        check(detached.previewCamera()!=before) { "F in the detached Scene did not frame the selection" }
        capture("walk-5-detached-scene",sceneWindow)
        key(KeyEvent.VK_DELETE);ready(detached)
        check(scene.entity(crate)==null) { "Delete in the detached Scene failed" }
        key(KeyEvent.VK_Z,KeyEvent.VK_CONTROL);ready(detached)
        check(scene.serialize()==edited) { "Ctrl+Z in the detached Scene did not restore Crate B" }
        sceneWindow.isAlwaysOnTop=false
        manager.resetLayout()
        await("Layout did not reset") { manager.hostFor(PanelId("scene"))==manager.layout.mainHost && manager.hostFor(PanelId("hierarchy"))==manager.layout.mainHost }
        window.isAlwaysOnTop=true;window.toFront();delay(500)
        println("L1_WALK_STEP 5 detached-hierarchy ctrl-d/delete/undo/ctrl-a/esc detached-scene click/q-w-e-r/f/delete/undo")
    }

    /** 6. Game focused while playing: W and Delete reach the game; F5, F6 and Shift+F5 still work. */
    suspend fun step6() {
        canvas=sceneCanvas()
        click(canvas,emptySpot(canvas,listOf(player,crate)))
        check(canvas.isFocusOwner && tools.active==ViewportToolId.Select) { "Scene canvas did not take focus before F5" }
        val authored=scene.serialize()
        fun game()=canvases().firstOrNull { it.viewId==ViewId.Game }
        fun playerY(target: EngineCanvas)=target.runtimeTransforms().getValue(player).jsonObject.getValue("position").jsonObject.getValue("y").jsonPrimitive.double
        suspend fun focusGame(target: EngineCanvas) {
            clickScreen(target.locationOnScreen.x+target.width/2,target.locationOnScreen.y+target.height/2)
            await("Game canvas did not take focus") { target.isFocusOwner }
        }
        key(KeyEvent.VK_F5)
        await("F5 did not start Play in the Game view") { game()?.let { it.scenePlayback()==Playback.Running && it.gameViewActive() }==true }
        delay(850) // audio starts after the first presented frame
        val gameCanvas=game()!!
        focusGame(gameCanvas)
        val y0=playerY(gameCanvas)
        robot.keyPress(KeyEvent.VK_W);delay(350);robot.keyRelease(KeyEvent.VK_W);delay(200)
        val y1=playerY(gameCanvas)
        check(y1>y0+10) { "W did not reach the game script: y $y0 -> $y1" }
        check(tools.active==ViewportToolId.Select) { "W switched the Scene tool while the Game had focus" }
        val selection=scene.selection.keys
        key(KeyEvent.VK_DELETE);key(KeyEvent.VK_E)
        check(scene.serialize()==authored && scene.selection.keys==selection && tools.active==ViewportToolId.Select) { "Delete or E acted on the scene while the Game had focus" }
        capture("walk-6-playing")
        key(KeyEvent.VK_F6)
        await("F6 did not pause") { gameCanvas.scenePlayback()==Playback.Paused }
        val frozen=gameCanvas.runtimeTransforms();delay(300)
        check(gameCanvas.runtimeTransforms()==frozen) { "Paused game still moves" }
        check(gameCanvas.isFocusOwner) { "Game canvas lost focus on pause" }
        capture("walk-6-paused")
        key(KeyEvent.VK_F5)
        await("F5 did not resume") { gameCanvas.scenePlayback()==Playback.Running }
        key(KeyEvent.VK_F5,KeyEvent.VK_SHIFT)
        await("Shift+F5 did not stop") { canvases().none { it.scenePlayback()!=Playback.Stopped } }
        check(scene.serialize()==authored) { "Stop changed the authored scene" }
        val stoppedGame=game()
        if(stoppedGame!=null) {
            focusGame(stoppedGame)
            key(KeyEvent.VK_F5)
            await("F5 from the focused stopped Game view did not play") { game()?.scenePlayback()==Playback.Running }
            key(KeyEvent.VK_F5,KeyEvent.VK_SHIFT)
            await("Shift+F5 did not stop the second run") { canvases().none { it.scenePlayback()!=Playback.Stopped } }
        }
        check(scene.serialize()==authored) { "Second run changed the authored scene" }
        println("L1_WALK_STEP 6 f5-play/w-reaches-game dy=${"%.1f".format(y1-y0)}/delete-e-ignored/f6-pause/f5-resume/shift-f5-stop"+(if(stoppedGame!=null) "/f5-from-stopped-game" else ""))
    }

    /**
     * 7. Each theme and density: multi-selection outlines, active row, a notice with Undo, Rotate
     *    handles, and the disabled-reason tooltip of Edit → Edit Entity.
     */
    suspend fun step7() {
        clickWindow(shellBounds["SceneTab"] ?: error("Scene tab not laid out"))
        for(mode in EditorThemeMode.entries) for(density in EditorDensity.entries) appearance(mode,density)
        workspace.appearance.update(EditorAppearance())
        println("L1_WALK_STEP 7 dark/light x compact/comfortable captures+tooltip java2d-scale=$scaleTag")
    }

    suspend fun appearance(mode: EditorThemeMode,density: EditorDensity) {
        val tag="${mode.name.lowercase()}-${density.name.lowercase()}"
        workspace.appearance.update(EditorAppearance(mode,density));delay(700)
        canvas=sceneCanvas()
        click(canvas,spriteCenter(canvas,crate))
        scene.select(created.first());delay(150)
        key(KeyEvent.VK_DELETE);ready(canvas)
        check(workspace.notices.current?.action?.available()==true) { "No Undo notice in $tag: ${workspace.notices.current?.text}" }
        scene.select(player);scene.toggleSelection(crate)
        tools.activate(ViewportToolId.Rotate);delay(500);ready(canvas)
        capture("walk-7-$tag")
        clickWindow(shellBounds["Edit"] ?: error("Edit menu not laid out"))
        await("Edit menu did not open") { popup()!=null }
        val item=popup()!!.components.filterIsInstance<JMenuItem>().single { it.text=="Edit Entity" }
        check(!item.isEnabled && item.toolTipText==sle.editor.ui.SINGLE_ENTITY_REASON) { "Edit Entity is not disabled with its reason in $tag" }
        val at=item.locationOnScreen;robot.mouseMove(at.x+item.width/3,at.y+item.height/2)
        await("Disabled-reason tooltip did not show in $tag") {
            Window.getWindows().filter { it.isShowing }.flatMap(::descendants).filterIsInstance<JToolTip>().any { it.isShowing && it.tipText==sle.editor.ui.SINGLE_ENTITY_REASON }
        }
        delay(150);capture("walk-7-$tag-tooltip")
        MenuSelectionManager.defaultManager().clearSelectedPath();delay(200)
        key(KeyEvent.VK_Z,KeyEvent.VK_CONTROL);ready(canvas)
        check(scene.serialize()==edited) { "Undo after the $tag captures did not restore the scene" }
        tools.activate(ViewportToolId.Select)
    }
}
