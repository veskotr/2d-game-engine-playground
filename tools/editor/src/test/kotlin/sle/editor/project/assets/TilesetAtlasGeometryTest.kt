package sle.editor.project.assets

import androidx.compose.ui.unit.Constraints
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class TilesetAtlasGeometryTest {
    @Test fun largeAtlasesCanUseZoomWithoutExceedingComposeLayoutLimits() {
        checkZoomLimit(2048, 2048, 1f, 16f)
        checkZoomLimit(4096, 4096, 1f, 8f)
        checkZoomLimit(8192, 8192, 2.5f, 4f)
    }

    @Test fun wideAndTallAtlasesAreLimitedByTheirLongerAxis() {
        checkZoomLimit(Int.MAX_VALUE, 64, 1.25f, 2f)
        checkZoomLimit(64, Int.MAX_VALUE, 1.25f, 2f)
    }

    private fun checkZoomLimit(width: Int, height: Int, density: Float, requested: Float) {
        val zoom = tilesetAtlasZoom(width, height, density, requested)
        val bounds = Constraints.fixed((width * zoom * density).roundToInt(), (height * zoom * density).roundToInt())
        assertTrue(zoom > 0f && zoom <= requested)
        assertEquals(8190, maxOf(bounds.maxWidth, bounds.maxHeight))
    }

    @Test fun keepsRequestedZoomWhenBothAxesFit() {
        assertEquals(4f, tilesetAtlasZoom(256, 512, 2f, 4f))
        assertEquals(0.75f, tilesetAtlasZoom(4096, 4096, 1f, 0.75f))
    }

    @Test fun rejectsInvalidDimensionsDensityAndZoom() {
        for (invalid in listOf(0, -1)) {
            assertFailsWith<IllegalArgumentException> { tilesetAtlasZoom(invalid, 64, 1f, 1f) }
            assertFailsWith<IllegalArgumentException> { tilesetAtlasZoom(64, invalid, 1f, 1f) }
        }
        for (invalid in listOf(0f, -1f, Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY)) {
            assertFailsWith<IllegalArgumentException> { tilesetAtlasZoom(64, 64, invalid, 1f) }
            assertFailsWith<IllegalArgumentException> { tilesetAtlasZoom(64, 64, 1f, invalid) }
        }
    }
}
