package sle.editor.ui

import sle.editor.model.ScenePlaybackState
import sle.editor.viewport.Playback

import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SceneViewControls(state: ScenePlaybackState,enabled: Boolean,onPlay: ()->Unit,onStop: ()->Unit) {
    FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
        Button(onClick=onPlay,enabled=enabled && state.playback!=Playback.Running) {
            Text(if(state.playback==Playback.Paused) "Resume" else "Play")
        }
        OutlinedButton(onClick={state.pause()},enabled=enabled && state.playback==Playback.Running) { Text("Pause") }
        OutlinedButton(onClick=onStop,enabled=enabled && state.playback!=Playback.Stopped) { Text("Stop") }
    }
}
