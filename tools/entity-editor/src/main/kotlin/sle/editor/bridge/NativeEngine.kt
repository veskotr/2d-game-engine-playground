package sle.editor.bridge

import java.io.File

/** Viewport calls need its current context; catalog/validation are context-independent. */
internal object NativeEngine {
    init {
        val library = File(requireNotNull(System.getProperty("sle.nativeLibrary")) {
            "Set -Dsle.nativeLibrary to the full path of sle_editor_native.dll"
        })
        check(library.isFile) {
            "Engine library missing: $library. Build the CMake sle_editor_native target first."
        }
        System.load(library.absolutePath)
    }

    external fun create(): Long
    external fun loadPreview(session: Long, source: ByteArray, preserveView: Boolean = false)
    external fun previewView(session: Long): FloatArray
    external fun loadSandbox(session: Long)
    external fun previewCamera(session: Long, zoom: Float, fit: Boolean)
    external fun enableAudio(session: Long)
    external fun componentCatalog(): String
    external fun validatePrefab(source: ByteArray)
    external fun canonicalPrefab(source: ByteArray): ByteArray
    external fun canonicalScene(source: ByteArray): ByteArray
    external fun loadScenePreview(session: Long, source: ByteArray, preserveView: Boolean)
    external fun startScenePlay(session: Long, source: ByteArray)
    external fun renderSceneView(session: Long, width: Int, height: Int, dt: Float, gameView: Boolean, playing: Boolean)
    external fun panPreview(session: Long, dx: Float, dy: Float)
    external fun sceneTransforms(session: Long): ByteArray
    external fun sceneGameViewport(session: Long): FloatArray
    external fun render(session: Long, width: Int, height: Int, dt: Float)
    // type: key=0, mouse button=1, logical cursor=2, release held input=3.
    external fun input(session: Long, type: Int, code: Int, x: Double, y: Double)
    external fun pause(session: Long, paused: Boolean)
    external fun destroy(session: Long)
}
