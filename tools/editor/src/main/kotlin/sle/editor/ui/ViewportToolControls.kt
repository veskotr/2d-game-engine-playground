package sle.editor.ui

import sle.editor.command.ToolCommands
import sle.editor.command.standardKeymap
import sle.editor.ui.controls.*
import sle.editor.ui.theme.*
import sle.editor.viewport.SnapSettings
import sle.editor.viewport.SnapService
import sle.editor.viewport.tools.ViewportToolId
import sle.editor.viewport.tools.ViewportToolState
import sle.editor.viewport.tools.formatStep

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.rememberDialogState

private fun ViewportToolId.glyph() = when(this) { ViewportToolId.Select -> EditorGlyph.ToolSelect; ViewportToolId.Move -> EditorGlyph.ToolMove }

/**
 * Viewport toolbar group: the Scene tools (Q/W) when [tools] is given, the snap toggle and
 * Configure Snap. Tooltips name each command's chord from the keymap.
 */
@Composable
internal fun ViewportToolControls(tools: ViewportToolState?, snap: SnapService, onConfigure: () -> Unit,
    onToolBounds: (ViewportToolId, androidx.compose.ui.geometry.Rect) -> Unit = { _, _ -> }) {
    val m = LocalEditorMetrics.current
    Row(horizontalArrangement = Arrangement.spacedBy(m.borderWidth), verticalAlignment = Alignment.CenterVertically) {
        if(tools != null) {
            ViewportToolId.entries.forEach { id ->
                EditorIconToggle(id.label, id.glyph(), tools.active == id, { tools.activate(id) },
                    Modifier.onGloballyPositioned { onToolBounds(id, it.boundsInWindow()) },
                    help = standardKeymap.hint(ToolCommands.command(id).id, "${id.label} tool"))
            }
            EditorToolbarDivider()
        }
        val settings = snap.settings
        EditorIconToggle("Snapping", EditorGlyph.Snap, settings.enabled, { snap.toggle() },
            help = (if(settings.enabled) "Snapping on" else "Snapping off") + ": move step ${formatStep(settings.move)}; hold Ctrl to invert while dragging")
        EditorGlyphButton("Configure Snap…", onConfigure) { tint -> EditorGlyphIcon(EditorGlyph.ChevronDown, tint) }
    }
}

/** Configure Snap…: the move, rotate and scale steps and the toggle, saved per user and project. */
@Composable
internal fun SnapSettingsDialog(current: SnapSettings, onDismiss: () -> Unit, onSave: (SnapSettings) -> Unit) {
    var enabled by remember(current) { mutableStateOf(current.enabled) }
    var move by remember(current) { mutableStateOf(formatStep(current.move)) }
    var rotate by remember(current) { mutableStateOf(formatStep(current.rotateDegrees)) }
    var scale by remember(current) { mutableStateOf(formatStep(current.scale)) }
    var formError by remember { mutableStateOf<String?>(null) }
    val appearance = LocalEditorAppearance.current
    DialogWindow(onCloseRequest = onDismiss, title = "Configure Snap", resizable = true, state = rememberDialogState(width = 400.dp, height = 330.dp)) {
        EditorTheme(appearance) {
            val metrics = LocalEditorMetrics.current
            Surface(Modifier.fillMaxSize()) {
                Column(Modifier.padding(metrics.panelPadding), verticalArrangement = Arrangement.spacedBy(metrics.gap)) {
                    Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(metrics.gap)) {
                        EditorCheckBox("Snap while dragging and nudging", enabled, { enabled = it })
                        StepField("Move step", move, { move = it }, "World units; the Scene grid follows this step")
                        StepField("Rotate step (°)", rotate, { rotate = it }, "Degrees, for the Rotate tool")
                        StepField("Scale step", scale, { scale = it }, "For the Scale tool")
                        Text("Hold Ctrl during a drag or nudge to invert snapping for that gesture.", style = LocalEditorTypography.current.secondary,
                            color = LocalEditorColors.current.textSecondary)
                        formError?.let { Text(it, color = LocalEditorColors.current.error) }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(metrics.gap, Alignment.End)) {
                        EditorButton("Cancel", onDismiss)
                        EditorButton("Save", {
                            try {
                                fun number(text: String, label: String) = text.trim().toDoubleOrNull() ?: throw IllegalArgumentException("$label must be a number")
                                onSave(SnapSettings(enabled, number(move, "Move step"), number(rotate, "Rotate step"), number(scale, "Scale step")))
                            } catch(failure: IllegalArgumentException) { formError = failure.message ?: "Enter valid steps" }
                        }, primary = true)
                    }
                }
            }
        }
    }
}

@Composable
private fun StepField(label: String, value: String, change: (String) -> Unit, help: String) {
    var field by remember { mutableStateOf(TextFieldValue(value)) }
    if(field.text != value) field = field.copy(text = value)
    EditorPropertyRow(label, help = help) {
        EditorTextField(field, { field = it;change(it.text) }, label, Modifier.weight(1f), numeric = true, help = help)
    }
}
