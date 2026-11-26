package sle.editor.project

import kotlinx.serialization.json.*
import sle.editor.project.assets.AssetIdentity
import java.nio.file.*
import kotlin.test.*

class GameContentUpdateTest {
    private fun project(): OpenedProject {
        val p=ProjectFiles.create(Files.createTempDirectory("sle-build-").resolve("Game"),"Game")
        Files.createDirectories(p.assets.resolve("Scripts/modules"))
        Files.writeString(p.assets.resolve("Scripts/modules/value.lua"),"return 42")
        val saved=ProjectDocuments(p).save(p.assets.resolve("Scenes/start.scene.json"),ProjectDocumentKind.Scene,"{\"key\":\"start\",\"entities\":[]}",emptyMap(),null) {}
        AssetIdentity.ensure(p.assets.resolve("Scripts/modules/value.lua"))
        return ProjectFiles.save(p,p.document.copy(startupScene=AssetIdentity.read(AssetIdentity.sidecar(saved.path)),
            game=ProjectGameSettings(title="Packaged",width=640,height=360,vsync=false,mode="Windowed",targetFPS=30)))
    }
    private fun bundle(engineVersion: String = "0.26.0"): Path {
        val root=Files.createTempDirectory("sle-runtime-")
        Files.write(root.resolve("engine_app.exe"), runtimeFixtureLauncher())
        Files.createDirectories(root.resolve("assets/shaders"));Files.writeString(root.resolve("assets/shaders/sprite.vert"),"shader")
        Files.writeString(root.resolve("msvcp140.dll"),"crt")
        Files.writeString(root.resolve("sle-runtime.json"),buildJsonObject {
            put("format","sle-runtime");put("version",1);put("engineVersion",engineVersion);put("platform","windows-x64");put("configuration","Release");put("executable","engine_app.exe")
            put("files",buildJsonObject {
                Files.walk(root).use { paths -> paths.filter { Files.isRegularFile(it) }.forEach { file ->
                    put(root.relativize(file).toString().replace('\\','/'),ProjectDistribution.hash(file))
                } }
            })
        }.toString())
        return root
    }

    @Test fun exportsOnlyChangedPackagesAndAppliesWithoutChangingBase() {
        val p = project()
        val runtime = bundle()
        val base = GameBuild.exportAutomatically(p, runtime) {}
        val before = ProjectDistribution.inventory(base.folder)
        val update = base.folder.resolveSibling("Update")
        assertNull(GameContentUpdate.export(p, runtime, base.folder, update) {})
        assertFalse(Files.exists(update))

        Files.writeString(p.assets.resolve("Scripts/modules/value.lua"), "return 99")
        val exported = assertNotNull(GameContentUpdate.export(p, runtime, base.folder, update) {})
        assertEquals(listOf("shared.slepkg"), exported.delta.replaced)
        assertEquals(2, exported.delta.unchanged.size)
        assertTrue(exported.delta.added.isEmpty())
        assertTrue(exported.delta.removed.isEmpty())
        val target = base.folder.resolveSibling("Updated")
        assertFailsWith<IllegalStateException> {
            GameContentUpdate.apply(base.folder, update, target) { error("Native validation failed") }
        }
        assertFalse(Files.exists(target))
        GameContentUpdate.apply(base.folder, update, target) {}
        assertEquals(before, ProjectDistribution.inventory(base.folder))
        for (path in exported.delta.unchanged) {
            assertEquals(before.files[path], ProjectDistribution.hash(target.resolve(path)))
        }
        assertNotEquals(before.files["shared.slepkg"], ProjectDistribution.hash(target.resolve("shared.slepkg")))
        assertFails { GameContentUpdate.apply(target, update, target.resolveSibling("Wrong Base")) {} }
        val occupied = target.resolveSibling("Occupied")
        assertFailsWith<FileAlreadyExistsException> {
            GameContentUpdate.apply(base.folder, update, occupied) {
                Files.createDirectory(occupied)
                Files.writeString(occupied.resolve("sentinel"), "keep")
            }
        }
        assertEquals("keep", Files.readString(occupied.resolve("sentinel")))

        val payload = update.resolve("shared.slepkg")
        val rejected = target.resolveSibling("Rejected")
        Files.writeString(payload, "tampered")
        assertFails { GameContentUpdate.apply(base.folder, update, rejected) {} }
        Files.delete(payload)
        assertFails { GameContentUpdate.apply(base.folder, update, rejected) {} }
        assertFalse(Files.exists(rejected))
        val invalid = update.resolveSibling("Invalid")
        Files.writeString(base.config, "{}")
        assertFails { GameContentUpdate.export(p, runtime, base.folder, invalid) {} }
        Files.delete(base.config)
        assertFails { GameContentUpdate.export(p, runtime, base.folder, invalid) {} }
        assertFalse(Files.exists(invalid))
    }

    @Test fun supportsConfigOnlyChangesAndRejectsDifferentRuntimeOrProject() {
        val p = project()
        val runtime = bundle()
        val base = GameBuild.exportAutomatically(p, runtime) {}
        val update = base.folder.resolveSibling("Update")
        assertFails { GameContentUpdate.export(p, bundle("0.27.0"), base.folder, update) {} }
        assertFails { GameContentUpdate.export(project(), runtime, base.folder, update) {} }
        val changed = ProjectFiles.save(p, p.document.copy(game = p.document.game.copy(width = 800)))
        val result = assertNotNull(GameContentUpdate.export(changed, runtime, base.folder, update) {})
        assertTrue(result.delta.replaced.isEmpty())
        assertEquals(base.packages.size, result.delta.unchanged.size)
        val target = base.folder.resolveSibling("Updated")
        GameContentUpdate.apply(base.folder, update, target) {}
        val config = Json.parseToJsonElement(Files.readString(target.resolve(GameBuild.CONFIG))).jsonObject
        assertEquals(800, config.getValue("window").jsonObject.getValue("width").jsonPrimitive.int)
    }
    @Test fun updatesSceneAndSharedAssetsAndPromotesOwnershipTogether() {
        val p = project()
        val runtime = bundle()
        val forest = p.assets.resolve("Scenes/start.scene.json")
        val castle = p.assets.resolve("Scenes/Main.scene.json")
        val forestId = AssetIdentity.read(AssetIdentity.sidecar(forest))
        val castleId = AssetIdentity.read(AssetIdentity.sidecar(castle))
        val forestPackage = "scene-$forestId.slepkg"
        val castlePackage = "scene-$castleId.slepkg"
        val enemy = p.assets.resolve("Enemy.txt")
        val prop = p.assets.resolve("Forest prop.txt")
        Files.writeString(enemy, "shared")
        Files.writeString(prop, "private")
        AssetIdentity.ensure(enemy)
        AssetIdentity.ensure(prop)
        val enemyId = AssetIdentity.read(AssetIdentity.sidecar(enemy))
        val propId = AssetIdentity.read(AssetIdentity.sidecar(prop))
        fun edit(scene: Path, key: String, vararg assets: String) {
            Files.writeString(scene, buildJsonObject {
                put("format", "sle-project-scene")
                put("version", 1)
                put("document", buildJsonObject { put("key", key); put("entities", JsonArray(emptyList())) })
                put("assets", JsonArray(assets.map(::JsonPrimitive)))
            }.toString())
        }
        edit(forest, "forest", enemyId, propId)
        edit(castle, "castle", enemyId)
        val base = GameBuild.exportAutomatically(p, runtime) {}
        var number = 0
        fun export() = assertNotNull(GameContentUpdate.export(p, runtime, base.folder,
            base.folder.resolveSibling("Update-${++number}")) {})

        Files.writeString(prop, "edited forest prop")
        assertEquals(listOf(forestPackage), export().delta.replaced)
        Files.writeString(prop, "private")
        Files.writeString(enemy, "edited shared enemy")
        assertEquals(listOf("shared.slepkg"), export().delta.replaced)
        Files.writeString(enemy, "shared")
        edit(castle, "castle", enemyId, propId)
        val promotion = export()
        assertEquals(setOf("shared.slepkg", forestPackage, castlePackage), promotion.delta.replaced.toSet())
        val promoted = base.folder.resolveSibling("Promoted")
        GameContentUpdate.apply(base.folder, promotion.folder, promoted) {}
        fun containsProp(path: Path) = java.util.zip.ZipFile(path.toFile()).use {
            it.getEntry("assets/Forest prop.txt") != null
        }
        assertTrue(containsProp(promoted.resolve("shared.slepkg")))
        assertFalse(containsProp(promoted.resolve(forestPackage)))
        assertFalse(containsProp(promoted.resolve(castlePackage)))

        val castleMetadata = AssetIdentity.sidecar(castle)
        val castleIdentity = Files.readString(castleMetadata)
        Files.delete(castle)
        Files.delete(castleMetadata)
        val removal = export()
        assertEquals(listOf(castlePackage), removal.delta.removed)
        val removed = base.folder.resolveSibling("Removed")
        GameContentUpdate.apply(base.folder, removal.folder, removed) {}
        assertFalse(Files.exists(removed.resolve(castlePackage)))
        assertTrue(Files.exists(base.folder.resolve(castlePackage)))
        val config = Json.parseToJsonElement(Files.readString(removed.resolve(GameBuild.CONFIG))).jsonObject
        assertEquals(JsonArray(listOf("shared.slepkg", forestPackage).map(::JsonPrimitive)), config["packages"])

        edit(castle, "castle", enemyId)
        Files.writeString(castleMetadata, castleIdentity)
        val addition = assertNotNull(GameContentUpdate.export(p, runtime, removed, removed.resolveSibling("Addition")) {})
        assertEquals(listOf(castlePackage), addition.delta.added)
        assertTrue(addition.delta.replaced.isEmpty())
        val restored = removed.resolveSibling("Restored")
        GameContentUpdate.apply(removed, addition.folder, restored) {}
        assertEquals(ProjectDistribution.inventory(base.folder), ProjectDistribution.inventory(restored))
    }
}
