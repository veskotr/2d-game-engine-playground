package sle.editor.model

import kotlinx.serialization.json.*
import kotlin.test.*

/** Session 6: prefab creation from scene subtrees, Apply Changes to Prefab and refresh state. */
class PrefabAuthoringTest {
    private fun entity(key: String,vararg components: Pair<String,JsonElement>)=EntityDocument(key=key,name=key.uppercase(),components=JsonObject(components.toMap()))
    private fun audio(volume: Double)=buildJsonObject { put("volume",volume) }
    private fun animator(vararg targets: Pair<String,JsonElement>)=buildJsonObject { put("targetEntities",JsonObject(targets.toMap())) }
    private fun placed(x: Int)=buildJsonObject { put("position",buildJsonArray { add(x);add(0) }) }

    private val authored=SceneDocument(key="scene",entities=listOf(
        entity("a","Transform" to placed(10)),
        entity("b","Audio" to audio(1.0),"Animator" to animator("self" to JsonPrimitive("b"),"root" to JsonPrimitive(1))),
        entity("c"),
        entity("outside","Animator" to animator("aim" to JsonPrimitive("b"),"numeric" to JsonPrimitive(4))),
    ),parents=mapOf("b" to "a","c" to "b"))

    @Test fun subtreeBecomesPrefabAndSceneIsRekeyedAsItsInstance() {
        val created=prefabFromSceneSubtree(authored,"a")
        assertEquals("a",created.prefab.root)
        assertEquals(listOf("a","b","c"),created.prefab.entities.map { it.key })
        assertEquals(mapOf("b" to "a","c" to "b"),created.prefab.parents)
        // Numeric internal targets become local keys inside the prefab.
        assertEquals(JsonPrimitive("a"),created.prefab.entities[1].components["Animator"]!!.jsonObject["targetEntities"]!!.jsonObject["root"])
        val scene=created.scene
        assertEquals(listOf("a","a/b","a/c","outside"),scene.entities.map { it.key })
        assertEquals(mapOf("a/b" to "a","a/c" to "a/b"),scene.parents)
        val outside=scene.entities.last().components["Animator"]!!.jsonObject["targetEntities"]!!.jsonObject
        assertEquals(JsonPrimitive("a/b"),outside["aim"])
        assertEquals(JsonPrimitive(4),outside["numeric"],"numeric targets outside the subtree keep their position")
        assertEquals(mapOf("a" to "a","a/b" to "b","a/c" to "c"),created.renamed)
        assertEquals(authored.entities[0].components,scene.entities[0].components)
    }

    @Test fun subtreeConversionRejectsExternalTargetsAndNestedInstances() {
        val external=authored.copy(entities=authored.entities.map {
            if(it.key=="c") it.copy(components=JsonObject(mapOf("Animator" to animator("out" to JsonPrimitive("outside"))))) else it
        })
        assertFails { prefabFromSceneSubtree(external,"a") }
        val numericIntoSubtree=authored.copy(entities=authored.entities.map {
            if(it.key=="outside") it.copy(components=JsonObject(mapOf("Animator" to animator("numeric" to JsonPrimitive(2))))) else it
        })
        assertFails { prefabFromSceneSubtree(numericIntoSubtree,"a") }
        val numericToRoot=authored.copy(entities=authored.entities.map {
            if(it.key=="outside") it.copy(components=JsonObject(mapOf("Animator" to animator("numeric" to JsonPrimitive(1))))) else it
        })
        assertFails { prefabFromSceneSubtree(numericToRoot,"a") }
        val nested=prefabFromSceneSubtree(authored,"a")
        val link=PrefabInstance("asset","a",nested.prefab).capture(nested.scene)
        val linked=nested.scene.copy(entities=nested.scene.entities+entity("holder"),parents=nested.scene.parents+("a" to "holder"),prefabInstances=listOf(link.copy(parent="holder")))
        assertFails { prefabFromSceneSubtree(linked,"holder") }
        assertFails { prefabFromSceneSubtree(linked,"a/b") }
        assertFails { prefabFromSceneSubtree(authored,"missing") }
    }

    @Test fun linkingIsOneUndoableChangeAndRefusesAStaleScene() {
        val state=SceneWorkspaceState(emptyMap()) { it }
        state.openResolved(authored.serialize(),emptyMap(),java.nio.file.Path.of("scene.json"))
        val captured=state.document()
        val created=prefabFromSceneSubtree(captured,"a")
        val outsideState=state.entities.last()
        state.linkSubtree(captured,created,"asset-id")
        assertEquals(listOf("a","a/b","a/c","outside"),state.entities.map { it.document.key })
        assertEquals("a",state.document().prefabInstances.single().key)
        assertEquals("a",state.selectedKey)
        assertNotSame(outsideState,state.entities.last(),"a changed outside entity gets a fresh state so undo can restore the original")
        state.undoStructure()
        assertEquals(authored.entities.map { it.key },state.entities.map { it.document.key })
        assertTrue(state.document().prefabInstances.isEmpty())
        assertSame(outsideState,state.entities.last())
        state.redoStructure()
        assertEquals(1,state.document().prefabInstances.size)
        state.undoStructure()
        // A committed Inspector edit leaves its text in drafts; that must not block linking.
        state.entities.first().document=state.entities.first().document.copy(components=JsonObject(state.entities.first().document.components+("Audio" to audio(1.0))))
        state.entities.first().editField("Audio",listOf("volume"),JsonPrimitive(1.0),"0.75")
        assertTrue(state.entities.first().drafts.isNotEmpty() && state.entities.first().fieldErrors.isEmpty())
        val drafted=state.document()
        state.linkSubtree(drafted,prefabFromSceneSubtree(drafted,"a"),"asset-id")
        assertEquals(1,state.document().prefabInstances.size)
        state.undoStructure()
        val stale=state.document();state.rename("Changed")
        assertFails { state.linkSubtree(stale,prefabFromSceneSubtree(stale,"a"),"asset-id") }
        assertTrue(state.document().prefabInstances.isEmpty())
    }

    @Test fun applyPromotesComponentEditsKeepsPlacementLocalAndRebasesTheLink() {
        val prefab=PrefabDocument("root",listOf(entity("root","Transform" to placed(0)),entity("child","Audio" to audio(1.0))),mapOf("child" to "root"))
        val bOverrides=buildJsonObject { put("child",buildJsonObject { put("Audio",audio(0.1)) }) }
        val a=prefab.instantiate("a");val b=prefab.instantiate("b",overrides=bOverrides)
        var scene=SceneDocument(key="scene",entities=a.entities+b.entities,parents=a.parents+b.parents,
            prefabInstances=listOf(PrefabInstance("id","a",prefab),PrefabInstance("id","b",prefab,bOverrides)))
        scene=scene.copy(entities=scene.entities.map { when(it.key) {
            "a" -> it.copy(components=JsonObject(it.components+("Transform" to placed(300))))
            "a/child" -> it.copy(components=JsonObject(it.components+("Audio" to audio(0.5))+("Sprite" to buildJsonObject { put("size",8) })))
            else -> it
        } })
        val (applied,rebased)=applyInstanceToPrefab(scene,"a")
        assertEquals(placed(0),applied.entities.first { it.key=="root" }.components["Transform"],"source placement is kept")
        val child=applied.entities.first { it.key=="child" }.components
        assertEquals(audio(0.5),child["Audio"]);assertTrue("Sprite" in child)
        val aLink=rebased.prefabInstances.first { it.key=="a" }
        assertEquals(applied,aLink.source)
        assertEquals(setOf("root"),aLink.overrides.keys,"only placement stays an override")
        // Other instances reconcile: shared edits arrive, explicit overrides survive.
        val updated=rebased.prefabInstances.first { it.key=="b" }.reconcile(rebased,applied)
        val bChild=updated.entities.first { it.key=="b/child" }.components
        assertEquals(audio(0.1),bChild["Audio"]);assertTrue("Sprite" in bChild)
        assertEquals(placed(300),updated.entities.first { it.key=="a" }.components["Transform"])
        assertFails { applyInstanceToPrefab(scene,"a/child") }
    }

    @Test fun refreshKeepsDraftsAndStateOfUnchangedEntities() {
        val prefab=PrefabDocument("root",listOf(entity("root","Audio" to audio(1.0))))
        val state=SceneWorkspaceState(emptyMap()) { it }
        state.insertLinkedPrefab(prefab,"id",emptyMap())
        state.addEntity()
        val other=state.selected!!
        other.document=other.document.copy(components=JsonObject(mapOf("Transform" to buildJsonObject { put("rotation",0.0) })))
        other.editField("Transform",listOf("rotation"),JsonPrimitive(0.0),"-")
        assertEquals("-",other.drafts["Transform/rotation"])
        val link=state.document().prefabInstances.single()
        val changed=prefab.copy(entities=prefab.entities.map { it.copy(components=JsonObject(mapOf("Audio" to audio(0.25)))) })
        val next=link.reconcile(state.document(),changed).copy(prefabInstances=listOf(link.copy(source=changed)))
        state.replaceLinkedScene(next)
        assertSame(other,state.entities.first { it.document.key==other.document.key })
        assertEquals("-",other.drafts["Transform/rotation"])
        assertEquals(audio(0.25),state.entities.first { it.document.key==link.key }.document.components["Audio"])
    }
}
