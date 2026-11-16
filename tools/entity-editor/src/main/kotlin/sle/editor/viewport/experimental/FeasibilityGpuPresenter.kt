package sle.editor.viewport.experimental

import org.lwjgl.opengl.GL
import org.lwjgl.opengl.GL11.*
import org.lwjgl.opengl.GL30.*
import org.lwjgl.opengl.awt.AWTGLCanvas
import org.lwjgl.opengl.awt.GLData
import org.lwjgl.system.MemoryUtil
import java.awt.EventQueue
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

/** Owns only its local read FBO/context. Owner textures and Runtime never transfer ownership. */
internal class FeasibilityGpuPresenter(
    private val owner: FeasibilityOwner,
    private val view: Int,
    private val onPaint: (Long) -> Unit,
    private val onContext: (String) -> Unit,
) : AWTGLCanvas(GLData().apply {
    majorVersion = 4; minorVersion = 3; profile = GLData.Profile.CORE
    swapInterval = 0
    shareContext = owner
}) {
    private lateinit var capabilities: org.lwjgl.opengl.GLCapabilities
    private var readFbo = 0
    var captureFile: File? = null
    var paints = 0; private set
    override fun initGL() {
        capabilities = GL.createCapabilities()
        readFbo = glGenFramebuffers()
        onContext("GPU presenter $view context=$context owner=${owner.identity}; thread=${Thread.currentThread().name}")
        check(context != 0L && context != owner.identity)
    }
    override fun paintGL() {
        check(EventQueue.isDispatchThread())
        GL.setCapabilities(capabilities)
        val frame = owner.sharedFrames[view].latest ?: return
        val start = System.nanoTime()
        check(glIsTexture(frame.texture)) { "Owner texture is not shared" }
        glBindFramebuffer(GL_READ_FRAMEBUFFER, readFbo)
        glFramebufferTexture2D(GL_READ_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, frame.texture, 0)
        check(glCheckFramebufferStatus(GL_READ_FRAMEBUFFER) == GL_FRAMEBUFFER_COMPLETE)
        glBindFramebuffer(GL_DRAW_FRAMEBUFFER, 0)
        glBlitFramebuffer(0, 0, frame.width, frame.height, 0, 0, framebufferWidth, framebufferHeight, GL_COLOR_BUFFER_BIT, GL_LINEAR)
        captureFile?.let { file ->
            val w = framebufferWidth; val h = framebufferHeight
            val bytes = MemoryUtil.memAlloc(w * h * 4)
            try {
                glBindFramebuffer(GL_READ_FRAMEBUFFER, 0)
                glReadBuffer(GL_BACK)
                glReadPixels(0, 0, w, h, GL_RGBA, GL_UNSIGNED_BYTE, bytes)
                val image = BufferedImage(w, h, BufferedImage.TYPE_INT_RGB)
                for (y in 0 until h) for (x in 0 until w) {
                    val at = ((h - y - 1) * w + x) * 4
                    val color = ((bytes.get(at).toInt() and 255) shl 16) or
                        ((bytes.get(at+1).toInt() and 255) shl 8) or (bytes.get(at+2).toInt() and 255)
                    image.setRGB(x, y, color)
                }
                check(image.getRGB(5, 5) and 0xffffff != 0) { "Shared texture presentation is blank" }
                ImageIO.write(image, "png", file)
            } finally { MemoryUtil.memFree(bytes) }
            captureFile = null
        }
        swapBuffers()
        glFinish() // consumer completion before owner may overwrite/delete this texture
        check(glGetError() == GL_NO_ERROR)
        paints++
        onPaint(System.nanoTime() - start)
    }
    override fun disposeGL() {
        check(EventQueue.isDispatchThread())
        GL.setCapabilities(capabilities)
        if (readFbo != 0) glDeleteFramebuffers(readFbo)
        readFbo = 0
        GL.setCapabilities(null)
        onContext("GPU presenter $view context=$context disposed; owner=${owner.identity}")
    }
}
