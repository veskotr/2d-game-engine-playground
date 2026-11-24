package sle.editor.project.assets

import kotlinx.serialization.json.*
import java.nio.file.Path
import java.util.UUID
import javax.imageio.ImageIO

internal data class SpriteRegion(val id: String, val name: String, val x: Int, val y: Int, val width: Int, val height: Int) {
    fun fits(imageWidth: Int, imageHeight: Int) =
        x.toLong() + width <= imageWidth && y.toLong() + height <= imageHeight

    fun sameRectangle(other: SpriteRegion) =
        x == other.x && y == other.y && width == other.width && height == other.height

    fun encode() = buildJsonObject {
        put("id", id)
        put("name", name)
        put("x", x)
        put("y", y)
        put("width", width)
        put("height", height)
    }
}

private const val MAX_REGIONS = 4096
private val regionIdPattern = Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")

internal fun validateRegions(regions: List<SpriteRegion>) {
    require(regions.size <= MAX_REGIONS) { "At most $MAX_REGIONS regions are supported" }
    val ids = mutableSetOf<String>()
    for (region in regions) {
        require(regionIdPattern.matches(region.id) && ids.add(region.id)) { "Invalid or duplicate region ID" }
        require(region.name.trim(' ', '\t', '\r', '\n').isNotEmpty() &&
            region.name.encodeToByteArray().size <= 256) { "Enter a region name of at most 256 UTF-8 bytes" }
        require(region.x >= 0 && region.y >= 0 && region.width > 0 && region.height > 0) { "Invalid region rectangle" }
    }
}

internal fun decodeRegions(value: JsonElement): List<SpriteRegion> {
    require(value is JsonArray && value.size <= MAX_REGIONS) { "Expected texture.regions array" }
    return value.map { entry ->
        require(entry is JsonObject && entry.keys == setOf("id", "name", "x", "y", "width", "height")) {
            "Invalid region fields"
        }
        fun string(key: String): String {
            val field = entry[key] as? JsonPrimitive
            require(field?.isString == true) { "Expected region $key string" }
            return field.content
        }
        fun integer(key: String): Int {
            val field = entry[key] as? JsonPrimitive
            require(field?.isString == false) { "Expected region $key integer" }
            return requireNotNull(field.intOrNull) { "Expected region $key integer" }
        }
        SpriteRegion(string("id"), string("name"), integer("x"), integer("y"), integer("width"), integer("height"))
    }.also(::validateRegions)
}

internal fun spriteImageSize(path: Path): Pair<Int, Int> {
    val stream = requireNotNull(ImageIO.createImageInputStream(path.toFile())) { "Cannot open texture image" }
    return stream.use {
        val reader = ImageIO.getImageReaders(it).asSequence().firstOrNull()
        requireNotNull(reader) { "Image format is unsupported for slicing" }
        try {
            reader.input = it
            reader.getWidth(0) to reader.getHeight(0)
        } finally {
            reader.dispose()
        }
    }
}

internal fun parseRegionIntegers(input: String, count: Int): List<Int> {
    val values = input.trim().split(Regex("[,\\s]+"))
    require(values.size == count) { "Enter $count whole numbers" }
    return values.map { requireNotNull(it.toIntOrNull()) { "Enter $count whole numbers" } }
}

internal data class SpriteRectangle(val x: Int, val y: Int, val width: Int, val height: Int) {
    fun region(name: String) = SpriteRegion(UUID.randomUUID().toString(), name, x, y, width, height)
}

internal fun SpriteRegion.rectangle() = SpriteRectangle(x, y, width, height)

internal data class SpriteGrid(
    val width: Int, val height: Int,
    val marginX: Int = 0, val marginY: Int = 0,
    val spacingX: Int = 0, val spacingY: Int = 0,
    val skipTransparent: Boolean = true,
) {
    fun rectangles(image: java.awt.image.BufferedImage): List<SpriteRectangle> {
        require(width > 0 && height > 0) { "Enter positive cell dimensions" }
        require(minOf(marginX, marginY, spacingX, spacingY) >= 0) { "Margins and spacing must be nonnegative" }
        val stepX = width.toLong() + spacingX
        val stepY = height.toLong() + spacingY
        fun count(size: Int, margin: Int, cell: Int, step: Long): Long {
            val remaining = size - 2L * margin - cell
            return if (remaining < 0) 0 else 1 + remaining / step
        }
        val columns = count(image.width, marginX, width, stepX)
        val rows = count(image.height, marginY, height, stepY)
        require(columns * rows in 1..MAX_REGIONS.toLong()) { "The grid must contain 1 to $MAX_REGIONS complete cells" }
        return buildList {
            for (row in 0 until rows) for (column in 0 until columns) {
                val x = (marginX + column * stepX).toInt()
                val y = (marginY + row * stepY).toInt()
                if (!skipTransparent || image.hasVisiblePixels(x, y, width, height)) {
                    add(SpriteRectangle(x, y, width, height))
                }
            }
        }
    }
}

private fun java.awt.image.BufferedImage.hasVisiblePixels(x: Int, y: Int, width: Int, height: Int): Boolean {
    if (!colorModel.hasAlpha()) return true
    for (row in y until y + height) for (column in x until x + width) {
        if (getRGB(column, row) ushr 24 != 0) return true
    }
    return false
}

internal fun appendGridRegions(existing: List<SpriteRegion>, cells: List<SpriteRectangle>): List<SpriteRegion> {
    val rectangles = existing.mapTo(mutableSetOf()) { it.rectangle() }
    val names = existing.mapTo(mutableSetOf()) { it.name }
    var index = 1
    return existing + cells.filter { rectangles.add(it) }.map { cell ->
        var name: String
        do {
            name = "Slice ${index++}"
        } while (!names.add(name))
        cell.region(name)
    }
}
