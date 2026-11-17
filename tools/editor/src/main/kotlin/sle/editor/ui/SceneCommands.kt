package sle.editor.ui

import sle.editor.model.SceneWorkspaceState
import java.awt.Dialog
import java.awt.Window

/** Tooltip of actions that work on one entity while several are selected. */
internal const val SINGLE_ENTITY_REASON="Select a single entity to use this"

/**
 * Reports a recorded deletion ("Deleted 3 entities") with an Undo button that runs the active
 * document's registered Undo command. The button shows only while [scene] is the active document
 * and the deletion is still its latest step, so it never undoes a later edit. [context] builds the
 * live command context when the button is drawn or clicked.
 */
internal fun reportDeletion(notices: EditorNotices, scene: SceneWorkspaceState, subject: String,
    context: () -> sle.editor.command.CommandContext) {
    val step=scene.history.latestStep ?: return
    val undo=sle.editor.command.EditCommands.undo
    // An open gesture would make Undo cancel it instead of reverting the deletion.
    fun applies(ctx: sle.editor.command.CommandContext)=ctx.document===scene && !scene.gestureActive &&
        scene.history.latestStep===step && undo.enabled(ctx)
    notices.info("Deleted $subject",NoticeAction("Undo",available={ applies(context()) }) {
        val ctx=context()
        if(applies(ctx)) undo.run(ctx)
    })
}

/** Local hierarchy/canvas handlers call this; Inspector text fields keep their own Delete keys. */
internal fun deleteSceneSelection(state: SceneWorkspaceState, editable: Boolean=true,
    modal: Boolean=Window.getWindows().any { it is Dialog && it.isModal && it.isVisible }): Boolean {
    if(!editable || modal || state.selected==null) return false
    return try {
        state.selected?.endGesture()
        state.removeSelected()
        true
    } catch(failure: Exception) {
        state.error=failure.message ?: "Could not delete selection"
        false
    }
}
