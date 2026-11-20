package sle.editor.project.assets

import kotlinx.serialization.json.*
import java.nio.file.*
import java.util.UUID
import sle.editor.project.relativeProjectPath

internal enum class AssetTextureFilter(val serialized: String, val label: String) {
    Nearest("nearest", "Nearest"),
    Linear("linear", "Linear"),
}

internal data class AssetMetadata(val id: String, val filter: AssetTextureFilter?, val regions: List<SpriteRegion> = emptyList())

internal fun isTextureAsset(path: String): Boolean = path.substringAfterLast('.', "").lowercase() in
    setOf("png", "jpg", "jpeg", "bmp", "tga", "gif", "psd", "hdr", "pic", "pnm", "ppm", "pgm")

/** Sidecars are source data. Only missing metadata is generated automatically. */
internal object AssetIdentity {
    fun isMetadata(path: Path)=path.fileName.toString().endsWith(".slemeta",true)
    fun sidecar(file: Path): Path=file.resolveSibling(file.fileName.toString()+".slemeta")
    fun read(meta: Path): String = readMetadata(meta).id.lowercase()

    fun readMetadata(meta: Path): AssetMetadata {
        require(!Files.isSymbolicLink(meta) && Files.isRegularFile(meta, LinkOption.NOFOLLOW_LINKS)) {
            "Metadata is not a regular file: $meta"
        }
        try {
            require(Files.size(meta) <= 16 * 1024 * 1024) { "Metadata exceeds 16 MiB" }
            val json = Json.parseToJsonElement(Files.readString(meta).removePrefix("\uFEFF")).jsonObject
            require(json.keys.all { it in setOf("format", "version", "id", "texture") } &&
                json["format"] == JsonPrimitive("sle-asset") && json["version"] == JsonPrimitive(1)) {
                "Unsupported asset metadata"
            }
            val id = json["id"] as? JsonPrimitive
            require(id?.isString == true && UUID.fromString(id.content).toString().equals(id.content, true)) {
                "Invalid asset UUID"
            }
            val texture = json["texture"]?.let { value ->
                require(value is JsonObject && value.isNotEmpty() && value.keys.all { it in setOf("filter", "regions") }) {
                    "Expected texture.filter or texture.regions"
                }
                value
            }
            val filter = texture?.get("filter")?.let { value ->
                requireNotNull(AssetTextureFilter.entries.find { JsonPrimitive(it.serialized) == value }) {
                    "Expected nearest or linear for texture.filter"
                }
            }
            return AssetMetadata(id.content, filter, texture?.get("regions")?.let(::decodeRegions).orEmpty())
        } catch (failure: IllegalArgumentException) {
            throw IllegalArgumentException("Invalid asset metadata: $meta. ${failure.message}", failure)
        }
    }

    fun encode(id: String, filter: AssetTextureFilter? = null, regions: List<SpriteRegion> = emptyList()) = buildJsonObject {
        put("format", "sle-asset")
        put("version", 1)
        put("id", id)
        if (filter != null || regions.isNotEmpty()) putJsonObject("texture") {
            filter?.let { put("filter", it.serialized) }
            if (regions.isNotEmpty()) putJsonArray("regions") { regions.forEach { add(it.encode()) } }
        }
    }.toString() + "\n"

    fun writeFilter(project: Path, assets: Path, relative: String, filter: AssetTextureFilter?): Unit = synchronized(this) {
        val file = assetPath(project, assets, relative)
        require(Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) && isTextureAsset(relative)) {
            "Select a registered texture file"
        }
        val meta = sidecar(file)
        val current = readMetadata(meta)
        if (current.filter != filter) atomicAssetWrite(meta, encode(current.id, filter, current.regions).toByteArray(), replace = true)
    }
    fun editRegions(project: Path, assets: Path, relative: String,
        edit: (List<SpriteRegion>) -> List<SpriteRegion>): Unit = synchronized(this) {
        val file = assetPath(project, assets, relative)
        require(Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) && isTextureAsset(relative)) {
            "Select a registered texture file"
        }
        val meta = sidecar(file)
        val current = readMetadata(meta)
        val regions = edit(current.regions)
        validateRegions(regions)
        if (regions != current.regions) {
            val previous = current.regions.associateBy { it.id }
            val changed = regions.filter { region -> previous[region.id]?.sameRectangle(region) != true }
            if (changed.isNotEmpty()) {
                val (width, height) = spriteImageSize(file)
                require(changed.all { it.fits(width, height) }) { "Region extends beyond the image" }
            }
            atomicAssetWrite(meta, encode(current.id, current.filter, regions).toByteArray(), replace = true)
        }
    }

    fun ensure(file: Path): String = ensureMetadata(file).id.lowercase()

    fun ensureMetadata(file: Path): AssetMetadata {
        val meta=sidecar(file)
        if(!Files.exists(meta,LinkOption.NOFOLLOW_LINKS)) {
            try { Files.writeString(meta,encode(UUID.randomUUID().toString()),StandardOpenOption.CREATE_NEW) }
            catch(_: FileAlreadyExistsException) { /* Another scan/import won creation. */ }
        }
        return readMetadata(meta)
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
