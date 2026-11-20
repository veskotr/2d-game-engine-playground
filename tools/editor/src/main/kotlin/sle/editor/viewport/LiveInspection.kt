package sle.editor.viewport

import kotlinx.serialization.json.*

internal data class LiveEntity(
    val id: Long,
    val parent: Long,
    val authoredKey: String?,
    val name: String?,
) {
    val label: String get() = name ?: authoredKey ?: "Entity #$id"
}

internal data class LiveSelection(val run: Long, val scene: Long, val entity: Long)

internal data class LiveInspection(
    val run: Long,
    val scene: Long,
    val entities: List<LiveEntity>,
    val selection: LiveSelection?,
    val details: JsonObject?,
    val playRevision: Long? = null,
) {
    fun selectionFor(entity: LiveEntity) = LiveSelection(run, scene, entity.id)

    companion object {
        fun parse(bytes: ByteArray, requested: LiveSelection?): LiveInspection? {
            val snapshot = Json.parseToJsonElement(bytes.toString(Charsets.UTF_8)) as? JsonObject ?: return null
            val run = snapshot.getValue("run").jsonPrimitive.long
            val scene = snapshot.getValue("sceneInstance").jsonPrimitive.long
            val entities = snapshot.getValue("entities").jsonArray.map { value ->
                val row = value.jsonObject
                LiveEntity(row.getValue("id").jsonPrimitive.long, row.getValue("parent").jsonPrimitive.long,
                    row.getValue("authoredKey").jsonPrimitive.contentOrNull, row["name"]?.jsonPrimitive?.contentOrNull)
            }
            val selection = requested?.takeIf { it.run == run && it.scene == scene &&
                entities.any { entity -> entity.id == it.entity } }
            return LiveInspection(run, scene, entities, selection,
                if (selection != null) snapshot["details"] as? JsonObject else null)
        }
    }
}
