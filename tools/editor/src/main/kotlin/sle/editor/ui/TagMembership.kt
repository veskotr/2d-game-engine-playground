package sle.editor.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import kotlinx.serialization.json.*
import sle.editor.model.EntityWorkspaceState
import sle.editor.ui.controls.EditorCheckBox
import sle.editor.ui.theme.LocalEditorMetrics

@Composable
internal fun TagMembership(state: EntityWorkspaceState, definitions: Map<String, String>, readOnly: Boolean) {
    val fields = state.document.components.getValue("Tags").jsonObject
    val ids = (fields["ids"] as? JsonArray)
        .orEmpty().map { it.jsonPrimitive.content }
    fun setMembership(id: String, assigned: Boolean) {
        val next = if (assigned) ids + id else ids - id
        val updated = JsonObject(fields + ("ids" to JsonArray(next.map(::JsonPrimitive))))
        val candidate = state.document.copy(components = JsonObject(state.document.components + ("Tags" to updated)))
        state.publishPropertyCandidate(candidate, if (assigned) "Assign tag" else "Remove tag")
    }
    val metrics = LocalEditorMetrics.current
    Column(Modifier.padding(horizontal = metrics.panelPadding, vertical = metrics.gap)) {
        if (definitions.isEmpty() && ids.isEmpty()) Text("Define tags in Project Settings.")
        for ((id, name) in definitions.entries.sortedBy { it.value }) {
            key(id) {
                EditorCheckBox(name, id in ids, { setMembership(id, it) }, enabled = !readOnly)
            }
        }
        for (id in ids.filterNot(definitions::containsKey)) {
            key(id) {
                EditorCheckBox("Unresolved tag ($id)", true, { setMembership(id, false) }, enabled = !readOnly)
            }
        }
    }
}
