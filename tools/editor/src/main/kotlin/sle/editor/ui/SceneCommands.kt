package sle.editor.ui

import sle.editor.model.SceneWorkspaceState
import java.awt.Dialog
import java.awt.Window

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
