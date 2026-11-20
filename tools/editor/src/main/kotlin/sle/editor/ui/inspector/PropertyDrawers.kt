package sle.editor.ui.inspector

import sle.editor.project.assets.AssetIdentity
import sle.editor.project.assets.LocalProjectAssets
import sle.editor.project.assets.caption
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toComposeImageBitmap
import kotlinx.serialization.json.*
import sle.editor.model.EntityWorkspaceState
import sle.editor.ui.controls.*
import sle.editor.ui.theme.LocalEditorColors

internal enum class PropertyDrawer(
    val id: String,
    val axisLabels: Map<String, String> = emptyMap(),
) {
    Color("color", mapOf("x" to "R", "y" to "G", "z" to "B", "w" to "A")),
    Uv("uv", mapOf("x" to "U0", "y" to "V0", "z" to "U1", "w" to "V1")),
    Resource("resource"),
    Texture("texture"),
    Structured("structured");

    fun accepts(property: EditorPropertyDefinition): Boolean = when (this) {
        Color -> property.kind == PropertyKind.Vector && property.value.jsonObject.size == 4
        Uv -> property.kind == PropertyKind.Vector
        Resource, Texture -> property.kind == PropertyKind.Resource
        Structured -> property.structured
    }
}

private val registeredDrawers = PropertyDrawer.entries.associateBy(PropertyDrawer::id)

internal fun resolvePropertyDrawer(property: EditorPropertyDefinition): PropertyDrawer? {
    registeredDrawers[property.descriptor?.drawer]?.takeIf { it.accepts(property) }?.let { return it }
    val inferred = when (property.path.last().lowercase()) {
        "color", "tint" -> PropertyDrawer.Color
        "uv" -> PropertyDrawer.Uv
        else -> null
    }
    return when {
        inferred?.accepts(property) == true -> inferred
        property.kind == PropertyKind.Resource ->
            if (property.component == "SpriteRenderer" && property.path == listOf("texture")) PropertyDrawer.Texture
            else PropertyDrawer.Resource
        property.structured -> PropertyDrawer.Structured
        else -> null
    }
}

@Composable
internal fun VectorPropertyFields(
    state: EntityWorkspaceState,
    property: EditorPropertyDefinition,
    renderAxis: @Composable (EditorPropertyDefinition, String) -> Unit,
) {
    val captions = property.axes.map { axis ->
        val key = axis.path.last()
        val caption = property.projectDrawer?.axisLabels?.get(key) ?: property.descriptor?.axisLabels?.get(key)
        axis.path to (caption ?: property.axisCaption(key).uppercase())
    }.toMap()
    val blocks = groupInspectorProperties(property.axes, state.catalog[property.component])
    InspectorGroups(state, property.component, blocks) { axis ->
        renderAxis(axis, captions.getValue(axis.path))
    }
}

@Composable
internal fun StructuredPropertyDrawer(
    state: EntityWorkspaceState,
    property: EditorPropertyDefinition,
    readOnly: Boolean,
    renderChild: @Composable (EditorPropertyDefinition) -> Unit,
) {
    val blocks = inspectorBlocks(property.component, property.value.jsonObject,
        state.catalog[property.component], property.path, readOnly)
    InspectorGroups(state, property.component, blocks, renderChild)
}

@Composable
internal fun ResourceDrawer(
    state: EntityWorkspaceState,
    property: EditorPropertyDefinition,
    display: String,
    readOnly: Boolean,
    browseEnabled: Boolean,
    browse: (() -> Unit)?,
    showTexture: Boolean,
) {
    val importedName = state.assets.entries.firstOrNull { it.key.toString().replace('\\', '/') == display }?.value
    InspectorTextInput(importedName ?: display, property.label,
        { state.editField(property.component, property.path, property.value, it) },
        readOnly = readOnly || importedName != null, invalid = property.id in state.fieldErrors)
    FlowResourceActions(!readOnly, browseEnabled, browse,
        if (display.isNotBlank()) ({ state.setValue(property.component, property.path, JsonPrimitive("")) }) else null)
    if (showTexture) {
        SpriteRegionPicker(state, display, readOnly)
        if (display.isNotBlank()) TexturePreview(state, property, display, readOnly)
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

private fun JsonElement?.number(axis: String)=((this as? JsonObject)?.get(axis) as? JsonPrimitive)?.doubleOrNull

/** Swatch of the current RGBA value; clicking opens a color chooser. Alpha is kept. */
@Composable
internal fun ColorPickerRow(state: EntityWorkspaceState, p: EditorPropertyDefinition, readOnly: Boolean) {
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
                put("x",chosen.red/255.0);put("y",chosen.green/255.0);put("z",chosen.blue/255.0);put("w",p.value.jsonObject.getValue("w"))
            })
        },enabled=!readOnly)
        EditorButton("White",{ state.setValue(p.component,p.path,buildJsonObject { put("x",1);put("y",1);put("z",1);put("w",1) }) },
            enabled=!readOnly,help="Reset tint (no color change)",style=EditorButtonStyle.Ghost)
    }
}

@Composable
private fun SpriteRegionPicker(state: EntityWorkspaceState, path: String, readOnly: Boolean) {
    val current = ((state.document.components["SpriteRenderer"] as? JsonObject)
        ?.get("regionId") as? JsonPrimitive)?.contentOrNull
    val snapshot = LocalProjectAssets.current?.snapshot
    val regions = remember(path, snapshot) {
        runCatching {
            val image = java.nio.file.Path.of(path)
            if (!image.isAbsolute) return@runCatching emptyList()
            val metadata = AssetIdentity.sidecar(image)
            if (!java.nio.file.Files.isRegularFile(metadata)) return@runCatching emptyList()
            val entries = AssetIdentity.readMetadata(metadata).regions
            val dimensions = runCatching { sle.editor.project.assets.spriteImageSize(image) }.getOrNull()
            if (dimensions == null) entries else entries.filter { it.fits(dimensions.first, dimensions.second) }
        }.getOrDefault(emptyList())
    }
    if (current == null && regions.isEmpty()) return
    val labels = regions.associateBy { it.caption() }
    val selected = regions.firstOrNull { it.id == current }?.caption()
    val manual = "Use UV rectangle"
    val missing = "Missing or out-of-bounds region"
    EditorSelect("Sprite region", selected ?: if (current == null) manual else missing,
        listOf(manual) + labels.keys,
        onSelect = { state.setSpriteRegion(labels[it]?.id) }, enabled = !readOnly)
    if (current != null && selected == null)
        Text("The sprite uses the full texture.", color = LocalEditorColors.current.error)
}

/** Thumbnail and pixel size of the sprite texture, with a one-click "use image size". */
@Composable
private fun TexturePreview(
    state: EntityWorkspaceState, property: EditorPropertyDefinition, path: String, readOnly: Boolean,
) {
    val m=sle.editor.ui.theme.LocalEditorMetrics.current
    val sizePath = listOf("size")
    val catalog = state.catalog["SpriteRenderer"]
    val canSetSize = !readOnly && catalog?.fields?.get(sizePath)?.readOnly != true &&
        !catalog.hasReadOnlyDescendants(sizePath)
    val image=remember(path) { runCatching { javax.imageio.ImageIO.read(java.io.File(path)) }.getOrNull() }
    if(image==null) { Text("Image not found or unsupported",color=LocalEditorColors.current.textSecondary,style=sle.editor.ui.theme.LocalEditorTypography.current.secondary);return }
    val bitmap=remember(image) { androidx.compose.ui.graphics.painter.BitmapPainter(image.toComposeImageBitmapCompat(),filterQuality=androidx.compose.ui.graphics.FilterQuality.None) }
    Row(Modifier.padding(top=m.gap/2),horizontalArrangement=Arrangement.spacedBy(m.gap),verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
        androidx.compose.foundation.Image(bitmap,"Texture preview",Modifier.size(m.button*2).background(LocalEditorColors.current.inputBackground),
            contentScale=androidx.compose.ui.layout.ContentScale.Fit)
        Column(verticalArrangement=Arrangement.spacedBy(m.gap/2)) {
            Text("${image.width} × ${image.height} px",color=LocalEditorColors.current.textSecondary,style=sle.editor.ui.theme.LocalEditorTypography.current.secondary)
            if (property.component == "SpriteRenderer" && property.path == listOf("texture")) EditorButton("Use image size",{
                state.setValue("SpriteRenderer",sizePath,buildJsonObject { put("x",image.width);put("y",image.height) })
            },enabled=canSetSize && "size" in (state.document.components["SpriteRenderer"] as? JsonObject).orEmpty(),help="Set the sprite size to the texture's pixel size")
        }
    }
}

private fun java.awt.image.BufferedImage.toComposeImageBitmapCompat(): androidx.compose.ui.graphics.ImageBitmap {
    val argb=java.awt.image.BufferedImage(width,height,java.awt.image.BufferedImage.TYPE_INT_ARGB)
    argb.createGraphics().apply { drawImage(this@toComposeImageBitmapCompat,0,0,null);dispose() }
    return argb.toComposeImageBitmap()
}
