package sle.editor.smoke

import sle.editor.bridge.NativeEngine
import sle.editor.viewport.EngineCanvas

import org.lwjgl.opengl.WGL
import java.util.concurrent.atomic.AtomicReference
import java.awt.Rectangle
import java.awt.Robot
import java.io.File
import javax.imageio.ImageIO

/** Extra assertions used exclusively by the finite Windows GUI smoke run. */
internal object ViewportSmokeChecks {
    fun isPlayerColor(r: Int,g: Int,b: Int): Boolean = (r>70 || b>70) && r+b>120 && g<70
    fun verifyDesktopSandbox(canvas: EngineCanvas) {
        val image=Robot().createScreenCapture(Rectangle(canvas.locationOnScreen,canvas.size))
        var player=0
        var npc=0
        for (y in 0 until image.height) for (x in 0 until image.width) {
            val pixel=image.getRGB(x,y)
            val r=(pixel shr 16) and 255
            val g=(pixel shr 8) and 255
            val b=pixel and 255
            if (isPlayerColor(r,g,b)) ++player
            if (g>150 && r<120 && b<150) ++npc
        }
        val report=File(System.getProperty("sle.smokeReport")).parentFile.resolve("sandbox-desktop.png")
        ImageIO.write(image,"png",report)
        check(player>20 && npc>20) { "Sandbox missing on desktop: player=$player npc=$npc; see $report" }
        println("SANDBOX_DESKTOP_PASS player=$player npc=$npc")
    }

    fun verifySessionGuardsAndReopen(session: Long): Long {
        check(runCatching { NativeEngine.create() }.exceptionOrNull() is IllegalStateException)
        check(runCatching { NativeEngine.render(session, 0, 270, 1f / 60f) }.exceptionOrNull() is IllegalStateException)
        val foreignFailure = AtomicReference<Throwable>()
        Thread {
            try {
                NativeEngine.render(session, 480, 270, 1f / 60f)
            } catch (failure: Throwable) {
                foreignFailure.set(failure)
            }
        }.apply { start(); join() }
        check(foreignFailure.get() is IllegalStateException) { "Foreign-thread native call was accepted" }
        NativeEngine.destroy(session)
        check(runCatching { NativeEngine.render(session, 480, 270, 1f / 60f) }.exceptionOrNull() is IllegalStateException)
        check(runCatching { NativeEngine.destroy(session) }.exceptionOrNull() is IllegalStateException)
        return NativeEngine.create().also { println("EMBEDDED_SESSION_GUARDS_PASS") }
    }

    fun verifyContextDeleted(retiredContext: Long) {
        // An already-deleted WGL context must be rejected. If it is still live,
        // this call also cleans it up before reporting the resource leak.
        check(!WGL.wglDeleteContext(null, retiredContext)) { "AWT viewport leaked its OpenGL context" }
        println("EMBEDDED_CONTEXT_CLEANUP_PASS")
    }
}
