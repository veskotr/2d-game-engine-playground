package sle.editor.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import sle.editor.command.CommandContext
import sle.editor.command.Keymap
import sle.editor.command.PlayCommands
import sle.editor.ui.controls.EditorButton
import sle.editor.ui.theme.LocalEditorMetrics
import sle.editor.viewport.Playback

@Composable
internal fun SceneViewControls(context: () -> CommandContext, keymap: Keymap) {
    val current = context()
    Row(horizontalArrangement = Arrangement.spacedBy(LocalEditorMetrics.current.gap / 2)) {
        for (command in listOf(PlayCommands.play, PlayCommands.pause, PlayCommands.step, PlayCommands.stop)) {
            val label = if (command == PlayCommands.play && current.playback?.playback == Playback.Paused)
                "Resume" else command.label
            EditorButton(
                label,
                onClick = { context().let { if (command.enabled(it)) command.run(it) } },
                enabled = command.enabled(current),
                primary = command == PlayCommands.play,
                help = if (command == PlayCommands.step) "Advance one simulation frame" else keymap.hint(command.id, label),
            )
        }
    }
}
