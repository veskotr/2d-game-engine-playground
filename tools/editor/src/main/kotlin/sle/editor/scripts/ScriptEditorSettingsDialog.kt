package sle.editor.scripts

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Surface
import sle.editor.ui.controls.EditorLabel
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.rememberDialogState
import sle.editor.ui.EditorTheme
import sle.editor.ui.controls.EditorButton
import sle.editor.ui.controls.EditorTextField
import sle.editor.ui.theme.*

@Composable
internal fun ScriptEditorSettingsDialog(
    current: ScriptEditorSettings?,
    onDismiss: () -> Unit,
    onSave: (ScriptEditorSettings) -> Unit,
    onReset: () -> Unit,
    onFailure: (String) -> Unit,
) {
    val defaults = ScriptEditorSettings.defaults("code")
    var executable by remember { mutableStateOf(TextFieldValue(current?.executable.orEmpty())) }
    var fileArguments by remember {
        mutableStateOf(TextFieldValue((current?.fileArguments ?: defaults.fileArguments).joinToString("\n")))
    }
    var lineArguments by remember {
        val arguments = if (current == null) defaults.lineArguments else current.lineArguments
        mutableStateOf(TextFieldValue(arguments.orEmpty().joinToString("\n")))
    }
    val appearance = LocalEditorAppearance.current
    DialogWindow(onCloseRequest = onDismiss, title = "Script Editor",
        state = rememberDialogState(width = 510.dp, height = 380.dp)) {
        EditorTheme(appearance) {
            val metrics = LocalEditorMetrics.current
            Surface(Modifier.fillMaxSize()) {
                Column(Modifier.padding(metrics.panelPadding), verticalArrangement = Arrangement.spacedBy(metrics.gap)) {
                    Column(Modifier.weight(1f).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(metrics.gap)) {
                        EditorLabel("IDE executable")
                        EditorTextField(executable, { executable = it }, "IDE executable", Modifier.fillMaxWidth())
                        EditorLabel("Enter one argument per row. Use {file} for the source path and {line} for the line number.")
                        EditorLabel("File arguments")
                        EditorTextField(fileArguments, { fileArguments = it }, "File arguments",
                            Modifier.fillMaxWidth(), singleLine = false)
                        EditorLabel("Line arguments (optional)")
                        EditorTextField(lineArguments, { lineArguments = it }, "Line arguments",
                            Modifier.fillMaxWidth(), singleLine = false)
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(metrics.gap, Alignment.End)) {
                        EditorButton("Reset to Defaults", onReset)
                        EditorButton("Cancel", onDismiss)
                        EditorButton("Save", {
                            runCatching {
                                onSave(ScriptEditorSettings(executable.text.trim(),
                                    fileArguments.text.lines(),
                                    lineArguments.text.takeUnless { it.isBlank() }?.lines()))
                            }.onFailure { onFailure(it.message ?: "Could not save script editor settings.") }
                        }, primary = true)
                    }
                }
            }
        }
    }
}
