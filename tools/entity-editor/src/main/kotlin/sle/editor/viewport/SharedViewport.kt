package sle.editor.viewport

import sle.editor.model.EntityWorkspaceState
import sle.editor.model.SceneWorkspaceState

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel

@Composable
internal fun SharedViewport(
    modifier: Modifier,
    mode: ViewportMode,
    document: String,
    playback: Playback,
    sandboxSmoke: Boolean,
    previewSmoke: Boolean,
    authoring: EntityWorkspaceState? = null,
    scene: SceneWorkspaceState? = null,
    gameView: Boolean = false,
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
            EngineCanvas(sandboxSmoke,{ frame,renderedMode -> frameHandler(frame,renderedMode) },
                { failure -> failureHandler(failure) },{ text -> statusHandler(text) },previewSmoke).also { canvas ->
                canvas.onContextMenu={x,y -> contextMenuHandler(x,y)}
                canvas.setContent(mode,document,playback)
                canvas.setScene(scene)
                canvas.setAuthoring(authoring)
                canvas.setSceneView(gameView) { sceneErrorHandler(it) }
                onCanvas(canvas)
            }
        },
        update={ canvas ->
            canvas.setContent(mode,document,playback);canvas.setScene(scene);canvas.setAuthoring(authoring)
            canvas.setSceneView(gameView) { sceneErrorHandler(it) }
        },
    )
}
