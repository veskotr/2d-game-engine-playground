package sle.editor.ui

import sle.editor.ui.controls.*
import sle.editor.ui.theme.*
import sle.editor.model.EntityWorkspaceState
import sle.editor.viewport.GizmoShape

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
internal fun EntityOutline(state: EntityWorkspaceState,modifier: Modifier=Modifier, editable: () -> Boolean = { true }) {
    val projectAssets=sle.editor.project.assets.LocalProjectAssets.current
    val components=state.document.components.keys.toList()
    LaunchedEffect(components) { if(state.selectedComponent !in components) state.selectedComponent=null }
    Column(modifier.editorContextMenu { entityCreationMenu(state,projectAssets=projectAssets,editable=editable) }) {
        EditorPanelHeader(state.document.name.ifBlank { "(Unnamed entity)" })
        EditorTreeRow("All components",state.selectedComponent==null,{state.selectedComponent=null})
        EditorSeparator()
        LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
            items(components,key={it}) { component ->
                EditorTreeRow(component,state.selectedComponent==component,{
                    state.selectedComponent=component
                    state.selectedTarget=if(component=="Transform" || component !in GizmoShape.targets) "Entity" else component
                },help=component,leading={TreeIcon(component)})
            }
        }
    }
}
