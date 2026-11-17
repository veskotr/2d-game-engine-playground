package sle.editor.command

import java.awt.event.KeyEvent
import kotlin.test.*
import org.junit.Test

class KeymapTest {
    private fun command(id: String, vararg chords: KeyChord, scopes: Set<CommandScope> = setOf(CommandScope.Global)) =
        EditorCommand(CommandId(id), id, defaultChords = chords.toList(), scopes = scopes) {}

    @Test fun conflictingChordsInOverlappingScopesAreRejectedAtRegistration() {
        val save = KeyChord.ctrl(KeyEvent.VK_S)
        val failure = assertFailsWith<IllegalArgumentException> { CommandRegistry(listOf(command("a", save), command("b", save))) }
        assertTrue("Ctrl+S" in failure.message!!)
        // Global overlaps every panel scope.
        assertFailsWith<IllegalArgumentException> {
            CommandRegistry(listOf(command("a", save), command("b", save, scopes = setOf(CommandScope.Hierarchy))))
        }
        assertFailsWith<IllegalArgumentException> {
            CommandRegistry(listOf(command("a", save, scopes = setOf(CommandScope.Hierarchy, CommandScope.Assets)),
                command("b", save, scopes = setOf(CommandScope.Assets))))
        }
        assertFailsWith<IllegalArgumentException> { CommandRegistry(listOf(command("a"), command("a"))) }
    }

    @Test fun disjointPanelScopesMayShareAChord() {
        val delete = KeyChord.plain(KeyEvent.VK_DELETE)
        val keymap = Keymap(CommandRegistry(listOf(
            command("tree", delete, scopes = setOf(CommandScope.Hierarchy, CommandScope.SceneViewport)),
            command("assets", delete, scopes = setOf(CommandScope.Assets)))))
        assertEquals(listOf("tree", "assets"), keymap.commandsFor(delete).map { it.id.value })
    }

    @Test fun overridesReplaceUnbindAndAreCheckedForConflicts() {
        val a = command("a", KeyChord.ctrl(KeyEvent.VK_S));val b = command("b", KeyChord.ctrl(KeyEvent.VK_D))
        val registry = CommandRegistry(listOf(a, b))
        val moved = Keymap(registry, mapOf(a.id to listOf(KeyChord.ctrl(KeyEvent.VK_E))))
        assertEquals(KeyChord.ctrl(KeyEvent.VK_E), moved.primary(a.id))
        assertTrue(moved.commandsFor(KeyChord.ctrl(KeyEvent.VK_S)).isEmpty())
        assertEquals(listOf(b), moved.commandsFor(KeyChord.ctrl(KeyEvent.VK_D)))
        val unbound = Keymap(registry, mapOf(b.id to emptyList()))
        assertNull(unbound.primary(b.id))
        assertFailsWith<IllegalArgumentException> { Keymap(registry, mapOf(a.id to listOf(KeyChord.ctrl(KeyEvent.VK_D)))) }
        assertFailsWith<IllegalArgumentException> { Keymap(registry, mapOf(CommandId("missing") to emptyList())) }
        // Swapping chords is valid once both overrides apply.
        val swapped = Keymap(registry, mapOf(a.id to listOf(KeyChord.ctrl(KeyEvent.VK_D)), b.id to listOf(KeyChord.ctrl(KeyEvent.VK_S))))
        assertEquals(listOf(a), swapped.commandsFor(KeyChord.ctrl(KeyEvent.VK_D)))
    }

    @Test fun chordsDisplayAndParseLocaleIndependently() {
        assertEquals("Ctrl+Shift+S", KeyChord.ctrl(KeyEvent.VK_S, shift = true).display())
        assertEquals("Shift+F5", KeyChord.shift(KeyEvent.VK_F5).display())
        assertEquals("Delete", KeyChord.plain(KeyEvent.VK_DELETE).display())
        for(text in listOf("Ctrl+Shift+S", "F6", "Ctrl+F5", "Shift+F5", "Delete", "Ctrl+Z", "Alt+Backspace"))
            assertEquals(text, KeyChord.parse(text).display())
        assertEquals(KeyChord.ctrl(KeyEvent.VK_Y), KeyChord.parse(" ctrl + y "))
        assertFailsWith<IllegalArgumentException> { KeyChord.parse("Hyper+S") }
        assertFailsWith<IllegalStateException> { KeyChord.parse("Ctrl+Nope") }
        assertFailsWith<IllegalArgumentException> { KeyChord(KeyEvent.VK_CONTROL, ctrl = true) }
    }

    @Test fun standardKeymapMatchesThePlannedScheme() {
        fun chords(command: EditorCommand) = standardKeymap.chords(command.id).map { it.display() }
        assertEquals(listOf("Ctrl+S"), chords(DocumentCommands.save))
        assertEquals(listOf("Ctrl+Shift+S"), chords(DocumentCommands.saveAs))
        assertEquals(listOf("Ctrl+Z"), chords(EditCommands.undo))
        assertEquals(listOf("Ctrl+Y", "Ctrl+Shift+Z"), chords(EditCommands.redo))
        assertEquals(listOf("Ctrl+D"), chords(EditCommands.duplicate))
        assertEquals(listOf("Delete", "Backspace"), chords(EditCommands.delete))
        assertEquals(listOf("F5"), chords(PlayCommands.play))
        assertEquals(listOf("F6"), chords(PlayCommands.pause))
        assertEquals(listOf("Shift+F5"), chords(PlayCommands.stop))
        assertEquals(listOf("Ctrl+F5"), chords(PlayCommands.runProject))
        assertTrue(PlayCommands.all.all { it.playControl })
        // Ctrl+P / Ctrl+Shift+P stay free for Quick Open and the Command Palette.
        assertTrue(standardKeymap.commandsFor(KeyChord.ctrl(KeyEvent.VK_P)).isEmpty())
        assertTrue(standardKeymap.commandsFor(KeyChord.ctrl(KeyEvent.VK_P, shift = true)).isEmpty())
    }

    @Test fun helpIsGeneratedFromTheRegistryGroupedByScope() {
        val sections = keyboardShortcutSections()
        assertEquals(listOf("Global", "Document", "Hierarchy and Scene view"), sections.map { it.title })
        val rows = sections.flatMap { it.rows }.associate { it.label to it.chords.map(KeyChord::display) }
        assertEquals(listOf("Ctrl+Y", "Ctrl+Shift+Z"), rows["Redo"])
        assertEquals(listOf("Delete", "Backspace"), rows["Delete"])
        // Unbound commands are not listed.
        assertNull(rows["Apply Changes to Prefab"])
        val html = keyboardShortcutsHtml()
        assertTrue("Ctrl+Shift+S" in html && "Mouse" in html && "Middle-drag" in html)
        // A user override changes Help with no other edits.
        val moved = standardKeymap.withOverrides(mapOf(DocumentCommands.save.id to listOf(KeyChord.ctrl(KeyEvent.VK_W))))
        assertEquals(listOf("Ctrl+W"), keyboardShortcutSections(moved).flatMap { it.rows }.single { it.label == "Save" }.chords.map(KeyChord::display))
    }
}
