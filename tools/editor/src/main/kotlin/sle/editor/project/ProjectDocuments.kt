package sle.editor.project

import kotlinx.serialization.json.*
import sle.editor.files.PrefabPackages
import sle.editor.model.*
import sle.editor.project.assets.*
import java.nio.file.*
import java.nio.file.StandardCopyOption.*
import java.security.MessageDigest

internal enum class ProjectDocumentKind(val format: String,val suffix: String) {
    Scene("sle-project-scene",".scene.json"), Prefab("sle-project-prefab",".prefab.json")
}
internal data class OpenedProjectDocument(val path: Path,val kind: ProjectDocumentKind,val source: String,
    val assets: Map<Path,String>,val revision: String,val prefabInstances: List<PrefabInstance> = emptyList())

internal data class PrefabUpdate(val saved: OpenedProjectDocument,val scene: SceneDocument?)

internal data class AdoptedAssets(val source: String,val assets: Map<Path,String>,val moved: Map<Path,Path>)

/** Portable source persistence. Native codecs only see the resolved, ordinary document. */
internal class ProjectDocuments(val project: OpenedProject) {
    private val prefix="project-asset://"
    private fun scan()=AssetScanner.scan(project.root,project.assets)
    private fun target(path: Path): Path {
        val root=project.assets.toAbsolutePath().normalize()
        val absolute=path.toAbsolutePath().normalize()
        require(absolute.startsWith(root) && absolute!=root) { "Save project documents beneath Assets" }
        return assetPath(project.root,project.assets,root.relativize(absolute).toString().replace('\\','/'))
    }
    private fun fingerprint(bytes: ByteArray)=MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    private fun read(path: Path): ByteArray {
        require(Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS) && Files.size(path)<=16*1024*1024) { "Missing or oversized project document: $path" }
        return Files.newInputStream(path).use { it.readNBytes(16*1024*1024+1) }.also { require(it.size<=16*1024*1024) { "Project document exceeds 16 MiB" } }
    }
    // Only component asset fields are interpreted. Names, script variables and entity keys are data.
    private fun references(document: JsonObject,resolve: (String)->String): JsonObject {
        fun fields(value: JsonElement,field: String=""): JsonElement = when(value) {
            is JsonObject -> if(field=="variables" || field == "targetEntities" || field == "parameters") value else JsonObject(value.mapValues { (key,child) -> fields(child,if(field=="stateClipMap") "clipAsset" else key) })
            is JsonArray -> JsonArray(value.map { fields(it,field) })
            is JsonPrimitive -> if(value.isString && value.content.isNotBlank() && PrefabPackages.isAssetField(field)) JsonPrimitive(resolve(value.content)) else value
        }
        return JsonObject(document.mapValues { (key,value) -> if(key=="entities") JsonArray(value.jsonArray.map { entity ->
            JsonObject(entity.jsonObject.mapValues { (field,child) -> if(field=="components") fields(child) else child })
        }) else if(key=="prefabs") JsonArray(value.jsonArray.map { item ->
            val instance=item.jsonObject
            JsonObject(instance.mapValues { (field,child) -> when(field) {
                "file" -> JsonPrimitive(resolve(child.jsonPrimitive.content))
                "overrides" -> fields(child)
                else -> child
            } })
        }) else value })
    }
    fun referencedAssetIds(document: JsonObject): Set<String> = buildSet {
        references(document) { text ->
            if (text.startsWith(prefix)) add(text.removePrefix(prefix))
            text
        }
    }

    private fun resolve(snapshot: AssetSnapshot,id: String): Path {
        val relative=snapshot.pathsById[id] ?: error("Missing or ambiguous project asset UUID: $id")
        return assetPath(project.root,project.assets,relative).also { require(Files.isRegularFile(it,LinkOption.NOFOLLOW_LINKS)) { "Missing asset: $relative" } }
    }
    private fun assetIdFor(snapshot: AssetSnapshot, root: Path, path: Path): String {
        val real=path.toRealPath()
        require(real.startsWith(root)) { "Asset is outside this project; import it first: $path" }
        val relative=root.relativize(real).toString().replace('\\','/')
        assetPath(project.root,project.assets,relative)
        return snapshot.idsByPath[relative] ?: error("Missing or ambiguous asset identity: $relative")
    }

    /** Resolve a property value using the same registered identities as project save. */
    fun resolvePropertyAsset(text: String): String = synchronized(AssetIdentity) {
        require(!text.startsWith("asset://")) { "Unresolved package reference: $text" }
        val snapshot = AssetScanner.scan(project.root, project.assets, registerMissing = false)
        val id = if (text.startsWith(prefix)) text.removePrefix(prefix)
            else assetIdFor(snapshot, assetPath(project.root,project.assets), Path.of(text))
        resolve(snapshot,id).toString().replace('\\','/')
    }

    /** Current location of a project asset; links follow moves/renames through the UUID. */
    fun pathOf(id: String): Path = synchronized(AssetIdentity) { resolve(scan(),id) }
    fun open(path: Path): OpenedProjectDocument = open(path,null)
    /** Apply rebuilds the complete prefab from a link's [source], so the asset must still contain
     * exactly that source; otherwise newer asset content would be silently overwritten. */
    fun requireLinkedSource(path: Path,source: PrefabDocument,canonical: (String)->String): OpenedProjectDocument {
        val expected=open(path)
        require(PrefabDocument.parse(canonical(expected.source))==PrefabDocument.parse(canonical(source.serialize()))) {
            "${path.fileName} changed since this scene loaded it; reopen the scene, then apply again"
        }
        return expected
    }
    private fun open(path: Path,expectedKind: ProjectDocumentKind?): OpenedProjectDocument = synchronized(AssetIdentity) {
        val file=target(path);val bytes=read(file);val envelope=Json.parseToJsonElement(bytes.toString(Charsets.UTF_8)).jsonObject
        require(envelope.keys==setOf("format","version","document","assets")) { "Unsupported project document fields" }
        val kind=ProjectDocumentKind.entries.firstOrNull { it.format==envelope["format"]?.jsonPrimitive?.content } ?: error("Not a project scene or entity template; use standalone Open for packages")
        require(expectedKind==null || kind==expectedKind) { "Instance asset is not an entity template" }
        require(envelope["version"]?.jsonPrimitive?.intOrNull==1) { "Unsupported project document version" }
        val snapshot=scan()
        fun pathFor(text: String): String {
            require(text.startsWith(prefix)) { "Project asset field must contain a UUID reference: $text" }
            return resolve(snapshot,text.removePrefix(prefix)).toString().replace('\\','/')
        }
        var document=references(envelope.getValue("document").jsonObject,::pathFor)
        val assets=envelope.getValue("assets").jsonArray.associate { value ->
            val asset=resolve(snapshot,value.jsonPrimitive.content);asset to asset.fileName.toString()
        }
        val links=mutableListOf<PrefabInstance>()
        if(kind==ProjectDocumentKind.Scene && "prefabs" in document) {
            val raw=document.getValue("prefabs").jsonArray
            require(raw.size<=MAX_SCENE_ENTITIES) { "Too many entity template instances" }
            val expandedEntities=document.getValue("entities").jsonArray.toMutableList()
            for(item in raw) {
                val declaration=item.jsonObject
                require(declaration.keys.all { it in setOf("file","key","parent","overrides") }) { "Unsupported entity template instance field" }
                val prefabPath=Path.of(declaration.getValue("file").jsonPrimitive.content)
                val opened=open(prefabPath,ProjectDocumentKind.Prefab)
                require(opened.kind==ProjectDocumentKind.Prefab) { "Instance asset is not an entity template" }
                val prefab=PrefabDocument.parse(opened.source)
                val id=AssetIdentity.read(AssetIdentity.sidecar(prefabPath))
                val link=PrefabInstance(id,declaration.getValue("key").jsonPrimitive.content,prefab,
                    declaration["overrides"]?.jsonObject ?: JsonObject(emptyMap()),declaration["parent"]?.jsonPrimitive?.content)
                require(expandedEntities.size+prefab.entities.size<=MAX_SCENE_ENTITIES) { "Too many scene entities after entity template expansion" }
                val expanded=prefab.instantiate(link.key,link.parent,link.overrides)
                expandedEntities+=expanded.entities.map { entity ->
                    val json=Json.parseToJsonElement(entity.serialize()).jsonObject.getValue("entities").jsonArray.single().jsonObject
                    JsonObject(json+(expanded.parents[entity.key]?.let { mapOf("parent" to JsonPrimitive(it)) } ?: emptyMap()))
                }
                links.add(link)
            }
            val scene=SceneDocument.parse(JsonObject((document-"prefabs")+("entities" to JsonArray(expandedEntities))).toString())
            scene.serialize() // Validate cross-instance parents and key collisions before publication.
            document=Json.parseToJsonElement(scene.serialize()).jsonObject
        }
        OpenedProjectDocument(file,kind,documentJson.encodeToString(JsonElement.serializer(),document),assets,fingerprint(bytes),links)
    }
    /** Validate and reconcile immutable snapshots before committing the asset. Callers publish
     * [PrefabUpdate.scene] only if their scene revision still matches the captured input. */
    fun updatePrefab(path: Path,prefab: PrefabDocument,assets: Map<Path,String>,expected: OpenedProjectDocument?,
        scene: SceneDocument?,validatePrefab: (String)->Unit,validateScene: (String)->Unit): PrefabUpdate = synchronized(AssetIdentity) {
        val file=target(path)
        val id=if(Files.exists(file)) AssetIdentity.read(AssetIdentity.sidecar(file)) else null
        var next=scene
        if(scene!=null && id!=null) {
            val affected=scene.prefabInstances.filter { it.assetId==id }
            val captured=affected.associate { it.key to it.copy(parent=scene.parents[it.key]).capture(scene) }
            for(link in affected) next=link.copy(parent=scene.parents[link.key]).reconcile(next!!,prefab)
            next=next.copy(prefabInstances=scene.prefabInstances.map { link ->
                captured[link.key]?.copy(source=prefab) ?: link
            })
            validateScene(next.serialize())
        }
        val saved=save(path,ProjectDocumentKind.Prefab,prefab.serialize(),assets,expected,validatePrefab)
        PrefabUpdate(saved,next?.takeIf { scene!=null && it!=scene })
    }

    /** Store declarations, never frozen expanded copies; all validation precedes replacement. */
    fun saveLinked(path: Path,scene: SceneDocument,instances: List<PrefabInstance>,assets: Map<Path,String>,
        expected: OpenedProjectDocument?,validate: (String)->Unit): OpenedProjectDocument = synchronized(AssetIdentity) {
        val links=instances.map { it.copy(parent=scene.parents[it.key]).capture(scene) }
        require(links.map { it.key }.distinct().size==links.size) { "Duplicate entity template instance key" }
        val owned=links.flatMap { it.entityKeys }
        require(owned.distinct().size==owned.size) { "Overlapping entity template instances" }
        val snapshot=scan()
        val expanded=scene.serialize()
        val ordinary=Json.parseToJsonElement(expanded).jsonObject
        val portable=JsonObject(ordinary+mapOf(
            "entities" to JsonArray(ordinary.getValue("entities").jsonArray.filterNot { it.jsonObject.getValue("key").jsonPrimitive.content in owned }),
            "prefabs" to JsonArray(links.map { link -> buildJsonObject {
                resolve(snapshot,link.assetId)
                put("file",prefix+link.assetId);put("key",link.key)
                link.parent?.let { put("parent",it) };put("overrides",link.overrides)
            } })
        ))
        val saved=save(path,ProjectDocumentKind.Scene,portable.toString(),assets,expected) { validate(expanded) }
        saved.copy(source=expanded,prefabInstances=links)
    }

    /** Copies asset files referenced from outside the project (package extractions, external
     * picks) into Assets and points the document at the copies. [moved] maps old to new paths. */
    fun adoptExternalAssets(source: String,assets: Map<Path,String>): AdoptedAssets = synchronized(AssetIdentity) {
        val adoption=ProjectAssetAdoption(project.root,project.assets)
        fun normalized(text: String): Path? = if(text.startsWith(prefix) || text.startsWith("asset://")) null
            else runCatching { Path.of(text).toAbsolutePath().normalize() }.getOrNull()
        val document=Json.parseToJsonElement(source).jsonObject
        val referenced=mutableListOf<Path>()
        references(document) { text -> normalized(text)?.let(referenced::add);text }
        val names=assets.mapKeys { it.key.toAbsolutePath().normalize() }
        val external=(referenced+names.keys).distinct().filter(adoption::outside)
        if(external.isEmpty()) return AdoptedAssets(source,assets,emptyMap())
        val moved=external.associateWith { adoption.adopt(it,names[it]) }
        val rewritten=references(document) { text -> normalized(text)?.let(moved::get)?.toString()?.replace('\\','/') ?: text }
        AdoptedAssets(documentJson.encodeToString(JsonElement.serializer(),rewritten),
            assets.entries.associate { (path,name) -> (moved[path.toAbsolutePath().normalize()] ?: path) to name },moved)
    }
    fun save(path: Path,kind: ProjectDocumentKind,source: String,assets: Map<Path,String>,expected: OpenedProjectDocument?,validate: (String)->Unit): OpenedProjectDocument = synchronized(AssetIdentity) {
        val file=target(path)
        require(file.fileName.toString().endsWith(kind.suffix,true)) { "Use ${kind.suffix} for project documents" }
        fun checkRevision() {
            if(expected!=null && file==expected.path) require(Files.exists(file) && fingerprint(read(file))==expected.revision) {
                "Document changed or was deleted on disk. Reopen to load external changes, or Save As to keep your draft."
            } else require(!Files.exists(file,LinkOption.NOFOLLOW_LINKS)) { "Destination exists; choose a new name or open it first" }
        }
        checkRevision();validate(source)
        val snapshot=scan();val root=assetPath(project.root,project.assets)
        if(Files.exists(AssetIdentity.sidecar(file),LinkOption.NOFOLLOW_LINKS)) {
            val id=AssetIdentity.read(AssetIdentity.sidecar(file))
            val relative=root.relativize(file).toString().replace('\\','/')
            require(snapshot.pathsById[id]==relative || (!Files.exists(file) && id !in snapshot.pathsById)) { "Destination asset identity is ambiguous" }
        }
        fun idFor(path: Path) = assetIdFor(snapshot,root,path)
        val portable=references(Json.parseToJsonElement(source).jsonObject) { text ->
            if(text.startsWith(prefix)) { resolve(snapshot,text.removePrefix(prefix));text }
            else { require(!text.startsWith("asset://")) { "Unresolved package reference" };prefix+idFor(Path.of(text)) }
        }
        val ids=assets.keys.map(::idFor).distinct().sorted()
        val envelope=buildJsonObject {
            put("format",kind.format);put("version",1);put("document",portable)
            put("assets",JsonArray(ids.map(::JsonPrimitive)))
        }
        val bytes=(documentJson.encodeToString(JsonElement.serializer(),envelope)+"\n").toByteArray(Charsets.UTF_8)
        require(bytes.size<=16*1024*1024) { "Project document exceeds 16 MiB" }
        val temporary=Files.createTempFile(file.parent,".sle-document-",".tmp")
        try {
            Files.write(temporary,bytes);checkRevision()
            if(expected?.path==file) {
                try { Files.move(temporary,file,ATOMIC_MOVE,REPLACE_EXISTING) }
                catch(_: AtomicMoveNotSupportedException) { Files.move(temporary,file,REPLACE_EXISTING) }
            } else Files.move(temporary,file)
        } finally { Files.deleteIfExists(temporary) }
        AssetIdentity.ensure(file)
        OpenedProjectDocument(file,kind,source,assets,fingerprint(bytes))
    }
}
