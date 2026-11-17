package sle.editor.ui.inspector

import androidx.compose.foundation.layout.*
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.foundation.border
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
            EditorVectorField(p.displayLabel,p.axes.mapIndexed { index,axis -> p.axisLabels[index] to state.numberEdit(p.component,axis.path) },readOnly=readOnly)
            if(p.isColor) ColorPickerRow(state,p,readOnly)
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
                        if(p.component=="SpriteRenderer" && p.path==listOf("texture") && display.isNotBlank()) TexturePreview(state,display,readOnly)
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

private fun JsonElement?.number(axis: String)=((this as? JsonObject)?.get(axis) as? JsonPrimitive)?.doubleOrNull

/** Swatch of the current RGBA value; clicking opens a color chooser. Alpha is kept. */
@Composable
private fun ColorPickerRow(state: EntityWorkspaceState, p: EditorPropertyDefinition, readOnly: Boolean) {
    val m=sle.editor.ui.theme.LocalEditorMetrics.current
    fun channel(axis: String)=(p.value.number(axis) ?: 1.0).coerceIn(0.0,1.0)
    val swatch=androidx.compose.ui.graphics.Color(channel("x").toFloat(),channel("y").toFloat(),channel("z").toFloat(),1f)
    EditorPropertyRow("",help="${p.component}.${p.label}") {
        androidx.compose.foundation.layout.Box(Modifier.size(m.button).background(swatch,androidx.compose.foundation.shape.RoundedCornerShape(m.controlRadius))
            .border(m.borderWidth,LocalEditorColors.current.border,androidx.compose.foundation.shape.RoundedCornerShape(m.controlRadius)))
        EditorButton("Pick color…",{
            val initial=java.awt.Color(channel("x").toFloat(),channel("y").toFloat(),channel("z").toFloat())
            val chosen=javax.swing.JColorChooser.showDialog(sle.editor.ui.EditorDialogOwner.resolve(null),"Choose ${p.displayLabel.lowercase()}",initial)
            if(chosen!=null) state.setValue(p.component,p.path,buildJsonObject {
                put("x",chosen.red/255.0);put("y",chosen.green/255.0);put("z",chosen.blue/255.0);put("w",p.value.number("w") ?: 1.0)
            })
        },enabled=!readOnly)
        EditorButton("White",{ state.setValue(p.component,p.path,buildJsonObject { put("x",1);put("y",1);put("z",1);put("w",1) }) },
            enabled=!readOnly,help="Reset tint (no color change)",style=EditorButtonStyle.Ghost)
    }
}

/** Thumbnail and pixel size of the sprite texture, with a one-click "use image size". */
@Composable
private fun TexturePreview(state: EntityWorkspaceState, path: String, readOnly: Boolean) {
    val m=sle.editor.ui.theme.LocalEditorMetrics.current
    val image=remember(path) { runCatching { javax.imageio.ImageIO.read(java.io.File(path)) }.getOrNull() }
    if(image==null) { Text("Image not found or unsupported",color=LocalEditorColors.current.textSecondary,style=sle.editor.ui.theme.LocalEditorTypography.current.secondary);return }
    val bitmap=remember(image) { androidx.compose.ui.graphics.painter.BitmapPainter(image.toComposeImageBitmapCompat(),filterQuality=androidx.compose.ui.graphics.FilterQuality.None) }
    Row(Modifier.padding(top=m.gap/2),horizontalArrangement=Arrangement.spacedBy(m.gap),verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
        androidx.compose.foundation.Image(bitmap,"Texture preview",Modifier.size(m.button*2).background(LocalEditorColors.current.inputBackground),
            contentScale=androidx.compose.ui.layout.ContentScale.Fit)
        Column(verticalArrangement=Arrangement.spacedBy(m.gap/2)) {
            Text("${image.width} × ${image.height} px",color=LocalEditorColors.current.textSecondary,style=sle.editor.ui.theme.LocalEditorTypography.current.secondary)
            EditorButton("Use image size",{
                state.setValue("SpriteRenderer",listOf("size"),buildJsonObject { put("x",image.width);put("y",image.height) })
            },enabled=!readOnly && "size" in (state.document.components["SpriteRenderer"] as? JsonObject).orEmpty(),help="Set the sprite size to the texture's pixel size")
        }
    }
}

private fun java.awt.image.BufferedImage.toComposeImageBitmapCompat(): androidx.compose.ui.graphics.ImageBitmap {
    val argb=java.awt.image.BufferedImage(width,height,java.awt.image.BufferedImage.TYPE_INT_ARGB)
    argb.createGraphics().apply { drawImage(this@toComposeImageBitmapCompat,0,0,null);dispose() }
    return argb.toComposeImageBitmap()
}
