package sle.editor.ui

import sle.editor.viewport.Playback

import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.Composable
import sle.editor.ui.controls.EditorButton
import sle.editor.ui.theme.LocalEditorMetrics

@Composable
internal fun SandboxControls(playback: Playback, enabled: Boolean, change: (Playback) -> Unit) {
    Row(horizontalArrangement=Arrangement.spacedBy(LocalEditorMetrics.current.gap)) {
        EditorButton(if(playback==Playback.Paused) "Resume" else "Run",{change(Playback.Running)},enabled=enabled && playback!=Playback.Running)
        EditorButton("Pause",{change(Playback.Paused)},enabled=enabled && playback==Playback.Running)
        EditorButton("Stop",{change(Playback.Stopped)},enabled=enabled && playback!=Playback.Stopped)
    }
}
