package sle.editor.ui

import androidx.compose.runtime.*
import java.awt.Dialog
import java.awt.KeyboardFocusManager
import java.awt.Window
import java.beans.PropertyChangeListener
import javax.swing.MenuSelectionManager
import javax.swing.SwingUtilities
import sle.editor.command.CommandContext
import sle.editor.command.GamePanel
import sle.editor.command.Keymap
import sle.editor.command.ShortcutDispatcher
import sle.editor.workspace.TextInputFocus
import sle.editor.workspace.WorkspaceWindowManager

/** True while a modal dialog or a Swing menu is open; shortcuts then stay out of the way. */
internal fun editorDialogOpen() = Window.getWindows().any { it is Dialog && it.isModal && it.isVisible }

internal fun editorModalOpen() = editorDialogOpen() ||
    MenuSelectionManager.defaultManager().selectedPath.isNotEmpty()

/**
 * Installs the one [ShortcutDispatcher] for all editor windows (main and detached). Events from
 * other windows pass through untouched. [context] is the application's current command context.
 */
@Composable
internal fun EditorShortcuts(manager: WorkspaceWindowManager, keymap: Keymap, context: () -> CommandContext) {
    val currentContext by rememberUpdatedState(context)
    val currentKeymap by rememberUpdatedState(keymap)
    DisposableEffect(manager) {
        val dispatcher = ShortcutDispatcher({ currentKeymap }) { event ->
            val source = event.component
            val window = source as? Window ?: source?.let(SwingUtilities::getWindowAncestor)
            val host = manager.boundWindows().entries.firstOrNull { it.value === window }?.key ?: return@ShortcutDispatcher null
            // A focused native canvas owns the keys even if Compose still remembers a focused field.
            val canvasPanel = manager.focus.componentPanel(source)
            currentContext().copy(
                focusedPanel = canvasPanel ?: manager.focus.composePanel(host),
                gameInputFocused = canvasPanel == GamePanel,
                modalOpen = editorModalOpen(),
                textInputFocused = canvasPanel == null && TextInputFocus.focusedIn(window),
            )
        }
        val focus = KeyboardFocusManager.getCurrentKeyboardFocusManager()
        // A key released while another window or app is active never reaches us: forget held keys.
        val reset = PropertyChangeListener { dispatcher.reset() }
        focus.addKeyEventDispatcher(dispatcher)
        focus.addPropertyChangeListener("activeWindow", reset)
        focus.addPropertyChangeListener("focusOwner", reset)
        onDispose {
            focus.removeKeyEventDispatcher(dispatcher)
            focus.removePropertyChangeListener("activeWindow", reset)
            focus.removePropertyChangeListener("focusOwner", reset)
        }
    }
}
