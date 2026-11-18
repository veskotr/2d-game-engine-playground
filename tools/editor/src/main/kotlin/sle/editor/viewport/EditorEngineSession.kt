package sle.editor.viewport

import org.lwjgl.opengl.GL
import org.lwjgl.opengl.GL11.*
import org.lwjgl.opengl.GL30.*
import java.awt.Dimension
import java.awt.EventQueue
import javax.swing.JFrame
import javax.swing.Timer

/** Workspace lifetime; native commands, GL production and consumption are confined to EDT. */
internal class EditorEngineSession(
    smoke: Boolean,
    onFrame: (Int, ViewportMode) -> Unit = {_,_->},
    onFailure: (Throwable) -> Unit = {},
    onStatus: (String) -> Unit = {},
    previewSmoke: Boolean = false,
    snap: SnapService = SnapService(),
    tools: sle.editor.viewport.tools.ViewportToolState = sle.editor.viewport.tools.ViewportToolState(),
) : AutoCloseable {
    var onFrame = onFrame
    var onFailure = onFailure
    var onStatus = onStatus
    internal val owner = NativeViewportOwner(smoke,{n,m -> this.onFrame(n,m)},{this.onFailure(it)},{this.onStatus(it)},previewSmoke,snap,tools)
    private var host: JFrame? = null
    fun start() {
        check(EventQueue.isDispatchThread())
        if(host!=null || closed) return
        host = JFrame().apply {
        isUndecorated = true
        add(this@EditorEngineSession.owner)
        this@EditorEngineSession.owner.preferredSize = Dimension(16,16)
        pack() // displayable but never visible; presenter peers never own this context
        }
        owner.render()
        timer.start()
    }
    private var generation = 0L
    internal fun nextGeneration(): Long = ++generation
    private val presenters = mutableSetOf<EngineCanvas>()
    private val targets = mutableMapOf<ViewId, Target>()
    private var closed = false
    private var lastNanos = System.nanoTime()
    private val backend = object : SessionBackend {
        override fun advance(dt: Float) = owner.advance(dt)
        override fun draw(view: ViewId, request: ViewRequest, sequence: Long): ViewFrame {
            val target = targets.getOrPut(view) { Target() }
            target.ensure(request.pixelWidth,request.pixelHeight)
            glBindFramebuffer(GL_FRAMEBUFFER,target.fbo)
            glViewport(0,0,request.pixelWidth,request.pixelHeight)
            val (camera,rect) = owner.drawView(view,request)
            glFinish() // producer complete before any consumer uses the shared texture
            glBindFramebuffer(GL_FRAMEBUFFER,0)
            val metrics=if(view==ViewId.Game) owner.gameCameraMetrics() else null
            return ViewFrame(view,sequence,request,target.texture,camera,rect,metrics?.get(4) ?: 0f,metrics?.get(5) ?: 1f)
        }
        override fun input(event: ViewInput) = owner.acceptInput(event)
        override fun releaseInput() = owner.releaseHeldInput()
        override fun close() {
            targets.values.forEach { it.close() }; targets.clear()
            owner.stopRendering()
        }
    }
    private val frames = SessionFrames(backend)
    private val timer = Timer(16) {
        if (!closed) try { tick() } catch (failure: Throwable) {
            timerStop()
            System.err.println("Engine owner stopped: ${failure.message}")
            try { close() } catch(cleanup: Throwable) { failure.addSuppressed(cleanup) }
            this.onFailure(failure)
        }
    }
    init {
        owner.inputSink = ::enqueueInput
        owner.releaseSink = ::releaseInput
    }
    private fun timerStop() { timer.stop() }
    private fun tick() {
        owner.render() // initializes the durable context, without advancing
        val now = System.nanoTime()
        val dt = ((now-lastNanos)/1e9).coerceIn(0.0,.1).toFloat()
        lastNanos = now
        presenters.toList().forEach { it.updateRequest() }
        owner.withContext { frames.tick(dt) }
        timer.delay = owner.schedulerDelay
        presenters.toList().filter { it.isShowing && it.width>0 && it.height>0 }.forEach { it.render() }
    }
    fun setProject(value: sle.editor.project.PreparedProject?) = owner.setProject(value)
    fun setContent(mode: ViewportMode, document: String?, playback: Playback) = owner.setContent(mode,document,playback)
    fun setScene(value: sle.editor.model.SceneWorkspaceState?) = owner.setScene(value)
    fun setAuthoring(value: sle.editor.model.EntityWorkspaceState?) = owner.setAuthoring(value)
    fun setSceneView(game: Boolean, onError: (String)->Unit) = owner.setSceneView(game,onError)
    fun finishGesture() = owner.finishGesture()
    fun fitEntity() = owner.fitEntity()
    fun frameBounds(bounds: Bounds) = owner.frameBounds(bounds)
    fun zoomEntity(factor: Float) = owner.zoomEntity(factor)
    fun setViewRequest(view: ViewId, request: ViewRequest?) {
        check(EventQueue.isDispatchThread())
        frames.setViewRequest(view,request)
        if (request == null || !request.drawable) {
            if(owner.contextIdentity()!=0L) owner.withContext { targets.remove(view)?.close() }
        }
    }
    fun latestFrame(view: ViewId) = frames.latestFrame(view)
    internal fun frameForInput(view: ViewId, request: ViewRequest) = frames.frameForInput(view,request)
    fun enqueueInput(event: ViewInput) { check(EventQueue.isDispatchThread()); frames.enqueueInput(event) }
    fun releaseInput() { check(EventQueue.isDispatchThread()); frames.releaseInput() }
    internal fun attach(presenter: EngineCanvas) { check(!closed); presenters.add(presenter) }
    internal fun detach(presenter: EngineCanvas) {
        presenters.remove(presenter)
        owner.finishGesture()
        setViewRequest(presenter.viewId,null)
        releaseInput()
    }
    override fun close() {
        check(EventQueue.isDispatchThread())
        if (closed) return
        closed = true; timer.stop()
        presenters.toList().forEach { it.disposeCanvas() }
        presenters.clear()
        if(owner.contextIdentity()!=0L) owner.withContext { frames.close() } else frames.close()
        owner.disposeCanvas(); host?.dispose(); host=null
    }
    private class Target : AutoCloseable {
        var fbo=0; var texture=0; private var depth=0
        private var width=0; private var height=0
        fun ensure(w: Int,h: Int) {
            if(width==w && height==h) return
            close(); width=w; height=h
            fbo=glGenFramebuffers(); texture=glGenTextures()
            glBindTexture(GL_TEXTURE_2D,texture)
            glTexImage2D(GL_TEXTURE_2D,0,GL_RGBA8,w,h,0,GL_RGBA,GL_UNSIGNED_BYTE,0L)
            glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MIN_FILTER,GL_LINEAR)
            glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MAG_FILTER,GL_LINEAR)
            glBindFramebuffer(GL_FRAMEBUFFER,fbo)
            glFramebufferTexture2D(GL_FRAMEBUFFER,GL_COLOR_ATTACHMENT0,GL_TEXTURE_2D,texture,0)
            depth=glGenRenderbuffers();glBindRenderbuffer(GL_RENDERBUFFER,depth)
            glRenderbufferStorage(GL_RENDERBUFFER,GL_DEPTH24_STENCIL8,w,h)
            glFramebufferRenderbuffer(GL_FRAMEBUFFER,GL_DEPTH_STENCIL_ATTACHMENT,GL_RENDERBUFFER,depth)
            check(glCheckFramebufferStatus(GL_FRAMEBUFFER)==GL_FRAMEBUFFER_COMPLETE)
        }
        override fun close() {
            if(fbo!=0) glDeleteFramebuffers(fbo)
            if(texture!=0) glDeleteTextures(texture)
            if(depth!=0) glDeleteRenderbuffers(depth)
            fbo=0;texture=0;depth=0;width=0;height=0
        }
    }
}
