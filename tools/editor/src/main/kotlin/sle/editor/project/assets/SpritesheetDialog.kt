package sle.editor.project.assets

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.HorizontalScrollbar
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.rememberDialogState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import sle.editor.ui.dialog.EditorPrompts
import sle.editor.ui.EditorTheme
import sle.editor.ui.controls.*
import sle.editor.ui.theme.*
import java.awt.image.BufferedImage
import javax.imageio.ImageIO
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

@Composable
internal fun SpritesheetDialog(
    path: String,
    regions: List<SpriteRegion>,
    commands: ProjectAssetCommands,
    measured: (String) -> Modifier,
    onClose: () -> Unit,
) {
    val appearance = LocalEditorAppearance.current
    var submitted by remember { mutableStateOf(false) }
    val source by produceState<Result<BufferedImage>?>(null, path, commands.snapshot?.byPath?.get(path)) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val file = assetPath(requireNotNull(commands.projectRoot), requireNotNull(commands.assetRoot), path)
                requireNotNull(ImageIO.read(file.toFile())) { "Image format is unsupported for slicing" }
            }
        }
    }
    DialogWindow(onCloseRequest = onClose, title = "Spritesheet — $path",
        state = rememberDialogState(width = 900.dp, height = 590.dp)) {
        EditorTheme(appearance) {
            Surface(Modifier.fillMaxSize()) {
                val loaded = source
                val image = loaded?.getOrNull()
                if (image != null) {
                    val createTileset = commands.createTileset?.let { create ->
                        { cells: List<SpriteRectangle>, tileWidth: Int, tileHeight: Int, grid: sle.editor.model.TilesetGrid ->
                            val relative = EditorPrompts.input("Create Tileset", "Path",
                                defaultTilesetPath(path, commands.snapshot), "Relative to the project asset folder.",
                                confirm = "Create") { if (it.isBlank()) "Enter a path" else null }?.trim()
                            if (!relative.isNullOrEmpty()) {
                                submitted = true
                                create(path, cells, tileWidth, tileHeight,
                                    if (relative.endsWith(".tileset.json", ignoreCase = true)) relative
                                    else relative.removeSuffix(".json") + ".tileset.json", grid)
                            }
                        }
                    }
                    SpritesheetEditor(image, regions, !commands.busy, commands.message.takeIf { submitted },
                        measured, onClose, createTileset) { edit ->
                        submitted = true
                        commands.editRegions(path, edit)
                    }
                } else {
                    Box(Modifier.padding(LocalEditorMetrics.current.panelPadding)) {
                        EditorLabel(loaded?.exceptionOrNull()?.message ?: "Loading texture…")
                    }
                }
            }
        }
    }
}

@Composable
private fun SpritesheetEditor(
    image: BufferedImage,
    regions: List<SpriteRegion>,
    enabled: Boolean,
    message: String?,
    measured: (String) -> Modifier,
    onClose: () -> Unit,
    /** Create Tileset…: (grid cells, tile width, tile height); null when the project cannot create files. */
    createTileset: ((List<SpriteRectangle>, Int, Int, sle.editor.model.TilesetGrid) -> Unit)?,
    save: ((List<SpriteRegion>) -> List<SpriteRegion>) -> Unit,
) {
    val metrics = LocalEditorMetrics.current
    val modes = listOf("Grid", "Manual rectangle")
    var mode by remember { mutableStateOf(modes.first()) }
    var zoom by remember { mutableStateOf("100%") }
    var cellText by remember { mutableStateOf(TextFieldValue("16, 16")) }
    var marginText by remember { mutableStateOf(TextFieldValue("0, 0")) }
    var spacingText by remember { mutableStateOf(TextFieldValue("0, 0")) }
    var skipTransparent by remember { mutableStateOf(true) }
    val gridMode = mode == modes.first()
    val grid = if (gridMode) key(image, cellText.text, marginText.text, spacingText.text, skipTransparent) {
        val result by produceState<Result<List<SpriteRectangle>>?>(null) {
            value = withContext(Dispatchers.Default) {
                runCatching {
                    val (width, height) = parseRegionIntegers(cellText.text, 2)
                    val (marginX, marginY) = parseRegionIntegers(marginText.text, 2)
                    val (spacingX, spacingY) = parseRegionIntegers(spacingText.text, 2)
                    SpriteGrid(width, height, marginX, marginY, spacingX, spacingY, skipTransparent).rectangles(image)
                }
            }
        }
        result
    } else null
    var selectedId by remember { mutableStateOf<String?>(null) }
    val selected = regions.firstOrNull { it.id == selectedId }
    var name by remember(selected) { mutableStateOf(TextFieldValue(selected?.name.orEmpty())) }
    var rectangleText by remember(selected) {
        mutableStateOf(TextFieldValue(selected?.let { "${it.x}, ${it.y}, ${it.width}, ${it.height}" }.orEmpty()))
    }
    val rectangle = remember(rectangleText.text, image) {
        runCatching {
            val (x, y, width, height) = parseRegionIntegers(rectangleText.text, 4)
            require(x >= 0 && y >= 0 && width > 0 && height > 0 &&
                x.toLong() + width <= image.width && y.toLong() + height <= image.height) {
                "Rectangle must fit inside the image"
            }
            SpriteRectangle(x, y, width, height)
        }
    }
    val cells = grid?.getOrNull().orEmpty()
    val bitmap = remember(image) { image.toComposeImageBitmap() }
    Row(Modifier.fillMaxSize().padding(metrics.panelPadding), horizontalArrangement = Arrangement.spacedBy(metrics.gap)) {
        Column(Modifier.width(305.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(metrics.gap)) {
            EditorFormRow("Mode") {
                EditorSelect("Mode", mode, modes, onSelect = { mode = it }, modifier = Modifier.weight(1f).then(measured("SpritesheetMode")),
                    showLabel = false)
            }
            EditorFormRow("Zoom") {
                EditorSelect("Zoom", zoom, listOf("25%", "50%", "100%", "200%", "400%"), onSelect = { zoom = it },
                    modifier = Modifier.weight(1f), showLabel = false)
            }
            if (gridMode) {
                SpritesheetField(cellText, { cellText = it }, "Cell size", "Cell width, height in pixels", modifier = measured("SpritesheetCell"))
                SpritesheetField(marginText, { marginText = it }, "Margins", "Margin X, Y in pixels", modifier = measured("SpritesheetMargin"))
                SpritesheetField(spacingText, { spacingText = it }, "Spacing", "Spacing X, Y in pixels", modifier = measured("SpritesheetSpacing"))
                EditorCheckBox("Skip fully transparent cells", skipTransparent, { skipTransparent = it })
                val pending = remember(cells, regions) {
                    val saved = regions.mapTo(mutableSetOf()) { it.rectangle() }
                    cells.count { it !in saved }
                }
                val error = grid?.exceptionOrNull()?.message
                EditorLabel(error ?: if (grid == null) "Calculating grid…" else "$pending new regions")
                Row(horizontalArrangement = Arrangement.spacedBy(metrics.gap)) {
                    EditorButton("Add grid regions", { save { appendGridRegions(it, cells) } },
                        enabled = enabled && pending > 0, modifier = measured("SpritesheetAdd"))
                    if (createTileset != null) {
                        val size = runCatching { parseRegionIntegers(cellText.text, 2) }.getOrNull()
                        EditorButton("Create Tileset…", { size?.let { (width, height) ->
                            val (mx,my) = parseRegionIntegers(marginText.text,2)
                            val (sx,sy) = parseRegionIntegers(spacingText.text,2)
                            val columns = ((image.width.toLong()-2L*mx+sx)/(width.toLong()+sx)).toInt()
                            createTileset(cells, width, height, sle.editor.model.TilesetGrid(mx,my,sx,sy,columns))
                        } },
                            enabled = enabled && size != null && cells.isNotEmpty(), primary = true,
                            help = "Add the grid regions, then save a tileset with one tile per cell",
                            modifier = measured("SpritesheetCreateTileset"))
                    }
                }
            } else {
                val labels = regions.associateBy { it.caption() }
                val newRegion = "New region"
                EditorFormRow("Region") {
                    EditorSelect("Region", selected?.caption() ?: newRegion, listOf(newRegion) + labels.keys,
                        onSelect = { selectedId = labels[it]?.id }, modifier = Modifier.weight(1f).then(measured("SpritesheetRegion")),
                        showLabel = false)
                }
                SpritesheetField(name, { name = it }, "Name", "Region name", modifier = measured("SpritesheetName"))
                SpritesheetField(rectangleText, { rectangleText = it }, "Rectangle", "Left, top, width, height in pixels")
                EditorLabel("Drag on the image to set the rectangle.")
                if (rectangleText.text.isNotBlank()) rectangle.exceptionOrNull()?.message?.let { EditorLabel(it) }
                EditorButton("Save region", {
                    val bounds = rectangle.getOrThrow()
                    val title = name.text.trim()
                    val id = selected?.id
                    val created = if (id == null) bounds.region(title) else null
                    if (created != null) selectedId = created.id
                    save { current ->
                        if (created != null) current + created
                        else {
                            require(current.any { it.id == id }) { "Region $id is no longer available" }
                            current.map {
                                if (it.id == id) it.copy(name = title, x = bounds.x, y = bounds.y,
                                    width = bounds.width, height = bounds.height) else it
                            }
                        }
                    }
                }, enabled = enabled && rectangle.isSuccess && name.text.isNotBlank(), primary = true,
                    modifier = measured("SpritesheetSave"))
            }
            message?.let { EditorLabel(it) }
            EditorButton("Close", onClose, modifier = measured("SpritesheetClose"))
        }
        SpritesheetPreview(bitmap, zoom.dropLast(1).toInt() / 100f, regions,
            if (gridMode) cells else listOfNotNull(rectangle.getOrNull()), !gridMode, Modifier.weight(1f).fillMaxHeight(),
            measured("SpritesheetCanvas")) {
            rectangleText = TextFieldValue("${it.x}, ${it.y}, ${it.width}, ${it.height}")
        }
    }
}

@Composable
private fun SpritesheetField(
    value: TextFieldValue,
    update: (TextFieldValue) -> Unit,
    label: String,
    help: String,
    modifier: Modifier = Modifier,
) {
    EditorFormRow(label, help = help) {
        EditorTextField(value, update, label, modifier = Modifier.weight(1f).then(modifier), help = help, placeholder = help)
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun SpritesheetPreview(
    bitmap: ImageBitmap,
    zoom: Float,
    saved: List<SpriteRegion>,
    preview: List<SpriteRectangle>,
    drawManual: Boolean,
    modifier: Modifier,
    canvasModifier: Modifier,
    onRectangle: (SpriteRectangle) -> Unit,
) {
    val currentOnRectangle by rememberUpdatedState(onRectangle)
    var dragRect by remember(bitmap, zoom, drawManual) { mutableStateOf<SpriteRectangle?>(null) }
    val horizontal = rememberScrollState()
    val vertical = rememberScrollState()
    Box(modifier.clipToBounds()) {
        Box(Modifier.fillMaxSize().padding(end = 12.dp, bottom = 12.dp)
            .horizontalScroll(horizontal).verticalScroll(vertical)) {
            Canvas(Modifier.size((bitmap.width * zoom).coerceAtLeast(1f).dp, (bitmap.height * zoom).coerceAtLeast(1f).dp)
                .then(canvasModifier)
                .pointerInput(bitmap, zoom, drawManual) {
                    if (!drawManual) return@pointerInput
                    var start = Offset.Zero
                    fun bounds(end: Offset): SpriteRectangle {
                        val scaleX = bitmap.width.toFloat() / size.width
                        val scaleY = bitmap.height.toFloat() / size.height
                        val left = floor(minOf(start.x, end.x) * scaleX).toInt().coerceIn(0, bitmap.width - 1)
                        val top = floor(minOf(start.y, end.y) * scaleY).toInt().coerceIn(0, bitmap.height - 1)
                        val right = ceil(maxOf(start.x, end.x) * scaleX).toInt().coerceIn(left + 1, bitmap.width)
                        val bottom = ceil(maxOf(start.y, end.y) * scaleY).toInt().coerceIn(top + 1, bitmap.height)
                        return SpriteRectangle(left, top, right - left, bottom - top)
                    }
                    detectDragGestures(
                        orientationLock = null,
                        onDragStart = { down, trigger, _ ->
                            start = down.position
                            dragRect = bounds(trigger.position)
                        },
                        onDragEnd = { up ->
                            currentOnRectangle(bounds(up.position))
                            dragRect = null
                        },
                        onDragCancel = { dragRect = null },
                        onDrag = { change, _ ->
                            change.consume()
                            dragRect = bounds(change.position)
                        },
                    )
                }) {
                val square = 12.dp.toPx()
                drawRect(Color(0xff4b4b4b))
                val firstColumn = (horizontal.value / square).toInt()
                val firstRow = (vertical.value / square).toInt()
                val lastColumn = minOf(ceil(size.width / square).toInt(),
                    ceil((horizontal.value.toFloat() + horizontal.viewportSize) / square).toInt())
                val lastRow = minOf(ceil(size.height / square).toInt(),
                    ceil((vertical.value.toFloat() + vertical.viewportSize) / square).toInt())
                for (row in firstRow until lastRow) for (column in firstColumn until lastColumn) {
                    if ((row + column) % 2 == 0)
                        drawRect(Color(0xff666666), Offset(column * square, row * square), Size(square, square))
                }
                drawImage(bitmap, dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
                    filterQuality = FilterQuality.None)
                val scaleX = size.width / bitmap.width
                val scaleY = size.height / bitmap.height
                fun outline(x: Int, y: Int, width: Int, height: Int, color: Color) {
                    drawRect(color, Offset(x * scaleX, y * scaleY), Size(width * scaleX, height * scaleY), style = Stroke(1.5.dp.toPx()))
                }
                saved.forEach { outline(it.x, it.y, it.width, it.height, Color.White) }
                (dragRect?.let(::listOf) ?: preview).forEach {
                    outline(it.x, it.y, it.width, it.height, Color(0xff59dfac))
                }
            }
        }
        HorizontalScrollbar(rememberScrollbarAdapter(horizontal),
            Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(end = 12.dp))
        VerticalScrollbar(rememberScrollbarAdapter(vertical),
            Modifier.align(Alignment.TopEnd).fillMaxHeight().padding(bottom = 12.dp))
    }
}
