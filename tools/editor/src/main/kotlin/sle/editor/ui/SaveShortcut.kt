package sle.editor.ui

import java.awt.KeyEventDispatcher
import java.awt.event.KeyEvent

/** Runs before Compose or heavyweight viewport delivery; command availability stays menu-owned. */
internal class SaveShortcut(
    private val accepts: (KeyEvent)->Boolean,
    private val modal: ()->Boolean,
    private val command: ()->EditorMenuEntry?,
) : KeyEventDispatcher {
    private var held=false

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if(event.keyCode!=KeyEvent.VK_S) return false
        if(event.id==KeyEvent.KEY_RELEASED) { held=false;return false }
        if(event.id!=KeyEvent.KEY_PRESSED || !event.isControlDown || event.isAltDown || event.isShiftDown || event.isMetaDown) return false
        if(!accepts(event) || modal()) { held=false;return false }
        val save=command()?.takeIf { it.enabled } ?: return false
        event.consume()
        if(!held) {
            held=true
            save.action()
        }
        return true
    }
}
