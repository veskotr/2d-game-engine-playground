package sle.editor.ui

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
internal fun EntityOutline(state: EntityWorkspaceState,modifier: Modifier=Modifier) {
    val components=state.document.components.keys.toList()
    LaunchedEffect(components) { if(state.selectedComponent !in components) state.selectedComponent=null }
    Column(modifier.editorContextMenu { entityCreationMenu(state) },verticalArrangement=Arrangement.spacedBy(6.dp)) {
        Text("Entity",style=MaterialTheme.typography.subtitle1)
        Text(state.document.name.ifBlank { "(Unnamed entity)" })
        TextButton(onClick={state.selectedComponent=null}) { Text("All components") }
        Divider()
        LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
            items(components,key={it}) { component ->
                TextButton(onClick={
                    state.selectedComponent=component
                    state.selectedTarget=if(component=="Transform" || component !in GizmoShape.targets) "Entity" else component
                },modifier=Modifier.fillMaxWidth().background(if(state.selectedComponent==component) MaterialTheme.colors.primary.copy(alpha=0.15f) else MaterialTheme.colors.surface)) {
                    Row(Modifier.fillMaxWidth(),verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
                        TreeIcon(if(component=="Camera2D") "Camera" else "Component")
                        Spacer(Modifier.width(6.dp))
                        Text(component)
                    }
                }
            }
        }
    }
}
