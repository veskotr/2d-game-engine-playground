package sle.editor.project

import kotlinx.serialization.json.*
import sle.editor.files.PrefabPackages
import sle.editor.model.documentJson
import sle.editor.project.assets.*
import java.nio.file.*
import java.nio.file.StandardCopyOption.*
import java.security.MessageDigest

internal enum class ProjectDocumentKind(val format: String,val suffix: String) {
    Scene("sle-project-scene",".scene.json"), Prefab("sle-project-prefab",".prefab.json")
}
internal data class OpenedProjectDocument(val path: Path,val kind: ProjectDocumentKind,val source: String,
    val assets: Map<Path,String>,val revision: String)

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
            is JsonObject -> if(field=="variables" || field=="targetEntities") value else JsonObject(value.mapValues { (key,child) -> fields(child,if(field=="stateClipMap") "clipAsset" else key) })
            is JsonArray -> JsonArray(value.map { fields(it,field) })
            is JsonPrimitive -> if(value.isString && value.content.isNotBlank() && PrefabPackages.isAssetField(field)) JsonPrimitive(resolve(value.content)) else value
        }
        return JsonObject(document.mapValues { (key,value) -> if(key=="entities") JsonArray(value.jsonArray.map { entity ->
            JsonObject(entity.jsonObject.mapValues { (field,child) -> if(field=="components") fields(child) else child })
        }) else value })
    }
    private fun resolve(snapshot: AssetSnapshot,id: String): Path {
        val relative=snapshot.pathsById[id] ?: error("Missing or ambiguous project asset UUID: $id")
        return assetPath(project.root,project.assets,relative).also { require(Files.isRegularFile(it,LinkOption.NOFOLLOW_LINKS)) { "Missing asset: $relative" } }
    }
    fun open(path: Path): OpenedProjectDocument = synchronized(AssetIdentity) {
        val file=target(path);val bytes=read(file);val envelope=Json.parseToJsonElement(bytes.toString(Charsets.UTF_8)).jsonObject
        require(envelope.keys==setOf("format","version","document","assets")) { "Unsupported project document fields" }
        val kind=ProjectDocumentKind.entries.firstOrNull { it.format==envelope["format"]?.jsonPrimitive?.content } ?: error("Not a project scene or prefab; use standalone Open for packages")
        require(envelope["version"]?.jsonPrimitive?.intOrNull==1) { "Unsupported project document version" }
        val snapshot=scan()
        fun pathFor(text: String): String {
            require(text.startsWith(prefix)) { "Project asset field must contain a UUID reference: $text" }
            return resolve(snapshot,text.removePrefix(prefix)).toString().replace('\\','/')
        }
        val document=references(envelope.getValue("document").jsonObject,::pathFor)
        val assets=envelope.getValue("assets").jsonArray.associate { value ->
            val asset=resolve(snapshot,value.jsonPrimitive.content);asset to asset.fileName.toString()
        }
        OpenedProjectDocument(file,kind,documentJson.encodeToString(JsonElement.serializer(),document),assets,fingerprint(bytes))
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
        fun idFor(path: Path): String {
            val real=path.toRealPath()
            require(real.startsWith(root)) { "Asset is outside this project; import it first: $path" }
            val relative=root.relativize(real).toString().replace('\\','/')
            assetPath(project.root,project.assets,relative)
            return snapshot.idsByPath[relative] ?: error("Missing or ambiguous asset identity: $relative")
        }
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
