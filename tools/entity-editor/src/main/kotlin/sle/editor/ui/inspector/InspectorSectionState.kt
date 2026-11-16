package sle.editor.ui.inspector

import androidx.compose.runtime.mutableStateMapOf

/** Owned by the document workspace, never a mounted panel or serialized source. */
internal class InspectorSectionState {
    private val collapsed=mutableStateMapOf<Pair<String,String>,Boolean>()
    fun expanded(entity: String, component: String) = collapsed[entity to component]!=true
    fun setExpanded(entity: String, component: String, expanded: Boolean) {
        if(expanded) collapsed.remove(entity to component) else collapsed[entity to component]=true
    }
}
