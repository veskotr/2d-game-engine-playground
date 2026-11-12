package sle.editor.ui

import sle.editor.model.EntityWorkspaceState
import sle.editor.viewport.GizmoShape

import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun GizmoControls(state: EntityWorkspaceState, entityOnly: Boolean = false) {
    var expanded by remember { mutableStateOf(false) }
    val targets = GizmoShape.available(state.document)
    LaunchedEffect(targets,entityOnly) { if (!entityOnly && state.selectedTarget !in targets) state.selectedTarget = "Entity" }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if(entityOnly) Text("Move entity",Modifier.padding(top=12.dp)) else Box {
                OutlinedButton(onClick = { expanded = true }, enabled = targets.isNotEmpty()) { Text("Tool: ${if(state.selectedTarget=="Entity") "Transform" else state.selectedTarget}") }
                DropdownMenu(expanded, onDismissRequest = { expanded = false }) {
                    targets.forEach { target -> DropdownMenuItem(onClick = {
                        state.endGesture(); state.selectedTarget = target
                        state.selectedComponent=if(target=="Entity") "Transform" else target
                        expanded = false
                    }) { Text(if(target=="Entity") "Transform" else target) } }
                }
            }
            OutlinedButton(onClick = { state.undo() }, enabled = state.canUndo) { Text("Undo") }
            OutlinedButton(onClick = { state.redo() }, enabled = state.canRedo) { Text("Redo") }
            Checkbox(state.snap, onCheckedChange = { state.snap = it })
            Text("Snap 0.1", Modifier.padding(top = 12.dp))
        }
    }
}
