package sle.editor.model

import kotlinx.serialization.json.*
import sle.editor.project.assets.SpriteRegion
import sle.editor.project.assets.readTileCollision
import java.util.UUID

/** Most tiles one tileset may hold; matches the native loader (`TileLayerLoader.cpp`). */
internal const val MAX_TILESET_TILES = 4096
internal const val MAX_TILESET_BYTES = 4 * 1024 * 1024
private const val TILESET_FORMAT = "sle-tileset"
private val tilesetFields = setOf("format", "version", "spritesheet", "tileWidth", "tileHeight", "tiles")
private val tileFields = setOf("id", "name", "region")
private val tileMetadataFields = setOf("tags", "properties", "animation")
private val jsonInteger = Regex("-?(0|[1-9][0-9]*)")
private val jsonNumber = Regex("-?(0|[1-9][0-9]*)(\\.[0-9]+)?([eE][+-]?[0-9]+)?")

internal data class TilesetGrid(val marginX: Int, val marginY: Int, val spacingX: Int, val spacingY: Int, val columns: Int) {
    init {
        require(minOf(marginX, marginY, spacingX, spacingY) >= 0) { "Margins and spacing must be nonnegative" }
        require(columns > 0) { "Grid columns must be positive" }
    }

    fun encode() = buildJsonObject {
        putJsonArray("margin") {
            add(marginX)
            add(marginY)
        }
        putJsonArray("spacing") {
            add(spacingX)
            add(spacingY)
        }
        put("columns", columns)
    }
}

/** A `*.tileset.json` read against the project's spritesheet regions. */
internal data class TilesetDocument(
    val palette: TilePalette,
    /** Asset-relative path of the spritesheet texture. */
    val atlas: String,
    /** Sprite region of each tile, by tile UUID. */
    val regions: Map<String, SpriteRegion>,
    val source: JsonObject,
    val grid: TilesetGrid?,
)

private fun canonicalUuid(value: String) = runCatching { UUID.fromString(value).toString() == value }.getOrDefault(false)

/**
 * Reads a v1 or v2 tileset. [reference] is the tileset's own `project-asset://` reference;
 * [atlasPath] resolves the spritesheet UUID to its asset path and [atlasRegions] lists that
 * texture's regions. The checks mirror the strict native loader, so a file that parses here loads there.
 */
internal fun parseTileset(
    text: String,
    reference: String,
    atlasPath: (String) -> String?,
    atlasRegions: (String) -> List<SpriteRegion>,
): TilesetDocument {
    require(text.encodeToByteArray().size <= MAX_TILESET_BYTES) { "Tileset exceeds 4 MiB" }
    val source = Json.parseToJsonElement(text) as? JsonObject
    requireNotNull(source) { "Expected a tileset object" }
    return parseTileset(source, reference, atlasPath, atlasRegions)
}

internal fun parseTileset(
    source: JsonObject,
    reference: String,
    atlasPath: (String) -> String?,
    atlasRegions: (String) -> List<SpriteRegion>,
): TilesetDocument {
    val version = tilesetInteger(source["version"], "version", 1)
    require(source["format"] == JsonPrimitive(TILESET_FORMAT) && version in 1..2) { "Unsupported tileset format/version" }
    val expectedFields = if (version == 2) tilesetFields + "grid" else tilesetFields
    require(source.keys == expectedFields) { "Unsupported or missing tileset fields" }
    val grid = if (version == 2) readTilesetGrid(source["grid"]) else null
    val width = tilesetInteger(source["tileWidth"], "tileWidth", 1)
    val height = tilesetInteger(source["tileHeight"], "tileHeight", 1)
    val spritesheet = tilesetString(source["spritesheet"], "spritesheet")
    val atlasId = spritesheet.removePrefix("project-asset://")
    require(spritesheet != atlasId && canonicalUuid(atlasId)) { "Tileset spritesheet must use a project asset UUID" }
    val atlas = requireNotNull(atlasPath(atlasId)) { "Missing spritesheet $atlasId" }
    val regions = atlasRegions(atlas).associateBy { it.id }
    val swatchRegions = mutableMapOf<String, SpriteRegion>()
    val rawTiles = source["tiles"] as? JsonArray
    requireNotNull(rawTiles) { "Expected a tiles array" }
    require(rawTiles.size <= MAX_TILESET_TILES) { "Tileset exceeds $MAX_TILESET_TILES tiles" }
    val allowedFields = if (version == 2) tileFields + "collision" + tileMetadataFields else tileFields + "collision"
    val tiles = rawTiles.map { entry ->
        val tile = entry as? JsonObject
        require(tile != null && tile.keys.containsAll(tileFields) && allowedFields.containsAll(tile.keys)) {
            "Unsupported or missing tile fields"
        }
        val tileId = tilesetString(tile["id"], "tile UUID")
        require(canonicalUuid(tileId)) { "Invalid tile UUID $tileId" }
        val regionId = tilesetString(tile["region"], "tile region UUID")
        require(canonicalUuid(regionId)) { "Invalid region UUID $regionId" }
        val region = requireNotNull(regions[regionId]) { "Tile $tileId has missing region $regionId" }
        require(swatchRegions.put(tileId, region) == null) { "Duplicate tile UUID $tileId" }
        readTileCollision(tile, region.width, region.height)
        val name = tilesetName(tilesetString(tile["name"], "tile name"), "name for tile $tileId")
        if (version == 2) validateTileMetadata(tile)
        TileSwatch(tileId, name)
    }
    return TilesetDocument(TilePalette(reference, width, height, tiles), atlas, swatchRegions, source, grid)
}

private fun tilesetInteger(value: JsonElement?, label: String, minimum: Int): Int {
    val field = value as? JsonPrimitive
    val number = field?.takeUnless { it.isString || !jsonInteger.matches(it.content) }?.intOrNull
    require(number != null && number >= minimum) { "Expected $label integer of at least $minimum" }
    return number
}

private fun tilesetString(value: JsonElement?, label: String): String {
    require(value is JsonPrimitive && value.isString) { "Expected $label string" }
    return value.content
}

private fun tilesetName(name: String, label: String): String {
    require(name.isNotBlank() && name.encodeToByteArray().size <= 256) { "Invalid $label" }
    return name
}

private fun readTilesetGrid(value: JsonElement?): TilesetGrid {
    require(value is JsonObject && value.keys == setOf("margin", "spacing", "columns")) { "Invalid tileset grid fields" }
    fun pair(key: String): Pair<Int, Int> {
        val pair = value[key] as? JsonArray
        require(pair?.size == 2) { "Expected grid $key pair" }
        return tilesetInteger(pair[0], "grid $key", 0) to tilesetInteger(pair[1], "grid $key", 0)
    }
    val (marginX, marginY) = pair("margin")
    val (spacingX, spacingY) = pair("spacing")
    return TilesetGrid(marginX, marginY, spacingX, spacingY, tilesetInteger(value["columns"], "grid columns", 1))
}

private fun validateTileMetadata(tile: JsonObject) {
    tile["tags"]?.let { tags ->
        require(tags is JsonArray && tags.size <= 256) { "Expected an array of at most 256 tile tags" }
        val names = tags.map { tilesetName(tilesetString(it, "tile tag"), "tile tag") }
        require(names.toSet().size == names.size) { "Tile tags must be unique" }
    }
    tile["properties"]?.let { properties ->
        require(properties is JsonObject && properties.size <= 256) { "Expected an object of at most 256 tile properties" }
        for ((key, value) in properties) {
            tilesetName(key, "tile property name")
            require(value is JsonPrimitive && value != JsonNull) { "Tile properties must be strings, numbers, or booleans" }
            if (value.isString) {
                require(value.content.encodeToByteArray().size <= 4096) { "Tile property string exceeds 4096 UTF-8 bytes" }
            } else {
                require(value == JsonPrimitive(true) || value == JsonPrimitive(false) ||
                    (jsonNumber.matches(value.content) && value.doubleOrNull?.isFinite() == true)) {
                    "Tile properties must be strings, finite numbers, or booleans"
                }
            }
        }
    }
    require(tile["animation"] == null || tile["animation"] == JsonNull) { "Tile animation is reserved and must be null" }
}

/** Retains the v1 writer for callers without grid settings. */
internal fun writeTileset(
    spritesheetUuid: String,
    tileWidth: Int,
    tileHeight: Int,
    regions: List<SpriteRegion>,
    newId: () -> String = { UUID.randomUUID().toString() },
): ByteArray = writeTilesetDocument(spritesheetUuid, tileWidth, tileHeight, regions, null, newId)

/** Writes a v2 tileset in row-major order with one new tile UUID per region. */
internal fun writeTileset(
    spritesheetUuid: String,
    tileWidth: Int,
    tileHeight: Int,
    regions: List<SpriteRegion>,
    grid: TilesetGrid,
    newId: () -> String = { UUID.randomUUID().toString() },
): ByteArray = writeTilesetDocument(spritesheetUuid, tileWidth, tileHeight, regions, grid, newId)

private fun writeTilesetDocument(
    spritesheetUuid: String,
    tileWidth: Int,
    tileHeight: Int,
    regions: List<SpriteRegion>,
    grid: TilesetGrid?,
    newId: () -> String,
): ByteArray {
    require(canonicalUuid(spritesheetUuid)) { "Spritesheet needs a project asset UUID" }
    require(tileWidth > 0 && tileHeight > 0) { "Tile dimensions must be positive" }
    require(regions.size <= MAX_TILESET_TILES) { "A tileset holds at most $MAX_TILESET_TILES tiles" }
    val ids = mutableSetOf<String>()
    val tiles = regions.sortedWith(compareBy({ it.y }, { it.x })).map { region ->
        val id = newId()
        require(canonicalUuid(id) && ids.add(id)) { "Tile UUIDs must be unique" }
        tilesetTile(region, id)
    }
    val document = buildJsonObject {
        put("format", TILESET_FORMAT)
        put("version", if (grid == null) 1 else 2)
        put("spritesheet", "project-asset://$spritesheetUuid")
        put("tileWidth", tileWidth)
        put("tileHeight", tileHeight)
        if (grid != null) put("grid", grid.encode())
        put("tiles", JsonArray(tiles))
    }
    return (documentJson.encodeToString(JsonObject.serializer(), document) + "\n").encodeToByteArray()
}

internal fun tilesetTile(region: SpriteRegion, id: String = UUID.randomUUID().toString()): JsonObject = buildJsonObject {
    put("id", id)
    put("name", tilesetName(region.name, "name for region ${region.id}"))
    require(canonicalUuid(region.id)) { "Invalid region UUID ${region.id}" }
    put("region", region.id)
}
