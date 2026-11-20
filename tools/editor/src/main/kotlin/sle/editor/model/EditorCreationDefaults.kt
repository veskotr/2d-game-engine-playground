package sle.editor.model

import kotlinx.serialization.json.*

/** Authoring presets only: never applied when reading a saved document or the native catalog. */
internal fun editorComponentDefaults(type: String, defaults: JsonObject): JsonObject {
    val dimensions=buildJsonObject { put("x",64.0);put("y",64.0) }
    return when(type) {
        "SpriteRenderer", "BoxCollider", "BoxZone" -> JsonObject(defaults+("size" to dimensions))
        "CircleCollider", "CircleZone" -> JsonObject(defaults+("radius" to JsonPrimitive(32.0)))
        else -> defaults
    }
}

internal fun isEventArea(type: String) = type == "BoxZone" || type == "CircleZone"

internal fun EntityDocument.addEditorComponent(type: String, catalog: Map<String, ComponentDefinition>): EntityDocument {
    val added = addComponent(type, editorComponentDefaults(type, catalog.getValue(type).defaults))
    if (!isEventArea(type)) return added
    val zone = added.setValue(type, listOf("zoneId"), JsonPrimitive("$key-$type"))
    val positioned = if ("Transform" in zone.components) zone
        else zone.addComponent("Transform", catalog.getValue("Transform").defaults)
    if ("RigidBody" in positioned.components) return positioned
    val body = catalog.getValue("RigidBody").defaults
    return positioned.addComponent("RigidBody", JsonObject(body + ("bodyType" to JsonPrimitive("Static"))))
}
