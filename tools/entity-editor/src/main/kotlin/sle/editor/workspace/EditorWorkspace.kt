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
        if (panel.value == "entity" && scenePlayback.editing) selectedTab=0
    })
    val sceneDocument get() = selectedTab != 0 || linkedEntity != null
    override fun close() = windows.close()
}
