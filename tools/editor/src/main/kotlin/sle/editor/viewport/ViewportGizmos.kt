package sle.editor.viewport

import sle.editor.model.EntityDocument
import sle.editor.model.EntityWorkspaceState
import sle.editor.model.SceneWorkspaceState

import java.awt.event.KeyEvent
import java.awt.event.MouseEvent

internal class ViewportGizmos(val state: EntityWorkspaceState, val entityOnly: Boolean = false, val scene: SceneWorkspaceState? = null) {
    private data class Drag(val document: EntityDocument, val target: String, val handle: GizmoHandle,
        val start: Vec2, val camera: PreviewCamera)
    private var drag: Drag? = null
    val dragging get() = drag != null
    private fun attached() = scene==null || scene.entities.any { it===state }
    private fun document() = if(entityOnly && scene!=null) scene.worldDocument(state.document.key) else state.document
    /** In the scene, "Entity" moves the whole entity; a selected shape (collider, sprite, zone) gets its own handles. */
    private fun activeTarget(): String = if(!entityOnly) state.selectedTarget
        else state.sceneTarget.takeIf { it!="Entity" && it in GizmoShape.available(document()) } ?: "Entity"
    /** In the scene a shape exposes only resize handles: dragging its center still moves the entity. */
    private fun sceneHandle(shape: GizmoShape, handle: GizmoHandle) = !entityOnly || shape.target=="Entity" || handle.kind!="move"
    private fun apply(value: EntityDocument, target: String = "Entity") {
        try {
            if(entityOnly && scene!=null && target!="Entity") {
                // Shape fields are entity-local; never round-trip the transform through world space.
                val fields=value.components[target] ?: return
                state.applyGizmo(state.document.copy(components=kotlinx.serialization.json.JsonObject(state.document.components+(target to fields))))
            } else if(entityOnly && scene!=null) scene.applyWorldDocument(state.document.key,value) else state.applyGizmo(value)
        }
        catch(failure: IllegalArgumentException) { state.error=failure.message }
    }
    fun selected(): GizmoShape? = if(attached()) GizmoShape.from(document(),activeTarget()) else null
    fun press(event: MouseEvent, camera: PreviewCamera) {
        if (!attached() || event.button != MouseEvent.BUTTON1 || state.fieldErrors.isNotEmpty()) return
        val point=Vec2(event.x.toDouble(),event.y.toDouble())
        val world=camera.world(point)
        // Scene view: the edited shape's resize handles plus the entity's own move handle.
        val candidates=listOfNotNull(selected(),if(entityOnly && selected()?.target!="Entity") GizmoShape.from(document(),"Entity") else null)
        val hit=candidates.flatMap { candidate -> GizmoMath.handles(candidate).filter { sceneHandle(candidate,it) }.map { candidate to it } }
            .minByOrNull { (candidate,it) -> (camera.screen(GizmoMath.handleWorld(candidate,it))-point).length() }
            ?.takeIf { (candidate,it) -> (camera.screen(GizmoMath.handleWorld(candidate,it))-point).length() <= 10 }
        var shape=hit?.first ?: selected()
        var handle=hit?.second
        if (handle == null) {
            shape=(if(entityOnly && cameraOutlineHit(document(),camera,point)) "Entity" else GizmoMath.pick(document(),camera,point,state.selectedTarget))?.let {
                GizmoShape.from(document(),if(entityOnly) "Entity" else it)
            }
            if (shape == null) return
            if(!entityOnly) { state.selectedTarget=shape.target;state.selectedComponent=if(shape.target=="Entity") "Transform" else shape.target }
            handle=GizmoHandle("move",shape.offset)
        }
        val target=shape ?: return
        state.beginGesture()
        drag=Drag(document(),target.target,handle,world,camera)
    }
    fun move(event: MouseEvent) {
        val active=drag ?: return
        if (!state.gestureActive || state.document.key != active.document.key || (scene!=null && scene.entities.none { it===state })) { drag=null; return }
        val world=active.camera.world(Vec2(event.x.toDouble(),event.y.toDouble()))
        apply(GizmoMath.drag(active.document,active.target,active.handle,active.start,world,state.snap || event.isShiftDown),active.target)
    }
    fun finish() { if (drag != null) { state.endGesture(); drag=null } }
    fun cancel() { if (drag != null) { state.cancelGesture(); drag=null } }
    fun resize(width: Int,height: Int) { drag?.let { if (it.camera.width!=width || it.camera.height!=height) cancel() } }
    fun key(event: KeyEvent) {
        if(!attached()) { cancel();return }
        if (event.keyCode == KeyEvent.VK_ESCAPE) { cancel(); return }
        if (event.isControlDown && event.keyCode == KeyEvent.VK_Z) {
            if (drag != null) { cancel(); return }
            if (event.isShiftDown) state.redo() else state.undo(); return
        }
        if (event.isControlDown && event.keyCode == KeyEvent.VK_Y) {
            if (drag != null) cancel() else state.redo()
            return
        }
        if (drag == null && entityOnly && scene != null && (event.keyCode == KeyEvent.VK_DELETE ||
                (event.isControlDown && event.keyCode == KeyEvent.VK_D))) {
            try { if (event.keyCode == KeyEvent.VK_DELETE) sle.editor.ui.deleteSceneSelection(scene) else scene.duplicateSelected() }
            catch (failure: IllegalArgumentException) { scene.error = failure.message }
            return
        }
        if (drag != null || state.fieldErrors.isNotEmpty()) return
        val amount=if (event.isShiftDown) 1.0 else 0.1
        val delta=when(event.keyCode) {
            KeyEvent.VK_LEFT -> Vec2(-amount,0.0); KeyEvent.VK_RIGHT -> Vec2(amount,0.0)
            KeyEvent.VK_UP -> Vec2(0.0,amount); KeyEvent.VK_DOWN -> Vec2(0.0,-amount)
            else -> return
        }
        val shape=selected() ?: return
        apply(GizmoMath.drag(document(),shape.target,GizmoHandle("move",shape.offset),shape.center,shape.center+delta,state.snap),shape.target)
    }
}
