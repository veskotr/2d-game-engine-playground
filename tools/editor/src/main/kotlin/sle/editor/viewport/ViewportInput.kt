package sle.editor.viewport

import java.awt.event.KeyEvent

private val engineKeys = buildMap {
    for (key in KeyEvent.VK_A..KeyEvent.VK_Z) put(key, key)
    for (key in KeyEvent.VK_0..KeyEvent.VK_9) put(key, key)
    for (key in KeyEvent.VK_F1..KeyEvent.VK_F12) put(key, 290 + key - KeyEvent.VK_F1)
    put(KeyEvent.VK_SPACE, 32)
    put(KeyEvent.VK_ESCAPE, 256)
    put(KeyEvent.VK_ENTER, 257)
    put(KeyEvent.VK_TAB, 258)
    put(KeyEvent.VK_BACK_SPACE, 259)
    put(KeyEvent.VK_INSERT, 260)
    put(KeyEvent.VK_DELETE, 261)
    put(KeyEvent.VK_RIGHT, 262)
    put(KeyEvent.VK_LEFT, 263)
    put(KeyEvent.VK_DOWN, 264)
    put(KeyEvent.VK_UP, 265)
    put(KeyEvent.VK_PAGE_UP, 266)
    put(KeyEvent.VK_PAGE_DOWN, 267)
    put(KeyEvent.VK_HOME, 268)
    put(KeyEvent.VK_END, 269)
    put(KeyEvent.VK_COMMA, 44)
    put(KeyEvent.VK_MINUS, 45)
    put(KeyEvent.VK_PERIOD, 46)
    put(KeyEvent.VK_SLASH, 47)
    put(KeyEvent.VK_SEMICOLON, 59)
    put(KeyEvent.VK_EQUALS, 61)
    put(KeyEvent.VK_OPEN_BRACKET, 91)
    put(KeyEvent.VK_BACK_SLASH, 92)
    put(KeyEvent.VK_CLOSE_BRACKET, 93)
    put(KeyEvent.VK_BACK_QUOTE, 96)
    put(KeyEvent.VK_QUOTE, 39)
}

private val modifierKeys = mapOf(
    KeyEvent.VK_SHIFT to 340,
    KeyEvent.VK_CONTROL to 341,
    KeyEvent.VK_ALT to 342,
    KeyEvent.VK_META to 343,
    KeyEvent.VK_WINDOWS to 343,
)

private const val keypadEnter = 335

private val keyNames = engineKeys.entries.associate { (awt, engine) -> engine to KeyEvent.getKeyText(awt) } +
    (keypadEnter to "Numpad Enter") + listOf("Shift", "Ctrl", "Alt", "Super").flatMapIndexed { index, name ->
        listOf(340 + index to "Left $name", 344 + index to "Right $name")
    }

/** Translate AWT virtual keys to the engine's existing GLFW-compatible key codes. */
internal fun engineKey(event: KeyEvent): Int {
    if (event.keyCode == KeyEvent.VK_ENTER && event.keyLocation == KeyEvent.KEY_LOCATION_NUMPAD) return keypadEnter
    val modifier = modifierKeys[event.keyCode]
    if (modifier != null) return modifier + if (event.keyLocation == KeyEvent.KEY_LOCATION_RIGHT) 4 else 0
    return engineKeys[event.keyCode] ?: -1
}

internal fun engineKeyName(key: Int): String? = keyNames[key]
