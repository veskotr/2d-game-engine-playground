package sle.editor.model

import sle.editor.bridge.NativeEngine

import kotlinx.serialization.json.*
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.util.UUID

internal val documentJson = Json { prettyPrint = true }

internal data class EntityDocument(
    val key: String = UUID.randomUUID().toString(),
    val name: String = "New Entity",
    val components: JsonObject = JsonObject(emptyMap()),
) {
    fun addComponent(type: String, defaults: JsonObject): EntityDocument {
        require(type !in components) { "$type is already attached" }
        return copy(components = JsonObject(components + (type to defaults)))
    }

    fun removeComponent(type: String) = copy(components = JsonObject(components - type))

    fun setValue(component: String, path: List<String>, value: JsonElement): EntityDocument {
        require(component in components && path.isNotEmpty()) { "Component/field does not exist" }
        fun replace(current: JsonElement, remaining: List<String>): JsonElement {
            val objectValue = current.jsonObject
            val field = remaining.first()
            require(field in objectValue) { "Field does not exist: $field" }
            val next = if (remaining.size == 1) value else replace(objectValue.getValue(field), remaining.drop(1))
            return JsonObject(objectValue + (field to next))
        }
        return copy(components = JsonObject(components + (component to replace(components.getValue(component), path))))
    }

    fun serialize(): String = documentJson.encodeToString(JsonElement.serializer(), buildJsonObject {
        put("root", key)
        put("entities", buildJsonArray {
            add(buildJsonObject { put("key", key); put("name", name); put("components", components) })
        })
    }) + "\n"

    companion object {
        fun parse(source: String): EntityDocument {
            val document = Json.parseToJsonElement(source).jsonObject
            require(document.keys.all { it in setOf("root", "entities") }) { "Unsupported prefab metadata; no content was discarded" }
            val entities = document.getValue("entities").jsonArray
            require(entities.size == 1) { "Open a single-entity prefab; hierarchies are not supported yet" }
            val entity = entities.single().jsonObject
            require(entity.keys.all { it in setOf("key", "name", "components") }) { "Unsupported entity fields; parent links are not supported yet" }
            fun string(value: JsonElement): String {
                val primitive = value.jsonPrimitive
                require(primitive.isString) { "Expected string" }
                return primitive.content
            }
            val key = string(entity.getValue("key"))
            require(key.isNotBlank() && key == string(document.getValue("root"))) { "Entity key must match the nonempty prefab root" }
            return EntityDocument(key, entity["name"]?.let(::string) ?: "", entity.getValue("components").jsonObject)
        }
    }
}

internal fun prefabFileName(name: String): String {
    val stem = name.map { if (it.code < 32 || it in "<>:\"/\\|?*") '_' else it }
        .joinToString("").trim().trimEnd('.')
        .ifBlank { "entity" }
        .removeSuffix(".prefab.json")
        .ifBlank { "entity" }
    val reserved = stem.substringBefore('.').uppercase() in
        setOf("CON", "PRN", "AUX", "NUL", "COM1", "COM2", "COM3", "COM4", "COM5", "COM6", "COM7", "COM8", "COM9",
            "LPT1", "LPT2", "LPT3", "LPT4", "LPT5", "LPT6", "LPT7", "LPT8", "LPT9")
    return (if (reserved) "_$stem" else stem) + ".prefab.json"
}

internal object EntityFiles {
    fun open(path: Path) = EntityDocument.parse(Files.readString(path, UTF_8))

    fun save(document: EntityDocument, path: Path, validate: (String) -> Unit) {
        val text = document.serialize()
        validate(text)
        val target = path.toAbsolutePath()
        val temporary = Files.createTempFile(target.parent, ".sle-entity-", ".tmp")
        try {
            Files.writeString(temporary, text, UTF_8)
            Files.move(temporary, target, ATOMIC_MOVE, REPLACE_EXISTING)
        } finally { Files.deleteIfExists(temporary) }
    }
}

internal data class ComponentDefinition(val defaults: JsonObject, val enums: JsonObject)

internal fun readComponentCatalog(): Map<String, ComponentDefinition> =
    Json.parseToJsonElement(NativeEngine.componentCatalog()).jsonObject.mapValues { (_, entry) ->
        ComponentDefinition(entry.jsonObject.getValue("defaults").jsonObject, entry.jsonObject.getValue("enums").jsonObject)
    }

internal fun validateEntityDocument(text: String) = NativeEngine.validatePrefab(text.toByteArray(UTF_8))
