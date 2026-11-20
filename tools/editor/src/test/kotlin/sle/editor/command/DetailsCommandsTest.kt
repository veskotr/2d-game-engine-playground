package sle.editor.command

import kotlinx.serialization.json.*
import kotlin.test.*
import sle.editor.model.EntityWorkspaceState
import sle.editor.model.PropertyClipboardValue
import sle.editor.ui.inspector.PropertyKind
import sle.editor.ui.inspector.propertyDefinition
import sle.editor.viewport.ViewportMode

class DetailsCommandsTest {
    private val state = EntityWorkspaceState(emptyMap()).apply {
        resetTo(document.addComponent("Test", buildJsonObject { put("label", "Some text") }))
    }
    private val clipboard = TestClipboard()
    private val base = CommandContext(mode = ViewportMode.Entity, entity = state, prefab = state,
        detailsClipboard = clipboard)

    @Test fun copyUsesJsonAndLeavesTextShortcutsAvailable() {
        val ctx = target(base.copy(textInputFocused = true), readOnly = true)
        assertTrue(DetailsCommands.copy.enabled(ctx))
        assertFalse(DetailsCommands.paste.enabled(ctx))
        DetailsCommands.copy.run(ctx)
        assertEquals("\"Some text\"", clipboard.value?.text)
        assertEquals(PropertyKind.String, clipboard.value?.kind)
        assertTrue(DetailsCommands.all.all { standardKeymap.chords(it.id).isEmpty() })
    }

    @Test fun menuChecksLiveContextAndPresenterAtInvocation() {
        var mounted = true
        var live = target(base, mounted = { mounted })
        val entry = DetailsCommands.copy.menuEntry(live, liveContext = { live })
        assertTrue(entry.enabled)
        entry.action()
        assertNotNull(clipboard.value)
        clipboard.value = null
        for (blocked in listOf(live.copy(modalOpen = true), live.copy(projectBusy = true),
            live.copy(editable = false), live.copy(gameRunning = true), live.copy(projectGeneration = 1),
            live.copy(prefab = EntityWorkspaceState(emptyMap())))) {
            assertFalse(DetailsCommands.copy.enabled(blocked))
        }
        mounted = false
        entry.action()
        assertNull(clipboard.value)
        mounted = true
        state.editField("Test", listOf("label"), JsonPrimitive(1), "invalid")
        entry.action()
        assertNull(clipboard.value)
        state.resetTo(state.document.copy())
        entry.action()
        assertNull(clipboard.value)
    }

    @Test fun componentHeadingUsesTheRegisteredCommandsAndRechecksTheTarget() {
        val catalog = sle.editor.model.ComponentDefinition(buildJsonObject { put("label", "default") }, buildJsonObject {})
        val owner = EntityWorkspaceState(mapOf("Test" to catalog)).apply { addComponent("Test") }
        val context = base.copy(entity = owner, prefab = owner)
        var live = context.copy(details = DetailsTarget(owner, "Test", true, context))
        assertFalse(DetailsCommands.copy.enabled(live))
        assertFalse(DetailsCommands.componentReset.enabled(live))
        assertFalse(DetailsCommands.componentPaste.enabled(live))
        val copy = DetailsCommands.componentCopy.menuEntry(live, liveContext = { live })
        copy.action()
        assertEquals("""{"component":"Test","values":{"label":"default"}}""", clipboard.value?.text)
        live = live.copy(gameRunning = true)
        clipboard.value = null
        copy.action()
        assertNull(clipboard.value)
    }

    private fun target(ctx: CommandContext, readOnly: Boolean = false, mounted: () -> Boolean = { true }): CommandContext {
        val property = propertyDefinition("Test", listOf("label"), JsonPrimitive("Some text"))
        return ctx.copy(details = DetailsTarget(state, property, readOnly, ctx, mounted))
    }

    private class TestClipboard : DetailsClipboard {
        var value: PropertyClipboardValue? = null
        override fun read() = checkNotNull(value)
        override fun write(value: PropertyClipboardValue) { this.value = value }
    }
}
