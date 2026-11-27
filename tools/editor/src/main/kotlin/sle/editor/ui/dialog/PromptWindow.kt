package sle.editor.ui.dialog

import androidx.compose.ui.awt.ComposeDialog
import sle.editor.ui.EditorDialogAppearance
import sle.editor.ui.EditorDialogOwner
import sle.editor.ui.EditorWindowChrome
import java.awt.Dialog
import java.awt.Window
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import javax.swing.JDialog
import javax.swing.SwingUtilities

/** Client property on the prompt's root pane naming its [PromptHandle]. */
internal const val PROMPT_HANDLE = "sle.editor.prompt"

/**
 * Shows [handle] in a modal [ComposeDialog] owned by the active editor window and returns once it
 * closes. Like `JOptionPane`, the call blocks in a nested event loop on the event-dispatch thread.
 */
internal fun presentPrompt(handle: PromptHandle) {
    check(SwingUtilities.isEventDispatchThread()) { "Editor prompts run on the event-dispatch thread" }
    val owner = EditorDialogOwner.resolve(null)?.let { it as? Window ?: SwingUtilities.getWindowAncestor(it) }
    val dialog = ComposeDialog(owner, Dialog.ModalityType.DOCUMENT_MODAL)
    try {
        dialog.title = handle.request.title
        dialog.defaultCloseOperation = JDialog.DO_NOTHING_ON_CLOSE
        dialog.isResizable = false
        dialog.rootPane.putClientProperty(PROMPT_HANDLE, handle)
        dialog.addWindowListener(object : WindowAdapter() {
            override fun windowClosing(event: WindowEvent) = handle.answer(-1)
        })
        handle.close = { dialog.isVisible = false }
        val appearance = EditorDialogAppearance.appearance
        dialog.setContent { EditorDialogSurface(appearance) { PromptContent(handle) } }
        dialog.pack()
        EditorWindowChrome.apply(dialog)
        dialog.setLocationRelativeTo(owner)
        if (handle.result == null) dialog.isVisible = true
    } finally {
        handle.close = {}
        dialog.dispose()
    }
}
