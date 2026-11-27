package sle.editor.ui

import sle.editor.model.EntityWorkspaceState
import sle.editor.model.SceneWorkspaceState
import sle.editor.model.prefabFileName
import java.io.File

internal fun sceneFileAction(state: SceneWorkspaceState, save: Boolean, saveAs: Boolean=false): Boolean {
    if(!(save && !saveAs && state.projectDocument==null && state.file!=null)) state.projectFileAction?.let { return it(save,saveAs) }
    return try {
        if(!save && !confirmDiscardScene(state)) return false
        if(save && !saveAs && state.file?.toString()?.endsWith(".slescene",true)==true) { state.save(state.file!!);return true }
        val start=EditorFileLocations.folder(EditorFileLocations.Kind.Scene,state.file)
        val file=(if(save) EditorFilePicker.save("Save Scene Package",start,prefabFileName(state.name).removeSuffix(".prefab.json")+".slescene",
                EditorFilePicker.Filter("Scene package","slescene"))
            else EditorFilePicker.open("Open Scene",start,EditorFilePicker.Filter("Scenes","slescene","json"))) ?: return false
        if(save && file.exists() && file.toPath()!=state.file && !sle.editor.ui.dialog.EditorPrompts.confirm("Replace Scene","Replace ${file.name}?","Replace",danger=true)) return false
        if(save) state.save(file.toPath()) else state.open(file.toPath())
        EditorFileLocations.remember(EditorFileLocations.Kind.Scene,file)
        true
    } catch(failure: Exception) { state.error=failure.message ?: "Scene operation failed";false }
}

internal fun entityFileAction(state: EntityWorkspaceState, save: Boolean, saveAs: Boolean=false): Boolean {
    if(!(save && !saveAs && state.projectDocument==null && state.file!=null)) state.projectFileAction?.let { return it(save,saveAs) }
    return try {
        if(!save && !confirmDiscard(state)) return false
        if(save && !saveAs && state.file!=null) { state.save(state.file!!);return true }
        // A typed .json name saves plain entity template JSON; anything else becomes a .sleprefab package.
        val start=EditorFileLocations.folder(EditorFileLocations.Kind.Prefab,state.file)
        val templates=EditorFilePicker.Filter("Entity templates","sleprefab","json")
        val file=(if(save) EditorFilePicker.save("Save Entity Template",start,prefabFileName(state.document.name).removeSuffix(".prefab.json")+".sleprefab",templates,"sleprefab")
            else EditorFilePicker.open("Open Entity Template",start,templates)) ?: return false
        if(save && file.exists() && file.toPath()!=state.file && !sle.editor.ui.dialog.EditorPrompts.confirm("Replace File","Replace ${file.name}?","Replace",danger=true)) return false
        if(save) state.save(file.toPath()) else state.open(file.toPath())
        EditorFileLocations.remember(EditorFileLocations.Kind.Prefab,file)
        true
    } catch(failure: Exception) { state.error=failure.message ?: "File operation failed";false }
}
