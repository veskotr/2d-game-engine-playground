package sle.editor.project

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import sle.editor.ui.controls.EditorButton
import sle.editor.ui.theme.LocalEditorMetrics
import java.util.UUID

@Composable
internal fun TagSettings(tags: Map<String, String>, onChange: (Map<String, String>) -> Unit, enabled: Boolean) {
    var draft by remember { mutableStateOf("") }
    val spacing = Arrangement.spacedBy(LocalEditorMetrics.current.gap)
    for ((id, name) in tags) {
        key(id) {
            Row(horizontalArrangement = spacing, verticalAlignment = Alignment.Bottom) {
                Box(Modifier.weight(1f)) {
                    SettingsField("Tag name", name, { onChange(tags + (id to it)) }, enabled)
                }
                EditorButton("Remove", { onChange(tags - id) }, enabled = enabled, help = "Remove tag $name")
            }
        }
    }
    Row(horizontalArrangement = spacing, verticalAlignment = Alignment.Bottom) {
        Box(Modifier.weight(1f)) {
            SettingsField("New tag name", draft, { draft = it }, enabled)
        }
        EditorButton("Add tag", {
            onChange(tags + (UUID.randomUUID().toString() to draft.trim()))
            draft = ""
        }, enabled = enabled && draft.isNotBlank() && draft.trim() !in tags.values)
    }
}
