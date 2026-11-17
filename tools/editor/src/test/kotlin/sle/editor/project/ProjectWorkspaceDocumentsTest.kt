package sle.editor.project

import sle.editor.model.*
import kotlinx.serialization.json.*
import java.nio.file.Files
import kotlin.test.*

class ProjectWorkspaceDocumentsTest {
    @Test fun normalExitStateSavePreservesLastSceneAndDoesNotRewriteProject() {
        val project=ProjectFiles.create(Files.createTempDirectory("scene-exit").resolve("Game"),"Game")
        val main=project.assets.resolve("Scenes/Main.scene.json")
        ProjectFiles.rememberScene(project,main)
        val descriptor=Files.readAllBytes(project.file);val source=Files.readAllBytes(main)
        val appearance=sle.editor.ui.theme.EditorAppearanceState(sle.editor.ui.theme.EditorAppearanceStore(Files.createTempDirectory("exit-appearance")))
        val workspace=sle.editor.workspace.EditorWorkspace(Result.success(EntityWorkspaceState(emptyMap())),
            Result.success(SceneWorkspaceState(emptyMap()) { it }),appearance=appearance)
        try {
            workspace.project.activate(project);workspace.uiScale=0.75f
            assertTrue(workspace.saveProjectEditorState())
            assertEquals(0.75f,ProjectFiles.editorState(project).uiScale)
            assertEquals("Assets/Scenes/Main.scene.json",ProjectFiles.editorState(project).lastScene)
            assertContentEquals(descriptor,Files.readAllBytes(project.file));assertContentEquals(source,Files.readAllBytes(main))
        } finally { workspace.close() }
    }

    @Test fun legacyProjectWithoutStartupOrLocalStateOpensWithoutRewritingContent() {
        var project=ProjectFiles.create(Files.createTempDirectory("scene-legacy").resolve("Game"),"Game")
        project=ProjectFiles.save(project,project.document.copy(startupScene=null))
        val before=Files.readAllBytes(project.file)
        val result=ProjectSceneRecovery.load(ProjectFiles.open(project.file)) { SceneDocument.parse(it);it }
        assertNull(result.document);assertNull(result.notice)
        assertContentEquals(before,Files.readAllBytes(project.file))
        assertFalse(Files.exists(project.root.resolve(".sle/editor-state.json")))
    }

    @Test fun savedSceneSurvivesCloseAndRecoveryWithStartupFallback() {
        val project=ProjectFiles.create(Files.createTempDirectory("scene-recovery").resolve("Game"),"Game")
        val documents=ProjectDocuments(project)
        val path=project.assets.resolve("Scenes/Other.scene.json")
        val saved=documents.save(path,ProjectDocumentKind.Scene,SceneDocument(name="Saved content",
            entities=listOf(EntityDocument(key="actor",name="Actor"))).serialize(),emptyMap(),null) { SceneDocument.parse(it) }
        ProjectFiles.rememberScene(project,saved.path)
        val state=ProjectWorkspaceState();state.activate(project);state.close();state.open(project.file)
        val recovered=ProjectSceneRecovery.load(state.opened!!) { SceneDocument.parse(it);it }
        assertEquals("Saved content",SceneDocument.parse(recovered.document!!.source).name)
        assertEquals("Actor",SceneDocument.parse(recovered.document.source).entities.single().name)
        Files.delete(path)
        val fallback=ProjectSceneRecovery.load(project) { SceneDocument.parse(it);it }
        assertEquals("Main",SceneDocument.parse(fallback.document!!.source).name)
        assertNotNull(fallback.notice)
    }

    @Test fun corruptOrEscapingRememberedScenesDoNotReplaceSavedStartup() {
        val project=ProjectFiles.create(Files.createTempDirectory("scene-recovery").resolve("Game"),"Game")
        val main=project.assets.resolve("Scenes/Main.scene.json");val before=Files.readAllBytes(main)
        val broken=project.assets.resolve("Scenes/Broken.scene.json");Files.writeString(broken,"broken")
        for(path in listOf("Assets/Scenes/Broken.scene.json","../outside.scene.json","/outside.scene.json")) {
            Files.writeString(project.root.resolve(".sle/editor-state.json"),"""{"uiScale":0.85,"lastScene":"$path"}""")
            val result=ProjectSceneRecovery.load(project) { SceneDocument.parse(it);it }
            assertEquals(main,result.document!!.path);assertNotNull(result.notice)
            assertContentEquals(before,Files.readAllBytes(main))
        }
        assertEquals(0.85f,ProjectFiles.editorState(project).uiScale)
        assertFails { ProjectFiles.rememberScene(project,project.root.parent.resolve("outside.scene.json")) }
    }

    @Test fun settingsRefreshDoesNotCountAsWorkspaceActivation() {
        val project=ProjectFiles.create(Files.createTempDirectory("scene-recovery").resolve("Game"),"Game")
        val state=ProjectWorkspaceState();state.activate(project);val activation=state.activationRevision
        state.saveSettings(project.document.copy(name="Renamed"))
        assertEquals(activation,state.activationRevision)
        state.close();state.activate(project);assertNotEquals(activation,state.activationRevision)
    }

    @Test fun sceneProjectOpenResetsHistoryAndInvalidOpenPreservesDraft() {
        val state=SceneWorkspaceState(emptyMap()) { it }
        state.addEntity();state.rename("Draft")
        val path=Files.createTempFile("scene",".scene.json")
        val loaded=OpenedProjectDocument(path,ProjectDocumentKind.Scene,SceneDocument(name="From disk").serialize(),emptyMap(),"revision")
        state.openProject(loaded)
        assertEquals("From disk",state.name);assertEquals(path,state.file)
        assertFalse(state.dirty);assertFalse(state.canUndo)
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
        scene.undo();assertEquals(listOf(first),scene.document().entities)
        scene.undo();assertTrue(scene.entities.isEmpty());assertFalse(scene.dirty)
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
