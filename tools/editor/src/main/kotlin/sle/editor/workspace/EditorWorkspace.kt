package sle.editor.workspace

import androidx.compose.runtime.*
import sle.editor.model.*
import sle.editor.project.ProjectWorkspaceState
import sle.editor.viewport.EditorEngineSession

/** Documents, command context and engine survive every panel/native-host recreation. */
internal class EditorWorkspace(
    val authoring: Result<EntityWorkspaceState>,
    val scene: Result<SceneWorkspaceState>,
    smoke: Boolean = false,
    previewSmoke: Boolean = false,
    val scenePlayback: ScenePlaybackState = ScenePlaybackState(),
    val appearance: sle.editor.ui.theme.EditorAppearanceState = sle.editor.ui.theme.EditorAppearanceState(),
) : AutoCloseable {
    /** Results and failures for the status bar; one per editor, shared by every window. */
    val notices = sle.editor.ui.EditorNotices()
    val project = ProjectWorkspaceState { root -> windows.switchStorage(root?.let(WorkspaceLayoutKey::project) ?: WorkspaceLayoutKey.standalone()) }
    fun startLayoutPersistence() = windows.startPersistence(project.opened?.root?.let(WorkspaceLayoutKey::project) ?: WorkspaceLayoutKey.standalone())
    private val engine = lazy { EditorEngineSession(smoke, previewSmoke = previewSmoke) }
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
