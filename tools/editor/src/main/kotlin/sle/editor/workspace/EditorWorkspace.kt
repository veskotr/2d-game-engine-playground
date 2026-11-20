package sle.editor.workspace

import androidx.compose.runtime.*
import sle.editor.model.*
import sle.editor.project.ProjectWorkspaceState
import sle.editor.viewport.EditorEngineSession
import sle.editor.viewport.SnapSettings

/** Documents, command context and engine survive every panel/native-host recreation. */
internal class EditorWorkspace(
    val authoring: Result<EntityWorkspaceState>,
    val scene: Result<SceneWorkspaceState>,
    smoke: Boolean = false,
    previewSmoke: Boolean = false,
    val scenePlayback: ScenePlaybackState = ScenePlaybackState(),
    val appearance: sle.editor.ui.theme.EditorAppearanceState = sle.editor.ui.theme.EditorAppearanceState(),
    /** Snap settings while no project is open (smokes pass a temporary folder). */
    private val snapStore: sle.editor.viewport.SnapSettingsStore = sle.editor.viewport.SnapSettingsStore(),
) : AutoCloseable {
    /** Results and failures for the status bar; one per editor, shared by every window. */
    val notices = sle.editor.ui.EditorNotices()
    val project: ProjectWorkspaceState = ProjectWorkspaceState(onArtStyleChanged = ::loadSnap) { root -> windows.switchStorage(root?.let(WorkspaceLayoutKey::project) ?: WorkspaceLayoutKey.standalone());loadSnap() }
    fun startLayoutPersistence() = windows.startPersistence(project.opened?.root?.let(WorkspaceLayoutKey::project) ?: WorkspaceLayoutKey.standalone())
    /** Snap steps: per project in `.sle/editor-state.json`, else in user-local prefs ([snapStore]). */
    val snap: sle.editor.viewport.SnapService = sle.editor.viewport.SnapService().also { service ->
        service.load(snapStore.load())
        snapStore.lastFailure?.let(notices::warning)
        // A change is saved at once: to the open project's `.sle/` state, else to user-local prefs.
        service.onChange = { before, value ->
            val opened = project.opened
            val failure = if(opened != null)
                runCatching { sle.editor.project.ProjectFiles.saveSnapChange(opened,uiScale,before,value) }
                    .exceptionOrNull()?.let { "Could not save snap settings: ${it.message}" }
            else snapStore.takeUnless { it.save(value) }?.lastFailure
            failure?.let(notices::warning)
        }
    }
    /** Snap settings follow the open project (its `.sle/` state) or the standalone prefs. Read
     * failures of the project state are reported by the scene recovery that reads the same file. */
    private fun loadSnap(): Unit {
        val opened = project.opened
        if(opened == null) { snap.load(snapStore.load());snapStore.lastFailure?.let(notices::warning);return }
        snap.load(sle.editor.project.ProjectFiles.editorState(opened).snap ?: SnapSettings.projectDefaults(opened.document.settings.artStyle))
    }
    /** The active Scene view tool; per-user state, never an undo step. */
    val tools = sle.editor.viewport.tools.ViewportToolState()
    private val engine = lazy { EditorEngineSession(smoke, previewSmoke = previewSmoke, snap = snap, tools = tools) }
    val engineSession get() = engine.value
    private var tab by mutableStateOf(2)
    var selectedTab: Int
        get() = tab
        set(value) {
            if(tab!=value) { activeEntity?.endGesture();scene.getOrNull()?.selected?.endGesture() }
            tab=value
        }
    var editingSceneKey by mutableStateOf<String?>(null)
    /** True while the temporary Prefab tab holds a document (opened, created or being edited). */
    var prefabSession by mutableStateOf(false)
    var uiScale: Float
        get() = appearance.value.scale
        set(value) { appearance.update(appearance.value.copy(scale=value)) }
    val linkedEntity get() = scene.getOrNull()?.entities?.firstOrNull { it.document.key == editingSceneKey }
    val activeEntity get() = linkedEntity ?: authoring.getOrNull()
    val windows = WorkspaceWindowManager(beforeLayout = {
        if(engine.isInitialized()) { engineSession.finishGesture(); engineSession.releaseInput() }
    }, onShutdown = { if(engine.isInitialized()) engineSession.close() }, onActivate = { panel ->
        // Focusing panels is harmless during Play; opening an entity document is explicit.
        if (panel.value == "scene") { selectedTab=2;scenePlayback.gameView=false }
        if (panel.value == "game" && selectedTab!=1) { selectedTab=2;scenePlayback.gameView=true }
        // Restored layouts can activate the temporary Prefab panel; only an open prefab switches to it.
        if (panel.value == "entity" && scenePlayback.editing && prefabSession) selectedTab=0
    })
    val sceneDocument get() = selectedTab != 0 || linkedEntity != null
    fun saveProjectEditorState(): Boolean = try {
        project.opened?.let { sle.editor.project.ProjectFiles.saveEditorState(it,sle.editor.project.ProjectEditorState(uiScale)) }
        true
    } catch(failure: Exception) {
        project.error="Could not save local editor state: ${failure.message}"
        false
    }
    override fun close() = windows.close()
}
