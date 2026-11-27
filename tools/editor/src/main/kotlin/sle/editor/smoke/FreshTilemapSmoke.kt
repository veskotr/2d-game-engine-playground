package sle.editor.smoke

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import sle.editor.model.*
import sle.editor.project.*
import sle.editor.project.assets.AssetBrowserState
import sle.editor.project.assets.AssetIdentity
import sle.editor.project.assets.ProjectAssetCommands
import sle.editor.project.assets.SpriteGrid
import sle.editor.ui.TileLayerCreation
import sle.editor.ui.sceneCreationMenu
import sle.editor.viewport.EngineCanvas
import sle.editor.viewport.Vec2
import sle.editor.viewport.tools.ViewportToolId
import sle.editor.viewport.tools.ViewportToolState
import java.awt.Rectangle
import java.awt.Robot
import java.awt.event.InputEvent.BUTTON1_DOWN_MASK
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO
import kotlin.math.roundToInt

/**
 * Phase 0 acceptance: a project holding only a PNG reaches painted, saved tiles without hand-edited
 * JSON. It creates the tileset through the slicer's Create Tileset command, adds the layer through
 * Create → Tile Layer, paints with the Robot, saves, reopens and lets the native viewport load it.
 */
internal suspend fun runFreshTilemapSmoke(
    window: java.awt.Window,
    openProject: (Path) -> Unit,
    project: ProjectWorkspaceState,
    scene: SceneWorkspaceState,
    canvas: () -> EngineCanvas?,
    tools: ViewportToolState,
    browser: () -> AssetBrowserState?,
    assets: () -> ProjectAssetCommands?,
    tileLayers: () -> TileLayerCreation?,
    paletteBesideDetails: () -> Boolean,
    bounds: Map<String, androidx.compose.ui.geometry.Rect>,
) {
    val fixture = Path.of("tests/data/tilemap-fresh")
    val reports = Path.of(System.getProperty("sle.smokeReport")).parent
    val root = withContext(Dispatchers.IO) {
        val copy = Files.createTempDirectory(Files.createDirectories(reports), "fresh-tilemap-")
        Files.walk(fixture).use { paths ->
            paths.filter { it != fixture }.forEach { Files.copy(it, copy.resolve(fixture.relativize(it))) }
        }
        copy
    }
    suspend fun await(message: String, condition: () -> Boolean) {
        repeat(300) {
            if (condition()) return
            delay(30)
        }
        error(message)
    }
    fun capture(name: String) {
        val origin = window.locationOnScreen
        ImageIO.write(Robot().createScreenCapture(Rectangle(origin.x, origin.y, window.width, window.height)),
            "png", reports.resolve(name).toFile())
    }

    openProject(root.resolve("Fresh.sleproject"))
    await("Fresh project did not open") { project.opened?.root == root && !project.busy }
    val opened = checkNotNull(project.opened)
    val documents = ProjectDocuments(opened)
    val map = root.resolve("Assets/Room.scene.json")
    scene.openProject(withContext(Dispatchers.IO) {
        documents.save(map, ProjectDocumentKind.Scene, SceneDocument(name = "Room").serialize(), emptyMap(), null, ::canonicalSceneDocument)
    })
    await("Viewport did not load the empty scene") { canvas()?.authoringReady() == true }

    // 0.2/0.3: New Tileset… on the selected texture opens the slicer; Create Tileset writes the file.
    val texture = "Textures/Colors.png"
    await("Texture was not scanned") { browser()?.snapshot?.idsByPath?.containsKey(texture) == true }
    browser()?.select(texture)
    await("Texture was not selected") { browser()?.selected == texture }
    checkNotNull(assets()).newTileset()
    await("New Tileset… did not open the slicer") { assets()?.tilesetTexture == texture && "SpritesheetCreateTileset" in bounds }
    val cells = withContext(Dispatchers.IO) {
        SpriteGrid(8, 8).rectangles(ImageIO.read(opened.assets.resolve(texture).toFile()))
    }
    check(cells.size == 2) { "Expected two 8×8 cells, found $cells" }
    val tilesetPath = "Textures/Colors.tileset.json"
    checkNotNull(checkNotNull(assets()).createTileset)(texture, cells, 8, 8, tilesetPath, sle.editor.model.TilesetGrid(0,0,0,0,1))
    await("Create Tileset did not finish: ${assets()?.message}") {
        assets()?.busy == false && browser()?.snapshot?.idsByPath?.containsKey(tilesetPath) == true
    }
    check(tools.tilePainting.newLayerTileset == tilesetPath) { "The palette did not select the new tileset" }
    checkNotNull(assets()).closeTileset()
    val sheetRegions = withContext(Dispatchers.IO) {
        AssetIdentity.readMetadata(AssetIdentity.sidecar(opened.assets.resolve(texture))).regions
    }
    check(sheetRegions.size == 2) { "Create Tileset did not add the grid regions: $sheetRegions" }

    // Phase 3: create a reusable map through the same command the menus show.
    await("Create → Tilemap did not become available") { tileLayers()?.newMap != null && tileLayers()?.tilesets?.any { it.first == "Colors" } == true }
    val mapAsset = opened.assets.resolve("Maps/World.tilemap.json")
    sle.editor.ui.EditorFilePicker.answerNext { listOf(mapAsset.toFile()) }
    val entry = sceneCreationMenu(scene, true, tileLayers = tileLayers()).single { it.label == "Create" }
        .children.single { it.label == "Tilemap" }.children.single { it.label == "New Tilemap…" }
    entry.action()
    await("Create → Tile Layer did not add an active layer") {
        scene.tileLayerDocuments().size == 1 && scene.activeTileLayer == scene.tileLayerDocuments().single().id
    }
    val layer = scene.tileLayerDocuments().single().id
    await("Tiles tool did not activate with a palette") {
        tools.active == ViewportToolId.Tiles && tools.tilePainting.layerId == layer && tools.tilePainting.palette?.tiles?.size == 2
    }
    // 0.6: the palette opens beside Details.
    await("Tile Palette did not open beside Details") { paletteBesideDetails() }
    await("Scene viewport did not reload") { canvas()?.authoringReady() == true }
    delay(300)
    capture("fresh-tilemap-palette.png")

    // Paint three cells in one row with the Robot.
    val view = checkNotNull(canvas())
    val robot = Robot()
    // A new scene frames its tiny content closely; zoom out until a three-cell stroke fits.
    repeat(20) {
        val camera = checkNotNull(view.previewCamera())
        val center = camera.world(Vec2(view.width / 2.0, view.height / 2.0))
        if (camera.screen(center + Vec2(24.0, 0.0)).x < view.width - 1.0) return@repeat
        val before = camera.size
        view.zoomEntity(1.5f)
        await("Viewport did not zoom out") { view.previewCamera()?.size?.let { it != before } == true }
    }
    val camera = checkNotNull(view.previewCamera())
    // Aim at cell centers so no point sits on a grid line.
    val center = camera.world(Vec2(view.width / 2.0, view.height / 2.0))
    val start = Vec2(Math.floor(center.x / 8.0) * 8.0 + 4.0, Math.floor(center.y / 8.0) * 8.0 + 4.0)
    fun cursor(world: Vec2) {
        val pixel = checkNotNull(view.previewCamera()).screen(world)
        check(pixel.x in 1.0..<view.width.toDouble() && pixel.y in 1.0..<view.height.toDouble()) { "Tile outside viewport: $world → $pixel" }
        val origin = view.locationOnScreen
        robot.mouseMove(origin.x + pixel.x.roundToInt(), origin.y + pixel.y.roundToInt())
    }
    // Focus the viewport first (as TilePaintingSmoke does); the click paints the stroke's first cell.
    cursor(start)
    robot.mousePress(BUTTON1_DOWN_MASK)
    robot.mouseRelease(BUTTON1_DOWN_MASK)
    await("Scene viewport did not get focus") { view.isFocusOwner && scene.tileLayer(layer)?.cells?.size == 1 }
    await("Click gesture did not finish") { !scene.gestureActive }
    delay(300)
    robot.mousePress(BUTTON1_DOWN_MASK)
    try {
        await("Brush press did not start a tile gesture") { scene.gestureActive }
        cursor(start + Vec2(8.0, 0.0))
        delay(150)
        cursor(start + Vec2(16.0, 0.0))
        delay(150)
        check(scene.gestureActive) { "Brush gesture canceled before release" }
    } finally {
        robot.mouseRelease(BUTTON1_DOWN_MASK)
    }
    await("Brush did not paint three cells: ${scene.tileLayer(layer)?.cells?.keys}") {
        !scene.gestureActive && scene.tileLayer(layer)?.cells?.size == 3
    }
    val painted = checkNotNull(scene.tileLayer(layer)).cells

    // Save, reopen, and let the strict native loader read the written tileset and scene.
    val edited = scene.serialize()
    val saved = withContext(Dispatchers.IO) {
        scene.saveTilemaps()
        documents.save(map, ProjectDocumentKind.Scene, edited, scene.allAssets(), scene.projectDocument, ::canonicalSceneDocument)
    }
    scene.savedProject(saved, edited)
    scene.openProject(documents.open(map))
    await("Reopened scene lost the painted cells") { scene.tileLayer(layer)?.cells == painted }
    await("Native viewport did not load the saved tilemap") { canvas()?.authoringReady() == true }
    check(scene.error == null) { "Scene reported an error: ${scene.error}" }
    delay(300)
    capture("fresh-tilemap-painted.png")
    val placing = checkNotNull(scene.selectedKey)
    val before = checkNotNull(scene.tileLayer(layer)).cells
    scene.entity(placing)!!.applyGizmo(scene.entity(placing)!!.document.withPose(ScenePose(position = Vec2(16.0, 24.0))))
    await("Moved map did not update its paint grid") {
        scene.tileLayer(layer)?.grid(checkNotNull(tools.tilePainting.palette))?.origin == Vec2(16.0, 24.0)
    }
    check(scene.tileLayer(layer)?.cells == before) { "Moving the map changed local cells" }
    scene.placeTilemap(mapAsset, Vec2(64.0, 24.0))
    await("Second map placement did not share painted cells") { scene.tileLayer(layer)?.cells == before && canvas()?.authoringReady() == true }
    check(scene.entities.count { "Tilemap" in it.document.components } == 2)
    check(scene.document().tileLayers.isNullOrEmpty())
    println("TILEMAP_PLACEMENT_SMOKE_PASS")
    println("FRESH_TILEMAP_SMOKE_PASS")
}
