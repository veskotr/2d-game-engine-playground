package sle.editor.ui.inspector

import androidx.compose.foundation.layout.*
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import kotlinx.serialization.json.*
import sle.editor.model.EntityWorkspaceState
import sle.editor.model.documentJson
import sle.editor.scripts.LocalProjectDrawers
import sle.editor.scripts.projectDrawer
import sle.editor.ui.controls.*
import sle.editor.ui.theme.LocalEditorColors

internal val LocalSortingLayers = staticCompositionLocalOf<Map<Int, String>> { emptyMap() }

/** Editing callbacks stay on the document owner; file operations are supplied by the host. */
@Composable
internal fun EditorPropertyRenderer(state: EntityWorkspaceState, property: EditorPropertyDefinition,
    hostReadOnly: Boolean = false, browseEnabled: Boolean = true,
    onBrowse: ((EditorPropertyDefinition, String) -> Unit)? = null,
    actions: (@Composable (EditorPropertyDefinition, Boolean) -> Unit)? = null,
    overrideIndicator: (@Composable () -> Unit)? = null,
    label: String = property.displayLabel,
    onScalarEdit: ((JsonPrimitive) -> Unit)? = null,
    scalarNumberEdit: NumberEdit? = null,
) {
    val extension = if (onScalarEdit == null) projectDrawer(property, LocalProjectDrawers.current) else null
    val p = extension?.let { property.copy(projectDrawer = it) } ?: property
    val drawer = p.drawer
    val readOnly = hostReadOnly || p.readOnly
    val canReplace = !readOnly && !p.hasReadOnlyDescendants
    val inputReadOnly = readOnly || (p.kind != PropertyKind.Vector && !canReplace)
    if (drawer == PropertyDrawer.Structured) {
        key(state, state.document.key, p.id) {
            StructuredPropertyDrawer(state, p, readOnly) { child ->
                EditorPropertyRenderer(state, child, browseEnabled = browseEnabled, onBrowse = onBrowse,
                    actions = actions)
            }
        }
        return
    }
    val display=state.drafts[p.id]?.takeIf { onScalarEdit == null } ?: if(p.value is JsonPrimitive && p.value.isString) p.value.content else
        documentJson.encodeToString(JsonElement.serializer(),p.value)
    key(state,state.document.key,p.id) {
        InspectorPropertyRow(p, inputReadOnly, p.id in state.fieldErrors, label = label,
            labelModifier = if (onScalarEdit == null) detailsPropertyMenu(state, p, !canReplace) else Modifier,
            actions = actions, actionsReadOnly = !canReplace, overrideIndicator = overrideIndicator) { disabled ->
            when {
                p.kind == PropertyKind.Vector -> {
                    VectorPropertyFields(state, p) { axis, caption ->
                        EditorPropertyRenderer(state, axis, disabled, actions = actions, label = caption)
                    }
                    if (p.isColor) ColorPickerRow(state, p, !canReplace)
                }
                drawer == PropertyDrawer.Resource || drawer == PropertyDrawer.Texture ->
                    ResourceDrawer(state, p, display, disabled, browseEnabled,
                        onBrowse?.let { browse -> { browse(p, display) } }, drawer == PropertyDrawer.Texture)
                p.kind == PropertyKind.Integer && p.component == "SpriteRenderer" && p.path == listOf("layer") -> {
                    PropertyNumberField(state, p, disabled)
                    val layers = LocalSortingLayers.current
                    if (layers.isNotEmpty()) {
                        val current = p.value.jsonPrimitive.int
                        val choices = layers.toSortedMap().entries.associate { (value, name) -> "$value · $name" to value }
                        val caption = layers[current]?.let { "$current · $it" } ?: current.toString()
                        EditorSelect("Sorting layer", caption, choices.keys.toList(),
                            { choice -> state.setValue(p.component, p.path, JsonPrimitive(choices.getValue(choice))) },
                            Modifier.fillMaxWidth(), enabled = !disabled, showLabel = false)
                    }
                }
                p.kind == PropertyKind.Integer && p.path.size == 1 &&
                    p.path.single() in setOf("categoryBits", "maskBits") &&
                    p.component in setOf("BoxCollider", "CircleCollider", "BoxZone", "CircleZone") -> {
                    PropertyNumberField(state, p, disabled)
                    CollisionMaskDrawer(p.label, p.value.jsonPrimitive.int, !disabled) {
                        state.setValue(p.component, p.path, JsonPrimitive(it))
                    }
                }
                else -> when (p.kind) {
                    PropertyKind.Number, PropertyKind.Integer -> if (scalarNumberEdit == null) PropertyNumberField(state,p,disabled)
                    else EditorNumberField(scalarNumberEdit, p.label, readOnly = disabled, authoredValue = p.value.jsonPrimitive.double)
                    PropertyKind.Boolean -> EditorCheckBox(p.label,p.value.jsonPrimitive.boolean,
                        { if (onScalarEdit == null) state.setValue(p.component,p.path,JsonPrimitive(it)) else onScalarEdit(JsonPrimitive(it)) },
                        enabled=!disabled,showLabel=false)
                    PropertyKind.Enum -> {
                        val quoteStrings = p.choices.any { !it.jsonPrimitive.isString }
                        fun caption(value: JsonElement) = if (quoteStrings) value.toString() else value.jsonPrimitive.content
                        val labels = p.choices.associateBy(::caption)
                        EditorSelect(p.label, caption(p.value), labels.keys.toList(),
                            { selected -> labels[selected]?.let { state.setValue(p.component,p.path,it) } },
                            Modifier.fillMaxWidth(),enabled=!disabled,showLabel=false)
                    }
                    else -> InspectorTextInput(display,p.label,{
                        if (onScalarEdit == null) state.editField(p.component,p.path,p.value,it) else onScalarEdit(JsonPrimitive(it))
                    },
                        readOnly=disabled,invalid=p.id in state.fieldErrors,multiline=p.kind in setOf(PropertyKind.Json, PropertyKind.Array, PropertyKind.Object))
                }
            }
        }
    }
}

/** Action slots receive the same path (including individual axes) and write permission as the editor. */
@Composable
private fun InspectorPropertyRow(
    property: EditorPropertyDefinition,
    readOnly: Boolean,
    invalid: Boolean,
    label: String = property.displayLabel,
    actions: (@Composable (EditorPropertyDefinition, Boolean) -> Unit)? = null,
    actionsReadOnly: Boolean = readOnly,
    labelModifier: Modifier = Modifier,
    overrideIndicator: (@Composable () -> Unit)? = null,
    content: @Composable (Boolean) -> Unit,
) {
    val help = listOfNotNull(property.descriptor?.help ?: "${property.component}.${property.label}",
        property.descriptor?.hard?.let { bounds ->
            listOfNotNull(
                bounds.min?.let { "Minimum $it${if (bounds.minExclusive) " (exclusive)" else ""}" },
                bounds.max?.let { "Maximum $it${if (bounds.maxExclusive) " (exclusive)" else ""}" },
            ).joinToString(". ").ifEmpty { null }
        }).joinToString(". ")
    EditorPropertyRow(label, help = help, labelModifier = labelModifier) {
        Column(Modifier.weight(1f)) {
            content(readOnly)
            if (invalid) Text(
                if (property.kind in setOf(PropertyKind.Number, PropertyKind.Integer)) "Enter a valid number." else "Enter a valid JSON value.",
                color = LocalEditorColors.current.error, style = sle.editor.ui.theme.LocalEditorTypography.current.secondary,
            )
        }
        overrideIndicator?.invoke()
        actions?.invoke(property, actionsReadOnly)
    }
}

@Composable
private fun PropertyNumberField(state: EntityWorkspaceState, property: EditorPropertyDefinition, readOnly: Boolean) {
    val descriptor = property.descriptor
    val presentation = remember(descriptor, property.kind) {
        descriptor?.ui?.let { NumberPresentation(it, descriptor.hard, property.kind == PropertyKind.Integer) }
    }
    EditorNumberField(state.numberEdit(property.component, property.path), property.label,
        readOnly = readOnly, presentation = presentation, authoredValue = property.value.jsonPrimitive.double)
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
