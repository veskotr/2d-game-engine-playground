package sle.editor.model

import kotlinx.serialization.json.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class BehaviourParametersTest {
    @Test
    fun overrideResetAndUndoUseAttachmentIdentity() {
        val state = EntityWorkspaceState(emptyMap())
        val script = Json.parseToJsonElement(
            """{"behaviours":[{"key":"first","scriptAsset":"move.lua"},{"key":"second","scriptAsset":"move.lua"}]}"""
        ).jsonObject
        state.applyStructure(EntityDocument(components = JsonObject(mapOf("Script" to script))))
        val original = state.document
        assertNull(patchBehaviourParameter(original, "second", "old.lua", "speed", JsonPrimitive(7)))
        val edited = patchBehaviourParameter(original, "second", "move.lua", "speed", JsonPrimitive(7))!!
        state.publishPropertyCandidate(edited, "Set speed")
        val attachments = state.document.components.getValue("Script").jsonObject.behaviourEntries()
        assertNull(attachments.first()["parameters"])
        assertEquals(JsonPrimitive(7), attachments.last()["parameters"]?.jsonObject?.get("speed"))
        state.undo()
        assertEquals(original, state.document)
        state.redo()
        assertEquals(edited, state.document)
        assertEquals(original, patchBehaviourParameter(edited, "second", "move.lua", "speed", null))
        val number = state.behaviourNumberEdit("second", "move.lua", "speed", JsonPrimitive(3), "Speed")
        number.focus()
        number.input("9")
        state.endGesture() // Save and Play both finish document gestures.
        val afterNumericEdit = state.document.components.getValue("Script").jsonObject.behaviourEntries().last()
        assertEquals(JsonPrimitive(9), afterNumericEdit["parameters"]?.jsonObject?.get("speed"))
        state.undo()
        assertEquals(edited, state.document)
        assertFalse(compatibleParameter(JsonPrimitive("7"), JsonPrimitive(3)))
    }
}
