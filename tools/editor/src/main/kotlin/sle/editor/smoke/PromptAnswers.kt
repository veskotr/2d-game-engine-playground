package sle.editor.smoke

import sle.editor.ui.dialog.EditorPrompts
import sle.editor.ui.dialog.PROMPT_HANDLE
import sle.editor.ui.dialog.PromptHandle
import java.awt.Window
import javax.swing.RootPaneContainer
import javax.swing.SwingUtilities

/** The window showing [handle], once it is on screen. */
internal fun promptWindow(handle: PromptHandle): Window? = Window.getWindows().firstOrNull {
    it.isShowing && (it as? RootPaneContainer)?.rootPane?.getClientProperty(PROMPT_HANDLE) === handle
}

/** Presses [label] on a prompt, first typing [text] into its field when given. */
internal fun press(label: String, text: String? = null): (PromptHandle) -> Unit = { it.answer(label, text) }

/**
 * Answers the next prompts in order from a background thread, as a user would: each response
 * runs on the event-dispatch thread once that prompt's window is showing.
 */
internal fun answerPrompts(description: String, vararg responses: (PromptHandle) -> Unit): Thread = Thread {
    for ((index, respond) in responses.withIndex()) {
        var answered: PromptHandle? = null
        repeat(350) {
            if (answered == null) {
                SwingUtilities.invokeAndWait {
                    val handle = EditorPrompts.current
                    if (handle != null && handle.result == null && promptWindow(handle) != null) {
                        respond(handle)
                        answered = handle
                    }
                }
                if (answered == null) Thread.sleep(20)
            }
        }
        val handle = checkNotNull(answered) { "$description prompt ${index + 1} did not open" }
        check(handle.result != null) { "$description prompt ${index + 1} (${handle.request.title}) rejected the answer" }
        // Let the nested loop return before the next prompt opens.
        Thread.sleep(200)
    }
}.apply { isDaemon = true; start() }
