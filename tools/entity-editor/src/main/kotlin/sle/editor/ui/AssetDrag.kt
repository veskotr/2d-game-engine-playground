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
