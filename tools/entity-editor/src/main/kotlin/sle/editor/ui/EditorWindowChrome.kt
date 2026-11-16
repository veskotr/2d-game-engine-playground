package sle.editor.ui

import androidx.compose.ui.awt.ComposeDialog
import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import org.lwjgl.system.JNI
import org.lwjgl.system.MemoryStack
import org.lwjgl.system.MemoryUtil
import org.lwjgl.system.jawt.JAWT
import org.lwjgl.system.jawt.JAWTFunctions.*
import org.lwjgl.system.jawt.JAWTWin32DrawingSurfaceInfo
import sle.editor.ui.theme.*
import java.awt.*
import java.awt.event.WindowEvent
import javax.swing.SwingUtilities

/**
 * Native window captions follow the editor theme on Windows: dark mode (Windows 10
 * 2004+) and caption/text/border colors (Windows 11). Best effort only: older systems
 * or failures keep the OS caption. Game output and window behavior are untouched.
 */
internal object EditorWindowChrome {
    private const val DWMWA_USE_IMMERSIVE_DARK_MODE=20
    private const val DWMWA_BORDER_COLOR=34
    private const val DWMWA_CAPTION_COLOR=35
    private const val DWMWA_TEXT_COLOR=36
    private val supported=System.getProperty("os.name").startsWith("Windows")
    private val setAttribute: Long by lazy {
        if(!supported) 0L else runCatching {
            org.lwjgl.system.APIUtil.apiCreateLibrary("dwmapi").getFunctionAddress("DwmSetWindowAttribute")
        }.getOrDefault(0L)
    }
    private var mode=EditorThemeMode.Dark
    private var installed=false

    /** Themes every editor window opened from now on, including Compose dialogs and choosers. */
    fun install(initial: EditorThemeMode) {
        check(SwingUtilities.isEventDispatchThread())
        mode=initial
        if(installed) return
        installed=true
        Toolkit.getDefaultToolkit().addAWTEventListener({ event ->
            if(event.id==WindowEvent.WINDOW_OPENED) apply((event as WindowEvent).window)
        },AWTEvent.WINDOW_EVENT_MASK)
    }

    fun update(next: EditorThemeMode) {
        check(SwingUtilities.isEventDispatchThread())
        mode=next
        Window.getWindows().filter { it.isDisplayable }.forEach(::apply)
    }

    /** Safe to call before showing once the window is displayable (for example after pack). */
    fun apply(window: Window) {
        if(setAttribute==0L || !window.isDisplayable || (window !is Frame && window !is Dialog)) return
        val hwnd=runCatching { handle(window) }.getOrDefault(0L)
        if(hwnd==0L) return
        val colors=editorColors(mode)
        MemoryStack.stackPush().use { stack ->
            val value=stack.mallocInt(1)
            fun set(attribute: Int,data: Int) {
                value.put(0,data)
                JNI.callPPI(hwnd,attribute,MemoryUtil.memAddress(value),4,setAttribute)
            }
            set(DWMWA_USE_IMMERSIVE_DARK_MODE,if(mode==EditorThemeMode.Dark) 1 else 0)
            set(DWMWA_CAPTION_COLOR,colorRef(colors.surface))
            set(DWMWA_TEXT_COLOR,colorRef(colors.textPrimary))
            set(DWMWA_BORDER_COLOR,colorRef(colors.borderSubtle))
        }
    }

    private val getAttribute: Long by lazy {
        if(!supported) 0L else runCatching {
            org.lwjgl.system.APIUtil.apiCreateLibrary("dwmapi").getFunctionAddress("DwmGetWindowAttribute")
        }.getOrDefault(0L)
    }

    /** Verification only: the dark-caption flag Windows reports, or null when unavailable. */
    internal fun darkCaption(window: Window): Boolean? {
        if(getAttribute==0L || !window.isDisplayable) return null
        val hwnd=runCatching { handle(window) }.getOrDefault(0L)
        if(hwnd==0L) return null
        return MemoryStack.stackPush().use { stack ->
            val value=stack.callocInt(1)
            if(JNI.callPPI(hwnd,DWMWA_USE_IMMERSIVE_DARK_MODE,MemoryUtil.memAddress(value),4,getAttribute)!=0) null else value.get(0)!=0
        }
    }

    /** COLORREF is 0x00BBGGRR. */
    internal fun colorRef(color: Color): Int {
        val argb=color.toArgb()
        return ((argb shr 16) and 255) or (((argb shr 8) and 255) shl 8) or ((argb and 255) shl 16)
    }

    private fun handle(window: Window): Long {
        val compose=when(window) { is ComposeWindow -> window.windowHandle;is ComposeDialog -> window.windowHandle;else -> 0L }
        return if(compose!=0L) compose else jawtHandle(window)
    }

    private fun jawtHandle(window: Window): Long = MemoryStack.stackPush().use { stack ->
        val awt=JAWT.calloc(stack).version(JAWT_VERSION_1_4)
        if(!JAWT_GetAWT(awt)) return 0L
        val surface=JAWT_GetDrawingSurface(window,awt.GetDrawingSurface()) ?: return 0L
        try {
            if(JAWT_DrawingSurface_Lock(surface,surface.Lock()) and JAWT_LOCK_ERROR!=0) return 0L
            try {
                val info=JAWT_DrawingSurface_GetDrawingSurfaceInfo(surface,surface.GetDrawingSurfaceInfo()) ?: return 0L
                try { JAWTWin32DrawingSurfaceInfo.create(info.platformInfo()).hwnd() }
                finally { JAWT_DrawingSurface_FreeDrawingSurfaceInfo(info,surface.FreeDrawingSurfaceInfo()) }
            } finally { JAWT_DrawingSurface_Unlock(surface,surface.Unlock()) }
        } finally { JAWT_FreeDrawingSurface(surface,awt.FreeDrawingSurface()) }
    }
}
