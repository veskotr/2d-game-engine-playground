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
