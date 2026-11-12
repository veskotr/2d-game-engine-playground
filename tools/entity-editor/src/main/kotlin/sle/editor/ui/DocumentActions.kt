package sle.editor.ui

import sle.editor.model.EntityWorkspaceState
import sle.editor.model.SceneWorkspaceState
import sle.editor.model.prefabFileName
import java.io.File
import javax.swing.JFileChooser
import javax.swing.JOptionPane
import javax.swing.filechooser.FileNameExtensionFilter

internal fun sceneFileAction(state: SceneWorkspaceState, save: Boolean, saveAs: Boolean=false): Boolean {
    return try {
        if(!save && !confirmDiscardScene(state)) return false
        if(save && !saveAs && state.file?.toString()?.endsWith(".slescene",true)==true) { state.save(state.file!!);return true }
        val chooser=JFileChooser(state.file?.parent?.toFile() ?: File("assets/scenes")).apply {
            fileFilter=if(save) FileNameExtensionFilter("Scene package (*.slescene)","slescene") else FileNameExtensionFilter("Scenes (*.slescene, *.json)","slescene","json")
            if(save) selectedFile=File(prefabFileName(state.name).removeSuffix(".prefab.json")+".slescene")
        }
        if((if(save) chooser.showSaveDialog(null) else chooser.showOpenDialog(null))!=JFileChooser.APPROVE_OPTION) return false
        val file=chooser.selectedFile.let { if(save && !it.name.endsWith(".slescene",true)) File(it.parentFile,it.name+".slescene") else it }
        if(save && file.exists() && file.toPath()!=state.file && JOptionPane.showConfirmDialog(null,"Replace ${file.name}?","Replace scene",JOptionPane.YES_NO_OPTION)!=JOptionPane.YES_OPTION) return false
        if(save) state.save(file.toPath()) else state.open(file.toPath())
        true
    } catch(failure: Exception) { state.error=failure.message ?: "Scene operation failed";false }
}

internal fun entityFileAction(state: EntityWorkspaceState, save: Boolean, saveAs: Boolean=false): Boolean {
    return try {
        if(!save && !confirmDiscard(state)) return false
        if(save && !saveAs && state.file!=null) { state.save(state.file!!);return true }
        val chooser=JFileChooser(state.file?.parent?.toFile() ?: File("assets/prefabs"))
        val jsonFilter=FileNameExtensionFilter("Plain prefab JSON (*.json)","json")
        chooser.addChoosableFileFilter(jsonFilter)
        chooser.fileFilter=if(save) FileNameExtensionFilter("Prefab package (*.sleprefab)","sleprefab") else FileNameExtensionFilter("Entity prefabs (*.sleprefab, *.json)","sleprefab","json")
        if(save) chooser.selectedFile=File(prefabFileName(state.document.name).removeSuffix(".prefab.json"))
        if((if(save) chooser.showSaveDialog(null) else chooser.showOpenDialog(null))!=JFileChooser.APPROVE_OPTION) return false
        val file=chooser.selectedFile.let { if(save && !it.name.endsWith(".json",true) && !it.name.endsWith(".sleprefab",true)) File(it.parentFile,it.name+if(chooser.fileFilter==jsonFilter) ".prefab.json" else ".sleprefab") else it }
        if(save && file.exists() && file.toPath()!=state.file && JOptionPane.showConfirmDialog(null,"Replace ${file.name}?","Replace file",JOptionPane.YES_NO_OPTION)!=JOptionPane.YES_OPTION) return false
        if(save) state.save(file.toPath()) else state.open(file.toPath())
        true
    } catch(failure: Exception) { state.error=failure.message ?: "File operation failed";false }
}
