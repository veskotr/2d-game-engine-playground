package sle.editor.ui

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.serialization.json.*
import sle.editor.model.ComponentDefinition
import sle.editor.model.KeepPlayPreview
import sle.editor.scripts.LocalOpenScriptAttachment
import sle.editor.ui.controls.EditorButton
import sle.editor.ui.inspector.EditorPropertyDefinition
import sle.editor.ui.inspector.inspectorProperties
import sle.editor.ui.inspector.propertyDefinition
import sle.editor.viewport.LiveInspection
import sle.editor.viewport.LiveSelection

@Composable
internal fun LiveHierarchy(snapshot: LiveInspection?, select: (LiveSelection) -> Unit) {
    Column(Modifier.fillMaxSize().padding(8.dp)) {
        Text("Live Hierarchy")
        if (snapshot == null) return@Column
        val depth = remember(snapshot.entities) {
            buildMap<Long, Int> {
                for (entity in snapshot.entities) put(entity.id, (get(entity.parent) ?: -1) + 1)
            }
        }
        key(snapshot.run, snapshot.scene) {
            val scroll = rememberLazyListState()
            Box(Modifier.fillMaxSize()) {
                LazyColumn(state = scroll, modifier = Modifier.fillMaxSize().padding(end = 8.dp)) {
                    items(snapshot.entities, key = { it.id }) { entity ->
                        val selection = snapshot.selectionFor(entity)
                        EditorButton(entity.label, { select(selection) },
                            Modifier.padding(start = (depth.getValue(entity.id).coerceAtMost(12) * 12).dp),
                            selected = snapshot.selection == selection)
                    }
                }
                VerticalScrollbar(rememberScrollbarAdapter(scroll), Modifier.align(Alignment.CenterEnd).fillMaxHeight())
            }
        }
    }
}

@Composable
internal fun LiveDetails(
    snapshot: LiveInspection?,
    catalog: Map<String, ComponentDefinition>,
    keepPreview: KeepPlayPreview?,
    reloadScript: (LiveSelection, String, String) -> Unit,
    keepChanges: (KeepPlayPreview) -> Unit,
) {
    val selection = snapshot?.selection
    val openScript = LocalOpenScriptAttachment.current
    Column(Modifier.fillMaxSize().padding(8.dp)) {
        Text("Live Details")
        if (selection == null) {
            Text("Select an entity in Live Hierarchy.")
            return@Column
        }
        val selected = snapshot.entities.first { it.id == selection.entity }
        Text("${selected.label}  ·  ID ${selected.id}")
        val details = snapshot.details ?: return@Column
        key(selection) {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (details.isEmpty()) Text("No supported components.")
                for ((component, fields) in details) {
                    Text(if (component == "Transform") "Transform (local)" else component,
                        modifier = Modifier.padding(top = 8.dp))
                    when (fields) {
                        is JsonObject -> inspectorProperties(component, fields, catalog[component]).forEach {
                            LiveProperty(it, catalog[component])
                        }
                        is JsonArray -> fields.forEach {
                            val attachment = it.jsonObject
                            val key = attachment.getValue("key").jsonPrimitive.content
                            val name = key.ifEmpty { "Primary" }
                            val asset = attachment.getValue("scriptAsset").jsonPrimitive.content
                            Text("$name · ${attachment.getValue("status").jsonPrimitive.content}")
                            Text(asset)
                            Text("Order ${attachment.getValue("order").jsonPrimitive.content}")
                            if (openScript != null && asset.isNotBlank()) {
                                EditorButton("Open in IDE", { openScript(asset) })
                            }
                            if (attachment["canReload"]?.jsonPrimitive?.booleanOrNull == true) {
                                EditorButton("Reload Script", { reloadScript(selection, key, asset) })
                            }
                        }
                        else -> Unit
                    }
                    if (component == "Transform" && keepPreview != null) {
                        var error by remember(keepPreview) { mutableStateOf<String?>(null) }
                        for (field in keepPreview.fields) {
                            Text("${field.label}  ${field.authored.content} → ${field.runtime.content}")
                        }
                        EditorButton("Keep Changes", {
                            error = runCatching { keepChanges(keepPreview) }.exceptionOrNull()?.message
                        })
                        error?.let { Text(it) }
                    }
                }
            }
        }
    }
}

@Composable
private fun LiveProperty(property: EditorPropertyDefinition, catalog: ComponentDefinition?) {
    val value = property.value
    when {
        property.axes.isNotEmpty() -> {
            val values = property.axes.zip(property.axisLabels).joinToString("  ") { (axis, label) ->
                "$label ${axis.value.jsonPrimitive.content}"
            }
            Text("${property.displayLabel}  $values")
        }
        value is JsonObject -> {
            Text(property.displayLabel)
            Column(Modifier.padding(start = 8.dp)) {
                for ((field, child) in value) LiveProperty(
                    propertyDefinition(property.component, property.path + field, child, catalog = catalog), catalog)
            }
        }
        value is JsonPrimitive -> Text("${property.displayLabel}  ${value.content}")
    }
}
