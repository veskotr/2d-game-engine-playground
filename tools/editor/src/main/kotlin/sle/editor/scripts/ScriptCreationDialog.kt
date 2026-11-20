package sle.editor.scripts

import androidx.compose.foundation.layout.*
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.rememberDialogState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import sle.editor.ui.EditorTheme
import sle.editor.ui.controls.EditorButton
import sle.editor.ui.controls.EditorSelect
import sle.editor.ui.controls.EditorTextField
import sle.editor.ui.theme.LocalEditorAppearance
import sle.editor.ui.theme.LocalEditorMetrics

@Composable
internal fun ScriptCreationDialog(
    request: ScriptCreation,
    busy: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onCreate: (String, ScriptTemplate) -> Unit,
) {
    val templates by produceState<List<ScriptTemplate>>(emptyList(), request) {
        value = withContext(Dispatchers.IO) { request.templates() }
    }
    var filename by remember(request) { mutableStateOf(BuiltInScriptTemplate.Basic.filename) }
    val template = templates.firstOrNull { it.filename == filename }
    var destination by remember { mutableStateOf(TextFieldValue("Scripts/Behavior.lua")) }
    val appearance = LocalEditorAppearance.current
    DialogWindow(onCloseRequest = { if (!busy) onDismiss() }, title = "New Lua Behavior",
        state = rememberDialogState(width = 480.dp, height = 250.dp)) {
        EditorTheme(appearance) {
            val metrics = LocalEditorMetrics.current
            Surface(Modifier.fillMaxSize()) {
                Column(Modifier.padding(metrics.panelPadding), verticalArrangement = Arrangement.spacedBy(metrics.gap)) {
                    EditorSelect("Template", template?.label ?: "Loading templates…", templates.map { it.label },
                        { label -> filename = templates.first { it.label == label }.filename },
                        Modifier.fillMaxWidth(), enabled = !busy && templates.isNotEmpty())
                    Text("Destination relative to the project asset root")
                    EditorTextField(destination, { destination = it }, "Destination",
                        Modifier.fillMaxWidth(), enabled = !busy)
                    error?.let { Text(it, color = androidx.compose.material.MaterialTheme.colors.error) }
                    Spacer(Modifier.weight(1f))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(metrics.gap, Alignment.End)) {
                        EditorButton("Cancel", onDismiss, enabled = !busy)
                        EditorButton("Create and Attach", { template?.let { onCreate(destination.text.trim(), it) } },
                            enabled = !busy && template != null && destination.text.isNotBlank(), primary = true)
                    }
                }
            }
        }
    }
}
