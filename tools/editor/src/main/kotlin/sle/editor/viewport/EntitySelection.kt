package sle.editor.viewport

import sle.editor.model.EntityDocument

import kotlinx.serialization.json.*

/** An axis-aligned rectangle, in world units or screen pixels. */
internal data class Bounds(val min: Vec2, val max: Vec2) {
    val center get() = (min+max)*0.5
    val size get() = max-min
    fun intersects(other: Bounds) = min.x <= other.max.x && max.x >= other.min.x && min.y <= other.max.y && max.y >= other.min.y
    fun union(other: Bounds) = Bounds(Vec2(minOf(min.x,other.min.x),minOf(min.y,other.min.y)),Vec2(maxOf(max.x,other.max.x),maxOf(max.y,other.max.y)))
    companion object {
        fun of(points: List<Vec2>): Bounds? = if(points.isEmpty()) null
            else Bounds(Vec2(points.minOf { it.x },points.minOf { it.y }),Vec2(points.maxOf { it.x },points.maxOf { it.y }))
    }
}

/** World-space points of an entity's visible geometry: its position, camera view, and enabled shapes (rotated corners). */
internal fun entityWorldPoints(document: EntityDocument): List<Vec2> {
    val points=mutableListOf<Vec2>()
    document.tilemapWorldBounds?.let { points.add(it.min); points.add(it.max) }
    points.addAll(cameraOutline(document))
    GizmoShape.from(document,"Entity")?.let { points.add(it.position) }
    for(target in GizmoShape.available(document).filter { it!="Entity" }) {
        val fields=document.components.getValue(target).jsonObject
        if(fields["enabled"]?.jsonPrimitive?.booleanOrNull==false || fields["visible"]?.jsonPrimitive?.booleanOrNull==false) continue
        val shape=GizmoShape.from(document,target) ?: continue
        points.addAll(shape.outline())
    }
    return points.filter { it.x.isFinite() && it.y.isFinite() }
}

/** World-space selection bounds of an entity; null without a Transform or geometry. */
internal fun entityWorldBounds(document: EntityDocument): Bounds? = Bounds.of(entityWorldPoints(document))

/** Screen-space selection bounds (no padding): what a marquee must intersect to select the entity. */
internal fun entityScreenBounds(document: EntityDocument,camera: PreviewCamera): Bounds? = Bounds.of(entityWorldPoints(document).map(camera::screen))

/** Screen-space outline encloses visible geometry, with no component resize handles. */
internal fun entitySelectionBounds(document: EntityDocument,camera: PreviewCamera): List<Vec2> {
    val bounds=entityScreenBounds(document,camera) ?: return emptyList()
    val left=bounds.min.x-6;val right=bounds.max.x+6
    val top=bounds.min.y-6;val bottom=bounds.max.y+6
    return listOf(Vec2(left,top),Vec2(right,top),Vec2(right,bottom),Vec2(left,bottom),Vec2(left,top))
}

/**
 * Frame Selected: the zoom factor (applied to the view size around its centre) and the new view
 * centre that fit [bounds] with a margin while keeping the view's aspect. Null for an empty view.
 * Tiny selections (a bare Transform) still get [minimumHeight] world units of view.
 */
internal fun frameView(viewSize: Vec2, bounds: Bounds, margin: Double = 1.3, minimumHeight: Double = 4.0): Pair<Double, Vec2>? {
    if(!(viewSize.x > 0 && viewSize.y > 0 && viewSize.x.isFinite() && viewSize.y.isFinite())) return null
    val aspect = viewSize.x / viewSize.y
    val height = maxOf(bounds.size.y, bounds.size.x / aspect) * margin
    val target = if(height.isFinite()) height.coerceIn(minimumHeight, 1.0e6) else return null
    val center = bounds.center.takeIf { it.x.isFinite() && it.y.isFinite() } ?: return null
    return target / viewSize.y to center
}
