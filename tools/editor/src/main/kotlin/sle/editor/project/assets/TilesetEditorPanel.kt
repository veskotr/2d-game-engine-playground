package sle.editor.project.assets

import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlinx.serialization.json.*
import sle.editor.model.*
import sle.editor.ui.*
import sle.editor.ui.controls.*
import sle.editor.ui.dialog.EditorPrompts
import sle.editor.ui.theme.*
import kotlin.math.*

@Composable
internal fun TilesetEditorPanel(
    asset: TilesetEditorAsset?, enabled: Boolean, save: () -> Unit, revert: () -> Unit,
    setCollisions: (Map<String, TileCollisionShape>) -> Unit,
    measured: (String) -> Modifier = { Modifier },
) {
    if (asset == null) {
        EditorEmptyState("No tileset open", "Select a tileset in Assets and choose Open Tileset Editor….")
        return
    }
    val notices = LocalEditorNotices.current
    val editable by rememberUpdatedState(enabled)
    val currentAsset by rememberUpdatedState(asset)
    fun act(action: () -> Unit) {
        runCatching {
            check(currentAsset === asset) { "The active tileset changed." }
            check(editable) { "Stop playback and wait for project operations to finish." }
            action()
        }
            .onFailure { notices?.error(it.message ?: "Tileset edit failed") }
    }
    val session = asset.session
    val parsed = session.parsed
    val metrics = LocalEditorMetrics.current
    val density = androidx.compose.ui.platform.LocalDensity.current.density
    val zoom = tilesetAtlasZoom(asset.image.width, asset.image.height, density, session.zoom)
    val zoomChoices = listOf(.25f, .5f, 1f, 2f, 4f, 8f, 16f)
        .filter { tilesetAtlasZoom(asset.image.width, asset.image.height, density, it) == it }
        .ifEmpty { listOf(zoom) }
    fun zoomLabel(value: Float) = "${value * 100f}%"
    asset.syncMetadata()
    fun value(key: String, initial: String) = asset.inputs[key] ?: TextFieldValue(initial)
    @Composable fun field(key: String, initial: String, hook: String, help: String? = null, multiline: Boolean = false) {
        EditorFormRow(key, help = help) {
            EditorTextField(value(key, initial), { asset.inputs[key] = it }, key,
                Modifier.weight(1f).then(measured(hook)), enabled = enabled, singleLine = !multiline)
        }
    }
    val tiles = parsed.source.getValue("tiles").jsonArray.map { it.jsonObject }
    val active = tiles.firstOrNull { it.getValue("id").jsonPrimitive.content in session.selected }
    val activeId = active?.getValue("id")?.jsonPrimitive?.content
    val region = activeId?.let(parsed.regions::get)
    val shape = active?.let { readTileCollision(it) }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(metrics.panelPadding),
            horizontalArrangement = Arrangement.spacedBy(metrics.gap)) {
            EditorLabel(asset.relative + if (session.dirty) " · Unsaved" else "")
            EditorButton("Save", { act(save) }, enabled = enabled && session.dirty,
                style = EditorButtonStyle.Primary, modifier = measured("TilesetSave"))
            EditorButton("Revert", { act {
                if (!session.dirty || EditorPrompts.confirm("Revert Tileset", "Discard unsaved changes and reload ${asset.relative}?",
                        "Revert", danger = true)) revert()
            } }, enabled = enabled)
            EditorButton("Undo", { act(session::undo) }, enabled = enabled && session.canUndo, modifier = measured("TilesetUndo"))
            EditorButton("Redo", { act(session::redo) }, enabled = enabled && session.canRedo, modifier = measured("TilesetRedo"))
            EditorSelect("Zoom", zoomLabel(zoom), zoomChoices.map(::zoomLabel),
                { session.zoom = it.removeSuffix("%").toFloat() / 100f })
        }
        EditorSeparator()
        Row(Modifier.weight(1f).fillMaxWidth()) {
            Column(Modifier.weight(1f).fillMaxHeight().padding(metrics.panelPadding),
                verticalArrangement = Arrangement.spacedBy(metrics.gap)) {
                EditorLabel("${session.selected.size} selected · drag a rectangle on the atlas")
                TilesetAtlas(asset, enabled, Modifier.weight(1f).fillMaxWidth(), measured("TilesetAtlas"))
                Row(horizontalArrangement = Arrangement.spacedBy(metrics.gap)) {
                    EditorButton("Add tile…", { act {
                        val used = parsed.regions.values.map { it.id }.toSet()
                        val available = asset.liveRegions().filter { it.id !in used }
                        require(available.isNotEmpty()) { "All sprite regions already have tiles. Use Reslice to create more." }
                        val choices = available.associateBy { "${it.name} · ${it.x},${it.y} · ${it.id}" }
                        EditorPrompts.select("Add Tile", "Region", choices.keys.toList(), confirm = "Add")?.let {
                            session.addTiles(listOf(choices.getValue(it)))
                        }
                    } }, enabled = enabled)
                    EditorButton("Remove tiles…", { act {
                        if (EditorPrompts.confirm("Remove Tiles", "Remove ${session.selected.size} tiles? Painted cells using their UUIDs will become missing tiles.",
                                "Remove", danger = true)) session.removeTiles(session.selected)
                    } }, enabled = enabled && session.selected.isNotEmpty())
                }
            }
            EditorSeparator(vertical = true)
            Column(Modifier.width(340.dp).fillMaxHeight().verticalScroll(rememberScrollState()).padding(metrics.panelPadding),
                verticalArrangement = Arrangement.spacedBy(metrics.gap)) {
                EditorSectionHeader("Selected tiles")
                if (active != null) {
                    if (session.selected.size > 1) EditorLabel("Apply replaces metadata on all selected tiles.")
                    field("Name", "", "TilesetName")
                    field("Tags", "[]", "TilesetTags", "Comma-separated tags or a JSON string array")
                    field("Properties", "{}", "TilesetProperties", "JSON object: string, number and boolean values", multiline = true)
                    EditorButton("Apply metadata", { act {
                        val properties = Json.parseToJsonElement(value("Properties", "{}").text).jsonObject
                        val tagText = value("Tags", "[]").text.trim()
                        val tags = if (tagText.startsWith('[')) Json.parseToJsonElement(tagText).jsonArray.map {
                            require(it is JsonPrimitive && it.isString) { "Tags must be strings" }; it.content
                        } else tagText.split(',').map(String::trim).filter(String::isNotEmpty)
                        session.updateTiles(session.selected, value("Name", "").text, tags, properties)
                    } }, enabled = enabled, modifier = measured("TilesetMetadata"))
                    EditorLabel("Animation reserved for a later phase")
                } else EditorLabel("Select a tile on the atlas.")
                EditorSectionHeader("Collision")
                if (region != null && shape != null) {
                    TilesetCollisionCanvas(asset, activeId, region, shape, enabled, measured("TilesetCollision")) { next -> act {
                        setCollisions(mapOf(activeId to next))
                    } }
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(metrics.gap)) {
                        EditorButton("Full Cell", { act { setCollisions(session.selected.associateWith { TileCollisionShape(TileCollisionMode.FullCell) }) } },
                            enabled = enabled, modifier = measured("TilesetFullCell"))
                        EditorButton("None", { act { setCollisions(session.selected.associateWith { TileCollisionShape(TileCollisionMode.None) }) } },
                            enabled = enabled, modifier = measured("TilesetNone"))
                        EditorButton("From Alpha", { act {
                            val threshold = value("Alpha threshold", "128").text.toInt()
                            val tolerance = value("Simplification", "0").text.toDouble()
                            // Generate the whole batch before staging, so errors cannot partially edit it.
                            val generated = session.selected.associateWith { id ->
                                val r = parsed.regions.getValue(id)
                                val crop = asset.image.getSubimage(r.x, r.y, r.width, r.height)
                                val alpha = crop.getRGB(0, 0, r.width, r.height, null, 0, r.width).map { it ushr 24 }.toIntArray()
                                TileCollisionShape(TileCollisionMode.Custom, generateTileCollision(r.width, r.height, alpha, threshold, tolerance))
                            }
                            setCollisions(generated)
                        } }, enabled = enabled, modifier = measured("TilesetFromAlpha"))
                    }
                    field("Alpha threshold", "128", "TilesetAlpha", "0–255")
                    field("Simplification", "0", "TilesetTolerance", "Pixels; 0 keeps the exact alpha contour")
                }
                EditorSectionHeader("Grid")
                val grid = parsed.grid
                field("Tile size", "${parsed.palette.width}, ${parsed.palette.height}", "TilesetSize", "Width, height in pixels")
                field("Margin", "${grid?.marginX ?: 0}, ${grid?.marginY ?: 0}", "TilesetMargin", "X, Y in pixels")
                field("Spacing", "${grid?.spacingX ?: 0}, ${grid?.spacingY ?: 0}", "TilesetSpacing", "X, Y in pixels")
                EditorButton("Reslice", { act {
                    val (width, height) = parseRegionIntegers(value("Tile size", "${parsed.palette.width}, ${parsed.palette.height}").text, 2)
                    val margin = parseRegionIntegers(value("Margin", "${grid?.marginX ?: 0}, ${grid?.marginY ?: 0}").text, 2)
                    val spacing = parseRegionIntegers(value("Spacing", "${grid?.spacingX ?: 0}, ${grid?.spacingY ?: 0}").text, 2)
                    val (regions, nextGrid) = asset.slice(width, height, margin, spacing)
                    if (EditorPrompts.confirm("Reslice Tileset", "Reslice the atlas? Tiles keep UUIDs by grid position. Tiles outside the new grid are removed; painted references to them become missing.",
                            "Reslice")) session.reslice(regions, width, height, nextGrid)
                } }, enabled = enabled, modifier = measured("TilesetReslice"))
            }
        }
    }
}

@Composable
private fun TilesetAtlas(asset: TilesetEditorAsset, enabled: Boolean, modifier: Modifier, imageModifier: Modifier) {
    val session = asset.session
    val parsed = session.parsed
    val bitmap = remember(asset.image) { asset.image.toComposeImageBitmap() }
    val colors = LocalEditorColors.current
    val metrics = LocalEditorMetrics.current
    val density = androidx.compose.ui.platform.LocalDensity.current.density
    val zoom = tilesetAtlasZoom(asset.image.width, asset.image.height, density, session.zoom)
    val scale = zoom * density
    var begin by remember { mutableStateOf<Offset?>(null) }
    var end by remember { mutableStateOf<Offset?>(null) }
    val current by rememberUpdatedState(parsed)
    fun choose(a: Offset, b: Offset) = session.selectRectangle(
        floor(min(a.x, b.x) / scale).toInt(), floor(min(a.y, b.y) / scale).toInt(),
        ceil(max(a.x, b.x) / scale).toInt().coerceAtLeast(floor(a.x / scale).toInt() + 1),
        ceil(max(a.y, b.y) / scale).toInt().coerceAtLeast(floor(a.y / scale).toInt() + 1))
    Box(modifier.background(colors.viewportBackground).clipToBounds()
        .horizontalScroll(rememberScrollState()).verticalScroll(rememberScrollState())) {
        Canvas(Modifier.size((asset.image.width * zoom).dp, (asset.image.height * zoom).dp)
            .then(imageModifier)
            .pointerInput(asset, scale, enabled) { if (enabled) detectTapGestures { choose(it, it) } }
            .pointerInput(asset, scale, enabled) { if (enabled) detectDragGestures(
                onDragStart = { begin = it; end = it },
                onDragCancel = { begin = null; end = null },
                onDragEnd = { begin?.let { a -> end?.let { b -> choose(a, b) } }; begin = null; end = null },
            ) { change, _ -> change.consume(); end = change.position } }) {
            drawImage(bitmap, dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()), filterQuality = FilterQuality.None)
            for ((id, r) in current.regions) {
                val top = Offset(r.x * scale, r.y * scale)
                val bounds = Size(r.width * scale, r.height * scale)
                if (id in session.selected) drawRect(colors.selectedBackground.copy(alpha = .4f), top, bounds)
                drawRect(if (id in session.selected) colors.selectionOutline else colors.border, top, bounds,
                    style = Stroke(metrics.borderWidth.toPx()))
            }
            begin?.let { a -> end?.let { b ->
                drawRect(colors.selectionOutline, Offset(min(a.x,b.x),min(a.y,b.y)), Size(abs(a.x-b.x),abs(a.y-b.y)),
                    style = Stroke(metrics.focusBorderWidth.toPx()))
            } }
        }
    }
}

@Composable
private fun TilesetCollisionCanvas(asset: TilesetEditorAsset, id: String, region: SpriteRegion, shape: TileCollisionShape,
    enabled: Boolean, modifier: Modifier, stage: (TileCollisionShape) -> Unit) {
    val colors = LocalEditorColors.current
    val metrics = LocalEditorMetrics.current
    val bitmap = remember(asset.image, region) { asset.image.getSubimage(region.x, region.y, region.width, region.height).toComposeImageBitmap() }
    val cell = asset.session.parsed.palette
    val width = max(region.width, cell.width)
    val height = max(region.height, cell.height)
    val polygons = when (shape.mode) {
        TileCollisionMode.None -> emptyList()
        TileCollisionMode.FullCell -> listOf(listOf(TileCollisionPoint(0.0,0.0), TileCollisionPoint(cell.width.toDouble(),0.0),
            TileCollisionPoint(cell.width.toDouble(),cell.height.toDouble()), TileCollisionPoint(0.0,cell.height.toDouble())))
        TileCollisionMode.Custom -> shape.pieces
    }
    var preview by remember(asset, id, shape, polygons) { mutableStateOf(polygons) }
    val latestStage by rememberUpdatedState(stage)
    Canvas(modifier.fillMaxWidth().height(200.dp).clipToBounds().background(colors.viewportBackground)
        .pointerInput(asset, id, shape, enabled, width, height) {
            if (!enabled) return@pointerInput
            var handle: Pair<Int, Int>? = null
            val inset = metrics.panelPadding.toPx()
            fun ratio() = min((size.width - 2 * inset) / width, (size.height - 2 * inset) / height)
            fun position(point: TileCollisionPoint) = Offset(inset + point.x.toFloat() * ratio(), size.height - inset - point.y.toFloat() * ratio())
            detectDragGestures(onDragStart = { cursor ->
                handle = preview.flatMapIndexed { p, polygon -> polygon.indices.map { v -> p to v } }
                    .minByOrNull { (p,v) -> (position(preview[p][v]) - cursor).getDistance() }
                    ?.takeIf { (p,v) -> (position(preview[p][v]) - cursor).getDistance() <= metrics.icon.toPx() }
            }, onDragCancel = { preview = polygons; handle = null }, onDragEnd = {
                if (handle != null) latestStage(TileCollisionShape(TileCollisionMode.Custom, preview))
                preview = polygons; handle = null
            }) { change, _ ->
                handle?.let { (p,v) ->
                    change.consume()
                    val scale = ratio()
                    if (scale > 0) {
                        val point = TileCollisionPoint(((change.position.x - inset) / scale).toDouble().coerceIn(0.0,region.width.toDouble()),
                            ((size.height - inset - change.position.y) / scale).toDouble().coerceIn(0.0,region.height.toDouble()))
                        preview = preview.mapIndexed { pi, polygon -> if (pi != p) polygon else polygon.mapIndexed { vi, old -> if (vi == v) point else old } }
                    }
                }
            }
        }) {
        val inset = metrics.panelPadding.toPx()
        val ratio = min((size.width - 2 * inset) / width, (size.height - 2 * inset) / height)
        if (ratio <= 0f) return@Canvas
        fun position(point: TileCollisionPoint) = Offset(inset + point.x.toFloat() * ratio, size.height - inset - point.y.toFloat() * ratio)
        drawImage(bitmap, dstOffset = IntOffset(inset.roundToInt(), (size.height-inset-region.height*ratio).roundToInt()),
            dstSize = IntSize((region.width*ratio).roundToInt().coerceAtLeast(1),(region.height*ratio).roundToInt().coerceAtLeast(1)), filterQuality = FilterQuality.None)
        for (polygon in preview) {
            val path = Path()
            polygon.forEachIndexed { index, vertex -> position(vertex).let { if (index == 0) path.moveTo(it.x,it.y) else path.lineTo(it.x,it.y) } }
            path.close()
            drawPath(path, colors.selectionOutline.copy(alpha = .25f))
            drawPath(path, colors.selectionOutline, style = Stroke(metrics.focusBorderWidth.toPx()))
            polygon.forEach { drawCircle(colors.selectionOutline, metrics.gap.toPx(), position(it)) }
        }
    }
}
