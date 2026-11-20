package sle.editor.project.assets

import kotlinx.serialization.json.*
import sle.editor.project.*
import java.nio.file.*
import java.util.UUID

internal data class AssetReference(val source: String, val id: String, val documentPath: String? = source)
internal data class AssetDeletion(val token: String,val original: String)

/** Managed source operations. Never infers arbitrary dependencies from Lua/XML text. */
internal class ProjectAssetOperations(private val project: OpenedProject) {
    private fun path(relative: String): Path {
        relativeProjectPath(relative).forEach {
            validateProjectFolderName(it)
            require(!it.endsWith(".slemeta",true)) { "Reserved metadata suffix" }
        }
        return assetPath(project.root,project.assets,relative)
    }
    private fun snapshot(registerMissing: Boolean = true): AssetSnapshot = AssetScanner.scan(project.root,project.assets,registerMissing).also {
        require(it.errors.isEmpty()) { it.errors.joinToString("\n") }
    }
    private fun affected(snapshot: AssetSnapshot,relative: String)=snapshot.idsByPath.filterKeys { it==relative || it.startsWith("$relative/") }.values.toSet()
    private fun referenceIndex(snapshot: AssetSnapshot, documents: ProjectDocuments? = null): List<AssetReference> {
        val result=mutableListOf<AssetReference>()
        // Use the current descriptor, not the UI's potentially stale settings snapshot.
        ProjectFiles.open(project.file).document.startupScene?.let { result.add(AssetReference("Project startup scene",it,documentPath=null)) }
        for(entry in snapshot.entries.filter { it.kind==AssetKind.File && (it.path.endsWith(".scene.json",true) || it.path.endsWith(".prefab.json",true)) }) {
            val file=path(entry.path)
            require(Files.size(file)<=16*1024*1024) { "Oversized source: ${entry.path}" }
            val envelope=Json.parseToJsonElement(Files.readString(file)).jsonObject
            require(envelope["version"]?.jsonPrimitive?.intOrNull==1 && envelope["format"]?.jsonPrimitive?.content in ProjectDocumentKind.entries.map { it.format }) { "Unsupported source: ${entry.path}" }
            fun walk(value: JsonElement) {
                when(value) {
                    is JsonObject -> value.values.forEach(::walk)
                    is JsonArray -> value.forEach(::walk)
                    is JsonPrimitive -> if(value.isString && value.content.startsWith("project-asset://")) result.add(AssetReference(entry.path,value.content.removePrefix("project-asset://")))
                }
            }
            val document = envelope.getValue("document")
            if (documents == null) walk(document)
            else documents.referencedAssetIds(document.jsonObject).forEach { result.add(AssetReference(entry.path, it)) }
            envelope.getValue("assets").jsonArray.forEach { result.add(AssetReference(entry.path,it.jsonPrimitive.content)) }
        }
        return result.distinct()
    }
    fun references(relative: String): List<AssetReference> = synchronized(AssetIdentity) {
        path(relative);val snapshot=snapshot();val ids=affected(snapshot,relative)
        referenceIndex(snapshot).filter { it.id in ids && it.source!=relative && !it.source.startsWith("$relative/") }
    }
    /** Read saved consumers without assigning asset identities or expanding linked templates. */
    fun findReferences(relative: String): List<AssetReference> = synchronized(AssetIdentity) {
        path(relative)
        val snapshot = snapshot(registerMissing = false)
        val id = requireNotNull(snapshot.idsByPath[relative]) { "Asset UUID is unavailable: $relative" }
        referenceIndex(snapshot, ProjectDocuments(project)).filter { it.id == id }
    }

    fun createFile(relative: String, contents: ByteArray): Path = synchronized(AssetIdentity) {
        val target = path(relative)
        require(!Files.exists(target, LinkOption.NOFOLLOW_LINKS) &&
            !Files.exists(AssetIdentity.sidecar(target), LinkOption.NOFOLLOW_LINKS)) {
            "Destination already exists: $relative"
        }
        Files.createDirectories(target.parent)
        atomicAssetWrite(target, contents, replace = false)
        try {
            AssetIdentity.ensure(target)
        } catch (failure: Exception) {
            throw IllegalStateException("Created $target without an attachment. Could not register its asset identity.", failure)
        }
        target
    }

    fun createFolder(relative: String): Path = synchronized(AssetIdentity) {
        val target=path(relative)
        require(Files.isDirectory(target.parent)) { "Parent folder no longer exists" }
        require(!Files.exists(AssetIdentity.sidecar(target),LinkOption.NOFOLLOW_LINKS)) { "Destination metadata already exists" }
        Files.createDirectory(target)
    }
    fun move(relative: String,destination: String): Path = synchronized(AssetIdentity) {
        val source=path(relative);val target=path(destination)
        require(Files.exists(source,LinkOption.NOFOLLOW_LINKS)) { "Source no longer exists" }
        require(!target.startsWith(source)) { "Cannot move a folder into itself" }
        require(Files.isDirectory(target.parent)) { "Destination folder no longer exists" }
        require(!Files.exists(target,LinkOption.NOFOLLOW_LINKS) && !Files.exists(AssetIdentity.sidecar(target),LinkOption.NOFOLLOW_LINKS)) { "Destination already exists" }
        ProjectDocumentKind.entries.firstOrNull { relative.endsWith(it.suffix,true) }?.let {
            require(destination.endsWith(it.suffix,true)) { "Keep the ${it.suffix} source suffix" }
        }
        val snapshot=snapshot();referenceIndex(snapshot) // Fail closed if references cannot be inspected.
        val file=Files.isRegularFile(source,LinkOption.NOFOLLOW_LINKS)
        Files.move(source,target)
        if(file) try { Files.move(AssetIdentity.sidecar(source),AssetIdentity.sidecar(target)) }
        catch(failure: Exception) {
            try { Files.move(target,source) } catch(rollback: Exception) { failure.addSuppressed(rollback) }
            throw failure
        }
        target
    }
    private fun trash(): Path {
        val local=project.root.resolve(".sle")
        require(!Files.isSymbolicLink(local)) { "Local storage is a link" }
        Files.createDirectories(local)
        val trash=local.resolve("trash")
        require(!Files.isSymbolicLink(trash)) { "Trash storage is a link" }
        Files.createDirectories(trash)
        return trash
    }
    fun deletions(): List<AssetDeletion> = synchronized(AssetIdentity) {
        Files.list(trash()).use { paths -> paths.filter { !Files.isSymbolicLink(it) && Files.isDirectory(it) && Files.isRegularFile(it.resolve("receipt.json"),LinkOption.NOFOLLOW_LINKS) }.map {
            val json=Json.parseToJsonElement(Files.readString(it.resolve("receipt.json"))).jsonObject
            AssetDeletion(it.fileName.toString(),json.getValue("original").jsonPrimitive.content)
        }.toList() }
    }
    fun delete(relative: String,allowReferenced: Boolean=false): AssetDeletion = synchronized(AssetIdentity) {
        val source=path(relative)
        require(Files.exists(source,LinkOption.NOFOLLOW_LINKS)) { "Source no longer exists" }
        val references=references(relative)
        require(allowReferenced || references.isEmpty()) { "Referenced by: ${references.map { it.source }.distinct().joinToString()}. Explicit confirmation is required." }
        val receipt=AssetDeletion(UUID.randomUUID().toString(),relative)
        val storage=Files.createDirectory(trash().resolve(receipt.token))
        Files.writeString(storage.resolve("receipt.json"),buildJsonObject { put("original",relative) }.toString(),StandardOpenOption.CREATE_NEW)
        val file=Files.isRegularFile(source,LinkOption.NOFOLLOW_LINKS)
        // Receipt precedes mutations. An interrupted pair can be restored after restart.
        if(file) Files.move(AssetIdentity.sidecar(source),storage.resolve("metadata"))
        try { Files.move(source,storage.resolve("payload")) }
        catch(failure: Exception) {
            if(file) try { Files.move(storage.resolve("metadata"),AssetIdentity.sidecar(source)) } catch(rollback: Exception) { failure.addSuppressed(rollback) }
            throw failure
        }
        receipt
    }
    fun restore(receipt: AssetDeletion): Unit = synchronized(AssetIdentity) {
        require(UUID.fromString(receipt.token).toString()==receipt.token) { "Invalid recovery token" }
        val storage=trash().resolve(receipt.token)
        require(!Files.isSymbolicLink(storage)) { "Recovery storage is a link" }
        val original=Json.parseToJsonElement(Files.readString(storage.resolve("receipt.json"))).jsonObject.getValue("original").jsonPrimitive.content
        require(original==receipt.original) { "Recovery receipt changed" }
        val target=path(original);val payload=storage.resolve("payload");val metadata=storage.resolve("metadata")
        require(Files.isDirectory(target.parent)) { "Restore the parent folder first" }
        require(!Files.isSymbolicLink(payload) && !Files.isSymbolicLink(metadata)) { "Recovery payload is a link" }
        val hasPayload=Files.exists(payload,LinkOption.NOFOLLOW_LINKS)
        val hasMetadata=Files.exists(metadata,LinkOption.NOFOLLOW_LINKS)
        require(hasPayload || hasMetadata) { "Already restored or incomplete recovery" }
        require(!hasPayload || !Files.exists(target,LinkOption.NOFOLLOW_LINKS)) { "Restore destination already exists" }
        require(!hasMetadata || !Files.exists(AssetIdentity.sidecar(target),LinkOption.NOFOLLOW_LINKS)) { "Restore metadata already exists" }
        if(hasMetadata) Files.move(metadata,AssetIdentity.sidecar(target))
        if(hasPayload) try { Files.move(payload,target) } catch(failure: Exception) {
            if(hasMetadata) try { Files.move(AssetIdentity.sidecar(target),metadata) } catch(rollback: Exception) { failure.addSuppressed(rollback) }
            throw failure
        }
        Files.delete(storage.resolve("receipt.json"));Files.delete(storage)
    }
}

/** A sidecar-only deletion is an interrupted operation, not a missing identity. */
internal fun pendingAssetRecoveries(project: Path): Set<String> {
    val local=project.resolve(".sle");val trash=local.resolve("trash")
    if(!Files.exists(local,LinkOption.NOFOLLOW_LINKS)) return emptySet()
    require(!Files.isSymbolicLink(local)) { "Local storage is a link" }
    if(!Files.exists(trash,LinkOption.NOFOLLOW_LINKS)) return emptySet()
    require(!Files.isSymbolicLink(trash)) { "Recovery storage is a link" }
    return Files.list(trash).use { entries -> entries.filter { !Files.isSymbolicLink(it) && Files.isDirectory(it) }.map { folder ->
        val receipt=folder.resolve("receipt.json")
        if(Files.isRegularFile(receipt,LinkOption.NOFOLLOW_LINKS) && Files.exists(folder.resolve("metadata"),LinkOption.NOFOLLOW_LINKS) && !Files.exists(folder.resolve("payload"),LinkOption.NOFOLLOW_LINKS)) {
            val original=Json.parseToJsonElement(Files.readString(receipt)).jsonObject.getValue("original").jsonPrimitive.content
            relativeProjectPath(original)
            original
        } else ""
    }.filter { it.isNotEmpty() }.toList().toSet() }
}
