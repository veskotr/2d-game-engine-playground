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
    notices: sle.editor.ui.EditorNotices? = null,selectTreeNode: (suspend (String)->Unit)? = null) {
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
    // L1 S3 multi-select: Ctrl-click in the viewport, a multi-move and a multi-delete, each one undo step.
    suspend fun key(code: Int,ctrl: Boolean=false) {
        if(ctrl) robot.keyPress(KeyEvent.VK_CONTROL)
        robot.keyPress(code);robot.keyRelease(code)
        if(ctrl) robot.keyRelease(KeyEvent.VK_CONTROL)
        delay(160)
    }
    fun spriteCenter(state: sle.editor.model.EntityWorkspaceState)=GizmoShape.from(state.document,"SpriteRenderer")!!.center
    fun worldPosition(state: sle.editor.model.EntityWorkspaceState)=sle.editor.model.ScenePose.from(state.document).position
    val pair=setOf(crate.document.key,other.document.key)
    click(spriteCenter(crate))
    check(scene.selection.keys==listOf(crate.document.key)) { "Viewport click did not select Crate A alone" }
    robot.keyPress(KeyEvent.VK_CONTROL);click(spriteCenter(other));robot.keyRelease(KeyEvent.VK_CONTROL);delay(120)
    check(scene.selection.keys.toSet()==pair && scene.selectedKey==other.document.key) { "Ctrl-click did not add Crate B: ${scene.selection.keys}" }
    ImageIO.write(robot.createScreenCapture(Rectangle(canvas.locationOnScreen,canvas.size)),"png",folder.resolve("scene-multi-select.png").toFile())
    val crateStart=worldPosition(crate);val otherStart=worldPosition(other);val movedSteps=scene.history.undoLabels.size
    drag(spriteCenter(other),spriteCenter(other)+Vec2(20.0,10.0))
    val crateDelta=worldPosition(crate)-crateStart;val otherDelta=worldPosition(other)-otherStart
    check(otherDelta.length()>5 && (crateDelta-otherDelta).length()<1e-6) { "Multi-move did not move both: crate=$crateDelta other=$otherDelta" }
    check(floor.document==floorBefore) { "Multi-move edited an unselected entity" }
    check(scene.history.undoLabels.size==movedSteps+1 && scene.history.undoLabel=="Move 2 entities") { "Multi-move was not one step: ${scene.history.undoLabels.takeLast(3)}" }
    key(KeyEvent.VK_Z,ctrl=true);ready()
    check(worldPosition(crate)==crateStart && worldPosition(other)==otherStart && scene.selection.keys.toSet()==pair) { "Multi-move undo did not restore both entities and the selection" }
    val beforeDelete=scene.entities.size
    key(KeyEvent.VK_DELETE);ready()
    check(scene.entities.size==beforeDelete-2 && scene.selection.isEmpty) { "Delete did not remove the selection: ${scene.entities.size}" }
    // The Delete key reports "Deleted 2 entities" with an Undo button in the status bar.
    val deleted=notices?.current
    if(notices!=null) check(deleted?.text=="Deleted 2 entities" && deleted.action?.available()==true) { "Delete notice missing: ${deleted?.text}" }
    key(KeyEvent.VK_Z,ctrl=true);ready()
    check(scene.entities.size==beforeDelete && scene.selection.keys.toSet()==pair && scene.selectedKey==other.document.key) { "Multi-delete undo did not restore the set" }
    check(deleted?.action?.available()!=true) { "The delete notice still offers Undo after Ctrl+Z" }
    if(selectTreeNode!=null) {
        // Hierarchy: Ctrl-click adds a row, Ctrl+A selects everything and Esc clears.
        robot.keyPress(KeyEvent.VK_CONTROL);selectTreeNode(floor.document.key);robot.keyRelease(KeyEvent.VK_CONTROL);delay(120)
        check(scene.selection.keys.toSet()==pair+floor.document.key && scene.selectedKey==floor.document.key) { "Hierarchy Ctrl-click did not add Ground: ${scene.selection.keys}" }
        key(KeyEvent.VK_A,ctrl=true)
        check(scene.selection.size==scene.entities.size) { "Ctrl+A did not select every entity: ${scene.selection.size}/${scene.entities.size}" }
        key(KeyEvent.VK_ESCAPE)
        check(scene.selection.isEmpty) { "Esc did not clear the Hierarchy selection" }
    }
    println("SCENE_MULTI_SELECT_SMOKE_PASS ctrl-click/multi-move/multi-delete/undo"+(if(notices!=null) "/delete-notice" else "")+(if(selectTreeNode!=null) "/hierarchy-ctrl-click/ctrl-a/escape" else ""))
    click(GizmoShape.from(other.document,"SpriteRenderer")!!.center)
    check(scene.selectedKey==other.document.key && scene.selection.size==1) { "Plain click did not reduce the selection: ${scene.selection.keys}" }
    val count=scene.entities.size
    scene.duplicateSelected();val duplicate=scene.selected!!.document.key
    check(scene.entities.size==count+1 && duplicate!=other.document.key)
    scene.undo();check(scene.entities.size==count)
    scene.redo();check(scene.selectedKey==duplicate)
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
    check(scene.entities.size==count && !scene.dirty && !scene.canUndo)
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
