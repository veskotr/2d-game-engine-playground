package sle.editor.model

import kotlinx.serialization.json.*
import kotlin.test.*

class HistoryTest {
    private class Counter { var value = 0; val log = mutableListOf<String>() }
    private fun History.step(counter: Counter, label: String, delta: Int = 1, mergeKey: Any? = null) {
        counter.value += delta
        record(History.Entry(label, mergeKey, undo = { counter.value -= delta; counter.log += "undo $label" },
            redo = { counter.value += delta; counter.log += "redo $label" }))
    }

    @Test fun undoAndRedoWalkStepsInOrderWithLabels() {
        val history = History(); val counter = Counter()
        assertFalse(history.canUndo); assertNull(history.undoLabel)
        history.step(counter, "Create A"); history.step(counter, "Move A", 10)
        assertEquals("Move A", history.undoLabel); assertEquals(listOf("Create A", "Move A"), history.undoLabels)
        assertTrue(history.undo()); assertEquals(1, counter.value)
        assertEquals("Create A", history.undoLabel); assertEquals("Move A", history.redoLabel)
        assertTrue(history.redo()); assertEquals(11, counter.value); assertFalse(history.canRedo)
        assertFalse(history.redo())
    }

    @Test fun newStepDiscardsRedo() {
        val history = History(); val counter = Counter()
        history.step(counter, "One"); history.step(counter, "Two"); history.undo()
        assertTrue(history.canRedo)
        history.step(counter, "Three")
        assertFalse(history.canRedo); assertEquals(listOf("One", "Three"), history.undoLabels)
    }

    @Test fun capKeepsTheLatestHundredSteps() {
        val history = History(); val counter = Counter()
        for (index in 0 until 130) history.step(counter, "Step $index")
        assertEquals(History.CAPACITY, history.undoLabels.size)
        assertEquals("Step 30", history.undoLabels.first())
        while (history.canUndo) history.undo()
        assertEquals(30, counter.value)
        repeat(History.CAPACITY) { assertTrue(history.redo()) }
        assertEquals(130, counter.value)
    }

    // Mergeable steps restore whole states, like the editor's snapshot entries.
    private fun History.set(counter: Counter, label: String, value: Int, mergeKey: Any?) {
        val before = counter.value; counter.value = value
        record(History.Entry(label, mergeKey, undo = { counter.value = before }, redo = { counter.value = value }))
    }

    @Test fun equalMergeKeysWithinTheWindowBecomeOneStep() {
        var now = 1_000L
        val history = History().apply { clock = { now } }; val counter = Counter()
        history.set(counter, "Nudge A", 1, "a")
        now += 500; history.set(counter, "Nudge A", 2, "a")
        now += 700; history.set(counter, "Nudge A", 3, "a") // the window rolls from the latest nudge
        assertEquals(listOf("Nudge A"), history.undoLabels)
        now += History.MERGE_WINDOW_MILLIS + 1; history.set(counter, "Nudge A", 4, "a")
        now += 10; history.set(counter, "Nudge B", 5, "b")
        now += 10; history.set(counter, "Edit", 6, null)
        now += 10; history.set(counter, "Edit", 7, null) // no merge key: never merges
        assertEquals(listOf("Nudge A", "Nudge A", "Nudge B", "Edit", "Edit"), history.undoLabels)
        repeat(4) { history.undo() }
        assertEquals(3, counter.value) // the merged step undid all three nudges at once
        history.undo(); assertEquals(0, counter.value)
        history.redo(); assertEquals(3, counter.value)
    }

    @Test fun undoEndsAMergeBurst() {
        var now = 0L
        val history = History().apply { clock = { now } }; val counter = Counter()
        history.set(counter, "Nudge", 1, "a"); history.set(counter, "Nudge", 2, "a")
        history.undo(); history.redo()
        now += 1; history.set(counter, "Nudge", 3, "a")
        assertEquals(2, history.undoLabels.size)
    }

    @Test fun groupRecordsOneStepAndNestedGroupsJoinTheirParent() {
        val history = History(); val counter = Counter()
        val result = history.group("Move 3 entities") {
            history.step(counter, "Move A")
            history.group("Inner") { history.step(counter, "Move B"); history.step(counter, "Move C") }
            "done"
        }
        assertEquals("done", result)
        assertEquals(listOf("Move 3 entities"), history.undoLabels)
        history.undo(); assertEquals(0, counter.value)
        assertEquals(listOf("undo Move C", "undo Move B", "undo Move A"), counter.log)
        history.redo(); assertEquals(3, counter.value)
        history.group<Unit>("Nothing") {}
        assertEquals(listOf("Move 3 entities"), history.undoLabels)
    }

    @Test fun failedGroupUndoesItsStepsAndRecordsNothing() {
        val history = History(); val counter = Counter()
        history.step(counter, "Before")
        assertFailsWith<IllegalStateException> {
            history.group("Compound") { history.step(counter, "A"); history.step(counter, "B"); error("refused") }
        }
        assertEquals(1, counter.value); assertEquals(listOf("Before"), history.undoLabels)
        assertEquals(listOf("undo B", "undo A"), counter.log)
    }

    @Test fun failingUndoKeepsTheStepAndPartialCompoundUndoIsPutBack() {
        val history = History(); val counter = Counter(); var refuse = true
        history.group("Compound") {
            counter.value += 1
            history.record(History.Entry("B", undo = { if (refuse) error("blocked"); counter.value -= 1 }, redo = { counter.value += 1 }))
            history.step(counter, "A")
        }
        assertFails { history.undo() } // A is undone first, then B refuses, so A is redone
        assertEquals(2, counter.value); assertEquals(listOf("undo A", "redo A"), counter.log)
        assertEquals("Compound", history.undoLabel); assertFalse(history.canRedo)
        refuse = false; history.undo(); assertEquals(0, counter.value)
    }

    @Test fun stepsRecordedWhileApplyingAreIgnoredAndSelectionIsRestored() {
        val history = History(); val selections = mutableListOf<String?>()
        history.restoreSelection = { selections += it.active }
        history.record(History.Entry("Create B", null, HistorySelection("a"), HistorySelection("b"),
            undo = { history.record(History.Entry("Nested", undo = {}, redo = {})) }, redo = {}))
        history.undo(); history.redo()
        assertEquals(listOf<String?>("a", "b"), selections); assertEquals(listOf("Create B"), history.undoLabels)
    }

    @Test fun entityEditLabelsDescribeTheChange() {
        val base = EntityDocument(name = "Player").withPose(ScenePose())
        fun pose(value: ScenePose) = base.withPose(value)
        assertEquals("Move Player", describeEntityEdit(base, pose(ScenePose(position = sle.editor.viewport.Vec2(1.0, 0.0)))))
        assertEquals("Rotate Player", describeEntityEdit(base, pose(ScenePose(rotation = 0.5))))
        assertEquals("Scale Player", describeEntityEdit(base, pose(ScenePose(scale = sle.editor.viewport.Vec2(2.0, 2.0)))))
        val box = base.addComponent("BoxCollider", buildJsonObject {
            put("size", buildJsonObject { put("x", 1); put("y", 1) }) })
        assertEquals("Add BoxCollider", describeEntityEdit(base, box))
        assertEquals("Remove BoxCollider", describeEntityEdit(box, base))
        val wider = box.setValue("BoxCollider", listOf("size", "x"), JsonPrimitive(4))
            .setValue("BoxCollider", listOf("size", "y"), JsonPrimitive(3))
        assertEquals("Edit BoxCollider size", describeEntityEdit(box, wider))
        assertEquals("Rename Player", describeEntityEdit(base, base.copy(name = "Hero")))
        // Several components at once, and a blank name.
        val unnamed = box.copy(name = "")
        assertEquals("Edit Entity", describeEntityEdit(unnamed, wider.copy(name = "").withPose(ScenePose(rotation = 1.0))))
    }
}
