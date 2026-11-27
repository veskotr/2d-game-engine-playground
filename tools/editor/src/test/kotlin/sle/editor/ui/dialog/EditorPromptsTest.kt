package sle.editor.ui.dialog

import androidx.compose.ui.input.key.Key
import sle.editor.ui.controls.EditorButtonStyle
import kotlin.test.*

class EditorPromptsTest {
    private val original = EditorPrompts.presenter
    private val shown = mutableListOf<PromptRequest>()
    /** Answers each prompt with the next response instead of opening a window. */
    private fun script(vararg responses: (PromptHandle) -> Unit) {
        val queue = ArrayDeque(responses.toList())
        EditorPrompts.presenter = { handle ->
            shown += handle.request
            assertSame(handle, EditorPrompts.current)
            queue.removeFirst()(handle)
        }
    }
    @AfterTest fun restore() { EditorPrompts.presenter = original }

    @Test fun footerPutsThePrimaryRightmostWithCancelBesideIt() {
        val ok = DialogAction("Save", DialogActionRole.Default) {}
        val cancel = DialogAction("Cancel", DialogActionRole.Cancel) {}
        val other = DialogAction("Don't Save") {}
        assertEquals(listOf(other, cancel, ok), footerOrder(listOf(ok, cancel, other)))
        assertEquals(EditorButtonStyle.Primary, ok.style)
        assertEquals(EditorButtonStyle.Secondary, cancel.style)
    }

    @Test fun enterRunsTheEnabledDefaultAndEscapeTheCancel() {
        val ok = DialogAction("OK", DialogActionRole.Default) {}
        val cancel = DialogAction("Cancel", DialogActionRole.Cancel) {}
        assertSame(ok, dialogKeyAction(listOf(ok, cancel), Key.Enter))
        assertSame(cancel, dialogKeyAction(listOf(ok, cancel), Key.Escape))
        assertNull(dialogKeyAction(listOf(ok.copy(enabled = false), cancel), Key.Enter))
        assertNull(dialogKeyAction(listOf(ok, cancel), Key.Enter, enterSubmits = false))
        assertNull(dialogKeyAction(listOf(ok, cancel), Key.A))
    }

    @Test fun confirmIsTrueOnlyForTheConfirmButton() {
        script(press("Delete"), press("Cancel"), { it.answer(-1) })
        assertTrue(EditorPrompts.confirm("Delete", "Delete it?", "Delete", danger = true))
        assertFalse(EditorPrompts.confirm("Delete", "Delete it?", "Delete", danger = true))
        assertFalse(EditorPrompts.confirm("Delete", "Delete it?", "Delete", danger = true), "closing the window cancels")
        assertTrue(shown.first().buttons.first().danger)
        assertNull(EditorPrompts.current)
    }

    @Test fun chooseReturnsTheOptionIndexOrMinusOne() {
        script(press("Skip"), press("Cancel"), { it.answer(-1) })
        val options = listOf("Rename", "Skip", "Replace")
        assertEquals(1, EditorPrompts.choose("Conflict", "Exists", options))
        assertEquals(-1, EditorPrompts.choose("Conflict", "Exists", options))
        assertEquals(-1, EditorPrompts.choose("Conflict", "Exists", options, cancel = null))
        val roles = shown.first().buttons.map { it.role }
        assertEquals(listOf(DialogActionRole.Default, DialogActionRole.Other, DialogActionRole.Other, DialogActionRole.Cancel), roles)
    }

    @Test fun inputValidatesBeforeAccepting() {
        var rejected = false
        script({ handle ->
            handle.answer("Create", "")
            rejected = handle.result == null && handle.error == "Enter a name"
            handle.answer("Create", "Level 2")
        }, press("Cancel"))
        val validate = { text: String -> if (text.isBlank()) "Enter a name" else null }
        assertEquals("Level 2", EditorPrompts.input("New Folder", "Name", confirm = "Create", validate = validate))
        assertTrue(rejected, "OK with an invalid name must be ignored")
        assertNull(EditorPrompts.input("New Folder", "Name", "Old", confirm = "Create", validate = validate))
        assertEquals("Old", shown.last().initial)
    }

    @Test fun selectAndListReturnTheChosenItem() {
        script({ it.item = 2; it.answer("Restore") }, { it.item = 1; it.answer("Open") }, press("Close"))
        assertEquals("c", EditorPrompts.select("Recover", "Asset", listOf("a", "b", "c"), "b", confirm = "Restore"))
        assertEquals(1, shown.first().initialItem)
        assertEquals(1, EditorPrompts.pickFromList("Find References", "Uses", listOf("x", "y"), "Open"))
        assertNull(EditorPrompts.pickFromList("Find References", "Uses", listOf("x", "y"), "Open"))
    }

    @Test fun messageShowsOneCloseButton() {
        script(press("Close"))
        EditorPrompts.message("About", "SLE Editor")
        assertEquals(listOf("Close"), shown.single().buttons.map { it.label })
    }

    private fun press(label: String): (PromptHandle) -> Unit = { it.answer(label) }
}
