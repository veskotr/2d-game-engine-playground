package sle.editor.project

import kotlinx.serialization.json.*
import java.nio.file.*
import java.security.MessageDigest
import java.time.LocalDateTime
import java.util.UUID
import sle.editor.bridge.NativeEngine
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Version 1 `.slepkg` game package: a STORED ZIP with `manifest.json` and every file
 * beneath the project asset root (including `.slemeta` identities) under `assets/`.
 * The native reader (`Systems/src/GamePackage.cpp`) enforces the same limits and names.
 */
internal object GamePackageWriter {
    const val MAX_ENTRIES=65000
    const val MAX_ENTRY=256L*1024*1024
    const val MAX_TOTAL=1024L*1024*1024
    private const val MAX_ARCHIVE=MAX_TOTAL+64L*1024*1024
    private val reserved = Regex("con|prn|aux|nul|conin[$]|conout[$]|(com|lpt)[1-9¹²³]")

    fun isWindowsDeviceName(name: String): Boolean =
        reserved.matches(name.substringBefore('.').trimEnd(' ').lowercase(java.util.Locale.ROOT))

    fun requirePortable(relative: String) {
        require(relative.isNotEmpty() && relative.encodeToByteArray().size<=1024-"assets/".length) { "Asset path is too long for a game package: $relative" }
        for(segment in relative.split('/')) {
            require(segment.isNotEmpty() && segment!="." && segment!=".." && segment.encodeToByteArray().size<=255 && !segment.endsWith('.') && !segment.endsWith(' ') &&
                segment.none { it.code<32 || it.code==127 || it in "\\:*?\"<>|" } && !isWindowsDeviceName(segment)) {
                "Asset name cannot be packaged portably: $relative"
            }
        }
    }

    /** Writes [files] (asset-root-relative path to SHA-256) and fails if any payload changed since it was inventoried. */
    fun write(output: Path,manifest: JsonObject,assets: Path,files: Map<String,String>) {
        require(files.size+1<=MAX_ENTRIES) { "Game package exceeds $MAX_ENTRIES files" }
        files.keys.forEach(::requirePortable)
        var total=0L
        ZipOutputStream(Files.newOutputStream(output,StandardOpenOption.CREATE_NEW)).use { zip ->
            fun entry(name: String,bytes: ByteArray) {
                require(bytes.size<=MAX_ENTRY) { "Game package file exceeds 256 MiB: $name" }
                total+=bytes.size;require(total<=MAX_TOTAL) { "Game package exceeds 1 GiB at $name" }
                val crc=CRC32().apply { update(bytes) }
                zip.putNextEntry(ZipEntry(name).apply {
                    method=ZipEntry.STORED;size=bytes.size.toLong();compressedSize=bytes.size.toLong();this.crc=crc.value
                    timeLocal=LocalDateTime.of(1980,1,1,0,0) // Reproducible archives.
                })
                zip.write(bytes);zip.closeEntry()
            }
            entry("manifest.json",manifest.toString().toByteArray(Charsets.UTF_8))
            for((relative,digest) in files.toSortedMap()) {
                val path=assets.resolve(relative)
                require(Files.size(path)<=MAX_ENTRY) { "Game package file exceeds 256 MiB: $relative" }
                val bytes=Files.readAllBytes(path)
                val actual=MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
                require(actual==digest) { "Project file changed during build: $relative; retry" }
                entry("assets/$relative",bytes)
            }
        }
        require(Files.size(output)<=MAX_ARCHIVE) { "Game package archive exceeds 1088 MiB" }
    }
}

internal data class BuiltPackage(val path: String, val name: String, val bytes: Long)
internal data class BuiltGame(
    val folder: Path,
    val executable: Path,
    val config: Path,
    val packages: List<BuiltPackage>,
    val colocatedAssets: List<String>,
)

internal data class GameExport(
    val folder: Path,
    val games: Map<GameTarget, BuiltGame>,
    val archives: Map<GameTarget, Path>,
)

/** Saved-source packaged build: runtime bundle + `config.json` + `game.slepkg`. */
internal object GameBuild {
    const val CONFIG="config.json"
    const val PACKAGE="game.slepkg"
    const val RELEASE="sle-release.json"
    // Runtime-bundle metadata that does not belong in a delivered game.
    private val bundleOnly=setOf("sle-runtime.json","RUNNING.txt")
    private val pretty=Json { prettyPrint=true }

    /** The engine executable is renamed after the project so the delivery looks like the game. */
    fun executableName(projectName: String, target: GameTarget = GameTarget.Windows): String {
        var base=projectName.map { if(it.code<32 || it.code==127 || it in "\\/:*?\"<>|") '_' else it }.joinToString("").trim().trimEnd('.',' ')
        if (base.length > 200) base = base.take(200).dropLastWhile(Char::isHighSurrogate)
        while (base.encodeToByteArray().size > 200) {
            base = base.substring(0, base.offsetByCodePoints(base.length, -1))
        }
        base = base.trimEnd('.', ' ')
        if(base.isEmpty()) base="Game"
        if (GamePackageWriter.isWindowsDeviceName(base)) {
            val dot = base.indexOf('.')
            base = if (dot < 0) "${base}_" else base.substring(0, dot) + "_" + base.substring(dot)
        }
        return if (target == GameTarget.Windows) "$base.exe" else base
    }

    fun configJson(settings: ProjectGameSettings,packages: List<String>): JsonObject=buildJsonObject {
        put("window",buildJsonObject {
            put("title",settings.title);put("width",settings.width);put("height",settings.height)
            put("vsync",settings.vsync);put("mode",settings.mode)
        })
        put("targetFPS",settings.targetFPS)
        put("packages",JsonArray(packages.map(::JsonPrimitive)))
    }

    /** [validate] receives the staged config and must release native state before returning. */
    fun build(project: OpenedProject,runtimeBundle: Path,destination: Path,validate: (Path)->Unit): BuiltGame =
        build(project, mapOf(GameTarget.Windows to runtimeBundle), destination, null, false, validate).games.getValue(GameTarget.Windows)

    fun buildPackages(
        project: OpenedProject,
        runtimeBundle: Path,
        destination: Path,
        priorOwners: Map<String, PackageOwner> = emptyMap(),
        validate: (Path) -> Unit = ::validatePackages,
    ): BuiltGame = build(project, mapOf(GameTarget.Windows to runtimeBundle), destination, priorOwners, false, validate)
        .games.getValue(GameTarget.Windows)

    fun exportAutomatically(
        project: OpenedProject,
        runtimeBundle: Path,
        validate: (Path) -> Unit = ::validatePackages,
    ): BuiltGame = buildPackages(project, runtimeBundle,
        ProjectDistribution.nextExportDestination(project, runtimeBundle), validate = validate)

    fun exportTargets(
        project: OpenedProject,
        bundles: Map<GameTarget, Path>,
        validate: (Path) -> Unit = ::validatePackages,
    ): GameExport {
        require(bundles.isNotEmpty()) { "Select at least one game export target" }
        return build(project, bundles, ProjectDistribution.nextExportDestination(project, bundles.values),
            emptyMap(), true, validate)
    }

    fun validatePackages(config: Path) {
        val result = NativeEngine.prepareGame(config.toString().encodeToByteArray())
        val handle = Json.parseToJsonElement(result.decodeToString()).jsonObject.getValue("handle").jsonPrimitive.long
        NativeEngine.releaseProject(handle)
    }

    private data class PackageOutput(val id: String, val path: String, val assignment: PackageAssignment)

    private fun build(
        project: OpenedProject,
        bundles: Map<GameTarget, Path>,
        destination: Path,
        priorOwners: Map<String, PackageOwner>?,
        withArchives: Boolean,
        validate: (Path) -> Unit,
    ): GameExport {
        val descriptor=Files.readAllBytes(project.file)
        val source=ProjectFiles.open(project.file)
        require(source.document==project.document) { "Project settings changed on disk; reopen before building" }
        val startup=requireNotNull(source.document.startupScene) { "Choose a startup scene before building" }
        val startupOwner = startup.lowercase()
        val projectId = source.document.id.lowercase()
        val runtimes = bundles.toSortedMap().mapValues { (_, bundle) ->
            require(!Files.isSymbolicLink(bundle)) { "Runtime bundle cannot be a link" }
            bundle.toRealPath()
        }
        val assets=sle.editor.project.assets.assetPath(source.root,source.assets)
        val raw=destination.toAbsolutePath().normalize()
        validateProjectFolderName(raw.fileName.toString())
        val target=raw.parent.toRealPath().resolve(raw.fileName)
        val sourceRoot=source.root.toRealPath()
        val exports = sourceRoot.resolve("Exports")
        val localOutput = priorOwners != null && target.startsWith(exports) && target != exports
        require(!sourceRoot.startsWith(target) && (!target.startsWith(sourceRoot) || localOutput) &&
            runtimes.values.none { target.startsWith(it) || it.startsWith(target) }) {
            "Build into project Exports or outside the source project and runtime bundle"
        }
        require(!target.startsWith(assets) && !assets.startsWith(target) &&
            (!localOutput || (!assets.startsWith(exports) && !exports.startsWith(assets)))) {
            "Export output must be separate from the asset root"
        }
        require(raw.parent.toRealPath() == raw.parent) { "Build destination cannot point through a link" }
        require(!Files.exists(target,LinkOption.NOFOLLOW_LINKS)) { "Build destination already exists; choose a new folder" }
        val inventory = priorOwners?.let { SavedPackageInventory.read(assets) }
        val assetFiles = inventory?.files ?: ProjectDistribution.inventory(assets).also {
            ProjectDistribution.identities(assets, it)
        }
        val plan = inventory?.let {
            require(startupOwner in it.scenes) { "Startup scene is missing from the saved asset inventory: $startup" }
            PackagePlanner.plan(it.assets, it.scenes, requireNotNull(priorOwners))
        }
        val errors = plan?.errors.orEmpty()
        require(errors.isEmpty()) {
            errors.joinToString("\n") { "Unresolved package dependency in ${it.source}: ${it.reference}" }
        }
        val outputs = plan?.packages?.map { assignment ->
            val scene = assignment.owner.sceneId
            val key = scene ?: "shared"
            PackageOutput(
                UUID.nameUUIDFromBytes("sle-package:$projectId:$key".encodeToByteArray()).toString(),
                if (scene == null) "shared.slepkg" else "scene-$scene.slepkg",
                assignment,
            )
        }.orEmpty()
        val runtimeInventories = runtimes.mapValues { (platform, runtime) ->
            ProjectDistribution.runtimeInventory(runtime, source.document.settings, assets, assetFiles, platform, packaged = true)
        }
        val stage=Files.createTempDirectory(target.parent,".sle-build-")
        try {
            val content = Files.createDirectory(stage.resolve(".content"))
            fun writePackage(path: String, id: String, name: String, mainScene: String?, files: Map<String, String>) {
                val manifest = buildJsonObject {
                    put("format", "sle-package")
                    put("version", 1)
                    put("id", id)
                    put("name", name)
                    put("mainScene", mainScene?.let(::JsonPrimitive) ?: JsonNull)
                    val settings = if (mainScene != null) source.document.settings.json()
                        else ProjectSettings(artStyle = source.document.settings.artStyle).json()
                    if (settings.isNotEmpty()) put("settings", settings)
                }
                try {
                    GamePackageWriter.write(content.resolve(path), manifest, assets, files)
                } catch (failure: IllegalArgumentException) {
                    throw IllegalArgumentException("$path: ${failure.message}", failure)
                }
            }
            if (plan == null) {
                writePackage(PACKAGE, source.document.id, source.document.name, startup, assetFiles.files)
            } else {
                for (output in outputs) {
                    val assignment = output.assignment
                    writePackage(output.path, output.id, assignment.name,
                        assignment.owner.sceneId?.takeIf { it == startupOwner },
                        assignment.paths.associateWith(assetFiles.files::getValue))
                }
            }
            val config=content.resolve(CONFIG)
            val packagePaths = if (plan == null) listOf(PACKAGE) else outputs.map { it.path }
            Files.writeString(config,pretty.encodeToString(JsonObject.serializer(),configJson(source.document.game,packagePaths))+"\n",StandardOpenOption.CREATE_NEW)
            val packaged = ProjectDistribution.inventory(content)
            val games = linkedMapOf<GameTarget, BuiltGame>()
            val archives = linkedMapOf<GameTarget, Path>()
            val validated = linkedMapOf<GameTarget, ProjectDistribution.Inventory>()
            val archiveHashes = linkedMapOf<Path, String>()
            for ((platform, runtime) in runtimes) {
                val runtimeInventory = runtimeInventories.getValue(platform)
                val windows = platform == GameTarget.Windows
                val runtimeFiles = runtimeInventory.inventory
                val engineVersion = runtimeInventory.engineVersion
                val executable = executableName(source.document.name, platform)
                val gameRuntime = ProjectDistribution.Inventory(runtimeFiles.folders, runtimeFiles.files - bundleOnly)
                val runtimePaths = gameRuntime.folders + gameRuntime.files.keys
                val generatedPaths = listOf(CONFIG, RELEASE, "sle-distribution.json") + packagePaths
                require(runtimePaths.none { path ->
                    val root = path.substringBefore('/')
                    generatedPaths.any { it.equals(root, ignoreCase = windows) } || root.equals(".content", ignoreCase = windows) ||
                        (executable.equals(root, ignoreCase = windows) && path != platform.launcher)
                }) { "The game launcher or generated files conflict with the selected runtime layout" }
                require(generatedPaths.none { it.equals(executable, ignoreCase = windows) } &&
                    !executable.equals(".content", ignoreCase = windows)) { "Rename the project: launcher name conflicts with generated game data" }
                if (windows) GamePackageWriter.requirePortable(executable)
                val delivery = if (withArchives) Files.createDirectory(stage.resolve(platform.id)) else stage
                ProjectDistribution.copy(runtime, delivery, gameRuntime.copy(files = gameRuntime.files - platform.launcher))
                Files.copy(runtime.resolve(platform.launcher), delivery.resolve(executable))
                if (platform == GameTarget.Linux) ExportArchive.makeExecutable(delivery.resolve(executable))
                ProjectDistribution.copy(content, delivery, packaged)
                if (!withArchives) ProjectDistribution.removeTree(content)
                val delivered = ProjectDistribution.inventory(delivery, caseInsensitive = windows)
                require(gameRuntime.files.all { (path, digest) ->
                    delivered.files[if (path == platform.launcher) executable else path] == digest
                }) { "Staged runtime differs from the selected bundle" }
                require(packaged.files.all { (path, digest) -> delivered.files[path] == digest }) {
                    "Staged game data differs from the packaged content"
                }
                if (plan != null) {
                    val idsByOwner = outputs.associate { it.assignment.owner to it.id }
                    Files.writeString(delivery.resolve(RELEASE), buildJsonObject {
                        put("format", "sle-release")
                        put("version", 1)
                        put("projectId", projectId)
                        put("platform", platform.id)
                        put("runtimeSha256", runtimeFiles.files.getValue("sle-runtime.json"))
                        engineVersion?.let { put("engineVersion", it) }
                        put("configSha256", delivered.files.getValue(CONFIG))
                        put("packages", buildJsonArray {
                            for (output in outputs) add(buildJsonObject {
                                put("id", output.id)
                                put("path", output.path)
                                put("sha256", delivered.files.getValue(output.path))
                                put("sceneId", output.assignment.owner.sceneId?.let(::JsonPrimitive) ?: JsonNull)
                            })
                        })
                        put("owners", buildJsonObject {
                            plan.owners.forEach { (asset, owner) -> put(asset, idsByOwner.getValue(owner)) }
                        })
                    }.toString() + "\n", StandardOpenOption.CREATE_NEW)
                }
                Files.writeString(delivery.resolve("sle-distribution.json"),buildJsonObject {
                    put("platform",platform.id);put("format","sle-distribution");put("version",1);put("config",CONFIG);put("executable",executable)
                    engineVersion?.let { put("engineVersion",it) };put("editorVersion",sle.editor.app.EditorVersion.text)
                    put("files",buildJsonObject {
                        delivered.files.forEach { (path,digest) -> put(path,digest) }
                        if (plan != null) put(RELEASE, ProjectDistribution.hash(delivery.resolve(RELEASE)))
                    })
                }.toString()+"\n",StandardOpenOption.CREATE_NEW)
                val staged=ProjectDistribution.inventory(delivery, caseInsensitive = windows)
                val manifestPaths = if (plan == null) setOf("sle-distribution.json") else setOf("sle-distribution.json", RELEASE)
                require(staged.folders == delivered.folders && staged.files - manifestPaths == delivered.files) {
                    "Staged build changed while recording file hashes"
                }
                validate(delivery.resolve(CONFIG))
                require(ProjectDistribution.inventory(delivery, caseInsensitive = windows) == staged) { "Staged build changed during validation" }
                validated[platform] = staged
                val finalFolder = target.resolve(stage.relativize(delivery))
                val archive = if (withArchives) ExportArchive.write(stage, delivery, platform, executable, staged) else null
                if (archive != null) {
                    archiveHashes[archive] = ProjectDistribution.hash(archive)
                    archives[platform] = target.resolve(archive.fileName)
                }
                val packages = if (plan == null) {
                    listOf(BuiltPackage(PACKAGE, source.document.name, Files.size(delivery.resolve(PACKAGE))))
                } else outputs.map { BuiltPackage(it.path, it.assignment.name, Files.size(delivery.resolve(it.path))) }
                val colocatedAssets = inventory?.assets.orEmpty()
                    .filter { plan?.reasons?.get(it.id) == PackageOwnershipReason.Colocation }
                    .map { it.path }
                    .sorted()
                games[platform] = BuiltGame(finalFolder, finalFolder.resolve(executable), finalFolder.resolve(CONFIG),
                    packages, colocatedAssets)
            }
            ProjectDistribution.removeTree(content)
            require(Files.readAllBytes(source.file).contentEquals(descriptor) && ProjectFiles.open(source.file).document==source.document &&
                source.assets.toRealPath()==assets && ProjectDistribution.inventory(assets)==assetFiles &&
                runtimes.all { (platform, runtime) ->
                    ProjectDistribution.inventory(runtime, caseInsensitive = platform == GameTarget.Windows) ==
                        runtimeInventories.getValue(platform).inventory
                }) { "Project or runtime files changed during build; retry" }
            val stagedFolders = validated.keys.associateWith { if (withArchives) stage.resolve(it.id) else stage }
            require(validated.all { (platform, expected) ->
                ProjectDistribution.inventory(stagedFolders.getValue(platform), caseInsensitive = platform == GameTarget.Windows) == expected
            } && archiveHashes.all { (path, digest) -> ProjectDistribution.hash(path) == digest }) {
                "Staged export changed during validation"
            }
            if (withArchives) Files.list(stage).use { paths ->
                require(paths.toList().toSet() == stagedFolders.values.toSet() + archiveHashes.keys) { "Unexpected files in staged export" }
            }
            Files.move(stage,target) // No replacement, including an external arrival.
            return GameExport(target, games, archives)
        } finally { ProjectDistribution.removeTree(stage) }
    }
}
