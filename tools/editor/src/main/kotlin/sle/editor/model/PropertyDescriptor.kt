package sle.editor.model

import kotlinx.serialization.json.*

internal enum class PropertyType { Boolean, String, Number, Integer, Enum, Vector, Resource, Object, Array, Json }

internal data class NumericConstraints(
    val min: Double?, val max: Double?, val minExclusive: Boolean, val maxExclusive: Boolean,
)

internal data class NumericPresentation(
    val min: Double?, val max: Double?, val step: Double?, val precision: Int?,
    val unit: String?, val slider: Boolean,
)

internal data class VisibilityCondition(
    val path: List<String>, val equalsValue: JsonPrimitive? = null, val members: List<JsonPrimitive>? = null,
)

internal data class PropertyGroup(val label: String? = null, val order: Int? = null)

internal data class PropertyDescriptor(
    val path: List<String>,
    val type: PropertyType?,
    val label: String?,
    val help: String?,
    val order: Int?,
    val group: String?,
    val drawer: String?,
    val axisLabels: Map<String, String>,
    val choices: List<JsonPrimitive>?,
    val hard: NumericConstraints?,
    val ui: NumericPresentation?,
    val visibleWhen: VisibilityCondition?,
    val readOnly: Boolean,
)

internal fun decodeComponentCatalog(text: String): Map<String, ComponentDefinition> =
    Json.parseToJsonElement(text).jsonObject.mapValues { (_, entry) ->
        val component = entry.jsonObject
        val fields = (component["fields"] as? JsonArray).orEmpty().mapNotNull { element ->
            (element as? JsonObject)?.let(::decodeProperty)
        }.groupBy { it.path }.mapNotNull { (path, records) ->
            records.singleOrNull()?.let { path to it }
        }.toMap()
        val groups = (component["groups"] as? JsonObject).orEmpty().mapNotNull { (id, element) ->
            val group = element as? JsonObject ?: return@mapNotNull null
            if (id.isBlank()) return@mapNotNull null
            id to PropertyGroup(group["label"].text(), group["order"].integer())
        }.toMap()
        ComponentDefinition(component.getValue("defaults").jsonObject, component.getValue("enums").jsonObject,
            fields, groups)
    }

private fun decodeProperty(record: JsonObject): PropertyDescriptor? {
    val path = record["path"].path() ?: return null
    val typeName = record["type"].text()
    return PropertyDescriptor(
        path = path,
        type = PropertyType.entries.firstOrNull { it.name.lowercase() == typeName },
        label = record["label"].text(),
        help = record["help"].text(),
        order = record["order"].integer(),
        group = record["group"].text(),
        drawer = record["drawer"].text(),
        axisLabels = (record["axisLabels"] as? JsonObject).orEmpty().mapNotNull { (axis, element) ->
            element.text()?.let { axis to it }
        }.toMap(),
        choices = record["choices"].scalars()?.takeIf { it.isNotEmpty() && it.distinct().size == it.size },
        hard = (record["hard"] as? JsonObject)?.let {
            val (min, max) = bounds(it)
            NumericConstraints(min, max, min != null && it["minExclusive"].flag(), max != null && it["maxExclusive"].flag())
        },
        ui = (record["ui"] as? JsonObject)?.let {
            val (min, max) = bounds(it)
            NumericPresentation(min, max, it["step"].number()?.takeIf { step -> step > 0 },
                it["precision"].integer()?.takeIf { precision -> precision >= 0 }, it["unit"].text(), it["slider"].flag())
        },
        visibleWhen = (record["visibleWhen"] as? JsonObject)?.let(::decodeVisibility),
        readOnly = record["readOnly"].flag(),
    )
}

private fun decodeVisibility(record: JsonObject): VisibilityCondition? {
    val path = record["path"].path()?.takeIf { it.size == 1 } ?: return null
    if (("equals" in record) == ("in" in record)) return null
    return if ("equals" in record) record["equals"].scalar()?.let { VisibilityCondition(path, equalsValue = it) }
    else record["in"].scalars()?.let { VisibilityCondition(path, members = it) }
}

private fun bounds(record: JsonObject): Pair<Double?, Double?> {
    val min = record["min"].number()
    val max = record["max"].number()
    return if (min != null && max != null && min > max) null to null else min to max
}

private fun JsonElement?.path(): List<String>? {
    val keys = this as? JsonArray ?: return null
    return keys.takeIf { it.isNotEmpty() }?.map { (it as? JsonPrimitive)?.takeIf { key -> key.isString }?.content ?: return null }
}

private fun JsonElement?.scalars(): List<JsonPrimitive>? =
    (this as? JsonArray)?.map { it.scalar() ?: return null }

private fun JsonElement?.scalar() = this as? JsonPrimitive
private fun JsonElement?.text() = scalar()?.takeIf { it.isString }?.content?.takeIf { it.isNotEmpty() }
private fun JsonElement?.number() = scalar()?.takeUnless { it.isString }?.doubleOrNull?.takeIf { it.isFinite() }
private fun JsonElement?.integer() = scalar()?.takeUnless { it.isString }?.intOrNull
private fun JsonElement?.flag() = scalar()?.takeUnless { it.isString }?.booleanOrNull == true
