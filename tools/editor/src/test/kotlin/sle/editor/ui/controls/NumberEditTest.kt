package sle.editor.ui.controls

import kotlinx.serialization.json.*
import sle.editor.model.*
import kotlin.test.*

class NumberEditTest {
    private fun state() = EntityWorkspaceState(mapOf("Transform" to ComponentDefinition(
        buildJsonObject { put("rotation", 0.0) }, JsonObject(emptyMap()))))
    private fun number(state: EntityWorkspaceState) = state.numberEdit("Transform", listOf("rotation"))
    private fun value(state: EntityWorkspaceState) = state.document.components.getValue("Transform").jsonObject.getValue("rotation").jsonPrimitive.double

    @Test fun jsonNumbersAreFiniteAndLocaleIndependent() {
        for (text in listOf("-2", "0.25", "1e-3", "-2.5E+2")) assertNotNull(NumberOptions().parse(text),text)
        for (text in listOf("", "-", "0.", "1e", "+1", "01", "NaN", "Infinity", "1e999", "1,5", "true", "[]", "\"2\""))
            assertNull(NumberOptions().parse(text),text)
        assertNull(NumberOptions(min=0.0,max=2.0).parse("-1"))
        assertNull(NumberOptions(min=0.0,max=2.0).parse("3"))
        assertNotNull(NumberOptions(min=0.0,max=2.0).parse("2"))
    }
    @Test fun previewsCommitAsOneUndoAndRedo() {
        val state=state();val edit=number(state)
        edit.focus();edit.input("1");edit.input("1.25");assertEquals(1.25,value(state))
        assertTrue(edit.commit());state.undo();assertEquals(0.0,value(state))
        assertFalse(state.canUndo);state.redo();assertEquals(1.25,value(state))
    }
    @Test fun escapeRestoresEntryIncludingAnInvalidDraft() {
        val state=state();val edit=number(state)
        edit.focus();edit.input("-");edit.blur()
        assertEquals("-",edit.text);assertTrue(edit.invalid);assertEquals(0.0,value(state))
        edit.focus();edit.input("2");edit.cancel()
        assertEquals("-",edit.text);assertTrue(edit.invalid);assertEquals(0.0,value(state))
    }
    @Test fun invalidBlurBlocksSaveAndSurvivesPresenterRemount() {
        val state=state();val edit=number(state)
        edit.focus();edit.input("3");edit.input("1e");assertFalse(edit.commit());edit.blur()
        assertEquals(3.0,value(state));assertTrue(state.fieldErrors.isNotEmpty())
        assertEquals("1e",number(state).text)
        assertFailsWith<IllegalArgumentException> { state.save(java.nio.file.Path.of("unused.prefab.json")) }
        number(state).focus();number(state).input("4e-2");number(state).blur()
        assertEquals(0.04,value(state));assertTrue(state.fieldErrors.isEmpty())
    }
    @Test fun externalUndoInvalidatesFocusedCallbacks() {
        val state=state();val edit=number(state)
        edit.focus();edit.input("2");state.undo();edit.input("9");edit.blur()
        assertEquals(0.0,value(state));assertTrue(state.drafts.isEmpty())
    }
    @Test fun removedComponentAndReplacedDocumentRejectStaleCallbacks() {
        val state=state();val edit=number(state)
        edit.focus();edit.input("2");state.removeComponent("Transform");edit.input("9");edit.cancel()
        assertFalse("Transform" in state.document.components)
        state.undo();val old=number(state);old.focus();state.newDocument();old.input("9");old.blur()
        assertEquals(0.0,value(state));assertTrue(state.drafts.isEmpty())
    }
    @Test fun unrelatedCommandFinishesNumericEditBeforeItsOwnUndoRecord() {
        val state=state();val edit=number(state)
        edit.focus();edit.input("2");state.rename("Renamed");edit.cancel()
        assertEquals("Renamed",state.document.name);assertEquals(2.0,value(state))
        state.undo();assertEquals("New Entity",state.document.name);assertEquals(2.0,value(state))
        state.undo();assertEquals(0.0,value(state))
    }
    @Test fun replacedDocumentRejectsRefocusFromAnOldPresenter() {
        val state=state();val old=number(state)
        state.newDocument();old.focus();old.input("9");old.blur()
        assertEquals(0.0,value(state));assertFalse(state.canUndo)
    }
    @Test fun selectionOnlyAndReadOnlyEventsDoNotStartTransactions() {
        val state=state();val edit=number(state)
        edit.updateText("0.0",editable=true)
        assertFalse(state.gestureActive);assertFalse(state.canUndo);assertTrue(state.drafts.isEmpty())
        edit.updateText("2",editable=false)
        assertEquals(0.0,value(state));assertFalse(state.gestureActive)
        edit.updateText("2",editable=true);assertEquals(2.0,value(state));edit.commit()
        state.undo();assertEquals(0.0,value(state));assertFalse(state.canUndo)
    }
    @Test fun sceneSelectionFinishesOnlyTheOriginalEntityTransaction() {
        val scene=SceneWorkspaceState(state().catalog);scene.addEntity();val first=scene.selected!!
        val edit=number(first);edit.focus();edit.input("7");scene.addEntity();val second=scene.selected!!
        edit.input("99");edit.cancel()
        assertEquals(7.0,value(first));assertEquals(0.0,value(second))
        // One scene timeline: the later Create is undone first, then the committed edit.
        first.undo();assertEquals(listOf(first),scene.entities);assertEquals(7.0,value(first))
        first.undo();assertEquals(0.0,value(first))
    }
}
