package sle.editor.smoke

import sle.editor.model.SceneWorkspaceState
import sle.editor.viewport.EngineCanvas
import sle.editor.viewport.GizmoShape
import sle.editor.viewport.Vec2

import kotlinx.coroutines.delay
import kotlinx.serialization.json.*
import java.awt.Rectangle
import java.awt.Robot
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import javax.imageio.ImageIO
import kotlin.math.roundToInt

internal suspend fun runSceneSmoke(canvas: EngineCanvas,scene: SceneWorkspaceState,
    selectTreeNode: (suspend (String)->Unit)? = null) {
    val robot=Robot()
    val folder=Path.of(System.getProperty("sle.smokeReport")).parent
    suspend fun ready() {
        repeat(120) { if(canvas.authoringReady()) { delay(100);return };delay(30) }
        error("Scene preview did not catch up")
    }
    fun point(world: Vec2): Vec2 = canvas.previewCamera()!!.screen(world)
    fun cursor(pixel: Vec2) { val origin=canvas.locationOnScreen;robot.mouseMove(origin.x+pixel.x.roundToInt(),origin.y+pixel.y.roundToInt()) }
    suspend fun click(world: Vec2) {
        cursor(point(world));robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);delay(160)
    }
    suspend fun drag(start: Vec2,end: Vec2) {
        cursor(point(start));robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);delay(80)
        for(i in 1..6) { cursor(point(start+(end-start)*(i/6.0)));delay(35) }
        robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);delay(150);ready()
    }
    ready();cursor(Vec2(canvas.width/2.0,canvas.height/2.0))
    robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);delay(200)
    val floor=scene.entities.first { it.document.name=="Ground" }
    val crate=scene.entities.first { it.document.name=="Crate A" }
    val other=scene.entities.first { it.document.name=="Crate B" }
    val floorBefore=floor.document
    val otherBefore=other.document
    val shape=GizmoShape.from(crate.document,"SpriteRenderer")!!
    selectTreeNode?.invoke(other.document.key)
    check(selectTreeNode==null || scene.selectedKey==other.document.key) { "Tree node did not select its entity" }
    click(shape.center)
    check(scene.selectedKey==crate.document.key) { "Viewport click did not select another entity" }
    crate.selectedTarget="SpriteRenderer";delay(120)
    val camera=canvas.previewCamera()!!
    val before=crate.document
    drag(shape.position,shape.position+Vec2(25.0,15.0))
    check(crate.document!=before && floor.document==floorBefore && other.document==otherBefore) { "Scene drag edited the wrong entity" }
    check(crate.document.components["SpriteRenderer"]==before.components["SpriteRenderer"] &&
        crate.document.components["BoxCollider"]==before.components["BoxCollider"]) { "Scene drag changed a component instead of the entity" }
    check(crate.selectedTarget=="SpriteRenderer") { "Scene selection overwrote the component editor selection" }
    check(canvas.previewCamera()==camera) { "Scene edit moved camera" }
    robot.keyPress(KeyEvent.VK_CONTROL);robot.keyPress(KeyEvent.VK_Z);robot.keyRelease(KeyEvent.VK_Z);robot.keyRelease(KeyEvent.VK_CONTROL);delay(160);ready()
    check(crate.document==before) { "Selected scene entity drag did not undo" }
    crate.redo();ready()
    click(GizmoShape.from(other.document,"SpriteRenderer")!!.center)
    check(scene.selectedKey==other.document.key)
    val count=scene.entities.size
    scene.duplicateSelected();val duplicate=scene.selected!!.document.key
    check(scene.entities.size==count+1 && duplicate!=other.document.key)
    scene.undoStructure();check(scene.entities.size==count)
    scene.redoStructure();check(scene.selectedKey==duplicate)
    scene.removeSelected();check(scene.entities.size==count)
    val target=folder.resolve("edited-level.slescene")
    scene.save(target);check(!scene.dirty)
    val bytes=Files.readAllBytes(target)
    floor.editField("Transform",listOf("position","x"),JsonPrimitive(0.0),"-")
    check(runCatching{scene.save(target)}.isFailure && Files.readAllBytes(target).contentEquals(bytes)) { "Hidden invalid draft was saved" }
    floor.undo()
    val expected=scene.document()
    val moved=folder.resolve("relocated-level.slescene")
    Files.copy(target,moved,REPLACE_EXISTING)
    scene.newScene();scene.open(moved);ready()
    check(scene.entities.size==count && !scene.dirty && !scene.canUndoStructure)
    check(scene.document().entities.map{it.key}==expected.entities.map{it.key})
    check(scene.entities.first { it.document.key==crate.document.key }.document.components["Transform"]==crate.document.components["Transform"])
    val desktop=robot.createScreenCapture(Rectangle(canvas.locationOnScreen,canvas.size))
    var textured=0;var cyan=0
    for(y in 0 until desktop.height) for(x in 0 until desktop.width) {
        val p=desktop.getRGB(x,y);val r=(p shr 16) and 255;val g=(p shr 8) and 255;val b=p and 255
        if(r>110 && g>90 && b>60 && r>g && g>b) ++textured
        if(r<100 && g>150 && b>150) ++cyan
    }
    check(textured>100 && cyan>100) { "Scene assets/colliders missing from actual desktop: textured=$textured cyan=$cyan" }
    ImageIO.write(desktop,"png",folder.resolve("scene-editor.png").toFile())
    // Deliver a runnable example without overwriting any user-created scene.
    val example=Path.of("assets/scenes/editor_starter.slescene")
    if(!Files.exists(example)) Files.copy(moved,example)
    val prefab=Path.of("assets/prefabs/editor_crate.sleprefab")
    Files.createDirectories(prefab.parent)
    if(!Files.exists(prefab)) Files.copy(folder.resolve("scene-crate.sleprefab"),prefab)
    val plain=Path.of("assets/scenes/editor_starter.json")
    if(!Files.exists(plain)) Files.writeString(plain,scene.document().copy(entities=scene.document().entities.map {
        it.setValue("SpriteRenderer",listOf("texture"),JsonPrimitive("assets/textures/tile2.png"))
    }).serialize())
    println("SCENE_EDITOR_SMOKE_PASS entities=$count import-copy/select/move/undo/structure/invalid-draft/save/relocate/open textured=$textured cyan=$cyan package=$moved")
}
