package sle.editor.viewport

import kotlin.test.*
import org.junit.Test

class EditorEngineSessionTest {
    private class Backend : SessionBackend {
        var advances = 0
        var draws = 0
        var closes = 0
        var released = 0
        var inputs = 0
        var fail = false
        override fun advance(dt: Float) { if (fail) error("owner failed"); advances++ }
        override fun draw(view: ViewId, request: ViewRequest, sequence: Long): ViewFrame {
            draws++
            return ViewFrame(view, sequence, request, 7, null, emptyList())
        }
        override fun input(event: ViewInput) { inputs++ }
        override fun releaseInput() { released++ }
        override fun close() { closes++ }
    }
    private fun request(generation: Long = 1) = ViewRequest(640, 360, 640, 360, true, generation)

    @Test fun twoViewsAdvanceOnceAndUnsubscribeDoesNotDestroyOwner() {
        val backend = Backend(); val session = SessionFrames(backend)
        session.setViewRequest(ViewId.Scene, request())
        session.setViewRequest(ViewId.Game, request())
        session.tick(.016f)
        assertEquals(1, backend.advances); assertEquals(2, backend.draws)
        assertEquals(1L, session.latestFrame(ViewId.Scene)?.sequence)
        session.setViewRequest(ViewId.Scene, null)
        session.setViewRequest(ViewId.Game, null)
        repeat(20) { session.tick(.016f) }
        assertEquals(21, backend.advances); assertEquals(2, backend.draws)
        assertEquals(0, backend.closes)
        assertNull(session.latestFrame(ViewId.Scene))
        session.close(); session.close()
        assertEquals(1, backend.closes)
    }
    @Test fun resizeRejectsOldInputAndRetainsOnlyLatestFrame() {
        val backend = Backend(); val session = SessionFrames(backend)
        session.setViewRequest(ViewId.Game, request())
        repeat(1000) { session.tick(.016f) }
        assertEquals(1000L, session.latestFrame(ViewId.Game)?.sequence)
        session.setViewRequest(ViewId.Game, request(2))
        assertNull(session.latestFrame(ViewId.Game))
        session.enqueueInput(ViewInput(ViewId.Game, 1, 0, 65, 1.0, 0.0))
        session.enqueueInput(ViewInput(ViewId.Scene, 2, 0, 65, 1.0, 0.0))
        assertEquals(0, backend.inputs)
        session.enqueueInput(ViewInput(ViewId.Game, 2, 0, 65, 1.0, 0.0))
        assertEquals(1, backend.inputs)
        session.setViewRequest(ViewId.Game, request(3).copy(visible = false))
        session.tick(.016f)
        assertNull(session.latestFrame(ViewId.Game))
        assertEquals(1001, backend.advances)
        assertTrue(backend.released > 0)
    }
    @Test fun resizeBeforeNextOwnerTickCannotUsePreviousFrameForInput() {
        val backend=Backend();val session=SessionFrames(backend)
        session.setViewRequest(ViewId.Game,request())
        session.tick(.016f)
        assertNotNull(session.frameForInput(ViewId.Game,request()))
        // The AWT resize event precedes the next owner tick.
        assertNull(session.frameForInput(ViewId.Game,request(2).copy(pixelWidth=800,logicalWidth=800)))
        session.enqueueInput(ViewInput(ViewId.Game,1,1,0,1.0))
        assertEquals(0,backend.inputs)
        session.tick(.016f)
        assertNotNull(session.frameForInput(ViewId.Game,request(2).copy(pixelWidth=800,logicalWidth=800)))
    }
    @Test fun ownerFailureStopsFutureTicksAndCloseStillCleansUpOnce() {
        val backend = Backend(); val session = SessionFrames(backend)
        backend.fail = true
        assertFailsWith<IllegalStateException> { session.tick(.016f) }
        backend.fail = false
        session.tick(.016f)
        assertEquals(0, backend.advances)
        session.close(); session.close()
        assertEquals(1, backend.closes)
    }
}
