package sle.editor.command

import java.awt.KeyEventDispatcher
import java.awt.event.KeyEvent

/** One routing rule: a command may fire for this chord only when every rule allows it. */
internal class ShortcutRule(val description: String, val allows: (CommandContext, EditorCommand, KeyChord) -> Boolean)

/** Chords a focused text field handles itself (its own undo, selection and clipboard). */
internal val textEditingChords: Set<KeyChord> = buildSet {
    listOf(KeyEvent.VK_Z, KeyEvent.VK_Y, KeyEvent.VK_A, KeyEvent.VK_C, KeyEvent.VK_X, KeyEvent.VK_V, KeyEvent.VK_INSERT,
        KeyEvent.VK_LEFT, KeyEvent.VK_RIGHT, KeyEvent.VK_UP, KeyEvent.VK_DOWN, KeyEvent.VK_HOME, KeyEvent.VK_END,
        KeyEvent.VK_DELETE, KeyEvent.VK_BACK_SPACE).forEach { code ->
        add(KeyChord.ctrl(code));add(KeyChord.ctrl(code, shift = true))
    }
    add(KeyChord.shift(KeyEvent.VK_INSERT));add(KeyChord.shift(KeyEvent.VK_DELETE))
}

/** Evaluated in order; the table is the specification and is unit-tested row by row. */
internal val shortcutRules: List<ShortcutRule> = listOf(
    ShortcutRule("A modal dialog or an open menu blocks every shortcut") { ctx, _, _ -> !ctx.modalOpen },
    ShortcutRule("The focused Game view keeps every key except play controls while the game runs or is paused") { ctx, command, _ ->
        !(ctx.focusedPanel == GamePanel && ctx.gameRunning) || command.playControl },
    ShortcutRule("Focused text input keeps plain keys and its own editing chords; only Ctrl/Alt and function-key chords fire") { ctx, _, chord ->
        !ctx.textInputFocused || ((chord.commandModifier || chord.functionKey) && chord !in textEditingChords) },
    ShortcutRule("Scoped commands fire only while their panel has focus") { ctx, command, _ ->
        command.scopes.any { it.accepts(ctx.focusedPanel) } },
)

/**
 * The editor's single keyboard shortcut path. It runs before Compose and the native canvas see
 * the key. [context] returns null for events outside editor windows. A matching but disabled
 * command lets the key through, so widgets keep their own handling.
 */
internal class ShortcutDispatcher(
    private val keymap: () -> Keymap,
    private val rules: List<ShortcutRule> = shortcutRules,
    private val context: (KeyEvent) -> CommandContext?,
) : KeyEventDispatcher {
    /** Keys whose press ran a command and that have not been released since. */
    private val held = mutableSetOf<Int>()

    /** Called on key release, focus change and window deactivation: a release outside the app is never seen. */
    fun reset() = held.clear()

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if(event.id == KeyEvent.KEY_RELEASED) { held.remove(event.keyCode);return false }
        if(event.id != KeyEvent.KEY_PRESSED) return false
        val chord = KeyChord.of(event) ?: return false
        val ctx = context(event) ?: run { held.remove(event.keyCode);return false }
        val routed = route(keymap(), chord, ctx)
        if(routed.isEmpty()) return false
        if(event.keyCode in held && routed.none { it.repeatable }) { event.consume();return true }
        val command = routed.firstOrNull { it.enabled(ctx) } ?: return false
        event.consume()
        held.add(event.keyCode)
        command.run(ctx)
        return true
    }

    /** Commands bound to [chord] that the rules allow here, before their enabled state. */
    fun route(keymap: Keymap, chord: KeyChord, ctx: CommandContext): List<EditorCommand> =
        keymap.commandsFor(chord).filter { command -> rules.all { it.allows(ctx, command, chord) } }
}
