package sle.editor.model

import kotlinx.serialization.json.*
import kotlin.math.abs

/** Explicit collision geometry includes own visual scale; physics supplies ancestor scale. */
internal fun EntityDocument.fitShapeToSprite(type: String): EntityDocument? {
    if (type !in setOf("BoxCollider", "BoxZone", "CircleCollider", "CircleZone")) return null
    val sprite = components["SpriteRenderer"] as? JsonObject ?: return null
    val shape = components[type] as? JsonObject ?: return null
    val notice = "Cannot Fit $type: sprite size, offset and Transform scale must produce finite, nonzero dimensions. Correct the sprite or scale and retry."
    fun number(value: JsonElement?): Double {
        val token = value as? JsonPrimitive
        require(token != null && !token.isString && token.doubleOrNull?.isFinite() == true) { notice }
        return token.double
    }
    fun axis(value: JsonElement?, key: String) = number((value as? JsonObject)?.get(key))
    fun finite(value: Double): Double {
        require(value.isFinite() && value.toFloat().isFinite()) { notice }
        return value
    }
    val transform = components["Transform"] as? JsonObject
    val sx = if (transform == null) 1.0 else axis(transform["scale"], "x")
    val sy = if (transform == null) 1.0 else axis(transform["scale"], "y")
    val width = finite(abs(axis(sprite["size"], "x") * sx))
    val height = finite(abs(axis(sprite["size"], "y") * sy))
    require(width.toFloat() > 0 && height.toFloat() > 0) { notice }
    val offset = sprite["offset"]
    val ox = finite((if (offset == null) 0.0 else axis(offset, "x")) * sx)
    val oy = finite((if (offset == null) 0.0 else axis(offset, "y")) * sy)
    fun pair(x: Double, y: Double) = buildJsonObject { put("x", x); put("y", y) }
    val dimensions = if (type.startsWith("Circle")) {
        val radius = maxOf(width, height) / 2
        require(radius.toFloat() > 0) { notice }
        "radius" to JsonPrimitive(radius)
    } else "size" to pair(width, height)
    require(dimensions.first in shape && "offset" in shape) { notice }
    val fitted = JsonObject(shape + dimensions + ("offset" to pair(ox, oy)))
    return copy(components = JsonObject(components + (type to fitted)))
}
