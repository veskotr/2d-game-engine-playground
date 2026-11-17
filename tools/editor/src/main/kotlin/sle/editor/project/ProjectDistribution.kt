package sle.editor.project

import kotlinx.serialization.json.*
import sle.editor.project.assets.AssetIdentity
import java.nio.file.*
import java.nio.file.attribute.BasicFileAttributes
import java.security.MessageDigest

/** Saved-source delivery. No scanner: export must never create metadata in the source. */
internal object ProjectDistribution {
    internal data class Inventory(val folders: List<String>,val files: Map<String,String>)
    internal fun hash(path: Path): String {
        val digest=MessageDigest.getInstance("SHA-256")
        Files.newInputStream(path).use { stream ->
            val buffer=ByteArray(128*1024)
            while(true) { val count=stream.read(buffer);if(count<0) break;digest.update(buffer,0,count) }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
    internal fun inventory(root: Path): Inventory {
        val folders=mutableListOf<String>();val files=sortedMapOf<String,String>();val names=mutableSetOf<String>()
        Files.walkFileTree(root,object: SimpleFileVisitor<Path>() {
            private fun checked(path: Path): String {
                require(!Files.isSymbolicLink(path) && path.toRealPath()==path) { "Distribution cannot include links: $path" }
                val relative=root.relativize(path).toString().replace('\\','/')
                require(names.add(relative.lowercase(java.util.Locale.ROOT))) { "Case-insensitive filename collision: $relative" }
                return relative
            }
            override fun preVisitDirectory(dir: Path,attrs: BasicFileAttributes): FileVisitResult {
                val relative=checked(dir);if(relative.isNotEmpty()) folders.add(relative)
                return FileVisitResult.CONTINUE
            }
            override fun visitFile(file: Path,attrs: BasicFileAttributes): FileVisitResult {
                val relative=checked(file)
                require(attrs.isRegularFile) { "Unsupported distribution file: $file" }
                files[relative]=hash(file);return FileVisitResult.CONTINUE
            }
        })
        return Inventory(folders.sorted(),files)
    }
    internal fun copy(root: Path,to: Path,inventory: Inventory) {
        Files.createDirectories(to)
        inventory.folders.sortedBy { it.count { c -> c=='/' } }.forEach { Files.createDirectory(to.resolve(it)) }
        inventory.files.keys.forEach { Files.copy(root.resolve(it),to.resolve(it)) }
    }
    internal fun identities(root: Path,inventory: Inventory) {
        val ids=mutableSetOf<String>()
        for(relative in inventory.files.keys) {
            val path=root.resolve(relative)
            if(AssetIdentity.isMetadata(path)) {
                require(relative.dropLast(8) in inventory.files) { "Orphan metadata: $relative" }
            } else {
                require(relative+".slemeta" in inventory.files) { "Missing metadata: $relative. Refresh assets first." }
                require(ids.add(AssetIdentity.read(AssetIdentity.sidecar(path)))) { "Duplicate asset UUID: $relative" }
            }
        }
    }
    /** Complete, unmodified Windows x64 Release bundle produced by sle_runtime_bundle. */
    internal fun runtimeInventory(runtime: Path): Inventory {
        val runtimeFiles=inventory(runtime)
        val manifest=Json.parseToJsonElement(Files.readString(runtime.resolve("sle-runtime.json"))).jsonObject
        require(manifest["format"]==JsonPrimitive("sle-runtime") && manifest["version"]==JsonPrimitive(1) &&
            manifest["platform"]==JsonPrimitive("windows-x64") && manifest["configuration"]==JsonPrimitive("Release") &&
            manifest["executable"]==JsonPrimitive("engine_app.exe")) { "Select a Windows x64 Release SLE runtime bundle" }
        val expected=manifest.getValue("files").jsonObject.mapValues { (_,value) -> value.jsonPrimitive.content }
        require(expected==runtimeFiles.files-"sle-runtime.json") { "Runtime bundle files are missing, modified or unexpected; rebuild sle_runtime_bundle" }
        require("engine_app.exe" in runtimeFiles.files && runtimeFiles.files.keys.any { it.startsWith("assets/shaders/") }) { "Runtime executable/resources are missing" }
        require(runtimeFiles.files.keys.none { it.substringBefore('/').equals("game",true) || it.substringBefore('/').equals(".sle",true) ||
            it.equals("sle-distribution.json",true) || it.equals("config.json",true) || it.endsWith(".slepkg",true) }) { "Select a runtime bundle, not an exported game" }
        return runtimeFiles
    }
    /** Publish inside the project, keeping earlier exports intact. */
    fun exportAutomatically(project: OpenedProject,runtimeBundle: Path,validate: (Path)->Unit): Path {
        val sourceRoot=project.root.toRealPath()
        val exports=sourceRoot.resolve("Exports")
        val assets=sle.editor.project.assets.assetPath(sourceRoot,project.assets)
        require(!assets.startsWith(exports) && !exports.startsWith(assets)) { "Asset root must be separate from Exports" }
        require(!Files.isSymbolicLink(exports)) { "Exports cannot be a link" }
        Files.createDirectories(exports)
        require(exports.toRealPath()==exports) { "Exports cannot point through a link" }
        val name=sourceRoot.fileName.toString()
        var target=exports.resolve(name)
        var number=2
        while(Files.exists(target,LinkOption.NOFOLLOW_LINKS)) target=exports.resolve("$name-${number++}")
        return export(project,runtimeBundle,target,validate)
    }
    fun export(project: OpenedProject,runtimeBundle: Path,destination: Path,validate: (Path)->Unit): Path {
        val source=ProjectFiles.open(project.file)
        require(source.document==project.document) { "Project settings changed on disk; reopen before exporting" }
        require(source.document.startupScene!=null) { "Choose a startup scene before exporting" }
        val descriptor=Files.readAllBytes(source.file)
        require(!Files.isSymbolicLink(runtimeBundle)) { "Runtime bundle cannot be a link" }
        val runtime=runtimeBundle.toRealPath();val assets=sle.editor.project.assets.assetPath(source.root,source.assets)
        val raw=destination.toAbsolutePath().normalize()
        validateProjectFolderName(raw.fileName.toString())
        val target=raw.parent.toRealPath().resolve(raw.fileName)
        val sourceRoot=source.root.toRealPath()
        val exports=sourceRoot.resolve("Exports")
        val localOutput=target.startsWith(exports) && target!=exports
        require(!sourceRoot.startsWith(target) && (!target.startsWith(sourceRoot) || localOutput) &&
            !target.startsWith(runtime) && !runtime.startsWith(target)) { "Export into project Exports or outside the project and runtime bundle" }
        require(!target.startsWith(assets) && !assets.startsWith(target) &&
            (!localOutput || (!assets.startsWith(exports) && !exports.startsWith(assets)))) { "Export output must be separate from the asset root" }
        require(raw.parent.toRealPath()==raw.parent) { "Export destination cannot point through a link" }
        require(!Files.exists(target,LinkOption.NOFOLLOW_LINKS)) { "Export destination already exists; choose a new folder" }
        val runtimeFiles=runtimeInventory(runtime)
        val assetFiles=inventory(assets);identities(assets,assetFiles)
        val stage=Files.createTempDirectory(target.parent,".sle-export-")
        try {
            copy(runtime,stage,runtimeFiles)
            val game=Files.createDirectory(stage.resolve("game"))
            val stagedAssets=game.resolve(source.document.assetRoot)
            Files.createDirectories(stagedAssets.parent)
            copy(assets,stagedAssets,assetFiles)
            val stagedDescriptor=game.resolve("game.sleproject")
            require(!Files.exists(stagedDescriptor)) { "Asset root conflicts with the delivery descriptor" }
            Files.write(stagedDescriptor,descriptor,StandardOpenOption.CREATE_NEW)
            validate(stagedDescriptor)
            require(Files.readAllBytes(source.file).contentEquals(descriptor) && ProjectFiles.open(source.file).document==source.document &&
                source.assets.toRealPath()==assets && inventory(assets)==assetFiles && inventory(runtime)==runtimeFiles) { "Project or runtime files changed during export; retry" }
            require(Files.readAllBytes(stagedDescriptor).contentEquals(descriptor) && inventory(stagedAssets)==assetFiles) { "Staged source changed during validation" }
            // The native resolver can produce disposable prefab derivatives while validating.
            removeTree(game.resolve(".sle"))
            val delivered=inventory(stage)
            require(runtimeFiles.files.all { (path,digest) -> delivered.files[path]==digest }) { "Staged runtime changed during export" }
            Files.writeString(stage.resolve("sle-distribution.json"),buildJsonObject {
                put("format","sle-distribution");put("version",1);put("project","game/game.sleproject")
                put("files",buildJsonObject { delivered.files.forEach { (path,digest) -> put(path,digest) } })
            }.toString()+"\n",StandardOpenOption.CREATE_NEW)
            Files.move(stage,target) // No replacement, including an external arrival.
            return target.resolve("game/game.sleproject")
        } finally { removeTree(stage) }
    }
    internal fun removeTree(root: Path) {
        if(!Files.exists(root,LinkOption.NOFOLLOW_LINKS)) return
        Files.walkFileTree(root,object: SimpleFileVisitor<Path>() {
            override fun visitFile(file: Path,attrs: BasicFileAttributes): FileVisitResult { Files.delete(file);return FileVisitResult.CONTINUE }
            override fun postVisitDirectory(dir: Path,error: java.io.IOException?): FileVisitResult { if(error!=null) throw error;Files.delete(dir);return FileVisitResult.CONTINUE }
        })
    }
}
