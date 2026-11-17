package sle.editor.viewport

import sle.editor.model.EntityWorkspaceState
import sle.editor.model.SceneWorkspaceState

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel

@Composable
internal fun SharedViewport(
    modifier: Modifier,
    engine: EditorEngineSession,
    mode: ViewportMode,
    document: String,
    playback: Playback,
    sandboxSmoke: Boolean,
    previewSmoke: Boolean,
    authoring: EntityWorkspaceState? = null,
    scene: SceneWorkspaceState? = null,
    gameView: Boolean = false,
    project: sle.editor.project.PreparedProject? = null,
    onSceneError: (String)->Unit = {},
    onContextMenu: (Int,Int)->Unit = {_,_->},
    onCanvas: (EngineCanvas) -> Unit,
    onFrame: (Int, ViewportMode) -> Unit,
    onStatus: (String) -> Unit,
    onFailure: (Throwable) -> Unit,
) {
    val contextMenuHandler by rememberUpdatedState(onContextMenu)
    val frameHandler by rememberUpdatedState(onFrame)
    val statusHandler by rememberUpdatedState(onStatus)
    val failureHandler by rememberUpdatedState(onFailure)
    val sceneErrorHandler by rememberUpdatedState(onSceneError)
    SwingPanel(
        modifier=modifier,
        factory={
            engine.onFrame={ frame,mode -> frameHandler(frame,mode) }
            engine.onStatus={ statusHandler(it) };engine.onFailure={ failureHandler(it) }
            engine.start()
            EngineCanvas(engine).also { canvas ->
                canvas.onContextMenu={x,y -> contextMenuHandler(x,y)}
                canvas.setProject(project);canvas.setContent(mode,document,playback)
                canvas.setScene(scene)
                canvas.setAuthoring(authoring)
                canvas.setSceneView(gameView) { sceneErrorHandler(it) }
                onCanvas(canvas)
            }
        },
        update={ canvas ->
            canvas.setProject(project);canvas.setContent(mode,document,playback);canvas.setScene(scene);canvas.setAuthoring(authoring)
            canvas.setSceneView(gameView) { sceneErrorHandler(it) }
        },
    )
}
