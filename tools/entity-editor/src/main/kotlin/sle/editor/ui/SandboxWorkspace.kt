package sle.editor.ui

import sle.editor.viewport.Playback

import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

@Composable
internal fun SandboxControls(playback: Playback, enabled: Boolean, change: (Playback) -> Unit) {
    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        Button(onClick={ change(Playback.Running) },enabled=enabled && playback != Playback.Running) {
            Text(if (playback == Playback.Paused) "Resume" else "Run")
        }
        Button(onClick={ change(Playback.Paused) },enabled=enabled && playback == Playback.Running) { Text("Pause") }
        Button(onClick={ change(Playback.Stopped) },enabled=enabled && playback != Playback.Stopped) { Text("Stop") }
    }
}
