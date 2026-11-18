package sle.editor.viewport.tools

import sle.editor.model.SceneWorkspaceState
import sle.editor.viewport.PreviewCamera
import sle.editor.viewport.SnapService
import sle.editor.viewport.Vec2
import sle.editor.viewport.ViewportGizmos
import java.awt.event.KeyEvent

/**
 * Routes Scene view left-button gestures and keys to the active tool. The viewport owner handles
 * right-click menus, panning, zoom and entity drags to Assets before calling it. One gesture
 * runs at a time; switching tools finishes it and Escape cancels it.
 */
internal class ToolHost(val state: ViewportToolState, val snap: SnapService) {
    var scene: SceneWorkspaceState? = null
    var camera: PreviewCamera? = null
    /** Handles of the scene's active entity; the owner rebuilds them after [selectionChanged]. */
    var gizmos: () -> ViewportGizmos? = { null }
    /** Called after a tool changed the selection, so the owner re-targets [gizmos]. */
    var selectionChanged: () -> Unit = {}
    private val tools: Map<ViewportToolId, ViewportTool> = ViewportToolId.entries.associateWith { id ->
        when(id) { ViewportToolId.Select -> SelectTool(); ViewportToolId.Move -> MoveTool() }
    }
    val tool: ViewportTool get() = tools.getValue(state.active)
    private var gesture: ViewportTool? = null
    /** A press was routed to a tool and its release has not arrived. */
    val pressed get() = gesture != null
    init { state.onSwitch = { finish() } }

    fun press(pointer: ToolPointer): ToolPress {
        finish()
        val current = tool
        gesture = current
        return current.press(this, pointer).also { updateHint() }
    }
    fun drag(pointer: ToolPointer) { gesture?.let { it.drag(this, pointer);updateHint() } }
    fun release(pointer: ToolPointer) {
        val current = gesture ?: return
        gesture = null
        current.release(this, pointer)
        updateHint()
    }
    /** Commits the gesture in progress: a tool switch, focus loss, a mode change or a new press. */
    fun finish() {
        val current = gesture ?: return
        gesture = null
        current.finish(this)
        updateHint()
    }
    /** Abandons the gesture in progress; the scene returns to its state at the press. */
    fun cancel() {
        val current = gesture ?: return
        gesture = null
        current.cancel(this)
        updateHint()
    }
    /** Escape cancels a gesture; other keys go to the tool (arrow nudges). */
    fun key(event: KeyEvent): Boolean {
        if(event.keyCode == KeyEvent.VK_ESCAPE && gesture != null) { cancel();return true }
        return tool.key(this, event)
    }
    fun cursor(point: Vec2): Int = tool.cursor(this, point)
    fun handles(): List<ToolHandle> = tool.handles(this)
    fun draw(overlay: ToolOverlay) = tool.draw(this, overlay)
    private fun updateHint() { state.gestureHint = gesture?.gestureHint(this) }
}
