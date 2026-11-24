package sle.editor.viewport.tools

import sle.editor.model.SceneWorkspaceState
import sle.editor.viewport.Bounds
import sle.editor.viewport.DragAxis
import sle.editor.viewport.PreviewCamera
import sle.editor.viewport.ScenePicking
import sle.editor.viewport.Vec2
import sle.editor.viewport.entityScreenBounds
import java.awt.Cursor
import java.awt.event.KeyEvent
import kotlin.math.abs

/** Entities whose screen-space selection bounds intersect the marquee from [from] to [to], in scene order. */
internal fun marqueeHits(scene: SceneWorkspaceState, camera: PreviewCamera, from: Vec2, to: Vec2): List<String> {
    val box = Bounds.of(listOf(from, to)) ?: return emptyList()
    return scene.entities.map { it.document.key }.filter { key ->
        entityScreenBounds(scene.worldDocument(key), camera)?.intersects(box) == true
    }
}

/** Shared by every Scene tool: click picks, Shift/Ctrl-click toggles, empty space starts a marquee and a selected body drags. */
internal abstract class PickingTool : ViewportTool {
    private data class Marquee(val from: Vec2, val to: Vec2, val additive: Boolean)
    private var marquee: Marquee? = null
    /** A plain press on one entity of a multi-selection keeps the set so it can be dragged; a
     * release without a drag then selects that entity alone. */
    private var pendingSingle: String? = null
    private var pressPoint: Vec2? = null
    private var moved = false
    /** The marquee in viewport pixels while one is drawn. */
    val marqueeRect: Pair<Vec2, Vec2>? get() = marquee?.takeIf { moved }?.let { it.from to it.to }

    /** A tool's own handles win over picking; true when one started a drag. */
    protected open fun pressHandle(host: ToolHost, pointer: ToolPointer): Boolean = false

    override fun cursor(host: ToolHost, point: Vec2) = Cursor.DEFAULT_CURSOR
    override fun press(host: ToolHost, pointer: ToolPointer): ToolPress {
        reset()
        val scene = host.scene ?: return ToolPress.None
        val camera = host.camera ?: return ToolPress.None
        pressPoint = pointer.point
        if(pressHandle(host, pointer)) return ToolPress.None
        val pick = ScenePicking.pick(scene, camera, pointer.point)
        val additive = pointer.shift || pointer.ctrl
        if(pick == null) {
            // Empty space: a plain press clears the selection; dragging draws a marquee.
            if(!additive) { scene.select(null);host.selectionChanged() }
            marquee = Marquee(pointer.point, pointer.point, additive)
            return ToolPress.None
        }
        if(additive) {
            // Shift/Ctrl-click toggles the picked entity and starts no drag.
            scene.toggleSelection(pick.key);host.selectionChanged()
            return ToolPress.None
        }
        if(pick.key in scene.selection && scene.selection.size > 1) { scene.activate(pick.key);pendingSingle = pick.key }
        else scene.select(pick.key)
        host.selectionChanged()
        host.gizmos()?.press(pointer.point, camera)
        // Saving as a prefab by dragging out works on one entity only.
        return ToolPress(dragEntity = pick.key.takeIf { scene.selection.size == 1 })
    }
    override fun drag(host: ToolHost, pointer: ToolPointer) {
        if(pressPoint?.let { (it-pointer.point).length() > 3 } == true) moved = true
        marquee?.let { marquee = it.copy(to = pointer.point);return }
        host.gizmos()?.move(pointer.point, pointer.shift, pointer.ctrl)
    }
    override fun release(host: ToolHost, pointer: ToolPointer) {
        marquee?.let { marquee = it.copy(to = pointer.point) }
        finish(host)
    }
    override fun finish(host: ToolHost) {
        val box = marquee
        val single = pendingSingle
        val scene = host.scene
        val camera = host.camera
        val wasMoved = moved
        reset()
        if(box != null) {
            if(wasMoved && scene != null && camera != null) {
                scene.selectBox(marqueeHits(scene, camera, box.from, box.to), box.additive)
                host.selectionChanged()
            }
            return
        }
        host.gizmos()?.finish()
        if(single != null && !wasMoved) scene?.let { it.select(single);host.selectionChanged() }
    }
    override fun cancel(host: ToolHost) {
        val box = marquee
        reset()
        if(box == null) host.gizmos()?.cancel()
    }
    override fun key(host: ToolHost, event: KeyEvent): Boolean {
        // Arrow nudges act on the selection; ViewportGizmos owns their step and undo merging.
        val gizmos = host.gizmos() ?: return false
        gizmos.key(event)
        return event.keyCode in setOf(KeyEvent.VK_LEFT, KeyEvent.VK_RIGHT, KeyEvent.VK_UP, KeyEvent.VK_DOWN)
    }
    override fun gestureHint(host: ToolHost): String? {
        if(marquee != null) return if(moved) "Box select: release to select${if(marquee!!.additive) " (adds to the selection)" else ""}; Esc cancels" else null
        val gizmos = host.gizmos()?.takeIf { it.dragging } ?: return null
        val axis = gizmos.constrainedAxis?.let { " along $it" } ?: ""
        val snap = host.snap.settings
        val ctrl = if(snap.enabled) "Ctrl moves freely" else "Ctrl snaps to ${formatStep(snap.move)}"
        return "Moving$axis: Shift locks the main axis; $ctrl; Esc cancels"
    }
    override fun draw(host: ToolHost, overlay: ToolOverlay) { marqueeRect?.let { (from, to) -> overlay.marquee(from, to) } }
    private fun reset() { marquee = null;pendingSingle = null;pressPoint = null;moved = false }
}

internal fun formatStep(value: Double): String =
    if(value == Math.rint(value) && abs(value) < 1e9) value.toLong().toString() else value.toBigDecimal().stripTrailingZeros().toPlainString()

private fun snapHint(ctx: ToolHintContext) =
    if(ctx.snap.enabled) "snapping to ${formatStep(ctx.snap.move)} (Ctrl moves freely)" else "Ctrl snaps to ${formatStep(ctx.snap.move)}"

private fun pivotHint(ctx: ToolHintContext) =
    if(ctx.pivot == PivotMode.Pivot) "around the active entity's pivot" else "around the selection center"

/** The status-bar hint of a tool while no gesture runs. Rotation steps are shown in degrees. */
internal fun toolHint(id: ViewportToolId, ctx: ToolHintContext) = when(id) {
    ViewportToolId.Tiles -> "Tiles · Choose a layer and paint mode"
    ViewportToolId.Select -> "Select: click picks; Shift/Ctrl+click adds or removes; drag empty space to box-select; drag a selection to move it; ${snapHint(ctx)}"
    ViewportToolId.Move -> "Move: drag an arrow for one axis or the square to move freely; Shift locks the main axis; ${snapHint(ctx)}"
    ViewportToolId.Rotate -> "Rotate: drag the ring to turn the selection ${pivotHint(ctx)}; " +
        if(ctx.snap.enabled) "snapping to ${formatStep(ctx.snap.rotateDegrees)}° (Ctrl rotates freely)" else "Ctrl snaps to ${formatStep(ctx.snap.rotateDegrees)}°"
    ViewportToolId.Scale -> "Scale: drag an axis handle, or the center box for uniform scale, ${pivotHint(ctx)}; Shift scales uniformly; " +
        if(ctx.snap.enabled) "snapping to ${formatStep(ctx.snap.scale)} (Ctrl scales freely)" else "Ctrl snaps to ${formatStep(ctx.snap.scale)}"
}

/** Q: pick, toggle, box-select, and drag a selection by its body. */
internal class SelectTool : PickingTool() {
    override val id = ViewportToolId.Select
    override fun hint(ctx: ToolHintContext) = toolHint(id, ctx)
}

/** W: X/Y arrows and a free-move square on the active entity; body drags still move the selection. */
internal class MoveTool : PickingTool() {
    override val id = ViewportToolId.Move
    override fun hint(ctx: ToolHintContext) = toolHint(id, ctx)
    private fun origin(host: ToolHost): Vec2? {
        val camera = host.camera ?: return null
        val shape = host.gizmos()?.entityShape() ?: return null
        return camera.screen(shape.position).takeIf { it.x.isFinite() && it.y.isFinite() }
    }
    override fun pressHandle(host: ToolHost, pointer: ToolPointer): Boolean {
        val origin = origin(host) ?: return false
        val axis = MoveHandles.hit(origin, pointer.point) ?: return false
        return host.gizmos()?.pressMove(pointer.point, host.camera ?: return false, axis) == true
    }
    override fun cursor(host: ToolHost, point: Vec2): Int =
        origin(host)?.let { MoveHandles.hit(it, point) }?.let { Cursor.MOVE_CURSOR } ?: Cursor.DEFAULT_CURSOR
    override fun handles(host: ToolHost): List<ToolHandle> = origin(host)?.let(MoveHandles::handles) ?: emptyList()
    override fun draw(host: ToolHost, overlay: ToolOverlay) {
        origin(host)?.let { overlay.moveHandles(it, host.gizmos()?.takeIf { gizmos -> gizmos.dragging }?.let { gizmos -> gizmos.constrainedAxis ?: DragAxis.Free }) }
        super.draw(host, overlay)
    }
}
