package sle.editor.command

import java.awt.event.KeyEvent
import sle.editor.model.EntityWorkspaceState
import sle.editor.model.SceneWorkspaceState
import sle.editor.model.UndoableDocument
import sle.editor.ui.applySelectionToPrefab
import sle.editor.ui.canApplyToPrefab
import sle.editor.ui.deleteSceneSelection

/** Edit commands. Undo and Redo walk the active document's single timeline ([CommandContext.document]). */
internal object EditCommands {
    val undo = EditorCommand(CommandId("edit.undo"), "Undo", defaultChords = listOf(KeyChord.ctrl(KeyEvent.VK_Z)),
        scopes = setOf(CommandScope.Document), repeatable = true,
        // Undo during a drag or numeric edit cancels that gesture first.
        enabled = { it.editable && it.document?.let { document -> document.history.canUndo || document.gestureActive } == true }) { ctx ->
        timelineAttempt(ctx, UndoableDocument::undo)
    }
    val redo = EditorCommand(CommandId("edit.redo"), "Redo",
        defaultChords = listOf(KeyChord.ctrl(KeyEvent.VK_Y), KeyChord.ctrl(KeyEvent.VK_Z, shift = true)),
        scopes = setOf(CommandScope.Document), repeatable = true,
        enabled = { it.editable && it.document?.let { document -> document.history.canRedo || document.gestureActive } == true }) { ctx ->
        timelineAttempt(ctx, UndoableDocument::redo)
    }
    /** "Undo Move Player" for the Edit menu; plain "Undo" while a gesture would be cancelled. */
    fun undoLabel(ctx: CommandContext) = ctx.document?.takeIf { !it.gestureActive }?.history?.undoLabel?.let { "Undo $it" } ?: "Undo"
    fun redoLabel(ctx: CommandContext) = ctx.document?.takeIf { !it.gestureActive }?.history?.redoLabel?.let { "Redo $it" } ?: "Redo"
    // A step that cannot be applied (it would break an Animator target) stays and becomes the notice.
    private fun timelineAttempt(ctx: CommandContext, action: (UndoableDocument) -> Unit) {
        val document = ctx.document ?: return
        try { action(document) } catch(failure: Exception) {
            val text = failure.message ?: "Undo failed"
            when(document) { is SceneWorkspaceState -> document.error = text; is EntityWorkspaceState -> document.error = text }
        }
    }

    private fun selected(ctx: CommandContext) = ctx.editable && ctx.sceneDocument && ctx.selection != null
    private val sceneSelection = setOf(CommandScope.Hierarchy, CommandScope.SceneViewport)
    /** Removes the selected entity and its descendants as one undoable step. */
    val delete = EditorCommand(CommandId("edit.delete"), "Delete",
        defaultChords = listOf(KeyChord.plain(KeyEvent.VK_DELETE), KeyChord.plain(KeyEvent.VK_BACK_SPACE)),
        scopes = sceneSelection, enabled = ::selected) { ctx -> ctx.scene?.let { deleteSceneSelection(it, ctx.editable) } }
    /** Duplicates the selected subtree and selects the copy. */
    val duplicate = EditorCommand(CommandId("edit.duplicate"), "Duplicate", defaultChords = listOf(KeyChord.ctrl(KeyEvent.VK_D)),
        scopes = sceneSelection, enabled = ::selected) { ctx -> sceneAttempt(ctx) { it.duplicateSelected() } }
    val editEntity = EditorCommand(CommandId("edit.entity"), "Edit Entity", enabled = ::selected) { ctx ->
        ctx.scene?.selectedKey?.let(ctx.actions::editEntity)
    }
    val applyToPrefab = EditorCommand(CommandId("edit.applyToPrefab"), "Apply Changes to Prefab",
        enabled = { it.editable && it.sceneDocument && it.scene?.let(::canApplyToPrefab) == true }) { ctx -> sceneAttempt(ctx, ::applySelectionToPrefab) }

    val all = listOf(undo, redo, editEntity, duplicate, delete, applyToPrefab)
}
