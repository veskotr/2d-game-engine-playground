package sle.editor.bridge

import java.io.File

/** Viewport calls need its current context; catalog/validation are context-independent. */
internal object NativeEngine {
    init {
        val library = System.getProperty("sle.nativeLibrary")?.let(::File) ?: requireNotNull(PackagedEngine.library) {
            "Set -Dsle.nativeLibrary to the full path of the sle_editor_native library"
        }
        check(library.isFile) {
            "Engine library missing: $library. Build the CMake sle_editor_native target first."
        }
        System.load(library.absolutePath)
        PackagedEngine.enterAssetRoot()
    }

    external fun prepareProject(path: ByteArray): ByteArray
    // config.json of a built game; same result shape and release as prepareProject.
    external fun prepareGame(path: ByteArray): ByteArray
    external fun releaseProject(handle: Long)
    external fun startProject(session: Long, handle: Long, pauseOnError: Boolean): ByteArray?
    external fun create(): Long
    external fun loadPreview(session: Long, source: ByteArray, preserveView: Boolean = false)
    external fun previewView(session: Long): FloatArray
    external fun gameCamera(session: Long): FloatArray
    external fun loadSandbox(session: Long)
    external fun previewCamera(session: Long, zoom: Float, fit: Boolean)
    external fun enableAudio(session: Long)
    external fun componentCatalog(): String
    external fun resolveStandaloneAsset(asset: ByteArray): ByteArray
    external fun runEditorLua(source: ByteArray, snapshot: ByteArray): ByteArray
    external fun runProjectEditorLua(source: ByteArray, snapshot: ByteArray, filename: ByteArray,
        mode: ByteArray, commandId: ByteArray): ByteArray
    external fun runPlayLua(session: Long, source: ByteArray, pauseOnError: Boolean): ByteArray?
    external fun behaviourSchema(source: ByteArray, filename: ByteArray): ByteArray
    external fun engineVersion(): String
    external fun validatePrefab(source: ByteArray)
    external fun canonicalPrefab(source: ByteArray): ByteArray
    external fun canonicalScene(source: ByteArray): ByteArray
    external fun setEditorProjectSettings(session: Long, root: ByteArray, settings: ByteArray)
    external fun loadScenePreview(session: Long, source: ByteArray, preserveView: Boolean)
    external fun startScenePlay(session: Long, source: ByteArray, pauseOnError: Boolean): ByteArray?
    // Owner frame: drain input and advance once, then draw zero or more views.
    external fun setLuaProfiling(session: Long, enabled: Boolean)
    external fun takeLuaProfile(session: Long, advanced: Boolean): ByteArray?
    external fun advanceFrame(session: Long, dt: Float, step: Boolean, pauseOnError: Boolean): ByteArray?
    external fun playEntityPosition(session: Long, id: Long): FloatArray?
    external fun drawEmbeddedFrame(session: Long, width: Int, height: Int)
    external fun drawSceneView(session: Long, width: Int, height: Int, gameView: Boolean, fit: Boolean)
    external fun renderSceneView(session: Long, width: Int, height: Int, dt: Float, gameView: Boolean, playing: Boolean)
    external fun panPreview(session: Long, dx: Float, dy: Float)
    // Scene/Entity preview grid only; spacing 0 restores the automatic grid.
    external fun setEditorGrid(session: Long, spacing: Float, majorEvery: Int)
    external fun drainConsole(session: Long): ByteArray?
    external fun clearConsole(session: Long)
    external fun reloadScript(session: Long, sceneInstance: Long, entityId: Long, key: ByteArray,
                              asset: ByteArray, pauseOnError: Boolean): ByteArray?
    external fun liveSnapshot(session: Long, sceneInstance: Long, entityId: Long): ByteArray
    external fun sceneTransforms(session: Long): ByteArray
    external fun sceneGameViewport(session: Long): FloatArray
    external fun render(session: Long, width: Int, height: Int, dt: Float)
    // type: key=0, mouse button=1, logical cursor=2, release held input=3.
    external fun input(session: Long, type: Int, code: Int, x: Double, y: Double)
    external fun pause(session: Long, paused: Boolean)
    external fun destroy(session: Long)
}
