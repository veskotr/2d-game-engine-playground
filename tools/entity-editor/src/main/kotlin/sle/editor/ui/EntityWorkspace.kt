package sle.editor.ui

import sle.editor.files.PrefabPackages
import sle.editor.model.documentJson
import sle.editor.files.chooseTexture
import sle.editor.model.EntityWorkspaceState

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.serialization.json.*
import javax.swing.JOptionPane

internal fun confirmDiscard(state: EntityWorkspaceState?): Boolean = state == null || !state.dirty ||
    JOptionPane.showConfirmDialog(null, "Discard unsaved entity changes?", "Unsaved entity", JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION

@Composable
internal fun EntityWorkspace(state: EntityWorkspaceState, embedded: Boolean = false) {
    var assetsExpanded by remember { mutableStateOf(false) }
    var choosingComponent by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().editorContextMenu { entityCreationMenu(state,embedded) }, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        state.error?.let { Text(it, color = MaterialTheme.colors.error) }
        OutlinedTextField(state.document.name, { state.rename(it) },
            label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Box {
            OutlinedButton(onClick={choosingComponent=true}) { Text(state.selectedComponent ?: "All components") }
            DropdownMenu(choosingComponent,onDismissRequest={choosingComponent=false}) {
                DropdownMenuItem(onClick={state.selectedComponent=null;choosingComponent=false}) { Text("All components") }
                state.document.components.keys.forEach { component ->
                    DropdownMenuItem(onClick={
                        state.selectedComponent=component
                        state.selectedTarget=if(component in sle.editor.viewport.GizmoShape.targets) component else "Entity"
                        choosingComponent=false
                    }) { Text(component) }
                }
            }
        }
        if (state.fieldErrors.isNotEmpty()) Text("Finish the highlighted fields before saving.", color = MaterialTheme.colors.error)
        TextButton(onClick = { assetsExpanded = !assetsExpanded }) { Text("Assets (${state.assets.size})") }
        if (state.assets.isNotEmpty() && (!embedded || assetsExpanded)) {
            Column(Modifier.fillMaxWidth().heightIn(max = 100.dp).verticalScroll(rememberScrollState())) {
                state.assets.forEach { (path, name) ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(name, Modifier.weight(1f), style = MaterialTheme.typography.caption)
                        TextButton(onClick = { state.removeAsset(path) }) { Text("Unlist") }
                    }
                }
            }
        }
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (state.document.components.isEmpty()) Text("Add components to define this entity.", Modifier.padding(top = 16.dp))
            state.document.components.filterKeys { state.selectedComponent==null || it==state.selectedComponent }.forEach { (component, values) ->
                key(component) {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(component, style = MaterialTheme.typography.h6, modifier = Modifier.weight(1f))
                                TextButton(onClick = { state.removeComponent(component) }) { Text("Remove") }
                            }
                            values.jsonObject.forEach { (field, value) ->
                                ComponentField(state, component, listOf(field), value,
                                    state.catalog[component]?.enums?.get(field) as? JsonArray)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun ComponentField(state: EntityWorkspaceState, component: String, path: List<String>, value: JsonElement, choices: JsonArray? = null) {
    val label = path.joinToString(".")
    val id = component + "/" + path.joinToString("/")
    when {
        path.size == 1 && PrefabPackages.isAssetField(path.single()) -> {
            val display = state.drafts[id] ?: value.jsonPrimitive.content
            val importedName = state.assets.entries.firstOrNull { it.key.toString().replace('\\', '/') == display }?.value
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                OutlinedTextField(importedName ?: display,
                    { state.editField(component, path, value, it) },
                    label = { Text(label) }, readOnly = importedName != null, singleLine = true, modifier = Modifier.weight(1f))
                OutlinedButton(onClick = {
                    try {
                        chooseTexture(display, component == "SpriteRenderer" && path.single() == "texture")?.let { selected ->
                            state.setValue(component, path, JsonPrimitive(state.importAsset(selected).toString().replace('\\', '/')))
                        }
                    } catch (failure: Exception) {
                        state.error = failure.message ?: "Could not select asset"
                    }
                }) { Text("Browse…") }
            }
            if (display.isNotBlank()) TextButton(onClick = { state.setValue(component, path, JsonPrimitive("")) }) { Text("Clear asset") }
        }
        choices != null -> {
            var expanded by remember { mutableStateOf(false) }
            Box {
                OutlinedButton(onClick = { expanded = true }) { Text("$label: ${value.jsonPrimitive.content}") }
                DropdownMenu(expanded, onDismissRequest = { expanded = false }) {
                    choices.forEach { choice ->
                        DropdownMenuItem(onClick = { state.setValue(component, path, choice); expanded = false }) { Text(choice.jsonPrimitive.content) }
                    }
                }
            }
        }
        value is JsonPrimitive && value.booleanOrNull != null && !value.isString -> {
            Row {
                Checkbox(value.boolean, { state.setValue(component, path, JsonPrimitive(it)) })
                Text(label, Modifier.padding(top = 12.dp))
            }
        }
        value is JsonObject && value.isNotEmpty() && (value.keys.all { it in setOf("x", "y", "z", "w") } || path.last() == "region") -> {
            Column(Modifier.padding(start = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(label, style = MaterialTheme.typography.subtitle2)
                value.forEach { (field, child) -> ComponentField(state, component, path + field, child) }
            }
        }
        else -> {
            val structured = value is JsonObject || value is JsonArray
            val display = state.drafts[id] ?: if (value is JsonPrimitive && value.isString) value.content else
                documentJson.encodeToString(JsonElement.serializer(), value)
            OutlinedTextField(display, { state.editField(component, path, value, it) },
                label = { Text(label + if (structured) " (JSON)" else "") }, isError = id in state.fieldErrors,
                singleLine = !structured, modifier = Modifier.fillMaxWidth().heightIn(max = 180.dp))
            if (id in state.fieldErrors) Text("Enter a valid ${if (structured) "JSON value" else "number"}.", color = MaterialTheme.colors.error, style = MaterialTheme.typography.caption)
        }
    }
}
