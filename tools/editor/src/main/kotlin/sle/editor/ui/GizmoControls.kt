package sle.editor.ui

import sle.editor.ui.controls.*
import sle.editor.ui.theme.*
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
    val targets = GizmoShape.available(state.document)
    LaunchedEffect(targets,entityOnly) { if (!entityOnly && state.selectedTarget !in targets) state.selectedTarget = "Entity" }
    fun display(target: String)=if(target=="Entity") "Transform" else target
    Row(horizontalArrangement=Arrangement.spacedBy(LocalEditorMetrics.current.gap/2),verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
        if(!entityOnly) EditorSelect("Tool",display(state.selectedTarget),targets.map(::display),{ choice ->
            targets.firstOrNull { display(it)==choice }?.let { target ->
                state.endGesture();state.selectedTarget=target
                state.selectedComponent=if(target=="Entity") "Transform" else target
            }
        },enabled=targets.isNotEmpty())
        // The active document's timeline: the scene for scene entities, else the prefab workspace.
        val timeline=state.timeline;val history=timeline.history
        EditorButton("Undo",{timeline.undo()},enabled=history.canUndo || timeline.gestureActive,
            help=history.undoLabel?.let { "Undo $it" },style=EditorButtonStyle.Ghost)
        EditorButton("Redo",{timeline.redo()},enabled=history.canRedo || timeline.gestureActive,
            help=history.redoLabel?.let { "Redo $it" },style=EditorButtonStyle.Ghost)
        EditorCheckBox("Snap 0.1",state.snap,{state.snap=it})
    }
}
