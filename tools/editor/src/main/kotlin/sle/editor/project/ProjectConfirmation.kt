package sle.editor.project

import kotlinx.coroutines.suspendCancellableCoroutine
import javax.swing.SwingUtilities

/** A modal Swing prompt must start after Compose finishes its current render/dispatch. */
internal suspend fun confirmProjectChange(confirm: ()->Boolean): Boolean = suspendCancellableCoroutine { continuation ->
    SwingUtilities.invokeLater {
        if(continuation.isActive) continuation.resumeWith(runCatching(confirm))
    }
}
