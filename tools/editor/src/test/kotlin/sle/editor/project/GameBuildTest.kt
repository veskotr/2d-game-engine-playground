package sle.editor.project

import kotlinx.serialization.json.*
import sle.editor.project.assets.AssetIdentity
import java.nio.file.*
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import kotlin.test.*

class GameBuildTest {
    private fun project(): OpenedProject {
        val p=ProjectFiles.create(Files.createTempDirectory("sle-build-").resolve("Game"),"Game")
        Files.createDirectories(p.assets.resolve("Scripts/modules"))
        Files.writeString(p.assets.resolve("Scripts/modules/value.lua"),"return 42")
        val saved=ProjectDocuments(p).save(p.assets.resolve("Scenes/start.scene.json"),ProjectDocumentKind.Scene,"{\"key\":\"start\",\"entities\":[]}",emptyMap(),null) {}
        AssetIdentity.ensure(p.assets.resolve("Scripts/modules/value.lua"))
        return ProjectFiles.save(p,p.document.copy(startupScene=AssetIdentity.read(AssetIdentity.sidecar(saved.path)),
            game=ProjectGameSettings(title="Packaged",width=640,height=360,vsync=false,mode="Windowed",targetFPS=30)))
    }
    private fun bundle(engineVersion: String? = "0.26.0", target: String = "windows-x64"): Path {
        val root=Files.createTempDirectory("sle-runtime-")
        val executable = if (target == "linux-x64") "engine_app" else "engine_app.exe"
        Files.write(root.resolve(executable), runtimeFixtureLauncher(target == "linux-x64"))
        Files.createDirectories(root.resolve("assets/shaders"));Files.writeString(root.resolve("assets/shaders/sprite.vert"),"shader")
        Files.writeString(root.resolve("msvcp140.dll"),"crt")
        Files.writeString(root.resolve("sle-runtime.json"),buildJsonObject {
            put("format","sle-runtime");put("version",1);engineVersion?.let { put("engineVersion", it) };put("platform",target);put("configuration","Release");put("executable",executable)
            put("files",buildJsonObject {
                Files.walk(root).use { paths -> paths.filter { Files.isRegularFile(it) }.forEach { file ->
                    put(root.relativize(file).toString().replace('\\','/'),ProjectDistribution.hash(file))
                } }
            })
        }.toString())
        return root
    }

    @Test fun exportsBothTargetsWithIdenticalPackagesAndPublishesAllOrNeither() {
        val p = project()
        val windows = bundle()
        val linux = bundle("0.33.0", "linux-x64")
        val bundles = mapOf(GameTarget.Windows to windows, GameTarget.Linux to linux)
        val result = GameBuild.exportTargets(p, bundles, validate = {})
        val win = result.games.getValue(GameTarget.Windows)
        val lin = result.games.getValue(GameTarget.Linux)
        assertEquals("Game.exe", win.executable.fileName.toString())
        assertEquals("Game", lin.executable.fileName.toString())
        for (file in win.packages.map { it.path } + GameBuild.CONFIG) {
            assertEquals(ProjectDistribution.hash(win.folder.resolve(file)), ProjectDistribution.hash(lin.folder.resolve(file)))
        }
        val archive = result.archives.getValue(GameTarget.Windows)
        ZipFile(archive.toFile()).use { zip ->
            val entry = zip.getEntry("windows-x64/Game.exe")
            assertNotNull(entry)
            assertContentEquals(runtimeFixtureLauncher(), zip.getInputStream(entry).use { it.readAllBytes() })
        }
        assertTrue(Files.size(result.archives.getValue(GameTarget.Linux)) > 0)
        for ((target, game) in result.games) {
            val manifest = Json.parseToJsonElement(Files.readString(game.folder.resolve(GameBuild.RELEASE))).jsonObject
            assertEquals(target.id, manifest.getValue("platform").jsonPrimitive.content)
            assertEquals(ProjectDistribution.hash(bundles.getValue(target).resolve("sle-runtime.json")),
                manifest.getValue("runtimeSha256").jsonPrimitive.content)
        }
        val before = ProjectDistribution.inventory(result.folder.parent)
        assertFailsWith<IllegalStateException> {
            GameBuild.exportTargets(p, bundles, validate = {
                if (it.parent.fileName.toString() == "linux-x64") error("second target failed")
            })
        }
        assertEquals(before, ProjectDistribution.inventory(result.folder.parent))
    }

    @Test fun verifiesTargetAndVersionAndAcceptsLegacyWindows() {
        val p = project()
        val win = bundle("0.16.0")
        val linux = bundle("0.33.0", "linux-x64")
        fun export(target: GameTarget, bundle: Path) =
            GameBuild.exportTargets(p, mapOf(target to bundle), validate = {})
        export(GameTarget.Windows, win)
        assertFailsWith<IllegalArgumentException> { export(GameTarget.Windows, bundle(null)) }
        assertFailsWith<IllegalArgumentException> { export(GameTarget.Windows, bundle("0.15.0")) }
        assertFailsWith<IllegalArgumentException> { export(GameTarget.Linux, win) }
        assertFailsWith<IllegalArgumentException> { export(GameTarget.Windows, linux) }
        assertFailsWith<IllegalArgumentException> { export(GameTarget.Linux, bundle("0.32.0", "linux-x64")) }
        Files.writeString(linux.resolve("engine_app"), "modified")
        assertFailsWith<IllegalArgumentException> { export(GameTarget.Linux, linux) }
    }

    @Test fun rejectsABundleWithTheWrongLauncherEvenIfItsHashMatches() {
        val p = project()
        val runtime = bundle("0.33.0", "linux-x64")
        val launcher = runtime.resolve("engine_app")
        val linuxHash = ProjectDistribution.hash(launcher)
        Files.write(launcher, runtimeFixtureLauncher())
        val manifest = runtime.resolve("sle-runtime.json")
        Files.writeString(manifest, Files.readString(manifest).replace(linuxHash, ProjectDistribution.hash(launcher)))
        val failure = assertFailsWith<IllegalArgumentException> {
            GameBuild.exportTargets(p, mapOf(GameTarget.Linux to runtime), validate = {})
        }
        assertContains(failure.message.orEmpty(), "Linux x64 launcher")
    }

    @Test fun linuxRuntimePathsCanDifferInCaseOnACaseSensitiveHost() {
        if (System.getProperty("os.name").startsWith("Windows")) return
        val p = project()
        val runtime = bundle("0.33.0", "linux-x64")
        val first = "assets/shaders/sprite.vert"
        val second = "assets/shaders/SPRITE.vert"
        if (Files.exists(runtime.resolve(second))) return
        Files.writeString(runtime.resolve(second), "a second shader")
        val manifest = runtime.resolve("sle-runtime.json")
        val original = Json.parseToJsonElement(Files.readString(manifest)).jsonObject
        val files = JsonObject(original.getValue("files").jsonObject +
            (second to JsonPrimitive(ProjectDistribution.hash(runtime.resolve(second)))))
        Files.writeString(manifest, JsonObject(original + ("files" to files)).toString())
        val result = GameBuild.exportTargets(p, mapOf(GameTarget.Linux to runtime), validate = {})
        val delivery = result.games.getValue(GameTarget.Linux).folder
        assertEquals("shader", Files.readString(delivery.resolve(first)))
        assertEquals("a second shader", Files.readString(delivery.resolve(second)))
        assertFailsWith<IllegalArgumentException> { GameContentUpdate.apply(delivery, runtime, delivery.resolveSibling("update")) }
    }

    @Test fun verifiesWindowsRuntimeNamesAndCaseFoldedPaths() {
        val p = project()
        val runtime = bundle()
        fun add(path: String) {
            Files.writeString(runtime.resolve(path), path)
            val inventory = runtime.resolve("sle-runtime.json")
            Files.writeString(inventory, Files.readString(inventory).replace("\"files\":{",
                "\"files\":{\"$path\":\"${ProjectDistribution.hash(runtime.resolve(path))}\","))
        }
        if (!System.getProperty("os.name").startsWith("Windows")) {
            add("con.txt")
            val error = assertFailsWith<IllegalArgumentException> {
                GameBuild.exportTargets(p, mapOf(GameTarget.Windows to runtime), validate = {})
            }
            assertContains(error.message.orEmpty(), "con.txt")
            Files.delete(runtime.resolve("con.txt"))
            add("MSVCP140.dll")
            assertFailsWith<IllegalArgumentException> {
                GameBuild.exportTargets(p, mapOf(GameTarget.Windows to runtime), validate = {})
            }
        }
        val malformed = bundle()
        val manifest = malformed.resolve("sle-runtime.json")
        Files.writeString(manifest, Files.readString(manifest).replace(
            "\"executable\":\"engine_app.exe\"", "\"executable\":\"msvcp140.dll\""))
        assertFailsWith<IllegalArgumentException> {
            GameBuild.exportTargets(p, mapOf(GameTarget.Windows to malformed), validate = {})
        }
    }

    @Test fun nativeTileScenesRequireATileCapableWindowsRuntime() {
        val p = project()
        val scene = p.assets.resolve("Scenes/start.scene.json")
        val saved = Json.parseToJsonElement(Files.readString(scene)).jsonObject
        val document = JsonObject(saved.getValue("document").jsonObject +
            ("tileLayers" to JsonArray(listOf(buildJsonObject { put("name", "Tiles") }))))
        Files.writeString(scene, JsonObject(saved + ("document" to document)).toString())
        val error = assertFailsWith<IllegalArgumentException> {
            GameBuild.build(p, bundle("0.32.0"), p.root.resolveSibling("Old Runtime")) {}
        }
        assertContains(error.message.orEmpty(), "Native tile layers")
    }

    @Test fun packagedExportNumbersDeliveriesAndPreservesThemOnFailure() {
        val p = project()
        val runtime = bundle()
        val first = GameBuild.exportAutomatically(p, runtime, validate = {})
        val second = GameBuild.exportAutomatically(p, runtime, validate = {})
        val exports = p.root.resolve("Exports").toRealPath()
        assertEquals(exports.resolve("Game"), first.folder)
        assertEquals(exports.resolve("Game-2"), second.folder)
        assertEquals(3, first.packages.size)
        assertTrue(first.packages.any { it.path == "shared.slepkg" })
        first.packages.forEach { assertEquals(Files.size(first.folder.resolve(it.path)), it.bytes) }
        val before = ProjectDistribution.inventory(exports)
        assertFailsWith<IllegalStateException> {
            GameBuild.exportAutomatically(p, runtime, validate = { error("Native validation failed") })
        }
        assertEquals(before, ProjectDistribution.inventory(exports))
        assertFailsWith<IllegalArgumentException> {
            GameBuild.exportAutomatically(p, exports, validate = {})
        }
    }

    @Test fun automaticBuildStagesDeterministicPackagesAndReleaseInventory() {
        val original = project()
        val mixedCaseDescriptor = original.document.copy(
            id = original.document.id.uppercase(),
            startupScene = original.document.startupScene?.uppercase(),
            settings = ProjectSettings(artStyle = ProjectArtStyle.PixelArt)
        )
        Files.writeString(original.file, mixedCaseDescriptor.serialize())
        val p = ProjectFiles.open(original.file)
        val runtime = bundle()
        val target = p.root.resolveSibling("Automatic")
        val startup = assertNotNull(p.document.startupScene).lowercase()
        val starter = AssetIdentity.read(AssetIdentity.sidecar(p.assets.resolve("Scenes/Main.scene.json")))
        val packages = listOf("shared.slepkg") + listOf(startup, starter).sorted().map { "scene-$it.slepkg" }
        val built = GameBuild.buildPackages(p, runtime, target, validate = { config ->
            assertFalse(Files.exists(target))
            assertTrue(Files.exists(config.resolveSibling(GameBuild.RELEASE)))
            assertEquals(JsonArray(packages.map(::JsonPrimitive)),
                Json.parseToJsonElement(Files.readString(config)).jsonObject["packages"])
            for ((index, path) in packages.withIndex()) {
                ZipFile(config.resolveSibling(path).toFile()).use { zip ->
                    val manifest = Json.parseToJsonElement(zip.getInputStream(zip.getEntry("manifest.json"))
                        .bufferedReader().use { it.readText() }).jsonObject
                    assertEquals(if (path == "scene-$startup.slepkg") JsonPrimitive(startup) else JsonNull, manifest["mainScene"])
                    assertEquals("pixelArt", manifest.getValue("settings").jsonObject.getValue("artStyle").jsonPrimitive.content)
                    assertEquals(index == 0, zip.getEntry("assets/Scripts/modules/value.lua") != null)
                }
            }
        })
        val release = Json.parseToJsonElement(Files.readString(target.resolve(GameBuild.RELEASE))).jsonObject
        assertEquals(original.document.id, release.getValue("projectId").jsonPrimitive.content)
        val packageRecords = release.getValue("packages").jsonArray.map { it.jsonObject }
        assertEquals(packages, packageRecords.map { it.getValue("path").jsonPrimitive.content })
        assertEquals(packages.size, packageRecords.map { it.getValue("id") }.toSet().size)
        for ((path, record) in packages.zip(packageRecords)) {
            assertEquals(ProjectDistribution.hash(target.resolve(path)), record.getValue("sha256").jsonPrimitive.content)
        }
        val moduleId = AssetIdentity.read(AssetIdentity.sidecar(p.assets.resolve("Scripts/modules/value.lua")))
        assertEquals(packageRecords.first().getValue("id"), release.getValue("owners").jsonObject[moduleId])
        val repeatTarget = target.resolveSibling("Again")
        val nativeLibrary = System.getenv("SLE_GAME_BUILD_TEST_NATIVE")
        val again = if (nativeLibrary == null) {
            GameBuild.buildPackages(p, runtime, repeatTarget, validate = {})
        } else {
            val previous = System.setProperty("sle.nativeLibrary", nativeLibrary)
            try {
                GameBuild.buildPackages(p, runtime, repeatTarget).also { println("Validated staged packages through prepareGame") }
            } finally {
                if (previous == null) System.clearProperty("sle.nativeLibrary")
                else System.setProperty("sle.nativeLibrary", previous)
            }
        }
        for (path in packages + listOf(GameBuild.CONFIG, GameBuild.RELEASE)) {
            assertEquals(ProjectDistribution.hash(built.folder.resolve(path)), ProjectDistribution.hash(again.folder.resolve(path)))
        }
    }

    @Test fun automaticBuildRejectsStageTamperingAndPreservesOccupiedDestination() {
        val p = project()
        val runtime = bundle()
        val target = p.root.resolveSibling("Automatic")
        assertFailsWith<IllegalArgumentException> {
            GameBuild.buildPackages(p, runtime, target, validate = {
                Files.writeString(it.resolveSibling(GameBuild.RELEASE), "tampered")
            })
        }
        assertFalse(Files.exists(target))
        val module = p.assets.resolve("Scripts/modules/value.lua")
        assertFailsWith<IllegalArgumentException> {
            GameBuild.buildPackages(p, runtime, target, validate = { Files.writeString(module, "return 99") })
        }
        assertFalse(Files.exists(target))
        assertFailsWith<FileAlreadyExistsException> {
            GameBuild.buildPackages(p, runtime, target, validate = {
                Files.createDirectory(target)
                Files.writeString(target.resolve("existing"), "keep")
            })
        }
        assertEquals("keep", Files.readString(target.resolve("existing")))
    }

    @Test fun buildKeepsPhysicalPathsTogetherAndRejectsDuplicateIdentities() {
        val initial = project()
        val map = initial.assets.resolve("Data/map.tmj")
        val image = initial.assets.resolve("Textures/map.png")
        Files.writeString(map, """{"tilesets":[{"image":"../Textures/map.png"}]}""")
        Files.writeString(image, "fixture image")
        AssetIdentity.ensure(map)
        AssetIdentity.ensure(image)
        val scene = ProjectDocuments(initial).save(
            initial.assets.resolve("Scenes/Map.scene.json"), ProjectDocumentKind.Scene,
            """{"key":"map","entities":[]}""", mapOf(map to "Map"), null,
        ) {}
        val sceneId = AssetIdentity.read(AssetIdentity.sidecar(scene.path))
        val p = ProjectFiles.save(initial, initial.document.copy(startupScene = sceneId))
        val runtime = bundle()
        val target = p.root.resolveSibling("With Map")
        val built = GameBuild.buildPackages(p, runtime, target, validate = {})
        ZipFile(built.folder.resolve("scene-$sceneId.slepkg").toFile()).use { archive ->
            assertNotNull(archive.getEntry("assets/Data/map.tmj"))
            assertNotNull(archive.getEntry("assets/Textures/map.png"))
            assertNotNull(archive.getEntry("assets/Textures/map.png.slemeta"))
        }
        Files.copy(AssetIdentity.sidecar(map), AssetIdentity.sidecar(image), StandardCopyOption.REPLACE_EXISTING)
        val rejected = target.resolveSibling("Duplicate")
        val failure = assertFailsWith<IllegalArgumentException> {
            GameBuild.buildPackages(p, runtime, rejected, validate = { fail("Invalid sources reached native validation") })
        }
        assertContains(failure.message.orEmpty(), "Duplicate asset UUID")
        assertFalse(Files.exists(rejected))
    }

    @Test fun packageWriterRejectsVersionOneEntryLimits() {
        val root = Files.createTempDirectory("sle-package-limits-")
        val out = root.resolve("limit.slepkg")
        val many = (1..GamePackageWriter.MAX_ENTRIES).associate { "$it.txt" to "" }
        val countError = assertFailsWith<IllegalArgumentException> {
            GamePackageWriter.write(out, buildJsonObject {}, root, many)
        }
        assertContains(countError.message.orEmpty(), "65000")
        assertFalse(Files.exists(out))
        assertFailsWith<IllegalArgumentException> { GamePackageWriter.requirePortable("é".repeat(128) + ".txt") }
    }

    @Test fun buildsConfigAndStoredPackageWithMainSceneSidecarsAndNoLocalState() {
        val legacy = project()
        val p = ProjectFiles.save(legacy, legacy.document.copy(settings = ProjectSettings(ProjectArtStyle.PixelArt)))
        val runtime=bundle()
        Files.createDirectories(p.root.resolve(".sle/trash/test"));Files.writeString(p.root.resolve(".sle/trash/test/payload"),"deleted")
        val target=p.root.resolveSibling("Build")
        var validated: Path?=null
        val built=GameBuild.build(p,runtime,target) { staged ->
            validated=staged
            assertTrue(Files.exists(staged.resolveSibling(GameBuild.PACKAGE)))
        }
        val config=built.config
        assertEquals(target.resolve("config.json"),config);assertNotEquals(config,validated)
        val json=Json.parseToJsonElement(Files.readString(config)).jsonObject
        assertEquals(JsonArray(listOf(JsonPrimitive("game.slepkg"))),json["packages"])
        assertEquals(30,json.getValue("targetFPS").jsonPrimitive.int)
        assertEquals("Packaged",json.getValue("window").jsonObject.getValue("title").jsonPrimitive.content)
        assertFalse("scenes" in json || "startScene" in json)
        ZipFile(target.resolve("game.slepkg").toFile()).use { zip ->
            val entries=zip.entries().toList()
            assertEquals("manifest.json",entries.first().name)
            assertTrue(entries.all { it.method==ZipEntry.STORED })
            val manifest=Json.parseToJsonElement(zip.getInputStream(zip.getEntry("manifest.json")).readBytes().decodeToString()).jsonObject
            assertEquals("sle-package",manifest.getValue("format").jsonPrimitive.content)
            assertEquals(p.document.startupScene,manifest.getValue("mainScene").jsonPrimitive.content)
            assertEquals(p.document.id,manifest.getValue("id").jsonPrimitive.content)
            assertEquals("pixelArt", manifest.getValue("settings").jsonObject.getValue("artStyle").jsonPrimitive.content)
            val names=entries.map { it.name }.toSet()
            assertTrue("assets/Scripts/modules/value.lua" in names && "assets/Scripts/modules/value.lua.slemeta" in names)
            assertTrue("assets/Scenes/start.scene.json.slemeta" in names)
            assertTrue(names.none { it.contains(".sle/") || it.contains("trash") })
            assertEquals("return 42",zip.getInputStream(zip.getEntry("assets/Scripts/modules/value.lua")).readBytes().decodeToString())
        }
        // The engine executable carries the project name; runtime-bundle metadata is not delivered.
        assertEquals(target.resolve("Game.exe"),built.executable);assertContentEquals(runtimeFixtureLauncher(), Files.readAllBytes(built.executable))
        assertFalse(Files.exists(target.resolve("engine_app.exe")) || Files.exists(target.resolve("sle-runtime.json")))
        assertTrue(Files.exists(target.resolve("msvcp140.dll")) && Files.exists(target.resolve("sle-distribution.json")))
        val distribution=Json.parseToJsonElement(Files.readString(target.resolve("sle-distribution.json"))).jsonObject
        assertEquals("Game.exe",distribution.getValue("executable").jsonPrimitive.content)
        // Versions of the engine runtime and the editor that produced the build.
        assertEquals("0.26.0",distribution.getValue("engineVersion").jsonPrimitive.content)
        assertTrue(Regex("0\\.\\d+\\.\\d+").matches(distribution.getValue("editorVersion").jsonPrimitive.content))
        assertFalse(Files.exists(target.resolve("game")))
        // Rebuilding identical sources yields an identical package.
        val again=GameBuild.build(p,runtime,target.resolveSibling("Build Again")) {}
        assertContentEquals(Files.readAllBytes(target.resolve("game.slepkg")),Files.readAllBytes(again.folder.resolve("game.slepkg")))
    }

    @Test fun styledExportsRejectOlderRuntimeBundles() {
        val legacy = project()
        val styled = ProjectFiles.save(legacy, legacy.document.copy(settings = ProjectSettings(ProjectArtStyle.Smooth)))
        val runtime = bundle("0.25.0")
        val packageOutput = styled.root.resolveSibling("Package")
        val sourceOutput = styled.root.resolveSibling("Export")
        assertFailsWith<IllegalArgumentException> { GameBuild.build(styled, runtime, packageOutput) {} }
        assertFailsWith<IllegalArgumentException> { ProjectDistribution.export(styled, runtime, sourceOutput) {} }
        assertFalse(Files.exists(packageOutput))
        assertFalse(Files.exists(sourceOutput))
        val exported = ProjectDistribution.export(styled, bundle(), sourceOutput) {}
        val relocated = Files.move(sourceOutput, sourceOutput.resolveSibling("Relocated"))
        assertEquals(styled.document, ProjectFiles.open(relocated.resolve(sourceOutput.relativize(exported))).document)
    }

    @Test fun collisionSettingsTravelWithBothExportFormats() {
        val legacy = project()
        val settings = ProjectSettings(collisionLayers = CollisionLayers().withLabel(0, "Player").withPair(0, 15, false))
        val p = ProjectFiles.save(legacy, legacy.document.copy(settings = settings))
        val target = p.root.resolveSibling("Package")
        assertFailsWith<IllegalArgumentException> { GameBuild.build(p, bundle(), target) {} }
        val runtime = bundle("0.27.0")
        GameBuild.build(p, runtime, target) {}
        ZipFile(target.resolve(GameBuild.PACKAGE).toFile()).use { zip ->
            val manifest = zip.getInputStream(zip.getEntry("manifest.json")).use {
                Json.parseToJsonElement(it.readBytes().decodeToString()).jsonObject
            }
            assertEquals(settings.json(), manifest["settings"])
        }
        val export = ProjectDistribution.export(p, runtime, p.root.resolveSibling("Export")) {}
        assertEquals(settings, ProjectFiles.open(export).document.settings)
    }

    @Test fun sortingLayerLabelsTravelWithBothExportFormats() {
        val legacy = project()
        val settings = ProjectSettings(sortingLayers = mapOf(-4 to "Backdrop", 9 to "Foreground"))
        val p = ProjectFiles.save(legacy, legacy.document.copy(settings = settings))
        val target = p.root.resolveSibling("Package")
        val source = p.root.resolveSibling("Export")
        val oldRuntime = bundle("0.27.0")
        assertFailsWith<IllegalArgumentException> { GameBuild.build(p, oldRuntime, target) {} }
        assertFailsWith<IllegalArgumentException> { ProjectDistribution.export(p, oldRuntime, source) {} }

        val runtime = bundle("0.28.0")
        GameBuild.build(p, runtime, target) {}
        ZipFile(target.resolve(GameBuild.PACKAGE).toFile()).use { zip ->
            val manifest = zip.getInputStream(zip.getEntry("manifest.json")).use {
                Json.parseToJsonElement(it.readBytes().decodeToString()).jsonObject
            }
            assertEquals(settings.json(), manifest["settings"])
        }
        val exported = ProjectDistribution.export(p, runtime, source) {}
        assertEquals(settings, ProjectFiles.open(exported).document.settings)
    }

    @Test fun tagsTravelWithBothExportFormats() {
        val legacy = project()
        val settings = ProjectSettings(tags = mapOf("actor-id" to "Actor"))
        val project = ProjectFiles.save(legacy, legacy.document.copy(settings = settings))
        val target = project.root.resolveSibling("Package")
        val source = project.root.resolveSibling("Export")
        val oldRuntime = bundle("0.28.0")
        assertFailsWith<IllegalArgumentException> { GameBuild.build(project, oldRuntime, target) {} }
        assertFailsWith<IllegalArgumentException> { ProjectDistribution.export(project, oldRuntime, source) {} }

        val runtime = bundle("0.29.0")
        GameBuild.build(project, runtime, target) {}
        ZipFile(target.resolve(GameBuild.PACKAGE).toFile()).use { zip ->
            val manifest = zip.getInputStream(zip.getEntry("manifest.json")).use {
                Json.parseToJsonElement(it.readBytes().decodeToString()).jsonObject
            }
            assertEquals(settings.json(), manifest["settings"])
        }
        val exported = ProjectDistribution.export(project, runtime, source) {}
        assertEquals(settings, ProjectFiles.open(exported).document.settings)
    }

    @Test fun inputActionsTravelWithBothExportFormats() {
        val legacy = project()
        val settings = ProjectSettings(inputActions = mapOf("Jump" to listOf(KeyboardBinding(32), KeyboardBinding(87))))
        val project = ProjectFiles.save(legacy, legacy.document.copy(settings = settings))
        val target = project.root.resolveSibling("Package")
        val source = project.root.resolveSibling("Export")
        val oldRuntime = bundle("0.29.0")
        assertFailsWith<IllegalArgumentException> { GameBuild.build(project, oldRuntime, target) {} }
        assertFailsWith<IllegalArgumentException> { ProjectDistribution.export(project, oldRuntime, source) {} }

        val runtime = bundle("0.30.0")
        GameBuild.build(project, runtime, target) {}
        ZipFile(target.resolve(GameBuild.PACKAGE).toFile()).use { zip ->
            val manifest = zip.getInputStream(zip.getEntry("manifest.json")).use {
                Json.parseToJsonElement(it.readBytes().decodeToString()).jsonObject
            }
            assertEquals(settings.json(), manifest["settings"])
        }
        val exported = ProjectDistribution.export(project, runtime, source) {}
        assertEquals(settings, ProjectFiles.open(exported).document.settings)
    }

    @Test fun unresolvedMembershipStillRequiresATagCapableBundle() {
        val project = project()
        val path = project.assets.resolve("Scenes/start.scene.json")
        val documents = ProjectDocuments(project)
        val tagged = """{"key":"start","entities":[{"key":"actor","components":{"Tags":{"ids":["unresolved"]}}}]}"""
        documents.save(path, ProjectDocumentKind.Scene, tagged, emptyMap(), documents.open(path)) {}
        val target = project.root.resolveSibling("Package")
        val source = project.root.resolveSibling("Export")
        val oldRuntime = bundle("0.28.0")
        assertFailsWith<IllegalArgumentException> { GameBuild.build(project, oldRuntime, target) {} }
        assertFailsWith<IllegalArgumentException> { ProjectDistribution.export(project, oldRuntime, source) {} }
        val supportedRuntime = bundle("0.29.0")
        GameBuild.build(project, supportedRuntime, target) {}
        val exported = ProjectDistribution.export(project, supportedRuntime, source) {}
        val copied = ProjectFiles.open(exported)
        assertContains(ProjectDocuments(copied).open(copied.assets.resolve("Scenes/start.scene.json")).source, "unresolved")
    }

    @Test fun failureConcurrentEditOrOccupiedDestinationNeverPublishes() {
        val p=project();val runtime=bundle();val target=p.root.resolveSibling("Build")
        assertFails { GameBuild.build(p,runtime,target) { error("native validation rejected") } }
        assertFalse(Files.exists(target))
        assertFails { GameBuild.build(p,runtime,target) { Files.writeString(p.assets.resolve("Scripts/modules/value.lua"),"return 99") } }
        assertFalse(Files.exists(target))
        assertFails { GameBuild.build(p,runtime,target) { staged -> Files.writeString(staged.resolveSibling("Game.exe"),"tampered") } }
        assertFalse(Files.exists(target))
        Files.createDirectory(target);Files.writeString(target.resolve("mine.txt"),"keep")
        assertFails { GameBuild.build(p,runtime,target) {} }
        assertEquals("keep",Files.readString(target.resolve("mine.txt")))
    }

    @Test fun requiresStartupSceneMetadataAndPortableNames() {
        val p=project();val runtime=bundle();val target=p.root.resolveSibling("Build")
        val noStartup=ProjectFiles.save(p,p.document.copy(startupScene=null))
        assertFails { GameBuild.build(noStartup,runtime,target) {} }
        val restored=ProjectFiles.save(noStartup,p.document)
        Files.writeString(restored.assets.resolve("loose.txt"),"no metadata")
        assertFails { GameBuild.build(restored,runtime,target) {} }
        assertFalse(Files.exists(target))
        for(bad in listOf("a/../b","con.txt","x/aux","name.","trailing ","a:b","")) assertFails { GamePackageWriter.requirePortable(bad) }
        GamePackageWriter.requirePortable("Scenes/Level 1/start.scene.json")
    }

    @Test fun executableIsNamedAfterTheProject() {
        assertEquals("My Game.exe",GameBuild.executableName("My Game"))
        assertEquals("Space_ Odyssey_.exe",GameBuild.executableName("Space: Odyssey?"))
        assertEquals("Trailing.exe",GameBuild.executableName("  Trailing.. "))
        assertEquals("CON_.exe",GameBuild.executableName("CON"))
        assertEquals("COM¹_.exe", GameBuild.executableName("COM¹"))
        assertFailsWith<IllegalArgumentException> { GamePackageWriter.requirePortable("path/LPT².txt") }
        assertEquals("Game.exe",GameBuild.executableName("..."))
        assertEquals("engine_app.exe",GameBuild.executableName("engine_app"))
        // A name that would replace a runtime file is refused rather than overwriting it.
        val p=project();val runtime=bundle();Files.writeString(runtime.resolve("Clash.exe"),"tool")
        Files.writeString(runtime.resolve("sle-runtime.json"),Files.readString(runtime.resolve("sle-runtime.json")).replace("\"files\":{",
            "\"files\":{\"Clash.exe\":\"${ProjectDistribution.hash(runtime.resolve("Clash.exe"))}\","))
        val clash=ProjectFiles.save(p,p.document.copy(name="clash"))
        assertFails { GameBuild.build(clash,runtime,p.root.resolveSibling("Build")) {} }
        assertFalse(Files.exists(p.root.resolveSibling("Build")))
        // A project named like the engine keeps the executable as is.
        val kept=ProjectFiles.save(clash,clash.document.copy(name="engine_app"))
        assertEquals("engine_app.exe",GameBuild.build(kept,bundle(),p.root.resolveSibling("Kept")) {}.executable.fileName.toString())
    }
}
