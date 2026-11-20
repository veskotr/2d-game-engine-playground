package sle.editor.model

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** The selection an undo step restores: every selected key in selection order, and the active one. */
internal data class HistorySelection(val keys: List<String>, val active: String?) {
    /** A single selected entity, or none. */
    constructor(key: String?) : this(listOfNotNull(key), key)
}

/**
 * One undo timeline for a document: the scene (structure and every scene entity's component
 * edits) or the standalone prefab workspace. Entries are closures over immutable snapshots, so
 * history never retains live component references.
 *
 * - [record] adds a step and discards redo. A step with the same non-null merge key as the
 *   latest recorded step, within [MERGE_WINDOW_MILLIS], extends that step instead: the merged
 *   step keeps the first undo and the last redo, so merged steps must restore whole states
 *   (snapshots), never apply deltas.
 * - [group] records everything inside it as one step; a nested group joins its parent. If the
 *   block throws, the steps it already recorded are undone and nothing is kept.
 * - Undo restores the selection recorded before the step; redo the one after it. Selection
 *   changes alone are never steps.
 */
internal class History(private val capacity: Int = CAPACITY) {
    class Entry(
        val label: String,
        val mergeKey: Any? = null,
        val undoSelection: HistorySelection? = null,
        val redoSelection: HistorySelection? = null,
        val undo: () -> Unit,
        val redo: () -> Unit,
    ) { internal var time = 0L }

    /** Milliseconds; replaced by tests that exercise the merge window. */
    var clock: () -> Long = System::currentTimeMillis
    /** Applies an entry's selection after undo or redo. The scene installs its own. */
    var restoreSelection: (HistorySelection) -> Unit = {}

    private var past by mutableStateOf<List<Entry>>(emptyList())
    private var future by mutableStateOf<List<Entry>>(emptyList())
    // Only the entry recorded last may absorb a merge; undo, redo and clear end the burst.
    private var mergeable: Entry? = null
    private var collecting: MutableList<Entry>? = null
    private var applying = false

    var revision by mutableLongStateOf(0L)
        private set

    val canUndo get() = past.isNotEmpty()
    val canRedo get() = future.isNotEmpty()
    val undoLabel: String? get() = past.lastOrNull()?.label
    val redoLabel: String? get() = future.lastOrNull()?.label
    /** The step Undo would revert, compared by identity: a notice's Undo button applies only while it is unchanged. */
    val latestStep: Entry? get() = past.lastOrNull()
    /** Labels of the undoable steps, oldest first. */
    val undoLabels: List<String> get() = past.map { it.label }

    fun record(entry: Entry) {
        if (applying) return
        collecting?.let { it += entry; return }
        val now = clock()
        val last = mergeable?.takeIf { it === past.lastOrNull() }
        val next = if (entry.mergeKey != null && last != null && last.mergeKey == entry.mergeKey && now - last.time <= MERGE_WINDOW_MILLIS)
            Entry(entry.label, entry.mergeKey, last.undoSelection, entry.redoSelection, last.undo, entry.redo)
        else null
        val kept = (if (next != null) past.dropLast(1) else past) + (next ?: entry)
        (next ?: entry).time = now
        past = kept.takeLast(capacity); future = emptyList(); mergeable = next ?: entry
        revision++
    }

    /** [mergeKey] lets consecutive groups merge like single steps (a nudge burst on a selection). */
    fun <T> group(label: String, mergeKey: Any? = null, block: () -> T): T {
        if (collecting != null) return block()
        val steps = mutableListOf<Entry>()
        collecting = steps
        val result = try { block() } catch (failure: Throwable) {
            collecting = null
            applying = true
            try { steps.asReversed().forEach { runCatching { it.undo() } } } finally { applying = false }
            throw failure
        }
        collecting = null
        if (steps.isNotEmpty()) record(Entry(label, mergeKey, steps.first().undoSelection, steps.last().redoSelection,
            undo = { runAll(steps.asReversed(), Entry::undo, Entry::redo) },
            redo = { runAll(steps, Entry::redo, Entry::undo) }))
        return result
    }

    /** Undoes the latest step. A step whose undo throws stays on the timeline. */
    fun undo(): Boolean {
        check(collecting == null) { "Undo inside a history group" }
        val entry = past.lastOrNull() ?: return false
        apply(entry.undo)
        past = past.dropLast(1); future = future + entry; mergeable = null
        revision++
        entry.undoSelection?.let(restoreSelection)
        return true
    }

    fun redo(): Boolean {
        check(collecting == null) { "Redo inside a history group" }
        val entry = future.lastOrNull() ?: return false
        apply(entry.redo)
        future = future.dropLast(1); past = (past + entry).takeLast(capacity); mergeable = null
        revision++
        entry.redoSelection?.let(restoreSelection)
        return true
    }

    fun clear() { past = emptyList(); future = emptyList(); mergeable = null; revision++ }

    private fun apply(action: () -> Unit) {
        applying = true
        try { action() } finally { applying = false }
    }

    companion object {
        const val CAPACITY = 100
        const val MERGE_WINDOW_MILLIS = 750L

        // A compound step either applies completely or is put back the way it was.
        private fun runAll(steps: List<Entry>, step: (Entry) -> (() -> Unit), back: (Entry) -> (() -> Unit)) {
            val done = mutableListOf<Entry>()
            try { steps.forEach { step(it)(); done += it } }
            catch (failure: Throwable) { done.asReversed().forEach { runCatching { back(it)() } }; throw failure }
        }
    }
}

/** A document with one undo timeline: the scene, or the standalone prefab workspace. */
internal interface UndoableDocument {
    val history: History
    /** A drag or numeric edit is open; undo and redo cancel it before touching the timeline. */
    val gestureActive: Boolean
    fun undo()
    fun redo()
}

/** The scene side of a scene-owned entity: its timeline, and entity lookup by key at undo time. */
internal interface EntityHistoryOwner : UndoableDocument {
    fun entity(key: String): EntityWorkspaceState?
    /** The selection an edit of [key] records: the whole selection when it includes [key]
     * (a multi-move), otherwise [key] alone, so undo selects the entity it changed. */
    fun historySelection(key: String): HistorySelection = HistorySelection(key)
}

/** Display name used in undo labels. */
internal fun historyName(document: EntityDocument) = document.name.ifBlank { "Entity" }

/** "Move Player", "Edit BoxCollider size": the label of an entity edit without an explicit one. */
internal fun describeEntityEdit(before: EntityDocument, after: EntityDocument): String {
    val name = historyName(after)
    val added = after.components.keys - before.components.keys
    val removed = before.components.keys - after.components.keys
    if (added.size == 1 && removed.isEmpty()) return "Add ${added.single()}"
    if (removed.size == 1 && added.isEmpty()) return "Remove ${removed.single()}"
    val changed = mutableListOf<List<String>>()
    fun compare(a: kotlinx.serialization.json.JsonElement?, b: kotlinx.serialization.json.JsonElement?, path: List<String>) {
        if (a == b) return
        // Pose writes turn 0 into 0.0; only a different value is a change.
        if (a is kotlinx.serialization.json.JsonPrimitive && b is kotlinx.serialization.json.JsonPrimitive && !a.isString && !b.isString &&
            a.content.toDoubleOrNull()?.let { it == b.content.toDoubleOrNull() } == true) return
        if (a is kotlinx.serialization.json.JsonObject && b is kotlinx.serialization.json.JsonObject)
            (a.keys + b.keys).forEach { compare(a[it], b[it], path + it) }
        else changed += path
    }
    (before.components.keys + after.components.keys).forEach { compare(before.components[it], after.components[it], listOf(it)) }
    if (changed.isEmpty()) return if (before.name != after.name) "Rename ${historyName(before)}" else "Edit $name"
    val components = changed.map { it.first() }.toSet()
    if (components.size > 1) return "Edit $name"
    val component = components.single()
    if (component == "Transform") {
        val fields = changed.mapNotNull { it.getOrNull(1) }.toSet()
        when (fields.singleOrNull()) {
            "position" -> return "Move $name"
            "rotation" -> return "Rotate $name"
            "scale" -> return "Scale $name"
        }
    }
    val common = changed.reduce { a, b -> a.zip(b).takeWhile { (x, y) -> x == y }.map { it.first } }.drop(1)
    return if (common.isEmpty()) "Edit $component" else "Edit $component ${common.joinToString(".")}"
}
