package sle.editor.viewport

import sle.editor.bridge.NativeEngine
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

/** One host context survives inspector/tab changes; SLE draws directly into it. */
internal class EngineCanvas(
    private val smokeTest: Boolean,
    private val onFrame: (Int, ViewportMode) -> Unit,
    private val onFailure: (Throwable) -> Unit,
    private val onStatus: (String) -> Unit,
    private val previewSmoke: Boolean = false,
) : AWTGLCanvas(GLData().apply {
    majorVersion = 4
    minorVersion = 3
    profile = GLData.Profile.CORE
    swapInterval = 0
}) {
    private var session = 0L
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
    fun previewCamera(): PreviewCamera? = camera
    fun contextIdentity(): Long = context
    fun scenePlayback(): Playback = actualScenePlayback
    fun gameViewActive(): Boolean = actualGameView
    fun runtimeTransforms(): JsonObject = Json.parseToJsonElement(NativeEngine.sceneTransforms(session).toString(Charsets.UTF_8)).jsonObject
    fun gameViewport(): FloatArray = NativeEngine.sceneGameViewport(session)
    fun finishGesture() { gizmos?.finish(); panAnchor=null }
    fun setSceneView(game: Boolean,onError: (String)->Unit) {
        if(desiredGameView!=game) { sendInput(3); finishGesture() }
        desiredGameView=game;sceneError=onError
    }
    private fun editingAllowed() = desiredMode==ViewportMode.Entity ||
        (desiredMode==ViewportMode.Scene && desiredPlayback==Playback.Stopped && !desiredGameView)
    private fun gameInput() = desiredPlayback==Playback.Running && (desiredMode==ViewportMode.Sandbox ||
        (desiredMode==ViewportMode.Scene && actualScenePlayback==Playback.Running && desiredGameView && actualGameView))
    fun authoringReady(): Boolean = actualMode == desiredMode && desiredMode != ViewportMode.Sandbox &&
        (desiredMode!=ViewportMode.Scene || actualScenePlayback==Playback.Stopped) &&
        desiredDocument == lastGoodPreview && (if (desiredMode == ViewportMode.Scene) scene?.serialize()
        else gizmos?.state?.document?.let { document ->
            if(scene!=null) isolatedComponentPreview(document,scene!!.entities.map { it.document.key }).serialize() else document.serialize()
        }) == lastGoodPreview
    fun setScene(value: SceneWorkspaceState?) { scene=value }
    fun setAuthoring(state: EntityWorkspaceState?) {
        val entityOnly=desiredMode==ViewportMode.Scene
        val graph=if(entityOnly) scene else null
        if (gizmos?.state !== state || gizmos?.entityOnly != entityOnly || gizmos?.scene !== graph) {
            gizmos?.cancel(); gizmos=state?.let { ViewportGizmos(it,entityOnly,graph) }
        }
    }

    fun setContent(mode: ViewportMode, document: String?, playback: Playback) {
        check(EventQueue.isDispatchThread())
        if (mode != desiredMode || playback!=desiredPlayback) { sendInput(3); finishGesture() }
        desiredMode = mode
        desiredDocument = document
        desiredPlayback = playback
    }
    fun fitEntity() { if(desiredMode!=ViewportMode.Scene || !desiredGameView) { finishGesture(); pendingFit = true } }
    fun zoomEntity(factor: Float) { if (gizmos?.dragging != true && (desiredMode!=ViewportMode.Scene || !desiredGameView)) pendingZoom *= factor }
    private fun status(text: String) = EventQueue.invokeLater { if (!stopped) onStatus(text) }

    var onContextMenu: ((Int,Int)->Unit)? = null

    init {
        background = Color(23,25,31)
        foreground = Color(200,205,215)
        isFocusable = true
        focusTraversalKeysEnabled = false
        addKeyListener(object : KeyAdapter() {
            override fun keyPressed(event: KeyEvent) = sendKey(event,true)
            override fun keyReleased(event: KeyEvent) = sendKey(event,false)
        })
        addFocusListener(object : FocusAdapter() {
            override fun focusLost(event: FocusEvent) { finishGesture(); sendInput(3) }
        })
        val mouse = object : MouseAdapter() {
            override fun mousePressed(event: MouseEvent) {
                requestFocusInWindow()
                if(event.button==MouseEvent.BUTTON3 && editingAllowed()) {
                    finishGesture()
                    if(desiredMode==ViewportMode.Scene) camera?.let { view -> scene?.let { state ->
                        state.select(ScenePicking.pick(state,view,Vec2(event.x.toDouble(),event.y.toDouble()))?.key)
                        setAuthoring(state.selected)
                    } }
                    onContextMenu?.invoke(event.xOnScreen,event.yOnScreen)
                    return
                }
                if(desiredMode!=ViewportMode.Sandbox && (desiredMode!=ViewportMode.Scene || !desiredGameView) &&
                    (event.button==MouseEvent.BUTTON2 || (event.button==MouseEvent.BUTTON1 && event.isAltDown))) {
                    finishGesture();panAnchor=Vec2(event.x.toDouble(),event.y.toDouble());return
                }
                if (editingAllowed() && authoringReady()) {
                    camera?.let { view ->
                        if (desiredMode == ViewportMode.Scene && event.button == MouseEvent.BUTTON1) {
                            scene?.let { scene ->
                                val pick=ScenePicking.pick(scene,view,Vec2(event.x.toDouble(),event.y.toDouble()))
                                scene.select(pick?.key)
                                setAuthoring(scene.selected)
                            }
                        }
                        gizmos?.press(event,view)
                    }
                    return
                }
                if(desiredMode==ViewportMode.Scene && gameInput() && !insideGame(event)) return
                sendCursor(event)
                sendButton(event,true)
            }
            override fun mouseReleased(event: MouseEvent) {
                if (event.button == MouseEvent.BUTTON1) gizmos?.finish()
                if(event.button==MouseEvent.BUTTON2 || event.button==MouseEvent.BUTTON1) panAnchor=null
                sendButton(event,false)
            }
            override fun mouseMoved(event: MouseEvent) = sendCursor(event)
            override fun mouseDragged(event: MouseEvent) {
                val previous=panAnchor
                if(previous!=null) {
                    val next=Vec2(event.x.toDouble(),event.y.toDouble())
                    camera?.let { pendingPan += Vec2(-(next.x-previous.x)*it.size.x/width,(next.y-previous.y)*it.size.y/height) }
                    panAnchor=next
                } else if (editingAllowed()) gizmos?.move(event) else sendCursor(event)
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
            graphics.fillRect(0,0,width,height)
            graphics.color = foreground
            graphics.drawString("Preparing viewport…",16,28)
        }
    }
    override fun update(graphics: Graphics) = paint(graphics)

    private fun sendInput(type: Int, code: Int = 0, x: Double = 0.0, y: Double = 0.0) {
        if (session != 0L && !stopped && gameInput() && (type == 3 || isFocusOwner))
            NativeEngine.input(session,type,code,x,y)
    }
    private fun sendKey(event: KeyEvent, down: Boolean) {
        if (!gameInput()) { if (down && editingAllowed()) gizmos?.key(event); return }
        val key=engineKey(event)
        if (key >= 0) sendInput(0,key,if (down) 1.0 else 0.0)
    }
    private fun sendButton(event: MouseEvent, down: Boolean) {
        val button=when(event.button) { MouseEvent.BUTTON1 -> 0; MouseEvent.BUTTON3 -> 1; MouseEvent.BUTTON2 -> 2; else -> return }
        sendInput(1,button,if (down) 1.0 else 0.0)
    }
    private fun insideGame(event: MouseEvent): Boolean {
        if(session==0L || width<=0 || height<=0) return false
        val view=NativeEngine.sceneGameViewport(session)
        val x=event.x.toDouble()*framebufferWidth/width
        val y=event.y.toDouble()*framebufferHeight/height
        val top=framebufferHeight-view[1]-view[3]
        return x>=view[0] && x<view[0]+view[2] && y>=top && y<top+view[3]
    }
    private fun sendCursor(event: MouseEvent) {
        if(width<=0 || height<=0) return
        if(desiredMode==ViewportMode.Scene && gameInput()) {
            val view=NativeEngine.sceneGameViewport(session)
            if(view[2]<=0 || view[3]<=0) return
            val top=framebufferHeight-view[1]-view[3]
            sendInput(2,x=(event.x.toDouble()*framebufferWidth/width-view[0])/view[2]*view[4],
                y=(event.y.toDouble()*framebufferHeight/height-top)/view[3]*view[5])
        } else sendInput(2,x=event.x*480.0/width,y=event.y*270.0/height)
    }
    private val timer=Timer(16) {
        if (!stopped && isShowing && width > 0 && height > 0) {
            try { render() }
            catch (failure: Throwable) {
                try { stopRendering() } catch (cleanup: Throwable) { failure.addSuppressed(cleanup) }
                onFailure(failure)
            }
        }
    }
    override fun addNotify() {
        super.addNotify()
        stopped=false
        timer.start()
    }
    override fun initGL() {
        GL.createCapabilities()
        GL11.glClearColor(0.09f,0.10f,0.12f,1f)
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT)
        check(platformCanvas.swapBuffers()) { "Native swapBuffers failed" } // Present a dark surface before loading engine resources.
        System.err.println("VIEWPORT_CONTEXT_READY_MS="+(System.nanoTime()-openedAt)/1_000_000)
        lastFrameNanos=System.nanoTime()
    }
    private fun createSession() {
        session=NativeEngine.create()
        if (smokeTest || previewSmoke) session=ViewportSmokeChecks.verifySessionGuardsAndReopen(session)
        actualMode=null
        actualPlayback=Playback.Running
        actualScenePlayback=Playback.Stopped
        actualGameView=false
        audioQueued=false
    }
    private fun loadPreview(document: String) {
        val mode=desiredMode
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
                val fallback=goodDocuments[mode] ?: if (mode==ViewportMode.Scene) SceneDocument().serialize() else EntityDocument().serialize()
                load(fallback,false)
                lastGoodPreview=fallback
            }
        }
        attemptedDocument=document
    }
    private fun recreateSession() {
        finishGesture()
        if(session!=0L) NativeEngine.destroy(session)
        session=0L;createSession();camera=null;attemptedDocument=null;pendingPan=Vec2(0.0,0.0)
    }
    private fun syncScenePlayback() {
        if(actualScenePlayback==desiredPlayback) return
        if(desiredPlayback==Playback.Stopped) {
            recreateSession()
            desiredDocument?.let(::loadPreview)
            actualMode=ViewportMode.Scene
            actualScenePlayback=Playback.Stopped
        } else if(actualScenePlayback==Playback.Stopped) {
            try {
                NativeEngine.startScenePlay(session,(desiredDocument ?: error("Missing scene")).toByteArray(Charsets.UTF_8))
                actualScenePlayback=Playback.Running
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
    override fun paintGL() {
        check(EventQueue.isDispatchThread())
        val pixelWidth=framebufferWidth
        val pixelHeight=framebufferHeight
        if (pixelWidth <= 0 || pixelHeight <= 0) return
        if (session == 0L) createSession()
        if (actualMode != desiredMode) {
            if(actualScenePlayback!=Playback.Stopped) recreateSession()
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
                NativeEngine.destroy(session)
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
        if(pendingPan!=Vec2(0.0,0.0)) {
            NativeEngine.panPreview(session,pendingPan.x.toFloat(),pendingPan.y.toFloat());pendingPan=Vec2(0.0,0.0)
        }
        val now=System.nanoTime()
        val dt=if (smokeTest || previewSmoke) 1f/60f else ((now-lastFrameNanos)/1_000_000_000.0).coerceIn(0.0,0.1).toFloat()
        lastFrameNanos=now
        if(desiredMode==ViewportMode.Scene) {
            NativeEngine.renderSceneView(session,pixelWidth,pixelHeight,dt,desiredGameView,actualScenePlayback!=Playback.Stopped)
            actualGameView=desiredGameView
        } else NativeEngine.render(session,pixelWidth,pixelHeight,dt)
        if (desiredMode != ViewportMode.Sandbox) {
            val view=NativeEngine.previewView(session)
            camera=PreviewCamera(Vec2(view[0].toDouble(),view[1].toDouble()),Vec2(view[2].toDouble(),view[3].toDouble()),width,height)
            gizmos?.resize(width,height)
            if (editingAllowed() && desiredDocument == lastGoodPreview) gizmos?.selected()?.let {
                val document=if(desiredMode==ViewportMode.Scene) scene?.selectedKey?.let { key -> scene?.worldDocument(key) } else null
                overlay.draw(it,camera!!,document)
            }
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
        if (smokeTest && frames == 60) verifySandboxPixels(pixelWidth,pixelHeight)
        if (previewSmoke && desiredMode == ViewportMode.Entity && frames in listOf(5,15)) verifyPreviewPixels(pixelWidth,pixelHeight,frames == 15)
        check(platformCanvas.swapBuffers()) { "Native swapBuffers failed" }
        val renderedFrame=frames
        val renderedMode=desiredMode
        EventQueue.invokeLater { if (!stopped) onFrame(renderedFrame,renderedMode) }
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
        timer.stop()
        gizmos?.cancel()
        if (session != 0L) runInContext { overlay.dispose(); NativeEngine.destroy(session); session=0L }
    }
    override fun disposeGL() {
        overlay.dispose()
        if (session != 0L) {
            NativeEngine.destroy(session)
            session=0L
        }
        GL.setCapabilities(null)
    }
    override fun removeNotify() {
        val retiredContext=context
        stopped=true
        timer.stop()
        super.removeNotify() // Calls disposeGL in context, then deletes context and peer.
        if ((smokeTest || previewSmoke) && retiredContext != 0L) {
            try { ViewportSmokeChecks.verifyContextDeleted(retiredContext) }
            catch (failure: Throwable) { onFailure(failure) }
        }
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
