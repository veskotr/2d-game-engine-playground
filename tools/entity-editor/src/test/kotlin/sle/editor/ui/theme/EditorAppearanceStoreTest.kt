package sle.editor.ui.theme

import java.nio.file.Files
import kotlin.test.*
import org.junit.Test

class EditorAppearanceStoreTest {
    @Test fun defaultsAndRoundTrip() {
        val directory=Files.createTempDirectory("appearance-test")
        val store=EditorAppearanceStore(directory)
        assertEquals(EditorAppearance(),store.load())
        val chosen=EditorAppearance(EditorThemeMode.Light,EditorDensity.Comfortable,1f)
        assertTrue(store.save(chosen))
        assertEquals(chosen,store.load())
    }
    @Test fun rejectsInvalidAndFutureSettings() {
        val directory=Files.createTempDirectory("appearance-invalid")
        val store=EditorAppearanceStore(directory)
        for(value in listOf("broken", "{\"version\":2}", "{\"version\":1,\"scale\":0}", "{\"version\":1,\"scale\":1e100}", "{\"version\":1,\"scale\":\"NaN\"}", "{\"version\":1,\"mode\":\"System\"}", "{\"version\":1,\"scale\":1.1}")) {
            Files.writeString(directory.resolve("appearance.json"),value)
            assertEquals(EditorAppearance(),store.load(),value)
            assertNotNull(store.lastFailure)
        }
        assertFailsWith<IllegalArgumentException> { EditorAppearance(scale=Float.NaN) }
    }
    @Test fun failedAtomicWritePreservesValidFile() {
        val directory=Files.createTempDirectory("appearance-atomic")
        val store=EditorAppearanceStore(directory)
        assertTrue(store.save(EditorAppearance()))
        val before=Files.readString(directory.resolve("appearance.json"))
        val failing=EditorAppearanceStore(directory) { error("injected failure") }
        assertFalse(failing.save(EditorAppearance(mode=EditorThemeMode.Light)))
        assertNotNull(failing.lastFailure)
        assertEquals(before,Files.readString(directory.resolve("appearance.json")))
        Files.list(directory).use { assertEquals(1,it.count()) }
    }
    @Test fun stateChangesOnlyAppearance() {
        val state=EditorAppearanceState(EditorAppearanceStore(Files.createTempDirectory("appearance-state")))
        state.update(state.value.copy(mode=EditorThemeMode.Light))
        assertEquals(EditorThemeMode.Light,state.value.mode)
        assertEquals(1f,state.value.scale)
        assertNull(state.notice)
    }
    @Test fun switchingWithDetachedPanelPreservesDocumentsDraftsHistoryAndLayout() {
        val catalog=mapOf("Transform" to sle.editor.model.ComponentDefinition(
            kotlinx.serialization.json.buildJsonObject { put("rotation",kotlinx.serialization.json.JsonPrimitive(0.0)) },
            kotlinx.serialization.json.JsonObject(emptyMap())))
        val appearance=EditorAppearanceState(EditorAppearanceStore(Files.createTempDirectory("appearance-workspace")))
        val workspace=sle.editor.workspace.EditorWorkspace(
            Result.success(sle.editor.model.EntityWorkspaceState(catalog)),
            Result.success(sle.editor.model.SceneWorkspaceState(catalog) { it }),appearance=appearance)
        try {
            val entity=workspace.authoring.getOrThrow()
            entity.rename("Draft")
            entity.editField("Transform",listOf("rotation"),kotlinx.serialization.json.JsonPrimitive(0.0),"-")
            workspace.windows.movePanel(sle.editor.workspace.PanelId("game"),sle.editor.workspace.DockTarget.Detached(sle.editor.workspace.LogicalBounds(0f,0f,600f,400f)))
            val layout=workspace.windows.layout
            val source=entity.document.serialize()
            val drafts=entity.drafts.toMap()
            val undo=entity.canUndo
            appearance.update(appearance.value.copy(mode=EditorThemeMode.Light))
            assertEquals(source,entity.document.serialize())
            assertEquals(drafts,entity.drafts)
            assertEquals(undo,entity.canUndo)
            assertEquals(layout,workspace.windows.layout)
            assertSame(entity,workspace.authoring.getOrThrow())
        } finally { workspace.close() }
    }

}
