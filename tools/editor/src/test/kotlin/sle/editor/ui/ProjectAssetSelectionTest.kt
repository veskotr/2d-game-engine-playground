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
    @Test fun browseRejectsRemovedAndReattachedComponent() {
        val state=state()
        val accept=projectAssetSelection(state,"SpriteRenderer",listOf("texture"))
        state.removeComponent("SpriteRenderer")
        state.document=state.document.addComponent("SpriteRenderer",buildJsonObject { put("texture","") })
        val before=state.document
        accept(Path.of("stale.png"))
        assertEquals(before,state.document)
    }
    @Test fun browseRejectsEditContextBoundaryEvenWithUnchangedSource() {
        val state=state()
        // A context boundary must reject a delayed result even if no field changed.
        val accept=projectAssetSelection(state,"SpriteRenderer",listOf("texture"))
        state.endGesture()
        val before=state.document
        accept(Path.of("stale.png"))
        assertEquals(before,state.document)
    }
    @Test fun browseRejectsPlaybackStartedDuringImport() {
        val defaults=buildJsonObject { put("texture","") }
        val scene=SceneWorkspaceState(mapOf("SpriteRenderer" to ComponentDefinition(defaults,JsonObject(emptyMap())))) { it }
        scene.addEntity();val owner=scene.selected!!;owner.addComponent("SpriteRenderer")
        val accept=projectAssetSelection(owner,"SpriteRenderer",listOf("texture"))
        val before=owner.document
        ScenePlaybackState { it }.play(scene)
        accept(Path.of("stale.png"))
        assertEquals(before,owner.document)
    }
}
