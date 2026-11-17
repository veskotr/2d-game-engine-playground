package sle.editor.viewport.experimental

/** Single-thread-confined latest value. A resize/disposal invalidates outstanding work. */
internal class LatestFrame<T> {
    var latest: T? = null
        private set
    private var generation = 0L
    private var enabled = false
    private var closed = false

    fun resize(width: Int, height: Int): Long {
        check(!closed)
        latest = null
        enabled = width > 0 && height > 0
        return ++generation
    }

    fun publish(expectedGeneration: Long, frame: T): Boolean {
        if (closed || !enabled || generation != expectedGeneration) return false
        latest = frame
        return true
    }

    fun close() {
        closed = true
        enabled = false
        latest = null
        generation++
    }
}
