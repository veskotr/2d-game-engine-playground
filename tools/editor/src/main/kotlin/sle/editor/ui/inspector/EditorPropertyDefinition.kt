package sle.editor.ui.inspector

import kotlinx.serialization.json.*
import sle.editor.files.PrefabPackages
import sle.editor.model.ComponentDefinition

internal enum class PropertyKind { Boolean, String, Number, Enum, Vector, Resource, Json }

/** Presentation metadata only: native defaults/enums and the authored JSON remain authoritative. */
internal data class EditorPropertyDefinition(
    val component: String,
    val path: List<String>,
    val label: String,
    val value: JsonElement,
    val kind: PropertyKind,
    val choices: List<JsonElement> = emptyList(),
    val axes: List<EditorPropertyDefinition> = emptyList(),
) {
    val id get() = component+"/"+path.joinToString("/")
    /** Visible row label only; [label] stays the authored key for help and accessibility. */
    val displayLabel get() = when(path.last()) { "uv" -> "UV rect"; else -> humanizePropertyLabel(path.last()) }
    /** Axis captions: RGBA for colors, U/V corners for texture rectangles, else the axis key. */
    val axisLabels get() = axes.map { axis -> val key=axis.path.last()
        when {
            path.last().lowercase().let { it=="color" || it=="tint" } -> mapOf("x" to "R","y" to "G","z" to "B","w" to "A")[key] ?: key
            path.last()=="uv" -> mapOf("x" to "U0","y" to "V0","z" to "U1","w" to "V1")[key] ?: key
            else -> key
        } }
    val isColor get() = kind==PropertyKind.Vector && axes.size==4 && path.last().lowercase().let { it=="color" || it=="tint" }
}

/** "position" -> "Position", "linearDamping" -> "Linear damping", "max_speed" -> "Max speed". */
internal fun humanizePropertyLabel(key: String): String {
    val words=key.replace('_',' ').replace(Regex("(?<=[a-z0-9])(?=[A-Z])")," ").trim().split(' ').filter { it.isNotEmpty() }
    if(words.isEmpty()) return key
    return words.mapIndexed { i,w -> if(i==0) w.replaceFirstChar { it.uppercaseChar() } else if(w.length>1 && w.all { it.isUpperCase() || it.isDigit() }) w else w.lowercase() }.joinToString(" ")
}

internal fun inspectorProperties(component: String, values: JsonObject, catalog: ComponentDefinition?): List<EditorPropertyDefinition> =
    values.map { (field,value) -> propertyDefinition(component,listOf(field),value,catalog?.enums?.get(field) as? JsonArray) }

internal fun propertyDefinition(component: String, path: List<String>, value: JsonElement, choices: JsonArray? = null): EditorPropertyDefinition {
    val primitive=value as? JsonPrimitive
    val vector=value as? JsonObject
    val kind=when {
        path.size==1 && PrefabPackages.isAssetField(path.single()) && primitive?.isString==true -> PropertyKind.Resource
        choices!=null -> PropertyKind.Enum
        primitive!=null && !primitive.isString && primitive.booleanOrNull!=null -> PropertyKind.Boolean
        primitive?.isString==true -> PropertyKind.String
        primitive!=null && primitive.doubleOrNull?.isFinite()==true -> PropertyKind.Number
        vector!=null && vector.isNotEmpty() && vector.keys.all { it in setOf("x","y","z","w") } &&
            vector.values.all { it is JsonPrimitive && !it.isString && it.doubleOrNull?.isFinite()==true } -> PropertyKind.Vector
        else -> PropertyKind.Json
    }
    return EditorPropertyDefinition(component,path,path.joinToString("."),value,kind,choices?.toList().orEmpty(),
        if(kind==PropertyKind.Vector) vector!!.map { (axis,child) -> propertyDefinition(component,path+axis,child) } else emptyList())
}

/** Display order for well-known components; unknown fields keep authored order after these. */
private val preferredFieldOrder=mapOf(
    "SpriteRenderer" to listOf("texture","color","size","offset","layer","visible","region"),
    "BoxCollider" to listOf("size","offset"),
    "CircleCollider" to listOf("radius","offset"),
)
internal fun orderedFields(component: String, fields: Set<String>): List<String> {
    val preferred=preferredFieldOrder[component].orEmpty()
    return preferred.filter { it in fields }+fields.filter { it !in preferred }
}

/** Small fixed-shape objects (e.g. SpriteRenderer.region = {uv:{x,y,z,w}}) render as nested rows
 * instead of raw JSON. Free-form maps such as script variables stay JSON. */
internal fun isStructuredGroup(field: String, value: JsonElement): Boolean {
    if(field in setOf("variables","targetEntities","stateClipMap") || value !is JsonObject || value.isEmpty()) return false
    if(value.keys.all { it in setOf("x","y","z","w") }) return false
    return value.values.all { child -> when(child) {
        is JsonObject -> child.isNotEmpty() && child.keys.all { it in setOf("x","y","z","w") } && child.values.all { it is JsonPrimitive && !it.isString && it.doubleOrNull?.isFinite()==true }
        is JsonPrimitive -> !child.isString
        else -> false
    } }
}
