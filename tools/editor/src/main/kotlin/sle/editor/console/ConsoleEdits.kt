package sle.editor.console

import kotlinx.serialization.json.*
import sle.editor.model.EntityDocument
import sle.editor.model.SceneDocument
import sle.editor.model.validateSceneParents

/** Interpret edits against detached data. The caller owns validation and publication. */
internal fun applyConsoleEdits(
    scene: SceneDocument,
    edits: JsonArray,
    defaults: (String) -> JsonObject,
): SceneDocument {
    val entities = scene.entities.associateByTo(linkedMapOf()) { it.key }
    val parents = scene.parents.toMutableMap()
    val removed = mutableSetOf<String>()
    val linked = scene.prefabInstances.flatMap { it.entityKeys }.toSet()
    fun JsonObject.text(key: String): String = getValue(key).jsonPrimitive
        .also { require(it.isString && it.content.isNotBlank()) { "Expected text for $key" } }.content

    require(edits.size <= 128) { "Too many console edits" }
    for ((index, item) in edits.withIndex()) {
        try {
            val edit = item.jsonObject
            val id = edit.text("entity")
            val entity = entities[id]
            val op = edit.text("op")
            if (op == "createEntity") {
                require(entity == null && id !in removed) { "Entity ID already used: $id" }
                entities[id] = EntityDocument(id, edit.text("name"))
                continue
            }
            requireNotNull(entity) { "Unknown entity: $id" }
            when (op) {
                "setField" -> {
                    val path = edit.getValue("path").jsonArray.map {
                        it.jsonPrimitive.also { part -> require(part.isString) }.content
                    }
                    entities[id] = entity.setValue(edit.text("component"), path, edit.getValue("value"))
                }
                "addComponent" -> {
                    val component = edit.text("component")
                    entities[id] = entity.addComponent(component, defaults(component))
                }
                "removeComponent" -> {
                    val component = edit.text("component")
                    require(component in entity.components) { "Component does not exist: $component" }
                    entities[id] = entity.removeComponent(component)
                }
                "reparentEntity" -> {
                    require(id !in linked) { "Unpack the linked entity template before reparenting" }
                    if (edit["parent"] == null || edit["parent"] == JsonNull) parents.remove(id)
                    else parents[id] = edit.text("parent").also { require(it in entities) { "Unknown parent: $it" } }
                    validateSceneParents(entities.keys.toList(), parents)
                }
                "deleteEntity" -> {
                    val byParent = parents.entries.groupBy({ it.value }, { it.key })
                    val pending = ArrayDeque(listOf(id))
                    while (pending.isNotEmpty()) {
                        val key = pending.removeFirst()
                        require(key !in linked) { "Unpack the linked entity template before deleting" }
                        pending.addAll(byParent[key].orEmpty())
                        entities.remove(key)
                        parents.remove(key)
                        removed.add(key)
                    }
                }
                else -> error("Unknown operation: $op")
            }
        } catch (failure: Exception) {
            throw IllegalArgumentException("Edit ${index + 1}: ${failure.message}", failure)
        }
    }
    return scene.copy(entities = entities.values.toList(), parents = parents)
}
