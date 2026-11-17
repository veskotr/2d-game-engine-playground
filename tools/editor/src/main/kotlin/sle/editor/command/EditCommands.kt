package sle.editor.command

import java.awt.event.KeyEvent
import sle.editor.ui.applySelectionToPrefab
import sle.editor.ui.canApplyToPrefab
import sle.editor.ui.deleteSceneSelection

/** Edit commands. Undo/Redo still use the active entity's history until the unified timeline (L1 S2). */
internal object EditCommands {
    val undo = EditorCommand(CommandId("edit.undo"), "Undo", defaultChords = listOf(KeyChord.ctrl(KeyEvent.VK_Z)),
        scopes = setOf(CommandScope.Document), repeatable = true,
        // Undo during a drag or numeric edit cancels that gesture first.
        enabled = { it.editable && it.entity?.let { entity -> entity.canUndo || entity.gestureActive } == true }) { it.entity?.undo() }
    val redo = EditorCommand(CommandId("edit.redo"), "Redo",
        defaultChords = listOf(KeyChord.ctrl(KeyEvent.VK_Y), KeyChord.ctrl(KeyEvent.VK_Z, shift = true)),
        scopes = setOf(CommandScope.Document), repeatable = true,
        enabled = { it.editable && it.entity?.let { entity -> entity.canRedo || entity.gestureActive } == true }) { ctx ->
        ctx.entity?.let { if(it.gestureActive) it.cancelGesture() else it.redo() }
    }
    val sceneUndo = EditorCommand(CommandId("edit.scene.undo"), "Scene Undo",
        enabled = { it.editable && it.sceneDocument && it.scene?.canUndoStructure == true }) { ctx -> sceneAttempt(ctx) { it.undoStructure() } }
    val sceneRedo = EditorCommand(CommandId("edit.scene.redo"), "Scene Redo",
        enabled = { it.editable && it.sceneDocument && it.scene?.canRedoStructure == true }) { ctx -> sceneAttempt(ctx) { it.redoStructure() } }

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

    val all = listOf(undo, redo, sceneUndo, sceneRedo, editEntity, duplicate, delete, applyToPrefab)
}
