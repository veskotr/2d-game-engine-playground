package sle.editor.project

import androidx.compose.foundation.layout.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.runtime.*
import sle.editor.ui.controls.EditorButton
import sle.editor.ui.theme.LocalEditorMetrics

@Composable
internal fun SortingLayerSettings(layers: Map<Int, String>, onChange: (Map<Int, String>) -> Unit, enabled: Boolean) {
    var draft by remember { mutableStateOf("") }
    val newValue = draft.toIntOrNull()
    val spacing = Arrangement.spacedBy(LocalEditorMetrics.current.gap)
    layers.toSortedMap().forEach { (value, label) ->
        key(value) {
            Row(horizontalArrangement = spacing, verticalAlignment = Alignment.Bottom) {
                Box(Modifier.weight(1f)) {
                    SettingsField(value.toString(), label, { onChange(layers + (value to it)) }, enabled)
                }
                EditorButton("Remove", { onChange(layers - value) }, enabled = enabled,
                    help = "Remove the label for layer $value")
            }
        }
    }
    Row(horizontalArrangement = spacing, verticalAlignment = Alignment.Bottom) {
        Box(Modifier.weight(1f)) {
            SettingsField("Layer value", draft, { draft = it }, enabled, numeric = true,
                help = "Enter an unused signed integer")
        }
        EditorButton("Add layer", {
            val value = checkNotNull(newValue)
            onChange(layers + (value to "Layer $value"))
            draft = ""
        }, enabled = enabled && newValue != null && newValue !in layers)
    }
}
