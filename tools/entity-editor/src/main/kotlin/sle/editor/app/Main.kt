package sle.editor.app

import sle.editor.workspace.*
import sle.editor.viewport.ViewId
import sle.editor.project.assets.LocalProjectAssets
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
import sle.editor.smoke.runCameraPlaySmoke
import sle.editor.ui.editorMenuLauncher
import sle.editor.ui.sceneCreationMenu
import sle.editor.ui.entityCreationMenu
import sle.editor.ui.EditorMenuStrip
import sle.editor.ui.editorApplicationMenus
import sle.editor.ui.EditorTheme
import sle.editor.ui.EntityWorkspace
import sle.editor.ui.GizmoControls
import sle.editor.ui.SandboxControls
import sle.editor.ui.SceneWorkspace
import sle.editor.ui.SceneEntityInspector
import sle.editor.ui.SceneTree
import sle.editor.ui.EntityOutline
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
import kotlinx.serialization.json.JsonPrimitive
import javax.swing.Timer
import java.awt.Robot
import java.awt.Rectangle
import java.io.File
import javax.imageio.ImageIO
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    if("--layout-persistence-smoke" in args) { sle.editor.smoke.runWorkspacePersistenceSmoke();return }
    if("--layout-persistence-phase" in args) System.setProperty("sle.workspaceData",java.nio.file.Path.of(args[2],"standalone").toString())
    if("--docking-workspace-smoke" in args) System.setProperty("sle.workspaceData",java.nio.file.Files.createTempDirectory("sle-docking-layout").toString())
    if ("--owner-failure-smoke" in args) { sle.editor.smoke.runMultiViewportSmoke(ownerFailure=true); return }
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
    val projectSmoke="--project-smoke" in args
    val sandboxSmoke="--smoke-test" in args
    val authoringUiSmoke="--authoring-ui-smoke" in args
    val switchingSmoke="--preview-smoke" in args
    val gizmoSmoke="--gizmo-smoke" in args
    val sceneSmoke="--scene-smoke" in args
    val menuSmoke="--context-menu-smoke" in args
    val cameraSmoke="--camera-smoke" in args
    val previewSmoke=authoringUiSmoke || switchingSmoke
    val testing=layoutPhase || dockingSmoke || sandboxSmoke || previewSmoke || gizmoSmoke || sceneSmoke || cameraSmoke || menuSmoke || projectSmoke || assetSmoke || documentSmoke || runtimeSmoke
    val windowState=rememberWindowState(width=1280.dp,height=900.dp)
    val authoring=remember {
        runCatching { EntityWorkspaceState(readComponentCatalog()).apply {
            if (previewSmoke || gizmoSmoke) {
                document=document.copy(name="Collider Preview")
                addComponent("BoxCollider")
                editField("BoxCollider",listOf("size","x"),JsonPrimitive(1.0),"2")
                editField("BoxCollider",listOf("size","y"),JsonPrimitive(1.0),"4")
                if (authoringUiSmoke || gizmoSmoke) {
                    addComponent("SpriteRenderer")
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
        if(sceneSmoke || menuSmoke || dockingSmoke) createStarterScene(this,workspace.catalog,java.nio.file.Path.of(System.getProperty("sle.smokeReport")).parent)
        if(cameraSmoke) createCameraScene(this,workspace.catalog,java.nio.file.Path.of(System.getProperty("sle.smokeReport")).parent)
    } } }
    val workspace=remember { EditorWorkspace(authoring,scene,sandboxSmoke,previewSmoke) }
    remember { if(!testing || dockingSmoke || layoutPhase) workspace.startLayoutPersistence();true }
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
    var status by remember { mutableStateOf("Preparing engine viewport…") }

    var ready by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var readyFrames by remember { mutableStateOf(0) }
    var lastRenderedMode by remember { mutableStateOf<ViewportMode?>(null) }
    val mode=when(selectedTab) { 0 -> ViewportMode.Entity; 1 -> ViewportMode.Sandbox; else -> ViewportMode.Scene }
    val inspector=if(mode==ViewportMode.Scene) scene.getOrNull()?.selected else activeEntity
    fun switchTab(index: Int) {
        if(index!=2) scenePlayback.stop()
        selectedTab=index
        manager.showPanel(PanelId(if(index==0) "entity" else if(index==1) "game" else "scene"))
    }
    fun editSceneEntity(key: String) {
        canvas?.finishGesture();scene.getOrNull()?.select(key);editingSceneKey=key;switchTab(0)
    }
    fun showScene(game: Boolean) {
        canvas?.finishGesture();selectedTab=2;scenePlayback.gameView=game
        manager.showPanel(PanelId(if(game) "game" else "scene"))
    }
    LaunchedEffect(Unit) {
        if("--simultaneous-views" in args) {
            val group=manager.layout.tabs().single { PanelId("scene") in it.panels }.group
            manager.movePanel(PanelId("game"),DockTarget.Split(group,DockEdge.Right))
        }
    }
    LaunchedEffect(selectedTab) { manager.showPanel(PanelId(if(selectedTab==0) "entity" else if(selectedTab==1) "game" else if(scenePlayback.gameView) "game" else "scene")) }
    LaunchedEffect(selectedTab,scene.getOrNull()?.selectedKey) {
        if(selectedTab==0 && editingSceneKey!=null) {
            val selected=scene.getOrNull()?.selectedKey
            if(selected==null) switchTab(2) else editingSceneKey=selected
        }
    }
    val watchdog=remember {
        if (testing) Timer(if(documentSmoke || distributionSmoke || dockingSmoke) 90_000 else 30_000) {
            System.err.println("EDITOR_SMOKE_FAIL: viewport timed out")
            exitProcess(1)
        }.apply { isRepeats=false; start() } else null
    }
    Window(
        onCloseRequest={ if (manager.requestExit { (testing && !dockingSmoke) || (confirmDiscard(authoring.getOrNull()) && confirmDiscardScene(scene.getOrNull())) }) { watchdog?.stop(); exitApplication() } },
        title=project.opened?.let { "SLE Editor — ${it.document.name}" } ?: "SLE Entity Editor",
        state=windowState,
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
            onDispose { manager.unbind(manager.layout.mainHost,window) }
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
                        {observedAssets},assetBounds,projectFields,scene.getOrThrow(),scenePlayback,distributionSmoke)
                    watchdog?.stop();exitApplication()
                } catch(failure: Throwable) { canvas?.stopRendering();failure.printStackTrace();exitProcess(1) }
            }
            if(documentSmoke) {
                try {
                    window.isAlwaysOnTop=true;window.toFront()
                    while(readyFrames<5 || projectActions==null) delay(30)
                    sle.editor.smoke.runProjectDocumentsSmoke(window,{canvas ?: error("Viewport missing")},project,projectActions!!,
                        {observedDocumentCommands},{observedAssets},assetBounds,scene.getOrThrow(),authoring.getOrThrow(),scenePlayback)
                    watchdog?.stop();exitApplication()
                } catch(failure: Throwable) { canvas?.stopRendering();failure.printStackTrace();exitProcess(1) }
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
                    runProjectSmoke(window,project,projectActions!!,scene.getOrThrow(),projectFields)
                    watchdog?.stop();exitApplication()
                } catch(failure: Throwable) { canvas?.stopRendering();failure.printStackTrace();exitProcess(1) }
            }
            if(menuSmoke) {
                try {
                    window.isAlwaysOnTop=true;window.toFront()
                    while(readyFrames<5) delay(50)
                    runContextMenuSmoke(window,{canvas ?: error("Viewport missing")},scene.getOrThrow(),treeNodeBounds,{entityMenuBounds[0]},shellBounds)
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
                    runSceneSmoke(canvas ?: error("Viewport missing"),scene.getOrThrow()) { key ->
                        clickUi(treeNodeBounds[key] ?: error("Tree node not laid out: $key"))
                    }
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
                    while(lastRenderedMode!=ViewportMode.Entity || canvas?.authoringReady()!=true) delay(30)
                    check(editingSceneKey==originalEntityKey)
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
                    runGizmoSmoke(canvas ?: error("Viewport missing"),authoring.getOrThrow())
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
        EditorTheme(uiScale) {
            val assets=rememberAssetBrowser(project.opened,window)
            SideEffect { if(assetSmoke || documentSmoke || runtimeSmoke) observedAssets=assets }
            val commands=projectControls(project,
                confirmSwitch={confirmDiscard(authoring.getOrNull()) && confirmDiscardScene(scene.getOrNull())},
                resetWorkspace={
                    canvas?.finishGesture();scenePlayback.stop();authoring.getOrNull()?.newDocument();scene.getOrNull()?.newScene()
                    editingSceneKey=null;selectedTab=2
                },workspaceRevision={
                    listOf(authoring.getOrNull()?.let { Triple(it.document,it.assets,it.drafts) },scene.getOrNull()?.document(),
                        scene.getOrNull()?.entities?.map { Triple(it.document,it.assets,it.drafts) })
                },uiScale={uiScale},setUiScale={uiScale=it},
                startupScenes=assets?.snapshot?.pathsById?.entries?.filter { it.value.endsWith(".scene.json",true) }?.map { it.key to it.value }?.sortedBy { it.second } ?: emptyList(),onBounds={id,bounds -> if(projectSmoke || runtimeSmoke) projectFields[id]=bounds})
            SideEffect { projectActions=commands }
            LaunchedEffect(requestedProject) { requestedProject?.let { commands.openPath(java.nio.file.Path.of(it)) } }
            val documents=sle.editor.project.projectDocumentControls(project,assets,scene.getOrNull(),authoring.getOrNull(),
                editable={scenePlayback.editing},onOpen={ kind ->
                    canvas?.finishGesture()
                    if(kind==sle.editor.project.ProjectDocumentKind.Scene) { editingSceneKey=null;showScene(false) }
                    else { editingSceneKey=null;switchTab(0) }
                })
            SideEffect { if(documentSmoke) observedDocumentCommands=documents }
            val distribution=sle.editor.project.projectDistributionControls(project)
            val runProject=sle.editor.project.projectRunAction(project,scenePlayback) { canvas?.finishGesture();editingSceneKey=null;showScene(true) }
            val assetCommands=projectAssetControls(project,assets,window,canMutate={
                scenePlayback.editing && scene.getOrNull()?.let { it.file==null && !it.dirty && it.entities.isEmpty() && it.allAssets().isEmpty() }!=false &&
                    authoring.getOrNull()?.let { it.file==null && !it.dirty && it.assets.isEmpty() }!=false
            })
            SideEffect { if(assetSmoke) observedAssetCommands=assetCommands }
            CompositionLocalProvider(LocalProjectAssets provides assetCommands) {
            val editorMenuHandler=editorMenuLauncher {
                if(mode==ViewportMode.Scene) scene.getOrNull()?.let { sceneCreationMenu(it,scenePlayback.editing) { key -> editSceneEntity(key) } } ?: emptyList()
                else activeEntity?.let { entityCreationMenu(it,linkedEntity!=null,assetCommands) } ?: emptyList()
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
                engineSession.setContent(mode,desiredSource,desiredPlayback)
                engineSession.setScene(if(mode==ViewportMode.Scene || linkedEntity!=null) scene.getOrNull() else null)
                engineSession.setAuthoring(inspector)
                engineSession.setSceneView(desiredGame) { message -> scenePlayback.stop();scene.getOrNull()?.error=message }
                engineSession.owner.onContextMenu=editorMenuHandler
                engineSession.start()
            }
            @Composable fun viewportPanel(game: Boolean,entity: Boolean) {
                var panelCanvas by remember { mutableStateOf<EngineCanvas?>(null) }
                Column(Modifier.fillMaxSize(),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                    if(entity && mode!=ViewportMode.Entity) {
                        Text("Entity component preview")
                        Button(onClick={switchTab(0)}) { Text("Open Entity Editor") }
                    } else if(!entity && mode==ViewportMode.Entity) {
                        Button(onClick={showScene(game)}) { Text(if(game) "Show Game" else "Show Scene") }
                    } else {
                        if(project.opened!=null && !entity) Button(onClick=runProject,enabled=!project.busy && scenePlayback.editing && !failed,
                            modifier=Modifier.onGloballyPositioned { if(documentSmoke || runtimeSmoke) assetBounds["RunProject"]=it.boundsInWindow() }) { Text("Run Project") }
                        if(mode==ViewportMode.Scene) SceneViewControls(scenePlayback,!failed,onPlay={
                            try { canvas?.finishGesture();scenePlayback.play(scene.getOrThrow());scene.getOrNull()?.error=null;manager.showPanel(PanelId("game")) }
                            catch(failure: Exception) { scene.getOrNull()?.error=failure.message }
                        },onStop={scenePlayback.stop()})
                        if(mode==ViewportMode.Sandbox) SandboxControls(playback,!failed) { playback=it }
                        if(!game) Row {
                            OutlinedButton(onClick={panelCanvas?.zoomEntity(.8f)},modifier=Modifier.onGloballyPositioned { if(dockingSmoke && !entity) shellBounds["SceneZoomIn"]=it.boundsInWindow() }) { Text("+") }
                            OutlinedButton(onClick={panelCanvas?.zoomEntity(1.25f)}) { Text("−") }
                            OutlinedButton(onClick={panelCanvas?.fitEntity()}) { Text("Fit") }
                        }
                        if(!game && scenePlayback.editing) inspector?.let { GizmoControls(it,entityOnly=!entity) }
                        if(failed) Text(status,color=MaterialTheme.colors.error)
                        if(!ready && !failed) LinearProgressIndicator(Modifier.fillMaxWidth())
                        if(inspector?.fieldErrors?.isNotEmpty()==true) Text("Finish the highlighted fields. Preview keeps the last valid values.",color=MaterialTheme.colors.error)
                        androidx.compose.ui.awt.SwingPanel(modifier=Modifier.weight(1f).fillMaxWidth(),
                            factory={EngineCanvas(engineSession,if(entity) ViewId.Entity else if(game || mode==ViewportMode.Sandbox) ViewId.Game else ViewId.Scene).also { panelCanvas=it;canvas=it }},
                            update={ view -> if((entity && mode==ViewportMode.Entity) || (!entity && game==scenePlayback.gameView)) canvas=view },
                        )
                    }
                }
            }
            Surface(Modifier.fillMaxSize()) {
                Column(Modifier.padding(6.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                    val applicationMenus=editorApplicationMenus(
                        scene.getOrNull(),inspector,authoring.getOrNull(),mode==ViewportMode.Entity,linkedEntity!=null,scenePlayback.editing,
                        onScene={showScene(it)},onEntity={key -> if(key==null) { editingSceneKey=null;switchTab(0) } else editSceneEntity(key) },
                        onFit={canvas?.fitEntity()},onScale={uiScale=it},
                        onExit={if(manager.requestExit { confirmDiscard(authoring.getOrNull()) && confirmDiscardScene(scene.getOrNull()) }) exitApplication()},
                        projectEntries=projectFileMenu(project,commands,scenePlayback.editing)+listOf(
                            sle.editor.ui.EditorMenuEntry("Export Game…",project.opened!=null && !project.busy,action=distribution.exportGame)),
                        projectAssets=assetCommands,projectDocuments=documents,
                        assetEntries=assetBrowserMenu(assets,assetBrowserVisible) { if(assetBrowserVisible) manager.hidePanel(PanelId("assets")) else manager.showPanel(PanelId("assets")) },
                    ) + windowsMenu(manager)
                    SideEffect { saveActive=applicationMenus.first { it.first=="File" }.second.first { it.label=="Save" }.action }
                    EditorMenuStrip(applicationMenus,window=window,onMenuBounds={label,bounds -> if(menuSmoke) shellBounds[label]=bounds},title=(if(project.busy) "Project busy… — " else project.opened?.let { "${it.document.name} — " } ?: "")+
                        if(mode==ViewportMode.Entity) (activeEntity?.document?.name ?: "Prefab")+(if(activeEntity?.dirty==true) " *" else "")
                        else (scene.getOrNull()?.name ?: "Scene")+(if(scene.getOrNull()?.dirty==true) " *" else ""))
                    manager.layoutNotice?.let { Text(it,color=MaterialTheme.colors.error) }
                    project.error?.let { Text(it,color=MaterialTheme.colors.error) }
                    distribution.message?.let { Text(it,style=MaterialTheme.typography.caption) }
                    val panels=WorkspacePanels(mapOf(
                        PanelId("hierarchy") to {
                            if(mode==ViewportMode.Scene) scene.getOrNull()?.let { state ->
                                SceneWorkspace(state,onEditEntity={editSceneEntity(it)},onEntityBounds={key,bounds -> if(sceneSmoke || menuSmoke) treeNodeBounds[key]=bounds},editable=scenePlayback.editing)
                            } else if(linkedEntity!=null) SceneTree(scene.getOrThrow(),Modifier.fillMaxSize(),onEditEntity={editSceneEntity(it)})
                            else activeEntity?.let { EntityOutline(it,Modifier.fillMaxSize()) }
                        },
                        PanelId("inspector") to {
                            if(mode==ViewportMode.Scene) scene.getOrNull()?.let { SceneEntityInspector(it,editable=scenePlayback.editing) }
                            else Column(Modifier.fillMaxSize()) {
                                activeEntity?.let { state ->
                                    GizmoControls(state)
                                    Box(Modifier.weight(1f).fillMaxWidth().onGloballyPositioned { if(menuSmoke) entityMenuBounds[0]=it.boundsInWindow() }) { EntityWorkspace(state,embedded=linkedEntity!=null) }
                                }
                            }
                        },
                        PanelId("assets") to {
                            if(assets==null) Text("Open a project to browse assets.")
                            else AssetBrowserPanel(assets,project.opened?.document?.assetRoot ?: "Assets",Modifier.fillMaxSize(),commands=assetCommands,onOpen={ relative -> project.opened?.let { documents?.openPath?.invoke(it.root.resolve(it.document.assetRoot).resolve(relative)) } },onBounds={id,bounds -> if(assetSmoke || documentSmoke || runtimeSmoke) assetBounds[id]=bounds})
                        },
                        PanelId("scene") to { viewportPanel(false,false) },
                        PanelId("game") to { viewportPanel(true,false) },
                        PanelId("entity") to { viewportPanel(false,true) },
                    ), manager.registry)
                    DockSurface(manager.layout.hosts.single { it.id==manager.layout.mainHost },manager,uiScale,Modifier.weight(1f).fillMaxWidth(),
                        onBounds={id,bounds -> if(menuSmoke || cameraSmoke) shellBounds[when(id.value) { "scene" -> "SceneTab"; "game" -> "GameTab"; else -> id.value }]=bounds}) { panels.Render(it) }
                    manager.layout.hosts.filter { it.id!=manager.layout.mainHost }.forEach { host ->
                        key(host.id) {
                            Window(visible=!manager.loading,onCloseRequest={manager.closeHost(host.id)},title="SLE — "+host.root?.let { root -> root.tabs().flatMap { it.panels }.joinToString(" / ") { manager.registry.byId.getValue(it).title } },
                                state=rememberWindowState(position=androidx.compose.ui.window.WindowPosition(host.bounds.x.dp,host.bounds.y.dp),width=host.bounds.width.dp,height=host.bounds.height.dp)) {
                                DisposableEffect(window) {
                                    manager.bind(host.id,window)
                                    onDispose { manager.unbind(host.id,window) }
                                }
                                EditorTheme(uiScale) { Surface(Modifier.fillMaxSize()) { DockSurface(host,manager,uiScale,Modifier.fillMaxSize()) { panels.Render(it) } } }
                            }
                        }
                    }

                }
            }
        }
        }
    }
}
