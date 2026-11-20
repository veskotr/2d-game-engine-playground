package sle.editor.viewport

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import sle.editor.bridge.NativeEngine
import sle.editor.console.ConsoleSeverity
import sle.editor.model.EntityDocument
import sle.editor.model.EntityWorkspaceState
import sle.editor.model.SceneDocument
import sle.editor.model.SceneWorkspaceState
import sle.editor.model.isolatedComponentPreview
import sle.editor.smoke.ViewportSmokeChecks
import kotlinx.serialization.json.*

import org.lwjgl.BufferUtils
import org.lwjgl.opengl.GL
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.awt.AWTGLCanvas
import org.lwjgl.opengl.awt.GLData
import java.awt.Color
import java.awt.Graphics
import java.awt.EventQueue
import java.awt.event.*
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import javax.swing.Timer

internal enum class Playback { Running, Paused, Stopped }
internal enum class ViewportMode { Entity, Sandbox, Scene }

/** Hidden, durable GL owner. Every method and context operation runs on EDT. */
internal class NativeViewportOwner(
    private val smokeTest: Boolean,
    private val onFrame: (Int, ViewportMode) -> Unit,
    private val onFailure: (Throwable) -> Unit,
    private val onStatus: (String) -> Unit,
    private val previewSmoke: Boolean = false,
    /** Snap steps and the toggle; the grid follows the move step. */
    val snap: SnapService = SnapService(),
    /** The active Scene view tool (per-user editor state). */
    val tools: sle.editor.viewport.tools.ViewportToolState = sle.editor.viewport.tools.ViewportToolState(),
) : AWTGLCanvas(GLData().apply {
    majorVersion = 4
    minorVersion = 3
    profile = GLData.Profile.CORE
    swapInterval = canvasSwapInterval
}) {
    val console = sle.editor.console.ConsoleState(persistPreferences = !smokeTest && !previewSmoke)
    val luaProfiler = sle.editor.profiler.LuaProfilerState()
    private var profilingApplied = false

    private fun syncLuaProfiler() {
        if (profilingApplied == luaProfiler.capture) return
        NativeEngine.setLuaProfiling(session, luaProfiler.capture)
        profilingApplied = luaProfiler.capture
    }

    var consumeSceneStep: () -> Boolean = { false }
    var onLuaErrorPause: () -> Unit = {}
    var liveInspection by mutableStateOf<LiveInspection?>(null)
        private set
    private var liveSelection: LiveSelection? = null
    private var lastLivePoll: Long? = null
    private var desiredPlayRevision: Long? = null
    private var startedPlayRevision: Long? = null
    private var startedSceneInstance: Long? = null

    private data class ScriptReload(val selection: LiveSelection, val key: String, val asset: String)
    private var pendingScriptReload: ScriptReload? = null

    fun reloadLiveScript(selection: LiveSelection, key: String, asset: String) {
        check(EventQueue.isDispatchThread())
        if (selection != liveSelection || desiredPlayback == Playback.Stopped) return
        pendingScriptReload = ScriptReload(selection, key, asset)
    }

    fun selectLiveEntity(selection: LiveSelection) {
        val current = liveInspection ?: return
        if (selection.run != current.run || selection.scene != current.scene ||
            current.entities.none { it.id == selection.entity }) return
        liveSelection = selection
        liveInspection = current.copy(selection = selection, details = null)
        lastLivePoll = null
    }

    private fun refreshLiveInspection(force: Boolean) {
        val now = System.nanoTime()
        if (!force && lastLivePoll?.let { now - it < 100_000_000L } == true) return
        liveInspection = readLiveInspection()
        liveSelection = liveInspection?.selection
        lastLivePoll = now
    }

    private fun readLiveInspection(): LiveInspection? {
        val selection = liveSelection?.takeIf { it.run == session }
        val snapshot = LiveInspection.parse(
            NativeEngine.liveSnapshot(session, selection?.scene ?: 0, selection?.entity ?: 0), selection) ?: return null
        return snapshot.copy(playRevision = startedPlayRevision.takeIf { snapshot.scene == startedSceneInstance })
    }

    fun readPausedPlaySelection(): LiveInspection? {
        check(EventQueue.isDispatchThread())
        if (actualMode != ViewportMode.Scene || desiredMode != ViewportMode.Scene ||
            actualScenePlayback != Playback.Paused || desiredPlayback != Playback.Paused ||
            startedPlayRevision == null || startedPlayRevision != desiredPlayRevision) return null
        refreshLiveInspection(force = true)
        return liveInspection
    }

    private fun clearLiveInspection() {
        pendingScriptReload = null
        luaProfiler.clear()
        profilingApplied = false
        liveInspection = null
        liveSelection = null
        lastLivePoll = null
        startedPlayRevision = null
        startedSceneInstance = null
    }

    var luaPauseMessage by mutableStateOf<String?>(null)
        private set
    private var luaPauseEntity = 0L
    private var session = 0L

    private fun clearLuaPause() {
        luaPauseMessage = null
        luaPauseEntity = 0L
    }

    private fun applyLuaPause(error: ByteArray?) {
        if (error == null) return
        val fields = Json.parseToJsonElement(error.decodeToString()).jsonObject
        luaPauseMessage = fields.getValue("message").jsonPrimitive.content
        luaPauseEntity = fields.getValue("entity").jsonPrimitive.long
        desiredPlayback = Playback.Paused
        actualScenePlayback = Playback.Paused
        releaseSink()
        onLuaErrorPause()
    }

    fun luaPausePosition(): Vec2? {
        if (session == 0L || actualScenePlayback != Playback.Paused || luaPauseEntity == 0L) return null
        return NativeEngine.playEntityPosition(session,luaPauseEntity)?.let { Vec2(it[0].toDouble(),it[1].toDouble()) }
    }

    private var pendingConsoleCommand: Pair<Long, String>? = null

    fun queuePlayLua(source: String) {
        require(desiredMode == ViewportMode.Scene && actualScenePlayback != Playback.Stopped &&
            desiredPlayback != Playback.Stopped && session != 0L) { "The Play runtime is unavailable" }
        check(pendingConsoleCommand == null) { "A Play Lua command is already queued" }
        pendingConsoleCommand = session to source
    }

    fun drainConsole() {
        if (session != 0L) NativeEngine.drainConsole(session)?.let(console::accept)
    }

    fun clearConsole() {
        if (session != 0L) NativeEngine.clearConsole(session)
        console.clearHistory()
    }
    var schedulerDelay = 16; private set
    var presentationVsync = false; private set
    private lateinit var capabilities: org.lwjgl.opengl.GLCapabilities
    var creates = 0; private set
    var destroys = 0; private set
    private var logicalWidth = 16
    private var logicalHeight = 16
    private var pixelWidth = 16
    private var pixelHeight = 16
    var inputFocused = false
    var inputGameView = false
    private var inputView = ViewId.Scene
    var inputRequest: ViewRequest? = null
    var inputSink: (ViewInput) -> Unit = {}
    var releaseSink: () -> Unit = {}
    private var frameCamera: PreviewCamera? = null
    private var gameMetrics = emptyList<Float>()
    fun activateInput(view: ViewId, frame: ViewFrame?, focused: Boolean) {
        inputView=view; inputGameView=view==ViewId.Game; inputFocused=focused
        inputRequest=frame?.request
        camera=frame?.camera; gameMetrics=frame?.gameRect ?: emptyList()
        logicalWidth=frame?.request?.logicalWidth ?: 16; logicalHeight=frame?.request?.logicalHeight ?: 16
        pixelWidth=frame?.request?.pixelWidth ?: 16; pixelHeight=frame?.request?.pixelHeight ?: 16
    }
    fun releaseHeldInput() { if(session!=0L) NativeEngine.input(session,3,0,0.0,0.0) }
    fun acceptInput(event: ViewInput) { if(session!=0L) NativeEngine.input(session,event.type,event.code,event.x,event.y) }
    private var frames = 0
    private var stopped = false
    private var lastFrameNanos = 0L
    private val openedAt = System.nanoTime()
    private var desiredMode = ViewportMode.Entity
    private var actualMode: ViewportMode? = null
    private var desiredDocument: String? = null
    private var attemptedDocument: String? = null
    private var lastGoodPreview: String? = null
    private val goodDocuments = mutableMapOf<ViewportMode,String>()
    private var scene: SceneWorkspaceState? = null
    private var editorAssetRoot: java.nio.file.Path? = null
    private var editorProjectSettings = sle.editor.project.ProjectSettings()
    fun setEditorAssetRoot(root: java.nio.file.Path?, settings: sle.editor.project.ProjectSettings = sle.editor.project.ProjectSettings()) {
        if (editorAssetRoot != root) refreshPreview()
        editorAssetRoot = root
        editorProjectSettings = settings
    }

    private fun applyEditorAssetSettings() {
        NativeEngine.setEditorProjectSettings(session, editorAssetRoot?.toString().orEmpty().toByteArray(Charsets.UTF_8),
            editorProjectSettings.json().toString().toByteArray(Charsets.UTF_8))
    }

    fun refreshPreview() {
        attemptedDocument = null
    }

    var activeActionKeys: Set<Int> = emptySet()
        private set
    private var desiredProject: sle.editor.project.PreparedProject? = null
    fun setProject(value: sle.editor.project.PreparedProject?) { desiredProject=value }
    private var desiredPlayback = Playback.Running
    private var actualPlayback = Playback.Running
    private var actualScenePlayback = Playback.Stopped
    private var desiredGameView = false
    private var actualGameView = false
    private var pendingPan = Vec2(0.0,0.0)
    private var panAnchor: Vec2? = null
    private var sceneError: (String)->Unit = {}
    private var pausedFingerprint: Int? = null
    private var pendingFit = false
    private var pendingZoom = 1f
    private var audioQueued = false
    private var previewFingerprint: Int? = null
    private var gizmos: ViewportGizmos? = null
    private var camera: PreviewCamera? = null
    private val overlay = GizmoOverlay()
    var luaErrorColor = sle.editor.ui.theme.editorColors(sle.editor.ui.theme.EditorThemeMode.Dark).error
    var editorSelectionColor = sle.editor.ui.theme.editorColors(sle.editor.ui.theme.EditorThemeMode.Dark).selectionOutline
    /** Move, Rotate and Scale handles use the viewport axis tokens of the current theme. */
    var editorAxisColors = sle.editor.ui.theme.editorColors(sle.editor.ui.theme.EditorThemeMode.Dark).let { it.viewportXAxis to it.viewportYAxis }
    /** Scene view tools: right-click, pan, zoom and entity drags to Assets are handled here first. */
    private val toolHost = sle.editor.viewport.tools.ToolHost(tools,snap).also { host ->
        host.gizmos={ gizmos }
        host.selectionChanged={ scene?.let { setAuthoring(it.selected) } }
    }
    /** Handles of the active tool in viewport pixels (smokes aim at them). */
    fun toolHandles(): List<sle.editor.viewport.tools.ToolHandle> { toolHost.camera=camera;return toolHost.handles() }
    private var appliedGrid: Pair<Float,Int>? = null
    fun previewCamera(): PreviewCamera? = camera
    fun contextIdentity(): Long = context
    fun withContext(action: () -> Unit) = runInContext { GL.setCapabilities(capabilities); action() }
    fun scenePlayback(): Playback = actualScenePlayback
    fun gameViewActive(): Boolean = actualGameView
    fun runtimeTransforms(): JsonObject = Json.parseToJsonElement(NativeEngine.sceneTransforms(session).toString(Charsets.UTF_8)).jsonObject
    fun gameCameraMetrics(): FloatArray = NativeEngine.gameCamera(session)
    fun gameViewport(): FloatArray = gameMetrics.toFloatArray()
    fun finishGesture() { toolHost.finish(); gizmos?.finish(); panAnchor=null; cancelEntityDrag() }
    /** A picked scene entity dragged out of the viewport onto an Assets folder (set by the workspace).
     * The drag shows only once the pointer leaves the canvas; dropping on a target first cancels the
     * in-progress move, so the entity keeps its pose and the scene is unchanged before the drop. */
    var entityDragMove: ((String,java.awt.Point)->Unit)? = null
    var entityDragOver: ((java.awt.Point)->Boolean)? = null
    var entityDrop: ((String,java.awt.Point)->Unit)? = null
    var entityDragCancel: (()->Unit)? = null
    private var draggedEntity: String? = null
    private var entityDragShown = false
    private fun cancelEntityDrag() {
        draggedEntity=null
        if(entityDragShown) { entityDragShown=false;entityDragCancel?.invoke() }
    }
    fun setSceneView(game: Boolean,onError: (String)->Unit) {
        if(desiredGameView!=game) { releaseSink(); finishGesture() }
        desiredGameView=game;sceneError=onError
    }
    private fun editingAllowed() = desiredMode==ViewportMode.Entity ||
        (desiredMode==ViewportMode.Scene && desiredPlayback==Playback.Stopped && !inputGameView)
    private fun gameInput(key: Int? = null) = when (desiredMode) {
        ViewportMode.Sandbox -> desiredPlayback == Playback.Running
        ViewportMode.Scene -> inputGameView &&
            (actualScenePlayback == Playback.Running && desiredPlayback == Playback.Running ||
                actualScenePlayback == Playback.Paused && desiredPlayback == Playback.Paused && key != null && key in activeActionKeys)
        else -> false
    }
    fun authoringReady(): Boolean = actualMode == desiredMode && desiredMode != ViewportMode.Sandbox &&
        (desiredMode!=ViewportMode.Scene || actualScenePlayback==Playback.Stopped) &&
        desiredDocument == lastGoodPreview && (if (desiredMode == ViewportMode.Scene) scene?.serialize()
        else gizmos?.state?.document?.let { document ->
            if(scene!=null) isolatedComponentPreview(document,scene!!.entities.map { it.document.key }).serialize() else document.serialize()
        }) == lastGoodPreview
    fun setScene(value: SceneWorkspaceState?) { if(scene!==value) toolHost.finish();scene=value;toolHost.scene=value }
    fun setAuthoring(state: EntityWorkspaceState?) {
        val entityOnly=desiredMode==ViewportMode.Scene
        val graph=if(entityOnly) scene else null
        if (gizmos?.state !== state || gizmos?.entityOnly != entityOnly || gizmos?.scene !== graph) {
            gizmos?.cancel(); gizmos=state?.let { ViewportGizmos(it,entityOnly,graph,snap) }
        }
    }

    fun setContent(mode: ViewportMode, document: String?, playback: Playback, playRevision: Long? = null) {
        check(EventQueue.isDispatchThread())
        if (mode != desiredMode || playback!=desiredPlayback) { releaseSink(); finishGesture() }
        if (mode != ViewportMode.Scene || playback != Playback.Paused) clearLuaPause()
        desiredMode = mode
        desiredDocument = document
        desiredPlayback = playback
        desiredPlayRevision = playRevision
    }
    fun fitEntity() { if(desiredMode!=ViewportMode.Scene || !inputGameView) { finishGesture(); pendingFit = true } }
    fun zoomEntity(factor: Float) { if (gizmos?.dragging != true && !toolHost.pressed && (desiredMode!=ViewportMode.Scene || !inputGameView)) pendingZoom *= factor }
    /** Frame Selected: fits the Scene view to [bounds] (world units), keeping the view's aspect. */
    fun frameBounds(bounds: Bounds) {
        if(desiredMode!=ViewportMode.Scene || inputGameView) return
        val view=camera ?: return
        finishGesture()
        val (factor,center)=frameView(view.size,bounds) ?: return
        pendingZoom*=factor.toFloat()
        pendingPan+=center-(view.origin+view.size*0.5)
    }
    private fun status(text: String) = EventQueue.invokeLater { if (!stopped) onStatus(text) }

    var contextPosition: Vec2? = null; private set
    var onContextMenu: ((Int,Int)->Unit)? = null

    init {
        background = Color(23,25,31)
        foreground = Color(200,205,215)
        isFocusable = true
        focusTraversalKeysEnabled = false
        addKeyListener(object : KeyAdapter() {
            override fun keyPressed(event: KeyEvent) { if(event.keyCode==KeyEvent.VK_ESCAPE) cancelEntityDrag();sendKey(event,true) }
            override fun keyReleased(event: KeyEvent) = sendKey(event,false)
        })
        addFocusListener(object : FocusAdapter() {
            override fun focusLost(event: FocusEvent) { finishGesture(); releaseSink() }
        })
        val mouse = object : MouseAdapter() {
            override fun mousePressed(event: MouseEvent) {
                requestFocusInWindow()
                if(event.button==MouseEvent.BUTTON3 && editingAllowed()) {
                    finishGesture()
                    // Click-to-create lands on the move step while snapping applies (Ctrl inverts).
                    contextPosition=camera?.world(Vec2(event.x.toDouble(),event.y.toDouble()))?.let { snap.placement(it,event.isControlDown) }
                    if(desiredMode==ViewportMode.Scene) camera?.let { view -> scene?.let { state ->
                        // Right-clicking a selected entity keeps the selection, so the menu acts on the set.
                        val pick=ScenePicking.pick(state,view,Vec2(event.x.toDouble(),event.y.toDouble()))?.key
                        if(pick!=null && pick in state.selection) state.activate(pick) else state.select(pick)
                        setAuthoring(state.selected)
                    } }
                    onContextMenu?.invoke(event.xOnScreen,event.yOnScreen)
                    return
                }
                if(desiredMode!=ViewportMode.Sandbox && (desiredMode!=ViewportMode.Scene || !inputGameView) &&
                    (event.button==MouseEvent.BUTTON2 || (event.button==MouseEvent.BUTTON1 && event.isAltDown))) {
                    finishGesture();panAnchor=Vec2(event.x.toDouble(),event.y.toDouble());return
                }
                if (editingAllowed() && authoringReady()) {
                    camera?.let { view ->
                        if (desiredMode == ViewportMode.Scene) {
                            // The active tool picks, toggles, box-selects or drags (see viewport/tools).
                            if(event.button == MouseEvent.BUTTON1) scene?.let { scene ->
                                cancelEntityDrag()
                                toolHost.scene=scene;toolHost.camera=view
                                draggedEntity=toolHost.press(pointer(event)).dragEntity
                            }
                        } else gizmos?.press(event,view)
                    }
                    return
                }
                if(desiredMode==ViewportMode.Scene && gameInput() && !insideGame(event)) return
                sendCursor(event)
                sendButton(event,true)
            }
            override fun mouseReleased(event: MouseEvent) {
                if (event.button == MouseEvent.BUTTON1) {
                    val dragged=draggedEntity.takeIf { entityDragShown }
                    if(dragged!=null && entityDragOver?.invoke(event.locationOnScreen)==true) {
                        toolHost.cancel();gizmos?.cancel();draggedEntity=null;entityDragShown=false
                        entityDrop?.invoke(dragged,event.locationOnScreen)
                    } else {
                        if(toolHost.pressed) toolHost.release(pointer(event)) else gizmos?.finish()
                        cancelEntityDrag()
                    }
                }
                if(event.button==MouseEvent.BUTTON2 || event.button==MouseEvent.BUTTON1) panAnchor=null
                sendButton(event,false)
            }
            override fun mouseMoved(event: MouseEvent) {
                // The presenter canvas shows the tool's cursor (e.g. over a Move arrow).
                if(desiredMode==ViewportMode.Scene && editingAllowed()) {
                    toolHost.camera=camera
                    (event.component ?: this@NativeViewportOwner).cursor=java.awt.Cursor.getPredefinedCursor(toolHost.cursor(Vec2(event.x.toDouble(),event.y.toDouble())))
                } else event.component?.cursor=java.awt.Cursor.getDefaultCursor()
                sendCursor(event)
            }
            override fun mouseDragged(event: MouseEvent) {
                val previous=panAnchor
                if(previous!=null) {
                    val next=Vec2(event.x.toDouble(),event.y.toDouble())
                    camera?.let { pendingPan += Vec2(-(next.x-previous.x)*it.size.x/logicalWidth,(next.y-previous.y)*it.size.y/logicalHeight) }
                    panAnchor=next
                } else if (editingAllowed()) {
                    if(toolHost.pressed) toolHost.drag(pointer(event)) else gizmos?.move(event)
                    val dragged=draggedEntity
                    if(dragged!=null && entityDragMove!=null) {
                        // Presenters forward their own events: test against the visible source canvas.
                        if(!(event.component ?: this@NativeViewportOwner).contains(event.point)) { entityDragShown=true;entityDragMove?.invoke(dragged,event.locationOnScreen) }
                        else if(entityDragShown) { entityDragShown=false;entityDragCancel?.invoke() }
                    }
                } else sendCursor(event)
            }
            override fun mouseWheelMoved(event: MouseWheelEvent) {
                if (desiredMode != ViewportMode.Sandbox) zoomEntity(Math.pow(1.15,event.preciseWheelRotation).toFloat())
            }
        }
        addMouseListener(mouse)
        addMouseMotionListener(mouse)
        addMouseWheelListener(mouse)
    }

    override fun paint(graphics: Graphics) {
        if (session == 0L) {
            graphics.color = background
            graphics.fillRect(0,0,logicalWidth,logicalHeight)
            graphics.color = foreground
            graphics.drawString("Preparing viewport…",16,28)
        }
    }
    override fun update(graphics: Graphics) = paint(graphics)

    private fun sendInput(type: Int, code: Int = 0, x: Double = 0.0, y: Double = 0.0) {
        if (session != 0L && !stopped && gameInput(key = code.takeIf { type == 0 }) && (type == 3 || inputFocused))
            if(type==3) releaseSink() else inputRequest?.let { inputSink(ViewInput(if(desiredMode==ViewportMode.Sandbox) ViewId.Game else inputView,it.generation,type,code,x,y)) }
    }
    private fun sendKey(event: KeyEvent, down: Boolean) {
        val key = engineKey(event)
        if (!gameInput(key)) {
            if (down && editingAllowed()) { if(desiredMode==ViewportMode.Scene) { toolHost.camera=camera;toolHost.key(event) } else gizmos?.key(event) }
            return
        }
        if (key >= 0) sendInput(0,key,if (down) 1.0 else 0.0)
    }
    private fun pointer(event: MouseEvent) = sle.editor.viewport.tools.ToolPointer(Vec2(event.x.toDouble(),event.y.toDouble()),
        event.isShiftDown,event.isControlDown || event.isMetaDown)
    private fun sendButton(event: MouseEvent, down: Boolean) {
        val button=when(event.button) { MouseEvent.BUTTON1 -> 0; MouseEvent.BUTTON3 -> 1; MouseEvent.BUTTON2 -> 2; else -> return }
        sendInput(1,button,if (down) 1.0 else 0.0)
    }
    private fun insideGame(event: MouseEvent): Boolean {
        if(session==0L || gameMetrics.size<6 || logicalWidth<=0 || logicalHeight<=0) return false
        val view=gameMetrics.toFloatArray()
        val x=event.x.toDouble()*pixelWidth/logicalWidth
        val y=event.y.toDouble()*pixelHeight/logicalHeight
        val top=pixelHeight-view[1]-view[3]
        return x>=view[0] && x<view[0]+view[2] && y>=top && y<top+view[3]
    }
    private fun sendCursor(event: MouseEvent) {
        if(logicalWidth<=0 || logicalHeight<=0) return
        if(desiredMode==ViewportMode.Scene && gameInput()) {
            val view=gameMetrics.toFloatArray()
            if(view.size<6 || view[2]<=0 || view[3]<=0) return
            val top=pixelHeight-view[1]-view[3]
            sendInput(2,x=(event.x.toDouble()*pixelWidth/logicalWidth-view[0])/view[2]*view[4],
                y=(event.y.toDouble()*pixelHeight/logicalHeight-top)/view[3]*view[5])
        } else sendInput(2,x=event.x*480.0/logicalWidth,y=event.y*270.0/logicalHeight)
    }
    override fun initGL() {
        capabilities=GL.createCapabilities()
        GL11.glClearColor(0.09f,0.10f,0.12f,1f)
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT)

        System.err.println("VIEWPORT_CONTEXT_READY_MS="+(System.nanoTime()-openedAt)/1_000_000)
        lastFrameNanos=System.nanoTime()
    }
    private fun createSession() {
        session=NativeEngine.create(); creates++;appliedGrid=null
        if (smokeTest || previewSmoke) session=ViewportSmokeChecks.verifySessionGuardsAndReopen(session)
        actualMode=null
        actualPlayback=Playback.Running
        actualScenePlayback=Playback.Stopped
        activeActionKeys = emptySet()
        actualGameView=false
        audioQueued=false
    }
    private fun loadPreview(document: String) {
        val mode=desiredMode
        applyEditorAssetSettings()
        fun identity(source: String) = if (mode==ViewportMode.Scene) SceneDocument.parse(source).key else EntityDocument.parse(source).key
        fun load(source: String,preserve: Boolean) {
            if (mode==ViewportMode.Scene) NativeEngine.loadScenePreview(session,source.toByteArray(Charsets.UTF_8),preserve)
            else NativeEngine.loadPreview(session,source.toByteArray(Charsets.UTF_8),preserve)
        }
        try {
            val previous=goodDocuments[mode]
            val preserve=actualMode==mode && previous?.let { identity(it)==identity(document) }==true
            load(document,preserve)
            lastGoodPreview=document
            goodDocuments[mode]=document
            status((if (mode==ViewportMode.Scene) "Scene preview" else "Entity preview")+" • cyan colliders • yellow zones • orange origins")
        } catch (failure: IllegalStateException) {
            status("Preview unchanged: ${failure.message}")
            if (actualMode!=mode) {
                val previous = goodDocuments[mode]
                lastGoodPreview = try {
                    previous?.also { load(it, false) }
                } catch (_: IllegalStateException) {
                    // A saved fallback can reference a sidecar that has since changed.
                    null
                }
                if (lastGoodPreview == null) {
                    val empty = if (mode == ViewportMode.Scene) SceneDocument().serialize() else EntityDocument().serialize()
                    load(empty, false)
                    lastGoodPreview = empty
                }
            }
        }
        attemptedDocument=document
    }
    private fun setSwapInterval(vsync: Boolean) {
        presentationVsync=vsync
        if(System.getProperty("os.name").startsWith("Windows") && GL.getCapabilitiesWGL().WGL_EXT_swap_control)
            org.lwjgl.opengl.WGLEXTSwapControl.wglSwapIntervalEXT(if(vsync) 1 else 0)
    }
    private fun recreateSession() {
        clearLiveInspection()
        clearLuaPause()
        setSwapInterval(false)
        schedulerDelay=16
        finishGesture()
        if(session!=0L) { NativeEngine.destroy(session); destroys++ }
        session=0L;createSession();camera=null;attemptedDocument=null;pendingPan=Vec2(0.0,0.0)
    }
    private fun syncScenePlayback() {
        if(actualScenePlayback==desiredPlayback) return
        if(desiredPlayback==Playback.Stopped) {
            schedulerDelay=16
            recreateSession()
            desiredDocument?.let(::loadPreview)
            actualMode=ViewportMode.Scene
            actualScenePlayback=Playback.Stopped
        } else if(actualScenePlayback==Playback.Stopped) {
            try {
                if (console.clearOnPlay) clearConsole()
                syncLuaProfiler()
                val project=desiredProject
                val pause = if(project!=null) {
                    val error = NativeEngine.startProject(session,project.handle,console.pauseOnLuaError)
                    setSwapInterval(project.settings.vsync)
                    schedulerDelay=if(project.settings.targetFPS>0) kotlin.math.ceil(1000.0/project.settings.targetFPS).toInt().coerceAtLeast(1) else 1
                    error
                } else {
                    applyEditorAssetSettings()
                    val error = NativeEngine.startScenePlay(session,(desiredDocument ?: error("Missing scene")).toByteArray(Charsets.UTF_8),console.pauseOnLuaError)
                    if (desiredPlayRevision != null) {
                        startedSceneInstance = readLiveInspection()?.scene
                        startedPlayRevision = desiredPlayRevision
                    }
                    error
                }
                activeActionKeys = project?.boundKeys ?: editorProjectSettings.boundKeys()
                actualScenePlayback=desiredPlayback
                if (pause != null) applyLuaPause(pause)
                else if (actualScenePlayback == Playback.Paused) NativeEngine.pause(session,true)
                audioQueued=false
            } catch(failure: IllegalStateException) {
                recreateSession();desiredDocument?.let(::loadPreview);actualMode=ViewportMode.Scene
                val message=failure.message ?: "Could not start scene"
                EventQueue.invokeLater { sceneError(message) }
            }
        } else {
            NativeEngine.pause(session,desiredPlayback==Playback.Paused)
            actualScenePlayback=desiredPlayback
        }
        lastFrameNanos=System.nanoTime()
    }
    fun advance(dt: Float) {
        GL.setCapabilities(capabilities)
        check(EventQueue.isDispatchThread())
        if (session == 0L) createSession()
        if (actualMode != desiredMode) {
            if(actualScenePlayback!=Playback.Stopped) {
                recreateSession()
                actualScenePlayback=Playback.Stopped
            }
            NativeEngine.pause(session,true)
            if (desiredMode != ViewportMode.Sandbox) desiredDocument?.let(::loadPreview)
            else {
                val started=System.nanoTime()
                NativeEngine.loadSandbox(session)
                actualPlayback=Playback.Running
                status("Sandbox ready • click viewport for WASD • F3 toggles colliders")
                System.err.println("VIEWPORT_SANDBOX_SWITCH_MS="+(System.nanoTime()-started)/1_000_000)
            }
            actualMode=desiredMode
            audioQueued=false
            lastFrameNanos=System.nanoTime()
        }
        if(desiredMode==ViewportMode.Scene) syncScenePlayback()
        if (desiredMode != ViewportMode.Sandbox && (desiredMode!=ViewportMode.Scene || desiredPlayback==Playback.Stopped) && attemptedDocument != desiredDocument)
            desiredDocument?.let(::loadPreview)
        if (desiredMode == ViewportMode.Sandbox && actualPlayback != desiredPlayback) {
            if (desiredPlayback == Playback.Stopped) {
                NativeEngine.destroy(session); destroys++
                session=0L
                createSession()
                NativeEngine.loadSandbox(session)
                actualMode=ViewportMode.Sandbox
            }
            NativeEngine.pause(session,desiredPlayback != Playback.Running)
            actualPlayback=desiredPlayback
            lastFrameNanos=System.nanoTime()
            pausedFingerprint=null
            if (smokeTest) println("EMBEDDED_PLAYBACK $actualPlayback")
        }
        if (desiredMode != ViewportMode.Sandbox && (pendingFit || pendingZoom != 1f)) {
            NativeEngine.previewCamera(session,pendingZoom,pendingFit)
            pendingFit=false
            pendingZoom=1f
        }
        // The editor grid follows the move snap step (Scene and Entity previews only).
        val grid=snap.gridSpacing.toFloat() to SnapSettings.GRID_MAJOR_EVERY
        if(desiredMode!=ViewportMode.Sandbox && appliedGrid!=grid) { NativeEngine.setEditorGrid(session,grid.first,grid.second);appliedGrid=grid }
        if(pendingPan!=Vec2(0.0,0.0)) {
            NativeEngine.panPreview(session,pendingPan.x.toFloat(),pendingPan.y.toFloat());pendingPan=Vec2(0.0,0.0)
        }
        pendingConsoleCommand?.let { (token, source) ->
            pendingConsoleCommand = null
            try {
                check(token == session && desiredMode == ViewportMode.Scene &&
                    actualScenePlayback != Playback.Stopped && desiredPlayback != Playback.Stopped) {
                    "The Play runtime changed before Lua could run"
                }
                applyLuaPause(NativeEngine.runPlayLua(token, source.encodeToByteArray(), console.pauseOnLuaError))
                drainConsole()
                refreshLiveInspection(true)
            } catch (failure: Exception) {
                console.commandMessage(failure.message ?: "Play Lua command failed", ConsoleSeverity.ERROR)
            }
        }
        if(desiredMode==ViewportMode.Sandbox || (desiredMode==ViewportMode.Scene && actualScenePlayback!=Playback.Stopped)) {
            val profileScene = desiredMode == ViewportMode.Scene
            if (profileScene) syncLuaProfiler()
            val reload = pendingScriptReload
            pendingScriptReload = null
            val reloadError = if (reload != null && desiredMode == ViewportMode.Scene && reload.selection.run == session) {
                NativeEngine.reloadScript(session, reload.selection.scene, reload.selection.entity,
                    reload.key.encodeToByteArray(), reload.asset.encodeToByteArray(), console.pauseOnLuaError)
            } else null
            applyLuaPause(reloadError)
            val stepRequested = desiredMode == ViewportMode.Scene && actualScenePlayback == Playback.Paused && consumeSceneStep()
            val step = stepRequested && reloadError == null
            if (step) clearLuaPause()
            val advanced = step || actualScenePlayback == Playback.Running
            applyLuaPause(NativeEngine.advanceFrame(session,dt,step,console.pauseOnLuaError))
            if (profileScene && profilingApplied)
                NativeEngine.takeLuaProfile(session, advanced)?.let(luaProfiler::accept)
            if (desiredMode == ViewportMode.Scene) refreshLiveInspection(step || reload != null)
        }
        if (!audioQueued && desiredPlayback == Playback.Running &&
            (desiredMode == ViewportMode.Sandbox || (desiredMode==ViewportMode.Scene && actualScenePlayback==Playback.Running))) {
            audioQueued=true
            val token=session
            EventQueue.invokeLater {
                if (!stopped && session == token && desiredPlayback == Playback.Running &&
                    (desiredMode==ViewportMode.Sandbox || (desiredMode==ViewportMode.Scene && actualScenePlayback==Playback.Running))) {
                    try { NativeEngine.enableAudio(token) } catch (failure: Throwable) { onFailure(failure) }
                } else audioQueued=false
            }
        }
    }
    override fun paintGL() { GL.setCapabilities(capabilities) }
    fun drawView(viewId: ViewId, request: ViewRequest) : Pair<PreviewCamera?,List<Float>> {
        GL.setCapabilities(capabilities)
        val pixelWidth=request.pixelWidth; val pixelHeight=request.pixelHeight
        val game=viewId==ViewId.Game
        if(desiredMode==ViewportMode.Scene) {
            NativeEngine.drawSceneView(session,pixelWidth,pixelHeight,game,false)
            actualGameView=desiredGameView
        } else if(desiredMode==ViewportMode.Sandbox) NativeEngine.drawEmbeddedFrame(session,pixelWidth,pixelHeight)
        else NativeEngine.drawEntityPreview(session,pixelWidth,pixelHeight)
        GL11.glViewport(0,0,pixelWidth,pixelHeight)
        val view = when {
            desiredMode == ViewportMode.Sandbox -> null
            game -> NativeEngine.gameCamera(session)
            else -> NativeEngine.previewView(session)
        }
        if (view != null) {
            frameCamera=PreviewCamera(Vec2(view[0].toDouble(),view[1].toDouble()),Vec2(view[2].toDouble(),view[3].toDouble()),request.logicalWidth,request.logicalHeight)
            if(!game) gizmos?.resize(request.logicalWidth,request.logicalHeight)
            if (!game && (desiredMode!=ViewportMode.Scene || desiredPlayback==Playback.Stopped) && desiredDocument == lastGoodPreview) {
                // Every selected entity is outlined; only the active one gets handles.
                if(desiredMode==ViewportMode.Scene) scene?.let { state ->
                    overlay.outline(state.selection.keys.filter { it!=state.selectedKey && state.entity(it)!=null }.map(state::worldDocument),frameCamera!!,editorSelectionColor)
                }
                // Move, Rotate and Scale draw their own handles at the entity (or the selection center).
                val toolHandles=desiredMode==ViewportMode.Scene && tools.active!=sle.editor.viewport.tools.ViewportToolId.Select
                gizmos?.selected()?.let {
                    val document=if(desiredMode==ViewportMode.Scene) scene?.selectedKey?.let { key -> scene?.worldDocument(key) } else null
                    overlay.draw(it,frameCamera!!,document,editorSelectionColor,entityHandles=!toolHandles)
                }
                if(desiredMode==ViewportMode.Scene) {
                    val view=frameCamera!!
                    toolHost.camera=view
                    toolHost.draw(object : sle.editor.viewport.tools.ToolOverlay {
                        override fun moveHandles(origin: Vec2, active: DragAxis?) =
                            overlay.moveHandles(origin,view,editorAxisColors.first,editorAxisColors.second,editorSelectionColor,active)
                        override fun marquee(from: Vec2, to: Vec2) = overlay.marquee(from,to,view,editorSelectionColor)
                        override fun rotateRing(center: Vec2, radius: Double, active: Boolean, sweep: Pair<Double, Double>?) =
                            overlay.rotateRing(center,radius,view,editorAxisColors.first,editorAxisColors.second,editorSelectionColor,active,sweep)
                        override fun scaleHandles(origin: Vec2, xTip: Vec2, yTip: Vec2, active: DragAxis?) =
                            overlay.scaleHandles(origin,xTip,yTip,view,editorAxisColors.first,editorAxisColors.second,editorSelectionColor,active)
                    })
                }
            }
        }
        if(game) gameMetrics=NativeEngine.sceneGameViewport(session).toList()
        if (desiredMode == ViewportMode.Scene && view != null) luaPausePosition()?.let { world ->
            val projection = checkNotNull(frameCamera)
            val point = if (game) {
                val local = (world - projection.origin).rotate(-view[4].toDouble()) * (1.0 / view[5])
                val x = local.x / projection.size.x
                val y = local.y / projection.size.y
                if (x in 0.0..1.0 && y in 0.0..1.0)
                    Vec2((gameMetrics[0] + x * gameMetrics[2]) * request.logicalWidth / pixelWidth,
                        (pixelHeight - gameMetrics[1] - y * gameMetrics[3]) * request.logicalHeight / pixelHeight)
                else null
            } else projection.screen(world)
            if (point != null) overlay.rotateRing(point,14.0,projection,luaErrorColor,luaErrorColor,luaErrorColor,true,null)
        }
        ++frames
        if (frames == 1) System.err.println("VIEWPORT_FIRST_FRAME_MS="+(System.nanoTime()-openedAt)/1_000_000)
        if (smokeTest && desiredMode == ViewportMode.Sandbox && actualPlayback == Playback.Paused) {
            val pixels=BufferUtils.createByteBuffer(pixelWidth*pixelHeight*4)
            GL11.glReadPixels(0,0,pixelWidth,pixelHeight,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,pixels)
            val fingerprint=pixels.hashCode()
            check(pausedFingerprint == null || pausedFingerprint == fingerprint) { "Paused viewport changed" }
            pausedFingerprint=fingerprint
            if (frames == 34) println("EMBEDDED_PAUSE_FROZEN_PASS")
        }
        if(smokeTest && desiredMode==ViewportMode.Sandbox && frames in listOf(54,59)) verifySandboxDebugPixels(pixelWidth,pixelHeight,frames==54)
        if (smokeTest && frames == 60) verifySandboxPixels(pixelWidth,pixelHeight)
        if (previewSmoke && desiredMode == ViewportMode.Entity && frames in listOf(5,15)) verifyPreviewPixels(pixelWidth,pixelHeight,frames == 15)

        val renderedFrame=frames
        val renderedMode=desiredMode
        EventQueue.invokeLater { if (!stopped) onFrame(renderedFrame,renderedMode) }
            return frameCamera to (if(game) NativeEngine.sceneGameViewport(session).toList() else emptyList())
    }
    private fun verifyPreviewPixels(w: Int,h: Int,changed: Boolean) {
        val pixels=BufferUtils.createByteBuffer(w*h*4)
        GL11.glReadPixels(0,0,w,h,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,pixels)
        var cyan=0
        for (p in 0 until w*h) {
            val r=pixels[p*4].toInt() and 255
            val g=pixels[p*4+1].toInt() and 255
            val b=pixels[p*4+2].toInt() and 255
            if (r < 100 && g > 150 && b > 150) ++cyan
        }
        check(cyan > 40) { "Missing authored collider pixels: $cyan" }
        if (changed) {
            check(previewFingerprint != pixels.hashCode()) { "Editing collider did not change live preview" }
            println("ENTITY_PREVIEW_EDIT_PASS cyan=$cyan")
        } else previewFingerprint=pixels.hashCode()
    }
    fun stopRendering() {
        check(EventQueue.isDispatchThread())
        stopped=true
        clearLiveInspection()

        gizmos?.cancel()
        if (session != 0L) { overlay.dispose(); NativeEngine.destroy(session); destroys++; session=0L }
    }
    override fun disposeGL() {
        GL.setCapabilities(capabilities)
        overlay.dispose()
        if (session != 0L) {
            NativeEngine.destroy(session); destroys++
            session=0L
        }
        GL.setCapabilities(null)
    }
    override fun removeNotify() {
        val retiredContext=context
        stopped=true

        super.removeNotify() // Calls disposeGL in context, then deletes context and peer.
        if ((smokeTest || previewSmoke) && retiredContext != 0L) {
            try { ViewportSmokeChecks.verifyContextDeleted(retiredContext) }
            catch (failure: Throwable) { onFailure(failure) }
        }
    }

    private fun verifySandboxDebugPixels(pixelWidth: Int,pixelHeight: Int,enabled: Boolean) {
        val pixels=BufferUtils.createByteBuffer(pixelWidth*pixelHeight*4)
        GL11.glReadPixels(0,0,pixelWidth,pixelHeight,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,pixels)
        var cyan=0
        for(p in 0 until pixelWidth*pixelHeight) {
            val r=pixels[p*4].toInt() and 255;val g=pixels[p*4+1].toInt() and 255;val b=pixels[p*4+2].toInt() and 255
            if(r<100 && g>180 && b>220) cyan++
        }
        check(if(enabled) cyan>=8 else cyan==0) { "Sandbox colliders enabled=$enabled unexpected draw-only pixels: $cyan" }
        val image=BufferedImage(pixelWidth,pixelHeight,BufferedImage.TYPE_INT_RGB)
        for(y in 0 until pixelHeight) for(x in 0 until pixelWidth) {
            val at=((pixelHeight-y-1)*pixelWidth+x)*4
            image.setRGB(x,y,((pixels[at].toInt() and 255) shl 16) or ((pixels[at+1].toInt() and 255) shl 8) or (pixels[at+2].toInt() and 255))
        }
        ImageIO.write(image,"png",File(System.getProperty("sle.smokeReport")).parentFile.resolve("sandbox-debug-$enabled.png"))
        println("SANDBOX_DEBUG_DRAW_PASS enabled=$enabled cyan=$cyan")
    }
    private fun verifySandboxPixels(pixelWidth: Int, pixelHeight: Int) {
        val pixels = BufferUtils.createByteBuffer(pixelWidth * pixelHeight * 4)
        GL11.glReadPixels(0, 0, pixelWidth, pixelHeight, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixels)
        val image = BufferedImage(pixelWidth, pixelHeight, BufferedImage.TYPE_INT_ARGB)
        var redPixels = 0
        var greenPixels = 0
        for (y in 0 until pixelHeight) {
            for (x in 0 until pixelWidth) {
                val offset = (y * pixelWidth + x) * 4
                val red = pixels[offset].toInt() and 255
                val green = pixels[offset + 1].toInt() and 255
                val blue = pixels[offset + 2].toInt() and 255
                // The player's animation alternates red and blue during live play.
                if (ViewportSmokeChecks.isPlayerColor(red,green,blue)) ++redPixels
                if (green > 150 && red < 120 && blue < 150) ++greenPixels
                image.setRGB(x, pixelHeight - y - 1, (255 shl 24) or (red shl 16) or (green shl 8) or blue)
            }
        }
        val report = File(System.getProperty("sle.smokeReport"))
        report.parentFile.mkdirs()
        ImageIO.write(image, "png", report)
        check(redPixels > 20 && greenPixels > 20) {
            "Sandbox framebuffer missing player/NPC: player=$redPixels npc=$greenPixels; see $report"
        }
        println("EMBEDDED_SMOKE_PASS ${pixelWidth}x$pixelHeight player=$redPixels npc=$greenPixels image=$report")
    }
}
