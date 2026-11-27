package sle.editor.ui.controls

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.runtime.key
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import sle.editor.ui.EditorMenuEntry
import sle.editor.ui.editorMenuLauncher
import sle.editor.ui.theme.LocalEditorMetrics
import java.awt.MouseInfo

/**
 * One toolbar group. [end] groups (view options) sit on the right, the rest (tools) on the left.
 * [menu] lists the group's actions for the overflow menu when the group does not fit.
 */
internal class ToolbarGroup(
    val key: String,
    val end: Boolean = false,
    val menu: () -> List<EditorMenuEntry>,
    val content: @Composable () -> Unit,
)

/**
 * Which groups move into the overflow menu. Groups are hidden from the right: first the end
 * (view option) groups, last to first, then the start groups. Returns hidden indices.
 */
internal fun toolbarOverflow(widths: List<Int>, end: List<Boolean>, available: Int, gap: Int, overflow: Int): Set<Int> {
    require(widths.size == end.size)
    fun width(visible: List<Int>) = visible.sumOf { widths[it] } + gap * (visible.size - 1).coerceAtLeast(0)
    val all = widths.indices.toList()
    if (width(all) <= available) return emptySet()
    val order = all.filter { end[it] }.reversed() + all.filterNot { end[it] }.reversed()
    val hidden = linkedSetOf<Int>()
    for (index in order) {
        hidden += index
        val visible = all - hidden
        if (width(visible) + (if (visible.isEmpty()) 0 else gap) + overflow <= available) break
    }
    return hidden
}

/**
 * A single-line toolbar: start groups left, end groups right, and a ⋯ button listing whatever
 * does not fit. It never wraps onto a second row.
 */
@Composable
internal fun EditorToolbarRow(groups: List<ToolbarGroup>, modifier: Modifier = Modifier, tooltipsAbove: Boolean = false) {
    val m = LocalEditorMetrics.current
    val hidden = remember { HiddenGroups() }
    val currentGroups by rememberUpdatedState(groups)
    val launcher = editorMenuLauncher {
        hidden.keys.mapNotNull { key -> currentGroups.firstOrNull { it.key == key } }.flatMapIndexed { index, group ->
            (if (index > 0) listOf(EditorMenuEntry.Separator) else emptyList()) + group.menu()
        }
    }
    // A plain Layout, not SubcomposeLayout: every group is an ordinary child, measured each pass,
    // and hidden groups are simply not placed. Subcompositions holding tooltip popups broke the
    // window when the Scene tab was disposed mid-tooltip (Run Project switches to Game).
    CompositionLocalProvider(LocalTooltipsAbove provides tooltipsAbove) {
        Layout(content = {
            groups.forEach { group ->
                key(group.key) {
                    Row(horizontalArrangement = Arrangement.spacedBy(m.gap / 2), verticalAlignment = Alignment.CenterVertically) { group.content() }
                }
            }
            EditorGlyphButton("More", {
                val point = MouseInfo.getPointerInfo().location
                launcher(point.x, point.y)
            }, help = "More toolbar actions") { tint -> EditorGlyphIcon(EditorGlyph.More, tint) }
        }, modifier = modifier.fillMaxWidth().padding(horizontal = m.gap, vertical = m.gap / 2)) { measurables, constraints ->
            val gap = (m.gap / 2).roundToPx()
            val loose = Constraints()
            val measured = measurables.dropLast(1).map { it.measure(loose) }
            val more = measurables.last().measure(loose)
            val hiddenIndices = toolbarOverflow(measured.map { it.width }, groups.map { it.end }, constraints.maxWidth, gap, more.width)
            hidden.keys = hiddenIndices.sorted().map { groups[it].key }
            val height = (measured + more).maxOfOrNull { it.height } ?: 0
            layout(constraints.maxWidth, height) {
                var x = 0
                for ((index, group) in groups.withIndex()) {
                    if (group.end || index in hiddenIndices) continue
                    measured[index].placeRelative(x, (height - measured[index].height) / 2)
                    x += measured[index].width + gap
                }
                var right = constraints.maxWidth
                if (hiddenIndices.isNotEmpty()) { right -= more.width; more.placeRelative(right, (height - more.height) / 2) }
                for ((index, group) in groups.withIndex().reversed()) {
                    if (!group.end || index in hiddenIndices) continue
                    if (right < constraints.maxWidth) right -= gap
                    right -= measured[index].width
                    measured[index].placeRelative(right, (height - measured[index].height) / 2)
                }
            }
        }
    }
}

/** Keys of the groups in the overflow menu; written during layout, read when ⋯ is clicked. */
private class HiddenGroups { @Volatile var keys: List<String> = emptyList() }
