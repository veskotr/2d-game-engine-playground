package sle.editor.ui

import sle.editor.model.*
import kotlinx.serialization.json.*
import java.nio.file.Path
import kotlin.test.*

class ProjectAssetSelectionTest {
    private fun state()=EntityWorkspaceState(emptyMap()).apply {
        document=document.copy(components=JsonObject(mapOf("SpriteRenderer" to buildJsonObject { put("texture","") })))
    }
    @Test fun browseReplacesManuallyTypedPathButDoesNotOverwriteLaterEdits() {
        val state=state();val path=listOf("texture")
        state.editField("SpriteRenderer",path,JsonPrimitive(""),"typed.png")
        val accept=projectAssetSelection(state,"SpriteRenderer",path)
        accept(Path.of("chosen.png"))
        assertEquals("chosen.png",state.document.components.getValue("SpriteRenderer").jsonObject.getValue("texture").jsonPrimitive.content)
        val stale=projectAssetSelection(state,"SpriteRenderer",path)
        state.editField("SpriteRenderer",path,JsonPrimitive("chosen.png"),"later.png")
        stale(Path.of("stale.png"))
        assertEquals("later.png",state.document.components.getValue("SpriteRenderer").jsonObject.getValue("texture").jsonPrimitive.content)
    }
    @Test fun browseDoesNotChangeNewDocument() {
        val state=state();val accept=projectAssetSelection(state,"SpriteRenderer",listOf("texture"))
        state.newDocument();val before=state.document
        accept(Path.of("stale.png"))
        assertEquals(before,state.document)
    }
}
