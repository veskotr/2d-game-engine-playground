package sle.editor.model

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * The scene's selection: an ordered set of entity keys (in the order they were selected) and
 * the active key, which the Inspector edits and the viewport gives handles. Selection changes
 * are never undo steps; history entries store a [snapshot] and restore it with [restore].
 *
 * The anchor is where a Shift range starts: the last key picked without Shift.
 */
internal class SceneSelection {
    var keys by mutableStateOf<List<String>>(emptyList())
        private set
    var active by mutableStateOf<String?>(null)
        private set
    private var anchor: String? = null

    val size get() = keys.size
    val isEmpty get() = keys.isEmpty()
    operator fun contains(key: String?) = key != null && key in keys

    /** Replaces the selection with [key] alone; null clears it. */
    fun select(key: String?) { if (key == null) clear() else apply(listOf(key), key, key) }

    /** Ctrl-click: adds [key] and makes it active, or removes it. Removing the active key makes
     * the most recently selected remaining key active. Either way [key] becomes the range anchor,
     * as in desktop file lists. */
    fun toggle(key: String) {
        if (key in keys) {
            val rest = keys - key
            apply(rest, if (active == key) rest.lastOrNull() else active, key)
        } else apply(keys + key, key, key)
    }

    /** Makes a selected [key] active without changing the set (a plain click on a selected entity). */
    fun activate(key: String) { if (key in keys) { active = key; anchor = key } }

    /** Shift-click: selects the keys between the anchor and [key] in [visibleOrder] (the Hierarchy's
     * visible rows). [additive] (Ctrl+Shift) keeps the current selection. Without an anchor in
     * [visibleOrder] this selects [key] alone. */
    fun extendTo(key: String, visibleOrder: List<String>, additive: Boolean = false) {
        val from = visibleOrder.indexOf(anchor ?: "")
        val to = visibleOrder.indexOf(key)
        if (from < 0 || to < 0) { select(key); return }
        val range = visibleOrder.subList(minOf(from, to), maxOf(from, to) + 1)
        val base = if (additive) keys.filterNot { it in range } else emptyList()
        // Keep the anchor where it was, so further Shift-clicks pivot around it.
        apply(base + range, key, anchor)
    }

    /** Replaces the selection; [active] defaults to the last key. */
    fun set(values: Collection<String>, active: String? = values.lastOrNull()) {
        val distinct = values.distinct()
        apply(distinct, active?.takeIf { it in distinct } ?: distinct.lastOrNull(), active?.takeIf { it in distinct } ?: distinct.lastOrNull())
    }

    fun clear() = apply(emptyList(), null, null)

    /** Drops keys that no longer exist, e.g. after a structural change. */
    fun prune(existing: Set<String>) {
        if (keys.all { it in existing }) return
        val rest = keys.filter { it in existing }
        apply(rest, active?.takeIf { it in existing } ?: rest.lastOrNull(), anchor?.takeIf { it in existing } ?: rest.lastOrNull())
    }

    fun snapshot() = HistorySelection(keys, active)

    /** Puts back a recorded selection, keeping only keys that still exist. */
    fun restore(value: HistorySelection, existing: Set<String>) {
        val rest = value.keys.filter { it in existing }
        val focus = value.active?.takeIf { it in existing } ?: rest.lastOrNull()
        apply(rest, focus, focus)
    }

    private fun apply(values: List<String>, focus: String?, start: String?) {
        if (keys != values) keys = values
        if (active != focus) active = focus
        anchor = start
    }
}
