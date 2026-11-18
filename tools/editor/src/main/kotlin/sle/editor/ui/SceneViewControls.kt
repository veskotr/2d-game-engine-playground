package sle.editor.ui

import sle.editor.ui.controls.*
import sle.editor.ui.theme.*
import sle.editor.model.ScenePlaybackState
import sle.editor.viewport.Playback

import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SceneViewControls(state: ScenePlaybackState,enabled: Boolean,onPlay: ()->Unit,onStop: ()->Unit) {
    Row(horizontalArrangement=Arrangement.spacedBy(LocalEditorMetrics.current.gap/2)) {
        val keys=sle.editor.command.standardKeymap
        EditorButton(if(state.playback==Playback.Paused) "Resume" else "Play",onPlay,
            enabled=enabled && state.playback!=Playback.Running,primary=true,help=keys.hint(sle.editor.command.PlayCommands.play.id))
        EditorButton("Pause",{state.pause()},enabled=enabled && state.playback==Playback.Running,help=keys.hint(sle.editor.command.PlayCommands.pause.id))
        EditorButton("Stop",onStop,enabled=enabled && state.playback!=Playback.Stopped,help=keys.hint(sle.editor.command.PlayCommands.stop.id))
    }
}
