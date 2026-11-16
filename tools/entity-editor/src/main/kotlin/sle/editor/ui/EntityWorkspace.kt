package sle.editor.ui

import sle.editor.project.assets.LocalProjectAssets
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
    EditorDialogs.showConfirmDialog(null, "Discard unsaved entity changes?", "Unsaved entity", JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION

@Composable
internal fun EntityWorkspace(state: EntityWorkspaceState, embedded: Boolean = false) {
    val projectAssets=LocalProjectAssets.current
    var assetsExpanded by remember { mutableStateOf(false) }
    val m=sle.editor.ui.theme.LocalEditorMetrics.current
    Column(Modifier.fillMaxSize().editorContextMenu { entityCreationMenu(state,embedded,projectAssets) }) {
        sle.editor.ui.controls.EditorPanelHeader("Prefab")
        Column(Modifier.padding(horizontal=m.panelPadding,vertical=m.gap),verticalArrangement=Arrangement.spacedBy(m.gap/2)) {
            state.error?.let { Text(it, color = MaterialTheme.colors.error) }
            key(state,state.document.key) {
                sle.editor.ui.controls.EditorPropertyRow("Name",help="Entity name") {
                    sle.editor.ui.inspector.InspectorTextInput(state.document.name,"Name",{state.rename(it)})
                }
            }
            val all="All components"
            sle.editor.ui.controls.EditorPropertyRow("Show",help="Filter the components shown below") {
                sle.editor.ui.controls.EditorSelect("Show",state.selectedComponent ?: all,listOf(all)+state.document.components.keys,{ component ->
                    state.endGesture()
                    if(component==all) state.selectedComponent=null else {
                        state.selectedComponent=component
                        state.selectedTarget=if(component in sle.editor.viewport.GizmoShape.targets) component else "Entity"
                    }
                },Modifier.weight(1f),showLabel=false)
            }
            if (state.fieldErrors.isNotEmpty()) Text("Finish the highlighted fields before saving.", color = MaterialTheme.colors.error)
            sle.editor.ui.controls.EditorToggle("Assets (${state.assets.size})",assetsExpanded,{ assetsExpanded = it },
                style=sle.editor.ui.controls.EditorButtonStyle.Ghost)
            if (state.assets.isNotEmpty() && (!embedded || assetsExpanded)) {
                Column(Modifier.fillMaxWidth().heightIn(max = m.compactListMaxHeight).verticalScroll(rememberScrollState())) {
                    state.assets.forEach { (path, name) ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            Text(name, Modifier.weight(1f), style = MaterialTheme.typography.caption)
                            sle.editor.ui.controls.EditorButton("Unlist",{ state.removeAsset(path) },style=sle.editor.ui.controls.EditorButtonStyle.Ghost)
                        }
                    }
                }
            }
        }
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(m.borderWidth)) {
            if (state.document.components.isEmpty()) sle.editor.ui.controls.EditorEmptyState("No components","Add components to define this entity.",Modifier.heightIn(min=m.emptyStateMinHeight))
            state.document.components.filterKeys { state.selectedComponent==null || it==state.selectedComponent }.forEach { (component, _) ->
                key(component) {
                    InspectorComponent(state,component,actions={
                        sle.editor.ui.controls.EditorButton("Remove",{state.removeComponent(component)},style=sle.editor.ui.controls.EditorButtonStyle.Ghost)
                    })
                }
            }
        }
    }
}

@Composable
internal fun ComponentField(state: EntityWorkspaceState, component: String, path: List<String>, value: JsonElement, choices: JsonArray? = null, readOnly: Boolean = false) {
    val projectAssets=LocalProjectAssets.current
    sle.editor.ui.inspector.EditorPropertyRenderer(state,
        sle.editor.ui.inspector.propertyDefinition(component,path,value,choices),readOnly,
        browseEnabled=projectAssets?.busy!=true,onBrowse={ display ->
            try {
                if(projectAssets!=null) projectAssets.browse(display,component=="SpriteRenderer" && path.single()=="texture",projectAssetSelection(state,component,path))
                else chooseTexture(display,component=="SpriteRenderer" && path.single()=="texture")?.let { selected ->
                    state.setValue(component,path,JsonPrimitive(state.importAsset(selected).toString().replace('\\','/')))
                }
            } catch(failure: Exception) { state.error=failure.message ?: "Could not select asset" }
        })
}

@Composable
internal fun InspectorComponent(state: EntityWorkspaceState, component: String, readOnly: Boolean = false,
    actions: @Composable RowScope.() -> Unit = {}) {
    val fields=state.document.components[component] as? JsonObject ?: return
    val identity=state.document.key
    sle.editor.ui.controls.EditorInspectorSection(component,state.inspectorSections.expanded(identity,component),
        { expanded -> state.endGesture();state.inspectorSections.setExpanded(identity,component,expanded) },actions=actions) {
        fields.forEach { (field,value) ->
            ComponentField(state,component,listOf(field),value,state.catalog[component]?.enums?.get(field) as? JsonArray,readOnly)
        }
    }
}

internal fun projectAssetSelection(state: EntityWorkspaceState,component: String,path: List<String>): (java.nio.file.Path)->Unit {
    val originalContext=state.editContextVersion
    val originalDocument=state.document
    val originalDrafts=state.drafts
    val original=state.document.components[component]?.jsonObject?.get(path.single())
    val originalKey=state.document.key
    val id=component+"/"+path.joinToString("/")
    val originalDraft=state.drafts[id]
    return { selected ->
        if(state.editContextVersion==originalContext && state.document === originalDocument && state.drafts === originalDrafts && state.document.key==originalKey && state.document.components[component]?.jsonObject?.get(path.single())==original && state.drafts[id]==originalDraft)
            state.setValue(component,path,JsonPrimitive(selected.toString().replace('\\','/')))
    }
}
