package sle.editor.viewport.tools

import sle.editor.model.EntityDocument
import sle.editor.model.ScenePose
import sle.editor.model.SceneWorkspaceState
import sle.editor.model.withPose
import sle.editor.viewport.Bounds
import sle.editor.viewport.DragAxis
import sle.editor.viewport.PreviewCamera
import sle.editor.viewport.SnapMath
import sle.editor.viewport.SnapService
import sle.editor.viewport.SnapSettings
import sle.editor.viewport.Vec2
import sle.editor.viewport.entityWorldBounds
import java.awt.Cursor
import java.awt.event.KeyEvent
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Rotate and Scale math in world space. Scenes store rotation in radians; snapping works in
 * degrees on the world rotation. Scale never crosses zero and keeps its sign (mirroring).
 * Callers write world poses, which `SceneWorkspaceState.applyWorldDocument` converts to local
 * through the parent pose.
 */
internal object TransformMath {
    /** The smallest scale magnitude the Scale tool writes. */
    const val MIN_SCALE = 0.001

    /** [pose] turned by [delta] radians around [pivot]: its position orbits the pivot. */
    fun rotateAround(pose: ScenePose, pivot: Vec2, delta: Double) =
        ScenePose(pivot+(pose.position-pivot).rotate(delta), pose.rotation+delta, pose.scale)

    /** The world rotation after turning [start] by [turned] radians; with [stepDegrees] it lands on a multiple of the step. */
    fun rotationTarget(start: Double, turned: Double, stepDegrees: Double?): Double =
        stepDegrees?.let { Math.toRadians(SnapMath.value(Math.toDegrees(start+turned), it)) } ?: (start+turned)

    /**
     * [start] multiplied by [factor] without crossing zero: the sign of [start] is kept, and the
     * magnitude snaps to [step] (when given) and never drops below the step or [MIN_SCALE].
     * A zero scale stays zero.
     */
    fun scaleValue(start: Double, factor: Double, step: Double?): Double {
        if(start == 0.0 || !start.isFinite()) return start
        val raw = abs(start)*factor
        if(!raw.isFinite()) return start
        val magnitude = step?.let { SnapMath.value(raw, it) } ?: raw
        return (if(start < 0) -1.0 else 1.0)*max(magnitude, max(step ?: 0.0, MIN_SCALE)) + 0.0
    }

    /**
     * [pose] scaled by [factor] along the axes of [frame] (radians) around [pivot]: its offset from
     * the pivot scales in that frame and its own scale per axis, guarded like [scaleValue].
     */
    fun scaleAround(pose: ScenePose, pivot: Vec2, frame: Double, factor: Vec2): ScenePose {
        val offset = (pose.position-pivot).rotate(-frame)
        fun axis(value: Double, by: Double) = if(by == 1.0) value else scaleValue(value, by, null)
        return ScenePose(pivot+Vec2(offset.x*factor.x, offset.y*factor.y).rotate(frame), pose.rotation,
            Vec2(axis(pose.scale.x, factor.x), axis(pose.scale.y, factor.y)))
    }

    /** The raw factor of a Scale drag: each [length] pixels moved along [direction] (a unit vector) adds 1. */
    fun dragFactor(delta: Vec2, direction: Vec2, length: Double) = 1.0+(delta.x*direction.x+delta.y*direction.y)/length

    /** [angle] wrapped into (-π, π]. */
    fun wrap(angle: Double): Double {
        var value = angle % (2*PI)
        if(value > PI) value -= 2*PI
        if(value <= -PI) value += 2*PI
        return value
    }
}

/**
 * What a Rotate or Scale drag acts on: the [active] entity decides the snapped amount, [roots]
 * (the top-most selected entities with a Transform) are written, and everything turns or scales
 * around [pivot] along the active entity's world [rotation]. [scale] is the active world scale.
 */
internal data class TransformFrame(val active: String, val pivot: Vec2, val rotation: Double, val scale: Vec2, val roots: List<String>)

/** The frame of the scene's selection; null without an active entity that has a Transform. */
internal fun transformFrame(scene: SceneWorkspaceState, mode: PivotMode): TransformFrame? {
    val active = scene.selectedKey ?: return null
    val state = scene.entity(active) ?: return null
    if("Transform" !in state.document.components) return null
    val pose = ScenePose.from(scene.worldDocument(active))
    val selected = scene.selection.keys.filter { scene.entity(it) != null }
    val roots = (if(selected.size > 1) scene.selectionRoots() else listOf(active))
        .filter { scene.entity(it)?.document?.components?.containsKey("Transform") == true }
    if(roots.isEmpty()) return null
    val pivot = if(mode == PivotMode.Pivot) pose.position
        else selected.mapNotNull { entityWorldBounds(scene.worldDocument(it)) }.reduceOrNull(Bounds::union)?.center ?: pose.position
    if(!pivot.x.isFinite() || !pivot.y.isFinite()) return null
    return TransformFrame(active, pivot, pose.rotation, pose.scale, roots)
}

/** Rotate tool ring geometry, in viewport pixels around the pivot. */
internal object RotateHandles {
    const val RADIUS = 60.0
    const val TOLERANCE = 6.0
    fun hit(center: Vec2, point: Vec2) = abs((point-center).length()-RADIUS) <= TOLERANCE
    fun handles(center: Vec2) = listOf(ToolHandle(DragAxis.Free, center, center+Vec2(RADIUS, 0.0), ToolHandleKind.Ring))
}

/** Scale tool handle geometry, in viewport pixels: axis lines along the active entity's rotation and a centre box. */
internal object ScaleHandles {
    const val LENGTH = 64.0
    const val CENTER = 7.0
    const val TOLERANCE = 7.0
    /** Screen directions of the frame's X and Y axes (screen Y points down). */
    fun directions(frame: TransformFrame, camera: PreviewCamera): Pair<Vec2, Vec2> {
        val origin = camera.screen(frame.pivot)
        fun unit(world: Vec2, fallback: Vec2): Vec2 {
            val delta = camera.screen(frame.pivot+world)-origin
            val length = delta.length()
            return if(length > 1e-9 && length.isFinite()) delta*(1.0/length) else fallback
        }
        return unit(Vec2(1.0, 0.0).rotate(frame.rotation), Vec2(1.0, 0.0)) to unit(Vec2(0.0, 1.0).rotate(frame.rotation), Vec2(0.0, -1.0))
    }
    fun handles(origin: Vec2, x: Vec2, y: Vec2) = listOf(
        ToolHandle(DragAxis.Free, origin-Vec2(CENTER, CENTER), origin+Vec2(CENTER, CENTER), ToolHandleKind.Scale),
        ToolHandle(DragAxis.X, origin+x*CENTER, origin+x*LENGTH, ToolHandleKind.Scale),
        ToolHandle(DragAxis.Y, origin+y*CENTER, origin+y*LENGTH, ToolHandleKind.Scale),
    )
    /** The handle under [point]: the centre box first, then the nearer axis line. */
    fun hit(origin: Vec2, x: Vec2, y: Vec2, point: Vec2): DragAxis? {
        val delta = point-origin
        if(abs(delta.x) <= CENTER+1 && abs(delta.y) <= CENTER+1) return DragAxis.Free
        fun distance(direction: Vec2): Double? {
            val along = delta.x*direction.x+delta.y*direction.y
            val across = abs(delta.x*direction.y-delta.y*direction.x)
            return across.takeIf { along in CENTER..LENGTH+TOLERANCE && across <= TOLERANCE }
        }
        val alongX = distance(x)
        val alongY = distance(y)
        return when {
            alongX != null && alongY != null -> if(alongX <= alongY) DragAxis.X else DragAxis.Y
            alongX != null -> DragAxis.X
            alongY != null -> DragAxis.Y
            else -> null
        }
    }
    /** Screen direction of the uniform centre box: right and up grow. */
    val UNIFORM = Vec2(1.0, -1.0)*(1.0/sqrt(2.0))
}

/**
 * One Rotate or Scale drag over the selection, from press to commit. Every root's gesture opens
 * at the press; the drag commits as one `History.group` step ("Rotate Player", "Scale 3
 * entities") or cancels back to the press. Snapping (Ctrl inverts it per update) applies to the
 * active entity's world rotation or scale, and the others follow by the same amount.
 */
internal class TransformDrag(private val scene: SceneWorkspaceState, val tool: ViewportToolId, val frame: TransformFrame,
    val handle: DragAxis, private val press: Vec2, private val camera: PreviewCamera) {
    private val starts: Map<String, EntityDocument> = frame.roots.associateWith(scene::worldDocument)
    private val activeStart = ScenePose.from(scene.worldDocument(frame.active))
    private val pivotPixel = camera.screen(frame.pivot)
    private var lastAngle = worldAngle(press)
    private var turned = 0.0
    private var lastShift = false
    private var lastCtrl = false
    /** The applied rotation change in radians (Rotate). */
    var rotation = 0.0; private set
    /** The active entity's world scale now (Scale). */
    var scale = activeStart.scale; private set
    /** Screen angles (radians, screen Y down) of the press and of the applied rotation, for the ring's spokes. */
    val sweep: Pair<Double, Double> get() {
        val start = atan2(press.y-pivotPixel.y, press.x-pivotPixel.x)
        return start to start-rotation
    }

    init { frame.roots.forEach { scene.entity(it)?.beginGesture() } }

    private fun worldAngle(pixel: Vec2): Double {
        val offset = camera.world(pixel)-frame.pivot
        return atan2(offset.y, offset.x)
    }
    /** Every written entity still exists and its gesture is open (a structural change ends them). */
    fun live() = frame.roots.all { scene.entity(it)?.gestureActive == true }

    /** Applies the drag to [pointer]; false when the drag can no longer run. */
    fun update(pointer: ToolPointer, snap: SnapService): Boolean {
        if(!live()) return false
        lastShift = pointer.shift;lastCtrl = pointer.ctrl
        if(tool == ViewportToolId.Rotate) {
            val angle = worldAngle(pointer.point)
            turned += TransformMath.wrap(angle-lastAngle);lastAngle = angle
            val target = TransformMath.rotationTarget(activeStart.rotation, turned, snap.rotateStep(pointer.ctrl))
            rotation = target-activeStart.rotation
            write { key, pose -> TransformMath.rotateAround(pose, frame.pivot, rotation).let { if(key == frame.active) it.copy(rotation = target) else it } }
        } else {
            val (x, y) = ScaleHandles.directions(frame, camera)
            val delta = pointer.point-press
            val raw = TransformMath.dragFactor(delta, when(handle) { DragAxis.X -> x; DragAxis.Y -> y; DragAxis.Free -> ScaleHandles.UNIFORM }, ScaleHandles.LENGTH)
            val uniform = handle == DragAxis.Free || pointer.shift
            val step = snap.scaleStep(pointer.ctrl)
            val target = Vec2(
                if(uniform || handle == DragAxis.X) TransformMath.scaleValue(activeStart.scale.x, raw, step) else activeStart.scale.x,
                if(uniform || handle == DragAxis.Y) TransformMath.scaleValue(activeStart.scale.y, raw, step) else activeStart.scale.y)
            scale = target
            val factor = Vec2(target.x/activeStart.scale.x, target.y/activeStart.scale.y)
            write { key, pose -> TransformMath.scaleAround(pose, frame.pivot, frame.rotation, factor).let { if(key == frame.active) it.copy(scale = target) else it } }
        }
        return true
    }
    private fun write(pose: (String, ScenePose) -> ScenePose) {
        try { starts.forEach { (key, start) -> scene.applyWorldDocument(key, start.withPose(pose(key, ScenePose.from(start)))) } }
        catch(failure: IllegalArgumentException) { scene.entity(frame.active)?.error = failure.message }
    }
    val label get() = "${tool.label} ${scene.subjects(frame.roots)}"
    /** Commits the drag as one undo step; an unchanged drag records nothing. When Undo (which
     * cancels one open gesture) or a structural change ended part of it, the rest is cancelled
     * too, so no partial step is recorded. */
    fun finish() {
        if(!live()) { cancel();return }
        scene.history.group(label) { frame.roots.forEach { scene.entity(it)?.endGesture() } }
    }
    fun cancel() = frame.roots.forEach { scene.entity(it)?.cancelGesture() }

    fun hint(snap: SnapSettings): String {
        val subject = scene.subjects(frame.roots)
        val around = when {
            frame.pivot != activeStart.position -> " around the selection center"
            frame.roots.size > 1 -> " around the pivot"
            else -> ""
        }
        return if(tool == ViewportToolId.Rotate) {
            val free = if(snap.enabled != lastCtrl) "snapping to ${formatStep(snap.rotateDegrees)}°" else "free"
            val ctrl = if(snap.enabled) "Ctrl rotates freely" else "Ctrl snaps to ${formatStep(snap.rotateDegrees)}°"
            "Rotating $subject by ${signedDegrees(rotation)}° to ${degrees(activeStart.rotation+rotation)}°$around ($free): $ctrl; Esc cancels"
        } else {
            val free = if(snap.enabled != lastCtrl) "snapping to ${formatStep(snap.scale)}" else "free"
            val ctrl = if(snap.enabled) "Ctrl scales freely" else "Ctrl snaps to ${formatStep(snap.scale)}"
            val uniform = if(handle == DragAxis.Free || lastShift) "uniform" else "along ${handle.name}"
            "Scaling $subject $uniform to ${rounded(scale.x)} × ${rounded(scale.y)}$around ($free): Shift scales uniformly; $ctrl; Esc cancels"
        }
    }
}

private fun rounded(value: Double, places: Int = 3): String =
    formatStep(java.math.BigDecimal.valueOf(value).setScale(places, java.math.RoundingMode.HALF_UP).toDouble()+0.0)
/** Radians shown as degrees, to two decimals. */
internal fun degrees(radians: Double) = rounded(Math.toDegrees(radians), 2)
private fun signedDegrees(radians: Double) = degrees(radians).let { if(it.startsWith("-") || it == "0") it else "+$it" }

/**
 * E Rotate and R Scale. A handle drag turns or scales the selection around the pivot (Pivot:
 * the active entity's position; Center: the selection bounds centre); clicks, Shift/Ctrl
 * toggles, marquees and body drags work as in Select.
 */
internal abstract class TransformTool : PickingTool() {
    protected var transform: TransformDrag? = null
        private set
    protected abstract fun hit(frame: TransformFrame, camera: PreviewCamera, point: Vec2): DragAxis?
    /** The frame of the drag in progress, else of the current selection. */
    protected fun frame(host: ToolHost): TransformFrame? = transform?.frame ?: host.scene?.let { transformFrame(it, host.state.pivot) }

    override fun pressHandle(host: ToolHost, pointer: ToolPointer): Boolean {
        val scene = host.scene ?: return false
        val camera = host.camera ?: return false
        if(scene.selected?.fieldErrors?.isEmpty() != true || host.gizmos()?.dragging == true) return false
        val frame = transformFrame(scene, host.state.pivot) ?: return false
        val axis = hit(frame, camera, pointer.point) ?: return false
        transform = TransformDrag(scene, id, frame, axis, pointer.point, camera)
        return true
    }
    override fun drag(host: ToolHost, pointer: ToolPointer) {
        val active = transform ?: return super.drag(host, pointer)
        if(!active.update(pointer, host.snap)) { transform = null;active.cancel() }
    }
    override fun release(host: ToolHost, pointer: ToolPointer) {
        val active = transform ?: return super.release(host, pointer)
        transform = null;active.finish()
    }
    override fun finish(host: ToolHost) {
        val active = transform ?: return super.finish(host)
        transform = null;active.finish()
    }
    override fun cancel(host: ToolHost) {
        val active = transform ?: return super.cancel(host)
        transform = null;active.cancel()
    }
    /** Arrow nudges wait until the handle drag ends. */
    override fun key(host: ToolHost, event: KeyEvent): Boolean = if(transform != null) true else super.key(host, event)
    override fun gestureHint(host: ToolHost): String? = transform?.hint(host.snap.settings) ?: super.gestureHint(host)
    override fun cursor(host: ToolHost, point: Vec2): Int {
        val camera = host.camera ?: return Cursor.DEFAULT_CURSOR
        return frame(host)?.let { hit(it, camera, point) }?.let { Cursor.HAND_CURSOR } ?: Cursor.DEFAULT_CURSOR
    }
}

/** E: a ring around the pivot; dragging it turns the selection, and other selected entities orbit the pivot. */
internal class RotateTool : TransformTool() {
    override val id = ViewportToolId.Rotate
    override fun hint(ctx: ToolHintContext) = toolHint(id, ctx)
    override fun hit(frame: TransformFrame, camera: PreviewCamera, point: Vec2) =
        DragAxis.Free.takeIf { RotateHandles.hit(camera.screen(frame.pivot), point) }
    override fun handles(host: ToolHost): List<ToolHandle> {
        val camera = host.camera ?: return emptyList()
        return frame(host)?.let { RotateHandles.handles(camera.screen(it.pivot)) } ?: emptyList()
    }
    override fun draw(host: ToolHost, overlay: ToolOverlay) {
        val camera = host.camera
        val frame = frame(host)
        if(camera != null && frame != null) overlay.rotateRing(camera.screen(frame.pivot), RotateHandles.RADIUS, transform != null, transform?.sweep)
        super.draw(host, overlay)
    }
}

/** R: X/Y handles along the active entity's axes and a uniform centre box; Shift makes any drag uniform. */
internal class ScaleTool : TransformTool() {
    override val id = ViewportToolId.Scale
    override fun hint(ctx: ToolHintContext) = toolHint(id, ctx)
    /** A zero world scale has no factor to scale by; its handles are hidden. */
    private fun scalable(frame: TransformFrame) = abs(frame.scale.x) > 1e-8 && abs(frame.scale.y) > 1e-8
    override fun hit(frame: TransformFrame, camera: PreviewCamera, point: Vec2): DragAxis? {
        if(!scalable(frame)) return null
        val (x, y) = ScaleHandles.directions(frame, camera)
        return ScaleHandles.hit(camera.screen(frame.pivot), x, y, point)
    }
    override fun handles(host: ToolHost): List<ToolHandle> {
        val camera = host.camera ?: return emptyList()
        val frame = frame(host)?.takeIf(::scalable) ?: return emptyList()
        val (x, y) = ScaleHandles.directions(frame, camera)
        return ScaleHandles.handles(camera.screen(frame.pivot), x, y)
    }
    override fun draw(host: ToolHost, overlay: ToolOverlay) {
        val camera = host.camera
        val frame = frame(host)?.takeIf(::scalable)
        if(camera != null && frame != null) {
            val (x, y) = ScaleHandles.directions(frame, camera)
            val origin = camera.screen(frame.pivot)
            val drag = transform
            // The handles stretch with the drag, so the dragged box follows the pointer.
            val grow = drag?.let { Vec2(it.scale.x/frame.scale.x, it.scale.y/frame.scale.y) } ?: Vec2(1.0, 1.0)
            overlay.scaleHandles(origin, origin+x*(ScaleHandles.LENGTH*grow.x.coerceIn(0.2, 4.0)), origin+y*(ScaleHandles.LENGTH*grow.y.coerceIn(0.2, 4.0)), drag?.handle)
        }
        super.draw(host, overlay)
    }
}
