package sle.editor.model

import kotlinx.serialization.json.*
import kotlin.test.*

class LinkedPrefabTest {
    private val source="""{"root":"actor","entities":[{"key":"actor","name":"Actor","components":{"Transform":{"position":[0,0]},"Audio":{"volume":1}}},{"key":"hand","parent":"actor","components":{"Animator":{"targetEntities":{"root":1,"self":"hand"}}}}]}"""
    @Test fun subtreeRoundTripAndRepeatedInstancesKeepReferencesIsolated() {
        val prefab=PrefabDocument.parse(source)
        assertEquals(prefab,PrefabDocument.parse(prefab.serialize()))
        val a=prefab.instantiate("a");val b=prefab.instantiate("b")
        assertEquals("a",a.parents["a/hand"])
        assertEquals("b",b.entities[1].components["Animator"]!!.jsonObject["targetEntities"]!!.jsonObject["root"]!!.jsonPrimitive.content)
        assertEquals("a/hand",a.entities[1].components["Animator"]!!.jsonObject["targetEntities"]!!.jsonObject["self"]!!.jsonPrimitive.content)
    }
    @Test fun refreshPreservesPlacementAndOverridesAndPropagatesRemoval() {
        val prefab=PrefabDocument.parse(source)
        val link=PrefabInstance("asset-id","a",prefab)
        val initial=prefab.instantiate("a")
        val edited=initial.copy(entities=initial.entities.map { if(it.key=="a") it.copy(components=JsonObject(it.components+("Transform" to buildJsonObject { put("position",buildJsonArray { add(100);add(50) }) }))) else it })
        val updated=PrefabDocument.parse(source.replace("\"volume\":1","\"volume\":0.5").replace("\"Actor\"","\"Updated\""))
        val result=link.reconcile(edited,updated)
        assertEquals("Updated",result.entities[0].name)
        assertEquals(JsonPrimitive(0.5),result.entities[0].components["Audio"]!!.jsonObject["volume"])
        assertEquals(JsonArray(listOf(JsonPrimitive(100),JsonPrimitive(50))),result.entities[0].components["Transform"]!!.jsonObject["position"])
        val removed=updated.copy(entities=updated.entities.map { it.removeComponent("Audio") })
        assertFalse("Audio" in link.reconcile(initial,removed).entities[0].components)
    }
    @Test fun explicitOverrideSurvivesWhenItEqualsAnIntermediatePrefabDefault() {
        val original=PrefabDocument.parse(source)
        val overrides=buildJsonObject { put("actor",buildJsonObject { put("Audio",buildJsonObject { put("volume",0.5) }) }) }
        val intermediate=PrefabDocument.parse(source.replace("\"volume\":1","\"volume\":0.5"))
        val link=PrefabInstance("asset-id","a",intermediate,overrides)
        val scene=intermediate.instantiate("a",overrides=overrides)
        val result=link.reconcile(scene,original)
        assertEquals(JsonPrimitive(0.5),result.entities.first().components["Audio"]!!.jsonObject["volume"])
    }
    @Test fun differentFieldsKeepExplicitOverridesAndNestedRemovals() {
        val prefab=PrefabDocument("root",listOf(EntityDocument(key="root",components=buildJsonObject {
            put("Audio",buildJsonObject { put("volume",0.5);put("loop",false) })
        })))
        val overrides=buildJsonObject { put("root",buildJsonObject { put("Audio",buildJsonObject { put("volume",0.5) }) }) }
        val scene=prefab.instantiate("a",overrides=overrides)
        val edited=scene.copy(entities=scene.entities.map { it.setValue("Audio",listOf("loop"),JsonPrimitive(true)) })
        val next=prefab.copy(entities=prefab.entities.map { it.setValue("Audio",listOf("volume"),JsonPrimitive(0.8)) })
        val link=PrefabInstance("asset-id","a",prefab,overrides)
        val audio=link.reconcile(edited,next).entities.single().components["Audio"]!!.jsonObject
        assertEquals(JsonPrimitive(0.5),audio["volume"])
        assertEquals(JsonPrimitive(true),audio["loop"])
    }
    @Test fun malformedSubtreesAndExternalReferencesAreRejected() {
        assertFails { PrefabDocument.parse(source.replace("\"parent\":\"actor\"","\"parent\":\"hand\"")) }
        assertFails { PrefabDocument.parse(source.replace("\"self\":\"hand\"","\"self\":\"outside\"")) }
    }
}
