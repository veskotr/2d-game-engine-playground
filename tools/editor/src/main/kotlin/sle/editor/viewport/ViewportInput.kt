package sle.editor.viewport

import java.awt.event.KeyEvent

/** Translate AWT virtual keys to the engine's existing GLFW-compatible key codes. */
internal fun engineKey(event: KeyEvent): Int = when (event.keyCode) {
    in KeyEvent.VK_A..KeyEvent.VK_Z, in KeyEvent.VK_0..KeyEvent.VK_9 -> event.keyCode
    in KeyEvent.VK_F1..KeyEvent.VK_F12 -> 290 + event.keyCode - KeyEvent.VK_F1
    KeyEvent.VK_SPACE -> 32
    KeyEvent.VK_ESCAPE -> 256
    KeyEvent.VK_ENTER -> 257
    KeyEvent.VK_TAB -> 258
    KeyEvent.VK_BACK_SPACE -> 259
    KeyEvent.VK_INSERT -> 260
    KeyEvent.VK_DELETE -> 261
    KeyEvent.VK_RIGHT -> 262
    KeyEvent.VK_LEFT -> 263
    KeyEvent.VK_DOWN -> 264
    KeyEvent.VK_UP -> 265
    KeyEvent.VK_PAGE_UP -> 266
    KeyEvent.VK_PAGE_DOWN -> 267
    KeyEvent.VK_HOME -> 268
    KeyEvent.VK_END -> 269
    KeyEvent.VK_SHIFT -> if (event.keyLocation == KeyEvent.KEY_LOCATION_RIGHT) 344 else 340
    KeyEvent.VK_CONTROL -> if (event.keyLocation == KeyEvent.KEY_LOCATION_RIGHT) 345 else 341
    KeyEvent.VK_ALT -> if (event.keyLocation == KeyEvent.KEY_LOCATION_RIGHT) 346 else 342
    KeyEvent.VK_META, KeyEvent.VK_WINDOWS -> if (event.keyLocation == KeyEvent.KEY_LOCATION_RIGHT) 347 else 343
    KeyEvent.VK_COMMA -> 44
    KeyEvent.VK_MINUS -> 45
    KeyEvent.VK_PERIOD -> 46
    KeyEvent.VK_SLASH -> 47
    KeyEvent.VK_SEMICOLON -> 59
    KeyEvent.VK_EQUALS -> 61
    KeyEvent.VK_OPEN_BRACKET -> 91
    KeyEvent.VK_BACK_SLASH -> 92
    KeyEvent.VK_CLOSE_BRACKET -> 93
    KeyEvent.VK_BACK_QUOTE -> 96
    KeyEvent.VK_QUOTE -> 39
    else -> -1
}
