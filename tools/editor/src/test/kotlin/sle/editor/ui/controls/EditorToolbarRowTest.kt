package sle.editor.ui.controls

import kotlin.test.*

class EditorToolbarRowTest {
    // Groups: run, play, tools (start) and view (end).
    private val widths = listOf(80, 200, 220, 120)
    private val end = listOf(false, false, false, true)

    @Test fun everythingFitsWithNoOverflow() {
        assertEquals(emptySet(), toolbarOverflow(widths, end, available = 80 + 200 + 220 + 120 + 3 * 4, gap = 4, overflow = 30))
    }

    @Test fun viewOptionsOverflowBeforeTools() {
        assertEquals(setOf(3), toolbarOverflow(widths, end, available = 560, gap = 4, overflow = 30))
    }

    @Test fun startGroupsOverflowFromTheRight() {
        assertEquals(setOf(3, 2), toolbarOverflow(widths, end, available = 330, gap = 4, overflow = 30))
        assertEquals(setOf(3, 2, 1, 0), toolbarOverflow(widths, end, available = 40, gap = 4, overflow = 30))
    }

    @Test fun theOverflowButtonCountsAgainstTheSpace() {
        // 80+200+220 plus two gaps is 508; with the ⋯ button and its gap it needs 542.
        assertEquals(setOf(3), toolbarOverflow(widths, end, available = 542, gap = 4, overflow = 30))
        assertEquals(setOf(3, 2), toolbarOverflow(widths, end, available = 541, gap = 4, overflow = 30))
    }
}
