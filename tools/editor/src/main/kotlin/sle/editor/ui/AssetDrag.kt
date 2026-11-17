package sle.editor.ui

import androidx.compose.foundation.border
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.LocalAwtWindow
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import sle.editor.ui.theme.LocalEditorColors
import sle.editor.ui.theme.LocalEditorMetrics
import java.awt.Point
import java.awt.Rectangle
import java.nio.file.Path

/** Dragging a project asset from the Assets panel onto the Scene view or Hierarchy.
 * Targets report AWT screen rectangles, so drops work across detached windows and onto the
 * heavyweight viewport canvas, which Compose cannot hit-test. */
internal object AssetDrag {
    class Target(val bounds: () -> Rectangle?, val drop: (Path, Point) -> Unit)
    var dragging by mutableStateOf<Path?>(null)
        private set
    private val targets = linkedMapOf<Any, Target>()
    fun register(key: Any, target: Target) { targets[key] = target }
    fun unregister(key: Any) { targets.remove(key) }
    fun begin(path: Path) { dragging = path }
    fun cancel() { dragging = null }
    /** Returns true when a target accepted the drop. */
    fun release(point: Point): Boolean {
        val path = dragging ?: return false
        dragging = null
        val target = targets.values.lastOrNull { runCatching { it.bounds()?.contains(point) }.getOrNull() == true } ?: return false
        target.drop(path, point)
        return true
    }
}

/** Identity of a scene entity being dragged; never a fake path. Resolved only at drop time, where
 * a payload whose scene/document no longer matches, or whose entity is gone, is rejected. */
internal data class EntityDragPayload(val sceneKey: String, val file: Path?, val entityKey: String, val label: String)

/** Dragging a scene entity (from the Hierarchy or the viewport) onto an Assets folder. Like
 * [AssetDrag], targets are AWT screen rectangles so detached windows and the native canvas work. */
internal object EntityDrag {
    class Target(val bounds: () -> Rectangle?, val drop: (EntityDragPayload) -> Unit)
    var dragging by mutableStateOf<EntityDragPayload?>(null)
        private set
    /** The target under the pointer, for valid-target feedback. */
    var hovered by mutableStateOf<Any?>(null)
        private set
    private val targets = linkedMapOf<Any, Target>()
    fun register(key: Any, target: Target) { targets[key] = target }
    fun unregister(key: Any) { targets.remove(key); if (hovered == key) hovered = null }
    private fun at(point: Point) = targets.entries.lastOrNull { runCatching { it.value.bounds()?.contains(point) }.getOrNull() == true }
    fun over(point: Point) = at(point) != null
    fun move(payload: EntityDragPayload, point: Point) { dragging = payload; hovered = at(point)?.key }
    fun cancel() { dragging = null; hovered = null }
    /** Returns true when a target accepted the drop. */
    fun release(point: Point): Boolean {
        val payload = dragging ?: return false
        val target = at(point)?.value
        cancel()
        target?.drop(payload) ?: return false
        return true
    }
}

/** Screen bounds of a composable inside its (possibly detached) AWT window. */
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
private fun rememberScreenBounds(): Pair<() -> Rectangle?, (Rect) -> Unit> {
    val window = LocalAwtWindow.current
    val density = LocalDensity.current.density
    var bounds by remember { mutableStateOf<Rect?>(null) }
    val currentBounds by rememberUpdatedState(bounds)
    val currentWindow by rememberUpdatedState(window)
    return remember {
        {
            val area = currentBounds
            val pane = (currentWindow as? javax.swing.RootPaneContainer)?.contentPane
            if (area == null || pane == null || !pane.isShowing) null else {
                val origin = pane.locationOnScreen
                Rectangle(origin.x + (area.left / density).toInt(), origin.y + (area.top / density).toInt(),
                    (area.width / density).toInt(), (area.height / density).toInt())
            }
        }
    } to { value: Rect -> bounds = value }
}

/** Registers an Assets folder as an entity drop target; highlights it while an entity drag hovers it. */
@Composable
internal fun Modifier.entityDropTarget(enabled: Boolean, drop: (EntityDragPayload) -> Unit): Modifier {
    if (!enabled) return this
    val key = remember { Any() }
    val (screen, update) = rememberScreenBounds()
    val currentDrop by rememberUpdatedState(drop)
    DisposableEffect(key) {
        EntityDrag.register(key, EntityDrag.Target(screen) { currentDrop(it) })
        onDispose { EntityDrag.unregister(key) }
    }
    val colors = LocalEditorColors.current
    val metrics = LocalEditorMetrics.current
    return onGloballyPositioned { update(it.boundsInWindow()) }.then(when {
        EntityDrag.hovered == key -> Modifier.border(metrics.focusBorderWidth * 2, colors.focusRing)
        EntityDrag.dragging != null -> Modifier.border(metrics.focusBorderWidth, colors.separator)
        else -> Modifier
    })
}

@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
/** Registers this composable's on-screen area as a drop target and outlines it while a drag is active. */
@Composable
internal fun Modifier.assetDropTarget(drop: (Path, Point) -> Unit): Modifier {
    val window = LocalAwtWindow.current
    val density = LocalDensity.current.density
    val key = remember { Any() }
    var bounds by remember { mutableStateOf<Rect?>(null) }
    val currentDrop by rememberUpdatedState(drop)
    val currentBounds by rememberUpdatedState(bounds)
    DisposableEffect(key, window) {
        AssetDrag.register(key, AssetDrag.Target({
            val area = currentBounds
            val pane = (window as? javax.swing.RootPaneContainer)?.contentPane
            if (area == null || pane == null || !pane.isShowing) null else {
                val origin = pane.locationOnScreen
                Rectangle(origin.x + (area.left / density).toInt(), origin.y + (area.top / density).toInt(),
                    (area.width / density).toInt(), (area.height / density).toInt())
            }
        }) { path, point -> currentDrop(path, point) })
        onDispose { AssetDrag.unregister(key) }
    }
    val highlight = AssetDrag.dragging != null
    return onGloballyPositioned { bounds = it.boundsInWindow() }
        .then(if (highlight) Modifier.border(LocalEditorMetrics.current.focusBorderWidth, LocalEditorColors.current.focusRing) else Modifier)
}
