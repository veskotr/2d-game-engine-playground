package sle.editor.app

import sle.editor.workspace.*
import sle.editor.viewport.ViewId
import sle.editor.project.assets.LocalProjectAssets
import sle.editor.scripts.LocalNewScriptBehavior
import sle.editor.scripts.ScriptCreation
import sle.editor.scripts.ScriptCreationDialog
import sle.editor.scripts.LocalOpenScriptAttachment
import sle.editor.scripts.ScriptSourceActions
import sle.editor.project.assets.projectAssetControls
import sle.editor.project.assets.AssetBrowserState
import sle.editor.smoke.runAssetBrowserSmoke
import sle.editor.project.assets.rememberAssetBrowser
import sle.editor.project.assets.AssetBrowserPanel
import sle.editor.project.assets.assetBrowserMenu
import sle.editor.project.ProjectWorkspaceState
import sle.editor.project.ProjectCommands
import sle.editor.project.projectControls
import sle.editor.project.projectFileMenu
import sle.editor.files.createStarterScene
import sle.editor.files.createCameraScene
import sle.editor.model.EntityDocument
import sle.editor.model.EntityWorkspaceState
import sle.editor.model.SceneWorkspaceState
import sle.editor.model.ScenePlaybackState
import sle.editor.model.isolatedComponentPreview
import sle.editor.model.readComponentCatalog
import sle.editor.smoke.ViewportSmokeChecks
import sle.editor.smoke.runProjectSmoke
import sle.editor.smoke.runAuthoringSmoke
import sle.editor.smoke.runGizmoSmoke
import sle.editor.smoke.runSceneSmoke
import sle.editor.smoke.runContextMenuSmoke
import sle.editor.smoke.addComponentAtEngineSize
import sle.editor.smoke.runCameraPlaySmoke
import sle.editor.ui.editorMenuLauncher
import sle.editor.ui.sceneCreationMenu
import sle.editor.ui.entityCreationMenu
import sle.editor.ui.EditorMenuStrip
import sle.editor.ui.editorApplicationMenus
import sle.editor.ui.EditorTheme
import sle.editor.ui.EntityWorkspace
import sle.editor.ui.controls.*
import sle.editor.ui.theme.LocalEditorMetrics
import sle.editor.ui.GizmoControls
import sle.editor.ui.SandboxControls
import sle.editor.ui.SceneWorkspace
import sle.editor.ui.SceneEntityInspector
import sle.editor.ui.SceneTree
import sle.editor.ui.EntityOutline
import sle.editor.ui.assetDropTarget
import sle.editor.ui.SceneViewControls
import sle.editor.ui.confirmDiscard
import sle.editor.ui.confirmDiscardScene
import sle.editor.viewport.EngineCanvas
import sle.editor.viewport.Playback
import sle.editor.viewport.SharedViewport
import sle.editor.viewport.ViewportMode

import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import sle.editor.console.ConsoleSeverity
import sle.editor.console.editorConsoleSnapshot
import sle.editor.command.menuEntry
import sle.editor.scripts.LocalProjectDrawers
import sle.editor.scripts.ProjectLuaCommand
import sle.editor.scripts.ProjectLuaPanelContent
import sle.editor.scripts.rememberProjectLuaScripts
import sle.editor.scripts.requireLuaSuccess
import javax.swing.Timer
import java.awt.Robot
import java.awt.Rectangle
import java.io.File
import javax.imageio.ImageIO
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    if("--theme-gallery" in args || "--inspector-gallery" in args) { sle.editor.ui.theme.launchThemeGallery(args.indexOf("--capture").takeIf { it>=0 }?.let { java.nio.file.Path.of(args[it+1]) },inspectors="--inspector-gallery" in args);return }
    if("--layout-persistence-smoke" in args) { sle.editor.smoke.runWorkspacePersistenceSmoke();return }
    if("--layout-persistence-phase" in args) System.setProperty("sle.workspaceData",java.nio.file.Path.of(args[2],"standalone").toString())
    if("--docking-workspace-smoke" in args) System.setProperty("sle.workspaceData",java.nio.file.Files.createTempDirectory("sle-docking-layout").toString())
    if ("--owner-failure-smoke" in args) { sle.editor.smoke.runMultiViewportSmoke(ownerFailure=true); return }
    if ("--input-actions-smoke" in args) { sle.editor.smoke.runInputActionsSmoke(); return }
    if ("--console-smoke" in args) { sle.editor.smoke.runConsoleSmoke(); return }
    if ("--multi-viewport-smoke" in args) { sle.editor.smoke.runMultiViewportSmoke(); return }
    if ("--docking-feasibility-smoke" in args) { sle.editor.smoke.runDockingFeasibilitySmoke("--docking-feasibility-gpu" in args); return }
    if ("--authoring-smoke" in args) { runAuthoringSmoke(); return }
    launchEditor(args)
}

private fun launchEditor(args: Array<String>) = application {
    val layoutPhase="--layout-persistence-phase" in args
    val dockingSmoke="--docking-workspace-smoke" in args
    val distributionSmoke="--project-distribution-smoke" in args
    val runtimeSmoke="--project-runtime-smoke" in args || distributionSmoke
    val documentSmoke="--project-documents-smoke" in args
    val assetSmoke="--asset-browser-smoke" in args
    val tileSmoke="--tile-painting-smoke" in args
    val projectSmoke="--project-smoke" in args
    val sandboxSmoke="--smoke-test" in args
    val authoringUiSmoke="--authoring-ui-smoke" in args
    val switchingSmoke="--preview-smoke" in args
    val gizmoSmoke="--gizmo-smoke" in args
    val sceneSmoke="--scene-smoke" in args
    val menuSmoke="--context-menu-smoke" in args
    val cameraSmoke="--camera-smoke" in args
    val appearanceSmoke="--editor-appearance-smoke" in args
    val walkSmoke="--l1-walk-smoke" in args
    val previewSmoke=authoringUiSmoke || switchingSmoke
    val testing=layoutPhase || dockingSmoke || sandboxSmoke || previewSmoke || gizmoSmoke || sceneSmoke || cameraSmoke || menuSmoke || projectSmoke || assetSmoke || documentSmoke || runtimeSmoke || appearanceSmoke || walkSmoke || tileSmoke
    val windowState=rememberWindowState(width=1280.dp,height=900.dp)
    val authoring=remember {
        runCatching { EntityWorkspaceState(readComponentCatalog()).apply {
            if (previewSmoke || gizmoSmoke) {
                document=document.copy(name="Collider Preview")
                addComponentAtEngineSize("BoxCollider")
                editField("BoxCollider",listOf("size","x"),JsonPrimitive(1.0),"2")
                editField("BoxCollider",listOf("size","y"),JsonPrimitive(1.0),"4")
                if (authoringUiSmoke || gizmoSmoke) {
                    addComponentAtEngineSize("SpriteRenderer")
                    val imported = importAsset(java.nio.file.Path.of("assets/textures/tile2.png"))
                    setValue("SpriteRenderer", listOf("texture"), JsonPrimitive(imported.toString().replace('\\', '/')))
                    val packagePath = java.nio.file.Path.of(System.getProperty("sle.smokeReport")).resolveSibling("visual-preview.sleprefab")
                    java.nio.file.Files.createDirectories(packagePath.parent)
                    save(packagePath)
                    newDocument()
                    open(packagePath)
                }
            }
        } }
    }
    var observedDocumentCommands by remember { mutableStateOf<sle.editor.project.ProjectDocumentCommands?>(null) }
    var observedAssetCommands by remember { mutableStateOf<sle.editor.project.assets.ProjectAssetCommands?>(null) }
    var observedAssets by remember { mutableStateOf<AssetBrowserState?>(null) }
    val assetBounds=remember { mutableMapOf<String,androidx.compose.ui.geometry.Rect>() }
    var projectActions by remember { mutableStateOf<ProjectCommands?>(null) }
    val scene=remember { authoring.map { workspace -> SceneWorkspaceState(workspace.catalog).apply {
        if(sceneSmoke || menuSmoke || dockingSmoke || gizmoSmoke) createStarterScene(this,workspace.catalog,java.nio.file.Path.of(System.getProperty("sle.smokeReport")).parent)
        if(cameraSmoke || walkSmoke) createCameraScene(this,workspace.catalog,java.nio.file.Path.of(System.getProperty("sle.smokeReport")).parent)
    } } }
    val workspace=remember { EditorWorkspace(authoring,scene,sandboxSmoke,previewSmoke,
        appearance=sle.editor.ui.theme.EditorAppearanceState(
            if(testing) sle.editor.ui.theme.EditorAppearanceStore(java.nio.file.Files.createTempDirectory("sle-smoke-appearance")).also { store ->
                // Smoke-only: -Dsle.smokeTheme=Light captures the real editor in the light theme.
                System.getProperty("sle.smokeTheme")?.let { mode -> store.save(sle.editor.ui.theme.EditorAppearance(mode=sle.editor.ui.theme.EditorThemeMode.valueOf(mode))) }
            }
            else sle.editor.ui.theme.EditorAppearanceStore()),
        snapStore=if(testing) sle.editor.viewport.SnapSettingsStore(java.nio.file.Files.createTempDirectory("sle-smoke-snap")) else sle.editor.viewport.SnapSettingsStore()) }
    remember { if(!testing || dockingSmoke || layoutPhase) workspace.startLayoutPersistence();true }
    val appearance=workspace.appearance.value
    SideEffect {
        val colors=sle.editor.ui.theme.editorColors(appearance.mode)
        workspace.engineSession.owner.editorSelectionColor=colors.selectionOutline
        workspace.engineSession.owner.luaErrorColor=colors.error
        workspace.engineSession.owner.editorAxisColors=colors.viewportXAxis to colors.viewportYAxis
    }
    LaunchedEffect(appearance) { sle.editor.ui.theme.applyEditorSwingAppearance(appearance) }
    val appIcon=androidx.compose.ui.res.painterResource("sle/editor/branding/sle-256.png")
    val project=workspace.project
    val manager=workspace.windows
    val assetBrowserVisible=PanelId("assets") !in manager.layout.hidden
    val requestedProject=args.indexOf("--project").takeIf { it>=0 }?.let { args.getOrNull(it+1) }
    val requestedScene=args.indexOf("--scene").takeIf { it>=0 }?.let { args.getOrNull(it+1) }
    remember { requestedScene?.let { path -> scene.getOrNull()?.let { state ->
        runCatching { state.open(java.nio.file.Path.of(path)) }.onFailure { state.error=it.message }
    } }; true }
    val fallbackDocument=remember { EntityDocument().serialize() }
    val initialIdentity=remember { authoring.getOrNull()?.document?.key }
    var selectedTab by workspace::selectedTab
    remember { selectedTab=if(sandboxSmoke) 1 else if(previewSmoke || gizmoSmoke) 0 else 2; true }
    var uiScale by workspace::uiScale
    var editingSceneKey by workspace::editingSceneKey
    val treeNodeBounds=remember { mutableMapOf<String,androidx.compose.ui.geometry.Rect>() }
    val entityMenuBounds=remember { arrayOfNulls<androidx.compose.ui.geometry.Rect>(1) }
    val projectFields=remember { mutableMapOf<String,androidx.compose.ui.geometry.Rect>() }
    val shellBounds=remember { mutableMapOf<String,androidx.compose.ui.geometry.Rect>() }
    val linkedEntity=scene.getOrNull()?.entities?.firstOrNull { it.document.key==editingSceneKey }
    val activeEntity=linkedEntity ?: authoring.getOrNull()
    val scenePlayback=workspace.scenePlayback
    var playback by remember { mutableStateOf(Playback.Running) }
    val engineSession=workspace.engineSession
    DisposableEffect(engineSession) { onDispose { java.awt.EventQueue.invokeLater { workspace.close() } } }
    var saveActive by remember { mutableStateOf<(() -> Unit)?>(null) }
    var canvas by remember { mutableStateOf<EngineCanvas?>(null) }
    val scriptEditorStore = remember { sle.editor.scripts.ScriptEditorSettingsStore() }
    val scriptEditor = remember(scriptEditorStore) { sle.editor.scripts.ScriptEditor(scriptEditorStore) }
    var scriptEditorSettings by remember { mutableStateOf<sle.editor.scripts.ScriptEditorSettings?>(null) }
    var scriptEditorDialog by remember { mutableStateOf(false) }
    var snapDialog by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("Preparing engine viewport…") }

    var ready by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var readyFrames by remember { mutableStateOf(0) }
    var lastRenderedMode by remember { mutableStateOf<ViewportMode?>(null) }
    val mode=when(selectedTab) { 0 -> ViewportMode.Entity; 1 -> ViewportMode.Sandbox; else -> ViewportMode.Scene }
    val inspector=if(mode==ViewportMode.Scene) scene.getOrNull()?.selected else activeEntity
    fun switchTab(index: Int) {
        if(index!=2) scenePlayback.stop()
        if(index==0) workspace.prefabSession=true
        selectedTab=index
        manager.showPanel(PanelId(if(index==0) "entity" else if(index==1) "game" else "scene"))
    }
    fun showScene(game: Boolean) {
        canvas?.finishGesture();selectedTab=2;scenePlayback.gameView=game
        manager.showPanel(PanelId(if(game) "game" else "scene"))
    }
    /** Ordinary scene entities are edited in the Inspector; prefab documents use the temporary Prefab tab. */
    fun editSceneEntity(key: String) {
        canvas?.finishGesture();scene.getOrNull()?.select(key);editingSceneKey=null
        if(selectedTab!=2 || scenePlayback.gameView) showScene(false)
        manager.showPanel(PanelId("inspector"))
    }
    /** Closing the Prefab tab respects unsaved changes, then returns to the scene. */
    fun closePrefabTab(): Boolean {
        val prefab=authoring.getOrNull()
        if(prefab!=null && !confirmDiscard(prefab)) return false
        canvas?.finishGesture();prefab?.newDocument();workspace.prefabSession=false;editingSceneKey=null
        if(selectedTab==0) selectedTab=2
        return true
    }
    remember { manager.closeRequest={ panel -> panel.value!="entity" || closePrefabTab() };true }
    /** Asset dropped from the Assets panel: prefabs become instances, images become sprite entities. */
    fun dropAsset(path: java.nio.file.Path,world: sle.editor.viewport.Vec2?) {
        val state=scene.getOrNull() ?: return
        if(!scenePlayback.editing) { state.error="Stop the game before adding to the scene";return }
        val name=path.fileName.toString()
        try {
            canvas?.finishGesture()
            when {
                name.endsWith(".prefab.json",true) || name.endsWith(".sleprefab",true) -> state.importPrefab(path,world)
                name.substringAfterLast('.').lowercase() in setOf("png","jpg","jpeg","bmp","tga","gif") -> {
                    val image=runCatching { ImageIO.read(path.toFile()) }.getOrNull()
                    state.addSprite(path.toAbsolutePath().toString().replace('\\','/'),name.substringBeforeLast('.'),
                        sle.editor.viewport.Vec2((image?.width ?: 32).toDouble(),(image?.height ?: 32).toDouble()),world)
                }
                else -> { state.error="Drop an entity template or an image into the scene";return }
            }
        } catch(failure: Exception) { state.error=failure.message ?: "Could not add $name";return }
        if(mode!=ViewportMode.Scene) { editingSceneKey=null;showScene(false) }
    }
    /** Scene entity dropped on an Assets folder: the payload is resolved now, and stale drags are refused. */
    fun dropEntity(folder: java.nio.file.Path,payload: sle.editor.ui.EntityDragPayload) {
        val state=scene.getOrNull() ?: return
        when {
            !scenePlayback.editing -> state.error="Stop the game before creating entity templates"
            state.key!=payload.sceneKey || state.file!=payload.file || state.entities.none { it.document.key==payload.entityKey } ->
                state.error="The scene changed during the drag; drag the entity again"
            else -> { canvas?.finishGesture();state.projectCreatePrefab?.invoke(payload.entityKey,folder) ?: run { state.error="Open a project to create entity templates" } }
        }
    }
    fun entityPayload(key: String)=scene.getOrNull()?.let { state ->
        sle.editor.ui.EntityDragPayload(state.key,state.file,key,state.entities.firstOrNull { it.document.key==key }?.document?.name?.ifBlank { null } ?: "entity")
    }
    LaunchedEffect(Unit) {
        if("--simultaneous-views" in args) {
            val group=manager.layout.tabs().single { PanelId("scene") in it.panels }.group
            manager.movePanel(PanelId("game"),DockTarget.Split(group,DockEdge.Right))
        }
    }
    LaunchedEffect(selectedTab) {
        if(selectedTab==0) workspace.prefabSession=true
        manager.showPanel(PanelId(if(selectedTab==0) "entity" else if(selectedTab==1) "game" else if(scenePlayback.gameView) "game" else "scene"))
    }
    // Saved layouts (including per-project ones) may still show the old permanent tab: hide it
    // until a prefab is opened. Nothing is open yet, so no document is discarded.
    LaunchedEffect(manager.loading,manager.layout) {
        if(!manager.loading && !workspace.prefabSession && selectedTab!=0 && PanelId("entity") !in manager.layout.hidden &&
            authoring.getOrNull()?.let { it.dirty || it.file!=null }!=true) manager.hidePanel(PanelId("entity"))
    }
    LaunchedEffect(selectedTab,scene.getOrNull()?.selectedKey) {
        if(selectedTab==0 && editingSceneKey!=null) {
            val selected=scene.getOrNull()?.selectedKey
            if(selected==null) switchTab(2) else editingSceneKey=selected
        }
    }
    val watchdog=remember {
        if (testing) Timer(if(documentSmoke || walkSmoke) 150_000 else if(distributionSmoke || dockingSmoke || appearanceSmoke || assetSmoke || tileSmoke) 90_000 else 30_000) {
            System.err.println("EDITOR_SMOKE_FAIL: viewport timed out")
            exitProcess(1)
        }.apply { isRepeats=false; start() } else null
    }
    // e.g. "SLE Editor 0.6.0 · engine 0.16.0"; the engine version comes from the loaded bridge.
    val editorTitle=remember {
        val engine=runCatching { sle.editor.bridge.NativeEngine.engineVersion() }.getOrNull()
        "SLE Editor ${EditorVersion.text}"+(engine?.let { " · engine $it" } ?: "")
    }
    Window(
        onCloseRequest={ if (manager.requestExit { (testing && !dockingSmoke) || (!project.busy && confirmDiscard(authoring.getOrNull()) && confirmDiscardScene(scene.getOrNull()) && workspace.saveProjectEditorState()) }) { watchdog?.stop(); exitApplication() } },
        title=project.opened?.let { "$editorTitle — ${it.document.name}" } ?: editorTitle,
        state=windowState,
        icon=appIcon,
        visible=!manager.loading,
    ) {
        LaunchedEffect(window) {
            if(layoutPhase) try {
                sle.editor.smoke.runWorkspacePersistencePhase(args[1],java.nio.file.Path.of(args[2]),workspace)
                watchdog?.stop();exitApplication()
            } catch(failure: Throwable) { failure.printStackTrace();runCatching { workspace.close() };exitProcess(1) }
        }
        DisposableEffect(window) {
            manager.bind(manager.layout.mainHost,window)
            // Fallback owner for dialogs opened while no editor window is active.
            sle.editor.ui.EditorDialogOwner.main=window
            onDispose {
                manager.unbind(manager.layout.mainHost,window)
                if(sle.editor.ui.EditorDialogOwner.main===window) sle.editor.ui.EditorDialogOwner.main=null
            }
        }
        LaunchedEffect(Unit) {
            if(dockingSmoke) {
                try {
                    while(readyFrames<5 || saveActive==null) delay(30)
                    sle.editor.smoke.runDockingWorkspaceSmoke(window,workspace,::editSceneEntity,shellBounds) { saveActive!!.invoke() }
                    watchdog?.stop();exitApplication()
                } catch(failure: Throwable) { runCatching { workspace.close() }.exceptionOrNull()?.let(failure::addSuppressed);failure.printStackTrace();exitProcess(1) }
            }
            if(runtimeSmoke) {
                try {
                    window.isAlwaysOnTop=true;window.toFront()
                    while(readyFrames<5 || projectActions==null) delay(30)
                    sle.editor.smoke.runProjectRuntimeSmoke(window,{canvas ?: error("Viewport missing")},project,projectActions!!,
                        {observedAssets},assetBounds,projectFields,scene.getOrThrow(),scenePlayback,workspace.notices,distributionSmoke)
                    watchdog?.stop();exitApplication()
                } catch(failure: Throwable) { canvas?.stopRendering();failure.printStackTrace();exitProcess(1) }
            }
            if(appearanceSmoke) {
                try {
                    window.isAlwaysOnTop=true;window.toFront()
                    while(readyFrames<5 || projectActions==null) delay(30)
                    sle.editor.smoke.runEditorAppearanceSmoke(window,{canvas ?: error("Viewport missing")},project,projectActions!!,
                        {observedAssets},scene.getOrThrow(),scenePlayback,workspace,::editSceneEntity,::showScene) { shellBounds["StatusBar"] }
                    watchdog?.stop();exitApplication()
                } catch(failure: Throwable) { canvas?.stopRendering();failure.printStackTrace();exitProcess(1) }
            }
            if(documentSmoke) {
                try {
                    window.isAlwaysOnTop=true;window.toFront()
                    while(readyFrames<5 || projectActions==null) delay(30)
                    sle.editor.smoke.runProjectDocumentsSmoke(window,{canvas ?: error("Viewport missing")},project,projectActions!!,
                        {observedDocumentCommands},{observedAssets},assetBounds,scene.getOrThrow(),authoring.getOrThrow(),scenePlayback,workspace.notices)
                    sle.editor.smoke.runPrefabWorkflowSmoke(window,{canvas ?: error("Viewport missing")},project,{observedDocumentCommands},
                        assetBounds,treeNodeBounds,scene.getOrThrow(),authoring.getOrThrow(),workspace)
                    watchdog?.stop();exitApplication()
                } catch(failure: Throwable) { canvas?.stopRendering();failure.printStackTrace();exitProcess(1) }
            }
            if (tileSmoke) {
                try {
                    window.isAlwaysOnTop = true
                    window.toFront()
                    while (readyFrames < 5 || projectActions == null) delay(30)
                    sle.editor.smoke.runTilePaintingSmoke(projectActions!!.openPath, project, scene.getOrThrow(),
                        { canvas }, workspace.tools, scenePlayback) {
                        repeat(100) { if ("Tile Palette" !in shellBounds) delay(30) }
                        val point = shellBounds.getValue("Tile Palette").center
                        val origin = window.contentPane.locationOnScreen
                        val robot = java.awt.Robot()
                        robot.mouseMove(origin.x + point.x.toInt(), origin.y + point.y.toInt())
                        robot.mousePress(java.awt.event.InputEvent.BUTTON1_DOWN_MASK)
                        robot.mouseRelease(java.awt.event.InputEvent.BUTTON1_DOWN_MASK)
                        val panel = PanelId("tilePalette")
                        repeat(100) { if (manager.layout.tabs().none { it.active == panel }) delay(30) }
                        check(manager.layout.tabs().any { it.active == panel }) { "Tile Palette button did not open its dock panel" }
                    }
                    watchdog?.stop()
                    exitApplication()
                } catch (failure: Throwable) { canvas?.stopRendering(); failure.printStackTrace(); exitProcess(1) }
            }
            if(assetSmoke) {
                try {
                    window.isAlwaysOnTop=true;window.toFront()
                    while(readyFrames<5 || projectActions==null) delay(30)
                    runAssetBrowserSmoke(window,{canvas ?: error("Viewport missing")},project,projectActions!!,{observedAssets},assetBounds,{readyFrames},scene.getOrThrow(),scenePlayback,{observedAssetCommands})
                    watchdog?.stop();exitApplication()
                } catch(failure: Throwable) { canvas?.stopRendering();failure.printStackTrace();exitProcess(1) }
            }
            if(projectSmoke) {
                try {
                    window.isAlwaysOnTop=true;window.toFront()
                    while(readyFrames<5 || projectActions==null) delay(30)
                    runProjectSmoke(window,project,projectActions!!,scene.getOrThrow(),projectFields,{observedDocumentCommands},{ mode -> workspace.appearance.update(workspace.appearance.value.copy(mode=mode)) },workspace.notices)
                    watchdog?.stop();exitApplication()
                } catch(failure: Throwable) { canvas?.stopRendering();failure.printStackTrace();exitProcess(1) }
            }
            if(menuSmoke) {
                try {
                    window.isAlwaysOnTop=true;window.toFront()
                    while(readyFrames<5) delay(50)
                    runContextMenuSmoke(window,{canvas ?: error("Viewport missing")},scene.getOrThrow(),treeNodeBounds,{entityMenuBounds[0]},shellBounds,workspace)
                    watchdog?.stop();exitApplication()
                } catch(failure: Throwable) { canvas?.stopRendering();failure.printStackTrace();exitProcess(1) }
            }
            if(walkSmoke) {
                try {
                    window.isAlwaysOnTop=true;window.toFront()
                    while(readyFrames<5) delay(50)
                    sle.editor.smoke.runL1WalkSmoke(window,scene.getOrThrow(),workspace,treeNodeBounds,shellBounds)
                    watchdog?.stop();exitApplication()
                } catch(failure: Throwable) { canvas?.stopRendering();failure.printStackTrace();exitProcess(1) }
            }
            if(cameraSmoke) {
                try {
                    window.isAlwaysOnTop=true;window.toFront()
                    while(readyFrames<5) delay(50)
                    runCameraPlaySmoke({canvas ?: error("Viewport missing")},scene.getOrThrow(),scenePlayback,switchView={game ->
                        val target=shellBounds.getValue(if(game) "GameTab" else "SceneTab").center
                        val origin=window.contentPane.locationOnScreen
                        Robot().apply {
                            mouseMove(origin.x+target.x.toInt(),origin.y+target.y.toInt())
                            mousePress(java.awt.event.InputEvent.BUTTON1_DOWN_MASK);mouseRelease(java.awt.event.InputEvent.BUTTON1_DOWN_MASK)
                        }
                        delay(100)
                    }) {
                        canvas?.finishGesture();scenePlayback.play(scene.getOrThrow());manager.showPanel(PanelId("game"))
                    }
                    ImageIO.write(Robot().createScreenCapture(Rectangle(window.locationOnScreen,window.size)),"png",
                        File(System.getProperty("sle.smokeReport")).parentFile.resolve("camera-editor-layout.png"))
                    watchdog?.stop();exitApplication()
                } catch(failure: Throwable) { canvas?.stopRendering();failure.printStackTrace();exitProcess(1) }
            }
            if(sceneSmoke) {
                try {
                    window.isAlwaysOnTop=true;window.toFront()
                    while(readyFrames<5) delay(50)
                    suspend fun clickUi(bounds: androidx.compose.ui.geometry.Rect) {
                        val origin=window.contentPane.locationOnScreen
                        Robot().apply {
                            mouseMove(origin.x+bounds.center.x.toInt(),origin.y+bounds.center.y.toInt())
                            mousePress(java.awt.event.InputEvent.BUTTON1_DOWN_MASK)
                            mouseRelease(java.awt.event.InputEvent.BUTTON1_DOWN_MASK)
                        }
                        delay(160)
                    }
                    ImageIO.write(Robot().createScreenCapture(Rectangle(window.locationOnScreen,window.size)),"png",
                        File(System.getProperty("sle.smokeReport")).parentFile.resolve("scene-tree-window.png"))
                    runSceneSmoke(canvas ?: error("Viewport missing"),scene.getOrThrow(),workspace.notices,{ key ->
                        clickUi(treeNodeBounds[key] ?: error("Tree node not laid out: $key"))
                    },workspace.tools,workspace.snap)
                    val beforeScale=scene.getOrThrow().serialize()
                    for(value in listOf(1.0f,0.75f,0.85f)) {
                        val frame=readyFrames
                        uiScale=value
                        while(readyFrames<frame+3) delay(30)
                        val key=scene.getOrThrow().entities.last().document.key
                        clickUi(treeNodeBounds[key] ?: error("Scaled tree node missing"))
                        check(scene.getOrThrow().selectedKey==key)
                    }
                    check(scene.getOrThrow().serialize()==beforeScale && !scene.getOrThrow().dirty)
                    clickUi(treeNodeBounds[scene.getOrThrow().entities.first().document.key] ?: error("Ground tree node missing"))
                    println("EDITOR_UI_SCALE_PASS 75/85/100")
                    val componentEntity=scene.getOrThrow().selected ?: error("No selected entity")
                    val originalEntityKey=componentEntity.document.key
                    val prefabBefore=authoring.getOrThrow().document
                    val otherKey=scene.getOrThrow().entities.last().document.key
                    componentEntity.addComponent("Animator")
                    componentEntity.setValue("Animator",listOf("targetEntities"),kotlinx.serialization.json.buildJsonObject { put("other",JsonPrimitive(otherKey)) })
                    editSceneEntity(originalEntityKey)
                    while(lastRenderedMode!=ViewportMode.Scene || canvas?.authoringReady()!=true) delay(30)
                    // Ordinary scene entities stay in the Scene view and Inspector; no Entity tab handoff.
                    check(editingSceneKey==null && selectedTab==2 && scene.getOrThrow().selectedKey==originalEntityKey && PanelId("entity") in manager.layout.hidden)
                    componentEntity.selectedTarget="SpriteRenderer"
                    val oldSprite=componentEntity.document.components.getValue("SpriteRenderer")
                    componentEntity.setValue("SpriteRenderer",listOf("size","x"),JsonPrimitive(430.0))
                    delay(150)
                    check(componentEntity.document.key==originalEntityKey && authoring.getOrThrow().document==prefabBefore)
                    componentEntity.undo()
                    check(componentEntity.document.components["SpriteRenderer"]==oldSprite)
                    check(componentEntity.document.serialize().contains(otherKey))
                    componentEntity.undo();componentEntity.undo()
                    selectedTab=2
                    while(lastRenderedMode!=ViewportMode.Scene || canvas?.authoringReady()!=true) delay(30)
                    println("SCENE_TREE_COMPONENT_HANDOFF_PASS key=$originalEntityKey")
                    val viewport=canvas ?: error("Viewport missing")
                    val originalContext=viewport.contextIdentity()
                    val sceneState=scene.getOrThrow()
                    val expected=sceneState.serialize()
                    sceneState.selected!!.editField("Transform",listOf("rotation"),JsonPrimitive(0.0),"-")
                    selectedTab=0
                    while(lastRenderedMode!=ViewportMode.Entity || (canvas?.authoringReady()!=true)) delay(30)
                    selectedTab=1
                    while(lastRenderedMode!=ViewportMode.Sandbox) delay(30)
                    val sandboxFrame=readyFrames
                    while(readyFrames<sandboxFrame+10) delay(30)
                    ViewportSmokeChecks.verifyDesktopSandbox(canvas ?: error("Viewport missing"))
                    selectedTab=2
                    while(lastRenderedMode!=ViewportMode.Scene || (canvas?.authoringReady()!=true)) delay(30)
                    check(engineSession.owner.contextIdentity()==originalContext)
                    check(sceneState.serialize()==expected && "Transform/rotation" in sceneState.selected!!.fieldErrors)
                    sceneState.selected!!.undo()
                    println("SCENE_ENTITY_SANDBOX_SWITCH_PASS context=$originalContext")
                    watchdog?.stop();exitApplication()
                } catch(failure: Throwable) { canvas?.stopRendering();failure.printStackTrace();exitProcess(1) }
            }
            if (gizmoSmoke) {
                try {
                    window.isAlwaysOnTop=true; window.toFront()
                    while (readyFrames < 5) delay(50)
                    runGizmoSmoke(canvas ?: error("Viewport missing"),authoring.getOrThrow(),workspace.snap)
                    // Rotate and Scale are Scene view tools (the prefab tab keeps its shape handles).
                    selectedTab=2
                    while(lastRenderedMode!=ViewportMode.Scene || canvas?.authoringReady()!=true) delay(30)
                    sle.editor.smoke.runTransformToolsSmoke(canvas ?: error("Viewport missing"),scene.getOrThrow(),workspace.tools,workspace.snap)
                    watchdog?.stop(); exitApplication()
                } catch (failure: Throwable) { canvas?.stopRendering(); failure.printStackTrace(); exitProcess(1) }
            }
            if (authoringUiSmoke) {
                try {
                    window.isAlwaysOnTop = true
                    window.toFront()
                    while (readyFrames < 18) delay(50)
                    delay(250)
                    val report=File(System.getProperty("sle.smokeReport")).parentFile.resolve("entity-editor.png")
                    report.parentFile.mkdirs()
                    val image=Robot().createScreenCapture(Rectangle(window.locationOnScreen,window.size))
                    ImageIO.write(image,"png",report)
                    val viewport=canvas ?: error("Viewport is missing")
                    val viewportImage=Robot().createScreenCapture(Rectangle(viewport.locationOnScreen,viewport.size))
                    var cyan=0
                    var textured=0
                    for (y in 0 until viewportImage.height) for (x in 0 until viewportImage.width) {
                        val pixel=viewportImage.getRGB(x,y)
                        if (((pixel shr 16) and 255)<100 && ((pixel shr 8) and 255)>150 && (pixel and 255)>150) ++cyan
                        val r=(pixel shr 16) and 255
                        val g=(pixel shr 8) and 255
                        val b=pixel and 255
                        if (r in 130..245 && g in 110..235 && b in 70..220 && r>g && g>b) ++textured
                    }
                    check(cyan>40) { "Native framebuffer rendered, but desktop preview is missing: cyan=$cyan; see $report" }
                    check(textured>200) { "Selected texture is missing from desktop viewport: textured=$textured; see $report" }
                    println("ENTITY_EDITOR_UI_SMOKE_PASS image=$report")
                    watchdog?.stop()
                    exitApplication()
                } catch (failure: Throwable) { failure.printStackTrace(); exitProcess(1) }
            }
        }
        EditorTheme(appearance) {
            val assets=rememberAssetBrowser(project.opened,window)
            val tileProject = project.opened
            val tileScene = scene.getOrNull()
            val tileOptions = tileProject?.let { sle.editor.ui.rememberTilePaletteOptions(it, assets?.snapshot) }
            if (tileProject != null && tileScene != null) {
                sle.editor.ui.SyncTilePainting(tileScene, tileProject, assets?.snapshot, tileOptions, workspace.tools)
            }
            SideEffect { if(assetSmoke || documentSmoke || runtimeSmoke || appearanceSmoke) observedAssets=assets }
            val recentDirectory=remember { if(testing) java.nio.file.Files.createTempDirectory("sle-smoke-recents") else null }
            val projectScripts = rememberProjectLuaScripts(project.opened, project.activationRevision, recentDirectory) {
                engineSession.owner.console.commandMessage(it, ConsoleSeverity.ERROR)
            }
            val currentScripts by rememberUpdatedState(projectScripts)
            val scripts = projectScripts.commands
            val scriptPanels = projectScripts.declarations.panels
            val openScriptPanels = remember(projectScripts) { mutableStateListOf<String>() }
            val scriptWindows = remember(projectScripts) { mutableMapOf<String, java.awt.Window>() }
            LaunchedEffect(projectScripts.trusted) {
                if (projectScripts.trusted != true) openScriptPanels.clear()
            }
            val commands=projectControls(project,
                scriptTrust=projectScripts.trusted,scriptTrustBusy=projectScripts.checkingTrust,setScriptTrust={ projectScripts.setTrust(it) },
                recentDirectory=recentDirectory,
                confirmSwitch={confirmDiscard(authoring.getOrNull()) && confirmDiscardScene(scene.getOrNull())},
                resetWorkspace={
                    canvas?.finishGesture();scenePlayback.stop();authoring.getOrNull()?.newDocument();scene.getOrNull()?.newScene()
                    editingSceneKey=null;selectedTab=2
                },workspaceRevision={
                    listOf(authoring.getOrNull()?.let { Triple(it.document,it.assets,it.drafts) },scene.getOrNull()?.document(),
                        scene.getOrNull()?.entities?.map { Triple(it.document,it.assets,it.drafts) })
                },uiScale={uiScale},setUiScale={uiScale=it},
                startupScenes=assets?.snapshot?.pathsById?.entries?.filter { it.value.endsWith(".scene.json",true) }?.map { it.key to it.value }?.sortedBy { it.second } ?: emptyList(),onBounds={id,bounds -> if(projectSmoke || runtimeSmoke) projectFields[id]=bounds})
            SideEffect { projectActions=commands;sle.editor.ui.EditorFileLocations.projectAssets=project.opened?.let { it.root.resolve(it.document.assetRoot) } }
            LaunchedEffect(requestedProject) { requestedProject?.let { commands.openPath(java.nio.file.Path.of(it)) } }
            val documents=sle.editor.project.projectDocumentControls(project,assets,scene.getOrNull(),authoring.getOrNull(),
                editable={scenePlayback.editing},onOpen={ kind ->
                    canvas?.finishGesture()
                    if(kind==sle.editor.project.ProjectDocumentKind.Scene) { editingSceneKey=null;showScene(false) }
                    else { editingSceneKey=null;switchTab(0) }
                })
            SideEffect { if(documentSmoke || projectSmoke) observedDocumentCommands=documents }
            val notices=workspace.notices
            val scriptSources = remember(project.activationRevision, project.opened?.document?.assetRoot) {
                ScriptSourceActions(project, scriptEditor)
            }
            fun openScriptSource(open: () -> Process) {
                runCatching(open).onFailure { notices.warning(it.message ?: "Could not open Lua source.") }
            }
            val openLuaSource: (sle.editor.console.LuaSource) -> Unit = { source ->
                openScriptSource { scriptEditor.open(source.path, source.line) }
            }
            val distribution=sle.editor.project.projectDistributionControls(project,notices)
            val runProject=sle.editor.project.projectRunAction(project,scenePlayback) { canvas?.finishGesture();editingSceneKey=null;showScene(true) }
            SideEffect {
                val opened = project.opened
                if (opened == null && workspace.tools.active == sle.editor.viewport.tools.ViewportToolId.Tiles) {
                    workspace.tools.activate(sle.editor.viewport.tools.ViewportToolId.Select)
                    workspace.tools.tilePainting.configure(null, null)
                }
                engineSession.owner.setEditorAssetRoot(opened?.let { it.root.resolve(it.document.assetRoot) },
                    opened?.document?.settings ?: sle.editor.project.ProjectSettings())
            }
            val assetCommands=projectAssetControls(project,assets,window,canMutate={
                scenePlayback.editing && scene.getOrNull()?.let { it.file==null && !it.dirty && it.entities.isEmpty() && it.allAssets().isEmpty() }!=false &&
                    authoring.getOrNull()?.let { it.file==null && !it.dirty && it.assets.isEmpty() }!=false
            },onTextureFilterChanged=engineSession.owner::refreshPreview,
                openDocument={ documents?.openPath?.invoke(it) },
                openProjectSettings=commands.settings)
            SideEffect { if(assetSmoke) observedAssetCommands=assetCommands }
            val creationScope = rememberCoroutineScope()
            val currentScriptTarget by rememberUpdatedState(inspector)
            val currentScriptAssets by rememberUpdatedState(assets)
            var scriptCreation by remember { mutableStateOf<ScriptCreation?>(null) }
            var creatingScript by remember { mutableStateOf(false) }
            var scriptCreationError by remember { mutableStateOf<String?>(null) }
            val newScriptBehavior: ((EntityWorkspaceState) -> Unit)? =
                if (project.opened == null || project.busy || !scenePlayback.editing) null
                else { target ->
                    if (currentScriptTarget === target) {
                        target.endGesture()
                        scriptCreationError = null
                        scriptCreation = ScriptCreation(project, target) {
                            currentScriptTarget === target && scenePlayback.editing && !project.busy
                        }
                    }
                }
            CompositionLocalProvider(
                LocalNewScriptBehavior provides newScriptBehavior,
                LocalProjectAssets provides assetCommands,
                sle.editor.ui.LocalEditorNotices provides notices,
                LocalOpenScriptAttachment provides { reference ->
                    openScriptSource { scriptSources.openAttachment(reference) }
                },
            ) {
            val editorMenuHandler=editorMenuLauncher {
                if(mode==ViewportMode.Scene) scene.getOrNull()?.let { sceneCreationMenu(it,scenePlayback.editing,onEditEntity={ key -> editSceneEntity(key) },position=engineSession.owner.contextPosition) } ?: emptyList()
                else activeEntity?.let { entityCreationMenu(it,linkedEntity!=null,assetCommands) } ?: emptyList()
            }
            // One command context for menus, toolbar actions and the keyboard dispatcher.
            val consoleScope = rememberCoroutineScope()
            val activeScene = scene.getOrNull()
            val currentAssets by rememberUpdatedState(assets)
            val enabledScripts by sle.editor.scripts.rememberEnabledProjectLuaCommands(
                scripts, activeScene, selectedTab == 2 && scenePlayback.editing && !project.busy,
                project.opened, project.activationRevision, assets?.snapshot,
            ) { engineSession.owner.console.commandMessage(it, ConsoleSeverity.ERROR) }
            val currentEnabledScripts by rememberUpdatedState(enabledScripts)
            fun projectLuaAvailable(script: ProjectLuaCommand): Boolean =
                currentScripts.generation == project.activationRevision && currentScripts.trusted == true &&
                    currentScripts.commands.any { it.script === script } &&
                    selectedTab == 2 && scenePlayback.editing && !project.busy

            fun submitLua(source: String, script: ProjectLuaCommand? = null) {
                val owner = workspace.engineSession.owner
                val console = owner.console
                if (console.commandRunning || (script != null && !projectLuaAvailable(script))) return
                if (!scenePlayback.editing) {
                    try { owner.queuePlayLua(source) }
                    catch (failure: Exception) {
                        console.commandMessage(failure.message ?: "Play Lua command failed", ConsoleSeverity.ERROR)
                    }
                    return
                }
                val state = scene.getOrNull() ?: return
                canvas?.finishGesture()
                state.selected?.endGesture()
                val captured = state.document()
                val revision = state.history.revision
                val generation = project.activationRevision
                val openedProject = project.opened
                val selection = state.selection.keys.toList()
                val assetSnapshot = currentAssets?.snapshot
                val playRevision = scenePlayback.playRevision
                console.commandRunning = true
                consoleScope.launch {
                    try {
                        val response = withContext(Dispatchers.Default) {
                            val snapshot = editorConsoleSnapshot(captured, selection, revision, generation,
                                openedProject, assetSnapshot).toString().encodeToByteArray()
                            script?.evaluate("run", snapshot) ?: Json.parseToJsonElement(
                                sle.editor.bridge.NativeEngine.runEditorLua(source.encodeToByteArray(), snapshot)
                                    .decodeToString()).jsonObject
                        }
                        response.getValue("output").jsonPrimitive.content.takeIf { it.isNotEmpty() }
                            ?.let { console.commandMessage(it.trimEnd('\n')) }
                        response.requireLuaSuccess()
                        val edits = response.getValue("edits").jsonArray
                        if (edits.isNotEmpty()) {
                            require(scene.getOrNull() === state && selectedTab == 2 && scenePlayback.editing &&
                                project.activationRevision == generation && project.opened === openedProject &&
                                state.history.revision == revision) { "Scene or project changed while Lua was running" }
                            if (script != null) {
                                require(projectLuaAvailable(script) && scenePlayback.playRevision == playRevision &&
                                    state.selection.keys.toList() == selection && currentAssets?.snapshot == assetSnapshot) {
                                    "Scene selection, assets, playback or script trust changed while Lua was running"
                                }
                            }
                            state.applyLuaConsoleEdits(captured, edits, openedProject)
                        }
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (failure: Exception) {
                        val diagnostic = failure.message ?: "Lua command failed"
                        console.commandMessage(script?.let { "${it.filename} (${it.id}) — $diagnostic" } ?: diagnostic,
                            ConsoleSeverity.ERROR)
                    } finally {
                        console.commandRunning = false
                    }
                }
            }
            val showSceneView: (Boolean)->Unit=::showScene
            val editorActions=remember { object: sle.editor.command.EditorActions {
                var projectCommands: ProjectCommands?=null
                var run: ()->Unit={}
                var keymap = sle.editor.command.standardKeymap
                var luaEnabled: (ProjectLuaCommand) -> Boolean = { false }
                var runLua: (ProjectLuaCommand) -> Unit = {}
                override fun projectLuaEnabled(script: ProjectLuaCommand) = luaEnabled(script)
                override fun runProjectLua(script: ProjectLuaCommand) = runLua(script)
                override val project get()=projectCommands
                override fun showScene(game: Boolean)=showSceneView(game)
                override fun openPrefabWorkspace() { editingSceneKey=null;switchTab(0) }
                override fun editEntity(key: String)=editSceneEntity(key)
                override fun showPanel(panel: PanelId)=manager.showPanel(panel)
                override fun showLuaConsole() {
                    manager.showPanel(PanelId("console"))
                    workspace.engineSession.owner.console.focusCommand()
                }
                override fun finishGesture() { canvas?.finishGesture() }
                override fun fit() { canvas?.fitEntity() }
                override fun frameSelected() {
                    val state=scene.getOrNull() ?: return
                    val bounds=state.selection.keys.filter { state.entity(it)!=null }
                        .mapNotNull { sle.editor.viewport.entityWorldBounds(state.worldDocument(it)) }.reduceOrNull { a,b -> a.union(b) } ?: return
                    canvas?.frameBounds(bounds)
                }
                override fun configureSnap() { snapDialog=true }
                override fun runProject()=run()
                override fun showKeyboardShortcuts()=sle.editor.ui.showKeyboardShortcuts(keymap)
            } }
            val keymap = remember(scripts) {
                if (scripts.isEmpty()) sle.editor.command.standardKeymap
                else sle.editor.command.Keymap(sle.editor.command.CommandRegistry(
                    sle.editor.command.standardCommands.commands + scripts.map { it.command }))
            }
            SideEffect {
                editorActions.projectCommands=commands
                editorActions.run=runProject
                editorActions.keymap=keymap
                editorActions.luaEnabled = { script ->
                    projectLuaAvailable(script) && !engineSession.owner.console.commandRunning &&
                        (!script.hasPredicate || script in currentEnabledScripts)
                }
                editorActions.runLua = { submitLua("", it) }
            }
            val commandContext = {
                val currentMode = when (selectedTab) { 0 -> ViewportMode.Entity; 1 -> ViewportMode.Sandbox; else -> ViewportMode.Scene }
                val currentScene = scene.getOrNull()
                val currentPrefab = authoring.getOrNull()
                val currentLinked = editingSceneKey?.let { currentScene?.entity(it) }
                sle.editor.command.CommandContext(
                    mode=currentMode,linked=currentLinked!=null,scene=currentScene,prefab=currentPrefab,
                    entity=if(currentMode==ViewportMode.Scene) currentScene?.selected else currentLinked ?: currentPrefab,
                    playback=scenePlayback,editable=scenePlayback.editing,projectOpen=project.opened!=null,
                    projectBusy=project.busy,viewportFailed=failed,
                    gameRunning=(currentMode==ViewportMode.Scene && scenePlayback.playback!=Playback.Stopped) ||
                        (currentMode==ViewportMode.Sandbox && playback!=Playback.Stopped),
                    gameActionKeys=if (currentMode==ViewportMode.Scene) engineSession.owner.activeActionKeys else emptySet(),
                    tools=workspace.tools,snap=workspace.snap,actions=editorActions,
                    modalOpen=sle.editor.ui.editorDialogOpen(),projectGeneration=project.activationRevision,
                    resolveDetailsAsset=project.opened?.let { opened ->
                        { text -> sle.editor.project.ProjectDocuments(opened).resolvePropertyAsset(text) }
                    },
                )
            }
            sle.editor.ui.EditorShortcuts(manager,keymap,commandContext)
            // Every delete path records through the scene; the notice offers the registered Undo.
            val liveCommandContext by rememberUpdatedState(commandContext)
            SideEffect { scene.getOrNull()?.let { state -> state.onDeleted={ subject -> sle.editor.ui.reportDeletion(notices,state,subject) { liveCommandContext() } } } }
            // Document, project, appearance and layout results go to the status bar, not panel text.
            sle.editor.ui.ReportNotices(notices) {
                fun document(key: String,error: ()->String?,clearError: ()->Unit,message: ()->String?,clearMessage: ()->Unit)=listOf(
                    sle.editor.ui.NoticeSource("$key/error",{ sle.editor.ui.NoticeLevel.Error },error,clearError),
                    sle.editor.ui.NoticeSource("$key/message",{ sle.editor.ui.documentMessageLevel(it) },message,clearMessage))
                fun entity(state: EntityWorkspaceState)=document("entity/"+System.identityHashCode(state),{state.error},{state.error=null},{state.message},{state.message=null})
                val sceneState=scene.getOrNull()
                (sceneState?.let { document("scene",{it.error},{it.error=null},{it.message},{it.message=null}) } ?: emptyList())+
                    (sceneState?.entities ?: emptyList()).flatMap(::entity)+
                    (authoring.getOrNull()?.let(::entity) ?: emptyList())+listOf(
                    sle.editor.ui.NoticeSource("project",{ sle.editor.ui.NoticeLevel.Error },{project.error},{project.error=null}),
                    sle.editor.ui.NoticeSource("appearance",{ sle.editor.ui.NoticeLevel.Warning },{workspace.appearance.notice},null),
                    sle.editor.ui.NoticeSource("layout",{ sle.editor.ui.NoticeLevel.Warning },{manager.layoutNotice},null))
            }
            val desiredProject=scenePlayback.project
            val desiredPlayback=if(mode==ViewportMode.Scene) scenePlayback.playback else playback
            val desiredSource=if(mode==ViewportMode.Scene) scenePlayback.source ?: scene.getOrNull()?.serialize() ?: "{\"entities\":[]}"
                else activeEntity?.document?.let { if(linkedEntity!=null) isolatedComponentPreview(it,scene.getOrThrow().entities.map { it.document.key }).serialize() else it.serialize() } ?: fallbackDocument
            val desiredGame=scenePlayback.gameView
            SideEffect {
                engineSession.onStatus={status=it}
                engineSession.onFailure={failure -> failed=true;status="Viewport failed: ${failure.message}";failure.printStackTrace();if(testing) exitProcess(1)}
            engineSession.onFrame={ frame,renderedMode ->
                                    ready=true
                                    if(testing) lastRenderedMode=renderedMode
                                    if (testing) readyFrames=frame
                                    if (sandboxSmoke) {
                                        if (frame == 20) windowState.size=DpSize(1000.dp,650.dp)
                                        if (frame == 25) playback=Playback.Paused
                                        if (frame == 35) playback=Playback.Running
                                        if (frame == 45) playback=Playback.Stopped
                                        if (frame == 50) playback=Playback.Running
                                        if(frame==55 || frame==56) engineSession.latestFrame(sle.editor.viewport.ViewId.Game)?.let { view ->
                                            engineSession.enqueueInput(sle.editor.viewport.ViewInput(sle.editor.viewport.ViewId.Game,view.request.generation,0,292,if(frame==55) 1.0 else 0.0))
                                        }
                                        if (frame >= 60) {
                                            try { ViewportSmokeChecks.verifyDesktopSandbox(canvas ?: error("Viewport missing")) }
                                            catch (failure: Throwable) { failure.printStackTrace(); exitProcess(1) }
                                            watchdog?.stop(); exitApplication()
                                        }
                                    }
                                    if (previewSmoke && frame == 10) authoring.getOrThrow().apply {
                                        editField("BoxCollider",listOf("size","x"),JsonPrimitive(1.0),"4")
                                        editField("BoxCollider",listOf("size","y"),JsonPrimitive(1.0),"1")
                                    }
                                    if (switchingSmoke) {
                                        if (frame == 19) authoring.getOrThrow().editField("Transform",listOf("rotation"),JsonPrimitive(0.0),"-")
                                        if (frame == 20 || frame == 40) selectedTab=1
                                        if (frame == 30 || frame == 50) selectedTab=0
                                        if ((frame == 28 || frame == 48) && renderedMode == ViewportMode.Sandbox) {
                                            try { ViewportSmokeChecks.verifyDesktopSandbox(canvas ?: error("Viewport missing")) }
                                            catch (failure: Throwable) { failure.printStackTrace(); exitProcess(1) }
                                        }
                                        if (frame >= 60 && renderedMode == ViewportMode.Entity) {
                                            check(authoring.getOrThrow().document.key == initialIdentity)
                                            check(authoring.getOrThrow().drafts["Transform/rotation"] == "-")
                                            println("ENTITY_SANDBOX_SWITCH_SMOKE_PASS")
                                            watchdog?.stop(); exitApplication()
                                        }
                                    }
                                }
                engineSession.setProject(desiredProject)
                engineSession.owner.consumeSceneStep=scenePlayback::consumeStep
                engineSession.owner.onLuaErrorPause=scenePlayback::pause
                engineSession.setContent(mode, desiredSource, desiredPlayback, scenePlayback.keepChangesRevision)
                engineSession.setScene(if(mode==ViewportMode.Scene || linkedEntity!=null) scene.getOrNull() else null)
                engineSession.setAuthoring(inspector)
                engineSession.setSceneView(desiredGame) { message -> scenePlayback.stop();scene.getOrNull()?.error=message }
                engineSession.owner.onContextMenu=editorMenuHandler
                val entityDrags=project.opened!=null && scene.getOrNull()?.projectCreatePrefab!=null
                engineSession.owner.entityDragMove=if(!entityDrags) null else { key,point -> entityPayload(key)?.let { sle.editor.ui.EntityDrag.move(it,point) } }
                engineSession.owner.entityDragOver={ point -> sle.editor.ui.EntityDrag.over(point) }
                engineSession.owner.entityDrop={ key,point -> entityPayload(key)?.let { sle.editor.ui.EntityDrag.move(it,point);sle.editor.ui.EntityDrag.release(point) } }
                engineSession.owner.entityDragCancel={ sle.editor.ui.EntityDrag.cancel() }
                engineSession.start()
            }
            @OptIn(ExperimentalLayoutApi::class)
            @Composable fun viewportPanel(game: Boolean,entity: Boolean) {
                var panelCanvas by remember { mutableStateOf<EngineCanvas?>(null) }
                val m=LocalEditorMetrics.current
                Column(Modifier.fillMaxSize()) {
                    if(entity && mode!=ViewportMode.Entity) {
                        sle.editor.ui.controls.EditorEmptyState("Entity component preview","Open the Entity Editor to preview entity template components.") {
                            EditorButton("Open Entity Editor",{switchTab(0)})
                        }
                    } else if(!entity && mode==ViewportMode.Entity) {
                        sle.editor.ui.controls.EditorEmptyState(if(game) "Game view" else "Scene view","The entity template workspace is active.") {
                            EditorButton(if(game) "Show Game" else "Show Scene",{showScene(game)})
                        }
                    } else {
                        // The heavyweight viewport covers Compose popups that extend below the toolbar.
                        sle.editor.ui.controls.EditorToolbar(tooltipsAbove = true) {
                            if (project.opened != null && !entity) {
                                val command = sle.editor.command.PlayCommands.runProject
                                EditorButton(command.label,
                                    onClick = { liveCommandContext().let { if (command.enabled(it)) command.run(it) } },
                                    enabled = command.enabled(commandContext()),
                                    help = keymap.hint(command.id),
                                    modifier = Modifier.onGloballyPositioned { if(documentSmoke || runtimeSmoke) assetBounds["RunProject"]=it.boundsInWindow() })
                            }
                            if (mode == ViewportMode.Scene) SceneViewControls({ liveCommandContext() }, keymap)
                            if(mode==ViewportMode.Sandbox) SandboxControls(playback,!failed) { playback=it }
                            if(!game) Row(horizontalArrangement=Arrangement.spacedBy(m.borderWidth)) {
                                if((project.opened!=null && !entity) || mode==ViewportMode.Scene || mode==ViewportMode.Sandbox) sle.editor.ui.controls.EditorToolbarDivider()
                                sle.editor.ui.controls.EditorGlyphButton("Zoom in",{panelCanvas?.zoomEntity(.8f)},modifier=Modifier.onGloballyPositioned { if(dockingSmoke && !entity) shellBounds["SceneZoomIn"]=it.boundsInWindow() }) { tint ->
                                    sle.editor.ui.controls.EditorGlyphIcon(sle.editor.ui.controls.EditorGlyph.Plus,tint) }
                                sle.editor.ui.controls.EditorGlyphButton("Zoom out",{panelCanvas?.zoomEntity(1.25f)}) { tint ->
                                    sle.editor.ui.controls.EditorGlyphIcon(sle.editor.ui.controls.EditorGlyph.Minus,tint) }
                                EditorButton("Fit",{panelCanvas?.fitEntity()},help="Fit content",style=sle.editor.ui.controls.EditorButtonStyle.Ghost)
                            }
                            if(!game) {
                                // Scene tools (Q/W) in the Scene view; snapping also applies to the prefab workspace's handles.
                                sle.editor.ui.controls.EditorToolbarDivider()
                                sle.editor.ui.ViewportToolControls(if(!entity && mode==ViewportMode.Scene) workspace.tools else null,workspace.snap,
                                    onConfigure={ snapDialog=true },onToolBounds={ id,bounds -> if(sceneSmoke) shellBounds["Tool"+id.name]=bounds })
                            }
                            if (!entity && !game && mode == ViewportMode.Scene) {
                                val opened = project.opened
                                val state = scene.getOrNull()
                                if (opened != null && state != null) {
                                    val command = sle.editor.command.ToolCommands.tiles
                                    EditorButton("Tile Palette", {
                                        liveCommandContext().let { if (command.enabled(it)) command.run(it) }
                                        manager.showPanel(PanelId("tilePalette"))
                                    }, enabled = command.enabled(commandContext()) && !project.busy,
                                        help = keymap.hint(command.id, "Open the tile palette"),
                                        modifier = Modifier.onGloballyPositioned { if (tileSmoke) shellBounds["Tile Palette"] = it.boundsInWindow() },
                                        primary = workspace.tools.active == sle.editor.viewport.tools.ViewportToolId.Tiles)
                                    val activeLayer = workspace.tools.tilePainting.layerId?.let(state::tileLayer)
                                    if (workspace.tools.active == sle.editor.viewport.tools.ViewportToolId.Tiles) {
                                        if (activeLayer?.locked == true) EditorLabel("${activeLayer.name} is locked")
                                        else if (activeLayer?.visible == false) EditorLabel("${activeLayer.name} is hidden")
                                    }
                                }
                            }
                            if(!game && scenePlayback.editing) inspector?.let {
                                sle.editor.ui.controls.EditorToolbarDivider()
                                GizmoControls(it,entityOnly=!entity)
                            }
                        }
                        sle.editor.ui.controls.EditorSeparator()
                        if(failed) EditorLabel(status,Modifier.padding(horizontal=m.panelPadding,vertical=m.gap),color=MaterialTheme.colors.error)
                        if(!ready && !failed) LinearProgressIndicator(Modifier.fillMaxWidth())
                        if(inspector?.fieldErrors?.isNotEmpty()==true) EditorLabel("Finish the highlighted fields. Preview keeps the last valid values.",Modifier.padding(horizontal=m.panelPadding,vertical=m.gap),color=MaterialTheme.colors.error)
                        if(!entity && !game) DisposableEffect(panelCanvas) {
                            val view=panelCanvas
                            val key=Any()
                            if(view!=null) sle.editor.ui.AssetDrag.register(key,sle.editor.ui.AssetDrag.Target({
                                if(view.isShowing) java.awt.Rectangle(view.locationOnScreen,view.size) else null
                            }) { path,point ->
                                val origin=view.locationOnScreen
                                val world=view.previewCamera()?.world(sle.editor.viewport.Vec2((point.x-origin.x).toDouble(),(point.y-origin.y).toDouble()))
                                // Snapping places the drop on the move step; otherwise on whole units as before.
                                dropAsset(path,world?.let { if(workspace.snap.active()) workspace.snap.placement(it) else sle.editor.viewport.Vec2(Math.round(it.x).toDouble(),Math.round(it.y).toDouble()) })
                            })
                            onDispose { sle.editor.ui.AssetDrag.unregister(key) }
                        }
                        androidx.compose.ui.awt.SwingPanel(modifier=Modifier.weight(1f).fillMaxWidth(),
                            factory={EngineCanvas(engineSession,if(entity) ViewId.Entity else if(game || mode==ViewportMode.Sandbox) ViewId.Game else ViewId.Scene).also {
                                panelCanvas=it;canvas=it
                                // The heavyweight canvas owns AWT focus; scoped shortcuts need its panel.
                                manager.focus.register(it,PanelId(if(entity) "entity" else if(game) "game" else "scene"))
                            }},
                            update={ view -> if((entity && mode==ViewportMode.Entity) || (!entity && game==scenePlayback.gameView)) canvas=view },
                        )
                    }
                }
            }
            Surface(Modifier.fillMaxSize(),color=sle.editor.ui.theme.LocalEditorColors.current.background) {
                Column {
                    val context = commandContext()
                    val baseMenus=editorApplicationMenus(context,onScale={uiScale=it},keymap=keymap,
                        onTheme={ workspace.appearance.update(appearance.copy(mode=it)) },
                        onDensity={ workspace.appearance.update(appearance.copy(density=it)) },
                        onExit={if(manager.requestExit { !project.busy && confirmDiscard(authoring.getOrNull()) && confirmDiscardScene(scene.getOrNull()) && workspace.saveProjectEditorState() }) exitApplication()},
                        projectEntries=projectFileMenu(project,commands,scenePlayback.editing)+listOf(
                            sle.editor.ui.EditorMenuEntry("Export Game…",project.opened!=null && !project.busy,action=distribution.exportGame)),
                        projectAssets=assetCommands,projectDocuments=documents,
                        assetEntries=assetBrowserMenu(assets,assetBrowserVisible) { if(assetBrowserVisible) manager.hidePanel(PanelId("assets")) else manager.showPanel(PanelId("assets")) },
                    ) + windowsMenu(manager) { panel ->
                        when (panel.value) {
                            "entity" -> workspace.prefabSession
                            "tilePalette" -> tileProject != null && tileScene != null
                            else -> false
                        }
                    }.let { (label, entries) ->
                        label to (entries + scriptPanels.map { panel ->
                            sle.editor.ui.EditorMenuEntry(panel.label) {
                                if (currentScripts === projectScripts && currentScripts.trusted == true) {
                                    val existing = scriptWindows[panel.id]
                                    if (existing != null) existing.toFront()
                                    else if (panel.id !in openScriptPanels) openScriptPanels.add(panel.id)
                                }
                            }
                        })
                    }
                    val scriptMenus = scripts.groupBy { it.script.menu }.mapValues { (_, entries) ->
                        entries.map { it.command.menuEntry(context, keymap, liveContext = { liveCommandContext() }) }
                    }
                    val applicationMenus = baseMenus.map { (label, entries) ->
                        label to (entries + scriptMenus[label]?.let { listOf(sle.editor.ui.EditorMenuEntry.Separator) + it }.orEmpty())
                    } + scriptMenus.filterKeys { label -> baseMenus.none { it.first == label } }.toList()
                    SideEffect { saveActive=applicationMenus.first { it.first=="File" }.second.first { it.label=="Save" }.action }
                    EditorMenuStrip(applicationMenus,window=window,onMenuBounds={label,bounds -> if(menuSmoke || walkSmoke) shellBounds[label]=bounds},title=(if(project.busy) "Project busy… — " else project.opened?.let { "${it.document.name} — " } ?: "")+
                        if(mode==ViewportMode.Entity) (activeEntity?.document?.name ?: "Entity Template")+(if(activeEntity?.dirty==true) " *" else "")
                        else (scene.getOrNull()?.name ?: "Scene")+(if(scene.getOrNull()?.dirty==true) " *" else ""))
                    val panels=WorkspacePanels(mapOf(
                        PanelId("hierarchy") to {
                            if (mode == ViewportMode.Scene && !scenePlayback.editing) {
                                sle.editor.ui.LiveHierarchy(engineSession.owner.liveInspection, engineSession.owner::selectLiveEntity)
                            } else Box(Modifier.fillMaxSize().assetDropTarget { path,_ -> dropAsset(path,null) }) {
                                if(mode==ViewportMode.Scene) scene.getOrNull()?.let { state ->
                                    SceneWorkspace(state,onEditEntity={editSceneEntity(it)},onEntityBounds={key,bounds -> if(sceneSmoke || menuSmoke || documentSmoke || walkSmoke) treeNodeBounds[key]=bounds},editable=scenePlayback.editing)
                                } else if(linkedEntity!=null) SceneTree(scene.getOrThrow(),Modifier.fillMaxSize(),onEditEntity={editSceneEntity(it)})
                                else activeEntity?.let { EntityOutline(it,Modifier.fillMaxSize()) }
                            }
                        },
                        PanelId("inspector") to {
                            if (mode == ViewportMode.Scene && !scenePlayback.editing) {
                                val owner = engineSession.owner
                                val authored = scene.getOrNull()
                                val preview = authored?.let { scenePlayback.previewKeepChanges(it, owner.liveInspection) }
                                sle.editor.ui.LiveDetails(owner.liveInspection, authoring.getOrNull()?.catalog.orEmpty(), preview, owner::reloadLiveScript) { shown ->
                                    scenePlayback.keepChanges(checkNotNull(scene.getOrNull()), owner.readPausedPlaySelection(), shown)
                                }
                            } else CompositionLocalProvider(
                                LocalProjectDrawers provides projectScripts.declarations.drawers,
                                sle.editor.ui.inspector.LocalDetailsContext provides { liveCommandContext() },
                                sle.editor.ui.inspector.LocalSortingLayers provides
                                    project.opened?.document?.settings?.sortingLayers.orEmpty(),
                                sle.editor.ui.inspector.LocalCollisionLayers provides
                                    (project.opened?.document?.settings?.collisionLayers ?: sle.editor.project.CollisionLayers()),
                            ) {
                                if(mode==ViewportMode.Scene) scene.getOrNull()?.let { SceneEntityInspector(it,editable=scenePlayback.editing,tags=project.opened?.document?.settings?.tags.orEmpty(),onNameBounds={ bounds -> if(menuSmoke) shellBounds["SceneNameField"]=bounds }) }
                                else Column(Modifier.fillMaxSize()) {
                                    activeEntity?.let { state ->
                                        Box(Modifier.weight(1f).fillMaxWidth().onGloballyPositioned { if(menuSmoke) entityMenuBounds[0]=it.boundsInWindow() }) { EntityWorkspace(state,embedded=linkedEntity!=null,tags=project.opened?.document?.settings?.tags.orEmpty()) }
                                    }
                                }
                            }
                        },
                        PanelId("assets") to {
                            if(assets==null) EditorEmptyState("No project open","Open or create a project to browse its assets.",brand=true)
                            else AssetBrowserPanel(assets,project.opened?.document?.assetRoot ?: "Assets",Modifier.fillMaxSize(),commands=assetCommands,onOpen={ relative -> project.opened?.let { documents?.openPath?.invoke(it.root.resolve(it.document.assetRoot).resolve(relative)) } },
                                dragPath={ relative -> project.opened?.let { it.root.resolve(it.document.assetRoot).resolve(relative) } },onBounds={id,bounds -> if(assetSmoke || documentSmoke || runtimeSmoke) assetBounds[id]=bounds},
                                onOpenScript={ relative, id -> openScriptSource { scriptSources.openAsset(relative, id) } },
                                tileLayerScales = { id ->
                                    scene.getOrNull()?.tileLayerDocuments().orEmpty()
                                        .filter { it.tileset == "project-asset://$id" }.map { it.pixelsPerUnit }
                                },
                                entityDrop=if(scene.getOrNull()?.projectCreatePrefab==null) null else { relative,payload ->
                                    project.opened?.let { dropEntity(it.root.resolve(it.document.assetRoot).resolve(relative),payload) }
                                })
                        },
                        PanelId("tilePalette") to {
                            when {
                                tileProject == null || tileScene == null ->
                                    EditorEmptyState("No project scene", "Open a project scene to paint tiles.")
                                !scenePlayback.editing || mode != ViewportMode.Scene || project.busy ->
                                    EditorEmptyState("Tile painting unavailable", "Switch to an editable Scene view to paint tiles.")
                                else -> sle.editor.ui.TilePaintingControls(tileScene, tileProject, assets?.snapshot,
                                    tileOptions, workspace.tools, activate = {
                                        workspace.tools.activate(sle.editor.viewport.tools.ViewportToolId.Tiles)
                                    })
                            }
                        },
                        PanelId("console") to {
                            val consoleOwner = workspace.engineSession.owner
                            sle.editor.console.ConsolePanel(consoleOwner.console, consoleOwner::clearConsole,
                                canRun = selectedTab == 2 && scene.getOrNull() != null && !project.busy,
                                submit = { submitLua(it) }, onOpenSource = openLuaSource, onConfigureEditor = {
                                    scriptEditorSettings = runCatching { scriptEditorStore.load() }
                                        .onFailure { notices.warning(it.message ?: "Could not read script editor settings.") }
                                        .getOrNull()
                                    scriptEditorDialog = true
                                })
                        },
                        PanelId("luaProfiler") to {
                            val owner = workspace.engineSession.owner
                            sle.editor.profiler.LuaProfilerPanel(owner.luaProfiler, owner.liveInspection,
                                onEntity = {
                                    owner.selectLiveEntity(it)
                                    manager.showPanel(PanelId("inspector"))
                                }, onOpenSource = openLuaSource)
                        },
                        PanelId("scene") to { viewportPanel(false,false) },
                        PanelId("game") to { viewportPanel(true,false) },
                        PanelId("entity") to { viewportPanel(false,true) },
                    ), manager.registry)
                    DockSurface(manager.layout.hosts.single { it.id==manager.layout.mainHost },manager,uiScale,Modifier.weight(1f).fillMaxWidth(),
                        onBounds={id,bounds -> if(menuSmoke || cameraSmoke || walkSmoke) shellBounds[when(id.value) { "scene" -> "SceneTab"; "game" -> "GameTab"; else -> id.value }]=bounds}) { panels.Render(it) }
                    // Main window only: detached windows report through this bar too.
                    val sceneState=scene.getOrNull()
                    sle.editor.ui.EditorStatusBar(notices,sle.editor.ui.EditorStatus(
                        hint=when {
                            mode==ViewportMode.Entity -> "Entity template workspace: drag a handle to edit a shape; Esc cancels the drag"
                            mode==ViewportMode.Sandbox -> "Sandbox: the game view receives keys while it has focus"
                            scenePlayback.editing && scenePlayback.gameView -> "Game view: "+sle.editor.command.standardKeymap.hint(sle.editor.command.PlayCommands.play.id,"play")+" to run the scene"
                            scenePlayback.playback==Playback.Paused && engineSession.owner.luaPauseMessage!=null ->
                                "Paused on Lua error • ${engineSession.owner.luaPauseMessage}"
                            !scenePlayback.editing -> "Play mode: scene edits are disabled; "+sle.editor.command.standardKeymap.hint(sle.editor.command.PlayCommands.stop.id,"stop")+" to edit"
                            workspace.tools.active == sle.editor.viewport.tools.ViewportToolId.Tiles ->
                                workspace.tools.gestureHint ?: workspace.tools.tilePainting.mode.help
                            else -> workspace.tools.gestureHint ?: sle.editor.viewport.tools.toolHint(workspace.tools.active,
                                sle.editor.viewport.tools.ToolHintContext(workspace.snap.settings,sceneState?.selection?.size ?: 0,workspace.tools.pivot))
                        },
                        progress=if(distribution.exporting) "Exporting game…" else if(project.busy) "Working…" else null,
                        selectionCount=if(mode==ViewportMode.Scene) sceneState?.selection?.size ?: 0 else 0,
                        playState=when(desiredPlayback) { Playback.Running -> "Playing"; Playback.Paused -> "Paused"; else -> if(mode==ViewportMode.Scene) "Editing" else null },
                        dirty=if(mode==ViewportMode.Entity) activeEntity?.dirty==true else sceneState?.dirty==true,
                    ),Modifier.onGloballyPositioned { if(appearanceSmoke || menuSmoke || dockingSmoke || walkSmoke) shellBounds["StatusBar"]=it.boundsInWindow() })
                    scriptCreation?.let { request ->
                        ScriptCreationDialog(request, creatingScript, scriptCreationError, onDismiss = { scriptCreation = null }) { destination, template ->
                            if (!creatingScript) {
                                creatingScript = true
                                scriptCreationError = null
                                creationScope.launch {
                                    try {
                                        request.requireCurrent()
                                        val source = withContext(Dispatchers.IO) { request.create(destination, template) }
                                        runCatching { request.attach(source) }.onFailure {
                                            notices.warning("Created ${source} without an attachment. ${it.message.orEmpty()}")
                                        }
                                        scriptCreation = null
                                    } catch (failure: CancellationException) {
                                        throw failure
                                    } catch (failure: Exception) {
                                        scriptCreationError = failure.message ?: "Could not create the Lua file."
                                    } finally {
                                        currentScriptAssets?.refresh()
                                        creatingScript = false
                                    }
                                }
                            }
                        }
                    }
                    if (scriptEditorDialog) sle.editor.scripts.ScriptEditorSettingsDialog(
                        scriptEditorSettings, onDismiss = { scriptEditorDialog = false },
                        onSave = {
                            scriptEditorStore.save(it)
                            scriptEditorDialog = false
                        },
                        onReset = {
                            runCatching { scriptEditorStore.reset() }
                                .onSuccess { scriptEditorDialog = false }
                                .onFailure { notices.warning(it.message ?: "Could not reset script editor settings.") }
                        },
                        onFailure = { notices.warning(it) },
                    )
                    if(snapDialog) sle.editor.ui.SnapSettingsDialog(workspace.snap.settings,onDismiss={ snapDialog=false }) { workspace.snap.update(it);snapDialog=false }
                    scriptPanels.filter { it.id in openScriptPanels }.forEach { panel ->
                        key(projectScripts, panel.id) {
                            Window(
                                icon = appIcon,
                                title = panel.label,
                                onCloseRequest = { openScriptPanels.remove(panel.id) },
                                state = rememberWindowState(width = 360.dp, height = 260.dp),
                            ) {
                                DisposableEffect(window) {
                                    scriptWindows[panel.id] = window
                                    onDispose { scriptWindows.remove(panel.id) }
                                }
                                EditorTheme(appearance) {
                                    ProjectLuaPanelContent(panel, commandContext()) { liveCommandContext() }
                                }
                            }
                        }
                    }
                    manager.layout.hosts.filter { it.id!=manager.layout.mainHost }.forEach { host ->
                        key(host.id) {
                            Window(icon=appIcon,visible=!manager.loading,onCloseRequest={manager.closeHost(host.id)},title="SLE — "+host.root?.let { root -> root.tabs().flatMap { it.panels }.joinToString(" / ") { manager.registry.byId.getValue(it).title } },
                                state=rememberWindowState(position=androidx.compose.ui.window.WindowPosition(host.bounds.x.dp,host.bounds.y.dp),width=host.bounds.width.dp,height=host.bounds.height.dp)) {
                                DisposableEffect(window) {
                                    manager.bind(host.id,window)
                                    onDispose { manager.unbind(host.id,window) }
                                }
                                EditorTheme(appearance) { Surface(Modifier.fillMaxSize()) { DockSurface(host,manager,uiScale,Modifier.fillMaxSize()) { panels.Render(it) } } }
                            }
                        }
                    }

                }
            }
        }
        }
    }
}
