package sle.editor.smoke

import sle.editor.model.EntityWorkspaceState
import sle.editor.viewport.EngineCanvas
import sle.editor.viewport.GizmoShape
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

internal suspend fun runGizmoSmoke(canvas: EngineCanvas, state: EntityWorkspaceState) {
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
    suspend fun drag(from: Vec2,to: Vec2,cancel: Boolean = false) {
        move(pixel(from)); robot.mousePress(InputEvent.BUTTON1_DOWN_MASK); delay(80)
        for (i in 1..6) { move(pixel(from+(to-from)*(i/6.0))); delay(40) }
        if (cancel) key(KeyEvent.VK_ESCAPE)
        robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK); delay(160); ready()
    }
    // Activate the desktop window before the first drag, including on Windows
    // sessions where the initial click is consumed solely for activation.
    move(Vec2(canvas.width/2.0,canvas.height/2.0))
    robot.mousePress(InputEvent.BUTTON1_DOWN_MASK); robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK)
    delay(250)
    state.setValue("Transform",listOf("rotation"),JsonPrimitive(0.4))
    state.setValue("Transform",listOf("scale"),buildJsonObject { put("x",-2.0); put("y",1.5) })
    state.addComponent("CircleCollider")
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
    state.selectedTarget="Entity"; state.snap=true; delay(100)
    drag(scaled.position,scaled.position+Vec2(0.24,0.16))
    val entity=GizmoShape.from(state.document,"Entity")!!
    check(abs(entity.position.x*10-round(entity.position.x*10))<1e-6) { "Move did not snap" }
    key(KeyEvent.VK_RIGHT)
    check(GizmoShape.from(state.document,"Entity")!!.position.x>entity.position.x+0.09) { "Keyboard nudge failed" }
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
        if (((color shr 16) and 255)>230 && ((color shr 8) and 255) in 100..180 && (color and 255)>220) ++handles
    }
    check(handles>30) { "Editor handles missing from desktop: $handles" }
    val report=File(System.getProperty("sle.smokeReport")).parentFile.resolve("gizmo-editor.png")
    ImageIO.write(desktop,"png",report)
    println("VIEWPORT_GIZMO_SMOKE_PASS picking/box/circle/independent-sprite/move/snap/nudge/undo/redo/cancel/save/open handles=$handles image=$report")
}
