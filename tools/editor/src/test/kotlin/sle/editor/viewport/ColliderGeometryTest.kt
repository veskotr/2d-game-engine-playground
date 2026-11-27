package sle.editor.viewport

import kotlinx.serialization.json.*
import sle.editor.model.*
import kotlin.math.PI
import kotlin.test.*

class ColliderGeometryTest {
    private fun shape(type: String) = buildJsonObject {
        put("enabled",true)
        put("offset",buildJsonObject { put("x",1);put("y",2) })
        if(type.startsWith("Circle")) put("radius",2)
        else put("size",buildJsonObject { put("x",4);put("y",6) })
    }
    private fun document(type: String, scale: Vec2 = Vec2(32.0,32.0)) = EntityDocument()
        .withPose(ScenePose(Vec2(5.0,7.0), PI/2,scale)).addComponent(type,shape(type))
    private fun close(expected: Vec2, actual: Vec2) {
        assertEquals(expected.x,actual.x,1e-8);assertEquals(expected.y,actual.y,1e-8)
    }
    @Test fun rootShapesIgnoreOwnScaleAndKeepSignedOffsets() {
        for(type in listOf("BoxCollider","CircleCollider","BoxZone","CircleZone")) {
            for(scale in listOf(Vec2(32.0,32.0),Vec2(-2.0,3.0),Vec2(0.0,0.0))) {
                val doc=document(type,scale)
                val geometry=GizmoShape.from(doc,type)!!
                close(Vec2(1.0,1.0),geometry.scale)
                close(Vec2(3.0,8.0),geometry.center)
                val moved=GizmoMath.drag(doc,type,GizmoHandle("move",geometry.offset),geometry.center,geometry.center+Vec2(-2.0,1.0))
                close(Vec2(2.0,4.0),GizmoShape.from(moved,type)!!.offset)
            }
        }
        val sprite=document("SpriteRenderer")
        close(Vec2(32.0,32.0),GizmoShape.from(sprite,"SpriteRenderer")!!.scale)
    }
    @Test fun nestedTransformlessNodesPreserveAncestorScaleForPickResizeAndUndo() {
        val scene=SceneWorkspaceState(emptyMap()) { it }
        scene.addEntity();val parent=scene.selected!!
        parent.document=EntityDocument(key=parent.document.key).withPose(ScenePose(Vec2(10.0,20.0),0.3,Vec2(-2.0,3.0)))
        scene.addChild();val middle=scene.selected!!
        middle.document=middle.document.removeComponent("Transform")
        scene.addChild();val child=scene.selected!!
        child.document=document("BoxCollider",Vec2(0.0,32.0)).copy(key=child.document.key)
        val world=scene.worldDocument(child.document.key)
        val geometry=GizmoShape.from(world,"BoxCollider")!!
        close(Vec2(-2.0,3.0),geometry.scale)
        val pose=ScenePose.from(world)
        close(pose.position+Vec2(-2.0,6.0).rotate(pose.rotation),geometry.center)
        val camera=PreviewCamera(Vec2(-100.0,-100.0),Vec2(200.0,200.0),2000,2000)
        val handle=GizmoMath.handles(geometry).first { it.x==1 && it.y==1 }
        val start=GizmoMath.handleWorld(geometry,handle)
        assertEquals("BoxCollider",GizmoMath.pick(world,camera,camera.screen(start),"BoxCollider"))
        child.sceneTarget="BoxCollider"
        val controller=ViewportGizmos(child,true,scene)
        assertTrue(controller.press(camera.screen(start),camera))
        controller.move(camera.screen(start+Vec2(-2.0,3.0).rotate(pose.rotation)),false,false)
        controller.finish()
        val resized=GizmoShape.from(scene.worldDocument(child.document.key),"BoxCollider")!!
        close(Vec2(5.0,7.0),resized.size)
        close(geometry.world(geometry.offset-geometry.size*0.5),resized.world(resized.offset-resized.size*0.5))
        scene.undo()
        assertEquals(world,scene.worldDocument(child.document.key))
    }
    @Test fun nonuniformCirclePickingUsesPhysicsOctagonRatherThanMaximumRadiusDisk() {
        val scene=SceneWorkspaceState(emptyMap()) { it }
        scene.addEntity();val parent=scene.selected!!
        parent.document=parent.document.withPose(ScenePose(scale=Vec2(-2.0,3.0)))
        scene.addChild();val child=scene.selected!!
        child.document=document("CircleZone").copy(key=child.document.key)
        val world=scene.worldDocument(child.document.key)
        val geometry=GizmoShape.from(world,"CircleZone")!!
        assertEquals(9,geometry.outline().size,"nonuniform circles have eight edges plus the closing vertex")
        assertTrue(geometry.contains(geometry.center))
        val betweenVertices=geometry.center+Vec2(4.0*kotlin.math.cos(PI/8),6.0*kotlin.math.sin(PI/8)).rotate(geometry.rotation)
        assertFalse(geometry.contains(betweenVertices),"physics uses the chord rather than the smooth ellipse")
        val camera=PreviewCamera(Vec2(-100.0,-100.0),Vec2(200.0,200.0),4000,4000)
        val top=geometry.center+Vec2(0.0,6.0).rotate(geometry.rotation)
        assertEquals("CircleZone",GizmoMath.pick(world,camera,camera.screen(top),"CircleZone"))
        val outside=geometry.center+Vec2(6.0,0.0).rotate(geometry.rotation)
        assertNull(GizmoMath.pick(world,camera,camera.screen(outside),"CircleZone"))
        val handle=GizmoMath.handles(geometry).first { it.x==1 }
        close(geometry.center+Vec2(-4.0,0.0).rotate(geometry.rotation),GizmoMath.handleWorld(geometry,handle))
        val start=GizmoMath.handleWorld(geometry,handle)
        val changed=GizmoMath.drag(world,"CircleZone",handle,start,start+Vec2(-2.0,0.0).rotate(geometry.rotation))
        assertEquals(3.0,GizmoShape.from(changed,"CircleZone")!!.radius,1e-8)
    }
}
