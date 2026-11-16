package sle.editor.ui.inspector

import androidx.compose.foundation.layout.*
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import kotlinx.serialization.json.*
import sle.editor.model.EntityWorkspaceState
import sle.editor.model.documentJson
import sle.editor.ui.controls.*
import sle.editor.ui.theme.LocalEditorColors

/** Editing callbacks stay on the document owner; file operations are supplied by the host. */
@Composable
internal fun EditorPropertyRenderer(state: EntityWorkspaceState, property: EditorPropertyDefinition,
    readOnly: Boolean = false, browseEnabled: Boolean = true, onBrowse: ((String) -> Unit)? = null) {
    val p=property
    val display=state.drafts[p.id] ?: if(p.value is JsonPrimitive && p.value.isString) p.value.content else
        documentJson.encodeToString(JsonElement.serializer(),p.value)
    key(state,state.document.key,p.id) {
        if(p.kind==PropertyKind.Vector) {
            EditorVectorField(p.displayLabel,p.axes.map { it.path.last() to state.numberEdit(p.component,it.path) },readOnly=readOnly)
        } else EditorPropertyRow(p.displayLabel,help="${p.component}.${p.label}") {
            Column(Modifier.weight(1f)) {
                when(p.kind) {
                    PropertyKind.Number -> EditorNumberField(state.numberEdit(p.component,p.path),p.label,readOnly=readOnly)
                    PropertyKind.Boolean -> EditorCheckBox(p.label,p.value.jsonPrimitive.boolean,
                        { state.setValue(p.component,p.path,JsonPrimitive(it)) },enabled=!readOnly,showLabel=false)
                    PropertyKind.Enum -> EditorSelect(p.label,p.value.jsonPrimitive.content,p.choices.map { it.jsonPrimitive.content },
                        { selected -> p.choices.firstOrNull { it.jsonPrimitive.content==selected }?.let { state.setValue(p.component,p.path,it) } },
                        Modifier.fillMaxWidth(),enabled=!readOnly,showLabel=false)
                    PropertyKind.Resource -> {
                        val importedName=state.assets.entries.firstOrNull { it.key.toString().replace('\\','/')==display }?.value
                        InspectorTextInput(importedName ?: display,p.label,{ state.editField(p.component,p.path,p.value,it) },
                            readOnly=readOnly || importedName!=null,invalid=p.id in state.fieldErrors)
                        FlowResourceActions(!readOnly,browseEnabled,onBrowse?.let { { it(display) } },
                            if(display.isNotBlank()) ({state.setValue(p.component,p.path,JsonPrimitive(""))}) else null)
                    }
                    else -> InspectorTextInput(display,p.label,{state.editField(p.component,p.path,p.value,it)},
                        readOnly=readOnly,invalid=p.id in state.fieldErrors,multiline=p.kind==PropertyKind.Json)
                }
                if(p.id in state.fieldErrors) Text("Enter a valid ${if(p.kind==PropertyKind.Json) "JSON value" else "number"}.",
                    color=LocalEditorColors.current.error,style=sle.editor.ui.theme.LocalEditorTypography.current.secondary)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FlowResourceActions(editable: Boolean, browseEnabled: Boolean, browse: (() -> Unit)?, clear: (() -> Unit)?) {
    FlowRow(Modifier.padding(top=sle.editor.ui.theme.LocalEditorMetrics.current.gap/2),horizontalArrangement=Arrangement.spacedBy(sle.editor.ui.theme.LocalEditorMetrics.current.gap)) {
        if(browse!=null) EditorButton("Browse…",browse,enabled=editable && browseEnabled)
        if(clear!=null) EditorButton("Clear",clear,enabled=editable,style=EditorButtonStyle.Ghost)
    }
}

/** Preserve caret locally; drafts and validity remain in the workspace across remounts. */
@Composable
internal fun InspectorTextInput(text: String, label: String, onEdit: (String) -> Unit,
    modifier: Modifier = Modifier, readOnly: Boolean = false, invalid: Boolean = false, multiline: Boolean = false) {
    var field by remember { mutableStateOf(TextFieldValue(text)) }
    if(field.text!=text) field=field.copy(text=text,selection=TextRange(text.length))
    EditorTextField(field,{ field=it;if(!readOnly && it.text!=text) onEdit(it.text) },label,modifier.fillMaxWidth(),
        readOnly=readOnly,invalid=invalid,help=label,singleLine=!multiline)
}
