package sle.editor.project.assets

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.rememberDialogState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import sle.editor.model.*
import sle.editor.ui.EditorTheme
import sle.editor.ui.controls.*
import sle.editor.ui.theme.*
import java.awt.image.BufferedImage
import java.nio.file.Files
import javax.imageio.ImageIO
import kotlin.math.min
import kotlin.math.roundToInt

private data class CollisionTile(val id: String, val name: String, val region: SpriteRegion)
private data class CollisionAsset(
    val session: TilesetCollisionSession,
    val image: BufferedImage,
    val tiles: List<CollisionTile>,
    val width: Int,
    val height: Int,
) {
    fun crop(tile: CollisionTile): BufferedImage = tile.region.run { image.getSubimage(x, y, width, height) }
}

@Composable
internal fun TilesetCollisionDialog(
    path: String, commands: ProjectAssetCommands, layerScales: List<Double>, onClose: () -> Unit,
) {
    val appearance = LocalEditorAppearance.current
    val loaded by produceState<Result<CollisionAsset>?>(null, path, commands.projectRoot) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val project = requireNotNull(commands.projectRoot)
                val assets = requireNotNull(commands.assetRoot)
                val snapshot = requireNotNull(commands.snapshot)
                val file = assetPath(project, assets, path)
                require(Files.size(file) <= 4 * 1024 * 1024) { "Tileset exceeds 4 MiB" }
                val session = TilesetCollisionSession(file, Files.readString(file))
                val source = session.document
                require(source["format"] == JsonPrimitive("sle-tileset") && source["version"] == JsonPrimitive(1)) {
                    "Expected a version 1 SLE tileset"
                }
                val width = source.getValue("tileWidth").jsonPrimitive.int
                val height = source.getValue("tileHeight").jsonPrimitive.int
                require(width > 0 && height > 0) { "Tile dimensions must be positive" }
                val sheet = source.getValue("spritesheet").jsonPrimitive.content
                require(sheet.startsWith("project-asset://")) { "Expected a spritesheet UUID" }
                val atlas = requireNotNull(snapshot.pathsById[sheet.removePrefix("project-asset://")]) { "Missing spritesheet" }
                val image = requireNotNull(ImageIO.read(assetPath(project, assets, atlas).toFile())) { "Unsupported spritesheet image" }
                val regions = snapshot.spriteRegions[atlas].orEmpty().associateBy { it.id }
                val tiles = source.getValue("tiles").jsonArray.map { element ->
                    val tile = element.jsonObject
                    val region = requireNotNull(regions[tile.getValue("region").jsonPrimitive.content]) { "Missing tile region" }
                    require(region.fits(image.width, image.height)) {
                        "Tile region exceeds spritesheet bounds"
                    }
                    readTileCollision(tile, region.width, region.height)
                    CollisionTile(tile.getValue("id").jsonPrimitive.content, tile.getValue("name").jsonPrimitive.content, region)
                }
                require(tiles.isNotEmpty() && tiles.map { it.id }.toSet().size == tiles.size) { "Tileset needs unique tiles" }
                CollisionAsset(session, image, tiles, width, height)
            }
        }
    }
    var busy by remember { mutableStateOf(false) }
    DialogWindow(onCloseRequest = { if (!busy) onClose() }, title = "Tile Collision — $path",
        state = rememberDialogState(width = 920.dp, height = 660.dp)) {
        EditorTheme(appearance) {
            Surface(Modifier.fillMaxSize()) {
                val asset = loaded?.getOrNull()
                if (asset == null) {
                    Box(Modifier.padding(LocalEditorMetrics.current.panelPadding)) {
                        EditorLabel(loaded?.exceptionOrNull()?.message ?: "Loading tileset…")
                    }
                } else CollisionEditor(asset, layerScales, busy, { busy = it })
            }
        }
    }
}

private fun List<List<TileCollisionPoint>>.vertexText(): String =
    joinToString("\n") { polygon -> polygon.joinToString(" ") { "${it.x},${it.y}" } }

private fun String.collisionVertices(): List<List<TileCollisionPoint>> = lineSequence().filter { it.isNotBlank() }.map { line ->
    line.trim().split(Regex("\\s+")).map { token ->
        val pair = token.split(',')
        require(pair.size == 2) { "Use x,y pairs separated by spaces" }
        TileCollisionPoint(pair[0].toDouble(), pair[1].toDouble())
    }
}.toList()

private fun rectangle(width: Int, height: Int) = listOf(listOf(
    TileCollisionPoint(0.0, 0.0), TileCollisionPoint(width.toDouble(), 0.0),
    TileCollisionPoint(width.toDouble(), height.toDouble()), TileCollisionPoint(0.0, height.toDouble())
))

@Composable
private fun CollisionEditor(asset: CollisionAsset, layerScales: List<Double>, busy: Boolean, setBusy: (Boolean) -> Unit) {
    val metrics = LocalEditorMetrics.current
    val scope = rememberCoroutineScope()
    val session = asset.session
    var active by remember(asset) { mutableStateOf(asset.tiles.first()) }
    var selected by remember(asset) { mutableStateOf(setOf(active.id)) }
    var drafts by remember(asset) { mutableStateOf(emptyMap<String, TileCollisionShape>()) }
    var revision by remember(asset) { mutableStateOf(0) }
    var message by remember(asset) { mutableStateOf<String?>(null) }
    var threshold by remember(asset) { mutableStateOf(TextFieldValue("128")) }
    var tolerance by remember(asset) { mutableStateOf(TextFieldValue("0")) }
    var scale by remember(asset) { mutableStateOf(TextFieldValue((layerScales.maxOrNull() ?: 1.0).toString())) }
    var vertexEdits by remember(asset) { mutableStateOf(emptyMap<String, TextFieldValue>()) }
    val saved = remember(revision, active) { session.shape(active.id) }
    val draft = drafts[active.id] ?: saved
    val canonicalVertices = draft.pieces.vertexText()
    var vertices by remember(active, draft) {
        mutableStateOf(vertexEdits[active.id] ?: TextFieldValue(canonicalVertices))
    }
    val crop = remember(active) { asset.crop(active) }
    val bitmap = remember(active) { crop.toComposeImageBitmap() }
    val targets = asset.tiles.filter { it.id in selected }
    fun validated(tile: CollisionTile, shape: TileCollisionShape, pixelsPerMeter: Double) {
        val pieces = if (shape.mode == TileCollisionMode.FullCell) rectangle(asset.width, asset.height) else shape.pieces
        val width = if (shape.mode == TileCollisionMode.FullCell) asset.width else tile.region.width
        val height = if (shape.mode == TileCollisionMode.FullCell) asset.height else tile.region.height
        for (scaleToCheck in layerScales.map { it * TILE_PIXELS_PER_METER }.toSet() + pixelsPerMeter) {
            validateTileCollision(pieces, width, height, scaleToCheck)
        }
    }
    fun stage(shape: TileCollisionShape, fromVertices: Boolean = false) {
        runCatching {
            val batch = if (fromVertices && active.id !in selected) targets + active else targets
            require(batch.isNotEmpty()) { "Select at least one tile" }
            val ppm = scale.text.toDouble() * TILE_PIXELS_PER_METER
            val staged = batch.associate { tile ->
                validated(tile, shape, ppm)
                tile.id to shape
            }
            drafts = drafts + staged
            if (fromVertices) {
                vertexEdits = vertexEdits - active.id
                if (active.id in staged) vertices = TextFieldValue(shape.pieces.vertexText())
            }
            message = null
        }.onFailure { message = it.message }
    }
    fun work(task: () -> Unit, success: () -> Unit) {
        setBusy(true)
        scope.launch {
            try {
                withContext(Dispatchers.IO) { task() }
                success()
                message = null
            } catch (failure: Exception) {
                if (failure is kotlinx.coroutines.CancellationException) throw failure
                message = failure.message ?: "Collision operation failed"
            } finally {
                setBusy(false)
            }
        }
    }
    Row(Modifier.fillMaxSize().padding(metrics.panelPadding), horizontalArrangement = Arrangement.spacedBy(metrics.gap)) {
        Column(Modifier.width(180.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(metrics.gap)) {
            EditorLabel("Tiles")
            for (tile in asset.tiles) {
                Row(horizontalArrangement = Arrangement.spacedBy(metrics.gap)) {
                    EditorToggle("Batch", tile.id in selected, {
                        selected = if (it) selected + tile.id else selected - tile.id
                    }, enabled = !busy)
                    EditorButton(tile.name, {
                        active = tile
                        selected = selected + tile.id
                    }, enabled = !busy, selected = active == tile)
                }
            }
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(metrics.gap)) {
            EditorLabel("${active.name} · ${draft.mode.label}")
            val ppm = scale.text.toDoubleOrNull()?.takeIf { it.isFinite() && it > 0 }
            if (ppm != null) EditorLabel("${crop.width / ppm} × ${crop.height / ppm} world units")
            val overlay = when (draft.mode) {
                TileCollisionMode.None -> emptyList()
                TileCollisionMode.FullCell -> rectangle(asset.width, asset.height)
                TileCollisionMode.Custom -> draft.pieces
            }
            val accent = LocalEditorColors.current.selectionOutline
            Canvas(Modifier.fillMaxWidth().height(240.dp)) {
                val inset = metrics.gap.toPx()
                val ratio = min((size.width - 2 * inset) / maxOf(crop.width, asset.width),
                    (size.height - 2 * inset) / maxOf(crop.height, asset.height))
                if (ratio <= 0f) return@Canvas
                val origin = Offset(inset, size.height - inset)
                drawImage(bitmap, dstOffset = androidx.compose.ui.unit.IntOffset(origin.x.roundToInt(),
                    (origin.y - crop.height * ratio).roundToInt()),
                    dstSize = IntSize((crop.width * ratio).roundToInt().coerceAtLeast(1),
                        (crop.height * ratio).roundToInt().coerceAtLeast(1)), filterQuality = FilterQuality.None)
                for (polygon in overlay) {
                    val outline = Path()
                    polygon.forEachIndexed { i, point ->
                        val x = origin.x + (point.x * ratio).toFloat()
                        val y = origin.y - (point.y * ratio).toFloat()
                        if (i == 0) outline.moveTo(x, y) else outline.lineTo(x, y)
                    }
                    outline.close()
                    drawPath(outline, accent.copy(alpha = 0.25f))
                    drawPath(outline, accent, style = Stroke(2.dp.toPx()))
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(metrics.gap)) {
                EditorButton("None", { stage(TileCollisionShape(TileCollisionMode.None)) }, enabled = !busy)
                EditorButton("Full Cell", { stage(TileCollisionShape(TileCollisionMode.FullCell)) }, enabled = !busy)
                EditorButton("Generate from Alpha", {
                    val batch = targets
                    val alpha = threshold.text
                    val smoothing = tolerance.text
                    val scaleText = scale.text
                    var generated = emptyMap<String, TileCollisionShape>()
                    work({
                        require(batch.isNotEmpty()) { "Select at least one tile" }
                        val pixelsPerMeter = scaleText.toDouble() * TILE_PIXELS_PER_METER
                        generated = batch.associate { tile ->
                            val image = asset.crop(tile)
                            val pixels = image.getRGB(0, 0, image.width, image.height, null, 0, image.width)
                            for (i in pixels.indices) pixels[i] = pixels[i] ushr 24
                            val shape = TileCollisionShape(TileCollisionMode.Custom, generateTileCollision(
                                image.width, image.height, pixels, alpha.toInt(), smoothing.toDouble()))
                            validated(tile, shape, pixelsPerMeter)
                            tile.id to shape
                        }
                    }) { drafts = drafts + generated }
                }, enabled = !busy)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(metrics.gap)) {
                EditorTextField(threshold, { threshold = it }, "Alpha threshold", Modifier.weight(1f), enabled = !busy)
                EditorTextField(tolerance, { tolerance = it }, "Simplification (px)", Modifier.weight(1f), enabled = !busy)
                EditorTextField(scale, { scale = it }, "Pixels per world unit", Modifier.weight(1f), enabled = !busy)
            }
            EditorTextField(vertices, { next ->
                vertices = next
                vertexEdits = if (next.text == canonicalVertices) vertexEdits - active.id else vertexEdits + (active.id to next)
            }, "Vertices", enabled = !busy, singleLine = false,
                help = "One polygon per line. Enter counterclockwise x,y pairs in lower-left tile pixels.")
            EditorButton("Preview vertices", {
                runCatching { TileCollisionShape(TileCollisionMode.Custom, vertices.text.collisionVertices()) }
                    .onSuccess { stage(it, fromVertices = true) }.onFailure { message = it.message }
            }, enabled = !busy)
            Row(horizontalArrangement = Arrangement.spacedBy(metrics.gap)) {
                EditorButton("Apply", {
                    val pending = drafts
                    val scaleText = scale.text
                    work({
                        val pixelsPerMeter = scaleText.toDouble() * TILE_PIXELS_PER_METER
                        for (tile in asset.tiles) pending[tile.id]?.let { validated(tile, it, pixelsPerMeter) }
                        session.apply(pending)
                    }) { drafts = emptyMap(); revision++ }
                }, enabled = !busy && drafts.isNotEmpty() && vertexEdits.isEmpty())
                EditorButton("Discard preview", {
                    drafts = emptyMap()
                    vertexEdits = emptyMap()
                    vertices = TextFieldValue(saved.pieces.vertexText())
                    message = null
                },
                    enabled = !busy && (drafts.isNotEmpty() || vertexEdits.isNotEmpty()))
                EditorButton("Undo", { work(session::undo) { revision++ } }, enabled = !busy && drafts.isEmpty() && vertexEdits.isEmpty() && session.canUndo)
                EditorButton("Redo", { work(session::redo) { revision++ } }, enabled = !busy && drafts.isEmpty() && vertexEdits.isEmpty() && session.canRedo)
            }
            if (vertexEdits.isNotEmpty()) EditorLabel("Preview edited vertices before applying.")
            message?.let { EditorLabel(it) }
        }
    }
}
