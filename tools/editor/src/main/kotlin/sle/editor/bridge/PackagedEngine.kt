package sle.editor.bridge

import org.lwjgl.system.APIUtil
import org.lwjgl.system.JNI
import org.lwjgl.system.MemoryStack
import org.lwjgl.system.MemoryUtil
import java.io.File
import sle.editor.project.GameTarget

/**
 * Packaged distributions carry the engine library and shared `assets/` as Compose app resources.
 * Development runs pass `-Dsle.nativeLibrary` and start in the repository root instead.
 */
internal object PackagedEngine {
    private val windows = System.getProperty("os.name").startsWith("Windows")
    private val resources = System.getProperty("compose.application.resources.dir")?.let(::File)

    val library: File?
        get() = resources?.resolve(if (windows) "sle_editor_native.dll" else "libsle_editor_native.so")?.takeIf { it.isFile }

    /**
     * Prebuilt standalone runtime (engine executable, DLLs, engine assets) shipped with packaged
     * editors. Export Game copies it next to the game data; no compiler is ever needed.
     * Development runs have none and choose a `sle_runtime_bundle` output instead.
     */
    fun runtimeBundle(target: GameTarget): File? {
        val runtime = resources?.resolve("runtime") ?: return null
        return runtime.resolve(target.id).takeIf { it.resolve("sle-runtime.json").isFile }
            ?: runtime.takeIf { windows && target == GameTarget.Windows && it.resolve("sle-runtime.json").isFile }
    }

    val assets: File? get() = resources?.resolve("assets")?.takeIf { it.isDirectory }

    /**
     * The engine resolves `assets/...` against the process working directory. A packaged editor
     * uses the bundled resources as its process directory; Java's cached `user.dir` is unaffected.
     */
    fun enterAssetRoot() {
        val root = resources?.takeIf { it.resolve("assets/shaders").isDirectory } ?: return
        MemoryStack.stackPush().use { stack ->
            val changed = if (windows) {
                val function = APIUtil.apiCreateLibrary("kernel32").getFunctionAddress("SetCurrentDirectoryW")
                JNI.callPI(MemoryUtil.memAddress(stack.UTF16(root.absolutePath)), function) != 0
            } else {
                val function = APIUtil.apiCreateLibrary("libc.so.6").getFunctionAddress("chdir")
                JNI.callPI(MemoryUtil.memAddress(stack.UTF8(root.absolutePath)), function) == 0
            }
            check(changed) { "Cannot enter packaged asset directory $root" }
        }
    }
}
