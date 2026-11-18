package sle.editor.viewport.tools

import sle.editor.command.CommandContext
import sle.editor.command.KeyChord
import sle.editor.command.ToolCommands
import sle.editor.command.standardKeymap
import sle.editor.model.ComponentDefinition
import sle.editor.model.ScenePlaybackState
import sle.editor.model.ScenePose
import sle.editor.model.SceneWorkspaceState
import sle.editor.model.withPose
import sle.editor.viewport.*

import kotlinx.serialization.json.*
import java.awt.Canvas
import java.awt.event.KeyEvent
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.*

/** L1 session 6: the Rotate (E) and Scale (R) tools, their pivot and their snapping. */
class TransformToolsTest {
    private val catalog=mapOf(
        "Transform" to ComponentDefinition(Json.parseToJsonElement("""{"position":{"x":0,"y":0},"rotation":0,"scale":{"x":1,"y":1}}""").jsonObject,JsonObject(emptyMap())),
    )
    /** 20 pixels per world unit; screen Y points down. */
    private val camera=PreviewCamera(Vec2(-10.0,-10.0),Vec2(20.0,20.0),400,400)
    private fun scene()=SceneWorkspaceState(catalog) { it }
    private fun SceneWorkspaceState.sprite(name: String,at: Vec2,size: Double=2.0): String {
        addEntity(at);val state=selected!!;state.rename(name)
        val sprite=buildJsonObject { put("size",buildJsonObject { put("x",size);put("y",size) });put("offset",buildJsonObject { put("x",0);put("y",0) }) }
        state.document=state.document.copy(components=JsonObject(state.document.components+("SpriteRenderer" to sprite)))
        return state.document.key
    }
    private fun world(scene: SceneWorkspaceState,key: String)=ScenePose.from(scene.worldDocument(key))
    private fun local(scene: SceneWorkspaceState,key: String)=ScenePose.from(scene.entity(key)!!.document)
    private class Rig(val scene: SceneWorkspaceState,camera: PreviewCamera,snap: SnapService=SnapService(),tool: ViewportToolId=ViewportToolId.Rotate) {
        val state=ViewportToolState(tool)
        var gizmos: ViewportGizmos?=null
        val host=ToolHost(state,snap).also { host ->
            host.scene=scene;host.camera=camera
            host.gizmos={ gizmos }
            host.selectionChanged={ gizmos=scene.selected?.let { ViewportGizmos(it,true,scene,snap) } }
            host.selectionChanged()
        }
        fun handle(axis: DragAxis)=host.handles().first { it.axis==axis }
    }
    private fun near(expected: Double,actual: Double,message: String)=assertEquals(expected,actual,1e-9,message)
    private fun near(expected: Vec2,actual: Vec2,message: String)=
        assertTrue((expected-actual).length()<1e-9,"$message: expected $expected, was $actual")
    private fun escape()=KeyEvent(Canvas(),KeyEvent.KEY_PRESSED,0,0,KeyEvent.VK_ESCAPE,KeyEvent.CHAR_UNDEFINED)

    /** Presses the ring at screen angle 0 and turns it [degrees] counter-clockwise in small steps. */
    private fun turn(rig: Rig,degrees: Double,ctrl: Boolean=false,release: Boolean=true) {
        val ring=rig.host.handles().single()
        assertEquals(ToolHandleKind.Ring,ring.kind)
        val center=ring.from;val radius=(ring.to-ring.from).length()
        fun at(angle: Double)=ToolPointer(center+Vec2(cos(Math.toRadians(angle)),-sin(Math.toRadians(angle)))*radius,ctrl=ctrl)
        rig.host.press(at(0.0))
        val steps=(abs(degrees)/10).toInt().coerceAtLeast(1)
        for(i in 1..steps) rig.host.drag(at(degrees*i/steps))
        if(release) rig.host.release(at(degrees))
    }
    /** Drags the Scale [axis] handle by [pixels] along its own screen direction (centre box: right and up). */
    private fun stretch(rig: Rig,axis: DragAxis,pixels: Double,shift: Boolean=false,ctrl: Boolean=false) {
        val handle=rig.handle(axis)
        val grab=(handle.from+handle.to)*0.5
        val direction=if(axis==DragAxis.Free) Vec2(1.0,-1.0)*(1.0/Math.sqrt(2.0)) else (handle.to-handle.from).let { it*(1.0/it.length()) }
        rig.host.press(ToolPointer(grab))
        for(i in 1..4) rig.host.drag(ToolPointer(grab+direction*(pixels*i/4),shift=shift,ctrl=ctrl))
        rig.host.release(ToolPointer(grab+direction*pixels,shift=shift,ctrl=ctrl))
    }

    @Test fun scaleMathNeverCrossesZeroAndKeepsMirroring() {
        assertEquals(-3.0,TransformMath.scaleValue(-2.0,1.5,null),"a mirrored scale grows away from zero")
        assertEquals(-TransformMath.MIN_SCALE,TransformMath.scaleValue(-2.0,-0.5,null),"dragging past the pivot stops at the minimum")
        assertEquals(TransformMath.MIN_SCALE,TransformMath.scaleValue(0.5,0.0,null))
        assertEquals(0.1,TransformMath.scaleValue(2.0,0.01,0.1),"a snapped scale stops at one step, never 0")
        assertEquals(-0.1,TransformMath.scaleValue(-2.0,-3.0,0.1))
        assertEquals(2.6,TransformMath.scaleValue(2.0,1.3125,0.1))
        assertEquals(-1.3,TransformMath.scaleValue(-1.0,1.26,0.1))
        assertEquals(0.0,TransformMath.scaleValue(0.0,3.0,0.1),"a zero scale has no direction to keep")
        val scaled=TransformMath.scaleAround(ScenePose(Vec2(3.0,1.0),0.2,Vec2(-1.0,2.0)),Vec2(1.0,1.0),0.0,Vec2(2.0,1.0))
        near(Vec2(5.0,1.0),scaled.position,"the offset scales from the pivot")
        assertEquals(Vec2(-2.0,2.0),scaled.scale);assertEquals(0.2,scaled.rotation)
        // In a turned frame the offset scales along the frame's axes.
        val turned=TransformMath.scaleAround(ScenePose(Vec2(0.0,2.0)),Vec2(0.0,0.0),PI/2,Vec2(3.0,1.0))
        near(Vec2(0.0,6.0),turned.position,"frame X is world Y here")
        near(PI/4,TransformMath.rotationTarget(0.1,0.7,15.0),"45° is the nearest 15° step to 45.8°")
        near(0.8,TransformMath.rotationTarget(0.1,0.7,null),"free rotation")
        near(-PI/2,TransformMath.wrap(3*PI/2),"wrap")
    }

    @Test fun rotateSnapsTheWorldAngleOfAChildOfARotatedMirroredParent() {
        val scene=scene()
        scene.addEntity(Vec2(2.0,1.0));val parent=scene.selectedKey!!
        scene.entity(parent)!!.let { it.document=it.document.withPose(ScenePose(Vec2(2.0,1.0),Math.toRadians(30.0),Vec2(2.0,-1.0))) }
        scene.addChild(Vec2(4.0,3.0));val child=scene.selectedKey!!
        val start=world(scene,child)
        near(Math.toRadians(30.0),start.rotation,"the child inherits the parent's angle")
        val rig=Rig(scene,camera,SnapService(SnapSettings(enabled=true,rotateDegrees=15.0)))
        assertEquals(world(scene,child).position.let(camera::screen),rig.host.handles().single().from,"the ring sits on the child's pivot")
        val steps=scene.history.undoLabels.size
        turn(rig,40.0) // 30° + 40° = 70° in the world, which snaps to 75°
        near(Math.toRadians(75.0),world(scene,child).rotation,"world angle snapped")
        near(Math.toRadians(45.0),local(scene,child).rotation,"local angle = world − parent (radians)")
        near(start.position,world(scene,child).position,"a single entity turns on its own pivot")
        assertEquals(steps+1,scene.history.undoLabels.size);assertEquals("Rotate ${scene.subjects(listOf(child))}",scene.history.undoLabel)
        // Ctrl inverts snapping for the drag.
        turn(rig,7.0,ctrl=true)
        near(Math.toRadians(82.0),world(scene,child).rotation,"Ctrl rotates freely")
        scene.undo();scene.undo()
        near(start.rotation,world(scene,child).rotation,"undo restores the angle")
    }

    @Test fun scaleSnapsTheWorldScaleOfAChildOfARotatedScaledParent() {
        val scene=scene()
        scene.addEntity(Vec2(0.0,0.0));val parent=scene.selectedKey!!
        scene.entity(parent)!!.let { it.document=it.document.withPose(ScenePose(Vec2(0.0,0.0),Math.toRadians(90.0),Vec2(2.0,-1.0))) }
        scene.addChild(Vec2(-1.0,3.0));val child=scene.selectedKey!!
        assertEquals(Vec2(2.0,-1.0),world(scene,child).scale)
        val rig=Rig(scene,camera,SnapService(SnapSettings(enabled=true,scale=0.1)),ViewportToolId.Scale)
        // Handles follow the child's world rotation (90°): its X axis points up the screen.
        val x=rig.handle(DragAxis.X)
        near(Vec2(0.0,-1.0),(x.to-x.from).let { it*(1.0/it.length()) },"X handle direction")
        val steps=scene.history.undoLabels.size
        stretch(rig,DragAxis.X,20.0) // factor 1.3125: 2 × 1.3125 = 2.625 snaps to 2.6
        assertEquals(Vec2(2.6,-1.0),world(scene,child).scale,"world X snapped; Y untouched")
        near(1.3,local(scene,child).scale.x,"local = world ÷ parent scale");near(1.0,local(scene,child).scale.y,"local Y")
        assertEquals(steps+1,scene.history.undoLabels.size);assertEquals("Scale ${scene.subjects(listOf(child))}",scene.history.undoLabel)
        // Shift makes an axis drag uniform; the mirrored Y keeps its sign.
        stretch(rig,DragAxis.Y,32.0,shift=true) // factor 1.5
        assertEquals(Vec2(3.9,-1.5),world(scene,child).scale)
        // Dragging far back past the pivot stops at one step; the sign never flips.
        stretch(rig,DragAxis.Free,-400.0)
        assertEquals(Vec2(0.1,-0.1),world(scene,child).scale)
        scene.undo();scene.undo();scene.undo()
        assertEquals(Vec2(2.0,-1.0),world(scene,child).scale)
    }

    @Test fun freeScaleClampsToTheMinimumAndZeroScaleHasNoHandles() {
        val scene=scene();val a=scene.sprite("A",Vec2(0.0,0.0))
        scene.entity(a)!!.let { it.document=it.document.withPose(ScenePose(Vec2(0.0,0.0),0.0,Vec2(-1.0,1.0))) }
        val rig=Rig(scene,camera,tool=ViewportToolId.Scale)
        stretch(rig,DragAxis.X,-200.0)
        assertEquals(Vec2(-TransformMath.MIN_SCALE,1.0),world(scene,a).scale,"mirrored X clamps at the minimum, never 0 or positive")
        scene.entity(a)!!.let { it.document=it.document.withPose(ScenePose(Vec2(0.0,0.0),0.0,Vec2(0.0,1.0))) }
        assertTrue(rig.host.handles().isEmpty(),"no factor can scale a zero scale")
        scene.entity(a)!!.let { it.document=it.document.withPose(ScenePose()) }
        assertEquals(3,rig.host.handles().size)
    }

    @Test fun multiEntityRotateOrbitsThePivotOrTheSelectionCenterAsOneStep() {
        val scene=scene()
        val a=scene.sprite("A",Vec2(0.0,0.0));val b=scene.sprite("B",Vec2(4.0,0.0))
        scene.select(a);scene.toggleSelection(b) // b is active
        val rig=Rig(scene,camera)
        val steps=scene.history.undoLabels.size
        turn(rig,90.0)
        near(Vec2(4.0,0.0),world(scene,b).position,"the active entity is the pivot")
        near(Vec2(4.0,-4.0),world(scene,a).position,"A orbits B by 90°")
        near(PI/2,world(scene,a).rotation,"A turns too");near(PI/2,world(scene,b).rotation,"B turns")
        assertEquals(steps+1,scene.history.undoLabels.size);assertEquals("Rotate 2 entities",scene.history.undoLabel)
        scene.undo()
        near(Vec2(0.0,0.0),world(scene,a).position,"undo");near(0.0,world(scene,a).rotation,"undo angle")
        assertEquals(setOf(a,b),scene.selection.keys.toSet(),"undo keeps the selection")
        // Undo mid-drag cancels one open gesture; releasing then cancels the rest, recording nothing.
        val undone=scene.history.undoLabels.size
        turn(rig,45.0,release=false)
        scene.undo();rig.host.release(ToolPointer(Vec2(0.0,0.0)))
        near(Vec2(0.0,0.0),world(scene,a).position,"A back");near(0.0,world(scene,b).rotation,"B back")
        assertEquals(undone,scene.history.undoLabels.size,"no partial step")
        // Center: the pivot is the middle of the selection bounds, (2, 0).
        rig.state.pivot=PivotMode.Center
        assertEquals(camera.screen(Vec2(2.0,0.0)),rig.host.handles().single().from)
        turn(rig,180.0)
        near(Vec2(4.0,0.0),world(scene,a).position,"A swaps places with B");near(Vec2(0.0,0.0),world(scene,b).position,"B")
        assertEquals("Rotate 2 entities",scene.history.undoLabel)
        assertTrue(rig.state.gestureHint==null)
    }

    @Test fun multiEntityScaleScalesOffsetsAndOnlyWritesTopMostRoots() {
        val scene=scene()
        val a=scene.sprite("A",Vec2(0.0,0.0));val b=scene.sprite("B",Vec2(4.0,0.0))
        scene.select(a);scene.addChild(Vec2(1.0,1.0));val child=scene.selectedKey!!
        val childLocal=local(scene,child)
        scene.select(a);scene.toggleSelection(child);scene.toggleSelection(b) // b is active; child follows its selected parent
        val rig=Rig(scene,camera,tool=ViewportToolId.Scale)
        stretch(rig,DragAxis.Free,64.0) // uniform factor 2 around B
        near(Vec2(-4.0,0.0),world(scene,a).position,"A's offset from B doubles")
        assertEquals(Vec2(2.0,2.0),world(scene,a).scale);assertEquals(Vec2(2.0,2.0),world(scene,b).scale)
        assertEquals(childLocal,local(scene,child),"the child is not written; it follows A")
        near(Vec2(-2.0,2.0),world(scene,child).position,"…and scales with it")
        assertEquals("Scale 2 entities",scene.history.undoLabel)
    }

    @Test fun eachDragIsOneStepEscCancelsAndSwitchingToolsCommits() {
        val scene=scene();val a=scene.sprite("A",Vec2(1.0,1.0))
        val rig=Rig(scene,camera,tool=ViewportToolId.Scale)
        val before=scene.entity(a)!!.document;val steps=scene.history.undoLabels.size
        val x=rig.handle(DragAxis.X);val grab=(x.from+x.to)*0.5
        rig.host.press(ToolPointer(grab))
        for(i in 1..10) rig.host.drag(ToolPointer(grab+Vec2(i*4.0,0.0)))
        assertTrue(rig.state.gestureHint!!.startsWith("Scaling A along X to 1.625 × 1"),rig.state.gestureHint)
        assertTrue(rig.host.key(escape()),"Esc cancels the drag")
        assertEquals(before,scene.entity(a)!!.document);assertEquals(steps,scene.history.undoLabels.size)
        assertFalse(rig.host.pressed)
        // A tool switch mid-drag commits one step; later events are ignored.
        rig.host.press(ToolPointer(grab));rig.host.drag(ToolPointer(grab+Vec2(32.0,0.0)))
        rig.state.activate(ViewportToolId.Rotate)
        assertEquals(steps+1,scene.history.undoLabels.size);assertEquals("Scale A",scene.history.undoLabel)
        assertEquals(Vec2(1.5,1.0),world(scene,a).scale)
        rig.host.drag(ToolPointer(grab+Vec2(64.0,0.0)));rig.host.release(ToolPointer(grab+Vec2(64.0,0.0)))
        assertEquals(Vec2(1.5,1.0),world(scene,a).scale,"events after the switch are ignored")
        // Rotate: a whole drag is one step, and the hint and step show degrees.
        turn(rig,30.0,release=false)
        assertTrue(rig.state.gestureHint!!.startsWith("Rotating A by +30° to 30°"),rig.state.gestureHint)
        rig.host.release(ToolPointer(Vec2(0.0,0.0)))
        assertEquals(steps+2,scene.history.undoLabels.size);assertEquals("Rotate A",scene.history.undoLabel)
        near(PI/6,local(scene,a).rotation,"stored in radians")
        // Clicking off the handles still selects and box-selects as in the Select tool.
        rig.host.press(ToolPointer(camera.screen(Vec2(-8.0,-8.0))));rig.host.release(ToolPointer(camera.screen(Vec2(-8.0,-8.0))))
        assertTrue(scene.selection.isEmpty);assertTrue(rig.host.handles().isEmpty())
    }

    @Test fun hintsCommandsAndThePivotToggle() {
        val off=ToolHintContext(SnapSettings(),1);val on=ToolHintContext(SnapSettings(enabled=true,rotateDegrees=22.5,scale=0.25),2,PivotMode.Center)
        assertTrue(toolHint(ViewportToolId.Rotate,off).contains("Ctrl snaps to 15°"))
        assertTrue(toolHint(ViewportToolId.Rotate,on).contains("snapping to 22.5°"))
        assertTrue(toolHint(ViewportToolId.Rotate,on).contains("around the selection center"))
        assertTrue(toolHint(ViewportToolId.Scale,off).contains("around the active entity's pivot"))
        assertTrue(toolHint(ViewportToolId.Scale,on).contains("snapping to 0.25"))
        assertEquals("-45",degrees(-PI/4));assertEquals("30",degrees(PI/6))
        val scene=scene();val tools=ViewportToolState()
        val ctx=CommandContext(scene=scene,playback=ScenePlaybackState(),tools=tools,snap=SnapService())
        assertEquals(listOf(ToolCommands.rotate),standardKeymap.commandsFor(KeyChord.plain(KeyEvent.VK_E)))
        assertEquals(listOf(ToolCommands.scale),standardKeymap.commandsFor(KeyChord.plain(KeyEvent.VK_R)))
        assertEquals(listOf(ToolCommands.select,ToolCommands.move,ToolCommands.rotate,ToolCommands.scale),ToolCommands.tools)
        ToolCommands.rotate.run(ctx);assertEquals(ViewportToolId.Rotate,tools.active)
        ToolCommands.scale.run(ctx);assertEquals(ViewportToolId.Scale,tools.active)
        assertFalse(ToolCommands.scale.enabled(ctx.copy(mode=ViewportMode.Entity)),"Scene view only")
        assertEquals("Rotate and Scale Around the Selection Center",ToolCommands.togglePivotLabel(ctx))
        ToolCommands.togglePivot.run(ctx);assertEquals(PivotMode.Center,tools.pivot)
        assertEquals("Rotate and Scale Around the Pivot",ToolCommands.togglePivotLabel(ctx))
        assertEquals(ViewportToolId.Scale,tools.active,"the pivot is not a tool switch")
    }
}
