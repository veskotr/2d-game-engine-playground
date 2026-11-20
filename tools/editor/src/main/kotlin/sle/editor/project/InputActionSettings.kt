package sle.editor.project

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import java.awt.event.KeyEvent
import sle.editor.ui.controls.EditorButton
import sle.editor.ui.theme.LocalEditorMetrics
import sle.editor.viewport.engineKey
import sle.editor.viewport.engineKeyName

@Composable
internal fun InputActionSettings(
    actions: Map<String, List<KeyboardBinding>>,
    onChange: (Map<String, List<KeyboardBinding>>) -> Unit,
    enabled: Boolean,
) {
    val gap = LocalEditorMetrics.current.gap
    var draft by remember { mutableStateOf("") }
    var capturing by remember { mutableStateOf<String?>(null) }
    for ((name, bindings) in actions) key(name) {
        Column(verticalArrangement = Arrangement.spacedBy(gap)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gap),
                verticalAlignment = Alignment.CenterVertically) {
                Text(name, Modifier.weight(1f))
                EditorButton("Add key", { capturing = name }, enabled = enabled && capturing != name)
                EditorButton("Remove action", {
                    if (capturing == name) capturing = null
                    onChange(actions - name)
                }, enabled = enabled, help = "Remove action $name")
            }
            for (binding in bindings) {
                Row(horizontalArrangement = Arrangement.spacedBy(gap), verticalAlignment = Alignment.CenterVertically) {
                    val label = checkNotNull(engineKeyName(binding.key))
                    Text(label)
                    EditorButton("Remove key", { onChange(actions + (name to (bindings - binding))) },
                        enabled = enabled, help = "Remove $label from $name")
                }
            }
            if (capturing == name && enabled) {
                val focus = remember { FocusRequester() }
                var prompt by remember { mutableStateOf("Press a key") }
                Row(horizontalArrangement = Arrangement.spacedBy(gap), verticalAlignment = Alignment.CenterVertically) {
                    Text(prompt, Modifier.weight(1f).focusRequester(focus).onPreviewKeyEvent { event ->
                        if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent true
                        val code = (event.nativeKeyEvent as? KeyEvent)?.let(::engineKey) ?: -1
                        val binding = KeyboardBinding(code)
                        when {
                            code < 0 -> prompt = "Unsupported key. Try another key."
                            binding in bindings -> prompt = "Key already bound. Try another key."
                            else -> {
                                onChange(actions + (name to (bindings + binding)))
                                capturing = null
                            }
                        }
                        true
                    }.focusable())
                    EditorButton("Cancel capture", { capturing = null })
                }
                LaunchedEffect(focus) { focus.requestFocus() }
            }
        }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gap), verticalAlignment = Alignment.CenterVertically) {
        SettingsField("Action name", draft, { draft = it }, enabled, Modifier.weight(1f))
        val name = draft.trim()
        EditorButton("Add action", {
            onChange(actions + (name to emptyList()))
            draft = ""
        }, enabled = enabled && name.isNotEmpty() && name !in actions)
    }
}
