package sle.editor.project.assets

import java.nio.file.*
import kotlin.test.*

class AssetIdentityTest {
    @Test fun paddedGridSkipsPartialAndTransparentCellsAndPersistsEditedIds() {
        val project = Files.createTempDirectory("sle-padded-sheet-")
        val assets = Files.createDirectory(project.resolve("Assets"))
        val texture = assets.resolve("sheet.png")
        val image = java.awt.image.BufferedImage(17, 20, java.awt.image.BufferedImage.TYPE_INT_ARGB)
        image.setRGB(2, 3, 0x01000000)
        image.setRGB(7, 10, -1)
        image.setRGB(12, 3, -1) // The next column falls inside the right margin.
        javax.imageio.ImageIO.write(image, "png", texture.toFile())

        val grid = SpriteGrid(4, 5, 2, 3, 1, 2)
        val cells = grid.rectangles(image)
        assertEquals(listOf(2 to 3, 7 to 10), cells.map { it.x to it.y })
        assertEquals(listOf(2 to 3, 7 to 3, 2 to 10, 7 to 10),
            grid.copy(skipTransparent = false).rectangles(image).map { it.x to it.y })

        val textureId = AssetIdentity.ensure(texture)
        AssetIdentity.editRegions(project, assets, "sheet.png") { appendGridRegions(it, cells) }
        val meta = AssetIdentity.sidecar(texture)
        val assigned = AssetIdentity.readMetadata(meta).regions.first().id
        AssetIdentity.editRegions(project, assets, "sheet.png") { regions ->
            regions.map { if (it.id == assigned) it.copy(name = "Idle", width = 3) else it }
        }
        AssetIdentity.editRegions(project, assets, "sheet.png") { appendGridRegions(it, cells) }
        assertFailsWith<IllegalArgumentException> {
            AssetIdentity.editRegions(project, assets, "sheet.png") { regions ->
                regions.map { if (it.id == assigned) it.copy(width = 17) else it }
            }
        }
        val reopened = AssetIdentity.readMetadata(meta)
        assertEquals(textureId, reopened.id)
        assertEquals("Idle", reopened.regions.single { it.id == assigned }.name)
        assertEquals(3, reopened.regions.single { it.id == assigned }.width)
        assertEquals(3, reopened.regions.size)
    }

    @Test fun regionEditsPreserveIdentityAndFilter() {
        val project = Files.createTempDirectory("sle-regions-")
        val assets = Files.createDirectory(project.resolve("Assets"))
        val texture = assets.resolve("sprite.png")
        javax.imageio.ImageIO.write(java.awt.image.BufferedImage(17, 16, 2), "png", texture.toFile())
        val id = AssetIdentity.ensure(texture)
        val meta = AssetIdentity.sidecar(texture)
        AssetIdentity.writeFilter(project, assets, "sprite.png", AssetTextureFilter.Nearest)
        val regions = appendGridRegions(emptyList(), SpriteGrid(8, 8, skipTransparent = false)
            .rectangles(javax.imageio.ImageIO.read(texture.toFile())))
        assertEquals(listOf(0 to 0, 8 to 0, 0 to 8, 8 to 8), regions.map { it.x to it.y })
        AssetIdentity.editRegions(project, assets, "sprite.png") { regions }
        val renamed = regions.first().copy(name = "Idle", width = 7)
        AssetIdentity.editRegions(project, assets, "sprite.png") { listOf(renamed) + it.drop(1) }
        AssetIdentity.writeFilter(project, assets, "sprite.png", null)
        assertEquals(AssetMetadata(id, null, listOf(renamed) + regions.drop(1)), AssetIdentity.readMetadata(meta))
        assertFailsWith<IllegalArgumentException> {
            AssetIdentity.editRegions(project, assets, "sprite.png") { it + renamed }
        }
        assertEquals(renamed, AssetIdentity.readMetadata(meta).regions.first())
    }

    @Test fun textureFilterPreservesIdentityAndRejectsInvalidMetadata() {
        val project = Files.createTempDirectory("sle-filter-")
        val assets = Files.createDirectory(project.resolve("Assets"))
        val texture = Files.createFile(assets.resolve("sprite.png"))
        val id = AssetIdentity.ensure(texture)
        val meta = AssetIdentity.sidecar(texture)
        assertNull(AssetIdentity.readMetadata(meta).filter)
        for (filter in AssetTextureFilter.entries) {
            AssetIdentity.writeFilter(project, assets, "sprite.png", filter)
            assertEquals(AssetMetadata(id, filter), AssetIdentity.readMetadata(meta))
        }
        AssetIdentity.writeFilter(project, assets, "sprite.png", null)
        assertEquals(AssetIdentity.encode(id), Files.readString(meta))
        for (invalid in listOf("null", "{}", """{"filter":"other"}""", """{"filter":null}""",
            """{"filter":"nearest","extra":0}""")) {
            val source = AssetIdentity.encode(id).trim().dropLast(1) + ""","texture":$invalid}"""
            Files.writeString(meta, source)
            val error = assertFailsWith<IllegalArgumentException> {
                AssetIdentity.writeFilter(project, assets, "sprite.png", null)
            }
            assertContains(error.message.orEmpty(), meta.toRealPath().toString())
            assertEquals(source, Files.readString(meta))
        }
    }

    @Test fun ordinaryDotFilesRemainVisible() {
        val project=Files.createTempDirectory("sle-id-");val assets=Files.createDirectory(project.resolve("Assets"))
        Files.writeString(assets.resolve(".sle-write-settings.lua"),"return {}")
        val snapshot=AssetScanner.scan(project,assets)
        assertEquals(listOf(".sle-write-settings.lua"),snapshot.entries.map { it.path })
        assertEquals(1,snapshot.pathsById.size)
    }
    @Test fun identitySurvivesEditsMovesAndProjectRelocation() {
        val project=Files.createTempDirectory("sle-id-");val assets=Files.createDirectories(project.resolve("Assets"))
        val file=Files.writeString(assets.resolve("main.lua"),"return 1")
        val first=AssetScanner.scan(project,assets)
        val id=first.idsByPath.getValue("main.lua")
        Files.writeString(file,"return 2")
        assertEquals(id,AssetScanner.scan(project,assets).idsByPath["main.lua"])
        Files.move(file,assets.resolve("renamed.lua"))
        Files.move(AssetIdentity.sidecar(file),AssetIdentity.sidecar(assets.resolve("renamed.lua")))
        val moved=project.resolveSibling(project.fileName.toString()+"-moved")
        Files.move(project,moved)
        assertEquals("renamed.lua",AssetScanner.scan(moved,moved.resolve("Assets")).pathsById[id])
    }
    @Test fun ambiguousAndMalformedMetadataNeverResolvesAndRepairIsExplicit() {
        val project=Files.createTempDirectory("sle-id-");val assets=Files.createDirectory(project.resolve("Assets"))
        val a=Files.writeString(assets.resolve("a.lua"),"a")
        AssetScanner.scan(project,assets)
        val meta=Files.readString(AssetIdentity.sidecar(a))
        val b=Files.writeString(assets.resolve("b.lua"),"b")
        Files.writeString(AssetIdentity.sidecar(b),meta)
        val bad=Files.writeString(assets.resolve("bad.lua"),"bad")
        Files.writeString(AssetIdentity.sidecar(bad),"broken")
        val snapshot=AssetScanner.scan(project,assets)
        assertTrue(snapshot.pathsById.isEmpty());assertEquals(3,snapshot.entries.size)
        assertTrue(snapshot.errors.any { it.contains("Duplicate") });assertTrue(snapshot.errors.any { it.contains("bad.lua") })
        assertEquals("broken",Files.readString(AssetIdentity.sidecar(bad)))
        AssetIdentity.repair(project,assets,"b.lua")
        AssetIdentity.repair(project,assets,"bad.lua")
        assertEquals(3,AssetScanner.scan(project,assets).pathsById.size)
    }
}
