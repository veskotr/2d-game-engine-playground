package sle.editor.viewport.experimental

import kotlin.test.*

class LatestFrameTest {
    @Test fun replacementBoundsStorageAndRejectsOldSize() {
        val slot = LatestFrame<String>()
        val generation = slot.resize(1280, 720)
        repeat(1000) { assertTrue(slot.publish(generation, "frame $it")) }
        assertEquals("frame 999", slot.latest)
        slot.resize(0, 0)
        assertNull(slot.latest)
        assertFalse(slot.publish(generation, "stale"))
        val next = slot.resize(640, 480)
        assertFalse(slot.publish(generation, "old size"))
        assertTrue(slot.publish(next, "new size"))
        slot.close()
        assertNull(slot.latest)
        assertFalse(slot.publish(next, "disposed"))
        assertFailsWith<IllegalStateException> { slot.resize(1280, 720) }
    }
}
