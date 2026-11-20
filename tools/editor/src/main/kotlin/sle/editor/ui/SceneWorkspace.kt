package sle.editor.ui

import sle.editor.model.SceneWorkspaceState
import sle.editor.ui.controls.*
import sle.editor.ui.inspector.InspectorTextInput
import sle.editor.ui.theme.LocalEditorColors
import sle.editor.ui.theme.LocalEditorMetrics
import sle.editor.ui.theme.LocalEditorTypography

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.unit.dp
import javax.swing.JOptionPane

internal fun confirmDiscardScene(state: SceneWorkspaceState?): Boolean = state==null || !state.dirty ||
    EditorDialogs.showConfirmDialog(null,"Discard unsaved scene changes?","Unsaved scene",JOptionPane.YES_NO_OPTION)==JOptionPane.YES_OPTION

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SceneWorkspace(state: SceneWorkspaceState, onEditEntity: (String)->Unit = {},
    onEntityBounds: (String,Rect)->Unit = {_,_->},editable: Boolean=true) {
    fun attempt(action: ()->Unit) { try { state.selected?.endGesture();action() } catch(failure: Exception) { state.error=failure.message ?: "Scene operation failed" } }
    Column(Modifier.fillMaxSize().editorContextMenu { sceneCreationMenu(state,editable,onEditEntity) }) {
        EditorToolbar {
            EditorButton("+ Entity",{attempt { state.addEntity() }},enabled=editable,help="Add an empty entity at the scene root")
            EditorButton("+ Child",{attempt { state.addChild() }},enabled=editable && state.selected!=null,help="Add an entity under the selection",style=EditorButtonStyle.Ghost)
            EditorButton("Duplicate",{attempt { state.duplicateSelected() }},enabled=editable && state.selected!=null,help="Duplicate the selection (Ctrl+D)",style=EditorButtonStyle.Ghost)
            EditorButton("Delete",{attempt { deleteSceneSelection(state,editable) }},enabled=editable && state.selected!=null,help="Delete the selection and its children (Delete)",style=EditorButtonStyle.Ghost)
        }
        EditorSeparator()
        // Scene results and failures are reported in the status bar (EditorNotices).
        SceneTree(state,Modifier.weight(1f).fillMaxWidth(),onEntityBounds,editable,onEditEntity)
    }
}

@Composable
internal fun SceneEntityInspector(state: SceneWorkspaceState,editable: Boolean=true,tags: Map<String, String> = emptyMap(),onNameBounds: (Rect)->Unit={}) {
    val selected=state.selected
    fun attempt(action: ()->Unit) { try { action() } catch(failure: Exception) { state.error=failure.message ?: "Scene operation failed" } }
    val m=LocalEditorMetrics.current
    val fields=Modifier.padding(horizontal=m.panelPadding,vertical=m.gap)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        if(state.selection.size>1) {
            // Common actions only; editing several entities at once is roadmap item 1.6.
            EditorPanelHeader("${state.selection.size} entities selected") {
                EditorButton("Duplicate",{attempt { selected?.endGesture();state.duplicateSelected() }},enabled=editable,help="Duplicate the selected entities (Ctrl+D)",style=EditorButtonStyle.Ghost)
                EditorButton("Delete",{attempt { deleteSceneSelection(state,editable) }},enabled=editable,help="Delete the selected entities and their children (Delete)",style=EditorButtonStyle.Ghost)
            }
            Column(fields,verticalArrangement=Arrangement.spacedBy(m.gap/2)) {
                // Reparenting is a single-entity action: shown disabled with the reason.
                val common=state.selection.keys.map { state.parents[it] }.distinct().singleOrNull()
                val parent=if(state.selection.keys.map { state.parents[it] }.distinct().size>1) "Mixed"
                    else common?.let { key -> state.entity(key)?.let { "${it.document.name} (${it.document.key})" } } ?: "Scene root"
                EditorPropertyRow("Parent",help=SINGLE_ENTITY_REASON) {
                    EditorSelect("Parent",parent,listOf(parent),{},Modifier.weight(1f),enabled=false,help=SINGLE_ENTITY_REASON,showLabel=false)
                }
                Text(state.selectedEntities.joinToString(", ") { it.document.name.ifBlank { "(Unnamed entity)" } },
                    color=LocalEditorColors.current.textSecondary,style=LocalEditorTypography.current.secondary)
                Text("Select a single entity to edit its components.",color=LocalEditorColors.current.textSecondary,style=LocalEditorTypography.current.secondary)
            }
        } else if(selected==null) {
            EditorPanelHeader("Scene")
            Column(fields) {
                key(state) { EditorPropertyRow("Name",help="Scene name") {
                    InspectorTextInput(state.name,"Scene name",{state.rename(it)},readOnly=!editable)
                } }
            }
        } else {
            EditorPanelHeader("Entity") {
                EditorButton("Delete",{attempt { deleteSceneSelection(state,editable) }},enabled=editable,help="Delete this entity and its children",style=EditorButtonStyle.Ghost)
            }
            Column(fields,verticalArrangement=Arrangement.spacedBy(m.gap/2)) {
                key(selected,selected.document.key) { EditorPropertyRow("Name",help="Entity name") {
                    InspectorTextInput(selected.document.name,"Entity name",{selected.rename(it)},
                        Modifier.onGloballyPositioned { onNameBounds(it.boundsInWindow()) },readOnly=!editable)
                } }
                val parentChoices=listOf("Scene root")+state.entities.filter { it!==selected }.map { "${it.document.name} (${it.document.key})" }
                EditorPropertyRow("Parent",help="Parent entity") {
                    EditorSelect("Parent",state.entities.firstOrNull { it.document.key==state.parents[selected.document.key] }?.let { "${it.document.name} (${it.document.key})" } ?: "Scene root",
                        parentChoices,{ choice -> attempt {
                            val index=parentChoices.indexOf(choice)
                            state.reparent(selected.document.key,if(index==0) null else state.entities.filter { it!==selected }[index-1].document.key)
                        } },Modifier.weight(1f),enabled=editable,showLabel=false)
                }
                if(selected.fieldErrors.isNotEmpty()) Text("Finish the highlighted fields before saving.",color=MaterialTheme.colors.error)
            }
            Column(verticalArrangement=Arrangement.spacedBy(m.borderWidth)) {
                ComponentSections(selected,readOnly=!editable,scene=true,tags=tags) { component ->
                    if(component=="Camera2D") EditorButton("Make Active",{attempt { state.setActiveCamera(selected.document.key) }},enabled=editable,style=EditorButtonStyle.Ghost)
                }
            }
        }
    }
}
