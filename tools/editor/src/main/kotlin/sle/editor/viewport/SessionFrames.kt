package sle.editor.viewport

internal interface SessionBackend : AutoCloseable {
    fun advance(dt: Float)
    fun draw(view: ViewId, request: ViewRequest, sequence: Long): ViewFrame
    fun input(event: ViewInput)
    fun releaseInput()
}

/** EDT-confined scheduler policy, independent of native/GL for regression coverage. */
internal class SessionFrames(private val backend: SessionBackend) : AutoCloseable {
    private val requests = mutableMapOf<ViewId, ViewRequest>()
    private val frames = mutableMapOf<ViewId, ViewFrame>()
    private var sequence = 0L
    private var failed = false
    private var closed = false
    fun setViewRequest(view: ViewId, request: ViewRequest?) {
        if (closed) return
        if (requests[view] == request) return
        if (view == ViewId.Game) backend.releaseInput()
        frames.remove(view)
        if (request == null) requests.remove(view) else requests[view] = request
    }
    fun latestFrame(view: ViewId): ViewFrame? = frames[view]
    fun frameForInput(view: ViewId, current: ViewRequest): ViewFrame? {
        setViewRequest(view,current) // AWT geometry can change between owner ticks.
        return frames[view]?.takeIf { current.drawable && it.request==current }
    }
    fun enqueueInput(event: ViewInput) {
        val request = requests[event.view] ?: return
        if (!closed && !failed && event.view == ViewId.Game && request.drawable && request.generation == event.generation)
            backend.input(event)
    }
    fun releaseInput() { if (!closed) backend.releaseInput() }
    fun tick(dt: Float) {
        if (closed || failed) return
        require(dt.isFinite() && dt >= 0)
        try {
            backend.advance(dt)
            ++sequence
            requests.forEach { (view, request) ->
                if (request.drawable) frames[view] = backend.draw(view, request, sequence)
            }
        } catch (failure: Throwable) {
            failed = true
            frames.clear()
            throw failure
        }
    }
    override fun close() {
        if (closed) return
        closed = true
        frames.clear(); requests.clear()
        backend.close()
    }
}
