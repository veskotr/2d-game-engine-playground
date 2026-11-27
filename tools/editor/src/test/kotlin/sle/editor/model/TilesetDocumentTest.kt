package sle.editor.model

import kotlinx.serialization.json.*
import sle.editor.project.assets.SpriteRegion
import kotlin.test.*

class TilesetDocumentTest {
    private val sheet = "00000004-1111-4111-8111-111111111111"
    private val atlas = "Textures/Terrain.png"
    private fun region(index: Int, name: String, x: Int, y: Int) =
        SpriteRegion("%08d-2222-4222-8222-222222222222".format(index), name, x, y, 16, 16)
    private fun parse(bytes: ByteArray, regions: List<SpriteRegion>) =
        parseTileset(bytes.decodeToString(), "project-asset://00000009-1111-4111-8111-111111111111",
            { if (it == sheet) atlas else null }, { if (it == atlas) regions else emptyList() })

    @Test fun writtenTilesetRoundTripsInSheetOrderWithNativeKeysOnly() {
        val regions = listOf(region(1, "Wall", 16, 0), region(2, "Water", 0, 16), region(3, "Grass", 0, 0))
        val bytes = writeTileset(sheet, 16, 16, regions)
        val json = Json.parseToJsonElement(bytes.decodeToString()).jsonObject
        assertEquals(setOf("format", "version", "spritesheet", "tileWidth", "tileHeight", "tiles"), json.keys)
        assertEquals("project-asset://$sheet", json.getValue("spritesheet").jsonPrimitive.content)
        assertTrue(json.getValue("tiles").jsonArray.all { it.jsonObject.keys == setOf("id", "name", "region") })

        val tileset = parse(bytes, regions)
        assertEquals(atlas, tileset.atlas)
        assertEquals(16, tileset.palette.width); assertEquals(16, tileset.palette.height)
        assertEquals(listOf("Grass", "Wall", "Water"), tileset.palette.tiles.map { it.name }, "row-major by (y, x)")
        assertEquals(listOf(regions[2], regions[0], regions[1]), tileset.palette.tiles.map { tileset.regions.getValue(it.id) })
        assertEquals(3, tileset.palette.tiles.map { it.id }.toSet().size)
    }

    @Test fun emptyRegionListWritesAnEmptyTileset() {
        val tileset = parse(writeTileset(sheet, 8, 4, emptyList()), emptyList())
        assertEquals(emptyList(), tileset.palette.tiles)
        assertEquals(8, tileset.palette.width); assertEquals(4, tileset.palette.height)
    }

    @Test fun duplicateRegionNamesStillGetDistinctTiles() {
        val regions = listOf(region(1, "Grass", 0, 0), region(2, "Grass", 16, 0))
        val tiles = parse(writeTileset(sheet, 16, 16, regions), regions).palette.tiles
        assertEquals(listOf("Grass", "Grass"), tiles.map { it.name })
        assertNotEquals(tiles[0].id, tiles[1].id)
    }

    @Test fun writerRejectsInvalidInput() {
        assertFailsWith<IllegalArgumentException> { writeTileset("not-a-uuid", 16, 16, emptyList()) }
        assertFailsWith<IllegalArgumentException> { writeTileset(sheet, 0, 16, emptyList()) }
        val regions = listOf(region(1, "A", 0, 0), region(2, "B", 16, 0))
        assertFailsWith<IllegalArgumentException> { writeTileset(sheet, 16, 16, regions) { "00000001-1111-4111-8111-111111111111" } }
    }

    @Test fun parserReportsAMissingRegion() {
        val regions = listOf(region(1, "Grass", 0, 0))
        val failure = assertFailsWith<IllegalArgumentException> { parse(writeTileset(sheet, 16, 16, regions), emptyList()) }
        assertTrue(failure.message!!.contains("missing region"))
    }

    @Test fun versionTwoPreservesGridAndEditableTileMetadata() {
        val regions = listOf(region(1, "Grass", 1, 2))
        val bytes = writeTileset(sheet, 16, 16, regions, grid = TilesetGrid(1, 2, 3, 4, 5))
        val written = Json.parseToJsonElement(bytes.decodeToString()).jsonObject
        assertEquals(JsonPrimitive(2), written["version"])
        assertEquals(Json.parseToJsonElement("""{"margin":[1,2],"spacing":[3,4],"columns":5}"""), written["grid"])
        val metadata = Json.parseToJsonElement(
            """{"tags":["grass","outdoors"],"properties":{"friction":0.5,"floor":true,"material":"soil"},"animation":null}"""
        ).jsonObject
        val source = withTileFields(written, metadata)

        val parsed = parse(source.toString().encodeToByteArray(), regions)

        assertEquals(TilesetGrid(1, 2, 3, 4, 5), parsed.grid)
        assertEquals(source, parsed.source)
    }

    @Test fun parserRejectsMalformedGridAndUnsupportedVersion() {
        val regions = listOf(region(1, "Grass", 0, 0))
        val written = Json.parseToJsonElement(writeTileset(sheet, 16, 16, regions, grid =
            TilesetGrid(0, 0, 0, 0, 1)).decodeToString()).jsonObject
        val malformed = listOf(
            JsonObject(written - "grid"),
            JsonObject(written + ("version" to JsonPrimitive(3))),
            JsonObject(written + ("version" to JsonPrimitive("2"))),
            JsonObject(written + ("tileWidth" to JsonPrimitive("16"))),
        ) + listOf(
            """{"margin":[-1,0],"spacing":[0,0],"columns":1}""",
            """{"margin":[0,0],"spacing":[0],"columns":1}""",
            """{"margin":[0,"0"],"spacing":[0,0],"columns":1}""",
            """{"margin":[0,0],"spacing":[0,0],"columns":0}""",
            """{"margin":[0,0],"spacing":[0,0],"columns":2147483648}""",
            """{"margin":[0,0],"spacing":[0,0],"columns":1,"rows":1}""",
        ).map { JsonObject(written + ("grid" to Json.parseToJsonElement(it))) }

        for (source in malformed) assertFailsWith<IllegalArgumentException>(source.toString()) {
            parse(source.toString().encodeToByteArray(), regions)
        }
    }

    @Test fun parserRejectsMalformedMetadataAndVersionOneMetadata() {
        val regions = listOf(region(1, "Grass", 0, 0))
        val grid = TilesetGrid(0, 0, 0, 0, 1)
        val versionTwo = Json.parseToJsonElement(writeTileset(sheet, 16, 16, regions, grid = grid).decodeToString()).jsonObject
        val malformed = listOf(
            """{"tags":["grass","grass"]}""",
            """{"tags":[" "]}""",
            """{"tags":[5]}""",
            """{"tags":["${"é".repeat(129)}"]}""",
            """{"properties":{" ":"soil"}}""",
            """{"properties":{"material":["soil"]}}""",
            """{"properties":{"material":null}}""",
            """{"properties":{"material":1e1000}}""",
            """{"properties":{"material":"${"é".repeat(2049)}"}}""",
            """{"animation":{}}""",
            """{"extra":0}""",
        ).map { Json.parseToJsonElement(it).jsonObject } + listOf(
            buildJsonObject { put("tags", JsonArray(List(257) { JsonPrimitive("tag$it") })) },
            buildJsonObject { put("properties", JsonObject((0..256).associate { "key$it" to JsonPrimitive(it) })) },
        )
        for (fields in malformed) assertFailsWith<IllegalArgumentException>(fields.toString()) {
            parse(withTileFields(versionTwo, fields).toString().encodeToByteArray(), regions)
        }
        val versionOne = Json.parseToJsonElement(writeTileset(sheet, 16, 16, regions).decodeToString()).jsonObject
        assertFailsWith<IllegalArgumentException> {
            parse(withTileFields(versionOne, buildJsonObject { put("animation", JsonNull) }).toString().encodeToByteArray(), regions)
        }
    }

    private fun withTileFields(document: JsonObject, fields: JsonObject): JsonObject {
        val tile = document.getValue("tiles").jsonArray.single().jsonObject
        return JsonObject(document + ("tiles" to JsonArray(listOf(JsonObject(tile + fields)))))
    }
}
