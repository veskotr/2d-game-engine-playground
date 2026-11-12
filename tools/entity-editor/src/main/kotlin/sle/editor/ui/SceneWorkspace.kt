package sle.editor.ui

import sle.editor.model.SceneWorkspaceState

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import javax.swing.JOptionPane

internal fun confirmDiscardScene(state: SceneWorkspaceState?): Boolean = state==null || !state.dirty ||
    JOptionPane.showConfirmDialog(null,"Discard unsaved scene changes?","Unsaved scene",JOptionPane.YES_NO_OPTION)==JOptionPane.YES_OPTION

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SceneWorkspace(state: SceneWorkspaceState, onEditEntity: (String)->Unit = {},
    onEntityBounds: (String,Rect)->Unit = {_,_->},editable: Boolean=true) {
    Column(Modifier.fillMaxSize().editorContextMenu { sceneCreationMenu(state,editable,onEditEntity) },verticalArrangement=Arrangement.spacedBy(6.dp)) {
        state.error?.let { Text(it,color=MaterialTheme.colors.error) }
        SceneTree(state,Modifier.weight(1f).fillMaxWidth(),onEntityBounds,editable,onEditEntity)
    }
}

@Composable
internal fun SceneEntityInspector(state: SceneWorkspaceState,editable: Boolean=true) {
    val selected=state.selected
    fun attempt(action: ()->Unit) { try { action() } catch(failure: Exception) { state.error=failure.message ?: "Scene operation failed" } }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(6.dp)) {
        if(selected==null) {
            Text("Scene properties",style=MaterialTheme.typography.subtitle1)
            OutlinedTextField(state.name,{state.rename(it)},enabled=editable,label={Text("Scene name")},singleLine=true,modifier=Modifier.fillMaxWidth())
        } else {
            Text("Entity properties",style=MaterialTheme.typography.subtitle1)
            OutlinedTextField(selected.document.name,{selected.rename(it)},enabled=editable,label={Text("Entity name")},singleLine=true,modifier=Modifier.fillMaxWidth())
            var parentsOpen by androidx.compose.runtime.remember(selected.document.key) { androidx.compose.runtime.mutableStateOf(false) }
            Box {
                OutlinedButton(onClick={parentsOpen=true},enabled=editable) { Text("Parent: "+(state.entities.firstOrNull { it.document.key==state.parents[selected.document.key] }?.document?.name ?: "Scene root")) }
                DropdownMenu(expanded=parentsOpen && editable,onDismissRequest={parentsOpen=false}) {
                    DropdownMenuItem(onClick={parentsOpen=false;attempt { state.reparent(selected.document.key,null) }}) { Text("Scene root") }
                    state.entities.filter { it.document.key!=selected.document.key }.forEach { candidate ->
                        DropdownMenuItem(onClick={parentsOpen=false;attempt { state.reparent(selected.document.key,candidate.document.key) }}) { Text(candidate.document.name) }
                    }
                }
            }
            for(component in listOf("Transform","Camera2D")) {
                val fields=selected.document.components[component] as? kotlinx.serialization.json.JsonObject ?: continue
                Text(if(component=="Transform") "Local transform" else "Camera",style=MaterialTheme.typography.subtitle2)
                fields.forEach { (field,value) -> if(editable) ComponentField(selected,component,listOf(field),value) else Text("$field: $value",style=MaterialTheme.typography.caption) }
                if(component=="Camera2D") OutlinedButton(onClick={attempt { state.setActiveCamera(selected.document.key) }},enabled=editable) { Text("Make Active") }
            }
        }
    }
}
