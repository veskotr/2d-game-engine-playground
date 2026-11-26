package sle.editor.smoke

import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import sle.editor.bridge.PackagedEngine
import sle.editor.model.*
import sle.editor.project.*
import sle.editor.project.assets.AssetIdentity
import sle.editor.viewport.EngineCanvas
import java.awt.*
import java.nio.file.*
import java.util.concurrent.TimeUnit
import java.util.zip.ZipFile
import javax.swing.*

internal fun distributionSmokeHost() =
    if (System.getProperty("os.name").startsWith("Windows")) GameTarget.Windows else GameTarget.Linux

internal fun distributionSmokeRuntime(target: GameTarget): Path {
    val variable = if (target == GameTarget.Windows) "SLE_SMOKE_WINDOWS_RUNTIME" else "SLE_SMOKE_LINUX_RUNTIME"
    val default = if (target == GameTarget.Windows) "build/release/runtime/Release" else "build/linux-x64-release/runtime/Release"
    return PackagedEngine.runtimeBundle(target)?.toPath()
        ?: Path.of(System.getenv(variable) ?: default).toAbsolutePath()
}

/** Runs after the existing whole-project fixture has loaded/switched/relocated. */
internal suspend fun runProjectDistributionSmoke(window: JFrame,canvas: ()->EngineCanvas,project: ProjectWorkspaceState,scene: SceneWorkspaceState,prefab: EntityWorkspaceState, notices: sle.editor.ui.EditorNotices) {
    val opened=project.opened!!
    withContext(Dispatchers.IO) {
        // Include a saved template and verify that project-local recovery data stays out of the delivery.
        ProjectDocuments(opened).save(opened.assets.resolve("Prefabs/export.prefab.json"),ProjectDocumentKind.Prefab,
            EntityDocument().serialize(),emptyMap(),null) { sle.editor.bridge.NativeEngine.canonicalPrefab(it.toByteArray()) }
        Files.createDirectories(opened.root.resolve(".sle/trash/export-sentinel"))
        Files.writeString(opened.root.resolve(".sle/trash/export-sentinel/payload"),"keep recovery data")
    }
    val reports = Path.of(System.getProperty("sle.smokeReport")).parent.toRealPath()
    val target=opened.root.resolve("Exports").resolve(opened.root.fileName)
    val host = distributionSmokeHost()
    val bundles = GameTarget.entries.associateWith(::distributionSmokeRuntime)
    for ((platform, bundle) in bundles) check(Files.isRegularFile(bundle.resolve("sle-runtime.json"))) {
        "Prepare the ${platform.label} Release bundle or set SLE_SMOKE_WINDOWS_RUNTIME and SLE_SMOKE_LINUX_RUNTIME."
    }
    val runtime = bundles.getValue(GameTarget.Windows)
    fun runtimeChoosers(targets: List<GameTarget>) = targets.filter { PackagedEngine.runtimeBundle(it) == null }
        .map { "Select prepared ${it.label} Release runtime bundle" to bundles.getValue(it) }.toTypedArray()
    fun windows(export: Path) = export.resolve(GameTarget.Windows.id)
    fun verifyTargets(export: Path): List<String> {
        val releases = GameTarget.entries.map { platform ->
            val delivery = export.resolve(platform.id)
            val release = Json.parseToJsonElement(Files.readString(delivery.resolve(GameBuild.RELEASE))).jsonObject
            check(release["platform"] == JsonPrimitive(platform.id))
            check(release["runtimeSha256"] == JsonPrimitive(ProjectDistribution.hash(bundles.getValue(platform).resolve("sle-runtime.json"))))
            val distribution = Json.parseToJsonElement(Files.readString(delivery.resolve("sle-distribution.json"))).jsonObject
            check(distribution["platform"] == JsonPrimitive(platform.id))
            release.getValue("packages").jsonArray.map { it.jsonObject }.map { entry ->
                val path = entry.getValue("path").jsonPrimitive.content
                check(entry["sha256"] == JsonPrimitive(ProjectDistribution.hash(delivery.resolve(path))))
                entry
            }
        }
        check(releases[0] == releases[1])
        check(ProjectDistribution.hash(windows(export).resolve(GameBuild.CONFIG)) ==
            ProjectDistribution.hash(export.resolve(GameTarget.Linux.id).resolve(GameBuild.CONFIG)))
        check(Files.isRegularFile(export.resolve("windows-x64.zip")) && Files.isRegularFile(export.resolve("linux-x64.tar.gz")))
        return releases.first().map { it.getValue("path").jsonPrimitive.content }
    }
    scene.addEntity();scene.selected!!.rename("Keep unsaved export draft")
    val draft=scene.serialize();val context=canvas().contextIdentity()
    val robot=Robot().apply { autoDelay=15 }
    suspend fun await(message: String,condition: ()->Boolean) {
        repeat(600) { if(condition()) { delay(80);return };delay(25) }
        val dialogs = Window.getWindows().filterIsInstance<Dialog>().filter { it.isShowing }.map { it.title }
        error("$message (busy=${project.busy}, dialogs=$dialogs, notice=${notices.recent.firstOrNull()?.text}): ${project.error}")
    }
    fun descendants(component: Component): List<Component> = listOf(component)+(if(component is Container) component.components.flatMap { descendants(it) } else emptyList())
    fun chooserAnswers(choice: String?, vararg folders: Pair<String, Path>, targets: Boolean = false): Thread = Thread {
        fun answer(description: String, respond: (List<Component>) -> Boolean) {
            repeat(500) {
                var answered = false
                SwingUtilities.invokeAndWait {
                    answered = respond(Window.getWindows().filter { it.isShowing }.flatMap(::descendants))
                }
                if (answered) return
                Thread.sleep(20)
            }
            error("$description did not open")
        }
        if (choice != null) answer("Dirty document prompt") { visible ->
            visible.filterIsInstance<JButton>().firstOrNull { it.text == choice }?.let {
                it.doClick()
                true
            } ?: false
        }
        if (targets) answer("Export target prompt") { visible ->
            visible.filterIsInstance<JButton>().firstOrNull { it.text == "Both" }?.let {
                it.doClick()
                true
            } ?: false
        }
        for ((title, path) in folders) {
            answer(title) { visible ->
                visible.filterIsInstance<JFileChooser>().firstOrNull { it.dialogTitle == title }?.let {
                    it.selectedFile = path.toFile()
                    check(it.selectedFile?.toPath() == path) { "Could not select $path in $title" }
                    it.approveSelection()
                    true
                } ?: false
            }
        }
    }.apply { isDaemon = true; start() }
    suspend fun distributionCommand(label: String, output: Path, answerDialogs: () -> Unit) {
        // Let Compose publish menu actions for document edits made by the smoke.
        repeat(2) { withFrameNanos {} }
        val file=window.jMenuBar.components.filterIsInstance<JMenu>().single { it.text=="File" }
        file.doClick();await("File menu not visible") { file.popupMenu.isShowing }
        val item=file.popupMenu.components.filterIsInstance<JMenuItem>().singleOrNull { it.text==label }
            ?: error("$label menu is missing")
        check(item.isEnabled)
        check(file.popupMenu.components.filterIsInstance<JMenuItem>().none { it.text=="Build Game…" })
        answerDialogs()
        MenuSelectionManager.defaultManager().clearSelectedPath()
        item.doClick(0)
        await("$label did not publish $output") { !project.busy && (Files.exists(output) || project.error!=null) }
    }
    suspend fun export(output: Path, choice: String = "Export Saved Versions") {
        distributionCommand("Export Game…", output) {
            chooserAnswers(choice, *runtimeChoosers(GameTarget.entries), targets = true)
        }
    }
    export(target)
    check(project.error==null && Files.exists(windows(target).resolve("sle-distribution.json"))) { "Export failed: ${project.error}" }
    check(scene.serialize()==draft && scene.dirty && canvas().contextIdentity()==context)
    val packagedConfig = Json.parseToJsonElement(Files.readString(windows(target).resolve(GameBuild.CONFIG))).jsonObject
    check(packagedConfig.getValue("packages").jsonArray.size == 4 && verifyTargets(target).size == 4)
    check(Files.exists(windows(target).resolve(GameBuild.RELEASE)) && !Files.exists(target.resolve("game")) && !Files.exists(target.resolve(".sle")))
    check(Files.readString(opened.root.resolve(".sle/trash/export-sentinel/payload"))=="keep recovery data")
    javax.imageio.ImageIO.write(robot.createScreenCapture(Rectangle(window.locationOnScreen,window.size)),"png",reports.resolve("project-distribution-editor.png").toFile())
    // Repeated menu exports must preserve the previous delivery.
    Files.writeString(target.resolve("mine.txt"),"keep")
    val next=target.resolveSibling("${target.fileName}-2")
    export(next)
    check(project.error==null && Files.exists(windows(next).resolve("sle-distribution.json")))
    check(Files.readString(target.resolve("mine.txt"))=="keep")
    check(scene.serialize()==draft && scene.dirty && canvas().contextIdentity()==context)
    project.error=null
    val relocated=withContext(Dispatchers.IO) { target.resolveSibling("Relocated Delivery").also { Files.move(target,it) } }
    val sourceScript=opened.assets.resolve("Scripts/main.lua")
    val original=withContext(Dispatchers.IO) { Files.readAllBytes(sourceScript) }
    try {
        withContext(Dispatchers.IO) { Files.writeString(sourceScript,"error('SOURCE_PROJECT_MUST_NOT_BE_USED')") }
        val log=reports.resolve("project-distribution-standalone.log")
        val result=withContext(Dispatchers.IO) {
            val extracted = Files.createTempDirectory(reports, "Extracted Delivery ")
            val archive = if (host == GameTarget.Windows) "windows-x64.zip" else "linux-x64.tar.gz"
            val extract = ProcessBuilder("tar", "-xf", relocated.resolve(archive).toString(), "-C", extracted.toString())
                .inheritIO().start()
            check(extract.waitFor(30, TimeUnit.SECONDS) && extract.exitValue() == 0) { "Could not extract $archive" }
            val delivery = extracted.resolve(host.id).toRealPath()
            check(ProjectDistribution.inventory(delivery) == ProjectDistribution.inventory(relocated.resolve(host.id)))
            val launcher = delivery.resolve(GameBuild.executableName(opened.document.name, host))
            check(host != GameTarget.Linux || Files.isExecutable(launcher))
            val process=ProcessBuilder(launcher.toString(),"--frames","8")
                .directory(reports.toFile()).redirectErrorStream(true).redirectOutput(log.toFile()).start()
            check(process.waitFor(30,TimeUnit.SECONDS)) { process.destroyForcibly();"Exported standalone timed out" }
            process.exitValue() to Files.readString(log)
        }
        check(result.first==0 && result.second.contains("scene: second-scene") && !result.second.contains("[ERROR]")) { result.toString() }
    } finally { withContext(Dispatchers.IO) { Files.write(sourceScript,original) } }
    check(scene.serialize()==draft && scene.dirty && canvas().contextIdentity()==context)
    val (savedScene, savedPrefab) = withContext(Dispatchers.IO) {
        val documents = ProjectDocuments(opened)
        val template = documents.open(opened.assets.resolve("Prefabs/export.prefab.json"))
        val source = PrefabDocument.parse(template.source)
        val link = PrefabInstance(AssetIdentity.read(AssetIdentity.sidecar(template.path)), "export-instance", source)
        val originalScene = documents.open(opened.assets.resolve("Scenes/Main.scene.json"))
        val originalDocument = SceneDocument.parse(originalScene.source)
        val linkedScene = originalDocument.copy(entities = originalDocument.entities + source.instantiate(link.key).entities)
        documents.saveLinked(originalScene.path, linkedScene, listOf(link), originalScene.assets, originalScene) { canonicalSceneDocument(it) } to template
    }
    scene.openProject(savedScene.copy(source = canonicalSceneDocument(savedScene.source)))
    val savedMarker = "Saved by game export"
    scene.addEntity()
    scene.selected!!.rename(savedMarker)
    prefab.openProject(savedPrefab)
    val templateMarker = "Template saved by game export"
    prefab.rename(templateMarker)
    // Moving the first delivery freed its original destination.
    export(target, "Save and Export")
    check(project.error == null && !scene.dirty && !prefab.dirty && canvas().contextIdentity() == context) { "Save and Export failed: ${project.error}" }
    check(scene.entities.any { it.document.name == templateMarker })
    check(templateMarker in Files.readString(savedPrefab.path))
    check(savedMarker in Files.readString(savedScene.path))
    val savedSceneId = AssetIdentity.read(AssetIdentity.sidecar(savedScene.path))
    ZipFile(windows(target).resolve("scene-$savedSceneId.slepkg").toFile()).use { archive ->
        val entry = checkNotNull(archive.getEntry("assets/Scenes/Main.scene.json"))
        check(savedMarker in archive.getInputStream(entry).bufferedReader().use { it.readText() })
        val templateEntry = checkNotNull(archive.getEntry("assets/Prefabs/export.prefab.json"))
        check(templateMarker in archive.getInputStream(templateEntry).bufferedReader().use { it.readText() })
    }
    val menuUpdate = target.resolveSibling("${target.fileName}-Update")
    distributionCommand("Export Content Update…", menuUpdate) {
        chooserAnswers(null, "Select the base game delivery" to windows(next), *runtimeChoosers(listOf(GameTarget.Windows)))
    }
    check(project.error == null && Files.exists(menuUpdate.resolve(GameContentUpdate.MANIFEST))) { project.error.orEmpty() }
    val menuTarget = next.resolve("windows-x64-Updated")
    distributionCommand("Apply Content Update…", menuTarget) {
        chooserAnswers(null, "Select the base game delivery" to windows(next), "Select the content update folder" to menuUpdate)
    }
    check(project.error == null && Files.exists(menuTarget.resolve(GameBuild.RELEASE))) { project.error.orEmpty() }
    withContext(Dispatchers.IO) {
        val sample = Path.of(System.getenv("SLE_SMOKE_TILEMAP_PROJECT") ?: "examples/tilemap_room").toAbsolutePath()
        check(Files.isRegularFile(sample.resolve("Room.sleproject"))) {
            "Set SLE_SMOKE_TILEMAP_PROJECT to a copy of examples/tilemap_room."
        }
        // Keep staged native package paths short on Windows. Export repeats the project folder name.
        val source = Files.createTempDirectory(reports, "r-").resolve("Room")
        Files.walk(sample).use { paths ->
            paths.forEach { path ->
                val destination = source.resolve(sample.relativize(path))
                if (Files.isDirectory(path)) Files.createDirectories(destination)
                else Files.copy(path, destination)
            }
        }
        val room = ProjectFiles.open(source.resolve("Room.sleproject"))
        val documents = ProjectDocuments(room)
        val template = PrefabDocument.parse(EntityDocument().serialize())
        val prefab = documents.save(room.assets.resolve("Shared.prefab.json"), ProjectDocumentKind.Prefab,
            template.serialize(), emptyMap(), null) { canonicalPrefabDocument(it) }
        val prefabId = AssetIdentity.read(AssetIdentity.sidecar(prefab.path))
        val instance = PrefabInstance(prefabId, "shared-instance", template)
        val main = documents.open(room.assets.resolve("Room.scene.json"))
        val first = SceneDocument.parse(main.source)
        val expanded = first.copy(entities = first.entities + template.instantiate(instance.key).entities)
        documents.saveLinked(main.path, expanded, listOf(instance), main.assets, main) { canonicalSceneDocument(it) }
        val second = expanded.copy(key = "second-tilemap-room", name = "Second tilemap room")
        documents.saveLinked(room.assets.resolve("Second.scene.json"), second, listOf(instance), main.assets, null) { canonicalSceneDocument(it) }

        val export = GameBuild.exportTargets(room, bundles)
        val game = export.games.getValue(GameTarget.Windows)
        check(verifyTargets(export.folder).size == 3)
        val config = Json.parseToJsonElement(Files.readString(game.config)).jsonObject
        val packages = config.getValue("packages").jsonArray.map { it.jsonPrimitive.content }
        check(packages == game.packages.map { it.path } && packages.size == 3)
        val release = Json.parseToJsonElement(Files.readString(game.folder.resolve(GameBuild.RELEASE))).jsonObject
        val shared = release.getValue("packages").jsonArray.first().jsonObject
        check(shared.getValue("path").jsonPrimitive.content == "shared.slepkg")
        check(release.getValue("owners").jsonObject[prefabId] == shared.getValue("id"))
        for (path in packages) {
            ZipFile(game.folder.resolve(path).toFile()).use { archive ->
                check((archive.getEntry("assets/Shared.prefab.json") != null) == (path == "shared.slepkg"))
            }
        }
        val moved = Files.move(game.folder, source.resolve("Moved Room"))
        val baseInventory = ProjectDistribution.inventory(moved)
        Files.writeString(prefab.path, "\n", StandardOpenOption.APPEND)
        val update = checkNotNull(GameContentUpdate.export(room, runtime, moved, game.folder.resolveSibling("Room Update")))
        check(update.delta.replaced == listOf("shared.slepkg") && update.delta.removed.isEmpty())
        val updated = GameContentUpdate.apply(moved, update.folder, source.resolve("Updated Room"))
        check(ProjectDistribution.inventory(moved) == baseInventory)

        val published = GameBuild.exportAutomatically(room, runtime)
        val before = ProjectDistribution.inventory(published.folder.parent)
        val image = room.assets.resolve("Room.png")
        val sidecar = AssetIdentity.sidecar(image)
        val missingId = AssetIdentity.readMetadata(sidecar).id
        Files.move(image, source.resolve(image.fileName))
        Files.move(sidecar, source.resolve(sidecar.fileName))

        val nativeGame = export.games.getValue(host)
        val nativeDeliveries = if (host == GameTarget.Windows) listOf(moved, updated) else listOf(nativeGame.folder)
        for (delivery in nativeDeliveries) {
            val log = source.resolve("${delivery.fileName}.log")
            val process = ProcessBuilder(delivery.resolve(nativeGame.executable.fileName).toString(), "--frames", "8")
                .directory(reports.toFile()).redirectErrorStream(true).redirectOutput(log.toFile()).start()
            if (!process.waitFor(30, TimeUnit.SECONDS)) {
                process.destroyForcibly()
                error("Tilemap room timed out: $delivery")
            }
            val output = Files.readString(log)
            check(process.exitValue() == 0 &&
                "Completed 8 frames; scene: ${room.document.startupScene}" in output && "[ERROR]" !in output) { output }
        }

        val rejected = runCatching { GameBuild.exportAutomatically(room, runtime) }
            .exceptionOrNull()
        check(rejected?.message?.contains(missingId) == true) { "Expected missing spritesheet diagnostic: $rejected" }
        check(ProjectDistribution.inventory(published.folder.parent) == before) { "Failed export changed an earlier delivery or published new files" }
    }
    println("PROJECT_DISTRIBUTION_SMOKE_PASS menu/both-targets/archive-extraction/automatic-packages/shared-template/two-tilemap-scenes/failed-export/content-update/native-launch/save-and-export/saved-sources/dirty-draft/previous-export-preservation/relocation/no-config-launch/unrelated-cwd/deferred-scene/Lua-module/source-independence delivery=$relocated")
}
