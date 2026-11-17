package sle.editor.project.assets

import kotlinx.serialization.json.*
import java.nio.file.*
import java.util.UUID
import sle.editor.project.relativeProjectPath

/** Sidecars are source data. Only missing metadata is generated automatically. */
internal object AssetIdentity {
    fun isMetadata(path: Path)=path.fileName.toString().endsWith(".slemeta",true)
    fun sidecar(file: Path): Path=file.resolveSibling(file.fileName.toString()+".slemeta")
    fun read(meta: Path): String {
        require(!Files.isSymbolicLink(meta) && Files.isRegularFile(meta,LinkOption.NOFOLLOW_LINKS)) { "Metadata is not a regular file: $meta" }
        val json=Json.parseToJsonElement(Files.readString(meta)).jsonObject
        require(json.keys==setOf("format","version","id") && json["format"]==JsonPrimitive("sle-asset") && json["version"]==JsonPrimitive(1)) { "Unsupported asset metadata: $meta" }
        val id=json["id"]?.jsonPrimitive
        require(id?.isString==true && runCatching { UUID.fromString(id.content).toString().equals(id.content,true) }.getOrDefault(false)) { "Invalid asset UUID: $meta" }
        return id.content.lowercase()
    }
    fun encode(id: String)=buildJsonObject { put("format","sle-asset");put("version",1);put("id",id) }.toString()+"\n"
    fun ensure(file: Path): String {
        val meta=sidecar(file)
        if(!Files.exists(meta,LinkOption.NOFOLLOW_LINKS)) {
            try { Files.writeString(meta,encode(UUID.randomUUID().toString()),StandardOpenOption.CREATE_NEW) }
            catch(_: FileAlreadyExistsException) { /* Another scan/import won creation. */ }
        }
        return read(meta)
    }
    fun repair(project: Path,assets: Path,relative: String): Unit = synchronized(this) {
        val file=assetPath(project,assets,relative)
        require(Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS) && !isMetadata(file)) { "Select an asset file to repair" }
        val meta=sidecar(file)
        require(!Files.isSymbolicLink(meta)) { "Metadata is a link" }
        atomicAssetWrite(meta,encode(UUID.randomUUID().toString()).toByteArray(),replace=true)
    }
}

/** Resolve only real paths contained beneath the configured asset root, without links. */
internal fun assetPath(project: Path,assets: Path,relative: String=""): Path {
    val projectPath=project.toAbsolutePath().normalize()
    val input=assets.toAbsolutePath().normalize()
    require(input.startsWith(projectPath) && input!=projectPath) { "Asset root must be inside the project" }
    val root=input.toRealPath()
    require(root==projectPath.toRealPath().resolve(projectPath.relativize(input))) { "Asset root points through a link" }
    val parts=if(relative.isEmpty()) emptyList() else relativeProjectPath(relative)
    var path=root
    for(part in parts) {
        path=path.resolve(part)
        require(!Files.isSymbolicLink(path)) { "Links cannot be used for project operations: $path" }
        if(Files.exists(path,LinkOption.NOFOLLOW_LINKS)) require(path.toRealPath()==path) { "Path points through a link: $path" }
    }
    return path
}

internal fun atomicAssetWrite(path: Path,bytes: ByteArray,replace: Boolean) {
    atomicAssetFile(path,replace) { Files.write(it,bytes) }
}

internal fun atomicAssetCopy(source: Path,path: Path,replace: Boolean) {
    atomicAssetFile(path,replace) { Files.copy(source,it,StandardCopyOption.REPLACE_EXISTING) }
}

private fun atomicAssetFile(path: Path,replace: Boolean,write: (Path)->Unit) {
    val temp=Files.createTempFile(path.parent,".sle-write-",".tmp")
    try {
        write(temp)
        if(replace) {
            try { Files.move(temp,path,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING) }
            catch(_: AtomicMoveNotSupportedException) { Files.move(temp,path,StandardCopyOption.REPLACE_EXISTING) }
        } else Files.move(temp,path) // No REPLACE: a concurrent arrival must win.
    } finally { Files.deleteIfExists(temp) }
}
