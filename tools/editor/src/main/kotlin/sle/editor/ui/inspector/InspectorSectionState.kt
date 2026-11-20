package sle.editor.ui.inspector

import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import sle.editor.model.EntityWorkspaceState
import sle.editor.ui.controls.EditorInspectorSection

/** Owned by the document workspace, never a mounted panel or serialized source. */
internal class InspectorSectionState {
    private val collapsed=mutableStateMapOf<Triple<String,String,String?>,Boolean>()
    fun expanded(entity: String, component: String, group: String? = null) = collapsed[Triple(entity, component, group)]!=true
    fun setExpanded(entity: String, component: String, expanded: Boolean, group: String? = null) {
        if(expanded) collapsed.remove(Triple(entity, component, group)) else collapsed[Triple(entity, component, group)]=true
    }
}

@Composable
internal fun InspectorGroups(
    state: EntityWorkspaceState,
    component: String,
    blocks: List<InspectorBlock>,
    row: @Composable (EditorPropertyDefinition) -> Unit,
) {
    val entity = state.document.key
    val catalog = state.catalog[component]
    for (block in blocks) key(block.group) {
        val rows: @Composable () -> Unit = {
            for (property in block.properties) key(property.path) { row(property) }
        }
        val group = block.group
        if (group == null) rows()
        else EditorInspectorSection(
            catalog?.groups?.get(group)?.label ?: humanizePropertyLabel(group),
            state.inspectorSections.expanded(entity, component, group),
            { expanded ->
                state.endGesture()
                state.inspectorSections.setExpanded(entity, component, expanded, group)
            },
        ) { rows() }
    }
}
