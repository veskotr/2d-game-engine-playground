package sle.editor.model

import sle.editor.bridge.NativeEngine
import sle.editor.files.PrefabPackages
import sle.editor.viewport.Vec2

import androidx.compose.runtime.*
import kotlinx.serialization.json.*
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID

internal fun canonicalSceneDocument(source: String): String =
    NativeEngine.canonicalScene(source.toByteArray(Charsets.UTF_8)).toString(Charsets.UTF_8)

internal class SceneWorkspaceState(private val catalog: Map<String,ComponentDefinition>,
    private val canonicalize: (String) -> String = ::canonicalSceneDocument) {
    var key by mutableStateOf(UUID.randomUUID().toString())
        private set
    var name by mutableStateOf("New Scene")
        private set
    var entities by mutableStateOf<List<EntityWorkspaceState>>(emptyList())
        private set
    var parents by mutableStateOf<Map<String,String>>(emptyMap())
        private set
    var selectedKey by mutableStateOf<String?>(null)
        private set
    var file by mutableStateOf<Path?>(null)
        private set
    var error by mutableStateOf<String?>(null)
    var message by mutableStateOf<String?>(null)
    private var extras by mutableStateOf<Map<Path,String>>(emptyMap())
    private data class Saved(val document: SceneDocument,val assets: Map<Path,String>)
    private fun snapshot() = Saved(document(),allAssets())
    private var saved by mutableStateOf(snapshot())
    private data class Structure(val entities: List<EntityWorkspaceState>,val selection: String?,val name: String,val parents: Map<String,String>,val poses: Map<String,JsonElement?>,val cameraFlags: Map<String,JsonElement?>,val poseChanges: Set<String> = emptySet(),val cameraChanges: Set<String> = emptySet())
    private var past by mutableStateOf<List<Structure>>(emptyList())
    private var future by mutableStateOf<List<Structure>>(emptyList())
    val selected get() = entities.firstOrNull { it.document.key==selectedKey }
    val dirty get() = snapshot()!=saved || entities.any { it.fieldErrors.isNotEmpty() }
    val canUndoStructure get() = past.isNotEmpty()
    val canRedoStructure get() = future.isNotEmpty()
    fun document() = SceneDocument(key,name,entities.map { it.document },parents)
    fun serialize() = document().serialize()
    fun allAssets() = extras + entities.flatMap { it.assets.entries }.associate { it.key to it.value }
    private fun structure() = Structure(entities,selectedKey,name,parents,entities.associate { it.document.key to it.document.components["Transform"] },entities.associate { it.document.key to (it.document.components["Camera2D"] as? JsonObject)?.get("primary") })
    private fun requireReferences(states: List<EntityWorkspaceState>) {
        val keys=states.map { it.document.key }.toSet()
        for(state in states) {
            val targets=(state.document.components["Animator"] as? JsonObject)?.get("targetEntities") as? JsonObject ?: continue
            require(targets.values.all { value -> value is JsonPrimitive &&
                (if(value.isString) value.content in keys else value.intOrNull?.let { ordinal ->
                    ordinal in 1..states.size && entities.getOrNull(ordinal-1)?.document?.key==states.getOrNull(ordinal-1)?.document?.key
                }==true) }) {
                "This change would invalidate an Animator target; remove its reference or save/reopen to normalize numeric targets first"
            }
        }
    }
    private fun restore(value: Structure) {
        selected?.cancelGesture()
        entities=value.entities;selectedKey=value.selection;name=value.name;parents=value.parents;error=null
    }
    private fun change(action: () -> Unit) {
        selected?.endGesture()
        val before=structure();action()
        val after=structure()
        if (before!=after) {
            val common=before.entities.map { it.document.key }.toSet().intersect(after.entities.map { it.document.key }.toSet())
            past=(past+before.copy(poseChanges=common.filter { before.poses[it]!=after.poses[it] }.toSet(),cameraChanges=common.filter { before.cameraFlags[it]!=after.cameraFlags[it] }.toSet())).takeLast(100);future=emptyList()
        }
        error=null
    }
    fun rename(value: String) = change { name=value }
    fun select(value: String?) { selected?.endGesture();selectedKey=value?.takeIf { key -> entities.any { it.document.key==key } } }
    fun addEntity() = change {
        require(entities.size<MAX_SCENE_ENTITIES) { "Scene editor supports at most $MAX_SCENE_ENTITIES entities" }
        val state=EntityWorkspaceState(catalog)
        entities=entities+state;selectedKey=state.document.key
    }
    fun importPrefab(path: Path) {
        require(entities.size<MAX_SCENE_ENTITIES) { "Scene editor supports at most $MAX_SCENE_ENTITIES entities" }
        val imported=EntityWorkspaceState(catalog).apply { open(path) }
        val copy=copySceneEntity(imported.document,listOf(imported.document.key))
        val state=EntityWorkspaceState(catalog).apply { resetTo(copy,imported.assets) }
        change { entities=entities+state;selectedKey=copy.key }
    }
    private fun subtree(root: String): Set<String> {
        val children=parents.entries.groupBy({it.value},{it.key});val result=mutableSetOf<String>();val queue=java.util.ArrayDeque<String>();queue.add(root)
        while(queue.isNotEmpty()) { val key=queue.removeFirst();if(result.add(key)) children[key]?.forEach(queue::add) };return result
    }
    fun worldDocument(key: String): EntityDocument = entities.first { it.document.key==key }.document.let {
        it.withPose(sceneWorldPose(key,entities.map { state -> state.document },parents))
    }
    fun applyWorldDocument(key: String,value: EntityDocument) {
        val state=entities.first { it.document.key==key }
        val local=parents[key]?.let { ScenePose.from(value).relativeTo(sceneWorldPose(it,entities.map { entity -> entity.document },parents)) } ?: ScenePose.from(value)
        state.applyGizmo(state.document.withPose(local))
    }
    fun reparent(childKey: String,parentKey: String?,keepWorld: Boolean=true) {
        require(entities.any { it.document.key==childKey }) { "Unknown child" }
        val next=parents-childKey+(parentKey?.let { mapOf(childKey to it) } ?: emptyMap())
        validateSceneParents(entities.map { it.document.key },next)
        val state=entities.first { it.document.key==childKey }
        val pose=if(keepWorld) {
            val world=sceneWorldPose(childKey,entities.map { it.document },parents)
            if(parentKey==null) world else world.relativeTo(sceneWorldPose(parentKey,entities.map { it.document },parents))
        } else null
        change { parents=next;pose?.let { if("Transform" in state.document.components || it!=ScenePose()) state.applyStructure(state.document.withPose(it)) } }
    }
    fun addChild() {
        require(entities.size<MAX_SCENE_ENTITIES) { "Scene editor supports at most $MAX_SCENE_ENTITIES entities" }
        val parent=selectedKey;val child=EntityWorkspaceState(catalog)
        change { entities=entities+child;selectedKey=child.document.key;if(parent!=null) parents=parents+(child.document.key to parent) }
    }
    fun addCamera(asChild: Boolean=false) {
        require(entities.size<MAX_SCENE_ENTITIES) { "Scene entity limit reached" }
        val parent=if(asChild) selectedKey else null
        val state=EntityWorkspaceState(catalog)
        val camera=catalog["Camera2D"]?.defaults ?: buildJsonObject { put("enabled",true);put("primary",true);put("viewSize",buildJsonObject { put("x",480);put("y",270) });put("zoom",1) }
        state.document=state.document.copy(name="Camera",components=JsonObject(state.document.components+("Camera2D" to camera))).withPose(ScenePose(position=if(parent==null) Vec2(240.0,135.0) else Vec2(0.0,0.0)))
        change { entities=entities+state;selectedKey=state.document.key;if(parent!=null) parents=parents+(state.document.key to parent) }
    }
    val activeCamera get()=entities.firstOrNull { val fields=it.document.components["Camera2D"] as? JsonObject;fields!=null && fields["enabled"]?.jsonPrimitive?.booleanOrNull!=false && fields["primary"]?.jsonPrimitive?.booleanOrNull!=false }
    fun setActiveCamera(key: String) {
        require(entities.any { it.document.key==key && "Camera2D" in it.document.components }) { "Entity has no camera" }
        change { entities.forEach { state -> (state.document.components["Camera2D"] as? JsonObject)?.let { camera -> state.applyStructure(state.document.copy(components=JsonObject(state.document.components+("Camera2D" to JsonObject(camera+("primary" to JsonPrimitive(state.document.key==key)))))) ) } } }
    }
    fun duplicateSelected() {
        val root=selectedKey ?: return;val copied=subtree(root)
        require(entities.size+copied.size<=MAX_SCENE_ENTITIES) { "Scene entity limit reached" }
        val remap=copied.associateWith { UUID.randomUUID().toString() };val ordinalKeys=entities.map { it.document.key }
        val copies=entities.filter { it.document.key in copied }.map { source ->
            var document=source.document.copy(key=remap.getValue(source.document.key),name=source.document.name+" Copy")
            val animator=document.components["Animator"] as? JsonObject;val targets=animator?.get("targetEntities") as? JsonObject
            if(targets!=null) document=document.copy(components=JsonObject(document.components+("Animator" to JsonObject(animator+("targetEntities" to JsonObject(targets.mapValues { (_,value) ->
                val key=(value as? JsonPrimitive)?.let { if(it.isString) it.content else it.intOrNull?.let { ordinal -> ordinalKeys.getOrNull(ordinal-1) } }
                key?.let { JsonPrimitive(remap[it] ?: it) } ?: value
            }))))))
            EntityWorkspaceState(catalog).apply { resetTo(document,source.assets) }
        }
        change { entities=entities+copies;parents=parents+copied.mapNotNull { child -> parents[child]?.let { remap.getValue(child) to (remap[it] ?: it) } };selectedKey=remap[root] }
    }
    fun removeSelected() {
        val removed=selectedKey?.let(::subtree) ?: return
        val remaining=entities.filterNot { it.document.key in removed };requireReferences(remaining)
        change { entities=remaining;parents=parents.filterKeys { it !in removed };selectedKey=entities.firstOrNull()?.document?.key }
    }
    private fun restoreOperation(target: Structure) {
        selected?.cancelGesture()
        for(state in target.entities) {
            val id=state.document.key
            if(id in target.poseChanges) {
                val transform=target.poses[id];state.applyStructure(state.document.copy(components=JsonObject(if(transform==null) state.document.components-"Transform" else state.document.components+("Transform" to transform))))
            }
            if(id in target.cameraChanges) (state.document.components["Camera2D"] as? JsonObject)?.let { camera ->
                val flag=target.cameraFlags[id];state.applyStructure(state.document.copy(components=JsonObject(state.document.components+("Camera2D" to JsonObject(if(flag==null) camera-"primary" else camera+("primary" to flag))))))
            }
        }
        restore(target)
    }
    fun undoStructure() { if(past.isNotEmpty()) { val previous=past.last();requireReferences(previous.entities);selected?.cancelGesture();val current=structure();future=future+current.copy(poseChanges=previous.poseChanges,cameraChanges=previous.cameraChanges);past=past.dropLast(1);restoreOperation(previous) } }
    fun redoStructure() { if(future.isNotEmpty()) { val next=future.last();requireReferences(next.entities);selected?.cancelGesture();val current=structure();past=(past+current.copy(poseChanges=next.poseChanges,cameraChanges=next.cameraChanges)).takeLast(100);future=future.dropLast(1);restoreOperation(next) } }
    fun newScene() {
        selected?.cancelGesture();key=UUID.randomUUID().toString();name="New Scene";entities=emptyList();parents=emptyMap();selectedKey=null
        extras=emptyMap();file=null;past=emptyList();future=emptyList();error=null;message=null;saved=snapshot()
    }
    fun open(path: Path) {
        val packet=if (path.toString().endsWith(".slescene",true)) PrefabPackages.openScene(path) else null
        val loaded=SceneDocument.parse(canonicalize(packet?.document?.toString() ?: Files.readString(path)))
        val assets=packet?.assets ?: emptyMap()
        val used=mutableSetOf<Path>()
        val states=loaded.entities.map { entity ->
            val text=entity.serialize()
            val own=assets.filterKeys { text.contains(it.toString().replace('\\','/')) }.also { used.addAll(it.keys) }
            EntityWorkspaceState(catalog).apply { resetTo(entity,own) }
        }
        selected?.cancelGesture();key=loaded.key;name=loaded.name;entities=states;parents=loaded.parents;selectedKey=states.firstOrNull()?.document?.key
        extras=assets.filterKeys { it !in used };file=path;past=emptyList();future=emptyList();error=null;message="Opened ${path.fileName}";saved=snapshot()
    }
    fun save(path: Path) {
        require(path.toString().endsWith(".slescene",true)) { "Save scenes with the .slescene extension" }
        require(entities.all { it.fieldErrors.isEmpty() }) { "Finish invalid fields in every entity before saving" }
        val source=canonicalize(serialize())
        PrefabPackages.saveScene(source,allAssets(),path) { canonicalize(it) }
        file=path;saved=snapshot();error=null;message="Saved ${path.fileName}"
    }
}
