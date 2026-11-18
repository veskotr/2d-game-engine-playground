package sle.editor.project

import sle.editor.model.*
import sle.editor.project.assets.AssetIdentity
import kotlinx.serialization.json.*
import java.nio.file.Files
import kotlin.test.*

class LinkedPrefabPersistenceTest {
    @Test fun linksSurviveSaveRenameReloadAndRefreshWithoutWritingTheScene() {
        val project=ProjectFiles.create(Files.createTempDirectory("linked-project").resolve("Game"),"Game")
        val store=ProjectDocuments(project)
        val path=project.assets.resolve("Prefabs/Actor.prefab.json")
        val prefab=PrefabDocument.parse("""{"root":"actor","entities":[{"key":"actor","name":"Actor","components":{"Audio":{"volume":1}}},{"key":"child","parent":"actor","components":{}}]}""")
        val asset=store.save(path,ProjectDocumentKind.Prefab,prefab.serialize(),emptyMap(),null) {}
        val id=AssetIdentity.read(AssetIdentity.sidecar(path))
        val a=prefab.instantiate("a");val b=prefab.instantiate("b")
        val scene=SceneDocument(key="scene",entities=a.entities+b.entities,parents=a.parents+b.parents)
        val saved=store.saveLinked(project.assets.resolve("Scenes/Linked.scene.json"),scene,listOf(PrefabInstance(id,"a",prefab),PrefabInstance(id,"b",prefab)),emptyMap(),null) {}
        val before=Files.readString(saved.path)
        assertTrue(before.contains("project-asset://$id"))
        assertFalse(Json.parseToJsonElement(before).jsonObject["document"]!!.jsonObject["entities"]!!.jsonArray.isNotEmpty())
        val changed=prefab.copy(entities=prefab.entities.map { if(it.key=="actor") it.copy(components=buildJsonObject { put("Audio",buildJsonObject { put("volume",0.25) }) }) else it })
        store.save(path,ProjectDocumentKind.Prefab,changed.serialize(),emptyMap(),asset) {}
        val moved=path.resolveSibling("Renamed.prefab.json")
        Files.move(path,moved);Files.move(AssetIdentity.sidecar(path),AssetIdentity.sidecar(moved))
        val opened=store.open(saved.path)
        assertEquals(2,opened.prefabInstances.size)
        val restored=SceneDocument.parse(opened.source)
        assertEquals(4,restored.entities.size)
        assertEquals(JsonPrimitive(0.25),restored.entities[0].components["Audio"]!!.jsonObject["volume"])
        assertEquals(before,Files.readString(saved.path))
        Files.delete(moved)
        assertFails { store.open(saved.path) }
    }
    @Test fun authoredChildrenCanReferenceLinkedDescendantsOnReload() {
        val project=ProjectFiles.create(Files.createTempDirectory("linked-parent").resolve("Game"),"Game")
        val store=ProjectDocuments(project)
        val prefab=PrefabDocument("root",listOf(EntityDocument(key="root"),EntityDocument(key="child")),mapOf("child" to "root"))
        val asset=store.save(project.assets.resolve("Prefabs/Actor.prefab.json"),ProjectDocumentKind.Prefab,prefab.serialize(),emptyMap(),null) {}
        val id=AssetIdentity.read(AssetIdentity.sidecar(asset.path))
        val expanded=prefab.instantiate("a")
        val scene=expanded.copy(entities=expanded.entities+EntityDocument(key="attached"),parents=expanded.parents+("attached" to "a/child"))
        val saved=store.saveLinked(project.assets.resolve("Scenes/Attached.scene.json"),scene,listOf(PrefabInstance(id,"a",prefab)),emptyMap(),null) {}
        assertEquals("a/child",SceneDocument.parse(store.open(saved.path).source).parents["attached"])
    }
    @Test fun legacyExpandedSceneStaysIndependentOfMatchingPrefabUpdates() {
        val project=ProjectFiles.create(Files.createTempDirectory("linked-legacy").resolve("Game"),"Game")
        val store=ProjectDocuments(project)
        val path=project.assets.resolve("Prefabs/Enemy.prefab.json")
        val prefab=PrefabDocument("enemy",listOf(
            EntityDocument(key="enemy",components=buildJsonObject { put("Audio",buildJsonObject { put("volume",1) }) }),
            EntityDocument(key="weapon")),mapOf("weapon" to "enemy"))
        val asset=store.save(path,ProjectDocumentKind.Prefab,prefab.serialize(),emptyMap(),null) {}
        val legacy="""{"key":"scene","entities":[{"key":"enemy","components":{"Audio":{"volume":1}}},{"key":"enemy/weapon","parent":"enemy","components":{}}]}"""
        val scenePath=project.assets.resolve("Scenes/Legacy.scene.json")
        store.save(scenePath,ProjectDocumentKind.Scene,legacy,emptyMap(),null) {}
        val opened=store.open(scenePath)
        assertTrue(opened.prefabInstances.isEmpty())
        val restored=SceneDocument.parse(opened.source)
        assertEquals("enemy",restored.parents["enemy/weapon"])
        val changed=prefab.copy(entities=prefab.entities.map { it.removeComponent("Audio") })
        val update=store.updatePrefab(path,changed,emptyMap(),asset,restored,{}, {})
        assertNull(update.scene,"Unlinked entities sharing entity template keys must not be reconciled")
        assertTrue("Audio" in SceneDocument.parse(store.open(scenePath).source).entities.first().components)
    }
    @Test fun subtreeSavedAsPrefabIsLinkedAndSurvivesSceneSaveAndReopen() {
        val project=ProjectFiles.create(Files.createTempDirectory("linked-create").resolve("Game"),"Game")
        val store=ProjectDocuments(project)
        val state=SceneWorkspaceState(emptyMap()) { it }
        val authored=SceneDocument(key="scene",entities=listOf(
            EntityDocument(key="a",name="Actor",components=buildJsonObject { put("Audio",buildJsonObject { put("volume",0.5) }) }),
            EntityDocument(key="b",name="Hand"),EntityDocument(key="watcher",components=buildJsonObject {
                put("Animator",buildJsonObject { put("targetEntities",buildJsonObject { put("hand","b") }) })
            })),parents=mapOf("b" to "a"))
        state.openResolved(authored.serialize(),emptyMap(),project.assets.resolve("Scenes/Main.scene.json"))
        val captured=state.document()
        val created=prefabFromSceneSubtree(captured,"a")
        val folder=project.assets.resolve("Prefabs")
        val path=uniquePrefabPath(folder,"Actor")
        assertEquals("Actor.prefab.json",path.fileName.toString())
        val saved=store.save(path,ProjectDocumentKind.Prefab,created.prefab.serialize(),emptyMap(),null) {}
        assertEquals("Actor 2.prefab.json",uniquePrefabPath(folder,"Actor").fileName.toString())
        val id=AssetIdentity.read(AssetIdentity.sidecar(saved.path))
        state.linkSubtree(captured,created.withSource(PrefabDocument.parse(saved.source)),id)
        val document=state.document()
        val scenePath=project.assets.resolve("Scenes/Linked.scene.json")
        val written=store.saveLinked(scenePath,document,document.prefabInstances,emptyMap(),null) {}
        val persisted=Json.parseToJsonElement(Files.readString(written.path)).jsonObject["document"]!!.jsonObject
        assertEquals(listOf("watcher"),persisted["entities"]!!.jsonArray.map { it.jsonObject["key"]!!.jsonPrimitive.content })
        assertEquals("project-asset://$id",persisted["prefabs"]!!.jsonArray.single().jsonObject["file"]!!.jsonPrimitive.content)
        val reopened=store.open(scenePath)
        val restored=SceneDocument.parse(reopened.source)
        assertEquals(setOf("a","a/b","watcher"),restored.entities.map { it.key }.toSet())
        assertEquals("a",restored.parents["a/b"])
        assertEquals(JsonPrimitive("a/b"),restored.entities.first { it.key=="watcher" }.components["Animator"]!!.jsonObject["targetEntities"]!!.jsonObject["hand"])
        assertEquals("a",reopened.prefabInstances.single().key)
    }
    @Test fun applyUsesTheStagedUpdateAndRefreshesOtherInstancesKeepingTheirOverrides() {
        val project=ProjectFiles.create(Files.createTempDirectory("linked-apply").resolve("Game"),"Game")
        val store=ProjectDocuments(project)
        val path=project.assets.resolve("Prefabs/Actor.prefab.json")
        val prefab=PrefabDocument("root",listOf(EntityDocument(key="root"),
            EntityDocument(key="child",components=buildJsonObject { put("Audio",buildJsonObject { put("volume",1);put("loop",false) }) })),mapOf("child" to "root"))
        val asset=store.save(path,ProjectDocumentKind.Prefab,prefab.serialize(),emptyMap(),null) {}
        val id=AssetIdentity.read(AssetIdentity.sidecar(path))
        val bOverrides=buildJsonObject { put("child",buildJsonObject { put("Audio",buildJsonObject { put("volume",0.1) }) }) }
        val a=prefab.instantiate("a");val b=prefab.instantiate("b",overrides=bOverrides)
        val edited=SceneDocument(key="scene",entities=(a.entities+b.entities).map {
            if(it.key=="a/child") it.setValue("Audio",listOf("loop"),JsonPrimitive(true)) else it
        },parents=a.parents+b.parents,prefabInstances=listOf(PrefabInstance(id,"a",prefab),PrefabInstance(id,"b",prefab,bOverrides)))
        val (applied,rebased)=applyInstanceToPrefab(edited,"a")
        assertEquals(asset.revision,store.requireLinkedSource(path,prefab) { it }.revision)
        // Newer asset content (another apply, an external edit) must never be overwritten by a stale link.
        val newer=prefab.copy(entities=prefab.entities.map { if(it.key=="root") it.copy(components=buildJsonObject { put("Tag",buildJsonObject { put("v",1) }) }) else it })
        val newerSave=store.save(path,ProjectDocumentKind.Prefab,newer.serialize(),emptyMap(),asset) {}
        assertFailsWith<IllegalArgumentException> { store.requireLinkedSource(path,prefab) { it } }
        val current=store.save(path,ProjectDocumentKind.Prefab,prefab.serialize(),emptyMap(),newerSave) {}
        val bytes=Files.readAllBytes(path)
        assertFails { store.updatePrefab(path,applied,emptyMap(),current,rebased,{}, { error("Invalid") }) }
        assertContentEquals(bytes,Files.readAllBytes(path),"rejected apply keeps the asset")
        val update=store.updatePrefab(path,applied,emptyMap(),current,rebased,{}, {})
        val scene=update.scene ?: rebased
        val bAudio=scene.entities.first { it.key=="b/child" }.components["Audio"]!!.jsonObject
        assertEquals(JsonPrimitive(true),bAudio["loop"]);assertEquals(JsonPrimitive(0.1),bAudio["volume"])
        assertEquals(applied,PrefabDocument.parse(store.open(path).source))
        assertTrue(scene.prefabInstances.all { it.source==applied })
        val written=store.saveLinked(project.assets.resolve("Scenes/Applied.scene.json"),scene,scene.prefabInstances,emptyMap(),null) {}
        val declarations=Json.parseToJsonElement(Files.readString(written.path)).jsonObject["document"]!!.jsonObject["prefabs"]!!.jsonArray
        assertEquals(setOf("root"),declarations.first { it.jsonObject["key"]!!.jsonPrimitive.content=="a" }.jsonObject["overrides"]!!.jsonObject.keys,
            "applied edits are no longer instance overrides")
    }
    @Test fun sceneUuidCannotBeFollowedAsAPrefabOrRecurseIntoItself() {
        val project=ProjectFiles.create(Files.createTempDirectory("linked-recursion").resolve("Game"),"Game")
        val store=ProjectDocuments(project)
        val main=store.open(project.assets.resolve("Scenes/Main.scene.json"))
        val id=AssetIdentity.read(AssetIdentity.sidecar(main.path))
        val invalid="""{"key":"scene","entities":[],"prefabs":[{"file":"project-asset://$id","key":"recursive"}]}"""
        store.save(main.path,ProjectDocumentKind.Scene,invalid,emptyMap(),main) {}
        assertFailsWith<IllegalArgumentException> { store.open(main.path) }
    }
    @Test fun stagedPrefabUpdateValidatesEveryInstanceBeforeReplacingTheAsset() {
        val project=ProjectFiles.create(Files.createTempDirectory("linked-update").resolve("Game"),"Game")
        val store=ProjectDocuments(project)
        val path=project.assets.resolve("Prefabs/Actor.prefab.json")
        val prefab=PrefabDocument("root",listOf(EntityDocument(key="root",components=buildJsonObject { put("Audio",buildJsonObject { put("volume",1) }) })))
        val asset=store.save(path,ProjectDocumentKind.Prefab,prefab.serialize(),emptyMap(),null) {}
        val id=AssetIdentity.read(AssetIdentity.sidecar(path))
        val initial=prefab.instantiate("a").copy(prefabInstances=listOf(PrefabInstance(id,"a",prefab)))
        val changed=prefab.copy(entities=prefab.entities.map { it.removeComponent("Audio") })
        val bytes=Files.readAllBytes(path)
        assertFails { store.updatePrefab(path,changed,emptyMap(),asset,initial,{}, { error("Invalid reconciled scene") }) }
        assertContentEquals(bytes,Files.readAllBytes(path))
        assertTrue("Audio" in initial.entities.single().components)
        val result=store.updatePrefab(path,changed,emptyMap(),asset,initial,{}, {})
        assertFalse("Audio" in result.scene!!.entities.single().components)
        assertEquals(changed,result.scene.prefabInstances.single().source)
        assertEquals(changed,PrefabDocument.parse(store.open(path).source))
        assertFails { store.updatePrefab(path,prefab,emptyMap(),asset,initial,{}, {}) }
    }
    @Test fun rejectedSaveRetainsAssetAndSceneBytes() {
        val project=ProjectFiles.create(Files.createTempDirectory("linked-failure").resolve("Game"),"Game")
        val store=ProjectDocuments(project)
        val path=project.assets.resolve("Prefabs/Actor.prefab.json")
        val prefab=PrefabDocument("root",listOf(EntityDocument(key="root")))
        val saved=store.save(path,ProjectDocumentKind.Prefab,prefab.serialize(),emptyMap(),null) {}
        val bytes=Files.readAllBytes(path)
        assertFails { store.save(path,ProjectDocumentKind.Prefab,prefab.serialize(),emptyMap(),saved) { error("Rejected") } }
        assertContentEquals(bytes,Files.readAllBytes(path))
    }
}
