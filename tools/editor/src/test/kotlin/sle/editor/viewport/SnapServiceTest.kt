package sle.editor.viewport

import sle.editor.model.ComponentDefinition
import sle.editor.model.History
import sle.editor.model.ScenePose
import sle.editor.model.SceneWorkspaceState
import sle.editor.model.withPose

import kotlinx.serialization.json.*
import java.nio.file.Files
import kotlin.math.abs
import kotlin.test.*

class SnapServiceTest {
    private val catalog=mapOf(
        "Transform" to ComponentDefinition(Json.parseToJsonElement("""{"position":{"x":0,"y":0},"rotation":0,"scale":{"x":1,"y":1}}""").jsonObject,JsonObject(emptyMap())),
    )
    private val camera=PreviewCamera(Vec2(-20.0,-20.0),Vec2(40.0,40.0),800,800)
    private fun onStep(value: Double,step: Double)=abs(value/step-Math.rint(value/step))<1e-9

    @Test fun valuesSnapSymmetricallyIncludingNegativesAndCleanDecimals() {
        assertEquals(-1.0,SnapMath.value(-1.4,1.0));assertEquals(-2.0,SnapMath.value(-1.6,1.0))
        assertEquals(2.0,SnapMath.value(1.5,1.0));assertEquals(-2.0,SnapMath.value(-1.5,1.0))
        assertEquals(0.3,SnapMath.value(0.31,0.1));assertEquals(-0.3,SnapMath.value(-0.29,0.1))
        val zero=SnapMath.value(-0.04,0.1);assertEquals(0.0,zero);assertFalse(1.0/zero<0,"never -0.0")
        assertEquals(48.0,SnapMath.value(40.0,16.0));assertEquals(32.0,SnapMath.value(39.0,16.0));assertEquals(-48.0,SnapMath.value(-41.0,16.0))
        assertEquals(Vec2(1.0,0.37),SnapMath.point(Vec2(1.2,0.37),0.5,SnapAxes.X))
        assertEquals(Vec2(1.2,0.5),SnapMath.point(Vec2(1.2,0.37),0.5,SnapAxes.Y))
    }

    @Test fun nextGridLineMovesOneStepOrOntoTheNearestLineThatWay() {
        assertEquals(1.0,SnapMath.next(0.0,1.0,1));assertEquals(-1.0,SnapMath.next(0.0,1.0,-1))
        assertEquals(1.0,SnapMath.next(0.35,1.0,1));assertEquals(0.0,SnapMath.next(0.35,1.0,-1))
        assertEquals(-1.0,SnapMath.next(-1.6,1.0,1));assertEquals(-2.0,SnapMath.next(-1.6,1.0,-1))
        assertEquals(0.4,SnapMath.next(0.3,0.1,1)) // 0.3 is on the grid despite binary rounding
        assertEquals(-0.2,SnapMath.next(-0.1,0.1,-1))
    }

    @Test fun ctrlInvertsTheToggleAndPlacementUsesTheMoveStep() {
        val snap=SnapService()
        assertFalse(snap.settings.enabled,"snapping is off by default")
        assertEquals(SnapSettings(false,1.0,15.0,0.1),snap.settings)
        assertNull(snap.moveStep());assertEquals(1.0,snap.moveStep(invert=true))
        assertEquals(Vec2(2.3,-0.7),snap.placement(Vec2(2.3,-0.7)))
        assertEquals(Vec2(2.0,-1.0),snap.placement(Vec2(2.3,-0.7),invert=true))
        var saved: SnapSettings?=null;snap.onChange={ _, value -> saved=value }
        snap.toggle();assertTrue(snap.active());assertFalse(snap.active(invert=true));assertEquals(snap.settings,saved)
        snap.load(SnapSettings(move=0.25));saved=null
        assertEquals(Vec2(2.25,-0.75),snap.placement(Vec2(2.3,-0.7),invert=true));assertNull(saved,"loading is not a change")
        for(bad in listOf({ SnapSettings(move=0.0) },{ SnapSettings(move=Double.NaN) },{ SnapSettings(rotateDegrees=-15.0) },{ SnapSettings(scale=1000.0) }))
            assertFailsWith<IllegalArgumentException> { bad() }
    }

    @Test fun dragsSnapWorldPositionsUnderRotatedAndMirroredParents() {
        val scene=SceneWorkspaceState(catalog) { it }
        scene.addEntity(Vec2(1.0,2.0));val parent=scene.selectedKey!!
        val parentState=scene.entity(parent)!!
        parentState.document=parentState.document.withPose(ScenePose(Vec2(1.0,2.0),0.5,Vec2(-2.0,1.5)))
        scene.addChild(Vec2(3.3,-1.2));val child=scene.selectedKey!!
        val snap=SnapService(SnapSettings(enabled=true,move=0.5))
        val gizmos=ViewportGizmos(scene.entity(child)!!,true,scene,snap)
        val start=ScenePose.from(scene.worldDocument(child)).position
        assertEquals(3.3,start.x,1e-9);assertEquals(-1.2,start.y,1e-9)
        val steps=scene.history.undoLabels.size
        assertTrue(gizmos.pressMove(camera.screen(start),camera,DragAxis.Free))
        gizmos.move(camera.screen(start+Vec2(2.13,-3.71)),shift=false,ctrl=false);gizmos.finish()
        val world=ScenePose.from(scene.worldDocument(child)).position
        assertEquals(5.5,world.x,1e-9);assertEquals(-5.0,world.y,1e-9) // snap(3.3+2.13), snap(-1.2-3.71)
        val local=ScenePose.from(scene.entity(child)!!.document)
        val expected=ScenePose(world).relativeTo(ScenePose.from(scene.worldDocument(parent)))
        assertEquals(expected.position.x,local.position.x,1e-9);assertEquals(expected.position.y,local.position.y,1e-9)
        assertEquals(steps+1,scene.history.undoLabels.size,"one undo step per drag")
        // Ctrl inverts snapping for the drag; Shift keeps the dominant axis (X here).
        assertTrue(gizmos.pressMove(camera.screen(world),camera,DragAxis.Free))
        gizmos.move(camera.screen(world+Vec2(1.13,0.31)),shift=true,ctrl=true);gizmos.finish()
        val free=ScenePose.from(scene.worldDocument(child)).position
        assertEquals(6.63,free.x,1e-9);assertEquals(-5.0,free.y,1e-9)
        // An X arrow snaps only X, leaving an off-grid Y alone.
        scene.applyWorldDocument(child,scene.worldDocument(child).withPose(ScenePose(Vec2(6.63,-5.21))))
        assertTrue(gizmos.pressMove(camera.screen(Vec2(6.63,-5.21)),camera,DragAxis.X))
        gizmos.move(camera.screen(Vec2(7.4,-2.0)),shift=false,ctrl=false);gizmos.finish()
        val axis=ScenePose.from(scene.worldDocument(child)).position
        assertEquals(7.5,axis.x,1e-9);assertEquals(-5.21,axis.y,1e-9)
        scene.undo();assertEquals(-5.21,ScenePose.from(scene.worldDocument(child)).position.y,1e-9)
    }

    @Test fun nudgesMoveOneStepWhenSnappingAndMergeIntoOneUndoStep() {
        val scene=SceneWorkspaceState(catalog) { it }
        var now=10_000L;scene.history.clock={ now }
        scene.addEntity(Vec2(0.35,-0.2));val key=scene.selectedKey!!
        val snap=SnapService(SnapSettings(enabled=true,move=1.0))
        val gizmos=ViewportGizmos(scene.entity(key)!!,true,scene,snap)
        fun x()=ScenePose.from(scene.worldDocument(key)).position.x
        val steps=scene.history.undoLabels.size
        gizmos.nudge(Vec2(1.0,0.0));assertEquals(1.0,x(),1e-9);now+=200
        gizmos.nudge(Vec2(1.0,0.0));gizmos.nudge(Vec2(1.0,0.0));assertEquals(3.0,x(),1e-9)
        assertEquals(steps+1,scene.history.undoLabels.size,"a nudge burst is one undo step")
        assertEquals(-0.2,ScenePose.from(scene.worldDocument(key)).position.y,1e-9)
        gizmos.nudge(Vec2(-1.0,0.0),ctrl=true);assertEquals(2.9,x(),1e-9) // Ctrl: plain 0.1
        snap.toggle();gizmos.nudge(Vec2(1.0,0.0),shift=true);assertEquals(3.9,x(),1e-9) // Shift: 1
        now+=History.MERGE_WINDOW_MILLIS
        gizmos.nudge(Vec2(1.0,0.0),ctrl=true);assertEquals(4.0,x(),1e-9) // Ctrl turns snapping on: next line
    }

    @Test fun clickToCreateAndDropsLandOnTheSnappedWorldPointUnderAParent() {
        val scene=SceneWorkspaceState(catalog) { it }
        scene.addEntity(Vec2(0.0,0.0));val parent=scene.entity(scene.selectedKey!!)!!
        parent.document=parent.document.withPose(ScenePose(Vec2(0.3,0.0),1.0,Vec2(1.0,-1.0)))
        val snap=SnapService(SnapSettings(enabled=true,move=2.0))
        scene.addChild(snap.placement(Vec2(4.9,-3.1)))
        val world=ScenePose.from(scene.worldDocument(scene.selectedKey!!)).position
        assertEquals(4.0,world.x,1e-9);assertEquals(-4.0,world.y,1e-9)
        assertTrue(onStep(world.x,2.0) && onStep(world.y,2.0))
    }

    @Test fun settingsJsonAndStandaloneStoreRoundTripAndRecover() {
        val value=SnapSettings(true,16.0,22.5,0.25)
        assertEquals(value,snapSettingsFrom(value.toJson()))
        assertEquals(SnapSettings(move=4.0),snapSettingsFrom(Json.parseToJsonElement("""{"move":4}""")))
        for(bad in listOf("""{"move":"1"}""","""{"enabled":"yes"}""","""{"move":-1}""","[]"))
            assertFails { snapSettingsFrom(Json.parseToJsonElement(bad)) }
        val folder=Files.createTempDirectory("snap-store")
        val store=SnapSettingsStore(folder)
        assertEquals(SnapSettings(),store.load());assertNull(store.lastFailure)
        assertTrue(store.save(value));assertEquals(value,SnapSettingsStore(folder).load())
        Files.writeString(folder.resolve("snap-settings.json"),"broken")
        assertEquals(SnapSettings(),store.load());assertNotNull(store.lastFailure)
    }
}

