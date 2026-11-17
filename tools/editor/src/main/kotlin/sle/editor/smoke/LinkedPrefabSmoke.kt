package sle.editor.smoke

import kotlinx.serialization.json.*
import sle.editor.bridge.NativeEngine
import sle.editor.model.*
import sle.editor.project.*
import sle.editor.project.assets.AssetIdentity
import java.nio.file.Files
import java.nio.file.Path

/** Uses the real JNI codecs/project loader; also checks portable subtree packages. */
internal fun runLinkedPrefabSmoke() {
    val root=Files.createTempDirectory(Path.of(System.getProperty("sle.authoringReport")).parent,"linked-prefab-")
    val tree=PrefabDocument.parse(canonicalPrefabDocument("""{"root":"actor","entities":[
        {"key":"actor","name":"Actor","components":{"Transform":{"position":[1,2]},"Audio":{"volume":0.8}}},
        {"key":"child","parent":"actor","components":{"Animator":{"targetEntities":{"root":1,"self":"child"}}}}
    ]}"""))
    val author=EntityWorkspaceState(readComponentCatalog())
    author.openResolved(tree.serialize(),emptyMap(),root.resolve("source.prefab.json"))
    val packagePath=root.resolve("tree.sleprefab")
    author.save(packagePath);author.newDocument();author.open(packagePath)
    check(author.prefabDocument().entities.size==2 && !author.dirty)
    val exported=root.resolve("exported.prefab.json")
    author.save(exported);author.open(exported)
    check(author.prefabDocument().parents["child"]=="actor")
    val project=ProjectFiles.create(root.resolve("Game"),"Game")
    val store=ProjectDocuments(project)
    val asset=store.save(project.assets.resolve("Prefabs/Actor.prefab.json"),ProjectDocumentKind.Prefab,tree.serialize(),emptyMap(),null) { canonicalPrefabDocument(it) }
    val id=AssetIdentity.read(AssetIdentity.sidecar(asset.path))
    val state=SceneWorkspaceState(readComponentCatalog())
    state.insertLinkedPrefab(tree,id,emptyMap());val first=state.selectedKey!!
    state.duplicateSelected();val second=state.selectedKey!!
    val main=store.open(project.assets.resolve("Scenes/Main.scene.json"))
    var saved=store.saveLinked(main.path,state.document(),state.document().prefabInstances,emptyMap(),main) { canonicalSceneDocument(it) }
    state.savedProject(saved)
    check(!state.dirty)
    state.openProject(store.open(saved.path))
    check(state.document().prefabInstances.size==2)
    val changed=tree.copy(entities=tree.entities.map { if(it.key==tree.root) it.setValue("Audio",listOf("volume"),JsonPrimitive(0.25)) else it })
    val update=store.updatePrefab(asset.path,changed,emptyMap(),asset,state.document(),{ canonicalPrefabDocument(it) },{ canonicalSceneDocument(it) })
    state.replaceLinkedScene(update.scene!!)
    check(state.dirty)
    saved=store.saveLinked(saved.path,state.document(),state.document().prefabInstances,emptyMap(),saved) { canonicalSceneDocument(it) }
    state.savedProject(saved);check(!state.dirty)
    val prepared=PreparedProject.open(project)
    try {
        val native=SceneDocument.parse(prepared.source)
        check(native.entities.size==4)
        for(key in listOf(first,second)) {
            val entity=native.entities.first { it.key==key }
            check(entity.components["Audio"]!!.jsonObject["volume"]!!.jsonPrimitive.double==0.25)
            val targets=native.entities.first { it.key=="$key/child" }.components["Animator"]!!.jsonObject["targetEntities"]!!.jsonObject
            check(targets["root"]==JsonPrimitive(key) && targets["self"]==JsonPrimitive("$key/child"))
        }
    } finally { NativeEngine.releaseProject(prepared.handle) }
    println("LINKED_PREFAB_AUTHORING_SMOKE_PASS file=${saved.path}")
}
