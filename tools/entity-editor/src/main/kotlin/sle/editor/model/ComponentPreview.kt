package sle.editor.model

import kotlinx.serialization.json.*

/** Paused isolated rendering does not evaluate Animator targets. Valid scene
 * lookups use self only in the preview projection; authored references stay intact. */
internal fun isolatedComponentPreview(document: EntityDocument,sceneKeys: List<String>): EntityDocument {
    val animator=document.components["Animator"] as? JsonObject ?: return document
    val targets=animator["targetEntities"] as? JsonObject ?: return document
    val projected=JsonObject(targets.mapValues { (_,value) ->
        val valid=value is JsonPrimitive && if(value.isString) value.content in sceneKeys
            else value.intOrNull?.let { it in 1..sceneKeys.size }==true
        if(valid) JsonPrimitive(document.key) else value
    })
    return document.copy(components=JsonObject(document.components+("Animator" to JsonObject(animator+("targetEntities" to projected)))))
}
