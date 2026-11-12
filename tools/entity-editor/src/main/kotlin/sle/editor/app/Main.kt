package sle.editor.app

import sle.editor.files.createStarterScene
import sle.editor.files.createCameraScene
import sle.editor.model.EntityDocument
import sle.editor.model.EntityWorkspaceState
import sle.editor.model.SceneWorkspaceState
import sle.editor.model.ScenePlaybackState
import sle.editor.model.isolatedComponentPreview
import sle.editor.model.readComponentCatalog
import sle.editor.smoke.ViewportSmokeChecks
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
    if ("--authoring-smoke" in args) { runAuthoringSmoke(); return }
    launchEditor(args)
}

private fun launchEditor(args: Array<String>) = application {
    val sandboxSmoke="--smoke-test" in args
    val authoringUiSmoke="--authoring-ui-smoke" in args
    val switchingSmoke="--preview-smoke" in args
    val gizmoSmoke="--gizmo-smoke" in args
    val sceneSmoke="--scene-smoke" in args
    val menuSmoke="--context-menu-smoke" in args
    val cameraSmoke="--camera-smoke" in args
    val previewSmoke=authoringUiSmoke || switchingSmoke
    val testing=sandboxSmoke || previewSmoke || gizmoSmoke || sceneSmoke || cameraSmoke || menuSmoke
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
    val scene=remember { authoring.map { workspace -> SceneWorkspaceState(workspace.catalog).apply {
        if(sceneSmoke || menuSmoke) createStarterScene(this,workspace.catalog,java.nio.file.Path.of(System.getProperty("sle.smokeReport")).parent)
        if(cameraSmoke) createCameraScene(this,workspace.catalog,java.nio.file.Path.of(System.getProperty("sle.smokeReport")).parent)
    } } }
    val requestedScene=args.indexOf("--scene").takeIf { it>=0 }?.let { args.getOrNull(it+1) }
    remember { requestedScene?.let { path -> scene.getOrNull()?.let { state ->
        runCatching { state.open(java.nio.file.Path.of(path)) }.onFailure { state.error=it.message }
    } }; true }
    val fallbackDocument=remember { EntityDocument().serialize() }
    val initialIdentity=remember { authoring.getOrNull()?.document?.key }
    var selectedTab by remember { mutableStateOf(if (sandboxSmoke) 1 else if (previewSmoke || gizmoSmoke) 0 else 2) }
    var uiScale by remember { mutableStateOf(0.85f) }
    var editingSceneKey by remember { mutableStateOf<String?>(null) }
    val treeNodeBounds=remember { mutableMapOf<String,androidx.compose.ui.geometry.Rect>() }
    val entityMenuBounds=remember { arrayOfNulls<androidx.compose.ui.geometry.Rect>(1) }
    val shellBounds=remember { mutableMapOf<String,androidx.compose.ui.geometry.Rect>() }
    val linkedEntity=scene.getOrNull()?.entities?.firstOrNull { it.document.key==editingSceneKey }
    val activeEntity=linkedEntity ?: authoring.getOrNull()
    val scenePlayback=remember { ScenePlaybackState() }
    var playback by remember { mutableStateOf(Playback.Running) }
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
    }
    fun editSceneEntity(key: String) {
        canvas?.finishGesture();scene.getOrNull()?.select(key);editingSceneKey=key;switchTab(0)
    }
    fun showScene(game: Boolean) {
        canvas?.finishGesture();selectedTab=2;scenePlayback.gameView=game
    }
    LaunchedEffect(selectedTab,scene.getOrNull()?.selectedKey) {
        if(selectedTab==0 && editingSceneKey!=null) {
            val selected=scene.getOrNull()?.selectedKey
            if(selected==null) switchTab(2) else editingSceneKey=selected
        }
    }
    val watchdog=remember {
        if (testing) Timer(30_000) {
            System.err.println("EDITOR_SMOKE_FAIL: viewport timed out")
            exitProcess(1)
        }.apply { isRepeats=false; start() } else null
    }
    Window(
        onCloseRequest={ if (testing || (confirmDiscard(authoring.getOrNull()) && confirmDiscardScene(scene.getOrNull()))) { watchdog?.stop(); exitApplication() } },
        title="SLE Entity Editor",
        state=windowState,
    ) {
        LaunchedEffect(Unit) {
            if(menuSmoke) {
                try {
                    window.isAlwaysOnTop=true;window.toFront()
                    while(readyFrames<5) delay(50)
                    runContextMenuSmoke(window,canvas ?: error("Viewport missing"),scene.getOrThrow(),treeNodeBounds,{entityMenuBounds[0]},shellBounds)
                    watchdog?.stop();exitApplication()
                } catch(failure: Throwable) { canvas?.stopRendering();failure.printStackTrace();exitProcess(1) }
            }
            if(cameraSmoke) {
                try {
                    window.isAlwaysOnTop=true;window.toFront()
                    while(readyFrames<5) delay(50)
                    runCameraPlaySmoke(canvas ?: error("Viewport missing"),scene.getOrThrow(),scenePlayback,switchView={game ->
                        val target=shellBounds.getValue(if(game) "GameTab" else "SceneTab").center
                        val origin=window.contentPane.locationOnScreen
                        Robot().apply {
                            mouseMove(origin.x+target.x.toInt(),origin.y+target.y.toInt())
                            mousePress(java.awt.event.InputEvent.BUTTON1_DOWN_MASK);mouseRelease(java.awt.event.InputEvent.BUTTON1_DOWN_MASK)
                        }
                        delay(100)
                    }) {
                        canvas?.finishGesture();scenePlayback.play(scene.getOrThrow())
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
                    while(lastRenderedMode!=ViewportMode.Entity || !viewport.authoringReady()) delay(30)
                    selectedTab=1
                    while(lastRenderedMode!=ViewportMode.Sandbox) delay(30)
                    val sandboxFrame=readyFrames
                    while(readyFrames<sandboxFrame+10) delay(30)
                    ViewportSmokeChecks.verifyDesktopSandbox(viewport)
                    selectedTab=2
                    while(lastRenderedMode!=ViewportMode.Scene || !viewport.authoringReady()) delay(30)
                    check(canvas===viewport && viewport.contextIdentity()==originalContext)
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
            Surface(Modifier.fillMaxSize()) {
                Column(Modifier.padding(6.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                    EditorMenuStrip(editorApplicationMenus(
                        scene.getOrNull(),inspector,authoring.getOrNull(),mode==ViewportMode.Entity,linkedEntity!=null,scenePlayback.editing,
                        onScene={showScene(it)},onEntity={key -> if(key==null) { editingSceneKey=null;switchTab(0) } else editSceneEntity(key) },
                        onFit={canvas?.fitEntity()},onScale={uiScale=it},
                        onExit={if(confirmDiscard(authoring.getOrNull()) && confirmDiscardScene(scene.getOrNull())) exitApplication()},
                    ),window=window,onMenuBounds={label,bounds -> if(menuSmoke) shellBounds[label]=bounds},title=
                        if(mode==ViewportMode.Entity) (activeEntity?.document?.name ?: "Prefab")+(if(activeEntity?.dirty==true) " *" else "")
                        else (scene.getOrNull()?.name ?: "Scene")+(if(scene.getOrNull()?.dirty==true) " *" else ""))
                    key(mode==ViewportMode.Entity) { TabRow(selectedTabIndex=if(mode==ViewportMode.Entity) 2 else if(scenePlayback.gameView) 1 else 0) {
                        Tab(mode==ViewportMode.Scene && !scenePlayback.gameView,onClick={showScene(false)},modifier=Modifier.onGloballyPositioned { if(menuSmoke || cameraSmoke) shellBounds["SceneTab"]=it.boundsInWindow() },text={Text("Scene")})
                        Tab(mode==ViewportMode.Scene && scenePlayback.gameView,onClick={showScene(true)},modifier=Modifier.onGloballyPositioned { if(menuSmoke || cameraSmoke) shellBounds["GameTab"]=it.boundsInWindow() },text={Text("Game")})
                        if(mode==ViewportMode.Entity) Tab(true,onClick={},text={
                            Row(verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
                                Text(activeEntity?.document?.name ?: "Entity")
                                TextButton(onClick={showScene(false)}) { Text("Close") }
                            }
                        })
                    }
                    }
                    Row(Modifier.weight(1f).fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                        if(mode!=ViewportMode.Sandbox) Box(Modifier.width(260.dp).fillMaxHeight()) {
                            if(mode==ViewportMode.Scene) scene.fold(onSuccess={ state ->
                                SceneWorkspace(state,onEditEntity={editSceneEntity(it)},onEntityBounds={key,bounds -> if(sceneSmoke || menuSmoke) treeNodeBounds[key]=bounds},editable=scenePlayback.editing)
                            },onFailure={Text("Cannot initialize scene: ${it.message}")})
                            else if(linkedEntity!=null) SceneTree(scene.getOrThrow(),Modifier.fillMaxSize(),onEditEntity={editSceneEntity(it)})
                            else activeEntity?.let { EntityOutline(it,Modifier.fillMaxSize()) }
                        }
                        Column(Modifier.weight(1f).fillMaxHeight(),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                            if(mode==ViewportMode.Scene) SceneViewControls(scenePlayback,!failed,onPlay={
                                try {
                                    canvas?.finishGesture()
                                    scenePlayback.play(scene.getOrThrow())
                                    scene.getOrNull()?.error=null
                                } catch(failure: Exception) { scene.getOrNull()?.error=failure.message }
                            },onStop={scenePlayback.stop()})
                            if (mode == ViewportMode.Sandbox) SandboxControls(playback,!failed) { playback=it }
                            else Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                                Spacer(Modifier.weight(1f))
                                OutlinedButton(onClick={ canvas?.zoomEntity(0.8f) },enabled=ready && !failed && !(mode==ViewportMode.Scene && scenePlayback.gameView)) { Text("+") }
                                OutlinedButton(onClick={ canvas?.zoomEntity(1.25f) },enabled=ready && !failed && !(mode==ViewportMode.Scene && scenePlayback.gameView)) { Text("−") }
                                OutlinedButton(onClick={ canvas?.fitEntity() },enabled=ready && !failed && !(mode==ViewportMode.Scene && scenePlayback.gameView)) { Text("Fit") }
                            }
                            if(mode==ViewportMode.Scene && scenePlayback.editing && !scenePlayback.gameView)
                                inspector?.let { GizmoControls(it,entityOnly=true) }
                            if(failed) Text(status,color=MaterialTheme.colors.error)
                            if (!ready && !failed) LinearProgressIndicator(Modifier.fillMaxWidth())
                            if (mode != ViewportMode.Sandbox && inspector?.fieldErrors?.isNotEmpty() == true)
                                Text("Finish the highlighted fields. Preview keeps the last valid values.",color=MaterialTheme.colors.error,style=MaterialTheme.typography.caption)
                            // Keep this composable at a stable slot: tab switches change content,
                            // not the AWT peer, OpenGL context or engine graphics session.
                            SharedViewport(
                                modifier=Modifier.weight(1f).fillMaxWidth(),mode=mode,
                                document=if(mode==ViewportMode.Scene) scenePlayback.source ?: scene.getOrNull()?.serialize() ?: "{\"entities\":[]}" else activeEntity?.document?.let {
                                    if(linkedEntity!=null) isolatedComponentPreview(it,scene.getOrThrow().entities.map { it.document.key }).serialize() else it.serialize()
                                } ?: fallbackDocument,
                                playback=if(mode==ViewportMode.Scene) scenePlayback.playback else playback,sandboxSmoke=sandboxSmoke,previewSmoke=previewSmoke,
                                gameView=mode==ViewportMode.Scene && scenePlayback.gameView,
                                onContextMenu=editorMenuLauncher {
                                    if(mode==ViewportMode.Scene) scene.getOrNull()?.let { sceneCreationMenu(it,scenePlayback.editing) { key -> editSceneEntity(key) } } ?: emptyList()
                                    else activeEntity?.let { entityCreationMenu(it,linkedEntity!=null) } ?: emptyList()
                                },
                                onSceneError={message -> scenePlayback.stop();scene.getOrNull()?.error=message},
                                authoring=inspector,scene=if(mode==ViewportMode.Scene || (mode==ViewportMode.Entity && linkedEntity!=null)) scene.getOrNull() else null,
                                onCanvas={ canvas=it },
                                onStatus={ status=it },
                                onFailure={ failure ->
                                    failed=true; status="Viewport failed: ${failure.message}"; failure.printStackTrace()
                                    if (testing) exitProcess(1)
                                },
                                onFrame={ frame,renderedMode ->
                                    ready=true
                                    if(testing) lastRenderedMode=renderedMode
                                    if (testing) readyFrames=frame
                                    if (sandboxSmoke) {
                                        if (frame == 20) windowState.size=DpSize(1000.dp,650.dp)
                                        if (frame == 25) playback=Playback.Paused
                                        if (frame == 35) playback=Playback.Running
                                        if (frame == 45) playback=Playback.Stopped
                                        if (frame == 50) playback=Playback.Running
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
                                },
                            )
                        }
                        if(mode!=ViewportMode.Sandbox) Box(Modifier.width(350.dp).fillMaxHeight()) {
                            if(mode==ViewportMode.Scene) scene.getOrNull()?.let { state ->
                                SceneEntityInspector(state,editable=scenePlayback.editing)
                            } else Column(Modifier.fillMaxSize(),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                                if(linkedEntity!=null) scene.getOrNull()?.error?.let { Text(it,color=MaterialTheme.colors.error) }
                                activeEntity?.let { state ->
                                    GizmoControls(state)
                                    key(state.document.key) {
                                        Box(Modifier.weight(1f).fillMaxWidth().onGloballyPositioned { if(menuSmoke) entityMenuBounds[0]=it.boundsInWindow() }) { EntityWorkspace(state,embedded=linkedEntity!=null) }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
