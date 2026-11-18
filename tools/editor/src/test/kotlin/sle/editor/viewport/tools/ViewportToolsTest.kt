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
import kotlin.test.*

class ViewportToolsTest {
    private val catalog=mapOf(
        "Transform" to ComponentDefinition(Json.parseToJsonElement("""{"position":{"x":0,"y":0},"rotation":0,"scale":{"x":1,"y":1}}""").jsonObject,JsonObject(emptyMap())),
    )
    private val camera=PreviewCamera(Vec2(-10.0,-10.0),Vec2(20.0,20.0),400,400)
    private fun scene()=SceneWorkspaceState(catalog) { it }
    /** A root entity at [at] with a [size] sprite, rotated by [rotation] radians. */
    private fun SceneWorkspaceState.sprite(name: String,at: Vec2,size: Double=2.0,rotation: Double=0.0): String {
        addEntity(at);val state=selected!!;state.rename(name)
        val sprite=buildJsonObject { put("size",buildJsonObject { put("x",size);put("y",size) });put("offset",buildJsonObject { put("x",0);put("y",0) }) }
        state.document=state.document.copy(components=JsonObject(state.document.components+("SpriteRenderer" to sprite)))
            .withPose(ScenePose(at,rotation))
        return state.document.key
    }
    private fun position(scene: SceneWorkspaceState,key: String)=ScenePose.from(scene.worldDocument(key)).position
    private class Rig(val scene: SceneWorkspaceState,camera: PreviewCamera,snap: SnapService=SnapService()) {
        val state=ViewportToolState()
        var gizmos: ViewportGizmos?=null
        val host=ToolHost(state,snap).also { host ->
            host.scene=scene;host.camera=camera
            host.gizmos={ gizmos }
            host.selectionChanged={ gizmos=scene.selected?.let { ViewportGizmos(it,true,scene,snap) } }
            host.selectionChanged()
        }
    }
    private fun assertNear(expected: Vec2,actual: Vec2,message: String?=null) =
        assertTrue((expected-actual).length()<1e-9,"${message ?: "position"}: expected $expected, was $actual")
    private fun at(world: Vec2,shift: Boolean=false)=ToolPointer(camera.screen(world),shift=shift)

    @Test fun marqueeUsesSelectionBoundsOfRotatedSprites() {
        val scene=scene()
        val diamond=scene.sprite("Diamond",Vec2(0.0,0.0),size=4.0,rotation=PI/4)
        scene.sprite("Far",Vec2(8.0,-8.0))
        // A 4×4 sprite turned 45° spans ±2.83 on both axes: a box at its bounds' corner selects it,
        // although the corner lies outside the sprite itself.
        assertEquals(listOf(diamond),marqueeHits(scene,camera,camera.screen(Vec2(2.2,2.2)),camera.screen(Vec2(3.0,3.0))))
        assertEquals(emptyList(),marqueeHits(scene,camera,camera.screen(Vec2(3.0,3.0)),camera.screen(Vec2(4.0,4.0))))
        val unrotated=scene.sprite("Square",Vec2(-6.0,6.0),size=4.0)
        assertEquals(listOf(unrotated),marqueeHits(scene,camera,camera.screen(Vec2(-4.5,4.5)),camera.screen(Vec2(-3.5,3.5))))
    }

    @Test fun selectToolBoxSelectsReplacingOrAddingWithShift() {
        val scene=scene()
        val a=scene.sprite("A",Vec2(-5.0,0.0));val b=scene.sprite("B",Vec2(5.0,0.0));val c=scene.sprite("C",Vec2(0.0,6.0))
        val rig=Rig(scene,camera)
        rig.host.press(at(Vec2(-8.0,-3.0)));rig.host.drag(at(Vec2(8.0,2.0)))
        assertNotNull(rig.state.gestureHint);rig.host.release(at(Vec2(8.0,2.0)))
        assertEquals(setOf(a,b),scene.selection.keys.toSet());assertNull(rig.state.gestureHint)
        rig.host.press(at(Vec2(-2.0,8.0),shift=true));rig.host.drag(at(Vec2(2.0,4.0),shift=true));rig.host.release(at(Vec2(2.0,4.0),shift=true))
        assertEquals(setOf(a,b,c),scene.selection.keys.toSet(),"Shift adds")
        rig.host.press(at(Vec2(-9.0,-9.0)));rig.host.drag(at(Vec2(-8.0,-8.0)));rig.host.release(at(Vec2(-8.0,-8.0)))
        assertTrue(scene.selection.isEmpty,"an empty plain box clears the selection")
        // Escape cancels a box; a press without a drag keeps the click semantics.
        scene.select(a);rig.host.selectionChanged()
        rig.host.press(at(Vec2(-8.0,-3.0),shift=true));rig.host.drag(at(Vec2(8.0,2.0),shift=true))
        assertTrue(rig.host.key(KeyEvent(Canvas(),KeyEvent.KEY_PRESSED,0,0,KeyEvent.VK_ESCAPE,KeyEvent.CHAR_UNDEFINED)))
        assertEquals(listOf(a),scene.selection.keys)
        rig.host.press(at(Vec2(5.0,0.0)));rig.host.release(at(Vec2(5.0,0.0)))
        assertEquals(listOf(b),scene.selection.keys)
        rig.host.press(at(Vec2(-5.0,0.0),shift=true));rig.host.release(at(Vec2(-5.0,0.0),shift=true))
        assertEquals(setOf(a,b),scene.selection.keys.toSet(),"Shift-click toggles")
    }

    @Test fun moveToolArrowsKeepAxesAndSwitchingToolsFinishesTheGesture() {
        val scene=scene();val a=scene.sprite("A",Vec2(1.0,1.0))
        val rig=Rig(scene,camera);rig.state.activate(ViewportToolId.Move)
        val origin=camera.screen(Vec2(1.0,1.0))
        assertEquals(DragAxis.X,MoveHandles.hit(origin,origin+Vec2(40.0,2.0)))
        assertEquals(DragAxis.Y,MoveHandles.hit(origin,origin+Vec2(-2.0,-40.0)))
        assertEquals(DragAxis.Free,MoveHandles.hit(origin,origin+Vec2(3.0,3.0)))
        assertNull(MoveHandles.hit(origin,origin+Vec2(40.0,30.0)))
        assertEquals(3,rig.host.handles().size)
        val steps=scene.history.undoLabels.size
        rig.host.press(ToolPointer(origin+Vec2(40.0,0.0)))
        rig.host.drag(ToolPointer(origin+Vec2(80.0,-30.0)))
        assertNear(Vec2(3.0,1.0),position(scene,a),"the X arrow keeps Y")
        assertTrue(rig.state.gestureHint!!.contains("along X"))
        rig.state.activate(ViewportToolId.Select) // Q mid-drag commits the drag
        assertFalse(rig.host.pressed);assertFalse(rig.gizmos!!.dragging)
        assertEquals(steps+1,scene.history.undoLabels.size);assertEquals("Move A",scene.history.undoLabel)
        rig.host.drag(ToolPointer(origin+Vec2(200.0,0.0)));rig.host.release(ToolPointer(origin+Vec2(200.0,0.0)))
        assertNear(Vec2(3.0,1.0),position(scene,a),"events after the switch are ignored")
        // Escape cancels a Move drag: nothing changes and no step is recorded.
        rig.state.activate(ViewportToolId.Move)
        val moved=camera.screen(Vec2(3.0,1.0))
        rig.host.press(ToolPointer(moved+Vec2(0.0,-40.0)));rig.host.drag(ToolPointer(moved+Vec2(20.0,-100.0)))
        assertNear(Vec2(3.0,4.0),position(scene,a),"the Y arrow keeps X")
        rig.host.key(KeyEvent(Canvas(),KeyEvent.KEY_PRESSED,0,0,KeyEvent.VK_ESCAPE,KeyEvent.CHAR_UNDEFINED))
        assertNear(Vec2(3.0,1.0),position(scene,a));assertEquals(steps+1,scene.history.undoLabels.size)
    }

    @Test fun moveToolSquareMovesTheSelectionAsOneSnappedStep() {
        val scene=scene();val a=scene.sprite("A",Vec2(0.0,0.0));val b=scene.sprite("B",Vec2(4.0,0.0))
        scene.select(a);scene.toggleSelection(b) // b is active
        val rig=Rig(scene,camera,SnapService(SnapSettings(enabled=true,move=1.0)));rig.state.activate(ViewportToolId.Move)
        val steps=scene.history.undoLabels.size
        val origin=camera.screen(Vec2(4.0,0.0))
        rig.host.press(ToolPointer(origin));rig.host.drag(ToolPointer(camera.screen(Vec2(5.4,2.6))));rig.host.release(ToolPointer(camera.screen(Vec2(5.4,2.6))))
        assertNear(Vec2(5.0,3.0),position(scene,b));assertNear(Vec2(1.0,3.0),position(scene,a))
        assertEquals(steps+1,scene.history.undoLabels.size);assertEquals("Move 2 entities",scene.history.undoLabel)
        assertEquals(setOf(a,b),scene.selection.keys.toSet())
    }

    @Test fun toolHintsDescribeSnapping() {
        val off=ToolHintContext(SnapSettings(),1);val on=ToolHintContext(SnapSettings(enabled=true,move=0.5),1)
        assertTrue(toolHint(ViewportToolId.Select,off).startsWith("Select:"))
        assertTrue(toolHint(ViewportToolId.Move,off).contains("Ctrl snaps to 1"))
        assertTrue(toolHint(ViewportToolId.Move,on).contains("snapping to 0.5"))
        assertEquals("16",formatStep(16.0));assertEquals("0.25",formatStep(0.25))
    }

    @Test fun toolCommandsAreRegisteredWithChordsAndNeedTheSceneView() {
        val scene=scene();val tools=ViewportToolState()
        val ctx=CommandContext(scene=scene,playback=ScenePlaybackState(),tools=tools,snap=SnapService())
        assertEquals(listOf(ToolCommands.select),standardKeymap.commandsFor(KeyChord.plain(KeyEvent.VK_Q)))
        assertEquals(listOf(ToolCommands.move),standardKeymap.commandsFor(KeyChord.plain(KeyEvent.VK_W)))
        assertEquals(listOf(ToolCommands.frameSelected),standardKeymap.commandsFor(KeyChord.plain(KeyEvent.VK_F)))
        assertTrue(ToolCommands.move.enabled(ctx));ToolCommands.move.run(ctx);assertEquals(ViewportToolId.Move,tools.active)
        assertFalse(ToolCommands.frameSelected.enabled(ctx),"nothing to frame")
        assertEquals("Select an entity to frame",ToolCommands.frameSelected.disabledReason(ctx))
        scene.sprite("A",Vec2(0.0,0.0));assertTrue(ToolCommands.frameSelected.enabled(ctx))
        val game=ctx.copy(playback=ScenePlaybackState().also { it.gameView=true })
        assertFalse(ToolCommands.select.enabled(game));assertFalse(ToolCommands.frameSelected.enabled(game))
        assertFalse(ToolCommands.select.enabled(ctx.copy(mode=ViewportMode.Entity)))
        assertEquals("Turn Snapping On",ToolCommands.toggleSnapLabel(ctx));ToolCommands.toggleSnap.run(ctx)
        assertEquals("Turn Snapping Off",ToolCommands.toggleSnapLabel(ctx))
    }

    @Test fun frameSelectedFitsTheBoundsWithTheViewAspect() {
        val (factor,center)=frameView(Vec2(48.0,27.0),Bounds(Vec2(10.0,10.0),Vec2(30.0,14.0)))!!
        assertEquals(Vec2(20.0,12.0),center)
        // 20 wide at 16:9 needs 11.25 high; with the 1.3 margin that is 14.625 of 27.
        assertEquals(14.625/27.0,factor,1e-9)
        val (tiny,_)=frameView(Vec2(48.0,27.0),Bounds(Vec2(1.0,1.0),Vec2(1.0,1.0)))!!
        assertEquals(4.0/27.0,tiny,1e-9)
        assertNull(frameView(Vec2(0.0,0.0),Bounds(Vec2(0.0,0.0),Vec2(1.0,1.0))))
    }
}
