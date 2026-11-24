package sle.editor.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.graphics.toComposeImageBitmap
import java.awt.image.BufferedImage
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import sle.editor.model.*
import sle.editor.project.OpenedProject
import sle.editor.project.assets.AssetKind
import sle.editor.project.assets.AssetSnapshot
import sle.editor.project.assets.SpriteRegion
import sle.editor.project.assets.assetPath
import sle.editor.project.assets.readTileCollision
import sle.editor.ui.controls.*
import sle.editor.ui.theme.*
import sle.editor.viewport.tools.ViewportToolState
import java.awt.Rectangle
import java.nio.file.Files
import javax.imageio.ImageIO

internal data class TilePaletteOption(
    val path: String, val palette: TilePalette?, val error: String? = null,
    val atlas: String? = null, val regions: Map<String, SpriteRegion> = emptyMap(),
)

private fun readStamp(project: OpenedProject, path: String, region: SpriteRegion): TileStampImage? = runCatching {
    val file = assetPath(project.root, project.assets, path)
    requireNotNull(ImageIO.createImageInputStream(file.toFile())).use { stream ->
        val reader = ImageIO.getImageReaders(stream).next()
        try {
            reader.input = stream
            require(region.fits(reader.getWidth(0), reader.getHeight(0)))
            val parameter = reader.defaultReadParam
            parameter.sourceRegion = Rectangle(region.x, region.y, region.width, region.height)
            val sampling = maxOf(1, (maxOf(region.width, region.height) - 1) / 256 + 1)
            parameter.setSourceSubsampling(sampling, sampling, 0, 0)
            val image = reader.read(0, parameter)
            TileStampImage(region.width, region.height,
                image.getRGB(0, 0, image.width, image.height, null, 0, image.width),
                image.width, image.height)
        } finally {
            reader.dispose()
        }
    }
}.getOrNull()

private fun readTilePalettes(project: OpenedProject, snapshot: AssetSnapshot): List<TilePaletteOption> =
    snapshot.entries.filter { it.kind == AssetKind.File && it.path.endsWith(".tileset.json", ignoreCase = true) }
        .mapNotNull { entry ->
            val id = snapshot.idsByPath[entry.path] ?: return@mapNotNull null
            runCatching {
                val file = assetPath(project.root, project.assets, entry.path)
                require(Files.size(file) <= 4 * 1024 * 1024) { "Tileset exceeds 4 MiB" }
                val source = Json.parseToJsonElement(Files.readString(file)).jsonObject
                require(source.keys == setOf("format", "version", "spritesheet", "tileWidth", "tileHeight", "tiles")) {
                    "Unsupported tileset fields"
                }
                require(source["format"]?.jsonPrimitive?.content == "sle-tileset" && source["version"]?.jsonPrimitive?.int == 1) {
                    "Expected a version 1 SLE tileset"
                }
                val width = source.getValue("tileWidth").jsonPrimitive.int
                val height = source.getValue("tileHeight").jsonPrimitive.int
                require(width > 0 && height > 0) { "Tile dimensions must be positive" }
                val reference = source.getValue("spritesheet").jsonPrimitive.content
                require(reference.startsWith("project-asset://")) { "Tileset spritesheet must use a project asset UUID" }
                val atlasId = reference.removePrefix("project-asset://")
                val atlas = requireNotNull(snapshot.pathsById[atlasId]) { "Missing spritesheet $atlasId" }
                val regions = snapshot.spriteRegions[atlas].orEmpty().associateBy { it.id }
                val swatchRegions = mutableMapOf<String, SpriteRegion>()
                val rawTiles = source.getValue("tiles").jsonArray
                require(rawTiles.size <= 4096) { "Tileset exceeds 4096 tiles" }
                val tiles = rawTiles.map {
                    val tile = it.jsonObject
                    require(tile.keys - "collision" == setOf("id", "name", "region")) { "Unsupported tile fields" }
                    val tileId = tile.getValue("id").jsonPrimitive.content
                    require(java.util.UUID.fromString(tileId).toString() == tileId) { "Invalid tile UUID $tileId" }
                    val regionId = tile.getValue("region").jsonPrimitive.content
                    val region = requireNotNull(regions[regionId]) { "Tile $tileId has missing region $regionId" }
                    readTileCollision(tile, region.width, region.height)
                    swatchRegions[tileId] = region
                    val name = tile.getValue("name").jsonPrimitive
                    require(name.isString && name.content.isNotBlank() && name.content.encodeToByteArray().size <= 256) {
                        "Invalid name for tile $tileId"
                    }
                    TileSwatch(tileId, name.content)
                }
                require(tiles.map { it.id }.distinct().size == tiles.size) { "Duplicate tile UUIDs" }
                TilePaletteOption(entry.path, TilePalette("project-asset://$id", width, height, tiles),
                    atlas = atlas, regions = swatchRegions)
            }.getOrElse { TilePaletteOption(entry.path, null, it.message ?: "Could not read tileset") }
        }

@Composable
internal fun rememberTilePaletteOptions(project: OpenedProject, snapshot: AssetSnapshot?): List<TilePaletteOption>? {
    val options by key(project.file, project.document.assetRoot) {
        produceState<List<TilePaletteOption>?>(null, snapshot) {
            value = if (snapshot == null) null else withContext(Dispatchers.IO) { readTilePalettes(project, snapshot) }
        }
    }
    return options
}

private fun activeLayer(scene: SceneWorkspaceState): TileLayerDocument? =
    scene.activeTileLayer?.let(scene::tileLayer) ?: scene.tileLayerDocuments().maxByOrNull { it.drawOrder }

@Composable
internal fun SyncTilePainting(scene: SceneWorkspaceState, project: OpenedProject, snapshot: AssetSnapshot?,
    options: List<TilePaletteOption>?, tools: ViewportToolState) {
    val painting = tools.tilePainting
    val layer = activeLayer(scene)
    val option = options?.firstOrNull { it.palette?.reference == layer?.tileset }
    val atlas = option?.atlas
    val imageIds = painting.brushTileIds().toSet()
    val regions = option?.regions.orEmpty().filterKeys { it in imageIds }
    val images by key(project.file, atlas, regions, snapshot?.thumbnailRevision) {
        produceState<Map<String, TileStampImage>>(emptyMap()) {
            if (atlas != null) value = withContext(Dispatchers.IO) {
                regions.mapNotNull { (id, region) -> readStamp(project, atlas, region)?.let { id to it } }.toMap()
            }
        }
    }
    SideEffect {
        painting.configure(layer?.id, option?.palette, scene)
        painting.images = images
    }
}

@Composable
internal fun TilePaintingControls(scene: SceneWorkspaceState, project: OpenedProject, snapshot: AssetSnapshot?,
    options: List<TilePaletteOption>?, tools: ViewportToolState, activate: () -> Unit) {
    val painting = tools.tilePainting
    val palettes = options.orEmpty().mapNotNull { option -> option.palette?.let { option.path to it } }.toMap()
    val layers = scene.tileLayerDocuments().sortedByDescending { it.drawOrder }
    val layer = activeLayer(scene)
    val palette = palettes.values.firstOrNull { it.reference == layer?.tileset }
    val metrics = LocalEditorMetrics.current
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(metrics.panelPadding),
        verticalArrangement = Arrangement.spacedBy(metrics.gap)) {
        var newPalette by remember { mutableStateOf<String?>(null) }
        val selected = newPalette?.takeIf(palettes::containsKey) ?: palettes.keys.firstOrNull()
        EditorSelect("Tileset for new layer", selected ?: "No tilesets", palettes.keys.toList(),
            { newPalette = it }, enabled = palettes.isNotEmpty())
        EditorButton("Add Tile Layer", {
            runCatching { scene.addTileLayer(palettes.getValue(checkNotNull(selected)).reference) }
                .onSuccess { activate(); scene.activeTileLayer = it; painting.selectMode(TilePaintMode.Brush) }
                .onFailure { scene.error = it.message }
        }, enabled = selected != null)
        val layerChoices = layers.associateBy { "${it.name} (${it.drawOrder})" }
        EditorSelect("Layer", layerChoices.entries.firstOrNull { it.value.id == layer?.id }?.key ?: "No layers",
            layerChoices.keys.toList(), { scene.activeTileLayer = layerChoices[it]?.id }, enabled = layers.isNotEmpty())
        if (layer != null) {
            EditorCheckBox("Lock layer", layer.locked, { locked ->
                runCatching { scene.setTileLayerLocked(layer.id, locked) }
                    .onFailure { scene.error = it.message }
            })
            var name by remember(layer.id, layer.name) { mutableStateOf(TextFieldValue(layer.name)) }
            EditorTextField(name, { name = it }, "Layer name", onCommit = {
                runCatching { scene.renameTileLayer(layer.id, name.text) }.onFailure { scene.error = it.message }
            })
            Row(horizontalArrangement = Arrangement.spacedBy(metrics.gap)) {
                EditorButton("Move Up", { scene.reorderTileLayer(layer.id, 1) },
                    enabled = layer != layers.first(), help = "Draw this layer in front of the next layer")
                EditorButton("Move Down", { scene.reorderTileLayer(layer.id, -1) },
                    enabled = layer != layers.last(), help = "Draw this layer behind the previous layer")
            }
            EditorCheckBox("Visible", layer.visible, { scene.setTileLayerVisible(layer.id, it) })
            EditorCheckBox("Collision", layer.collisionEnabled, { scene.setTileLayerCollision(layer.id, it) })
            if (palette == null && options != null) EditorLabel("Tileset unavailable · ${layer.tileset}")
        }
        for ((index, modes) in TilePaintMode.entries.chunked(3).withIndex()) {
            Row(horizontalArrangement = Arrangement.spacedBy(metrics.gap)) {
                modes.forEachIndexed { column, mode ->
                    EditorButton(mode.label, { activate(); painting.selectMode(mode) }, enabled = palette != null,
                        primary = mode == painting.mode, help = "${index * 3 + column + 1} · ${mode.help}")
                }
            }
        }
        var query by remember(scene, palette?.reference) { mutableStateOf(TextFieldValue()) }
        EditorTextField(query, { query = it }, "Search tiles", enabled = palette != null)
        val allTiles = palette?.tiles.orEmpty()
        val repeatedNames = allTiles.groupingBy { it.name }.eachCount()
        val tiles = allTiles.filter { it.name.contains(query.text.trim(), ignoreCase = true) }
            .associateBy { if (repeatedNames[it.name] == 1) it.name else "${it.name} · ${it.id}" }
        if (layer != null && palette != null) {
            var buildingStamp by remember(palette.reference) { mutableStateOf(false) }
            EditorCheckBox("Build Stamp", buildingStamp, { buildingStamp = it },
                help = "Click tiles in left-to-right stamp order")
            val atlasOption = options?.firstOrNull { it.palette?.reference == palette.reference }
            if (tiles.isEmpty()) EditorLabel("No matching tiles")
            LazyRow(horizontalArrangement = Arrangement.spacedBy(metrics.gap)) {
                items(tiles.entries.toList(), key = { it.value.id }) { (label, tile) ->
                    val region = atlasOption?.regions?.get(tile.id)
                    val bitmap by produceState<androidx.compose.ui.graphics.ImageBitmap?>(
                        null, project.file, atlasOption?.atlas, region, snapshot?.thumbnailRevision) {
                        val atlas = atlasOption?.atlas
                        value = if (atlas == null || region == null) null else withContext(Dispatchers.IO) {
                            readStamp(project, atlas, region)?.let { image ->
                                BufferedImage(image.imageWidth, image.imageHeight, BufferedImage.TYPE_INT_ARGB).apply {
                                    setRGB(0, 0, image.imageWidth, image.imageHeight, image.argb, 0, image.imageWidth)
                                }.toComposeImageBitmap()
                            }
                        }
                    }
                    Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                        bitmap?.let { Image(it, null, Modifier.size(36.dp)) }
                        EditorButton(label, {
                            activate()
                            if (buildingStamp) {
                                painting.toggleStampTile(tile.id)
                                painting.selectMode(TilePaintMode.Brush)
                            } else painting.selectTile(tile.id)
                        }, primary = if (buildingStamp) tile.id in painting.stampTiles else tile.id == painting.tile,
                            enabled = !buildingStamp || tile.id in painting.stampTiles || painting.stampTiles.size < 8)
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(metrics.gap)) {
                EditorButton("Use Copied Stamp", {
                    activate()
                    runCatching { painting.useCopiedStamp() }.onFailure { scene.error = it.message }
                }, enabled = painting.clipboard?.tileset == palette.reference)
                val stamp = painting.brushPattern()
                if (stamp != null && (stamp.width != 1L || stamp.height != 1L)) {
                    EditorLabel("Stamp ${stamp.width} × ${stamp.height} · ${stamp.tiles.size} tiles")
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(metrics.gap)) {
                EditorButton("Copy", { runCatching { painting.copy(scene) }.onFailure { scene.error = it.message } },
                    enabled = scene.tileSelection(layer.id) != null, help = "Ctrl+C in the viewport")
                EditorButton("Paste", { runCatching { painting.paste(scene) }.onFailure { scene.error = it.message } },
                    enabled = painting.clipboard != null && !layer.locked && layer.visible,
                    help = "Ctrl+V at the last pointed cell or selection")
                EditorButton("Clear Selection", { scene.selectTiles(layer.id, null) },
                    enabled = scene.tileSelection(layer.id) != null)
            }
        }
        EditorSeparator()
        when {
            options == null -> EditorLabel("Scanning project tilesets…")
            options.isEmpty() -> EditorLabel("Add a registered .tileset.json asset to the project first")
            else -> options.orEmpty().firstOrNull { it.error != null }?.let {
                EditorLabel("${it.path} · ${it.error}", color = LocalEditorColors.current.error)
            }
        }
    }
}
