package sle.editor.ui.inspector

import kotlinx.serialization.json.*
import sle.editor.files.PrefabPackages
import sle.editor.model.ComponentDefinition
import sle.editor.model.PropertyDescriptor
import sle.editor.model.PropertyType

internal enum class PropertyKind { Boolean, String, Number, Integer, Enum, Vector, Resource, Object, Array, Json }

/** Presentation metadata only: native defaults/enums and the authored JSON remain authoritative. */
internal data class EditorPropertyDefinition(
    val component: String,
    val path: List<String>,
    val label: String,
    val value: JsonElement,
    val kind: PropertyKind,
    val choices: List<JsonElement> = emptyList(),
    val axes: List<EditorPropertyDefinition> = emptyList(),
    val defaultValue: JsonElement? = null,
    val descriptor: PropertyDescriptor? = null,
    val readOnly: Boolean = false,
    val structured: Boolean = false,
    val hasReadOnlyDescendants: Boolean = false,
    val projectDrawer: PropertyDrawer? = null,
) {
    val id get() = component+"/"+path.joinToString("/")
    /** Visible row label only; [label] stays the authored key for help and accessibility. */
    val displayLabel get() = descriptor?.label ?: when(path.last()) { "uv" -> "UV rect"; else -> humanizePropertyLabel(path.last()) }
    val drawer get() = projectDrawer ?: resolvePropertyDrawer(this)
    val axisLabels get() = axes.map { axisCaption(it.path.last()) }
    val isColor get() = drawer == PropertyDrawer.Color && axes.size == 4

    fun axisCaption(axis: String): String {
        val legacyCaptions = when (path.last().lowercase()) {
            "color", "tint" -> PropertyDrawer.Color.axisLabels
            else -> emptyMap()
        }
        return projectDrawer?.axisLabels?.get(axis) ?: descriptor?.axisLabels?.get(axis)
            ?: drawer?.axisLabels?.get(axis) ?: legacyCaptions[axis] ?: axis
    }
}

/** "position" -> "Position", "linearDamping" -> "Linear damping", "max_speed" -> "Max speed". */
internal fun humanizePropertyLabel(key: String): String {
    val words=key.replace('_',' ').replace(Regex("(?<=[a-z0-9])(?=[A-Z])")," ").trim().split(' ').filter { it.isNotEmpty() }
    if(words.isEmpty()) return key
    return words.mapIndexed { i,w -> if(i==0) w.replaceFirstChar { it.uppercaseChar() } else if(w.length>1 && w.all { it.isUpperCase() || it.isDigit() }) w else w.lowercase() }.joinToString(" ")
}

internal fun inspectorProperties(component: String, values: JsonObject, catalog: ComponentDefinition?): List<EditorPropertyDefinition> =
    values.asSequence().filterNot { (field, _) -> component == "SpriteRenderer" && field == "regionId" }
        .map { (field, value) -> propertyDefinition(component, listOf(field), value, catalog = catalog) }.toList()

internal fun propertyDefinition(
    component: String,
    path: List<String>,
    value: JsonElement,
    choices: JsonArray? = null,
    catalog: ComponentDefinition? = null,
    inheritedReadOnly: Boolean = false,
): EditorPropertyDefinition {
    val descriptor = catalog?.fields?.get(path)
    val primitive = value as? JsonPrimitive
    val vector = value as? JsonObject
    val numeric = primitive != null && !primitive.isString && primitive.doubleOrNull?.isFinite() == true
    val boolean = primitive != null && !primitive.isString && primitive.booleanOrNull != null
    val string = primitive?.isString == true
    val vectorShape = vector != null && vector.isNotEmpty() && vector.keys.all { it in setOf("x", "y", "z", "w") } &&
        vector.values.all { it is JsonPrimitive && !it.isString && it.doubleOrNull?.isFinite() == true }
    val legacy = choices ?: path.singleOrNull()?.let { catalog?.enums?.get(it) as? JsonArray }
    val declaredChoices = descriptor?.choices?.takeIf { options ->
        descriptor.type == PropertyType.Enum && options.any { choice ->
            when {
                choice is JsonNull -> value is JsonNull
                choice.isString -> string
                choice.booleanOrNull != null -> boolean
                else -> numeric
            }
        }
    }
    val enumChoices = declaredChoices ?: legacy?.takeIf { string && it.isNotEmpty() && it.all { choice -> (choice as? JsonPrimitive)?.isString == true } }
    val declaredKind = when (descriptor?.type) {
        PropertyType.Boolean -> PropertyKind.Boolean.takeIf { boolean }
        PropertyType.String -> PropertyKind.String.takeIf { string }
        PropertyType.Number -> PropertyKind.Number.takeIf { numeric }
        PropertyType.Integer -> PropertyKind.Integer.takeIf { numeric && primitive.longOrNull != null }
        PropertyType.Enum -> PropertyKind.Enum.takeIf { enumChoices != null }
        PropertyType.Vector -> PropertyKind.Vector.takeIf { vectorShape }
        PropertyType.Resource -> PropertyKind.Resource.takeIf { string && path.size == 1 }
        PropertyType.Object -> PropertyKind.Object.takeIf { value is JsonObject }
        PropertyType.Array -> PropertyKind.Array.takeIf { value is JsonArray }
        PropertyType.Json -> PropertyKind.Json
        null -> null
    }
    val kind = declaredKind ?: when {
        path.size == 1 && PrefabPackages.isAssetField(path.single()) && string -> PropertyKind.Resource
        enumChoices != null -> PropertyKind.Enum
        boolean -> PropertyKind.Boolean
        string -> PropertyKind.String
        numeric -> PropertyKind.Number
        vectorShape -> PropertyKind.Vector
        else -> PropertyKind.Json
    }
    val readOnly = inheritedReadOnly || descriptor?.readOnly == true
    val axes = if (kind == PropertyKind.Vector) vector!!.map { (axis, child) ->
        propertyDefinition(component, path + axis, child, catalog = catalog, inheritedReadOnly = readOnly)
    }.filter { it.descriptor.isVisibleIn(vector) }.sortedWith(propertyOrder) else emptyList()
    val default = path.fold<String, JsonElement?>(catalog?.defaults) { parent, key -> (parent as? JsonObject)?.get(key) }
    val structured = declaredKind == PropertyKind.Object ||
        declaredKind == null && kind == PropertyKind.Json && isStructuredGroup(path.last(), value)
    return EditorPropertyDefinition(component, path, path.joinToString("."), value, kind,
        enumChoices.orEmpty(), axes, default, descriptor, readOnly, structured,
        catalog.hasReadOnlyDescendants(path))
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

internal data class InspectorBlock(
    val group: String?,
    val properties: List<EditorPropertyDefinition>,
)

private val propertyOrder = compareBy<EditorPropertyDefinition> { it.descriptor?.order == null }
    .thenBy { it.descriptor?.order }

private fun PropertyDescriptor?.isVisibleIn(siblings: JsonObject): Boolean {
    val condition = this?.visibleWhen ?: return true
    val sibling = siblings[condition.path.singleOrNull()] as? JsonPrimitive ?: return true
    return condition.members?.contains(sibling) ?: (condition.equalsValue == null || sibling == condition.equalsValue)
}

internal fun inspectorBlocks(
    component: String,
    values: JsonObject,
    catalog: ComponentDefinition?,
    parent: List<String> = emptyList(),
    inheritedReadOnly: Boolean = false,
): List<InspectorBlock> {
    val keys = if (parent.isEmpty()) orderedFields(component, values.keys) else values.keys.toList()
    val properties = keys.map { field ->
        propertyDefinition(component, parent + field, values.getValue(field), catalog = catalog,
            inheritedReadOnly = inheritedReadOnly)
    }.filter { it.descriptor.isVisibleIn(values) }
    return groupInspectorProperties(properties, catalog)
}

internal fun groupInspectorProperties(
    properties: List<EditorPropertyDefinition>,
    catalog: ComponentDefinition?,
): List<InspectorBlock> {
    val groups = catalog?.groups.orEmpty()
    val buckets = properties.groupBy { it.descriptor?.group?.takeIf(groups::containsKey) }

    val order = listOf(null) + groups.keys.sortedWith(compareBy<String> { groups.getValue(it).order == null }
        .thenBy { groups.getValue(it).order })
    return order.mapNotNull { group ->
        buckets[group]?.let { InspectorBlock(group, it.sortedWith(propertyOrder)) }
    }
}

internal fun inspectorPathVisible(values: JsonObject, catalog: ComponentDefinition?, path: List<String>): Boolean {
    var siblings = values
    for ((index, part) in path.withIndex()) {
        if (!catalog?.fields?.get(path.take(index + 1)).isVisibleIn(siblings)) return false
        if (index < path.lastIndex) siblings = siblings[part] as? JsonObject ?: return true
    }
    return true
}

internal fun ComponentDefinition?.hasReadOnlyDescendants(path: List<String>): Boolean =
    this?.fields?.values?.any {
        it.readOnly && it.path.size > path.size && it.path.take(path.size) == path
    } == true
