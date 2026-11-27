package sle.editor.project.assets

// A fixed canvas of this size fits Compose's packed constraints in both axes.
private const val MAX_ATLAS_AXIS_PIXELS = 8190

internal fun tilesetAtlasZoom(width: Int, height: Int, density: Float, requested: Float): Float {
    require(width > 0 && height > 0) { "Atlas dimensions must be positive" }
    require(density.isFinite() && density > 0f) { "Display density must be positive and finite" }
    require(requested.isFinite() && requested > 0f) { "Atlas zoom must be positive and finite" }

    val longestAxis = maxOf(width, height).toFloat()
    val maxDp = minOf(MAX_ATLAS_AXIS_PIXELS / density.toDouble(), Float.MAX_VALUE.toDouble())
    val zoom = minOf(requested, (maxDp / longestAxis).toFloat())
    // Match the two float multiplications used by Modifier.size and Density.roundToPx.
    return if (longestAxis * zoom * density > MAX_ATLAS_AXIS_PIXELS) Math.nextDown(zoom) else zoom
}
