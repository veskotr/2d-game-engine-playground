package sle.editor.viewport

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.math.BigDecimal
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.sign

/**
 * Snap steps, per user and per project (`.sle/editor-state.json`; standalone mode keeps them in
 * user-local prefs). Snapping is off by default. Rotation is in degrees here; scenes store radians.
 */
internal data class SnapSettings(
    val enabled: Boolean = false,
    val move: Double = 1.0,
    val rotateDegrees: Double = 15.0,
    val scale: Double = 0.1,
) {
    init {
        require(move.isFinite() && move in MOVE_RANGE) { "Move step must be between ${MOVE_RANGE.start} and ${MOVE_RANGE.endInclusive}" }
        require(rotateDegrees.isFinite() && rotateDegrees in ROTATE_RANGE) { "Rotate step must be between ${ROTATE_RANGE.start}° and ${ROTATE_RANGE.endInclusive}°" }
        require(scale.isFinite() && scale in SCALE_RANGE) { "Scale step must be between ${SCALE_RANGE.start} and ${SCALE_RANGE.endInclusive}" }
    }
    companion object {
        val MOVE_RANGE = 0.001..10_000.0
        val ROTATE_RANGE = 0.01..360.0
        val SCALE_RANGE = 0.001..100.0
        /** Every Nth grid line is a major line. */
        const val GRID_MAJOR_EVERY = 10
    }
}

/** Which world axes a constrained move changes, and therefore snaps. */
internal enum class SnapAxes { Both, X, Y }

/** Pure snapping math; values are cleaned to the step's decimals (3 × 0.1 is 0.3, never 0.30000000000000004). */
internal object SnapMath {
    /** Nearest multiple of [step]; halves round away from zero, so snapping is symmetric around 0. */
    fun value(value: Double, step: Double): Double {
        if(!value.isFinite() || step <= 0.0) return value
        val ratio = value / step
        return multiple(sign(ratio) * floor(abs(ratio) + 0.5), step)
    }
    fun point(point: Vec2, step: Double, axes: SnapAxes = SnapAxes.Both) = Vec2(
        if(axes != SnapAxes.Y) value(point.x, step) else point.x,
        if(axes != SnapAxes.X) value(point.y, step) else point.y)
    /** The next grid line from [value] in [direction] (+1 or -1); an off-grid value moves to the nearest line that way. */
    fun next(value: Double, step: Double, direction: Int): Double {
        if(!value.isFinite() || step <= 0.0 || direction == 0) return value
        val ratio = value / step
        val tolerance = 1e-9 * maxOf(1.0, abs(ratio))
        return multiple(if(direction > 0) floor(ratio + tolerance) + 1 else ceil(ratio - tolerance) - 1, step)
    }
    private fun multiple(count: Double, step: Double): Double {
        if(abs(count) > 1e15) return count * step
        val exact = BigDecimal.valueOf(step).multiply(BigDecimal.valueOf(count.toLong())).toDouble()
        return exact + 0.0 // never -0.0
    }
}

/**
 * The editor's one snapping service. Ctrl held during a drag (or a nudge) inverts the toggle for
 * that gesture. Positions are snapped in world space; callers convert to local through the
 * parent pose afterwards. The grid follows [SnapSettings.move].
 */
internal class SnapService(initial: SnapSettings = SnapSettings()) {
    var settings by mutableStateOf(initial)
        private set
    /** Persists a user change; the workspace sets it. Loading never calls it. */
    var onChange: (SnapSettings) -> Unit = {}

    /** A user change: stored and persisted. */
    fun update(value: SnapSettings) { if(value != settings) { settings = value;onChange(value) } }
    /** Settings read from the project or user prefs; not a change to persist. */
    fun load(value: SnapSettings) { settings = value }
    fun toggle() = update(settings.copy(enabled = !settings.enabled))

    /** Snapping applies: the toggle, inverted while Ctrl is held. */
    fun active(invert: Boolean = false) = settings.enabled != invert
    /** The move step while snapping applies, else null. */
    fun moveStep(invert: Boolean = false): Double? = settings.move.takeIf { active(invert) }
    /** Where click-to-create and asset drops place a new entity. */
    fun placement(world: Vec2, invert: Boolean = false): Vec2 = moveStep(invert)?.let { SnapMath.point(world, it) } ?: world
    val gridSpacing get() = settings.move
}
