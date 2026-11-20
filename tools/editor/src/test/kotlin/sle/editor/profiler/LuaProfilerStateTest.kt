package sle.editor.profiler

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LuaProfilerStateTest {
    @Test
    fun retainsSamplesAndClearsOnSceneChangeOrCaptureStop() {
        val state = LuaProfilerState()
        state.updateCapture(true)
        repeat(150) { index -> state.accept(sample(scene = 1, time = index)) }
        assertEquals(120, state.samples.size)
        assertEquals(30L, state.samples.first().elapsed)
        assertEquals(listOf("fast", "slow"), state.samples.last().rows.map { it.behaviour })
        state.accept(sample(scene = 2, time = 5))
        assertEquals(2L, state.samples.single().scene)
        state.updateCapture(false)
        assertTrue(state.samples.isEmpty())
    }

    private fun sample(scene: Int, time: Int) = """
        {"scene":$scene,"elapsed":$time,"gc":0,"memory":100,"omittedRows":0,"rows":[
          {"entity":1,"behaviour":"slow","source":"","calls":[0,1,0,0],"elapsed":[0,1,0,0],"allocated":0,"freed":0},
          {"entity":2,"behaviour":"fast","source":"","calls":[0,1,0,0],"elapsed":[0,3,0,0],"allocated":0,"freed":0}
        ]}
    """.trimIndent().encodeToByteArray()
}
