package sle.editor.smoke

import sle.editor.model.EntityWorkspaceState
import sle.editor.viewport.EngineCanvas
import sle.editor.viewport.GizmoShape
import sle.editor.viewport.SnapMath
import sle.editor.viewport.SnapService
import sle.editor.viewport.SnapSettings
import sle.editor.viewport.Vec2

import kotlinx.coroutines.delay
import kotlinx.serialization.json.*
import java.awt.Robot
import java.awt.Rectangle
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import java.io.File
import java.nio.file.Path
import javax.imageio.ImageIO
import kotlin.math.*

internal suspend fun runGizmoSmoke(canvas: EngineCanvas, state: EntityWorkspaceState, snap: SnapService) {
    val robot=Robot()
    suspend fun ready() {
        repeat(100) { if (canvas.authoringReady()) { delay(80); return }; delay(30) }
        error("Authoring frame did not catch up")
    }
    fun pixel(world: Vec2) = canvas.previewCamera()!!.screen(world)
    fun move(point: Vec2) { val p=canvas.locationOnScreen; robot.mouseMove(p.x+point.x.roundToInt(),p.y+point.y.roundToInt()) }
    suspend fun key(code: Int, control: Boolean = false) {
        if (control) robot.keyPress(KeyEvent.VK_CONTROL)
        robot.keyPress(code); robot.keyRelease(code)
        if (control) robot.keyRelease(KeyEvent.VK_CONTROL)
        delay(140); ready()
    }
    /** [held] modifiers are pressed after the button, so they act on the drag rather than the click. */
    suspend fun drag(from: Vec2,to: Vec2,cancel: Boolean = false,held: List<Int> = emptyList()) {
        move(pixel(from)); robot.mousePress(InputEvent.BUTTON1_DOWN_MASK); delay(80)
        held.forEach { robot.keyPress(it) }
        for (i in 1..6) { move(pixel(from+(to-from)*(i/6.0))); delay(40) }
        if (cancel) key(KeyEvent.VK_ESCAPE)
        robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK); held.asReversed().forEach { robot.keyRelease(it) }; delay(160); ready()
    }
    // Activate the desktop window before the first drag, including on Windows
    // sessions where the initial click is consumed solely for activation.
    move(Vec2(canvas.width/2.0,canvas.height/2.0))
    robot.mousePress(InputEvent.BUTTON1_DOWN_MASK); robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK)
    delay(250)
    state.setValue("Transform",listOf("rotation"),JsonPrimitive(0.4))
    state.setValue("Transform",listOf("scale"),buildJsonObject { put("x",-2.0); put("y",1.5) })
    state.addComponentAtEngineSize("CircleCollider")
    state.selectedTarget="BoxCollider"
    ready()
    val camera=canvas.previewCamera()!!
    val before=state.document
    val box=GizmoShape.from(before,"BoxCollider")!!
    val image=GizmoShape.from(before,"SpriteRenderer")!!
    move(pixel(image.world(image.offset+Vec2(0.25,0.2))))
    robot.mousePress(InputEvent.BUTTON1_DOWN_MASK); robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK); delay(140)
    check(state.selectedTarget=="SpriteRenderer") { "Transparent collider interior intercepted sprite click" }
    move(pixel(box.world(box.offset+Vec2(box.size.x/2,0.7))))
    robot.mousePress(InputEvent.BUTTON1_DOWN_MASK); robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK); delay(140)
    check(state.selectedTarget=="BoxCollider") { "Collider outline could not be selected" }
    val corner=box.offset+box.size*0.5
    val opposite=box.world(box.offset-box.size*0.5)
    ImageIO.write(robot.createScreenCapture(Rectangle(canvas.locationOnScreen,canvas.size)),"png",File(System.getProperty("sle.smokeReport")).parentFile.resolve("gizmo-before.png"))
    drag(box.world(corner),box.world(corner+Vec2(0.6,0.4)))
    val resized=state.document
    val next=GizmoShape.from(resized,"BoxCollider")!!
    check(next.size.x>box.size.x+0.4 && next.size.y>box.size.y+0.2) { "Box handle did not resize collider: before=${box.size} after=${next.size}" }
    check((next.world(next.offset-next.size*0.5)-opposite).length()<0.05) { "Opposite box corner moved" }
    check(canvas.previewCamera()==camera) { "Dragging changed the camera" }
    key(KeyEvent.VK_Z,true); check(state.document==before) { "Drag did not undo as one operation" }
    key(KeyEvent.VK_Y,true); check(state.document==resized) { "Drag redo failed" }
    val position=next.position
    drag(next.center,next.center+Vec2(0.35,0.2))
    val moved=GizmoShape.from(state.document,"BoxCollider")!!
    check(moved.position==position && (moved.center-next.center).length()>0.25) { "Collider move changed the wrong values" }
    val beforeCancel=state.document
    drag(moved.center,moved.center+Vec2(0.3,0.1),true)
    check(state.document==beforeCancel) { "Escape did not restore drag start" }
    state.selectedTarget="CircleCollider"; delay(100)
    val circle=GizmoShape.from(state.document,"CircleCollider")!!
    drag(circle.center+Vec2(circle.worldRadius,0.0),circle.center+Vec2(circle.worldRadius*1.4,0.0))
    check(abs(GizmoShape.from(state.document,"CircleCollider")!!.radius-circle.radius*1.4)<0.04) { "Circle radius handle failed" }
    state.selectedTarget="SpriteRenderer"; delay(100)
    val sprite=GizmoShape.from(state.document,"SpriteRenderer")!!
    val transformBeforeSprite=state.document.components["Transform"]
    val colliderBeforeSprite=state.document.components["BoxCollider"]
    drag(sprite.world(sprite.offset+sprite.size*0.5),sprite.world(sprite.offset+sprite.size*0.7))
    val scaled=GizmoShape.from(state.document,"SpriteRenderer")!!
    check(scaled.size.x>sprite.size.x && scaled.size.y>sprite.size.y) { "Sprite size handle failed" }
    check(state.document.components["Transform"]==transformBeforeSprite && state.document.components["BoxCollider"]==colliderBeforeSprite) { "Sprite resizing changed collider/transform" }
    // Snapping comes from the per-user SnapService (Configure Snap); Ctrl inverts it for one drag
    // or nudge and Shift keeps a move on its dominant axis.
    state.selectedTarget="Entity"; snap.update(SnapSettings(enabled=true,move=0.1)); delay(100)
    fun onStep(value: Double) = abs(value*10-round(value*10))<1e-6
    drag(scaled.position,scaled.position+Vec2(0.24,0.16))
    val entity=GizmoShape.from(state.document,"Entity")!!
    check(onStep(entity.position.x) && onStep(entity.position.y)) { "Move did not snap: ${entity.position}" }
    val snappedSteps=state.history.undoLabels.size
    drag(entity.position,entity.position+Vec2(0.37,0.05),held=listOf(KeyEvent.VK_CONTROL,KeyEvent.VK_SHIFT))
    val free=GizmoShape.from(state.document,"Entity")!!.position
    check(abs(free.y-entity.position.y)<1e-9 && free.x>entity.position.x+0.2) { "Shift did not keep the drag on X: ${entity.position} -> $free" }
    check(!onStep(free.x)) { "Ctrl did not turn snapping off for the drag: $free" }
    check(state.history.undoLabels.size==snappedSteps+1) { "A drag was not one undo step" }
    key(KeyEvent.VK_RIGHT)
    val nudged=GizmoShape.from(state.document,"Entity")!!.position
    check(abs(nudged.x-SnapMath.next(free.x,0.1,1))<1e-9 && nudged.y==free.y) { "Snapped nudge did not reach the next step: $free -> $nudged" }
    snap.update(SnapSettings(enabled=false,move=0.1))
    key(KeyEvent.VK_RIGHT)
    check(abs(GizmoShape.from(state.document,"Entity")!!.position.x-(nudged.x+0.1))<1e-9) { "Keyboard nudge failed" }
    val persisted=state.document
    val path=Path.of(System.getProperty("sle.smokeReport")).resolveSibling("gizmo-edited.sleprefab")
    state.save(path); state.newDocument(); state.open(path); ready()
    check(!state.canUndo && !state.canRedo && !state.dirty)
    for (component in listOf("Transform","BoxCollider","CircleCollider")) check(state.document.components[component]==persisted.components[component])
    val restoredSprite=state.document.components.getValue("SpriteRenderer").jsonObject
    val persistedSprite=persisted.components.getValue("SpriteRenderer").jsonObject
    check(restoredSprite["size"]==persistedSprite["size"] && restoredSprite["offset"]==persistedSprite["offset"])
    val desktop=robot.createScreenCapture(Rectangle(canvas.locationOnScreen,canvas.size))
    var handles=0
    for(y in 0 until desktop.height) for(x in 0 until desktop.width) {
        val color=desktop.getRGB(x,y)
        val expected=sle.editor.ui.theme.editorColors(sle.editor.ui.theme.EditorThemeMode.Dark).selectionOutline
        if(abs(((color shr 16) and 255)-expected.red*255)<3 &&
            abs(((color shr 8) and 255)-expected.green*255)<3 && abs((color and 255)-expected.blue*255)<3) ++handles
    }
    check(handles>30) { "Editor handles missing from desktop: $handles" }
    val report=File(System.getProperty("sle.smokeReport")).parentFile.resolve("gizmo-editor.png")
    ImageIO.write(desktop,"png",report)
    println("VIEWPORT_GIZMO_SMOKE_PASS picking/box/circle/independent-sprite/move/snap/ctrl-invert/shift-axis/step-nudge/nudge/undo/redo/cancel/save/open handles=$handles image=$report")
}

/** Fixtures built for engine-sized shapes: add a component without the editor's practical
 * creation sizes (64×64 / radius 32). */
internal fun EntityWorkspaceState.addComponentAtEngineSize(type: String) {
    addComponent(type)
    // The catalog may omit size entirely (the engine then uses its own default), so replace the
    // whole new component with the native defaults rather than patching individual fields.
    document=document.copy(components=JsonObject(document.components+(type to catalog.getValue(type).defaults)))
}

/**
 * L1 session 6 (Scene view): E Rotate and R Scale by Robot with the native canvas focused. A
 * snapped ring drag and an axis drag are one undo step each; Shift makes a scale drag uniform;
 * Esc cancels; a two-entity ring drag around the selection center orbits both as one step.
 */
internal suspend fun runTransformToolsSmoke(canvas: EngineCanvas, scene: sle.editor.model.SceneWorkspaceState,
    tools: sle.editor.viewport.tools.ViewportToolState, snap: SnapService) {
    val robot=Robot()
    val folder=Path.of(System.getProperty("sle.smokeReport")).parent
    suspend fun ready() {
        repeat(120) { if(canvas.authoringReady()) { delay(100);return };delay(30) }
        error("Scene preview did not catch up")
    }
    fun cursor(pixel: Vec2) { val origin=canvas.locationOnScreen;robot.mouseMove(origin.x+pixel.x.roundToInt(),origin.y+pixel.y.roundToInt()) }
    suspend fun key(code: Int,ctrl: Boolean=false) {
        if(ctrl) robot.keyPress(KeyEvent.VK_CONTROL)
        robot.keyPress(code);robot.keyRelease(code)
        if(ctrl) robot.keyRelease(KeyEvent.VK_CONTROL)
        delay(160);ready()
    }
    suspend fun click(pixel: Vec2,ctrl: Boolean=false) {
        cursor(pixel)
        if(ctrl) robot.keyPress(KeyEvent.VK_CONTROL)
        robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK)
        if(ctrl) robot.keyRelease(KeyEvent.VK_CONTROL)
        delay(160);ready()
    }
    /** Presses [from], follows [path] and releases; [held] modifiers go down after the press; [cancel] presses Esc first. */
    suspend fun drag(from: Vec2,path: List<Vec2>,held: List<Int> = emptyList(),cancel: Boolean=false) {
        cursor(from);robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);delay(80)
        held.forEach { robot.keyPress(it) }
        path.forEach { cursor(it);delay(35) }
        if(cancel) { robot.keyPress(KeyEvent.VK_ESCAPE);robot.keyRelease(KeyEvent.VK_ESCAPE);delay(120) }
        robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);held.asReversed().forEach { robot.keyRelease(it) };delay(150);ready()
    }
    /** A ring drag turning [degrees] counter-clockwise from the ring's right-hand point. */
    suspend fun turn(degrees: Double) {
        val ring=canvas.toolHandles().singleOrNull { it.kind==sle.editor.viewport.tools.ToolHandleKind.Ring } ?: error("Rotate ring missing: ${canvas.toolHandles()}")
        val radius=(ring.to-ring.from).length()
        drag(ring.to,(1..12).map { i -> Math.toRadians(degrees*i/12).let { ring.from+Vec2(cos(it),-sin(it))*radius } })
    }
    fun pose(key: String)=sle.editor.model.ScenePose.from(scene.worldDocument(key))
    fun scaleHandle(axis: sle.editor.viewport.DragAxis)=canvas.toolHandles().firstOrNull { it.kind==sle.editor.viewport.tools.ToolHandleKind.Scale && it.axis==axis }
        ?: error("Scale handle $axis missing: ${canvas.toolHandles()}")
    fun onStep(value: Double,step: Double)=abs(value/step-Math.rint(value/step))<1e-9
    ready()
    val crate=scene.entities.first { it.document.name=="Crate A" }.document.key
    val other=scene.entities.first { it.document.name=="Crate B" }.document.key
    val camera=canvas.previewCamera()!!
    fun spriteCenter(key: String)=GizmoShape.from(scene.worldDocument(key),"SpriteRenderer")!!.center.let(camera::screen)
    // A click on Crate A selects it and gives the native canvas keyboard focus.
    click(spriteCenter(crate))
    check(scene.selection.keys==listOf(crate)) { "Click did not select Crate A: ${scene.selection.keys}" }
    snap.update(SnapSettings(enabled=true,move=8.0,rotateDegrees=15.0,scale=0.1));delay(120);ready()
    key(KeyEvent.VK_E)
    check(tools.active==sle.editor.viewport.tools.ViewportToolId.Rotate) { "E did not switch to the Rotate tool with the canvas focused" }
    val start=pose(crate);val sprite=scene.entity(crate)!!.document.components["SpriteRenderer"]
    var steps=scene.history.undoLabels.size
    turn(47.0) // snaps to 45°
    val turned=pose(crate)
    check(abs(turned.rotation-(start.rotation+PI/4))<1e-9) { "Ring drag did not snap to 45°: ${Math.toDegrees(turned.rotation)}°" }
    check((turned.position-start.position).length()<1e-6) { "Rotate moved its own pivot: ${start.position} -> ${turned.position}" }
    check(scene.history.undoLabels.size==steps+1 && scene.history.undoLabel=="Rotate Crate A") { "Ring drag was not one step: ${scene.history.undoLabels.takeLast(2)}" }
    ImageIO.write(robot.createScreenCapture(Rectangle(canvas.locationOnScreen,canvas.size)),"png",folder.resolve("gizmo-rotate-tool.png").toFile())
    key(KeyEvent.VK_Z,ctrl=true)
    check(pose(crate)==start) { "Undo did not restore the rotation: ${pose(crate)} vs $start" }
    // R: the X handle scales along X only, snapped, without touching the sprite size.
    key(KeyEvent.VK_R)
    check(tools.active==sle.editor.viewport.tools.ViewportToolId.Scale) { "R did not switch to the Scale tool" }
    steps=scene.history.undoLabels.size
    val x=scaleHandle(sle.editor.viewport.DragAxis.X);val grab=(x.from+x.to)*0.5
    drag(grab,(1..6).map { grab+Vec2(22.0*it/6,0.0) })
    val stretched=pose(crate)
    check(stretched.scale.y==start.scale.y && stretched.scale.x>start.scale.x && onStep(stretched.scale.x,0.1)) { "X handle did not scale X only, snapped: ${start.scale} -> ${stretched.scale}" }
    check(scene.entity(crate)!!.document.components["SpriteRenderer"]==sprite) { "Scaling changed the sprite size" }
    check(scene.history.undoLabels.size==steps+1 && scene.history.undoLabel=="Scale Crate A") { "Scale drag was not one step: ${scene.history.undoLabels.takeLast(2)}" }
    // Shift held during a Y drag scales uniformly.
    val y=scaleHandle(sle.editor.viewport.DragAxis.Y);val yGrab=(y.from+y.to)*0.5
    drag(yGrab,(1..6).map { yGrab+Vec2(0.0,-20.0*it/6) },held=listOf(KeyEvent.VK_SHIFT))
    val uniform=pose(crate)
    check(uniform.scale.x>stretched.scale.x && uniform.scale.y>stretched.scale.y && onStep(uniform.scale.x,0.1) && onStep(uniform.scale.y,0.1)) {
        "Shift did not scale uniformly: ${stretched.scale} -> ${uniform.scale}" }
    ImageIO.write(robot.createScreenCapture(Rectangle(canvas.locationOnScreen,canvas.size)),"png",folder.resolve("gizmo-scale-tool.png").toFile())
    // Esc mid-drag restores the scale and records nothing.
    val beforeCancel=scene.history.undoLabels.size
    val center=scaleHandle(sle.editor.viewport.DragAxis.Free).let { (it.from+it.to)*0.5 }
    drag(center,(1..6).map { center+Vec2(5.0*it,-5.0*it) },cancel=true)
    check(pose(crate)==uniform && scene.history.undoLabels.size==beforeCancel) { "Esc did not cancel the scale drag" }
    key(KeyEvent.VK_Z,ctrl=true);key(KeyEvent.VK_Z,ctrl=true)
    check(pose(crate)==start) { "Undo did not restore the scale: ${pose(crate)} vs $start" }
    // Two entities around the selection center: both turn 90° (snapped) and orbit the center, as one step.
    click(spriteCenter(other),ctrl=true)
    check(scene.selection.keys.toSet()==setOf(crate,other) && scene.selectedKey==other) { "Ctrl-click did not add Crate B: ${scene.selection.keys}" }
    tools.pivot=sle.editor.viewport.tools.PivotMode.Center;delay(120)
    key(KeyEvent.VK_E)
    val pivot=sle.editor.viewport.tools.transformFrame(scene,sle.editor.viewport.tools.PivotMode.Center)!!.pivot
    val before=mapOf(crate to pose(crate),other to pose(other))
    steps=scene.history.undoLabels.size
    turn(88.0) // snaps to 90°
    for((key,was) in before) {
        val now=pose(key);val expected=pivot+(was.position-pivot).rotate(PI/2)
        check(abs(now.rotation-(was.rotation+PI/2))<1e-9 && (now.position-expected).length()<1e-6) { "$key did not orbit the center: $was -> $now (expected $expected)" }
    }
    check(scene.history.undoLabels.size==steps+1 && scene.history.undoLabel=="Rotate 2 entities") { "Multi rotate was not one step: ${scene.history.undoLabels.takeLast(2)}" }
    ImageIO.write(robot.createScreenCapture(Rectangle(canvas.locationOnScreen,canvas.size)),"png",folder.resolve("gizmo-rotate-center.png").toFile())
    key(KeyEvent.VK_Z,ctrl=true)
    check(before.all { (key,was) -> pose(key)==was }) { "Undo did not restore both entities" }
    tools.pivot=sle.editor.viewport.tools.PivotMode.Pivot
    snap.update(SnapSettings())
    println("VIEWPORT_TRANSFORM_TOOLS_SMOKE_PASS e-key/rotate-snap/one-step/undo/r-key/scale-axis-snap/sprite-size-kept/shift-uniform/esc-cancel/center-orbit/multi-one-step folder=$folder")
}
