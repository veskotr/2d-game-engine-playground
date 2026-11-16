package sle.editor.ui

import sle.editor.model.SceneWorkspaceState
import sle.editor.ui.controls.*
import sle.editor.ui.inspector.InspectorTextInput
import sle.editor.ui.theme.LocalEditorMetrics

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
    EditorDialogs.showConfirmDialog(null,"Discard unsaved scene changes?","Unsaved scene",JOptionPane.YES_NO_OPTION)==JOptionPane.YES_OPTION

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SceneWorkspace(state: SceneWorkspaceState, onEditEntity: (String)->Unit = {},
    onEntityBounds: (String,Rect)->Unit = {_,_->},editable: Boolean=true) {
    Column(Modifier.fillMaxSize().editorContextMenu { sceneCreationMenu(state,editable,onEditEntity) }) {
        state.error?.let { Text(it,Modifier.padding(horizontal=LocalEditorMetrics.current.panelPadding,vertical=LocalEditorMetrics.current.gap),color=MaterialTheme.colors.error) }
        SceneTree(state,Modifier.weight(1f).fillMaxWidth(),onEntityBounds,editable,onEditEntity)
    }
}

@Composable
internal fun SceneEntityInspector(state: SceneWorkspaceState,editable: Boolean=true) {
    val selected=state.selected
    fun attempt(action: ()->Unit) { try { action() } catch(failure: Exception) { state.error=failure.message ?: "Scene operation failed" } }
    val m=LocalEditorMetrics.current
    val fields=Modifier.padding(horizontal=m.panelPadding,vertical=m.gap)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        if(selected==null) {
            EditorPanelHeader("Scene")
            Column(fields) {
                key(state) { EditorPropertyRow("Name",help="Scene name") {
                    InspectorTextInput(state.name,"Scene name",{state.rename(it)},readOnly=!editable)
                } }
            }
        } else {
            EditorPanelHeader("Entity")
            Column(fields,verticalArrangement=Arrangement.spacedBy(m.gap/2)) {
                key(selected,selected.document.key) { EditorPropertyRow("Name",help="Entity name") {
                    InspectorTextInput(selected.document.name,"Entity name",{selected.rename(it)},readOnly=!editable)
                } }
                val parentChoices=listOf("Scene root")+state.entities.filter { it!==selected }.map { "${it.document.name} (${it.document.key})" }
                EditorPropertyRow("Parent",help="Parent entity") {
                    EditorSelect("Parent",state.entities.firstOrNull { it.document.key==state.parents[selected.document.key] }?.let { "${it.document.name} (${it.document.key})" } ?: "Scene root",
                        parentChoices,{ choice -> attempt {
                            val index=parentChoices.indexOf(choice)
                            state.reparent(selected.document.key,if(index==0) null else state.entities.filter { it!==selected }[index-1].document.key)
                        } },Modifier.weight(1f),enabled=editable,showLabel=false)
                }
                if(selected.fieldErrors.isNotEmpty()) Text("Finish the highlighted fields before saving.",color=MaterialTheme.colors.error)
            }
            Column(verticalArrangement=Arrangement.spacedBy(m.borderWidth)) {
                for(component in listOf("Transform","Camera2D")) {
                    InspectorComponent(selected,component,readOnly=!editable,actions={
                        if(component=="Camera2D") EditorButton("Make Active",{attempt { state.setActiveCamera(selected.document.key) }},enabled=editable,style=EditorButtonStyle.Ghost)
                    })
                }
            }
        }
    }
}
