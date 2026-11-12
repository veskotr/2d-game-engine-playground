package sle.editor.model

import kotlinx.serialization.json.*
import java.util.UUID

internal const val MAX_SCENE_ENTITIES=4096

internal data class SceneDocument(val key: String = UUID.randomUUID().toString(), val name: String = "New Scene",
    val entities: List<EntityDocument> = emptyList(), val parents: Map<String,String> = emptyMap()) {
    fun serialize(): String {
        require(key.isNotBlank()) { "Scene key is missing" }
        require(entities.size<=MAX_SCENE_ENTITIES) { "Scene editor supports at most $MAX_SCENE_ENTITIES entities" }
        require(entities.map { it.key }.distinct().size == entities.size && entities.all { it.key.isNotBlank() && it.key != "@scene" }) { "Entity keys must be unique and nonempty" }
        validateSceneParents(entities.map { it.key },parents)
        return documentJson.encodeToString(JsonElement.serializer(),buildJsonObject {
            put("key",key);put("name",name)
            put("entities",JsonArray(entities.map { JsonObject(Json.parseToJsonElement(it.serialize()).jsonObject.getValue("entities").jsonArray.single().jsonObject + (parents[it.key]?.let { parent -> mapOf("parent" to JsonPrimitive(parent)) } ?: emptyMap())) }))
        })+"\n"
    }
    companion object {
        fun parse(source: String): SceneDocument {
            val root=Json.parseToJsonElement(source).jsonObject
            require(root.keys.all { it in setOf("key","name","entities") }) { "Scene editor supports authored entities; unsupported scene metadata was not discarded" }
            require(root["entities"] is JsonArray) { "Scene entities must be an array" }
            val raw=root.getValue("entities").jsonArray
            require(raw.size<=MAX_SCENE_ENTITIES) { "Too many scene entities" }
            val keys=raw.map { it.jsonObject.getValue("key").jsonPrimitive.content }
            val parents=raw.mapIndexedNotNull { index,value ->
                val parent=value.jsonObject["parent"] ?: return@mapIndexedNotNull null
                if(parent==JsonNull) return@mapIndexedNotNull null
                val primitive=parent as? JsonPrimitive ?: error("Parent must be a key or ordinal")
                val resolved=if(primitive.isString) primitive.content else primitive.intOrNull?.let { keys.getOrNull(it-1) } ?: error("Invalid parent ordinal")
                keys[index] to resolved
            }.toMap()
            val entities=raw.map { value ->
                val entity=value.jsonObject
                val key=entity.getValue("key").jsonPrimitive.also { require(it.isString) }.content
                EntityDocument.parse(buildJsonObject { put("root",key);put("entities",JsonArray(listOf(JsonObject(entity-"parent")))) }.toString())
            }
            fun text(field: String, fallback: String) = root[field]?.jsonPrimitive?.also { require(it.isString) { "$field must be a string" } }?.content ?: fallback
            return SceneDocument(text("key",UUID.randomUUID().toString()),text("name","New Scene"),entities,parents).also { it.serialize() }
        }
    }
}

internal fun copySceneEntity(source: EntityDocument, ordinalKeys: List<String> = emptyList()): EntityDocument {
    val key=UUID.randomUUID().toString()
    var components=source.components
    val animator=components["Animator"] as? JsonObject
    val targets=animator?.get("targetEntities") as? JsonObject
    if (targets!=null) {
        val remapped=JsonObject(targets.mapValues { (_, value) ->
            val resolved=if (value is JsonPrimitive && !value.isString) value.intOrNull?.let { ordinalKeys.getOrNull(it-1) }?.let(::JsonPrimitive) ?: value else value
            if (resolved is JsonPrimitive && resolved.isString && resolved.content==source.key) JsonPrimitive(key) else resolved
        })
        components=JsonObject(components+("Animator" to JsonObject(animator+("targetEntities" to remapped))))
    }
    return source.copy(key=key,components=components)
}
