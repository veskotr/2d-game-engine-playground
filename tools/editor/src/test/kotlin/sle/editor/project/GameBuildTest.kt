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
    private fun bundle(): Path {
        val root=Files.createTempDirectory("sle-runtime-")
        Files.writeString(root.resolve("engine_app.exe"),"executable")
        Files.createDirectories(root.resolve("assets/shaders"));Files.writeString(root.resolve("assets/shaders/sprite.vert"),"shader")
        Files.writeString(root.resolve("msvcp140.dll"),"crt")
        Files.writeString(root.resolve("sle-runtime.json"),buildJsonObject {
            put("format","sle-runtime");put("version",1);put("engineVersion","0.16.0");put("platform","windows-x64");put("configuration","Release");put("executable","engine_app.exe")
            put("files",buildJsonObject {
                Files.walk(root).use { paths -> paths.filter { Files.isRegularFile(it) }.forEach { file ->
                    put(root.relativize(file).toString().replace('\\','/'),ProjectDistribution.hash(file))
                } }
            })
        }.toString())
        return root
    }

    @Test fun buildsConfigAndStoredPackageWithMainSceneSidecarsAndNoLocalState() {
        val p=project();val runtime=bundle()
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
            val names=entries.map { it.name }.toSet()
            assertTrue("assets/Scripts/modules/value.lua" in names && "assets/Scripts/modules/value.lua.slemeta" in names)
            assertTrue("assets/Scenes/start.scene.json.slemeta" in names)
            assertTrue(names.none { it.contains(".sle/") || it.contains("trash") })
            assertEquals("return 42",zip.getInputStream(zip.getEntry("assets/Scripts/modules/value.lua")).readBytes().decodeToString())
        }
        // The engine executable carries the project name; runtime-bundle metadata is not delivered.
        assertEquals(target.resolve("Game.exe"),built.executable);assertEquals("executable",Files.readString(built.executable))
        assertFalse(Files.exists(target.resolve("engine_app.exe")) || Files.exists(target.resolve("sle-runtime.json")))
        assertTrue(Files.exists(target.resolve("msvcp140.dll")) && Files.exists(target.resolve("sle-distribution.json")))
        val distribution=Json.parseToJsonElement(Files.readString(target.resolve("sle-distribution.json"))).jsonObject
        assertEquals("Game.exe",distribution.getValue("executable").jsonPrimitive.content)
        // Versions of the engine runtime and the editor that produced the build.
        assertEquals("0.16.0",distribution.getValue("engineVersion").jsonPrimitive.content)
        assertTrue(Regex("0\\.\\d+\\.\\d+").matches(distribution.getValue("editorVersion").jsonPrimitive.content))
        assertFalse(Files.exists(target.resolve("game")))
        // Rebuilding identical sources yields an identical package.
        val again=GameBuild.build(p,runtime,target.resolveSibling("Build Again")) {}
        assertContentEquals(Files.readAllBytes(target.resolve("game.slepkg")),Files.readAllBytes(again.folder.resolve("game.slepkg")))
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
