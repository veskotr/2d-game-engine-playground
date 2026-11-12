package sle.editor.viewport

import sle.editor.model.EntityDocument
import sle.editor.model.EntityWorkspaceState

import kotlinx.serialization.json.*
import kotlin.test.*

class GizmoTest {
    @Test fun transparentColliderInteriorsDoNotStealSpriteClicks() {
        val doc=EntityDocument().addComponent("Transform",buildJsonObject {
            put("position",buildJsonObject { put("x",0); put("y",0) })
        }).addComponent("SpriteRenderer",buildJsonObject {
            put("size",buildJsonObject { put("x",2); put("y",2) })
            put("offset",buildJsonObject { put("x",0); put("y",0) })
        }).addComponent("BoxCollider",buildJsonObject {
            put("enabled",true)
            put("size",buildJsonObject { put("x",4); put("y",4) })
            put("offset",buildJsonObject { put("x",0); put("y",0) })
        })
        val camera=PreviewCamera(Vec2(-3.0,-3.0),Vec2(6.0,6.0),600,600)
        assertEquals("SpriteRenderer",GizmoMath.pick(doc,camera,camera.screen(Vec2(0.5,0.5)),"BoxCollider"))
        assertEquals("BoxCollider",GizmoMath.pick(doc,camera,camera.screen(Vec2(2.0,0.5)),"SpriteRenderer"))
        assertNull(GizmoMath.pick(doc,camera,camera.screen(Vec2(1.5,0.5)),"BoxCollider"))
        val disabled=doc.setValue("BoxCollider",listOf("enabled"),JsonPrimitive(false))
            .setValue("BoxCollider",listOf("size"),buildJsonObject { put("x",2); put("y",2) })
            .setValue("SpriteRenderer",listOf("size"),buildJsonObject { put("x",4); put("y",4) })
        assertEquals("SpriteRenderer",GizmoMath.pick(disabled,camera,camera.screen(Vec2(1.0,0.3)),"SpriteRenderer"))
    }

    @Test fun spriteResizeAndMoveLeaveTransformAndColliderUntouched() {
        val doc=document().addComponent("SpriteRenderer",buildJsonObject {
            put("size",buildJsonObject { put("x",1); put("y",1) })
            put("offset",buildJsonObject { put("x",0); put("y",0) })
        })
        val shape=GizmoShape.from(doc,"SpriteRenderer")!!
        val handle=GizmoMath.handles(shape).first { it.x==1 && it.y==1 }
        val start=GizmoMath.handleWorld(shape,handle)
        val changed=GizmoMath.drag(doc,"SpriteRenderer",handle,start,shape.world(Vec2(1.0,1.0)),false)
        assertEquals(doc.components["Transform"],changed.components["Transform"])
        assertEquals(doc.components["BoxCollider"],changed.components["BoxCollider"])
        assertTrue(GizmoShape.from(changed,"SpriteRenderer")!!.size.x>1.0)
        val moved=GizmoMath.drag(changed,"SpriteRenderer",GizmoHandle("move",shape.offset),start,start+Vec2(1.0,0.0),false)
        assertEquals(doc.components["Transform"],moved.components["Transform"])
        assertEquals(doc.components["BoxCollider"],moved.components["BoxCollider"])
        assertNotEquals(shape.offset,GizmoShape.from(moved,"SpriteRenderer")!!.offset)
    }

    @Test fun undoDuringDragCancelsOnlyTheGestureAndKeepsPreviousEdits() {
        val state=EntityWorkspaceState(emptyMap())
        state.document=document()
        state.rename("Renamed")
        val before=state.document
        state.selectedTarget="BoxCollider"
        val controller=ViewportGizmos(state)
        val canvas=java.awt.Canvas()
        val camera=PreviewCamera(Vec2(-2.0,-2.0),Vec2(10.0,10.0),800,600)
        val center=camera.screen(GizmoShape.from(before,"BoxCollider")!!.center)
        controller.press(java.awt.event.MouseEvent(canvas,java.awt.event.MouseEvent.MOUSE_PRESSED,0,0,center.x.toInt(),center.y.toInt(),1,false,1),camera)
        controller.move(java.awt.event.MouseEvent(canvas,java.awt.event.MouseEvent.MOUSE_DRAGGED,0,0,center.x.toInt()+20,center.y.toInt(),1,false,0))
        assertNotEquals(before,state.document)
        controller.key(java.awt.event.KeyEvent(canvas,java.awt.event.KeyEvent.KEY_PRESSED,0,java.awt.event.InputEvent.CTRL_DOWN_MASK,java.awt.event.KeyEvent.VK_Z,'z'))
        assertEquals(before,state.document)
        assertTrue(state.canUndo)
        assertFalse(controller.dragging)
    }
    private fun document() = EntityDocument().addComponent("Transform", buildJsonObject {
        put("position", buildJsonObject { put("x", 3); put("y", 4) })
        put("rotation", Math.PI / 2)
        put("scale", buildJsonObject { put("x", 2); put("y", -3) })
    }).addComponent("BoxCollider", buildJsonObject {
        put("offset", buildJsonObject { put("x", 0); put("y", 0) })
        put("size", buildJsonObject { put("x", 2); put("y", 4) })
    }).addComponent("CircleCollider", buildJsonObject {
        put("offset", buildJsonObject { put("x", 0); put("y", 0) }); put("radius", 1)
    })

    @Test fun cameraCoordinatesRoundTripWithTopLeftMouseCoordinates() {
        val camera = PreviewCamera(Vec2(-4.0, -2.0), Vec2(8.0, 4.0), 800, 400)
        assertEquals(Vec2(0.0, 0.0), camera.world(Vec2(400.0, 200.0)))
        assertEquals(Vec2(0.0, 0.0), camera.screen(Vec2(-4.0, 2.0)))
        val roundTrip=camera.screen(camera.world(Vec2(123.0, 85.0)))
        assertEquals(123.0, roundTrip.x, 1e-8)
        assertEquals(85.0, roundTrip.y, 1e-8)
    }

    @Test fun rotatedMirroredBoxResizeKeepsOppositeCornerFixed() {
        val doc = document()
        val shape = GizmoShape.from(doc, "BoxCollider")!!
        val oldOpposite = shape.world(Vec2(-1.0, -2.0))
        val handle = GizmoHandle("size", Vec2(1.0, 2.0), 1, 1)
        val changed = GizmoMath.drag(doc, "BoxCollider", handle, shape.world(Vec2(1.0, 2.0)), shape.world(Vec2(2.0, 3.0)), false)
        val resized = GizmoShape.from(changed, "BoxCollider")!!
        val actual = resized.world(resized.offset - resized.size * 0.5)
        assertEquals(oldOpposite.x, actual.x, 1e-8)
        assertEquals(oldOpposite.y, actual.y, 1e-8)
        assertEquals(3.0, resized.size.x, 1e-8)
        assertEquals(5.0, resized.size.y, 1e-8)
    }

    @Test fun colliderMovementUsesLocalOffsetAndCircleUsesMaximumScale() {
        val doc = document()
        val shape = GizmoShape.from(doc, "CircleCollider")!!
        val moved = GizmoMath.drag(doc, "CircleCollider", GizmoHandle("move", Vec2(0.0, 0.0)), shape.position, shape.position + Vec2(3.0, 2.0), false)
        val offset = GizmoShape.from(moved, "CircleCollider")!!.offset
        assertEquals(1.0, offset.x, 1e-8)
        assertEquals(1.0, offset.y, 1e-8)
        val radius = GizmoMath.drag(doc, "CircleCollider", GizmoHandle("radius", Vec2(0.0, 0.0),1,0), shape.position+Vec2(3.0,0.0), shape.position + Vec2(6.0, 0.0), false)
        assertEquals(2.0, GizmoShape.from(radius, "CircleCollider")!!.radius, 1e-8)
    }

    @Test fun offCenterGrabsDoNotJumpAndZeroScaleCannotCorruptColliders() {
        val doc=document()
        for (target in listOf("BoxCollider","CircleCollider")) {
            val shape=GizmoShape.from(doc,target)!!
            val handle=GizmoMath.handles(shape).last()
            val grab=GizmoMath.handleWorld(shape,handle)+Vec2(0.08,0.05)
            val unchanged=GizmoMath.drag(doc,target,handle,grab,grab,false)
            val actual=GizmoShape.from(unchanged,target)!!
            assertEquals(shape.radius,actual.radius,1e-8)
            assertEquals(shape.size.x,actual.size.x,1e-8)
            assertEquals(shape.size.y,actual.size.y,1e-8)
            val handleCenter=GizmoMath.handleWorld(shape,handle)
            val delta=Vec2(0.01,0.02)
            val centered=GizmoShape.from(GizmoMath.drag(doc,target,handle,handleCenter,handleCenter+delta,false),target)!!
            val offsetGrab=GizmoShape.from(GizmoMath.drag(doc,target,handle,grab,grab+delta,false),target)!!
            assertEquals(centered.radius,offsetGrab.radius,1e-8)
            assertEquals(centered.size.x,offsetGrab.size.x,1e-8)
            assertEquals(centered.size.y,offsetGrab.size.y,1e-8)
        }
        val zero=doc.setValue("Transform",listOf("scale","x"),JsonPrimitive(0.0))
        assertEquals(zero,GizmoMath.drag(zero,"BoxCollider",GizmoHandle("size",Vec2(1.0,1.0),1,1),Vec2(0.0,0.0),Vec2(4.0,4.0),false))
        val tiny=doc.setValue("Transform",listOf("scale"),buildJsonObject { put("x",0.000001); put("y",0.000002) })
        assertEquals(0.0001,GizmoShape.from(tiny,"CircleCollider")!!.worldRadius,1e-9)
    }

    @Test fun undoRestoresRemovedComponentDraftAndNewEditsDiscardRedo() {
        val state=EntityWorkspaceState(emptyMap())
        state.document=document()
        state.editField("BoxCollider",listOf("size","x"),JsonPrimitive(2.0),"-")
        state.removeComponent("BoxCollider")
        state.undo()
        assertTrue("BoxCollider" in state.document.components)
        assertEquals("-",state.drafts["BoxCollider/size/x"])
        assertTrue(state.canRedo)
        state.rename("Changed")
        assertFalse(state.canRedo)
        state.newDocument()
        assertFalse(state.canUndo)
        assertFalse(state.canRedo)
    }

    @Test fun oneDragIsOneUndoAndCancellationRestoresDrafts() {
        val state = EntityWorkspaceState(emptyMap())
        state.document = document()
        val initial = state.document
        state.beginGesture()
        state.setValue("Transform", listOf("position", "x"), JsonPrimitive(20))
        state.setValue("Transform", listOf("position", "x"), JsonPrimitive(30))
        state.endGesture()
        assertTrue(state.canUndo)
        state.undo()
        assertEquals(initial, state.document)
        assertFalse(state.canUndo)
        state.redo()
        assertNotEquals(initial, state.document)
        state.editField("Transform", listOf("rotation"), JsonPrimitive(0.0), "-")
        state.beginGesture()
        state.setValue("Transform", listOf("rotation"), JsonPrimitive(1.0))
        state.cancelGesture()
        assertEquals("-", state.drafts["Transform/rotation"])
        assertTrue("Transform/rotation" in state.fieldErrors)
    }
}
