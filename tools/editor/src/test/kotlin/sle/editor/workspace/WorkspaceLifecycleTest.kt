package sle.editor.workspace

import kotlin.test.*
import sle.editor.model.*
import sle.editor.project.ProjectFiles
import sle.editor.ui.editorApplicationMenus
import sle.editor.viewport.Playback
import kotlinx.serialization.json.*
import java.nio.file.Files

class WorkspaceLifecycleTest {
    private val game = PanelId("game")
    @Test fun closingToolHostHidesAndReopeningRestoresSingletonWithoutShutdown() {
        var closes = 0
        val manager = WorkspaceWindowManager(onShutdown = { closes++ })
        manager.movePanel(game, DockTarget.Detached(LogicalBounds(40f, 40f, 600f, 400f)))
        val host = manager.layout.hosts.last().id
        manager.closeHost(host)
        assertTrue(game in manager.layout.hidden)
        assertEquals(0, closes)
        manager.showPanel(game)
        assertEquals(1, manager.layout.tabs().sumOf { it.panels.count { id -> id == game } })
        assertEquals(host, manager.hostFor(game))
        assertTrue(validateLayout(manager.layout, standardPanels().map { it.id }.toSet()).isEmpty())
    }
    @Test fun canceledHomeExitPreservesHostsAndAcceptedExitClosesOnce() {
        var closes = 0
        val manager = WorkspaceWindowManager(onShutdown = { closes++ })
        manager.movePanel(game, DockTarget.Detached(LogicalBounds(40f, 40f, 600f, 400f)))
        val before = manager.layout
        assertFalse(manager.requestExit { false })
        assertEquals(before, manager.layout)
        assertEquals(0, closes)
        assertTrue(manager.requestExit { true })
        manager.requestExit { true }
        manager.close()
        assertEquals(1, closes)
    }
    @Test fun focusRestoreResetAndGroupCommandsKeepRegistryUnique() {
        val manager = WorkspaceWindowManager()
        val group = manager.layout.tabs().single { game in it.panels }.group
        manager.moveGroup(group, DockTarget.Detached(LogicalBounds(0f, 0f, 600f, 400f)))
        manager.focusPanel(game)
        assertEquals(game, manager.layout.tabs().single { it.group == group }.active)
        manager.hidePanel(game)
        manager.focusPanel(game)
        assertFalse(game in manager.layout.hidden)
        manager.resetLayout()
        assertEquals(defaultDockLayout(standardPanels()), manager.layout)
    }
    private fun workspace(): EditorWorkspace {
        val catalog=mapOf("Transform" to ComponentDefinition(buildJsonObject { put("rotation",0.0) },JsonObject(emptyMap())))
        return EditorWorkspace(Result.success(EntityWorkspaceState(catalog)),Result.success(SceneWorkspaceState(catalog) { it }),
            scenePlayback=ScenePlaybackState { it })
    }
    @Test fun hostClosureAndPanelFocusPreserveRealPlaybackDraftsAndHistory() {
        val workspace=workspace()
        val prefab=workspace.authoring.getOrThrow()
        prefab.rename("Unsaved prefab")
        prefab.editField("Transform",listOf("rotation"),JsonPrimitive(0.0),"-")
        val scene=workspace.scene.getOrThrow();scene.addEntity()
        workspace.scenePlayback.play(scene)
        val snapshot=workspace.scenePlayback.source
        workspace.windows.movePanel(game,DockTarget.Detached(LogicalBounds(0f,0f,600f,400f)))
        workspace.windows.closeHost(workspace.windows.hostFor(game)!!)
        workspace.windows.focusPanel(PanelId("entity"))
        assertEquals(Playback.Running,workspace.scenePlayback.playback)
        assertEquals(snapshot,workspace.scenePlayback.source)
        assertEquals(2,workspace.selectedTab)
        assertEquals("-",prefab.drafts["Transform/rotation"])
        assertTrue(prefab.canUndo)
        assertFalse(workspace.windows.requestExit { false })
        assertEquals(Playback.Running,workspace.scenePlayback.playback)
        workspace.close()
    }
    @Test fun failedProjectOpenPreservesDocumentsAndSuccessfulSwitchKeepsWorkspaceAndLayout() {
        val workspace=workspace()
        val folder=Files.createTempDirectory("sle-workspace-project")
        try {
            val first=ProjectFiles.create(folder.resolve("first"),"First")
            val second=ProjectFiles.create(folder.resolve("second"),"Second")
            workspace.project.open(first.file)
            workspace.authoring.getOrThrow().rename("Draft")
            val before=workspace.windows.layout
            assertFails { workspace.project.open(folder.resolve("missing.sleproject")) }
            assertEquals(first,workspace.project.opened)
            assertEquals("Draft",workspace.activeEntity!!.document.name)
            workspace.project.open(second.file)
            assertEquals(second,workspace.project.opened)
            assertEquals(before,workspace.windows.layout)
            assertSame(workspace.authoring.getOrThrow(),workspace.activeEntity)
        } finally { folder.toFile().deleteRecursively();workspace.close() }
    }
    @Test fun saveRoutesSceneCopyToSceneAndStandalonePrefabToPrefab() {
        val workspace=workspace()
        val scene=workspace.scene.getOrThrow();scene.addEntity()
        val prefab=workspace.authoring.getOrThrow()
        var sceneSaves=0;var prefabSaves=0
        scene.projectFileAction={save,_ -> if(save) sceneSaves++;true}
        prefab.projectFileAction={save,_ -> if(save) prefabSaves++;true}
        fun save(linked: Boolean) {
            editorApplicationMenus(scene,if(linked) scene.selected else prefab,prefab,true,linked,true,
                {},{},{},{},{}).first { it.first=="File" }.second.first { it.label=="Save" }.action.invoke()
        }
        save(true);assertEquals(1,sceneSaves);assertEquals(0,prefabSaves)
        save(false);assertEquals(1,sceneSaves);assertEquals(1,prefabSaves)
        workspace.close()
    }

    @Test fun restoredPrefabPanelWithoutAnOpenPrefabDoesNotSwitchModes() {
        val workspace=workspace()
        workspace.windows.showPanel(PanelId("entity"))
        assertEquals(2,workspace.selectedTab,"an old layout's active Entity tab must not open an empty prefab")
        workspace.close()
    }

    @Test fun hidingEntityAndRestoringDefaultLayoutSelectExposedAuthoringContext() {
        val workspace=workspace();workspace.prefabSession=true
        workspace.windows.showPanel(PanelId("entity"))
        assertEquals(0,workspace.selectedTab)
        workspace.windows.showPanel(PanelId("assets"))
        workspace.windows.hidePanel(PanelId("entity"))
        assertEquals(2,workspace.selectedTab)
        workspace.windows.showPanel(PanelId("entity"))
        workspace.windows.resetLayout()
        assertEquals(2,workspace.selectedTab)
        workspace.close()
    }

    @Test fun hidingEntityFromUtilityGroupReturnsToVisibleSceneContext() {
        val workspace=workspace();workspace.prefabSession=true
        val assetsGroup=workspace.windows.layout.tabs().single { PanelId("assets") in it.panels }.group
        workspace.windows.movePanel(PanelId("entity"),DockTarget.Tab(assetsGroup,1))
        workspace.windows.showPanel(PanelId("entity"))
        assertEquals(0,workspace.selectedTab)
        workspace.windows.hidePanel(PanelId("entity"))
        assertEquals(2,workspace.selectedTab)
        assertTrue(workspace.sceneDocument)
        workspace.close()
    }

    @Test fun prefabTabCloseCanBeVetoedAndOnlyAsksForTheTemporaryPanel() {
        val workspace=workspace();workspace.prefabSession=true
        val asked=mutableListOf<PanelId>()
        var allow=false
        workspace.windows.closeRequest={ asked+=it;allow }
        workspace.windows.showPanel(PanelId("entity"))
        workspace.windows.hidePanel(PanelId("entity"))
        assertFalse(PanelId("entity") in workspace.windows.layout.hidden,"vetoed close keeps the unsaved prefab tab")
        assertEquals(0,workspace.selectedTab)
        workspace.windows.hidePanel(PanelId("assets"))
        assertEquals(listOf(PanelId("entity")),asked,"ordinary panels never ask")
        allow=true
        workspace.windows.hidePanel(PanelId("entity"))
        assertTrue(PanelId("entity") in workspace.windows.layout.hidden)
        assertEquals(2,workspace.selectedTab,"closing returns to the scene")
        workspace.close()
    }

}
