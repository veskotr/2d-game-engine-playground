package sle.editor.command

import java.awt.Canvas
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import kotlin.test.*
import org.junit.Test
import sle.editor.model.ScenePlaybackState
import sle.editor.model.SceneWorkspaceState
import sle.editor.ui.editorApplicationMenus
import sle.editor.ui.sceneCreationMenu
import sle.editor.workspace.PanelId

/** Covers [shortcutRules] row by row, plus repeat handling and the standard Delete/Ctrl+D/Ctrl+S commands. */
class ShortcutDispatcherTest {
    private val source = Canvas()
    private fun press(code: Int, modifiers: Int = 0) = KeyEvent(source, KeyEvent.KEY_PRESSED, 0, modifiers, code, KeyEvent.CHAR_UNDEFINED)
    private fun release(code: Int) = KeyEvent(source, KeyEvent.KEY_RELEASED, 0, 0, code, KeyEvent.CHAR_UNDEFINED)
    private val ctrl = InputEvent.CTRL_DOWN_MASK

    private val runs = mutableListOf<String>()
    private var enabled = true
    private fun command(id: String, chord: KeyChord, scopes: Set<CommandScope> = setOf(CommandScope.Global),
        repeatable: Boolean = false, playControl: Boolean = false) =
        EditorCommand(CommandId(id), id, defaultChords = listOf(chord), scopes = scopes, repeatable = repeatable,
            playControl = playControl, enabled = { enabled }) { runs += id }
    private val keymap = Keymap(CommandRegistry(listOf(
        command("save", KeyChord.ctrl(KeyEvent.VK_S)),
        command("undo", KeyChord.ctrl(KeyEvent.VK_Z), setOf(CommandScope.Document), repeatable = true),
        command("delete", KeyChord.plain(KeyEvent.VK_DELETE), setOf(CommandScope.Hierarchy, CommandScope.SceneViewport)),
        command("play", KeyChord.plain(KeyEvent.VK_F5), playControl = true),
    )))
    private var context: CommandContext? = CommandContext(focusedPanel = PanelId("hierarchy"))
    private val dispatcher = ShortcutDispatcher({ keymap }) { context }

    @Test fun rulesTableIsTheDocumentedOrder() {
        assertEquals(4, shortcutRules.size)
        assertTrue(shortcutRules.all { it.description.isNotBlank() })
    }

    @Test fun nonRepeatableCommandsRunOncePerPressAndRepeatableOnesRepeat() {
        repeat(3) { assertTrue(dispatcher.dispatchKeyEvent(press(KeyEvent.VK_S, ctrl))) }
        assertEquals(listOf("save"), runs)
        assertFalse(dispatcher.dispatchKeyEvent(release(KeyEvent.VK_S)))
        dispatcher.dispatchKeyEvent(press(KeyEvent.VK_S, ctrl))
        assertEquals(listOf("save", "save"), runs)
        runs.clear()
        repeat(3) { dispatcher.dispatchKeyEvent(press(KeyEvent.VK_Z, ctrl)) }
        assertEquals(listOf("undo", "undo", "undo"), runs)
    }

    @Test fun releaseOutsideTheAppDoesNotLeaveTheRepeatGuardArmed() {
        dispatcher.dispatchKeyEvent(press(KeyEvent.VK_S, ctrl))
        // S is released while another window is active: the editor never sees KEY_RELEASED.
        dispatcher.reset()
        dispatcher.dispatchKeyEvent(press(KeyEvent.VK_S, ctrl))
        assertEquals(listOf("save", "save"), runs)
    }

    @Test fun modalDialogOrOpenMenuBlocksEverything() {
        context = CommandContext(focusedPanel = PanelId("hierarchy"), modalOpen = true)
        for(event in listOf(press(KeyEvent.VK_S, ctrl), press(KeyEvent.VK_DELETE), press(KeyEvent.VK_F5)))
            assertFalse(dispatcher.dispatchKeyEvent(event))
        assertTrue(runs.isEmpty())
    }

    @Test fun focusedGameViewKeepsKeysExceptPlayControlsWhileRunningOrPaused() {
        context = CommandContext(focusedPanel = PanelId("game"), gameRunning = true)
        val save = press(KeyEvent.VK_S, ctrl)
        assertFalse(dispatcher.dispatchKeyEvent(save));assertFalse(save.isConsumed)
        assertFalse(dispatcher.dispatchKeyEvent(press(KeyEvent.VK_Z, ctrl)))
        assertTrue(dispatcher.dispatchKeyEvent(press(KeyEvent.VK_F5)))
        assertEquals(listOf("play"), runs)
        // Stopped: the Game panel is an ordinary panel again.
        context = CommandContext(focusedPanel = PanelId("game"), gameRunning = false)
        assertTrue(dispatcher.dispatchKeyEvent(press(KeyEvent.VK_S, ctrl)))
        // Running, but focus elsewhere: shortcuts work.
        dispatcher.reset();context = CommandContext(focusedPanel = PanelId("hierarchy"), gameRunning = true)
        assertTrue(dispatcher.dispatchKeyEvent(press(KeyEvent.VK_S, ctrl)))
        assertEquals(listOf("play", "save", "save"), runs)
    }

    @Test fun textInputKeepsPlainKeysAndItsEditingChords() {
        context = CommandContext(focusedPanel = PanelId("hierarchy"), textInputFocused = true)
        val delete = press(KeyEvent.VK_DELETE)
        assertFalse(dispatcher.dispatchKeyEvent(delete));assertFalse(delete.isConsumed)
        assertFalse(dispatcher.dispatchKeyEvent(press(KeyEvent.VK_Z, ctrl)), "the field undoes its own text")
        assertTrue(dispatcher.dispatchKeyEvent(press(KeyEvent.VK_S, ctrl)))
        assertTrue(dispatcher.dispatchKeyEvent(press(KeyEvent.VK_F5)))
        assertEquals(listOf("save", "play"), runs)
    }

    @Test fun scopedCommandsFireOnlyWhileTheirPanelHasFocus() {
        for(panel in listOf("hierarchy", "scene")) {
            context = CommandContext(focusedPanel = PanelId(panel))
            assertTrue(dispatcher.dispatchKeyEvent(press(KeyEvent.VK_DELETE)), panel)
            dispatcher.dispatchKeyEvent(release(KeyEvent.VK_DELETE))
        }
        for(panel in listOf(PanelId("inspector"), PanelId("assets"), PanelId("game"), null)) {
            context = CommandContext(focusedPanel = panel)
            assertFalse(dispatcher.dispatchKeyEvent(press(KeyEvent.VK_DELETE)), panel.toString())
        }
        assertEquals(listOf("delete", "delete"), runs)
        // Global and Document commands work from any panel.
        assertTrue(dispatcher.dispatchKeyEvent(press(KeyEvent.VK_S, ctrl)))
    }

    @Test fun disabledCommandsAndForeignWindowsLetTheKeyThrough() {
        enabled = false
        val save = press(KeyEvent.VK_S, ctrl)
        assertFalse(dispatcher.dispatchKeyEvent(save));assertFalse(save.isConsumed)
        enabled = true;context = null
        assertFalse(dispatcher.dispatchKeyEvent(press(KeyEvent.VK_S, ctrl)))
        // Modifiers alone, Meta chords and unbound chords never match.
        context = CommandContext(focusedPanel = PanelId("hierarchy"))
        assertFalse(dispatcher.dispatchKeyEvent(press(KeyEvent.VK_CONTROL, ctrl)))
        assertFalse(dispatcher.dispatchKeyEvent(press(KeyEvent.VK_S, InputEvent.META_DOWN_MASK)))
        assertFalse(dispatcher.dispatchKeyEvent(press(KeyEvent.VK_S, ctrl or InputEvent.SHIFT_DOWN_MASK)))
        assertTrue(runs.isEmpty())
    }

    // --- Standard commands -------------------------------------------------------------------

    private val catalog = mapOf("Transform" to sle.editor.model.ComponentDefinition(
        kotlinx.serialization.json.Json.parseToJsonElement("""{"position":{"x":0,"y":0},"rotation":0,"scale":{"x":1,"y":1}}""") as kotlinx.serialization.json.JsonObject,
        kotlinx.serialization.json.JsonObject(emptyMap())))
    private fun standard(scene: SceneWorkspaceState, panel: String, playback: ScenePlaybackState = ScenePlaybackState { it }) =
        ShortcutDispatcher({ standardKeymap }) {
            CommandContext(scene = scene, playback = playback, editable = playback.editing, focusedPanel = PanelId(panel))
        }

    @Test fun deleteAndDuplicateRunFromHierarchyAndSceneViewOnlyWhileEditing() {
        val scene = SceneWorkspaceState(catalog) { it }
        scene.addEntity();val first = scene.selectedKey!!
        assertTrue(standard(scene, "hierarchy").dispatchKeyEvent(press(KeyEvent.VK_D, ctrl)))
        assertEquals(2, scene.entities.size);assertNotEquals(first, scene.selectedKey)
        assertTrue(standard(scene, "scene").dispatchKeyEvent(press(KeyEvent.VK_DELETE)))
        assertEquals(1, scene.entities.size);assertNull(scene.selectedKey)
        // Nothing selected: Delete is disabled and passes through.
        assertFalse(standard(scene, "scene").dispatchKeyEvent(press(KeyEvent.VK_DELETE)))
        scene.select(first)
        assertFalse(standard(scene, "inspector").dispatchKeyEvent(press(KeyEvent.VK_DELETE)))
        val playing = ScenePlaybackState { it };playing.play(scene)
        assertFalse(standard(scene, "scene", playing).dispatchKeyEvent(press(KeyEvent.VK_DELETE)))
        assertFalse(standard(scene, "hierarchy", playing).dispatchKeyEvent(press(KeyEvent.VK_D, ctrl)))
        assertEquals(1, scene.entities.size)
    }

    @Test fun menuEntriesTakeEnabledStateLabelAndChordFromCommands() {
        val scene = SceneWorkspaceState(catalog) { it }
        fun menus(editable: Boolean) = editorApplicationMenus(CommandContext(scene = scene, editable = editable), {}, {}).toMap()
        fun edit(editable: Boolean, label: String) = menus(editable).getValue("Edit").single { it.label == label }
        assertFalse(edit(true, "Delete").enabled)
        scene.addEntity()
        val delete = edit(true, "Delete")
        assertTrue(delete.enabled);assertEquals(EditCommands.delete.id, delete.command);assertEquals("Delete", delete.shortcut?.display())
        assertFalse(edit(false, "Delete").enabled)
        assertEquals("Ctrl+Y", edit(true, "Redo").shortcut?.display())
        val file = menus(true).getValue("File")
        assertEquals("Ctrl+S", file.single { it.label == "Save" }.shortcut?.display())
        assertEquals(listOf("Scene", "Prefab"), file.single { it.label == "New" }.children.map { it.label })
        assertTrue(menus(true).getValue("Edit").none { it.label == "Delete Entity" || it.label == "Duplicate Subtree" })
        assertEquals(listOf("Keyboard Shortcuts…", "About SLE Editor…"), menus(true).getValue("Help").map { it.label })
        // Context menus reference the same commands.
        val context = sceneCreationMenu(scene, true)
        assertEquals(EditCommands.duplicate.id, context.single { it.label == "Duplicate" }.command)
        assertEquals("Ctrl+D", context.single { it.label == "Duplicate" }.shortcut?.display())
        assertFalse(sceneCreationMenu(scene, false).single { it.label == "Delete" }.enabled)
        // A disabled entry never runs its command.
        sceneCreationMenu(scene, false).single { it.label == "Duplicate" }.action()
        assertEquals(1, scene.entities.size)
    }
}
