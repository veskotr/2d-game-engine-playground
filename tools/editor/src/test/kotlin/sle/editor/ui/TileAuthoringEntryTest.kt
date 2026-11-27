package sle.editor.ui

import sle.editor.command.CommandContext
import sle.editor.command.EditorActions
import sle.editor.command.ToolCommands
import sle.editor.model.SceneWorkspaceState
import sle.editor.model.ScenePlaybackState
import sle.editor.model.TilePalette
import sle.editor.project.assets.SpriteRectangle
import sle.editor.project.assets.SpriteRegion
import sle.editor.project.assets.defaultTilesetPath
import sle.editor.project.assets.tilesetRegions
import sle.editor.viewport.tools.ViewportToolId
import sle.editor.viewport.tools.ViewportToolState
import kotlin.test.*

/** Phase 0 entry points: Create → Tile Layer, the Tiles command and the New Tileset helpers. */
class TileAuthoringEntryTest {
    private val grass = "project-asset://00000009-1111-4111-8111-111111111111"
    private fun scene() = SceneWorkspaceState(emptyMap()) { it }
    private fun tileLayerEntry(menu: List<EditorMenuEntry>) =
        menu.single { it.label == "Create" }.children.single { it.label in listOf("Tile Layer", "Tilemap") }

    @Test fun mapMenuOffersCreationPlacementAndExplicitConversion() {
        val actions = mutableListOf<String>()
        val creation = TileLayerCreation(emptyList(), {}, {}, newMap = { actions += "new" },
            existingMap = { actions += "place" }, convertLegacy = { actions += "convert" })
        val entry = sceneCreationMenu(scene(), true, tileLayers = creation).single { it.label == "Create" }.children.single { it.label == "Tilemap" }
        assertEquals(listOf("New Tilemap…", "Place Existing Tilemap…", "Convert Legacy Tile Layers…"), entry.children.map { it.label })
        entry.children.forEach { it.action() }
        assertEquals(listOf("new", "place", "convert"), actions)
    }

    @Test fun createTileLayerListsTilesetsThenNewTileset() {
        var created: String? = null
        var newTilesets = 0
        val creation = TileLayerCreation(listOf("Grass" to grass), { created = it }, { newTilesets++ })
        val entry = tileLayerEntry(sceneCreationMenu(scene(), true, tileLayers = creation))
        assertTrue(entry.enabled)
        assertEquals(listOf("Grass", "", "New Tileset…"), entry.children.map { it.label })
        assertTrue(entry.children[1].separator)
        entry.children.first().action()
        assertEquals(grass, created)
        entry.children.last().action()
        assertEquals(1, newTilesets)
    }

    @Test fun createTileLayerWithoutTilesetsOffersOnlyNewTileset() {
        val creation = TileLayerCreation(emptyList(), {}, {})
        assertEquals(listOf("New Tileset…"), tileLayerEntry(sceneCreationMenu(scene(), true, tileLayers = creation)).children.map { it.label })
    }

    @Test fun createTileLayerRechecksLiveEditability() {
        var created: String? = null
        val creation = TileLayerCreation(listOf("Grass" to grass), { created = it }, {})
        tileLayerEntry(sceneCreationMenu(scene(), true, editableNow = { false }, tileLayers = creation)).children.first().action()
        assertNull(created)
    }

    @Test fun createTileLayerIsDisabledWithAReasonWithoutAProjectScene() {
        val entry = tileLayerEntry(sceneCreationMenu(scene(), true))
        assertFalse(entry.enabled)
        assertEquals(TILE_LAYER_UNAVAILABLE, entry.help)
    }

    @Test fun menuBarCreateMenuCarriesTheTileLayerEntry() {
        val creation = TileLayerCreation(listOf("Grass" to grass), {}, {})
        val menus = editorApplicationMenus(CommandContext(scene = scene(), editable = true), {}, {}, tileLayers = creation).toMap()
        assertEquals(listOf("Grass", "", "New Tileset…"), menus.getValue("Create").single { it.label == "Tile Layer" }.children.map { it.label })
    }

    @Test fun tilesCommandActivatesTheToolAndOpensThePalette() {
        var opened = 0
        val tools = ViewportToolState()
        val actions = object : EditorActions { override fun openTilePalette() { opened++ } }
        val ctx = CommandContext(scene = scene(), playback = ScenePlaybackState(), tools = tools,
            projectOpen = true, editable = true, actions = actions)
        assertTrue(ToolCommands.tiles.enabled(ctx))
        ToolCommands.tiles.run(ctx)
        assertEquals(ViewportToolId.Tiles, tools.active)
        assertEquals(1, opened)
        assertEquals("tilemap", ToolCommands.tiles.icon)
    }

    @Test fun tilesetChoicesUseFileNamesAndSkipUnreadableTilesets() {
        fun option(path: String, id: Int) =
            TilePaletteOption(path, TilePalette("project-asset://%08d-1111-4111-8111-111111111111".format(id), 16, 16, emptyList()))
        val options = listOf(option("Textures/Grass.tileset.json", 1), option("A/Wall.tileset.json", 2),
            option("B/Wall.tileset.json", 3), TilePaletteOption("Broken.tileset.json", null, "Unsupported tileset fields"))
        assertEquals(listOf("Grass", "A/Wall.tileset.json", "B/Wall.tileset.json"), tilesetMenuChoices(options).map { it.first })
        assertEquals(options[0].palette!!.reference, tilesetMenuChoices(options).first().second)
    }

    @Test fun newTilesetDefaultsNextToTheTexture() {
        assertEquals("Textures/Terrain.tileset.json", defaultTilesetPath("Textures/Terrain.png", null))
        assertEquals("Sheet.tileset.json", defaultTilesetPath("Sheet.png", null))
    }

    @Test fun tilesetRegionsTakeOneRegionPerGridCell() {
        fun region(index: Int, x: Int, y: Int, size: Int = 16) =
            SpriteRegion("%08d-2222-4222-8222-222222222222".format(index), "Slice $index", x, y, size, size)
        val regions = listOf(region(1, 0, 0), region(2, 0, 0), region(3, 16, 0), region(4, 0, 0, 32))
        val cells = listOf(SpriteRectangle(0, 0, 16, 16), SpriteRectangle(16, 0, 16, 16))
        assertEquals(listOf(regions[0], regions[2]), tilesetRegions(regions, cells))
    }
}
