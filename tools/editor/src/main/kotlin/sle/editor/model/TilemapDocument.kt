package sle.editor.model

import kotlinx.serialization.json.*
import sle.editor.viewport.Vec2
import java.util.UUID

private const val MAX_TILEMAP_BYTES = 16 * 1024 * 1024
private fun mapFields(value: JsonElement?, vararg fields: String): JsonObject {
    require(value is JsonObject && value.keys == fields.toSet()) { "Unsupported or missing tilemap fields" }
    return value
}
private fun mapInteger(value: JsonElement?, min: Int = Int.MIN_VALUE, max: Int = Int.MAX_VALUE): Int {
    val primitive = value as? JsonPrimitive
    val number = primitive?.takeUnless { it.isString }?.intOrNull
    require(number != null && number in min..max && Regex("-?(0|[1-9][0-9]*)").matches(primitive.content)) { "Invalid tilemap integer" }
    return number
}
private fun mapText(value: JsonElement?): String {
    require(value is JsonPrimitive && value.isString && value.content.isNotBlank() && value.content.encodeToByteArray().size <= 256) { "Invalid tilemap name" }
    return value.content
}
private fun mapUuid(value: JsonElement?): String = mapText(value).also {
    require(runCatching { UUID.fromString(it).toString() == it }.getOrDefault(false)) { "Invalid tilemap UUID" }
}

/** Authored map coordinates are local to the placing entity; layers have no transform. */
internal data class TilemapDocument private constructor(val source: JsonObject) {
    val name get() = source.getValue("name").jsonPrimitive.content
    val grid get() = source.getValue("grid").jsonObject
    val cellWidth get() = grid.getValue("cellWidth").jsonPrimitive.int
    val cellHeight get() = grid.getValue("cellHeight").jsonPrimitive.int
    val pixelsPerUnit get() = grid.getValue("pixelsPerUnit").jsonPrimitive.double
    val tilesets get() = source.getValue("tilesets").jsonArray.associate {
        it.jsonObject.getValue("key").jsonPrimitive.int to it.jsonObject.getValue("asset").jsonPrimitive.content
    }
    fun serialize() = documentJson.encodeToString(JsonElement.serializer(), source) + "\n"

    /** Adapter for existing paint tools. Qualified IDs keep stamps/fill/copy unambiguous. */
    fun projectedLayers(reference: String, translation: Vec2 = Vec2(0.0, 0.0)) = JsonArray(source.getValue("layers").jsonArray.map { raw ->
        val layer = raw.jsonObject
        JsonObject((layer - "collision") + mapOf(
            "tileset" to JsonPrimitive(reference), "origin" to JsonArray(listOf(JsonPrimitive(translation.x), JsonPrimitive(translation.y))),
            "pixelsPerUnit" to JsonPrimitive(pixelsPerUnit), "collisionEnabled" to layer.getValue("collision"),
            "cells" to JsonArray(layer.getValue("cells").jsonArray.map { entry ->
                val cell = entry.jsonObject
                JsonObject((cell - "set") + ("tile" to JsonPrimitive("${cell.getValue("set").jsonPrimitive.int}:${cell.getValue("tile").jsonPrimitive.content}")))
            }),
        ))
    })

    fun withProjectedLayers(layers: JsonArray): TilemapDocument {
        val converted = layers.map { raw ->
        val layer = raw.jsonObject
        buildJsonObject {
            for (field in listOf("id", "name", "drawOrder")) put(field, layer.getValue(field))
            put("visible", layer["visible"] ?: JsonPrimitive(true)); put("locked", layer["locked"] ?: JsonPrimitive(false))
            put("collision", layer["collisionEnabled"] ?: JsonPrimitive(true))
            put("cells", JsonArray(layer.getValue("cells").jsonArray.map { rawCell ->
                val cell = rawCell.jsonObject
                val qualified = cell.getValue("tile").jsonPrimitive.content
                val key = qualified.substringBefore(':').toIntOrNull()
                require(key != null && key in tilesets) { "Tile must name a tileset in this map" }
                JsonObject(cell + mapOf("set" to JsonPrimitive(key), "tile" to JsonPrimitive(qualified.substringAfter(':'))))
            }))
        }
    }
        return parse(JsonObject(source + ("layers" to JsonArray(converted))).toString())
    }

    fun addTileset(reference: String): TilemapDocument {
        if (reference in tilesets.values) return this
        val key = (0..tilesets.size).first { it !in tilesets }
        val sets = source.getValue("tilesets").jsonArray + buildJsonObject { put("key", key); put("asset", reference) }
        return parse(JsonObject(source + ("tilesets" to JsonArray(sets))).toString())
    }

    companion object {
        fun empty(name: String, width: Int = 16, height: Int = 16, ppu: Double = 1.0) = parse(buildJsonObject {
            put("format", "sle-tilemap"); put("version", 1); put("name", name)
            putJsonObject("grid") { put("cellWidth", width); put("cellHeight", height); put("pixelsPerUnit", ppu) }
            putJsonObject("streaming") { put("mode", "always"); put("chunkCells", 32); put("loadRadius", 2); put("unloadRadius", 3) }
            put("tilesets", JsonArray(emptyList())); put("layers", JsonArray(emptyList()))
        }.toString())

        fun parse(text: String): TilemapDocument {
            require(text.encodeToByteArray().size <= MAX_TILEMAP_BYTES) { "Tilemap exceeds 16 MiB" }
            val root = mapFields(Json.parseToJsonElement(text), "format", "version", "name", "grid", "tilesets", "streaming", "layers")
            require(root["format"] == JsonPrimitive("sle-tilemap") && mapInteger(root["version"]) == 1) { "Unsupported tilemap format/version" }
            mapText(root["name"])
            val grid = mapFields(root["grid"], "cellWidth", "cellHeight", "pixelsPerUnit")
            mapInteger(grid["cellWidth"], 1, 65536); mapInteger(grid["cellHeight"], 1, 65536)
            val ppu = grid["pixelsPerUnit"] as? JsonPrimitive
            val number = ppu?.takeUnless { it.isString }?.doubleOrNull
            require(number != null && number.isFinite() && number in 0.001..1.0e6) { "Invalid map pixelsPerUnit" }
            val streaming = mapFields(root["streaming"], "mode", "chunkCells", "loadRadius", "unloadRadius")
            require(streaming["mode"] in listOf(JsonPrimitive("always"), JsonPrimitive("aroundFocus"))) { "Invalid streaming mode" }
            mapInteger(streaming["chunkCells"], 1, 1024)
            val load = mapInteger(streaming["loadRadius"], 0, 1024); mapInteger(streaming["unloadRadius"], load, 1024)
            val sets = root["tilesets"] as? JsonArray
            require(sets != null && sets.size <= 256) { "Invalid tilesets array" }
            val keys = mutableSetOf<Int>()
            sets.forEach {
                val set = mapFields(it, "key", "asset")
                require(keys.add(mapInteger(set["key"], 0))) { "Duplicate tileset key" }
                val ref = mapText(set["asset"])
                require(ref.startsWith("project-asset://")) { "Tileset requires a project asset UUID" }
                mapUuid(JsonPrimitive(ref.removePrefix("project-asset://")))
            }
            val layers = root["layers"] as? JsonArray
            require(layers != null && layers.size <= 4096) { "Invalid layers array" }
            val ids = mutableSetOf<String>(); val orders = mutableSetOf<Int>(); var count = 0
            val canonical = layers.map { raw ->
                val layer = mapFields(raw, "id", "name", "drawOrder", "visible", "locked", "collision", "cells")
                require(ids.add(mapUuid(layer["id"]))) { "Duplicate layer UUID" }; mapText(layer["name"])
                require(orders.add(mapInteger(layer["drawOrder"], 0))) { "Duplicate layer order" }
                for (flag in listOf("visible", "locked", "collision")) require(layer[flag] is JsonPrimitive && layer[flag]?.jsonPrimitive?.isString == false && layer[flag]?.jsonPrimitive?.booleanOrNull != null) { "Invalid layer boolean" }
                val cells = layer["cells"] as? JsonArray
                require(cells != null) { "Expected cells array" }; count += cells.size
                require(count <= 262144) { "Map exceeds 262144 cells" }
                val positions = mutableSetOf<TileCell>()
                cells.forEach {
                    val cell = mapFields(it, "x", "y", "set", "tile")
                    require(positions.add(TileCell(mapInteger(cell["x"]), mapInteger(cell["y"])))) { "Duplicate map cell" }
                    require(mapInteger(cell["set"], 0) in keys) { "Unknown tileset key" }; mapUuid(cell["tile"])
                }
                JsonObject(layer + ("cells" to JsonArray(cells.sortedWith(compareBy({ it.jsonObject.getValue("y").jsonPrimitive.int }, { it.jsonObject.getValue("x").jsonPrimitive.int })))))
            }.sortedBy { it.getValue("drawOrder").jsonPrimitive.int }
            return TilemapDocument(JsonObject(root + mapOf("layers" to JsonArray(canonical), "tilesets" to JsonArray(sets.sortedBy { it.jsonObject.getValue("key").jsonPrimitive.int }))))
        }
    }
}
