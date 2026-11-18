package sle.editor.model

import sle.editor.project.*
import kotlinx.serialization.json.*
import java.nio.file.Path
import kotlin.test.*

class LinkedPrefabWorkspaceTest {
    private val prefab=PrefabDocument("root",listOf(EntityDocument(key="root",name="Actor"),EntityDocument(key="child")),mapOf("child" to "root"))
    @Test fun importDuplicateDeleteUndoAndReloadRetainLinksAndIsolatedDescendants() {
        val state=SceneWorkspaceState(emptyMap()) { it }
        state.insertLinkedPrefab(prefab,"asset-id",emptyMap())
        val first=state.selectedKey!!
        assertEquals(2,state.entities.size)
        state.duplicateSelected()
        val second=state.selectedKey!!
        assertNotEquals(first,second)
        assertEquals(2,state.document().prefabInstances.size)
        assertEquals(second,state.parents["$second/child"])
        state.removeSelected();assertEquals(1,state.document().prefabInstances.size)
        state.undo();assertEquals(2,state.document().prefabInstances.size)
        val opened=OpenedProjectDocument(Path.of("scene.scene.json"),ProjectDocumentKind.Scene,state.serialize(),emptyMap(),"revision",state.document().prefabInstances)
        state.openProject(opened)
        assertEquals(2,state.document().prefabInstances.size)
        assertFalse(state.dirty)
        state.select("$first/child")
        assertFails { state.removeSelected() }
        assertEquals(4,state.entities.size)
    }
    @Test fun savingLinkedSceneRecordsACleanBaselineAndKeepsLaterEditsDirty() {
        val state=SceneWorkspaceState(emptyMap()) { it }
        state.insertLinkedPrefab(prefab,"asset-id",emptyMap())
        val savedSource=state.serialize()
        val opened=OpenedProjectDocument(Path.of("scene.scene.json"),ProjectDocumentKind.Scene,savedSource,emptyMap(),"revision",state.document().prefabInstances)
        state.savedProject(opened)
        assertFalse(state.dirty)
        state.rename("Later")
        state.savedProject(opened,savedSource)
        assertTrue(state.dirty)
    }
    @Test fun saveAfterComponentEditsUsesCapturedLinkBaseline() {
        val state=SceneWorkspaceState(emptyMap()) { it }
        state.insertLinkedPrefab(prefab,"asset-id",emptyMap())
        state.selected!!.document=state.selected!!.document.addComponent("Audio",buildJsonObject { put("volume",0.25) })
        val current=state.document()
        val captured=current.prefabInstances.map { it.capture(current) }
        state.savedProject(OpenedProjectDocument(Path.of("scene.scene.json"),ProjectDocumentKind.Scene,state.serialize(),emptyMap(),"revision",captured))
        assertFalse(state.dirty)
    }
    @Test fun standalonePrefabOpenAndSaveKeepChildren() {
        val state=EntityWorkspaceState(emptyMap())
        state.openResolved(prefab.serialize(),emptyMap(),Path.of("prefab.json"))
        state.rename("Edited")
        val saved=PrefabDocument.parse(state.serializePrefab())
        assertEquals(2,saved.entities.size)
        assertEquals("Edited",saved.entities.first { it.key==saved.root }.name)
        assertEquals("root",saved.parents["child"])
    }
}
