package sle.editor.command

import java.awt.event.KeyEvent
import sle.editor.workspace.PanelId

/** Stable identity of an editor command; keymap overrides and menus refer to it. */
@JvmInline value class CommandId(val value: String) { init { require(value.isNotBlank()) } }

/**
 * One key plus modifiers. Meta (Cmd) is not part of SLE's Windows/Linux scheme, so a key
 * pressed with Meta never matches a chord.
 */
internal data class KeyChord(val keyCode: Int, val ctrl: Boolean = false, val shift: Boolean = false, val alt: Boolean = false) {
    init { require(keyCode !in modifierKeys && keyCode != KeyEvent.VK_UNDEFINED) { "A chord needs a non-modifier key" } }
    /** Ctrl or Alt chords are commands rather than text entry. */
    val commandModifier get() = ctrl || alt
    val functionKey get() = keyCode in KeyEvent.VK_F1..KeyEvent.VK_F12 || keyCode in KeyEvent.VK_F13..KeyEvent.VK_F24
    fun display(): String = buildList {
        if(ctrl) add("Ctrl");if(shift) add("Shift");if(alt) add("Alt");add(keyName(keyCode))
    }.joinToString("+")
    override fun toString() = display()

    companion object {
        private val modifierKeys = setOf(KeyEvent.VK_CONTROL, KeyEvent.VK_SHIFT, KeyEvent.VK_ALT, KeyEvent.VK_META,
            KeyEvent.VK_ALT_GRAPH, KeyEvent.VK_WINDOWS, KeyEvent.VK_CAPS_LOCK)
        private val names: Map<Int, String> = buildMap {
            ('A'..'Z').forEach { put(KeyEvent.VK_A + (it - 'A'), it.toString()) }
            ('0'..'9').forEach { put(KeyEvent.VK_0 + (it - '0'), it.toString()) }
            (1..12).forEach { put(KeyEvent.VK_F1 + it - 1, "F$it") }
            (13..24).forEach { put(KeyEvent.VK_F13 + it - 13, "F$it") }
            put(KeyEvent.VK_DELETE, "Delete");put(KeyEvent.VK_BACK_SPACE, "Backspace");put(KeyEvent.VK_ESCAPE, "Esc")
            put(KeyEvent.VK_ENTER, "Enter");put(KeyEvent.VK_SPACE, "Space");put(KeyEvent.VK_TAB, "Tab")
            put(KeyEvent.VK_LEFT, "Left");put(KeyEvent.VK_RIGHT, "Right");put(KeyEvent.VK_UP, "Up");put(KeyEvent.VK_DOWN, "Down")
            put(KeyEvent.VK_HOME, "Home");put(KeyEvent.VK_END, "End");put(KeyEvent.VK_PAGE_UP, "PageUp");put(KeyEvent.VK_PAGE_DOWN, "PageDown")
            put(KeyEvent.VK_INSERT, "Insert")
        }
        private val codes = names.entries.associate { (code, name) -> name.lowercase() to code }
        /** Locale-independent names, so menus and Help read the same on every system. */
        fun keyName(code: Int): String = names[code] ?: KeyEvent.getKeyText(code)

        fun plain(code: Int) = KeyChord(code)
        fun ctrl(code: Int, shift: Boolean = false) = KeyChord(code, ctrl = true, shift = shift)
        fun shift(code: Int) = KeyChord(code, shift = true)

        /** The chord of a key event, or null for bare modifiers and Meta chords. */
        fun of(event: KeyEvent): KeyChord? =
            if(event.keyCode in modifierKeys || event.keyCode == KeyEvent.VK_UNDEFINED || event.isMetaDown) null
            else KeyChord(event.keyCode, event.isControlDown, event.isShiftDown, event.isAltDown)

        /** Parses "Ctrl+Shift+S"-style text, the format keymap overrides are stored in. */
        fun parse(text: String): KeyChord {
            val parts = text.split('+').map { it.trim() }.filter { it.isNotEmpty() }
            require(parts.isNotEmpty()) { "Empty shortcut" }
            val modifiers = parts.dropLast(1).map { it.lowercase() }
            modifiers.forEach { require(it in setOf("ctrl", "shift", "alt")) { "Unknown modifier '$it' in '$text'" } }
            val code = codes[parts.last().lowercase()] ?: error("Unknown key '${parts.last()}' in '$text'")
            return KeyChord(code, "ctrl" in modifiers, "shift" in modifiers, "alt" in modifiers)
        }
    }
}

/**
 * Where a command's shortcut is active. Global and Document commands work from any panel;
 * the others need focus inside their panel (Compose focus-within or the native canvas).
 */
internal enum class CommandScope(val title: String, private val panels: Set<PanelId>?) {
    Global("Global", null),
    Document("Document", null),
    Hierarchy("Hierarchy", setOf(PanelId("hierarchy"))),
    SceneViewport("Scene view", setOf(PanelId("scene"))),
    Assets("Assets", setOf(PanelId("assets")));

    fun accepts(panel: PanelId?) = panels == null || panel in panels
    fun overlaps(other: CommandScope) = panels == null || other.panels == null || panels.any { it in other.panels }
}

/** The Game panel receives raw keys while the game runs; see [shortcutRules]. */
internal val GamePanel = PanelId("game")
