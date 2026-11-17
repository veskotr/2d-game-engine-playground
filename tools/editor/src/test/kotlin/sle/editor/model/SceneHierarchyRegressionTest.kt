package sle.editor.model

import sle.editor.ui.flattenSceneTree
import sle.editor.ui.sceneTree
import sle.editor.viewport.PreviewCamera
import sle.editor.viewport.Vec2
import sle.editor.viewport.ViewportGizmos
import sle.editor.viewport.cameraOutline

import kotlinx.serialization.json.*
import kotlin.math.PI
import kotlin.test.*

class SceneHierarchyRegressionTest {
    private fun scene()=SceneWorkspaceState(emptyMap()) { it }
    private fun add(scene: SceneWorkspaceState,pose: ScenePose=ScenePose()): String {
        scene.addEntity();scene.selected!!.document=scene.selected!!.document.withPose(pose);return scene.selectedKey!!
    }
    private fun assertPose(expected: ScenePose,actual: ScenePose) {
        assertEquals(expected.position.x,actual.position.x,1e-8);assertEquals(expected.position.y,actual.position.y,1e-8)
        assertEquals(expected.rotation,actual.rotation,1e-8);assertEquals(expected.scale.x,actual.scale.x,1e-8);assertEquals(expected.scale.y,actual.scale.y,1e-8)
    }
    @Test fun parentReferencesNormalizeAndDeepForestsValidateWithoutRecursion() {
        val loaded=SceneDocument.parse("""{"entities":[{"key":"a","components":{},"parent":null},{"key":"b","components":{},"parent":1}]}""")
        assertEquals(mapOf("b" to "a"),loaded.parents);assertEquals(loaded,SceneDocument.parse(loaded.serialize()))
        val keys=(0 until 4096).map { "node$it" };val parents=keys.drop(1).mapIndexed { i,key -> key to keys[i] }.toMap()
        validateSceneParents(keys,parents)
        assertFails { validateSceneParents(keys,parents+(keys.first() to keys.last())) }
        assertFails { validateSceneParents(keys,mapOf("node0" to "missing")) }
        val tree=sceneTree(SceneDocument(entities=keys.map { EntityDocument(key=it) },parents=parents))
        assertEquals(4096,flattenSceneTree(tree,keys.map { "entity:$it" }.toSet()).size)
    }
    @Test fun reparentPreservesWorldPoseWithRotationNegativeScaleAndTransparentAncestors() {
        val scene=scene();val parent=add(scene,ScenePose(Vec2(12.0,8.0),PI/3,Vec2(-2.0,3.0)))
        scene.addEntity();val transparent=scene.selectedKey!!;scene.reparent(transparent,parent,false)
        val child=add(scene,ScenePose(Vec2(3.0,4.0),0.2,Vec2(2.0,-1.0)))
        val world=ScenePose.from(scene.worldDocument(child));scene.reparent(child,transparent)
        assertPose(world,ScenePose.from(scene.worldDocument(child)))
        scene.reparent(child,null);assertPose(world,ScenePose.from(scene.worldDocument(child)))
        scene.select(parent);scene.selected!!.document=scene.selected!!.document.withPose(ScenePose(scale=Vec2(0.0,1.0)))
        val before=scene.document();assertFails { scene.reparent(child,parent) };assertEquals(before,scene.document())
        assertFails { scene.reparent(parent,transparent,false) }
    }
    @Test fun transparentReparentPreservesInheritedPoseAndUndoRemovesOnlyInsertedTransform() {
        val scene=scene();val parent=add(scene,ScenePose(Vec2(12.0,8.0),0.4,Vec2(-2.0,3.0)))
        scene.addEntity();val child=scene.selectedKey!!;val state=scene.selected!!
        scene.reparent(child,parent);assertPose(ScenePose(),ScenePose.from(scene.worldDocument(child)))
        assertTrue("Transform" in state.document.components)
        state.rename("Keep name")
        scene.undoStructure();assertFalse("Transform" in state.document.components);assertEquals("Keep name",state.document.name)
        scene.redoStructure();assertPose(ScenePose(),ScenePose.from(scene.worldDocument(child)))
    }
    @Test fun worldEditsBecomeLocalAndTopologyUndoRestoresOnlyOperationTransform() {
        val scene=scene();val parent=add(scene,ScenePose(Vec2(10.0,0.0),0.0,Vec2(2.0,2.0)))
        val child=add(scene,ScenePose(Vec2(4.0,3.0)));scene.reparent(child,parent)
        val state=scene.selected!!
        state.document=state.document.addComponent("SpriteRenderer",buildJsonObject { put("visible",false) })
        scene.undoStructure();assertPose(ScenePose(Vec2(4.0,3.0)),ScenePose.from(state.document))
        assertEquals(false,state.document.components.getValue("SpriteRenderer").jsonObject.getValue("visible").jsonPrimitive.boolean)
        scene.redoStructure();scene.applyWorldDocument(child,scene.worldDocument(child).withPose(ScenePose(Vec2(20.0,8.0))))
        assertPose(ScenePose(Vec2(5.0,4.0),0.0,Vec2(0.5,0.5)),ScenePose.from(state.document))
        scene.addEntity();state.document=state.document.withPose(ScenePose(Vec2(99.0,88.0)))
        scene.undoStructure();assertPose(ScenePose(Vec2(99.0,88.0)),ScenePose.from(state.document))
    }
    @Test fun subtreeCopiesRemapInternalReferencesAndDeleteUndoRetainsTopology() {
        val scene=scene();val external=add(scene);val root=add(scene);scene.addChild();val child=scene.selectedKey!!
        scene.selected!!.document=scene.selected!!.document.addComponent("Animator",buildJsonObject { put("targetEntities",buildJsonObject { put("root",root);put("external",external) }) })
        scene.select(root);scene.duplicateSelected();val copiedRoot=scene.selectedKey!!
        val copiedChild=scene.parents.entries.single { it.value==copiedRoot }.key
        val targets=scene.entities.first { it.document.key==copiedChild }.document.components.getValue("Animator").jsonObject.getValue("targetEntities").jsonObject
        assertEquals(copiedRoot,targets.getValue("root").jsonPrimitive.content);assertEquals(external,targets.getValue("external").jsonPrimitive.content)
        scene.removeSelected();assertFalse(scene.entities.any { it.document.key==copiedChild });scene.undoStructure()
        assertEquals(copiedRoot,scene.parents[copiedChild]);assertEquals(root,scene.parents[child])
    }
    @Test fun explicitReparentEndsOldLocalGestureAndEscapePreservesWorldPose() {
        val scene=scene();val parent=add(scene,ScenePose(Vec2(10.0,0.0),0.0,Vec2(2.0,2.0)))
        val child=add(scene,ScenePose(Vec2(2.0,3.0)));val state=scene.selected!!
        val camera=PreviewCamera(Vec2(0.0,0.0),Vec2(20.0,20.0),400,400)
        val point=camera.screen(Vec2(2.0,3.0));val controller=ViewportGizmos(state,true,scene);val canvas=java.awt.Canvas()
        controller.press(java.awt.event.MouseEvent(canvas,java.awt.event.MouseEvent.MOUSE_PRESSED,0,0,point.x.toInt(),point.y.toInt(),1,false,1),camera)
        assertTrue(controller.dragging);scene.reparent(child,parent);assertFalse(state.gestureActive)
        controller.move(java.awt.event.MouseEvent(canvas,java.awt.event.MouseEvent.MOUSE_DRAGGED,0,0,point.x.toInt()+40,point.y.toInt(),1,false,0));controller.key(java.awt.event.KeyEvent(canvas,java.awt.event.KeyEvent.KEY_PRESSED,0,0,java.awt.event.KeyEvent.VK_ESCAPE,java.awt.event.KeyEvent.CHAR_UNDEFINED))
        assertEquals(2.0,ScenePose.from(scene.worldDocument(child)).position.x,1e-8)
        assertEquals(-4.0,ScenePose.from(state.document).position.x,1e-8)
    }
    @Test fun reparentRefreshesChangedDraftsAndPreservesUnrelatedInvalidInput() {
        val scene=scene();val parent=add(scene,ScenePose(Vec2(10.0,0.0)))
        val child=add(scene);val state=scene.selected!!
        state.editField("Transform",listOf("position","x"),JsonPrimitive(0.0),"5")
        state.editField("Transform",listOf("position","y"),JsonPrimitive(0.0),"-")
        scene.reparent(child,parent)
        assertEquals(-5.0,ScenePose.from(state.document).position.x)
        assertFalse("Transform/position/x" in state.drafts)
        assertEquals("-",state.drafts["Transform/position/y"])
        assertTrue("Transform/position/y" in state.fieldErrors)
        scene.undoStructure();assertEquals(5.0,ScenePose.from(state.document).position.x)
        scene.redoStructure();assertEquals(-5.0,ScenePose.from(state.document).position.x)
        assertEquals("-",state.drafts["Transform/position/y"])
    }
    @Test fun removedEntityControllerIgnoresFramesAndInputBeforeUiRebinds() {
        val scene=scene();add(scene);val state=scene.selected!!
        val controller=ViewportGizmos(state,true,scene)
        scene.removeSelected()
        assertNull(controller.selected())
        val before=state.document
        val canvas=java.awt.Canvas()
        controller.press(java.awt.event.MouseEvent(canvas,java.awt.event.MouseEvent.MOUSE_PRESSED,0,0,10,10,1,false,1),PreviewCamera(Vec2(0.0,0.0),Vec2(20.0,20.0),400,400))
        controller.key(java.awt.event.KeyEvent(canvas,java.awt.event.KeyEvent.KEY_PRESSED,0,0,java.awt.event.KeyEvent.VK_RIGHT,java.awt.event.KeyEvent.CHAR_UNDEFINED))
        assertEquals(before,state.document)
    }
    @Test fun activeCameraFlagsUndoAndGeometryIgnoresTransformScale() {
        val scene=scene();scene.addCamera();val first=scene.selectedKey!!;scene.addCamera();val second=scene.selectedKey!!
        scene.setActiveCamera(second);assertEquals(second,scene.activeCamera!!.document.key)
        scene.undoStructure();assertEquals(first,scene.activeCamera!!.document.key);scene.redoStructure();assertEquals(second,scene.activeCamera!!.document.key)
        val document=scene.worldDocument(second).withPose(ScenePose(Vec2(3.0,4.0),PI/2,Vec2(-20.0,0.0)))
        val outline=cameraOutline(document);assertEquals(5,outline.size)
        assertEquals(138.0,outline.first().x,1e-8);assertEquals(-236.0,outline.first().y,1e-8)
    }
}
