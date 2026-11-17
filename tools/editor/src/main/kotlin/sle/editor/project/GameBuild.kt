package sle.editor.project

import kotlinx.serialization.json.*
import java.nio.file.*
import java.security.MessageDigest
import java.time.LocalDateTime
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
    private val reserved=Regex("(con|prn|aux|nul|com[1-9]|lpt[1-9])(\\..*)?")

    fun requirePortable(relative: String) {
        require(relative.isNotEmpty() && relative.length<=1024-"assets/".length) { "Asset path is too long for a game package: $relative" }
        for(segment in relative.split('/')) {
            require(segment.isNotEmpty() && segment!="." && segment!=".." && segment.length<=255 && !segment.endsWith('.') && !segment.endsWith(' ') &&
                segment.none { it.code<32 || it.code==127 || it in "\\:*?\"<>|" } && !reserved.matches(segment.lowercase(java.util.Locale.ROOT))) {
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
                total+=bytes.size;require(total<=MAX_TOTAL) { "Game package exceeds 1 GiB" }
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
                require(actual==digest) { "Project files changed during build; retry" }
                entry("assets/$relative",bytes)
            }
        }
    }
}

internal data class BuiltGame(val folder: Path,val executable: Path,val config: Path)

/** Saved-source packaged build: runtime bundle + `config.json` + `game.slepkg`. */
internal object GameBuild {
    const val CONFIG="config.json"
    const val PACKAGE="game.slepkg"
    private const val ENGINE="engine_app.exe"
    // Runtime-bundle metadata that does not belong in a delivered game.
    private val bundleOnly=setOf("sle-runtime.json","RUNNING.txt")
    private val pretty=Json { prettyPrint=true }
    private val reserved=Regex("(con|prn|aux|nul|com[1-9]|lpt[1-9])(\\..*)?")

    /** The engine executable is renamed after the project so the delivery looks like the game. */
    fun executableName(projectName: String): String {
        var base=projectName.map { if(it.code<32 || it.code==127 || it in "\\/:*?\"<>|") '_' else it }.joinToString("").trim().trimEnd('.',' ')
        if(base.length>200) base=base.take(200).trimEnd('.',' ')
        if(base.isEmpty()) base="Game"
        if(reserved.matches(base.lowercase(java.util.Locale.ROOT))) base+="_"
        return "$base.exe"
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
    fun build(project: OpenedProject,runtimeBundle: Path,destination: Path,validate: (Path)->Unit): BuiltGame {
        val source=ProjectFiles.open(project.file)
        require(source.document==project.document) { "Project settings changed on disk; reopen before building" }
        val startup=requireNotNull(source.document.startupScene) { "Choose a startup scene before building" }
        val descriptor=Files.readAllBytes(source.file)
        require(!Files.isSymbolicLink(runtimeBundle)) { "Runtime bundle cannot be a link" }
        val runtime=runtimeBundle.toRealPath();val assets=sle.editor.project.assets.assetPath(source.root,source.assets)
        val raw=destination.toAbsolutePath().normalize()
        validateProjectFolderName(raw.fileName.toString())
        val target=raw.parent.toRealPath().resolve(raw.fileName)
        val sourceRoot=source.root.toRealPath()
        require(!target.startsWith(sourceRoot) && !sourceRoot.startsWith(target) && !target.startsWith(runtime) && !runtime.startsWith(target)) { "Build outside the source project and runtime bundle" }
        require(!Files.exists(target,LinkOption.NOFOLLOW_LINKS)) { "Build destination already exists; choose a new folder" }
        val runtimeFiles=ProjectDistribution.runtimeInventory(runtime)
        // Bundles built before versioning have no engineVersion.
        val engineVersion=Json.parseToJsonElement(Files.readString(runtime.resolve("sle-runtime.json"))).jsonObject["engineVersion"]?.jsonPrimitive?.content
        val executable=executableName(source.document.name)
        val gameRuntime=ProjectDistribution.Inventory(runtimeFiles.folders,runtimeFiles.files-bundleOnly)
        require(gameRuntime.files.keys.none { !it.equals(ENGINE,true) && it.equals(executable,true) }) { "Rename the project: $executable conflicts with a runtime file" }
        val assetFiles=ProjectDistribution.inventory(assets);ProjectDistribution.identities(assets,assetFiles)
        val stage=Files.createTempDirectory(target.parent,".sle-build-")
        try {
            ProjectDistribution.copy(runtime,stage,gameRuntime)
            if(executable!=ENGINE) Files.move(stage.resolve(ENGINE),stage.resolve(executable))
            val manifest=buildJsonObject {
                put("format","sle-package");put("version",1);put("id",source.document.id)
                put("name",source.document.name);put("mainScene",startup)
            }
            GamePackageWriter.write(stage.resolve(PACKAGE),manifest,assets,assetFiles.files)
            val config=stage.resolve(CONFIG)
            Files.writeString(config,pretty.encodeToString(JsonObject.serializer(),configJson(source.document.game,listOf(PACKAGE)))+"\n",StandardOpenOption.CREATE_NEW)
            val staged=ProjectDistribution.inventory(stage)
            validate(config)
            require(Files.readAllBytes(source.file).contentEquals(descriptor) && ProjectFiles.open(source.file).document==source.document &&
                source.assets.toRealPath()==assets && ProjectDistribution.inventory(assets)==assetFiles &&
                ProjectDistribution.inventory(runtime)==runtimeFiles) { "Project or runtime files changed during build; retry" }
            val delivered=ProjectDistribution.inventory(stage)
            require(delivered==staged) { "Staged build changed during validation" }
            Files.writeString(stage.resolve("sle-distribution.json"),buildJsonObject {
                put("format","sle-distribution");put("version",1);put("config",CONFIG);put("executable",executable)
                engineVersion?.let { put("engineVersion",it) };put("editorVersion",sle.editor.app.EditorVersion.text)
                put("files",buildJsonObject { delivered.files.forEach { (path,digest) -> put(path,digest) } })
            }.toString()+"\n",StandardOpenOption.CREATE_NEW)
            Files.move(stage,target) // No replacement, including an external arrival.
            return BuiltGame(target,target.resolve(executable),target.resolve(CONFIG))
        } finally { ProjectDistribution.removeTree(stage) }
    }
}
