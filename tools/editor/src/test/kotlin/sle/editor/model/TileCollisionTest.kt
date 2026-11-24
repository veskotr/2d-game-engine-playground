package sle.editor.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TileCollisionTest {
    @Test fun transparentTileProducesNoFixtures() {
        assertTrue(generateTileCollision(3, 2, IntArray(6)).isEmpty())
    }

    @Test fun opaqueTileProducesOneFixture() {
        val pieces = generateTileCollision(3, 2, IntArray(6) { 255 })
        assertEquals(1, pieces.size)
        assertEquals(6.0, pieces.sumOf(::area))
        assertEquals(setOf(TileCollisionPoint(0.0, 0.0), TileCollisionPoint(3.0, 0.0),
            TileCollisionPoint(3.0, 2.0), TileCollisionPoint(0.0, 2.0)), pieces.single().toSet())
    }

    @Test fun stairPreservesPixelBoundaries() {
        checkMask("#...", "##..", "###.", "####")
    }

    @Test fun ringPreservesTransparentHole() {
        checkMask("#####", "#...#", "#...#", "#...#", "#####")
    }

    @Test fun diagonalContactProducesSeparateFixtures() {
        val pieces = checkMask("#.", ".#")
        assertEquals(2, pieces.size)
        assertEquals(1, pieces[0].toSet().intersect(pieces[1].toSet()).size)
    }

    @Test fun separatedIslandsAreDeterministic() {
        val rows = arrayOf("##..#", "##..#", ".....", ".###.")
        val pieces = checkMask(*rows)
        assertEquals(3, pieces.size)
        assertEquals(pieces, checkMask(*rows))
    }

    @Test fun thresholdIncludes128AndExcludes127() {
        val alpha = intArrayOf(0, 64, 127, 128, 192, 255)
        val pieces = generateTileCollision(6, 1, alpha)
        assertEquals(listOf(false, false, false, true, true, true),
            alpha.indices.map { x -> pieces.any { contains(it, x + 0.5, 0.5) } })
        assertEquals(2.0, generateTileCollision(6, 1, alpha, threshold = 192).sumOf(::area))
    }

    @Test fun simplifiesStairWithinToleranceAndKeepsRingHole() {
        val stair = intArrayOf(255, 0, 0, 255, 255, 0, 255, 255, 255)
        assertEquals(1, generateTileCollision(3, 3, stair, tolerance = 1.0).size)
        val ring = intArrayOf(255, 255, 255, 255, 0, 255, 255, 255, 255)
        assertEquals(8.0, generateTileCollision(3, 3, ring, tolerance = 2.0).sumOf(::area))
    }

    @Test fun appliesTheFixtureLimitAfterSimplification() {
        val height = 5000
        val strip = IntArray(3 * height) { i -> if (i % 3 < 2 || i / 3 % 2 == 0) 255 else 0 }
        val pieces = generateTileCollision(3, height, strip, tolerance = 1.0)
        assertEquals(1, pieces.size)
        validateTileCollision(pieces, 3, height)
    }

    @Test fun validatesConvexityAndBox2dSize() {
        val square = generateTileCollision(1, 1, intArrayOf(255))
        validateTileCollision(square, 1, 1, 50.0)
        kotlin.test.assertFailsWith<IllegalArgumentException> {
            validateTileCollision(square, 1, 1, 500.0)
        }
        kotlin.test.assertFailsWith<IllegalArgumentException> {
            validateTileCollision(listOf(square.single().reversed()), 1, 1, 50.0)
        }
    }

    private fun checkMask(vararg rows: String): List<List<TileCollisionPoint>> {
        val width = rows.first().length
        val pixels = rows.joinToString("").map { if (it == '#') 255 else 0 }.toIntArray()
        val pieces = generateTileCollision(width, rows.size, pixels)
        for (piece in pieces) {
            assertTrue(piece.size in 3..8)
            assertTrue(piece.indices.all { i ->
                val a = piece[i]
                val b = piece[(i + 1) % piece.size]
                val c = piece[(i + 2) % piece.size]
                cross(a, b, c.x, c.y) > 0
            })
        }
        rows.forEachIndexed { y, row ->
            row.forEachIndexed { x, value ->
                val expectedCount = if (value == '#') 1 else 0
                assertEquals(expectedCount, pieces.count { contains(it, x + 0.5, rows.size - y - 0.5) },
                    "fixture coverage at source pixel ($x, $y)")
            }
        }
        assertEquals(pixels.count { it == 255 }.toDouble(), pieces.sumOf(::area))
        return pieces
    }

    private fun area(piece: List<TileCollisionPoint>): Double =
        piece.indices.sumOf { i ->
            val a = piece[i]
            val b = piece[(i + 1) % piece.size]
            a.x * b.y - a.y * b.x
        } / 2

    private fun contains(piece: List<TileCollisionPoint>, x: Double, y: Double): Boolean =
        piece.indices.all { i -> cross(piece[i], piece[(i + 1) % piece.size], x, y) >= 0 }

    private fun cross(a: TileCollisionPoint, b: TileCollisionPoint, x: Double, y: Double): Double =
        (b.x - a.x) * (y - a.y) - (b.y - a.y) * (x - a.x)
}
