package sle.editor.viewport

import sle.editor.model.EntityDocument
import sle.editor.model.EntityWorkspaceState
import sle.editor.model.ScenePose
import sle.editor.model.SceneWorkspaceState
import sle.editor.model.withPose

import java.awt.event.KeyEvent
import java.awt.event.MouseEvent

/** Arrow nudges of the same entities and shape merge into one undo step. */
internal data class NudgeMergeKey(val entities: Set<String>, val target: String)

internal class ViewportGizmos(val state: EntityWorkspaceState, val entityOnly: Boolean = false, val scene: SceneWorkspaceState? = null) {
    /** [group]: world documents, at press time, of the other selected subtree roots that move
     * with the active entity; [moveSelf] is false when the active entity follows a selected ancestor. */
    private data class Drag(val document: EntityDocument, val target: String, val handle: GizmoHandle,
        val start: Vec2, val camera: PreviewCamera, val group: Map<String, EntityDocument> = emptyMap(), val moveSelf: Boolean = true)
    private var drag: Drag? = null
    val dragging get() = drag != null
    private fun attached() = scene==null || scene.entities.any { it===state }
    private fun document() = if(entityOnly && scene!=null) scene.worldDocument(state.document.key) else state.document
    /** In the scene, "Entity" moves the whole entity; a selected shape (collider, sprite, zone) gets its own handles. */
    private fun activeTarget(): String = if(!entityOnly) state.selectedTarget
        else state.sceneTarget.takeIf { it!="Entity" && it in GizmoShape.available(document()) } ?: "Entity"
    /** In the scene a shape exposes only resize handles: dragging its center still moves the entity. */
    private fun sceneHandle(shape: GizmoShape, handle: GizmoHandle) = !entityOnly || shape.target=="Entity" || handle.kind!="move"
    private fun apply(value: EntityDocument, target: String = "Entity", mergeKey: Any? = null) {
        try {
            if(entityOnly && scene!=null && target!="Entity") {
                // Shape fields are entity-local; never round-trip the transform through world space.
                val fields=value.components[target] ?: return
                state.applyGizmo(state.document.copy(components=kotlinx.serialization.json.JsonObject(state.document.components+(target to fields))),mergeKey)
            } else if(entityOnly && scene!=null) scene.applyWorldDocument(state.document.key,value,mergeKey) else state.applyGizmo(value,mergeKey)
        }
        catch(failure: IllegalArgumentException) { state.error=failure.message }
    }
    fun selected(): GizmoShape? = if(attached()) GizmoShape.from(document(),activeTarget()) else null
    /** Scene view with several entities selected: the top-most selected roots move together when the
     * active entity moves. Null when the move concerns the active entity alone. */
    private fun movers(): List<String>? {
        if(!entityOnly || scene==null || scene.selection.size<2) return null
        return scene.selectionRoots().takeIf { it != listOf(state.document.key) }
    }
    private fun translated(document: EntityDocument, delta: Vec2) =
        ScenePose.from(document).let { document.withPose(it.copy(position=it.position+delta)) }
    private fun moveGroup(group: Map<String, EntityDocument>, delta: Vec2) {
        val graph=scene ?: return
        try { group.forEach { (key, start) -> graph.applyWorldDocument(key, translated(start, delta)) } }
        catch(failure: IllegalArgumentException) { state.error=failure.message }
    }
    private fun groupLabel(keys: Collection<String>) = "Move ${scene?.subjects(keys) ?: "${keys.size} entities"}"
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
        val roots=if(target.target=="Entity" && handle.kind=="move") movers() else null
        val group=roots.orEmpty().filter { it!=state.document.key }.associateWith { scene!!.worldDocument(it) }
        state.beginGesture()
        group.keys.forEach { scene!!.entity(it)?.beginGesture() }
        drag=Drag(document(),target.target,handle,world,camera,group,roots==null || state.document.key in roots)
    }
    fun move(event: MouseEvent) {
        val active=drag ?: return
        if (!state.gestureActive || state.document.key != active.document.key || (scene!=null && scene.entities.none { it===state })) { drag=null; return }
        val world=active.camera.world(Vec2(event.x.toDouble(),event.y.toDouble()))
        val next=GizmoMath.drag(active.document,active.target,active.handle,active.start,world,state.snap || event.isShiftDown)
        if(active.moveSelf) apply(next,active.target)
        if(active.group.isNotEmpty()) moveGroup(active.group,ScenePose.from(next).position-ScenePose.from(active.document).position)
    }
    /** Ends the drag; moving several entities is one undo step. */
    fun finish() {
        val active=drag ?: return
        drag=null
        val graph=scene
        if(active.group.isEmpty() || graph==null) { state.endGesture();return }
        val moved=active.group.keys+(if(active.moveSelf) listOf(state.document.key) else emptyList())
        graph.history.group(groupLabel(moved)) {
            state.endGesture();active.group.keys.forEach { graph.entity(it)?.endGesture() }
        }
    }
    fun cancel() {
        val active=drag ?: return
        drag=null
        state.cancelGesture();active.group.keys.forEach { scene?.entity(it)?.cancelGesture() }
    }
    fun resize(width: Int,height: Int) { drag?.let { if (it.camera.width!=width || it.camera.height!=height) cancel() } }
    fun key(event: KeyEvent) {
        if(!attached()) { cancel();return }
        if (event.keyCode == KeyEvent.VK_ESCAPE) { cancel(); return }
        if (event.isControlDown && event.keyCode == KeyEvent.VK_Z) {
            if (drag != null) { cancel(); return }
            // Fallback when the Undo/Redo commands are disabled; both use the document's timeline.
            if (event.isShiftDown) state.timeline.redo() else state.timeline.undo(); return
        }
        if (event.isControlDown && event.keyCode == KeyEvent.VK_Y) {
            if (drag != null) cancel() else state.timeline.redo()
            return
        }
        // Delete and Ctrl+D are registered commands; the shortcut dispatcher runs them before this listener.
        if (drag != null || state.fieldErrors.isNotEmpty()) return
        val amount=if (event.isShiftDown) 1.0 else 0.1
        val delta=when(event.keyCode) {
            KeyEvent.VK_LEFT -> Vec2(-amount,0.0); KeyEvent.VK_RIGHT -> Vec2(amount,0.0)
            KeyEvent.VK_UP -> Vec2(0.0,amount); KeyEvent.VK_DOWN -> Vec2(0.0,-amount)
            else -> return
        }
        val shape=selected() ?: return
        val roots=if(shape.target=="Entity") movers() else null
        val graph=scene
        if(roots!=null && graph!=null) {
            // The selection moves as one step, and a burst of nudges on the same set merges into it.
            // A failure (a zero-scale parent) inside the group undoes the entities already moved.
            try { graph.history.group(groupLabel(roots),NudgeMergeKey(roots.toSet(),"Entity")) {
                roots.forEach { key -> graph.worldDocument(key).let { graph.applyWorldDocument(key,translated(it,delta)) } }
            } } catch(failure: IllegalArgumentException) { state.error=failure.message }
            return
        }
        // A burst of nudges on one shape is one undo step (see History.MERGE_WINDOW_MILLIS).
        apply(GizmoMath.drag(document(),shape.target,GizmoHandle("move",shape.offset),shape.center,shape.center+delta,state.snap),shape.target,
            NudgeMergeKey(setOf(state.document.key),shape.target))
    }
}
