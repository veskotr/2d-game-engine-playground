package sle.editor.project

import sle.editor.model.*
import kotlinx.serialization.json.*
import java.nio.file.Files
import kotlin.test.*

class ProjectWorkspaceDocumentsTest {
    @Test fun sceneProjectOpenResetsHistoryAndInvalidOpenPreservesDraft() {
        val state=SceneWorkspaceState(emptyMap()) { it }
        state.addEntity();state.rename("Draft")
        val path=Files.createTempFile("scene",".scene.json")
        val loaded=OpenedProjectDocument(path,ProjectDocumentKind.Scene,SceneDocument(name="From disk").serialize(),emptyMap(),"revision")
        state.openProject(loaded)
        assertEquals("From disk",state.name);assertEquals(path,state.file)
        assertFalse(state.dirty);assertFalse(state.canUndoStructure)
        state.rename("Keep draft")
        assertFails { state.openProject(loaded.copy(source="{}")) }
        assertEquals("Keep draft",state.name);assertTrue(state.dirty)
        state.newScene();assertNull(state.projectDocument)
    }
    @Test fun prefabProjectOpenPreservesResolvedAssetsAndSaveHistory() {
        val state=EntityWorkspaceState(emptyMap())
        val path=Files.createTempFile("prefab",".prefab.json")
        val document=EntityDocument(key="actor",name="From disk")
        val loaded=OpenedProjectDocument(path,ProjectDocumentKind.Prefab,document.serialize(),emptyMap(),"revision")
        state.openProject(loaded)
        assertFalse(state.dirty);assertEquals(path,state.file)
        state.rename("Changed")
        state.savedProject(loaded.copy(source=state.document.serialize(),revision="saved"))
        assertFalse(state.dirty);assertTrue(state.canUndo)
        state.undo();assertTrue(state.dirty);assertEquals("From disk",state.document.name)
        state.newDocument();assertNull(state.projectDocument)
    }
    @Test fun projectPrefabInsertionCreatesIndependentEntityAndUndoKeepsSharedPaths() {
        val scene=SceneWorkspaceState(emptyMap()) { it }
        val source=EntityDocument(key="prefab-key",name="Actor",components=buildJsonObject {
            put("SpriteRenderer",buildJsonObject { put("texture","C:/Game/Assets/Textures/shared.png") })
        })
        scene.insertPrefab(source,emptyMap())
        val first=scene.selected!!.document
        scene.insertPrefab(source,emptyMap())
        assertNotEquals(first.key,scene.selected!!.document.key)
        assertEquals(source.components,scene.selected!!.document.components)
        assertTrue(scene.dirty)
        scene.undoStructure();assertEquals(listOf(first),scene.document().entities)
        scene.undoStructure();assertTrue(scene.entities.isEmpty());assertFalse(scene.dirty)
    }

    @Test fun editsDuringSaveRemainDirtyButNextSaveUsesNewDiskRevision() {
        val state=EntityWorkspaceState(emptyMap())
        val path=Files.createTempFile("prefab",".prefab.json")
        val initial=OpenedProjectDocument(path,ProjectDocumentKind.Prefab,state.document.serialize(),emptyMap(),"old")
        state.openProject(initial)
        state.rename("Saved snapshot");val source=state.document.serialize()
        state.rename("Later draft")
        state.savedProject(initial.copy(source=source,revision="new"),source,emptyMap())
        assertEquals("Later draft",state.document.name);assertTrue(state.dirty)
        assertEquals("new",state.projectDocument!!.revision)
        val scene=SceneWorkspaceState(emptyMap()) { it }
        scene.rename("Saved scene");val savedSource=scene.serialize()
        scene.rename("Later scene")
        scene.savedProject(initial.copy(kind=ProjectDocumentKind.Scene,source=savedSource),savedSource,emptyMap())
        assertEquals("Later scene",scene.name);assertTrue(scene.dirty)
    }

}
