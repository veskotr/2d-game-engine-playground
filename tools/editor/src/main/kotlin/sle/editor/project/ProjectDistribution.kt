package sle.editor.project

import kotlinx.serialization.json.*
import sle.editor.project.assets.AssetIdentity
import java.io.RandomAccessFile
import java.nio.file.*
import java.nio.file.attribute.BasicFileAttributes
import java.security.MessageDigest

internal enum class GameTarget(val id: String, val label: String, val launcher: String) {
    Windows("windows-x64", "Windows x64", "engine_app.exe"),
    Linux("linux-x64", "Linux x64", "engine_app"),
}

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
    internal fun inventory(root: Path, caseInsensitive: Boolean = true): Inventory {
        val folders=mutableListOf<String>();val files=sortedMapOf<String,String>();val names=mutableSetOf<String>()
        Files.walkFileTree(root,object: SimpleFileVisitor<Path>() {
            private fun checked(path: Path): String {
                require(!Files.isSymbolicLink(path) && path.toRealPath()==path) { "Distribution cannot include links: $path" }
                val relative=root.relativize(path).joinToString("/")
                if (caseInsensitive) {
                    val folded = relative.map(Char::uppercaseChar).joinToString("")
                    require(names.add(folded)) { "Case-insensitive filename collision: $relative" }
                }
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
    private fun anyProjectDocument(assets: Path, inventory: Inventory, matches: (JsonObject) -> Boolean): Boolean =
        inventory.files.keys.asSequence()
            .filter { name -> ProjectDocumentKind.entries.any { name.endsWith(it.suffix, ignoreCase = true) } }
            .any { name ->
                val path = assets.resolve(name)
                require(Files.size(path) <= 16 * 1024 * 1024) { "Oversized project document: $name" }
                val document = Json.parseToJsonElement(Files.readString(path)).jsonObject["document"] as? JsonObject
                document != null && matches(document)
            }

    private fun hasTagComponents(document: JsonObject): Boolean {
        fun hasTags(fields: JsonElement?) = (fields as? JsonObject)?.containsKey("Tags") == true

        val entities = document["entities"] as? JsonArray
        val prefabs = document["prefabs"] as? JsonArray
        return entities.orEmpty().any { hasTags((it as? JsonObject)?.get("components")) } ||
            prefabs.orEmpty().any { instance ->
                val overrides = (instance as? JsonObject)?.get("overrides") as? JsonObject
                overrides?.values.orEmpty().any(::hasTags)
            }
    }

    internal data class RuntimeInventory(val inventory: Inventory, val engineVersion: String?)

    /** Complete, unmodified Release bundle produced by sle_runtime_bundle for the selected target. */
    internal fun runtimeInventory(
        runtime: Path,
        settings: ProjectSettings,
        assets: Path,
        assetFiles: Inventory,
        target: GameTarget = GameTarget.Windows,
        packaged: Boolean = false,
    ): RuntimeInventory {
        val runtimeFiles = inventory(runtime, caseInsensitive = target == GameTarget.Windows)
        val manifestPath = runtime.resolve("sle-runtime.json")
        require("sle-runtime.json" in runtimeFiles.files) { "Missing runtime inventory in $runtime" }
        val manifestBytes = Files.readAllBytes(manifestPath)
        val manifestDigest = MessageDigest.getInstance("SHA-256").digest(manifestBytes).toHexString()
        require(manifestDigest == runtimeFiles.files.getValue("sle-runtime.json")) { "Runtime inventory changed during export" }
        val manifest = Json.parseToJsonElement(manifestBytes.decodeToString()).jsonObject
        require(manifest["format"]==JsonPrimitive("sle-runtime") && manifest["version"]==JsonPrimitive(1) &&
            manifest["platform"]==JsonPrimitive(target.id) && manifest["configuration"]==JsonPrimitive("Release") &&
            manifest["executable"]==JsonPrimitive(target.launcher)) { "Select a ${target.label} Release SLE runtime bundle" }
        val engineVersion = manifest["engineVersion"]?.let {
            require(it is JsonPrimitive && it.isString && it.content.matches(Regex("[0-9]+\\.[0-9]+\\.[0-9]+"))) {
                "Invalid SLE runtime engine version"
            }
            it.content
        }
        val version = engineVersion?.split('.')?.map {
            requireNotNull(it.toIntOrNull()) { "Invalid SLE runtime engine version" }
        }
        fun compatible(minor: Int) = version != null && (version[0] > 0 || version[1] >= minor)
        require(target != GameTarget.Linux || compatible(33)) { "Linux x64 export requires an SLE 0.33 or newer runtime bundle" }
        require(!packaged || compatible(16)) { "Packaged Windows games require an SLE 0.16 or newer runtime bundle" }
        val hasCollisionSettings = settings.collisionLayers.json().isNotEmpty()
        val minimumMinor = when {
            !compatible(33) && anyProjectDocument(assets, assetFiles) { (it["tileLayers"] as? JsonArray).orEmpty().isNotEmpty() } -> 33
            settings.inputActions.isNotEmpty() -> 30
            settings.tags.isNotEmpty() || (!compatible(29) && anyProjectDocument(assets, assetFiles, ::hasTagComponents)) -> 29
            settings.sortingLayers.isNotEmpty() -> 28
            hasCollisionSettings -> 27
            settings.artStyle != null -> 26
            else -> 0
        }
        if (minimumMinor > 0) {
            require(compatible(minimumMinor)) {
                val feature = when (minimumMinor) {
                    33 -> "Native tile layers"
                    30 -> "Project input actions"
                    29 -> "Project tags"
                    28 -> "Sorting layer labels"
                    27 -> "Collision layer settings"
                    else -> "Art-style presets"
                }
                "$feature require an SLE 0.$minimumMinor or newer runtime bundle"
            }
        }
        val expected=manifest.getValue("files").jsonObject.mapValues { (_,value) -> value.jsonPrimitive.content }
        require(expected==runtimeFiles.files-"sle-runtime.json") { "Runtime bundle files are missing, modified or unexpected; rebuild sle_runtime_bundle" }
        require(target.launcher in runtimeFiles.files && runtimeFiles.files.keys.any { it.startsWith("assets/shaders/") }) { "Runtime executable/resources are missing" }
        require(launcherMatchesTarget(runtime.resolve(target.launcher), target)) {
            "Expected an executable ${target.label} launcher at ${runtime.resolve(target.launcher)}"
        }
        require(runtimeFiles.files.keys.none { it.substringBefore('/').equals("game",true) || it.substringBefore('/').equals(".sle",true) ||
            it.equals("sle-distribution.json",true) || it.equals(GameContentUpdate.MANIFEST,true) || it.equals(GameBuild.RELEASE,true) || it.equals("config.json",true) || it.endsWith(".slepkg",true) }) { "Select a runtime bundle, not an exported game" }
        val paths = runtimeFiles.folders + runtimeFiles.files.keys
        if (target == GameTarget.Windows) paths.forEach(GamePackageWriter::requirePortable)
        else require(paths.none { path -> path.any { it.code < 32 || it == '\\' } }) { "Invalid path in Linux runtime bundle" }
        return RuntimeInventory(runtimeFiles, engineVersion)
    }
    private fun launcherMatchesTarget(path: Path, target: GameTarget): Boolean = RandomAccessFile(path.toFile(), "r").use { binary ->
        fun word() = java.lang.Short.reverseBytes(binary.readShort()).toInt() and 0xffff
        when (target) {
            GameTarget.Windows -> {
                if (binary.length() < 64 || binary.readUnsignedShort() != 0x4d5a) return@use false // MZ
                binary.seek(60)
                val pe = Integer.reverseBytes(binary.readInt()).toLong()
                if (pe < 64 || pe + 26 > binary.length()) return@use false
                binary.seek(pe)
                if (binary.readInt() != 0x50450000 || word() != 0x8664) return@use false // PE, AMD64
                binary.seek(pe + 20)
                val optionalHeaderSize = word()
                val characteristics = word()
                optionalHeaderSize >= 2 && pe + 24 + optionalHeaderSize <= binary.length() &&
                    characteristics and 0x2002 == 2 && word() == 0x20b // Executable PE32+, not a DLL
            }
            GameTarget.Linux -> {
                if (binary.length() < 64 || binary.readInt() != 0x7f454c46 ||
                    binary.readUnsignedByte() != 2 || binary.readUnsignedByte() != 1 ||
                    binary.readUnsignedByte() != 1) return@use false // Little-endian ELF64
                binary.seek(16)
                val type = word()
                (type == 2 || type == 3) && word() == 62 // ELF executable or PIE for x86-64
            }
        }
    }

    /** Publish inside the project, keeping earlier exports intact. */
    fun exportAutomatically(project: OpenedProject,runtimeBundle: Path,validate: (Path)->Unit): Path {
        return export(project,runtimeBundle,nextExportDestination(project,runtimeBundle),validate)
    }

    internal fun nextExportDestination(project: OpenedProject, runtimeBundle: Path): Path =
        nextExportDestination(project, listOf(runtimeBundle))

    internal fun nextExportDestination(project: OpenedProject, runtimeBundles: Collection<Path>): Path {
        val sourceRoot=project.root.toRealPath()
        val exports=sourceRoot.resolve("Exports")
        val assets=sle.editor.project.assets.assetPath(sourceRoot,project.assets)
        require(!assets.startsWith(exports) && !exports.startsWith(assets)) { "Asset root must be separate from Exports" }
        for (bundle in runtimeBundles) {
            require(!Files.isSymbolicLink(bundle)) { "Runtime bundle cannot be a link" }
            require(!exports.startsWith(bundle.toRealPath())) { "Exports must be separate from the runtime bundle" }
        }
        require(!Files.isSymbolicLink(exports)) { "Exports cannot be a link" }
        Files.createDirectories(exports)
        require(exports.toRealPath()==exports) { "Exports cannot point through a link" }
        val name=sourceRoot.fileName.toString()
        var target=exports.resolve(name)
        var number=2
        while(Files.exists(target,LinkOption.NOFOLLOW_LINKS)) target=exports.resolve("$name-${number++}")
        return target
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
        val assetFiles=inventory(assets);identities(assets,assetFiles)
        val runtimeFiles=runtimeInventory(runtime, source.document.settings, assets, assetFiles).inventory
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
