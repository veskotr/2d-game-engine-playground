package sle.editor.command

import sle.editor.ui.EditorMenuEntry

/**
 * A menu or context-menu entry for this command: label, enabled state and displayed chord come
 * from the registry. The chord is display-only; the dispatcher alone handles keys, so a key
 * never triggers both a menu accelerator and a shortcut.
 */
internal fun EditorCommand.menuEntry(ctx: CommandContext, keymap: Keymap = standardKeymap, label: String = menuLabel ?: this.label) =
    enabled(ctx).let { on ->
        EditorMenuEntry(label, on, command = id, shortcut = keymap.primary(id), help = if(on) null else disabledReason(ctx)) { if(enabled(ctx)) run(ctx) }
    }

internal data class ShortcutRow(val label: String, val chords: List<KeyChord>)
internal data class ShortcutSection(val title: String, val rows: List<ShortcutRow>)

/** Help → Keyboard Shortcuts content: every bound command, grouped by where it works. */
internal fun keyboardShortcutSections(keymap: Keymap = standardKeymap): List<ShortcutSection> {
    val bound = keymap.registry.commands.filter { keymap.chords(it.id).isNotEmpty() }
    return bound.groupBy { command -> command.scopes.sortedBy { it.ordinal } }
        .entries.sortedBy { (scopes, _) -> scopes.first().ordinal }
        .map { (scopes, commands) ->
            ShortcutSection(scopes.joinToString(" and ") { it.title }, commands.map { ShortcutRow(it.label, keymap.chords(it.id)) })
        }
}

/** Mouse gestures are widget behaviour, not commands, so Help lists them separately. */
internal val mouseGestureHelp: List<Pair<String, String>> = listOf(
    "Pan the Scene view" to "Middle-drag, or Alt+left-drag",
    "Zoom" to "Mouse wheel",
    "Select" to "Click an entity in the Scene view or Hierarchy",
    "Add to or remove from the selection" to "Ctrl+click or Shift+click in the Scene view; Ctrl+click in the Hierarchy",
    "Box select" to "Drag on empty space in the Scene view (Shift or Ctrl adds)",
    "Select a range" to "Shift+click in the Hierarchy (Ctrl+Shift adds the range)",
    "Clear the selection" to "Esc in the Hierarchy",
    "Move the selection" to "Drag any selected entity, or a Move tool (W) arrow or square; arrows nudge the selection",
    "Lock a move to one axis" to "Hold Shift while dragging",
    "Invert snapping for one drag or nudge" to "Hold Ctrl",
    "Context actions" to "Right-click",
    "Edit with handles" to "Drag a handle; Esc cancels the drag",
    "Save as a linked prefab" to "Drag an entity onto an Assets folder",
    "Edit a prefab" to "Double-click it in Assets",
    "Game input" to "Click the Game view while playing",
)

internal fun keyboardShortcutsHtml(keymap: Keymap = standardKeymap): String = buildString {
    fun escape(text: String) = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
    append("<html><body style='width:420px'>")
    for(section in keyboardShortcutSections(keymap) + ShortcutSection("Mouse", emptyList())) {
        append("<p style='margin-top:6px'><b>").append(escape(section.title)).append("</b></p><table cellspacing='0' cellpadding='2'>")
        val rows = if(section.title == "Mouse") mouseGestureHelp else section.rows.map { it.label to it.chords.joinToString(" or ") { chord -> chord.display() } }
        rows.forEach { (label, keys) -> append("<tr><td width='190'>").append(escape(label)).append("</td><td>").append(escape(keys)).append("</td></tr>") }
        append("</table>")
    }
    append("<p style='margin-top:6px'>While the Game view has focus and the game runs or is paused, every other key goes to the game.</p>")
    append("</body></html>")
}
