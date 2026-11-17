package sle.editor.command

import java.awt.event.KeyEvent
import sle.editor.model.EntityWorkspaceState
import sle.editor.model.SceneWorkspaceState
import sle.editor.model.UndoableDocument
import sle.editor.ui.applySelectionToPrefab
import sle.editor.ui.canApplyToPrefab
import sle.editor.ui.deleteSceneSelection
import sle.editor.ui.SINGLE_ENTITY_REASON

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
    /** Commands that need exactly one entity are disabled, with [singleReason], for other selections. */
    private fun single(ctx: CommandContext) = selected(ctx) && ctx.selectionCount == 1
    private fun singleReason(ctx: CommandContext) = if(ctx.selectionCount > 1) SINGLE_ENTITY_REASON else null
    private val sceneSelection = setOf(CommandScope.Hierarchy, CommandScope.SceneViewport)
    /** Selects every scene entity; selection is never an undo step. */
    val selectAll = EditorCommand(CommandId("edit.selectAll"), "Select All", defaultChords = listOf(KeyChord.ctrl(KeyEvent.VK_A)),
        scopes = sceneSelection, enabled = { it.sceneDocument && it.scene?.entities?.isNotEmpty() == true }) { ctx -> ctx.scene?.selectAll() }
    /** Removes the selected entities and their descendants as one undoable step. */
    val delete = EditorCommand(CommandId("edit.delete"), "Delete",
        defaultChords = listOf(KeyChord.plain(KeyEvent.VK_DELETE), KeyChord.plain(KeyEvent.VK_BACK_SPACE)),
        scopes = sceneSelection, enabled = ::selected) { ctx -> ctx.scene?.let { deleteSceneSelection(it, ctx.editable) } }
    /** Duplicates the top-most selected subtrees and selects the copies. */
    val duplicate = EditorCommand(CommandId("edit.duplicate"), "Duplicate", defaultChords = listOf(KeyChord.ctrl(KeyEvent.VK_D)),
        scopes = sceneSelection, enabled = ::selected) { ctx -> sceneAttempt(ctx) { it.duplicateSelected() } }
    val editEntity = EditorCommand(CommandId("edit.entity"), "Edit Entity", enabled = ::single, disabledReason = ::singleReason) { ctx ->
        ctx.scene?.selectedKey?.let(ctx.actions::editEntity)
    }
    val applyToPrefab = EditorCommand(CommandId("edit.applyToPrefab"), "Apply Changes to Prefab",
        enabled = { it.editable && it.sceneDocument && it.scene?.let(::canApplyToPrefab) == true },
        disabledReason = ::singleReason) { ctx -> sceneAttempt(ctx, ::applySelectionToPrefab) }

    val all = listOf(undo, redo, selectAll, editEntity, duplicate, delete, applyToPrefab)
}
