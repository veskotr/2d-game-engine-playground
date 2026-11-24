package sle.editor.viewport

import org.lwjgl.opengl.GL
import org.lwjgl.opengl.GL11.*
import org.lwjgl.opengl.GL30.*
import org.lwjgl.opengl.awt.AWTGLCanvas
import org.lwjgl.opengl.awt.GLData
import sle.editor.model.EntityWorkspaceState
import sle.editor.model.SceneWorkspaceState
import sle.editor.project.PreparedProject
import java.awt.EventQueue
import java.awt.event.*

/**
 * GLData swap interval for editor canvases. lwjgl3-awt fails context creation when an interval is
 * requested but GLX does not report swap control (e.g. WSLg's Mesa). Windows keeps 0; elsewhere
 * the driver default applies.
 */
internal val canvasSwapInterval: Int? = if (System.getProperty("os.name").startsWith("Windows")) 0 else null

/** Replaceable presenter. Owns its read FBO/context only; commands go to the workspace owner. */
internal class EngineCanvas(
    internal val engine: EditorEngineSession,
    initialView: ViewId = ViewId.Scene,
) : AWTGLCanvas(GLData().apply {
    majorVersion=4;minorVersion=3;profile=GLData.Profile.CORE;swapInterval=canvasSwapInterval
    shareContext=engine.owner
}) {
    internal var viewId = initialView; private set
    private val owner get() = engine.owner
    private var generation=0L
    private var request: ViewRequest?=null
    private lateinit var capabilities: org.lwjgl.opengl.GLCapabilities
    private var readFbo=0
    var captureFile: java.io.File? = null
    private var mode=ViewportMode.Entity
    private var game=false
    fun selectView(view: ViewId) {
        if(viewId==view) return
        finishGesture(); engine.setViewRequest(viewId,null)
        viewId=view;request=null;generation=engine.nextGeneration()
    }
    fun setProject(value: PreparedProject?) = engine.setProject(value)
    fun setContent(mode: ViewportMode,document: String?,playback: Playback) {
        this.mode=mode
        selectView(if(mode==ViewportMode.Entity) ViewId.Entity else if(mode==ViewportMode.Sandbox || game) ViewId.Game else ViewId.Scene)
        engine.setContent(mode,document,playback)
    }
    fun setScene(value: SceneWorkspaceState?) = engine.setScene(value)
    fun setAuthoring(value: EntityWorkspaceState?) = engine.setAuthoring(value)
    fun setSceneView(game: Boolean,onError: (String)->Unit) {
        this.game=game
        selectView(if(mode==ViewportMode.Entity) ViewId.Entity else if(mode==ViewportMode.Sandbox || game) ViewId.Game else ViewId.Scene)
        engine.setSceneView(game,onError)
    }
    var onContextMenu: ((Int,Int)->Unit)?
        get()=owner.onContextMenu
        set(value) { owner.onContextMenu=value }
    fun previewCamera() = engine.latestFrame(viewId)?.camera
    fun contextIdentity() = owner.contextIdentity()
    fun scenePlayback() = owner.scenePlayback()
    fun gameViewActive() = viewId==ViewId.Game && engine.latestFrame(viewId)!=null
    fun runtimeTransforms() = owner.runtimeTransforms()
    fun gameViewport() = engine.latestFrame(viewId)?.gameRect?.toFloatArray() ?: floatArrayOf()
    fun finishGesture() = engine.finishGesture()
    fun fitEntity() { activate();engine.fitEntity() }
    /** Frame Selected: fits the Scene view to [bounds] in world units. */
    fun frameBounds(bounds: Bounds) { activate();engine.frameBounds(bounds) }
    /** The active Scene tool's handles in this canvas's pixels (smokes aim at them). */
    fun toolHandles() = run { activate();owner.toolHandles() }
    fun zoomEntity(factor: Float) { activate();engine.zoomEntity(factor) }
    fun authoringReady() = owner.authoringReady() && engine.latestFrame(viewId)!=null
    fun stopRendering() = engine.close()
    private fun activate(): Boolean {
        updateRequest()
        val frame=request?.let { engine.frameForInput(viewId,it) }
        owner.activateInput(viewId,frame,isFocusOwner)
        return frame!=null
    }
    init {
        isFocusable=true;focusTraversalKeysEnabled=false
        addKeyListener(object : KeyAdapter() {
            override fun keyPressed(e: KeyEvent) { if(activate()) owner.keyListeners.forEach { it.keyPressed(e) } }
            override fun keyReleased(e: KeyEvent) { if(activate()) owner.keyListeners.forEach { it.keyReleased(e) } else engine.releaseInput() }
        })
        addFocusListener(object : FocusAdapter() {
            override fun focusLost(e: FocusEvent) { finishGesture();engine.releaseInput() }
        })
        val mouse=object : MouseAdapter() {
            override fun mousePressed(e: MouseEvent) { requestFocusInWindow();if(activate()) owner.mouseListeners.forEach { it.mousePressed(e) } }
            override fun mouseReleased(e: MouseEvent) { if(activate()) owner.mouseListeners.forEach { it.mouseReleased(e) } else { finishGesture();engine.releaseInput() } }
            override fun mouseExited(e: MouseEvent) { owner.mouseListeners.forEach { it.mouseExited(e) } }
            override fun mouseMoved(e: MouseEvent) { if(activate()) owner.mouseMotionListeners.forEach { it.mouseMoved(e) } }
            override fun mouseDragged(e: MouseEvent) { if(activate()) owner.mouseMotionListeners.forEach { it.mouseDragged(e) } }
            override fun mouseWheelMoved(e: MouseWheelEvent) { if(activate()) owner.mouseWheelListeners.forEach { it.mouseWheelMoved(e) } }
        }
        addMouseListener(mouse);addMouseMotionListener(mouse);addMouseWheelListener(mouse)
    }
    internal fun updateRequest() {
        val next=ViewRequest(framebufferWidth,framebufferHeight,width,height,isShowing,generation)
        if(next!=request) {
            finishGesture()
            generation=engine.nextGeneration()
            request=next.copy(generation=generation)
        }
        engine.setViewRequest(viewId,request)
    }
    override fun addNotify() { super.addNotify(); engine.attach(this) }
    override fun removeNotify() { engine.detach(this);super.removeNotify(); request=null }
    override fun initGL() { capabilities=GL.createCapabilities();readFbo=glGenFramebuffers() }
    override fun paintGL() {
        check(EventQueue.isDispatchThread());GL.setCapabilities(capabilities)
        if(System.getProperty("os.name").startsWith("Windows") && GL.getCapabilitiesWGL().WGL_EXT_swap_control)
            org.lwjgl.opengl.WGLEXTSwapControl.wglSwapIntervalEXT(if(viewId==ViewId.Game && owner.presentationVsync) 1 else 0)
        val frame=engine.latestFrame(viewId) ?: return
        if(frame.request!=request) return
        glBindFramebuffer(GL_READ_FRAMEBUFFER,readFbo)
        glFramebufferTexture2D(GL_READ_FRAMEBUFFER,GL_COLOR_ATTACHMENT0,GL_TEXTURE_2D,frame.texture,0)
        check(glCheckFramebufferStatus(GL_READ_FRAMEBUFFER)==GL_FRAMEBUFFER_COMPLETE)
        glBindFramebuffer(GL_DRAW_FRAMEBUFFER,0)
        glBlitFramebuffer(0,0,frame.request.pixelWidth,frame.request.pixelHeight,0,0,framebufferWidth,framebufferHeight,GL_COLOR_BUFFER_BIT,GL_LINEAR)
        captureFile?.let { file ->
            val w=framebufferWidth;val h=framebufferHeight
            val bytes=org.lwjgl.system.MemoryUtil.memAlloc(w*h*4)
            try {
                glBindFramebuffer(GL_READ_FRAMEBUFFER,0);glReadBuffer(GL_BACK)
                glReadPixels(0,0,w,h,GL_RGBA,GL_UNSIGNED_BYTE,bytes)
                val image=java.awt.image.BufferedImage(w,h,java.awt.image.BufferedImage.TYPE_INT_RGB)
                for(y in 0 until h) for(x in 0 until w) {
                    val at=((h-y-1)*w+x)*4
                    image.setRGB(x,y,((bytes[at].toInt() and 255) shl 16) or ((bytes[at+1].toInt() and 255) shl 8) or (bytes[at+2].toInt() and 255))
                }
                javax.imageio.ImageIO.write(image,"png",file)
            } finally { org.lwjgl.system.MemoryUtil.memFree(bytes) }
            captureFile=null
        }
        glBindFramebuffer(GL_READ_FRAMEBUFFER,readFbo)
        glFramebufferTexture2D(GL_READ_FRAMEBUFFER,GL_COLOR_ATTACHMENT0,GL_TEXTURE_2D,0,0)
        swapBuffers();glFinish() // consumer complete before owner reuses/deletes texture
    }
    override fun disposeGL() {
        GL.setCapabilities(capabilities)
        if(readFbo!=0) glDeleteFramebuffers(readFbo)
        readFbo=0;GL.setCapabilities(null)
    }
}
