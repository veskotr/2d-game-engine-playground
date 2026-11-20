package sle.editor.console

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ConsoleStateTest {
    @Test
    fun coalescesAcrossBatchesAndBoundsHistory() {
        val console = ConsoleState(capacity = 2, persistPreferences = false)
        val error = ConsoleMessage(ConsoleSeverity.ERROR, "broken")
        console.append(listOf(error), 1)
        console.append(listOf(error), 2)
        assertEquals(2, console.rows.single().repeats)
        assertEquals(3L, console.dropped)
        console.append(listOf(error.copy(message = "second"), error.copy(message = "third")), 0)
        assertEquals(listOf("second", "third"), console.rows.map { it.entry.message })
        assertEquals(5L, console.dropped)
        console.clearHistory()
        assertTrue(console.rows.isEmpty())
        assertEquals(0L, console.dropped)
    }
}
