package sle.editor.viewport.experimental

import sle.editor.bridge.NativeEngine
import org.lwjgl.opengl.GL
import org.lwjgl.opengl.GL11.*
import org.lwjgl.opengl.GL12.*
import org.lwjgl.opengl.GL30.*
import org.lwjgl.opengl.awt.AWTGLCanvas
import org.lwjgl.opengl.awt.GLData
import org.lwjgl.system.MemoryUtil
import java.awt.EventQueue
import java.awt.image.BufferedImage
import java.awt.image.DataBufferInt
import java.nio.ByteBuffer

/** Opt-in experiment only. Every method, including disposal, runs on the AWT EDT. */
internal class FeasibilityOwner(val gpu: Boolean = false) : AWTGLCanvas(GLData().apply {
    majorVersion = 4; minorVersion = 3; profile = GLData.Profile.CORE; swapInterval = 0
}) {
    var creates = 0; private set
    var destroys = 0; private set
    var ticks = 0; private set
    var identity = 0L; private set
    var hardware = ""; private set
    var copyNanos = 0L; private set
    var sessionToken = 0L; private set
    private var firstIdentity = 0L
    private lateinit var capabilities: org.lwjgl.opengl.GLCapabilities
    data class SharedFrame(val texture: Int, val width: Int, val height: Int, val generation: Long)
    val sharedFrames = arrayOf(LatestFrame<SharedFrame>(), LatestFrame<SharedFrame>())
    private val source = Target()
    private val targets = arrayOf(Target(), Target())
    val frames = arrayOf(LatestFrame<BufferedImage>(), LatestFrame<BufferedImage>())
    private var requested = arrayOf(1280 to 720, 1280 to 720)
    private var pendingShutdown = false

    fun request(view: Int, width: Int, height: Int) {
        check(EventQueue.isDispatchThread())
        if (requested[view] != width to height) {
            requested[view] = width to height
            targets[view].generation = frames[view].resize(width, height)
            sharedFrames[view].resize(width, height)
        }
    }

    override fun initGL() {
        check(EventQueue.isDispatchThread())
        capabilities = GL.createCapabilities()
        hardware = "${glGetString(GL_VENDOR)} / ${glGetString(GL_RENDERER)} / ${glGetString(GL_VERSION)}"
        identity = context
        firstIdentity = context
        sessionToken = NativeEngine.create()
        creates++
        NativeEngine.loadSandbox(sessionToken)
        targets.forEachIndexed { i, target ->
            target.generation = frames[i].resize(1280, 720)
            sharedFrames[i].resize(1280, 720)
        }
    }

    override fun paintGL() {
        check(EventQueue.isDispatchThread())
        check(context == firstIdentity && context != 0L)
        GL.setCapabilities(capabilities)
        if (pendingShutdown) return
        ticks++
        // Advance/draw the existing runtime exactly once. The second view copies this
        // snapshot: distinct cameras await the session-3 native API split.
        source.ensure(1280, 720, false)
        glBindFramebuffer(GL_FRAMEBUFFER, source.fbo)
        NativeEngine.render(sessionToken, 1280, 720, 1f / 60f)
        val start = System.nanoTime()
        targets.forEachIndexed { i, target ->
            val (w, h) = requested[i]
            if (w > 0 && h > 0) {
                target.ensure(w, h, !gpu)
                glBindFramebuffer(GL_READ_FRAMEBUFFER, source.fbo)
                glBindFramebuffer(GL_DRAW_FRAMEBUFFER, target.fbo)
                glBlitFramebuffer(0, 0, 1280, 720, 0, 0, w, h, GL_COLOR_BUFFER_BIT, GL_LINEAR)
                capture(i, target, w, h)
            }
        }
        if (gpu) glFinish() // producer completion before any shared-context consumer
        glBindFramebuffer(GL_FRAMEBUFFER, 0)
        check(glGetError() == GL_NO_ERROR) { "OpenGL error in frame publication" }
        copyNanos = System.nanoTime() - start
    }

    private fun capture(view: Int, target: Target, w: Int, h: Int) {
        glBindFramebuffer(GL_FRAMEBUFFER, target.fbo)
        // Colored top stripe makes the two independently painted windows distinguishable.
        glEnable(GL_SCISSOR_TEST); glScissor(0, h - 12, w, 12)
        glClearColor(if (view == 0) 0.2f else 0.9f, 0.35f, if (view == 0) 0.9f else 0.2f, 1f)
        glClear(GL_COLOR_BUFFER_BIT); glDisable(GL_SCISSOR_TEST)
        if (gpu) {
            check(sharedFrames[view].publish(target.generation, SharedFrame(target.texture, w, h, target.generation)))
            return
        }
        val bytes = target.bytes!!
        bytes.clear()
        glReadPixels(0, 0, w, h, GL_BGRA, GL_UNSIGNED_INT_8_8_8_8_REV, bytes)
        val image = target.images.first { it !== frames[view].latest }
        val pixels = (image.raster.dataBuffer as DataBufferInt).data
        val ints = bytes.asIntBuffer()
        for (row in 0 until h) {
            ints.position((h - row - 1) * w)
            ints.get(pixels, row * w, w)
        }
        check(frames[view].publish(targets[view].generation, image))
    }

    fun shutdown() {
        check(EventQueue.isDispatchThread())
        pendingShutdown = true
        disposeCanvas() // lwjgl3-awt invokes disposeGL with this context current.
    }

    override fun disposeGL() {
        check(EventQueue.isDispatchThread())
        check(context == firstIdentity)
        GL.setCapabilities(capabilities)
        try {
            if (sessionToken != 0L) {
                NativeEngine.destroy(sessionToken)
                sessionToken = 0
                destroys++
            }
        } finally {
            source.release()
            targets.forEach { it.release() }
            frames.forEach { it.close() }
            sharedFrames.forEach { it.close() }
            GL.setCapabilities(null)
        }
    }

    fun ownerTargetCount(): Int = (targets.toList() + source).count { it.fbo != 0 }

    fun allocatedBytes(): Long = targets.sumOf { if (it.bytes != null) it.width.toLong() * it.height * 12 else 0 } // direct + 2 images

    private class Target {
        var fbo = 0
        var texture = 0
        private var depth = 0
        var width = 0
        var height = 0
        var generation = 0L
        var bytes: ByteBuffer? = null
        var images = emptyList<BufferedImage>()
        fun ensure(w: Int, h: Int, readback: Boolean = true) {
            if (width == w && height == h) return
            release()
            width = w; height = h
            fbo = glGenFramebuffers()
            texture = glGenTextures()
            glBindTexture(GL_TEXTURE_2D, texture)
            glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, w, h, 0, GL_RGBA, GL_UNSIGNED_BYTE, 0L)
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR)
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR)
            glBindFramebuffer(GL_FRAMEBUFFER, fbo)
            glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, texture, 0)
            depth = glGenRenderbuffers()
            glBindRenderbuffer(GL_RENDERBUFFER, depth)
            glRenderbufferStorage(GL_RENDERBUFFER, GL_DEPTH24_STENCIL8, w, h)
            glFramebufferRenderbuffer(GL_FRAMEBUFFER, GL_DEPTH_STENCIL_ATTACHMENT, GL_RENDERBUFFER, depth)
            check(glCheckFramebufferStatus(GL_FRAMEBUFFER) == GL_FRAMEBUFFER_COMPLETE)
            if (readback) {
                bytes = MemoryUtil.memAlloc(w * h * 4)
                images = List(2) { BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB) }
            }
        }
        fun release() {
            if (fbo != 0) glDeleteFramebuffers(fbo)
            if (texture != 0) glDeleteTextures(texture)
            if (depth != 0) glDeleteRenderbuffers(depth)
            bytes?.let { MemoryUtil.memFree(it) }
            images.forEach { it.flush() }
            bytes = null; images = emptyList()
            fbo = 0; texture = 0; depth = 0; width = 0; height = 0
        }
    }
}
