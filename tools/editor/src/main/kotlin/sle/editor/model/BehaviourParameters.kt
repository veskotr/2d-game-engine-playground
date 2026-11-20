package sle.editor.model

import kotlinx.serialization.json.*

internal fun JsonObject.behaviourEntries(): List<JsonObject> =
    (get("behaviours") as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }

internal fun JsonObject.behaviourText(field: String): String? =
    (get(field) as? JsonPrimitive)?.takeIf { it.isString }?.content?.takeIf { it.isNotEmpty() }

internal fun compatibleParameter(value: JsonElement?, default: JsonElement): Boolean {
    val scalar = value as? JsonPrimitive ?: return false
    val expected = default as? JsonPrimitive ?: return false
    if (scalar is JsonNull || expected is JsonNull) return false
    return when {
        expected.isString -> scalar.isString
        expected.booleanOrNull != null -> !scalar.isString && scalar.booleanOrNull != null
        else -> !scalar.isString && scalar.doubleOrNull?.isFinite() == true
    }
}

internal fun patchBehaviourParameter(
    document: EntityDocument, key: String, asset: String, name: String, value: JsonPrimitive?,
): EntityDocument? {
    val script = document.components["Script"] as? JsonObject ?: return null
    val behaviours = script["behaviours"] as? JsonArray ?: return null
    val matches = behaviours.indices.filter { index ->
        (behaviours[index] as? JsonObject)?.behaviourText("key") == key
    }
    val index = matches.singleOrNull() ?: return null
    val entry = behaviours[index].jsonObject
    if (entry.behaviourText("scriptAsset") != asset) return null
    val previous = (entry["parameters"] as? JsonObject).orEmpty()
    val updated = if (value == null) previous - name else previous + (name to value)
    if (updated == previous) return null
    val replacement = if (updated.isEmpty()) entry - "parameters"
        else entry + ("parameters" to JsonObject(updated))
    val attachments = behaviours.toMutableList().apply { this[index] = JsonObject(replacement) }
    return document.setValue("Script", listOf("behaviours"), JsonArray(attachments))
}
