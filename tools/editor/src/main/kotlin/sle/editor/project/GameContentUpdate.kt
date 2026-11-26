package sle.editor.project

import kotlinx.serialization.json.*
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path

internal object GameContentUpdate {
    const val MANIFEST = "sle-content-update.json"
    private const val DISTRIBUTION = "sle-distribution.json"
    private val metadata = setOf(GameBuild.CONFIG, GameBuild.RELEASE, DISTRIBUTION)
    private val sha256 = Regex("[0-9a-f]{64}")
    private val packagePath = Regex("(shared|scene-[0-9a-f-]{36})\\.slepkg")

    data class Delta(
        val added: List<String>,
        val replaced: List<String>,
        val removed: List<String>,
        val unchanged: List<String>,
    ) {
        val payload = added + replaced
    }

    data class Export(val folder: Path, val delta: Delta)

    private data class Release(
        val project: String,
        val platform: String,
        val runtime: String,
        val config: String,
        val packages: Map<String, String>,
        val owners: Map<String, PackageOwner>,
    )

    private data class Delivery(
        val release: Release,
        val executable: String,
        val runtimeFiles: Map<String, String>,
        val files: Map<String, String>,
    ) {
        val runtime = runtimeFiles - executable + ("engine_app.exe" to runtimeFiles.getValue(executable))
    }

    fun nextSibling(folder: Path, suffix: String): Path {
        val root = folder.toAbsolutePath().normalize()
        val name = "${root.fileName}-$suffix"
        var path = root.resolveSibling(name)
        var number = 2
        while (Files.exists(path, LinkOption.NOFOLLOW_LINKS)) path = root.resolveSibling("$name-${number++}")
        return path
    }

    fun export(
        project: OpenedProject,
        runtime: Path,
        baseFolder: Path,
        destination: Path,
        validate: (Path) -> Unit = GameBuild::validatePackages,
    ): Export? {
        val baseRoot = baseFolder.toAbsolutePath().normalize()
        val base = verifiedDelivery(baseRoot)
        require(base.release.project == project.document.id.lowercase() &&
            base.release.runtime == ProjectDistribution.hash(runtime.resolve("sle-runtime.json"))) {
            "Select a release of this project with the same runtime, or use Export Game for a full delivery"
        }
        val target = publicationPath(destination)
        require(!target.startsWith(baseRoot)) { "Export the update outside the base delivery" }
        val work = Files.createTempDirectory(target.parent, ".sle-update-export-")
        try {
            val game = GameBuild.buildPackages(project, runtime, work.resolve("target"), base.release.owners, validate)
            val next = verifiedDelivery(game.folder)
            requireCompatible(base, next)
            val delta = compare(base.release, next.release)
            if (delta.payload.isEmpty() && delta.removed.isEmpty() &&
                base.release.config == next.release.config && base.executable == next.executable &&
                base.files[GameBuild.RELEASE] == next.files[GameBuild.RELEASE]) return null

            val stage = Files.createDirectory(work.resolve("update"))
            val payload = metadata + delta.payload
            for (path in payload) Files.copy(game.folder.resolve(path), stage.resolve(path))
            val files = payload.associateWith(next.files::getValue)
            Files.writeString(stage.resolve(MANIFEST), buildJsonObject {
                put("format", "sle-content-update")
                put("version", 1)
                put("baseReleaseSha256", base.files.getValue(GameBuild.RELEASE))
                put("baseDistributionSha256", base.files.getValue(DISTRIBUTION))
                put("files", JsonObject(files.mapValues { JsonPrimitive(it.value) }))
                put("obsoletePackages", JsonArray(delta.removed.map(::JsonPrimitive)))
            }.toString() + "\n")
            require(ProjectDistribution.inventory(stage).files - MANIFEST == files) { "Staged update files changed" }
            require(verifiedDelivery(baseRoot) == base) { "Base delivery changed during export" }
            Files.move(stage, target)
            return Export(target, delta)
        } finally {
            ProjectDistribution.removeTree(work)
        }
    }

    fun apply(
        baseFolder: Path,
        updateFolder: Path,
        destination: Path,
        validate: (Path) -> Unit = GameBuild::validatePackages,
    ): Path {
        val baseRoot = baseFolder.toAbsolutePath().normalize()
        val updateRoot = updateFolder.toAbsolutePath().normalize()
        val target = publicationPath(destination)
        require(target.parent == baseRoot.parent && !target.startsWith(updateRoot)) {
            "Apply the update to a new sibling of the base delivery"
        }
        val base = verifiedDelivery(baseRoot)
        val updateFiles = ProjectDistribution.inventory(updateRoot).files
        val update = manifest(updateRoot, updateFiles, MANIFEST, "sle-content-update")
        require(update["baseReleaseSha256"] == JsonPrimitive(base.files.getValue(GameBuild.RELEASE)) &&
            update["baseDistributionSha256"] == JsonPrimitive(base.files.getValue(DISTRIBUTION))) {
            "This content update requires a different base delivery"
        }
        require(fileRecords(update) == updateFiles - MANIFEST) { "Update files are missing, modified or unexpected" }
        val next = readDelivery(updateRoot, updateFiles)
        requireCompatible(base, next)
        val delta = compare(base.release, next.release)
        require(update["obsoletePackages"] == JsonArray(delta.removed.map(::JsonPrimitive)) &&
            updateFiles.keys == metadata + delta.payload + MANIFEST &&
            delta.payload.all { updateFiles[it] == next.release.packages[it] }) {
            "Update payload does not match the target release"
        }
        val stage = Files.createTempDirectory(target.parent, ".sle-update-apply-")
        try {
            for (path in next.files.keys) {
                val source = when {
                    path in updateFiles -> updateRoot.resolve(path)
                    path == next.executable -> baseRoot.resolve(base.executable)
                    else -> baseRoot.resolve(path)
                }
                val output = stage.resolve(path)
                Files.createDirectories(output.parent)
                Files.copy(source, output)
            }
            require(verifiedDelivery(stage) == next) { "Reconstructed delivery differs from the target inventory" }
            validate(stage.resolve(GameBuild.CONFIG))
            require(verifiedDelivery(stage) == next) { "Reconstructed delivery changed during validation" }
            Files.move(stage, target)
            return target
        } finally {
            ProjectDistribution.removeTree(stage)
        }
    }

    private fun publicationPath(destination: Path): Path {
        val path = destination.toAbsolutePath().normalize()
        validateProjectFolderName(path.fileName.toString())
        require(path.parent.toRealPath() == path.parent && !Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
            "Choose a new delivery folder with an existing, link-free parent"
        }
        return path
    }

    private fun compare(base: Release, target: Release) = Delta(
        added = target.packages.keys.filter { it !in base.packages },
        replaced = target.packages.keys.filter { it in base.packages && target.packages[it] != base.packages[it] },
        removed = base.packages.keys.filter { it !in target.packages },
        unchanged = target.packages.keys.filter { target.packages[it] == base.packages[it] },
    )

    private fun requireCompatible(base: Delivery, target: Delivery) {
        require(base.release.project == target.release.project && base.release.platform == target.release.platform &&
            base.release.runtime == target.release.runtime && base.runtime == target.runtime) {
            "The project or shipped runtime differs from the base. Use Export Game for a full delivery"
        }
    }

    private fun verifiedDelivery(root: Path): Delivery {
        val actual = ProjectDistribution.inventory(root).files
        return readDelivery(root, actual).also {
            require(it.files == actual) { "Delivery files are missing, modified or unexpected: $root" }
        }
    }

    private fun readDelivery(root: Path, actual: Map<String, String>): Delivery {
        val release = manifest(root, actual, GameBuild.RELEASE, "sle-release")
        val platform = release.getValue("platform").jsonPrimitive.content
        require(platform == GameTarget.Windows.id) { "Content updates support Windows x64 only. Export a full game for Linux x64." }
        val distribution = manifest(root, actual, DISTRIBUTION, "sle-distribution")
        require(distribution["platform"]?.let { it == JsonPrimitive(platform) } != false) {
            "Release platform differs from the delivery inventory"
        }
        val files = fileRecords(distribution)
        require(files[GameBuild.RELEASE] == actual[GameBuild.RELEASE] &&
            files[GameBuild.CONFIG] == actual[GameBuild.CONFIG] &&
            release["configSha256"] == JsonPrimitive(files.getValue(GameBuild.CONFIG)) &&
            distribution["config"] == JsonPrimitive(GameBuild.CONFIG) && DISTRIBUTION !in files &&
            MANIFEST !in files) { "Release or launch config differs from the recorded inventory" }

        val packages = linkedMapOf<String, String>()
        val packageOwners = mutableMapOf<String, PackageOwner>()
        for (entry in release.getValue("packages").jsonArray) {
            val record = entry.jsonObject
            val path = record.getValue("path").jsonPrimitive.content
            val digest = record.getValue("sha256").jsonPrimitive.content
            val scene = record.getValue("sceneId").jsonPrimitive.contentOrNull
            require(packagePath.matches(path) && path == (scene?.let { "scene-$it.slepkg" } ?: "shared.slepkg") &&
                packages.put(path, digest) == null && files[path] == digest &&
                packageOwners.put(record.getValue("id").jsonPrimitive.content, PackageOwner(scene)) == null) {
                "Invalid package inventory entry: $path"
            }
        }
        val config = Json.parseToJsonElement(Files.readString(root.resolve(GameBuild.CONFIG))).jsonObject
        require(config.keys == setOf("window", "targetFPS", "packages") && packages.isNotEmpty() &&
            config["packages"] == JsonArray(packages.keys.map(::JsonPrimitive)) &&
            files.keys.filter { it.endsWith(".slepkg", ignoreCase = true) }.toSet() == packages.keys) {
            "Launch config does not match the release package inventory"
        }
        val executable = distribution.getValue("executable").jsonPrimitive.content
        val runtime = files - packages.keys - metadata
        require('/' !in executable && executable.endsWith(".exe") && executable in runtime) { "Missing game executable" }
        val owners = release.getValue("owners").jsonObject.mapValues { packageOwners.getValue(it.value.jsonPrimitive.content) }
        return Delivery(
            Release(release.getValue("projectId").jsonPrimitive.content, platform,
                release.getValue("runtimeSha256").jsonPrimitive.content, files.getValue(GameBuild.CONFIG), packages, owners),
            executable, runtime, files + (DISTRIBUTION to actual.getValue(DISTRIBUTION)),
        )
    }

    private fun manifest(root: Path, actual: Map<String, String>, name: String, format: String): JsonObject {
        require(name in actual) { "Missing $name in $root" }
        return Json.parseToJsonElement(Files.readString(root.resolve(name))).jsonObject.also {
            require(it["format"] == JsonPrimitive(format) && it["version"] == JsonPrimitive(1)) {
                "Unsupported $name format or version"
            }
        }
    }

    private fun fileRecords(manifest: JsonObject) = manifest.getValue("files").jsonObject.mapValues { (path, value) ->
        GamePackageWriter.requirePortable(path)
        value.jsonPrimitive.content.also { require(sha256.matches(it)) { "Invalid digest for $path" } }
    }
}
