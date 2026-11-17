package sle.editor.ui.theme

import sle.editor.ui.EditorDialogAppearance

/** Update editor-owned Swing roots on the EDT without changing application defaults. */
internal fun applyEditorSwingAppearance(appearance: EditorAppearance) {
    sle.editor.ui.EditorWindowChrome.install(appearance.mode)
    EditorDialogAppearance.update(appearance)
}
