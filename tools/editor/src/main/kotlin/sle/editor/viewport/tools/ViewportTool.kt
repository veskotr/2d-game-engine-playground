package sle.editor.viewport.tools

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import sle.editor.command.KeyChord
import sle.editor.viewport.DragAxis
import sle.editor.viewport.SnapSettings
import sle.editor.viewport.Vec2
import java.awt.event.KeyEvent

/**
 * Scene view tools, Unity style: Q Select, W Move, E Rotate, R Scale. The Rect tool is deferred
 * (see docs/plans/editor-core-l1.md).
 */
internal enum class ViewportToolId(val label: String, val keyCode: Int) {
    Select("Select", KeyEvent.VK_Q),
    Move("Move", KeyEvent.VK_W),
    Rotate("Rotate", KeyEvent.VK_E),
    Scale("Scale", KeyEvent.VK_R),
    Tiles("Tiles", KeyEvent.VK_T);
    val chord get() = KeyChord.plain(keyCode)
}

/** What Rotate and Scale turn around: the active entity's position, or the centre of the selection bounds. */
internal enum class PivotMode(val label: String) { Pivot("Pivot"), Center("Center") }

/**
 * The active Scene view tool: per-user editor state, never an undo step. Switching tools
 * finishes the gesture in progress ([onSwitch], set by the viewport's [ToolHost]).
 */
internal class ViewportToolState(initial: ViewportToolId = ViewportToolId.Select) {
    var active by mutableStateOf(initial)
        private set
    /** Describes the gesture in progress for the status bar; null when idle. */
    var gestureHint by mutableStateOf<String?>(null)
        internal set
    /** Rotate and Scale pivot; like the active tool, per-user editor state and never an undo step. */
    var pivot by mutableStateOf(PivotMode.Pivot)
    val tilePainting = TilePaintingState { onTileChange() }
    internal var onTileChange: () -> Unit = {}
    internal var onSwitch: () -> Unit = {}
    fun activate(id: ViewportToolId) {
        if(id == active) return
        onSwitch()
        active = id
    }
}

/** One pointer event in viewport pixels, with the modifiers the tools use. */
internal data class ToolPointer(val point: Vec2, val shift: Boolean = false, val ctrl: Boolean = false)

/** What a press started: [dragEntity] is the one picked entity, which may be dragged out onto an Assets folder. */
internal data class ToolPress(val dragEntity: String? = null) { companion object { val None = ToolPress() } }

internal enum class ToolHandleKind { Move, Ring, Scale }

/** A tool handle in viewport pixels, for hit tests and drawing. A [ToolHandleKind.Ring] runs
 * from its centre [from] to the point [to] on the ring. */
internal data class ToolHandle(val axis: DragAxis, val from: Vec2, val to: Vec2, val kind: ToolHandleKind = ToolHandleKind.Move)

/** Facts a tool hint depends on. */
internal data class ToolHintContext(val snap: SnapSettings, val selectionCount: Int, val pivot: PivotMode = PivotMode.Pivot)

/** Editor drawing a tool may add after the engine frame (implemented by the GL overlay). */
internal interface ToolOverlay {
    fun tileGhost(image: sle.editor.model.TileStampImage, stamps: List<Pair<Vec2, Vec2>>) {}
    fun tiles(grid: List<Vec2>, stamps: List<Pair<Vec2, Vec2>>, erase: Boolean) {}
    fun moveHandles(origin: Vec2, active: DragAxis?)
    fun marquee(from: Vec2, to: Vec2)
    /** Rotate ring around [center]; while dragging, spokes at the [sweep] start and current screen angles. */
    fun rotateRing(center: Vec2, radius: Double, active: Boolean, sweep: Pair<Double, Double>?)
    /** Scale handles: axis lines towards [xTip]/[yTip] with end boxes, and the uniform centre box. */
    fun scaleHandles(origin: Vec2, xTip: Vec2, yTip: Vec2, active: DragAxis?)
}

/**
 * A Scene view tool. The [ToolHost] keeps right-click menus, panning, zoom and entity drags to
 * Assets ahead of the tool, and routes left-button gestures and keys to the active tool.
 */
internal interface ViewportTool {
    val id: ViewportToolId
    val label: String get() = id.label
    val chord: KeyChord get() = id.chord
    /** A `java.awt.Cursor` type for [point] (e.g. over a handle). */
    fun cursor(host: ToolHost, point: Vec2): Int
    fun hint(ctx: ToolHintContext): String
    fun press(host: ToolHost, pointer: ToolPointer): ToolPress
    fun drag(host: ToolHost, pointer: ToolPointer)
    fun release(host: ToolHost, pointer: ToolPointer)
    /** Returns true when the tool consumed the key. */
    fun key(host: ToolHost, event: KeyEvent): Boolean = false
    /** Abandons the gesture and restores what it changed. */
    fun cancel(host: ToolHost)
    /** Commits the gesture in progress (a tool switch, focus loss or a mode change). */
    fun finish(host: ToolHost)
    /** Describes the gesture in progress; null when idle. */
    fun gestureHint(host: ToolHost): String? = null
    fun handles(host: ToolHost): List<ToolHandle> = emptyList()
    fun draw(host: ToolHost, overlay: ToolOverlay) {}
}

/** Move tool handle geometry, in viewport pixels around the entity's screen position. */
internal object MoveHandles {
    const val ARROW = 64.0
    const val SQUARE = 7.0
    const val TOLERANCE = 7.0
    fun handles(origin: Vec2) = listOf(
        ToolHandle(DragAxis.Free, origin-Vec2(SQUARE,SQUARE), origin+Vec2(SQUARE,SQUARE)),
        ToolHandle(DragAxis.X, origin+Vec2(SQUARE,0.0), origin+Vec2(ARROW,0.0)),
        ToolHandle(DragAxis.Y, origin-Vec2(0.0,SQUARE), origin-Vec2(0.0,ARROW)),
    )
    /** The handle under [point]: the square first, then the nearer arrow. */
    fun hit(origin: Vec2, point: Vec2): DragAxis? {
        val delta=point-origin
        if(kotlin.math.abs(delta.x)<=SQUARE+1 && kotlin.math.abs(delta.y)<=SQUARE+1) return DragAxis.Free
        val alongX=delta.x in SQUARE..ARROW+TOLERANCE && kotlin.math.abs(delta.y)<=TOLERANCE
        val alongY=-delta.y in SQUARE..ARROW+TOLERANCE && kotlin.math.abs(delta.x)<=TOLERANCE
        return when {
            alongX && alongY -> if(kotlin.math.abs(delta.y)<=kotlin.math.abs(delta.x)) DragAxis.X else DragAxis.Y
            alongX -> DragAxis.X
            alongY -> DragAxis.Y
            else -> null
        }
    }
}
