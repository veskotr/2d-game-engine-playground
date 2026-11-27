package sle.editor.project.assets

import kotlinx.serialization.json.*
import sle.editor.model.*
import java.nio.file.Files
import java.nio.file.Path

internal enum class TileCollisionMode(val label: String, val wireName: String) {
    None("None", "none"), FullCell("Full Cell", "fullCell"), Custom("Custom", "custom")
}

internal data class TileCollisionShape(
    val mode: TileCollisionMode,
    val pieces: List<List<TileCollisionPoint>> = emptyList(),
)

internal fun readTileCollision(tile: JsonObject, width: Int = Int.MAX_VALUE, height: Int = Int.MAX_VALUE): TileCollisionShape {
    val collision = tile["collision"]?.jsonObject ?: return TileCollisionShape(TileCollisionMode.None)
    require(collision.keys == setOf("mode", "pieces")) { "Invalid tile collision fields" }
    val mode = TileCollisionMode.entries.firstOrNull { it.wireName == collision.getValue("mode").jsonPrimitive.content }
        ?: error("Unknown tile collision mode")
    val polygons = collision.getValue("pieces").jsonArray
    require(polygons.size <= 4096) { "A tile can contain at most 4096 fixtures" }
    val pieces = polygons.map { polygon ->
        require(polygon.jsonArray.size in 3..8) { "A fixture needs 3 to 8 vertices" }
        polygon.jsonArray.map { vertex ->
            val pair = vertex.jsonArray
            require(pair.size == 2) { "Expected a collision vertex pair" }
            val x = pair[0].jsonPrimitive
            val y = pair[1].jsonPrimitive
            require(!x.isString && !y.isString) { "Expected numeric collision coordinates" }
            TileCollisionPoint(x.double, y.double)
        }
    }
    require(mode == TileCollisionMode.Custom || pieces.isEmpty()) { "Only Custom can store polygons" }
    validateTileCollisionPixels(pieces, width, height)
    return TileCollisionShape(mode, pieces)
}

internal fun TileCollisionShape.json(): JsonObject = buildJsonObject {
    put("mode", mode.wireName)
    putJsonArray("pieces") {
        for (piece in pieces) addJsonArray {
            for (vertex in piece) addJsonArray {
                add(JsonPrimitive(vertex.x))
                add(JsonPrimitive(vertex.y))
            }
        }
    }
}

internal class TilesetCollisionSession(private val file: Path, source: String) {
    private var saved = source
    private val past = ArrayDeque<String>()
    private val future = ArrayDeque<String>()
    val document get() = Json.parseToJsonElement(saved).jsonObject
    val canUndo get() = past.isNotEmpty()
    val canRedo get() = future.isNotEmpty()

    fun shape(id: String): TileCollisionShape = readTileCollision(document.getValue("tiles").jsonArray
        .map { it.jsonObject }.first { it.getValue("id").jsonPrimitive.content == id })

    fun apply(updates: Map<String, TileCollisionShape>) {
        val source = document
        val remaining = updates.toMutableMap()
        val tiles = source.getValue("tiles").jsonArray.map { element ->
            val tile = element.jsonObject
            val shape = remaining.remove(tile.getValue("id").jsonPrimitive.content)
            if (shape == null || shape == readTileCollision(tile)) tile
            else JsonObject(tile + ("collision" to shape.json()))
        }
        require(remaining.isEmpty()) { "A selected tile was removed" }
        val next = JsonObject(source + ("tiles" to JsonArray(tiles)))
        if (next == source) return
        val previous = saved
        write(next.toString())
        past.addLast(previous)
        future.clear()
    }

    fun undo() = restore(past, future)
    fun redo() = restore(future, past)

    private fun restore(from: ArrayDeque<String>, to: ArrayDeque<String>) {
        val next = from.lastOrNull() ?: return
        val previous = saved
        write(next)
        from.removeLast()
        to.addLast(previous)
    }

    private fun write(next: String) = synchronized(AssetIdentity) {
        check(Files.readString(file) == saved) { "The tileset changed on disk. Reopen it before saving." }
        val bytes = next.encodeToByteArray()
        require(bytes.size <= 4 * 1024 * 1024) { "Tileset exceeds 4 MiB" }
        atomicAssetWrite(file, bytes, replace = true)
        saved = next
    }
}
