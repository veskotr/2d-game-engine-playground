package sle.editor.project.assets

import androidx.compose.ui.text.input.TextFieldValue
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import sle.editor.model.TileCollisionPoint
import sle.editor.model.TilesetGrid
import sle.editor.model.writeTileset
import java.awt.image.BufferedImage
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID
import javax.imageio.ImageIO
import kotlin.test.*

class TilesetEditorWorkspaceTest {
    @get:Rule val temp = TemporaryFolder()

    @Test fun switchingProjectsRetainsIndependentDraftsForTheSameRelativePath() {
        val first = project("first")
        val second = project("second")
        val workspace = TilesetEditorWorkspace()
        val a = first.open(workspace)
        val diskA = Files.readString(first.tileset)
        a.session.updateTiles(setOf(a.tileId), name = "Moss")
        val b = second.open(workspace)
        b.session.updateTiles(setOf(b.tileId), name = "Water")

        assertSame(a, workspace.current(first.root))
        assertSame(b, workspace.current(second.root))
        assertSame(a, first.open(workspace))
        assertEquals(setOf(a, b), workspace.dirtyAssets.toSet())

        workspace.save(b)

        assertEquals("Water", second.open(TilesetEditorWorkspace()).session.parsed.palette.tiles.single().name)
        assertEquals(diskA, Files.readString(first.tileset))
        assertEquals("Moss", a.session.parsed.palette.tiles.single().name)
        assertEquals(listOf(a), workspace.dirtyAssets)
    }

    @Test fun saveAppendsReslicedRegionsWithoutReplacingExistingSpriteRegions() {
        val project = project("reslice")
        val workspace = TilesetEditorWorkspace()
        val asset = project.open(workspace)
        val original = asset.liveRegions()
        val firstTile = asset.tileId
        val (regions, grid) = asset.slice(8, 16, listOf(0, 0), listOf(0, 0))

        asset.session.reslice(regions, 8, 16, grid)

        assertEquals(original, asset.liveRegions())
        assertEquals(4, asset.session.pendingRegions.size)
        workspace.save(asset)

        assertEquals(original + regions, asset.liveRegions())
        val reopened = project.open(TilesetEditorWorkspace())
        assertEquals(firstTile, reopened.session.parsed.palette.tiles.first().id)
        assertEquals(regions.toSet(), reopened.session.parsed.regions.values.toSet())
        assertEquals(TilesetGrid(0, 0, 0, 0, 4), reopened.session.parsed.grid)
        assertFalse(reopened.session.dirty)
    }

    @Test fun reloadUsesTheNewSpritesheetFileAndImage() {
        val project = project("reload")
        val workspace = TilesetEditorWorkspace()
        val original = project.open(workspace)
        val replacement = atlas(project.root, project.assets, "replacement.png", width = 16, color = 0xff123456.toInt())
        Files.write(project.tileset, writeTileset(replacement.id, 16, 16, listOf(replacement.firstRegion),
            TilesetGrid(0, 0, 0, 0, 1)))

        val reloaded = project.open(workspace, reload = true)

        assertNotSame(original, reloaded)
        assertSame(reloaded, workspace.current(project.root))
        assertEquals("replacement.png", reloaded.atlas)
        assertEquals("replacement.png", reloaded.session.parsed.atlas)
        assertEquals(16, reloaded.image.width)
        assertEquals(0xff123456.toInt(), reloaded.image.getRGB(0, 0))
        assertEquals(replacement.firstRegion, reloaded.session.parsed.regions.values.single())
    }

    @Test fun reloadPreventsAnOldSaveCallbackFromPublishingTheDiscardedDraft() {
        val project = project("stale")
        val workspace = TilesetEditorWorkspace()
        val discarded = project.open(workspace)
        val disk = Files.readString(project.tileset)
        discarded.session.updateTiles(setOf(discarded.tileId), name = "Discarded")

        val reloaded = project.open(workspace, reload = true)
        assertFailsWith<IllegalStateException> { workspace.save(discarded) }

        assertEquals(disk, Files.readString(project.tileset))
        assertEquals("Grass", reloaded.session.parsed.palette.tiles.single().name)
        assertTrue(workspace.dirtyAssets.isEmpty())
    }

    @Test fun replacingAByteIdenticalTilesetDoesNotGiveItsNewIdentityTheOldDraft() {
        val project = project("replaced-tileset")
        val workspace = TilesetEditorWorkspace()
        val asset = project.open(workspace)
        val disk = Files.readString(project.tileset)
        asset.session.updateTiles(setOf(asset.tileId), name = "Draft")
        replaceAssetId(project.tileset)

        assertFailsWith<IllegalStateException> { project.open(workspace) }
        assertFailsWith<IllegalStateException> { workspace.save(asset) }

        assertEquals(disk, Files.readString(project.tileset))
        assertEquals(listOf(asset), workspace.dirtyAssets)
        assertEquals("Draft", asset.session.parsed.palette.tiles.single().name)
    }

    @Test fun aStaleSnapshotCannotBindTheOldSpritesheetUuidToAReplacement() {
        val project = project("replaced-atlas")
        val workspace = TilesetEditorWorkspace()
        val snapshot = AssetScanner.scan(project.root, project.assets)
        val asset = project.open(workspace, snapshot = snapshot)
        val disk = Files.readString(project.tileset)
        asset.session.updateTiles(setOf(asset.tileId), name = "Draft")
        replaceAssetId(project.assets.resolve(asset.atlas))

        assertFailsWith<IllegalStateException> { project.open(TilesetEditorWorkspace(), snapshot = snapshot) }
        assertFailsWith<IllegalStateException> { project.open(workspace, snapshot = snapshot) }
        assertFailsWith<IllegalStateException> { workspace.save(asset) }

        assertEquals(disk, Files.readString(project.tileset))
        assertEquals(listOf(asset), workspace.dirtyAssets)
    }

    @Test fun metadataSaveRejectsAnAtlasThatShrankOnDisk() {
        val project = project("shrunk-atlas")
        val workspace = TilesetEditorWorkspace()
        val asset = project.open(workspace)
        val disk = Files.readString(project.tileset)
        asset.session.updateTiles(setOf(asset.tileId), name = "Draft")
        val atlas = project.assets.resolve(asset.atlas)
        val shrunken = BufferedImage(8, 16, BufferedImage.TYPE_INT_ARGB)
        check(ImageIO.write(shrunken, "png", atlas.toFile()))

        assertFailsWith<IllegalStateException> { workspace.save(asset) }
        assertEquals(disk, Files.readString(project.tileset))
        assertTrue(asset.session.dirty)
    }

    @Test fun saveRejectsCollisionTooSmallForARegisteredSceneThatUsesTheTileset() {
        val project = project("scene-scale")
        val workspace = TilesetEditorWorkspace()
        val asset = project.open(workspace)
        val disk = Files.readString(project.tileset)
        val tilesetId = AssetIdentity.read(AssetIdentity.sidecar(project.tileset))
        val scene = Files.writeString(project.assets.resolve("Room.scene.json"), """
            {"format":"sle-project-scene","version":1,"assets":[],"document":{
                "key":"room","name":"Room","entities":[],"tileLayers":[{
                    "id":"00000001-1111-4111-8111-111111111111","name":"Floor","drawOrder":0,
                    "tileset":"project-asset://$tilesetId","pixelsPerUnit":100,"cells":[]
                }]
            }}
        """.trimIndent())
        AssetIdentity.ensure(scene)
        val updates = mapOf(asset.tileId to tinyShape)
        assertFailsWith<IllegalArgumentException> { workspace.setCollisions(asset, updates, scales = listOf(1.0)) }
        assertFalse(asset.session.dirty || asset.session.canUndo)
        asset.session.setCollision(setOf(asset.tileId), tinyShape)

        assertFailsWith<IllegalArgumentException> { workspace.save(asset) }
        assertEquals(disk, Files.readString(project.tileset))
        assertTrue(asset.session.dirty)
    }

    @Test fun stagedCollisionIsValidatedAgainstTheInMemorySceneScale() {
        val project = project("open-scene-scale")
        val workspace = TilesetEditorWorkspace()
        val asset = project.open(workspace)
        val updates = mapOf(asset.tileId to tinyShape)

        assertFailsWith<IllegalArgumentException> { workspace.setCollisions(asset, updates, scales = listOf(100.0)) }
        assertFalse(asset.session.dirty || asset.session.canUndo)
        workspace.setCollisions(asset, updates, scales = listOf(1.0))
        assertTrue(asset.session.dirty && asset.session.canUndo)
    }

    @Test fun undoRefreshesGridFieldsAndUnrelatedEditsKeepUnappliedInput() {
        val project = project("grid-fields")
        val asset = project.open(TilesetEditorWorkspace())
        asset.syncMetadata()
        asset.inputs["Tile size"] = TextFieldValue("8, 16")
        asset.session.updateTiles(setOf(asset.tileId), name = "Draft")
        asset.syncMetadata()
        assertEquals("8, 16", asset.inputs["Tile size"]?.text)

        val (regions, grid) = asset.slice(8, 16, listOf(0, 0), listOf(0, 0))
        asset.session.reslice(regions, 8, 16, grid)
        asset.syncMetadata()
        asset.inputs["Tile size"] = TextFieldValue("8, 16")
        asset.session.undo()
        asset.syncMetadata()

        assertEquals(16, asset.session.parsed.palette.width)
        assertNull(asset.inputs["Tile size"])
    }

    private data class Project(val root: Path, val assets: Path, val tileset: Path) {
        fun open(workspace: TilesetEditorWorkspace, reload: Boolean = false, snapshot: AssetSnapshot = AssetScanner.scan(root, assets)) = workspace.open(
            root, assets, tileset.fileName.toString(), snapshot, reload = reload,
        )
    }

    private fun replaceAssetId(file: Path) {
        val sidecar = AssetIdentity.sidecar(file)
        val metadata = AssetIdentity.readMetadata(sidecar)
        Files.writeString(sidecar, AssetIdentity.encode(UUID.randomUUID().toString(), metadata.filter, metadata.regions))
    }

    private data class Atlas(val id: String, val firstRegion: SpriteRegion)

    private fun project(name: String): Project {
        val root = temp.newFolder(name).toPath()
        val assets = Files.createDirectory(root.resolve("Assets"))
        val atlas = atlas(root, assets, "terrain.png", width = 32, color = 0xffc8d080.toInt())
        val tileset = Files.write(assets.resolve("Terrain.tileset.json"), writeTileset(atlas.id, 16, 16,
            listOf(atlas.firstRegion), TilesetGrid(0, 0, 0, 0, 2)))
        return Project(root, assets, tileset)
    }

    private fun atlas(root: Path, assets: Path, filename: String, width: Int, color: Int): Atlas {
        val path = assets.resolve(filename)
        val image = BufferedImage(width, 16, BufferedImage.TYPE_INT_ARGB)
        image.setRGB(0, 0, color)
        check(ImageIO.write(image, "png", path.toFile()))
        val id = AssetIdentity.ensure(path)
        val firstRegion = SpriteRectangle(0, 0, 16, 16).region("Grass")
        AssetIdentity.editRegions(root, assets, filename) {
            listOf(firstRegion) + (if (width == 32) listOf(SpriteRectangle(16, 0, 16, 16).region("Sprite")) else emptyList())
        }
        return Atlas(id, firstRegion)
    }

    private val TilesetEditorAsset.tileId get() = session.parsed.palette.tiles.single().id
    private val tinyShape = TileCollisionShape(TileCollisionMode.Custom, listOf(listOf(
        TileCollisionPoint(0.0, 0.0), TileCollisionPoint(0.5, 0.0), TileCollisionPoint(0.0, 0.5),
    )))
}
