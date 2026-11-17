package sle.editor.ui

import sle.editor.model.EntityDocument
import sle.editor.model.SceneDocument
import sle.editor.model.SceneWorkspaceState
import sle.editor.model.isolatedComponentPreview
import sle.editor.smoke.ViewportSmokeChecks
import sle.editor.viewport.PreviewCamera
import sle.editor.viewport.ScenePicking
import sle.editor.viewport.Vec2
import sle.editor.viewport.ViewportGizmos

import kotlinx.serialization.json.*
import java.awt.Canvas
import java.awt.event.MouseEvent
import kotlin.test.*

class SceneTreeTest {
    @Test fun visualSmokeRecognizesRedBlueAnimationBlendsWithoutCountingBackgroundOrNpc() {
        assertTrue(ViewportSmokeChecks.isPlayerColor(180,0,0))
        assertTrue(ViewportSmokeChecks.isPlayerColor(90,0,100))
        assertTrue(ViewportSmokeChecks.isPlayerColor(0,20,180))
        assertFalse(ViewportSmokeChecks.isPlayerColor(198,198,198))
        assertFalse(ViewportSmokeChecks.isPlayerColor(64,64,64))
        assertFalse(ViewportSmokeChecks.isPlayerColor(20,200,50))
    }
    private fun entity()=EntityDocument(name="Actor").addComponent("Transform",buildJsonObject {
        put("position",buildJsonObject { put("x",0);put("y",0) });put("rotation",0)
        put("scale",buildJsonObject { put("x",1);put("y",1) })
    }).addComponent("SpriteRenderer",buildJsonObject {
        put("size",buildJsonObject { put("x",2);put("y",2) })
        put("offset",buildJsonObject { put("x",0);put("y",0) })
    }).addComponent("BoxCollider",buildJsonObject {
        put("size",buildJsonObject { put("x",4);put("y",4) })
        put("offset",buildJsonObject { put("x",0);put("y",0) })
    })

    @Test fun treeContainsSceneAndEntityNodesWithoutComponentNodes() {
        val doc=entity()
        val tree=sceneTree(SceneDocument(key=doc.key,name="Level",entities=listOf(doc)))
        assertEquals("Level",tree.label)
        assertEquals(doc.key,tree.children.single().entityKey)
        assertEquals("Actor",tree.children.single().label)
        assertTrue(tree.children.single().children.isEmpty())
        assertNotEquals(tree.id,tree.children.single().id)
    }

    @Test fun scenePickingReturnsEntityAndDraggingNeverEditsAComponentShape() {
        val scene=SceneWorkspaceState(emptyMap()) { it }
        scene.addEntity()
        val state=scene.selected!!
        state.resetTo(entity());scene.select(state.document.key)
        state.selectedTarget="BoxCollider"
        val camera=PreviewCamera(Vec2(-3.0,-3.0),Vec2(6.0,6.0),600,600)
        val point=camera.screen(Vec2(0.5,0.5))
        assertEquals("Entity",ScenePicking.pick(scene,camera,point)!!.target)
        val before=state.document
        val controller=ViewportGizmos(state,entityOnly=true)
        val canvas=Canvas()
        controller.press(MouseEvent(canvas,MouseEvent.MOUSE_PRESSED,0,0,point.x.toInt(),point.y.toInt(),1,false,1),camera)
        controller.move(MouseEvent(canvas,MouseEvent.MOUSE_DRAGGED,0,0,point.x.toInt()+20,point.y.toInt(),1,false,0))
        controller.finish()
        assertNotEquals(before.components["Transform"],state.document.components["Transform"])
        assertEquals(before.components["SpriteRenderer"],state.document.components["SpriteRenderer"])
        assertEquals(before.components["BoxCollider"],state.document.components["BoxCollider"])
        assertEquals("BoxCollider",state.selectedTarget)
        state.undo();assertEquals(before,state.document)
    }

    @Test fun isolatedComponentPreviewPreservesAuthoredCrossEntityReferences() {
        val doc=entity().addComponent("Animator",buildJsonObject {
            put("targetEntities",buildJsonObject { put("other","other");put("bad","missing") })
        })
        val preview=isolatedComponentPreview(doc,listOf(doc.key,"other"))
        val targets=preview.components.getValue("Animator").jsonObject.getValue("targetEntities").jsonObject
        assertEquals(doc.key,targets.getValue("other").jsonPrimitive.content)
        assertEquals("missing",targets.getValue("bad").jsonPrimitive.content)
        assertEquals("other",doc.components.getValue("Animator").jsonObject.getValue("targetEntities").jsonObject.getValue("other").jsonPrimitive.content)
        assertEquals(doc.key,preview.key)
    }
}
