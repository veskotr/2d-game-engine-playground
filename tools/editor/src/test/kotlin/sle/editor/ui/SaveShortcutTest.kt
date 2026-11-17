package sle.editor.ui

import java.awt.Canvas
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import kotlin.test.*
import org.junit.Test

class SaveShortcutTest {
    private fun key(id: Int=KeyEvent.KEY_PRESSED, modifiers: Int=InputEvent.CTRL_DOWN_MASK) =
        KeyEvent(Canvas(),id,0,modifiers,KeyEvent.VK_S,'s')

    @Test fun savesOncePerPressAndUsesLatestCommand() {
        var saves=0
        var command=EditorMenuEntry("Save") { saves++ }
        val shortcut=SaveShortcut({true},{false}) { command }
        assertTrue(shortcut.dispatchKeyEvent(key()))
        assertTrue(shortcut.dispatchKeyEvent(key()))
        assertEquals(1,saves)
        shortcut.dispatchKeyEvent(key(KeyEvent.KEY_RELEASED,0))
        command=EditorMenuEntry("Save") { saves+=10 }
        assertTrue(shortcut.dispatchKeyEvent(key()))
        assertEquals(11,saves)
    }

    @Test fun respectsDisabledCommandsModalDialogsAndWindowScope() {
        var saves=0
        var allowed=true
        var modal=false
        var enabled=false
        val shortcut=SaveShortcut({allowed},{modal}) { EditorMenuEntry("Save",enabled) { saves++ } }
        assertFalse(shortcut.dispatchKeyEvent(key()))
        enabled=true;modal=true
        assertFalse(shortcut.dispatchKeyEvent(key()))
        modal=false;allowed=false
        assertFalse(shortcut.dispatchKeyEvent(key()))
        allowed=true
        assertFalse(shortcut.dispatchKeyEvent(key(modifiers=InputEvent.CTRL_DOWN_MASK or InputEvent.SHIFT_DOWN_MASK)))
        assertEquals(0,saves)
        assertTrue(shortcut.dispatchKeyEvent(key()))
        assertEquals(1,saves)
    }
}
