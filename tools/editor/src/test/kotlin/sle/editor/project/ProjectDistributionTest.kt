package sle.editor.project

import kotlinx.serialization.json.*
import sle.editor.project.assets.AssetIdentity
import sle.editor.project.assets.AssetThumbnailCache
import sle.editor.project.assets.AssetTextureFilter
import sle.editor.project.assets.SpriteGrid
import sle.editor.project.assets.appendGridRegions
import java.util.zip.ZipFile
import java.nio.file.*
import kotlin.test.*

class ProjectDistributionTest {
    @Test fun builtGameKeepsTextureFilterRegionsAndIdentity() {
        val project = project()
        val image = Files.createFile(project.assets.resolve("Textures/sprite.png"))
        val pixels = java.awt.image.BufferedImage(12, 15, java.awt.image.BufferedImage.TYPE_INT_ARGB)
        pixels.setRGB(2, 3, -1)
        javax.imageio.ImageIO.write(pixels, "png", image.toFile())
        val id = AssetIdentity.ensure(image)
        AssetIdentity.editRegions(project.root, project.assets, "Textures/sprite.png") {
            appendGridRegions(it, SpriteGrid(8, 8, 2, 3).rectangles(pixels))
        }
        val region = AssetIdentity.readMetadata(AssetIdentity.sidecar(image)).regions.single()
        AssetIdentity.writeFilter(project.root, project.assets, "Textures/sprite.png", AssetTextureFilter.Nearest)
        val documents = ProjectDocuments(project)
        val path = project.assets.resolve("Scenes/region.scene.json")
        val source = buildJsonObject {
            put("key", "region-scene")
            putJsonArray("entities") {
                addJsonObject {
                    put("key", "sprite")
                    putJsonObject("components") {
                        putJsonObject("SpriteRenderer") {
                            put("texture", image.toString())
                            put("regionId", region.id)
                        }
                    }
                }
            }
        }
        documents.save(path, ProjectDocumentKind.Scene, source.toString(), emptyMap(), null) {}
        val renamed = region.copy(name = "Idle", width = 7)
        AssetIdentity.editRegions(project.root, project.assets, "Textures/sprite.png") { listOf(renamed) }
        val reopened = Json.parseToJsonElement(ProjectDocuments(project).open(path).source).jsonObject
        val sprite = reopened.getValue("entities").jsonArray.single().jsonObject.getValue("components")
            .jsonObject.getValue("SpriteRenderer").jsonObject
        assertEquals(JsonPrimitive(region.id), sprite["regionId"])
        assertNotNull(AssetThumbnailCache(project.root, project.assets).load("Textures/sprite.png", AssetTextureFilter.Nearest))
        val thumbnailName = Files.list(project.root.resolve(".sle/cache/thumbnails")).use {
            it.toList().single().fileName.toString()
        }
        val game = GameBuild.build(project, bundle(), project.root.resolveSibling("Built")) {}
        ZipFile(game.folder.resolve(GameBuild.PACKAGE).toFile()).use { archive ->
            assertTrue(archive.entries().asSequence().none { ".sle" in it.name.split('/') || it.name.substringAfterLast('/') == thumbnailName })
            val entry = archive.getEntry("assets/Textures/sprite.png.slemeta")
            val metadata = Json.parseToJsonElement(archive.getInputStream(entry).bufferedReader().use { it.readText() }).jsonObject
            assertEquals(JsonPrimitive(id), metadata["id"])
            val texture = metadata.getValue("texture").jsonObject
            assertEquals(JsonPrimitive("nearest"), texture["filter"])
            assertEquals(renamed.encode(), texture.getValue("regions").jsonArray.single())
        }
    }

    @Test fun automaticExportsStayInsideProjectAndPreservePreviousGames() {
        val p=project();val runtime=bundle()
        val first=ProjectDistribution.exportAutomatically(p,runtime) {}
        val second=ProjectDistribution.exportAutomatically(p,runtime) {}
        val exports=p.root.resolve("Exports")
        assertEquals(exports.resolve("Game/game/game.sleproject"),first)
        assertEquals(exports.resolve("Game-2/game/game.sleproject"),second)
        assertTrue(Files.exists(first))
        assertTrue(Files.exists(second))
        assertEquals("return 42",Files.readString(p.assets.resolve("Scripts/modules/value.lua")))
    }

    @Test fun nestedOutputExcludesPreviousExportsAndProjectCaches() {
        val p=project();val runtime=bundle()
        Files.createDirectories(p.root.resolve("Exports/Old/game/Assets"))
        Files.writeString(p.root.resolve("Exports/Old/game/Assets/old.txt"),"old export")
        Files.createDirectories(p.root.resolve(".sle/cache"))
        Files.writeString(p.root.resolve(".sle/cache/derived"),"cache")
        val result=ProjectDistribution.exportAutomatically(p,runtime) {}
        assertEquals(p.root.resolve("Exports/Game/game/game.sleproject"),result)
        val game=result.parent
        assertFalse(Files.exists(game.resolve("Exports")))
        assertFalse(Files.exists(game.resolve(".sle")))
        assertEquals("old export",Files.readString(p.root.resolve("Exports/Old/game/Assets/old.txt")))
        assertEquals("return 42",Files.readString(game.resolve("Assets/Scripts/modules/value.lua")))
    }

    @Test fun failedLocalExportRemovesStageAndPreservesPreviousExport() {
        val p=project();val runtime=bundle()
        val previous=ProjectDistribution.exportAutomatically(p,runtime) {}
        val before=Files.readAllBytes(previous)
        assertFails { ProjectDistribution.exportAutomatically(p,runtime) { error("validation rejected") } }
        assertContentEquals(before,Files.readAllBytes(previous))
        Files.list(p.root.resolve("Exports")).use { paths ->
            assertEquals(listOf("Game"),paths.map { it.fileName.toString() }.sorted().toList())
        }
    }

    @Test fun automaticExportRejectsAssetRootOverlappingExports() {
        val p=project();val runtime=bundle()
        Files.move(p.assets,p.root.resolve("Exports"))
        Files.createDirectory(p.assets)
        val configured=ProjectFiles.save(p,p.document.copy(assetRoot="Exports"))
        assertFails { ProjectDistribution.exportAutomatically(configured,runtime) {} }
        assertFalse(Files.exists(p.root.resolve("Exports/Game")))
    }

    private fun project(): OpenedProject {
        val p=ProjectFiles.create(Files.createTempDirectory("sle-distribution-").resolve("Game"),"Game")
        Files.createDirectories(p.assets.resolve("Scripts/modules"))
        Files.writeString(p.assets.resolve("Scripts/modules/value.lua"),"return 42")
        val saved=ProjectDocuments(p).save(p.assets.resolve("Scenes/start.scene.json"),ProjectDocumentKind.Scene,"{\"key\":\"start\",\"entities\":[]}",emptyMap(),null) {}
        AssetIdentity.ensure(p.assets.resolve("Scripts/modules/value.lua"))
        return ProjectFiles.save(p,p.document.copy(startupScene=AssetIdentity.read(AssetIdentity.sidecar(saved.path))))
    }
    private fun bundle(): Path {
        val root=Files.createTempDirectory("sle-runtime-")
        Files.writeString(root.resolve("engine_app.exe"),"executable")
        Files.createDirectories(root.resolve("assets/shaders"));Files.writeString(root.resolve("assets/shaders/sprite.vert"),"shader")
        Files.writeString(root.resolve("msvcp140.dll"),"crt")
        val manifest=buildJsonObject {
            put("format","sle-runtime");put("version",1);put("platform","windows-x64");put("configuration","Release");put("executable","engine_app.exe")
            put("files",buildJsonObject {
                Files.walk(root).use { paths -> paths.filter { Files.isRegularFile(it) }.forEach { file ->
                    put(root.relativize(file).toString().replace('\\','/'),java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file)).joinToString("") { "%02x".format(it) })
                } }
            })
        }
        Files.writeString(root.resolve("sle-runtime.json"),manifest.toString())
        return root
    }
    @Test fun copiesSavedSourcesModulesAndSidecarsButNoLocalStateOrTrash() {
        val p=project();val runtime=bundle()
        Files.createDirectories(p.root.resolve(".sle/trash/test"));Files.writeString(p.root.resolve(".sle/trash/test/payload"),"deleted")
        val before=Files.readString(p.file);val scene=Files.readString(p.assets.resolve("Scenes/start.scene.json"))
        val target=p.root.resolveSibling("Delivery")
        var checked: Path?=null
        val result=ProjectDistribution.export(p,runtime,target) { descriptor ->
            checked=descriptor
            assertTrue(Files.exists(descriptor.parent.resolve("Assets/Scripts/modules/value.lua")))
        }
        assertNotEquals(result,checked)
        assertEquals(before,Files.readString(p.file));assertEquals(before,Files.readString(result))
        assertEquals(scene,Files.readString(result.parent.resolve("Assets/Scenes/start.scene.json")))
        assertEquals(AssetIdentity.read(p.assets.resolve("Scripts/modules/value.lua.slemeta")),AssetIdentity.read(result.parent.resolve("Assets/Scripts/modules/value.lua.slemeta")))
        assertFalse(Files.exists(result.parent.resolve(".sle")))
        assertTrue(Files.exists(target.resolve("msvcp140.dll")))
        assertTrue(Files.exists(target.resolve("sle-distribution.json")))
        val relocated=target.resolveSibling("Relocated");Files.move(target,relocated)
        assertEquals(p.document,ProjectFiles.open(relocated.resolve("game/game.sleproject")).document)
    }
    @Test fun failureOrConcurrentSourceEditNeverPublishesOrOverwritesDestination() {
        val p=project();val runtime=bundle();val target=p.root.resolveSibling("Delivery")
        assertFails { ProjectDistribution.export(p,runtime,target) { error("native validation rejected") } }
        assertFalse(Files.exists(target))
        assertFails { ProjectDistribution.export(p,runtime,target) { Files.writeString(p.assets.resolve("Scripts/modules/value.lua"),"return 99") } }
        assertFalse(Files.exists(target));assertEquals("return 99",Files.readString(p.assets.resolve("Scripts/modules/value.lua")))
        Files.createDirectory(target);Files.writeString(target.resolve("mine.txt"),"keep")
        assertFails { ProjectDistribution.export(p,runtime,target) {} }
        assertEquals("keep",Files.readString(target.resolve("mine.txt")))
    }
    @Test fun refusesContainmentDebugRuntimeMissingMetadataAndStaleSettings() {
        val p=project();val runtime=bundle();val target=p.root.resolveSibling("Delivery")
        assertFails { ProjectDistribution.export(p,runtime,p.assets.resolve("Delivery")) {} }
        assertFails { ProjectDistribution.export(p,runtime,runtime.resolve("Delivery")) {} }
        Files.writeString(runtime.resolve("sle-runtime.json"),Files.readString(runtime.resolve("sle-runtime.json")).replace("Release","Debug"))
        assertFails { ProjectDistribution.export(p,runtime,target) {} }
        Files.writeString(runtime.resolve("sle-runtime.json"),Files.readString(runtime.resolve("sle-runtime.json")).replace("Debug","Release"))
        Files.delete(p.assets.resolve("Scripts/modules/value.lua.slemeta"))
        assertFails { ProjectDistribution.export(p,runtime,target) {} }
        assertFalse(Files.exists(p.assets.resolve("Scripts/modules/value.lua.slemeta")))
        AssetIdentity.ensure(p.assets.resolve("Scripts/modules/value.lua"))
        ProjectFiles.save(p,p.document.copy(name="Changed"))
        assertFails { ProjectDistribution.export(p,runtime,target) {} }
    }
    @Test fun destinationArrivingDuringValidationIsPreserved() {
        val p=project();val runtime=bundle();val target=p.root.resolveSibling("Delivery")
        assertFails { ProjectDistribution.export(p,runtime,target) { Files.createDirectory(target);Files.writeString(target.resolve("mine.txt"),"arrival") } }
        assertEquals("arrival",Files.readString(target.resolve("mine.txt")))
    }
    @Test fun stagedRuntimeTamperingIsRejectedBeforePublication() {
        val p=project();val runtime=bundle();val target=p.root.resolveSibling("Delivery")
        assertFails { ProjectDistribution.export(p,runtime,target) { descriptor ->
            Files.writeString(descriptor.parent.parent.resolve("engine_app.exe"),"changed executable")
        } }
        assertFalse(Files.exists(target));assertEquals("executable",Files.readString(runtime.resolve("engine_app.exe")))
    }
    @Test fun preservesCustomAssetRootUnknownFilesAndRuntimeChangeFailure() {
        val p=project();val custom=p.root.resolve("Content/Shared")
        Files.createDirectories(custom.parent);Files.move(p.assets,custom);Files.createDirectory(p.assets)
        val configured=ProjectFiles.save(p,p.document.copy(assetRoot="Content/Shared"))
        Files.writeString(custom.resolve("unknown.xyz"),"data");AssetIdentity.ensure(custom.resolve("unknown.xyz"))
        val runtime=bundle();val target=p.root.resolveSibling("Delivery")
        val result=ProjectDistribution.export(configured,runtime,target) {}
        assertEquals("data",Files.readString(result.parent.resolve("Content/Shared/unknown.xyz")))
        assertFails { ProjectDistribution.export(configured,runtime,target.resolveSibling("Changed")) {
            Files.writeString(runtime.resolve("engine_app.exe"),"new engine")
        } }
        assertFalse(Files.exists(target.resolveSibling("Changed")))
    }

    @Test fun missingRuntimeLibraryCannotPublishAnIncompleteGame() {
        val p=project();val runtime=bundle();val target=p.root.resolveSibling("Delivery")
        Files.delete(runtime.resolve("msvcp140.dll"))
        assertFails { ProjectDistribution.export(p,runtime,target) {} }
        assertFalse(Files.exists(target))
    }

}
