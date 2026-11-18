package sle.editor.command

import java.awt.event.KeyEvent
import sle.editor.ui.confirmDiscard
import sle.editor.ui.confirmDiscardScene
import sle.editor.ui.entityFileAction
import sle.editor.ui.sceneFileAction
import sle.editor.viewport.ViewportMode

/** File commands: New/Open entries and Save. */
internal object DocumentCommands {
    private fun files(ctx: CommandContext) = ctx.editable && !ctx.projectBusy

    val newProject = EditorCommand(CommandId("file.new.project"), "New Project…", menuLabel = "Project…", icon = "folder",
        enabled = { files(it) && it.actions.project != null }) { it.actions.project?.create?.invoke() }
    val newScene = EditorCommand(CommandId("file.new.scene"), "New Scene", menuLabel = "Scene", icon = "scene", enabled = ::files) { ctx ->
        ctx.scene?.let { if(confirmDiscardScene(it)) { it.newScene();ctx.actions.showScene(false) } }
    }
    val newPrefab = EditorCommand(CommandId("file.new.prefab"), "New Entity Template", menuLabel = "Entity Template", icon = "prefab", enabled = ::files) { ctx ->
        ctx.prefab?.let { if(confirmDiscard(it)) { it.newDocument();ctx.actions.openPrefabWorkspace() } }
    }
    val openProject = EditorCommand(CommandId("file.open.project"), "Open Project…", menuLabel = "Project…", icon = "folder",
        enabled = { files(it) && it.actions.project != null }) { it.actions.project?.open?.invoke() }
    val openScene = EditorCommand(CommandId("file.open.scene"), "Open Scene…", menuLabel = "Scene…", icon = "scene", enabled = ::files) { ctx ->
        ctx.scene?.let { if(sceneFileAction(it, false)) ctx.actions.showScene(false) else if(ctx.mode == ViewportMode.Entity) ctx.entity?.error = it.error }
    }
    val openPrefab = EditorCommand(CommandId("file.open.prefab"), "Open Entity Template…", menuLabel = "Entity Template…", icon = "prefab", enabled = ::files) { ctx ->
        ctx.prefab?.let { if(entityFileAction(it, false)) ctx.actions.openPrefabWorkspace() else if(ctx.sceneDocument) ctx.scene?.error = it.error }
    }

    /** Saves the active document: the scene (also while a scene entity is linked) or the standalone prefab. */
    private fun save(ctx: CommandContext, saveAs: Boolean) {
        if(ctx.sceneDocument) ctx.scene?.let { sceneFileAction(it, true, saveAs) } else ctx.entity?.let { entityFileAction(it, true, saveAs) }
    }
    private fun canSave(ctx: CommandContext) = files(ctx) && (ctx.mode != ViewportMode.Entity || ctx.entity != null)
    val save = EditorCommand(CommandId("file.save"), "Save", icon = "save", defaultChords = listOf(KeyChord.ctrl(KeyEvent.VK_S)),
        enabled = ::canSave) { save(it, false) }
    val saveAs = EditorCommand(CommandId("file.saveAs"), "Save As…", icon = "save_as", defaultChords = listOf(KeyChord.ctrl(KeyEvent.VK_S, shift = true)),
        enabled = ::canSave) { save(it, true) }

    val all = listOf(newProject, newScene, newPrefab, openProject, openScene, openPrefab, save, saveAs)
}
