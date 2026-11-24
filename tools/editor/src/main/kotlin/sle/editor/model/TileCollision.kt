package sle.editor.model

import java.awt.geom.Line2D

internal data class TileCollisionPoint(val x: Double, val y: Double)

private data class PixelSpan(val left: Int, val right: Int)

/**
 * Partition a thresholded mask into rectangles by coalescing identical runs
 * on consecutive rows. Input is top-down, output is counterclockwise in
 * lower-left tile-local pixels.
 */
internal fun generateTileCollision(
    width: Int,
    height: Int,
    alpha: IntArray,
    threshold: Int = 128,
    tolerance: Double = 0.0
): List<List<TileCollisionPoint>> {
    require(width > 0 && height > 0 && width.toLong() * height == alpha.size.toLong())
    require(threshold in 0..255 && alpha.all { it in 0..255 })

    require(tolerance.isFinite() && tolerance >= 0) { "Simplification must be nonnegative" }

    val pieces = mutableListOf<List<TileCollisionPoint>>()
    var active = linkedMapOf<PixelSpan, Int>()
    fun finish(span: PixelSpan, firstRow: Int, pastLastRow: Int) {
        val left = span.left.toDouble()
        val right = span.right.toDouble()
        val bottom = (height - pastLastRow).toDouble()
        val top = (height - firstRow).toDouble()
        pieces.add(listOf(TileCollisionPoint(left, bottom), TileCollisionPoint(right, bottom),
            TileCollisionPoint(right, top), TileCollisionPoint(left, top)))
    }

    for (row in 0 until height) {
        val next = linkedMapOf<PixelSpan, Int>()
        val offset = row * width
        var x = 0
        while (x < width) {
            while (x < width && alpha[offset + x] < threshold) x++
            val start = x
            while (x < width && alpha[offset + x] >= threshold) x++
            if (start < x) {
                val span = PixelSpan(start, x)
                next[span] = active.remove(span) ?: row
            }
        }
        active.forEach { (span, firstRow) -> finish(span, firstRow, row) }
        active = next
    }
    active.forEach { (span, firstRow) -> finish(span, firstRow, height) }
    if (tolerance == 0.0 || pieces.size < 2) return pieces
    val contour = traceSimpleTileOutline(width, height, alpha, threshold) ?: return pieces
    return simplifyTileOutline(contour, tolerance) ?: pieces
}

internal const val TILE_PIXELS_PER_METER = 50.0

internal fun validateTileCollision(
    pieces: List<List<TileCollisionPoint>>,
    width: Int,
    height: Int,
    pixelsPerMeter: Double = TILE_PIXELS_PER_METER,
) {
    require(pixelsPerMeter.isFinite() && pixelsPerMeter > 0) { "Invalid scale" }
    validateTileCollisionPixels(pieces, width, height)
    val epsilon = Math.ulp(1f).toDouble()
    for ((index, piece) in pieces.withIndex()) {
        val label = "Fixture ${index + 1}"
        val native = piece.map {
            TileCollisionPoint((it.x.toFloat() / pixelsPerMeter.toFloat()).toDouble(),
                (it.y.toFloat() / pixelsPerMeter.toFloat()).toDouble())
        }
        for ((i, a) in native.withIndex()) {
            for (b in native.take(i)) {
                require(kotlin.math.hypot(a.x - b.x, a.y - b.y) > 0.0025) {
                    "$label has vertices too close together at this scale"
                }
            }
            val b = native[(i + 1) % native.size]
            for (j in native.indices) {
                if (j == i || j == (i + 1) % native.size) continue
                require(turn(a, b, native[j]) > epsilon) {
                    "$label must be strictly convex and counterclockwise at this scale"
                }
            }
        }
        val twiceArea = native.indices.drop(1).dropLast(1).sumOf { i ->
            turn(native.first(), native[i], native[i + 1])
        }
        require(twiceArea.isFinite() && twiceArea > 2 * epsilon) { "$label is too small at this scale" }
    }
}

internal fun validateTileCollisionPixels(pieces: List<List<TileCollisionPoint>>, width: Int, height: Int) {
    require(pieces.size <= 4096) { "A tile can contain at most 4096 fixtures" }
    for ((index, piece) in pieces.withIndex()) {
        val label = "Fixture ${index + 1}"
        require(piece.size in 3..8) { "$label needs 3 to 8 vertices" }
        require(piece.all { it.x in 0.0..width.toDouble() && it.y in 0.0..height.toDouble() }) {
            "$label has a vertex outside the tile"
        }
        for (i in piece.indices) {
            val next = (i + 1) % piece.size
            require(piece.indices.all { it == i || it == next || turn(piece[i], piece[next], piece[it]) > 0 }) {
                "$label must be strictly convex and counterclockwise"
            }
        }
    }
}

private fun turn(a: TileCollisionPoint, b: TileCollisionPoint, c: TileCollisionPoint): Double =
    (b.x - a.x) * (c.y - a.y) - (b.y - a.y) * (c.x - a.x)

/** Trace exposed edges in counterclockwise order. Separate contours and corner contacts retain exact coverage. */
private fun traceSimpleTileOutline(width: Int, height: Int, alpha: IntArray, threshold: Int): List<TileCollisionPoint>? {
    val edges = linkedMapOf<TileCollisionPoint, TileCollisionPoint>()
    fun edge(x1: Int, y1: Int, x2: Int, y2: Int): Boolean =
        edges.put(TileCollisionPoint(x1.toDouble(), y1.toDouble()),
            TileCollisionPoint(x2.toDouble(), y2.toDouble())) == null

    for (row in 0 until height) {
        val y = height - row - 1
        for (x in 0 until width) {
            val i = row * width + x
            if (alpha[i] < threshold) continue
            if (row == height - 1 || alpha[i + width] < threshold)
                if (!edge(x, y, x + 1, y)) return null
            if (x == width - 1 || alpha[i + 1] < threshold)
                if (!edge(x + 1, y, x + 1, y + 1)) return null
            if (row == 0 || alpha[i - width] < threshold)
                if (!edge(x + 1, y + 1, x, y + 1)) return null
            if (x == 0 || alpha[i - 1] < threshold)
                if (!edge(x, y + 1, x, y)) return null
            if (edges.size > 262144) return null
        }
    }
    val start = edges.keys.firstOrNull() ?: return null
    val contour = mutableListOf<TileCollisionPoint>()
    var point = start
    do {
        contour.add(point)
        point = edges.remove(point) ?: return null
    } while (point != start)
    return contour.takeIf { edges.isEmpty() }
}

/** Replace a single simple outline with its hull only when every shortcut is within tolerance. */
private fun simplifyTileOutline(contour: List<TileCollisionPoint>, tolerance: Double): List<List<TileCollisionPoint>>? {
    val sorted = contour.sortedWith(compareBy({ it.x }, { it.y }))
    fun half(points: List<TileCollisionPoint>): List<TileCollisionPoint> {
        val chain = mutableListOf<TileCollisionPoint>()
        for (point in points) {
            while (chain.size >= 2 && turn(chain[chain.lastIndex - 1], chain.last(), point) <= 0)
                chain.removeLast()
            chain.add(point)
        }
        return chain.dropLast(1)
    }
    val hull = half(sorted) + half(sorted.asReversed())
    if (hull.size !in 3..8) return null

    val hullVertices = hull.toSet()
    val start = contour.indexOfFirst { it in hullVertices }
    val cyclic = contour.drop(start) + contour.take(start) + contour[start]
    var previous = 0
    for (index in 1 until cyclic.size) {
        val end = cyclic[index]
        if (end !in hullVertices) continue
        val begin = cyclic[previous]
        for (point in cyclic.subList(previous + 1, index)) {
            if (Line2D.ptSegDist(begin.x, begin.y, end.x, end.y, point.x, point.y) > tolerance) return null
        }
        previous = index
    }
    return listOf(hull)
}
