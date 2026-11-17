package sle.editor.command

import java.awt.event.KeyEvent
import sle.editor.viewport.Playback
import sle.editor.viewport.ViewportMode

/**
 * Scene playback. F5/F6/Shift+F5/Ctrl+F5 instead of Unity's Ctrl+P: Ctrl+P and Ctrl+Shift+P are
 * reserved for Quick Open and the Command Palette.
 */
internal object PlayCommands {
    private fun available(ctx: CommandContext) = ctx.mode == ViewportMode.Scene && !ctx.viewportFailed && ctx.scene != null && ctx.playback != null

    val play = EditorCommand(CommandId("play.play"), "Play", defaultChords = listOf(KeyChord.plain(KeyEvent.VK_F5)), playControl = true,
        enabled = { available(it) && it.playback?.playback != Playback.Running }) { ctx ->
        val scene = ctx.scene ?: return@EditorCommand
        try {
            ctx.actions.finishGesture();ctx.playback?.play(scene);scene.error = null
            ctx.actions.showPanel(GamePanel)
        } catch(failure: Exception) { scene.error = failure.message }
    }
    val pause = EditorCommand(CommandId("play.pause"), "Pause", defaultChords = listOf(KeyChord.plain(KeyEvent.VK_F6)), playControl = true,
        enabled = { available(it) && it.playback?.playback == Playback.Running }) { it.playback?.pause() }
    val stop = EditorCommand(CommandId("play.stop"), "Stop", defaultChords = listOf(KeyChord.shift(KeyEvent.VK_F5)), playControl = true,
        enabled = { available(it) && it.playback?.playback != Playback.Stopped }) { it.playback?.stop() }
    /** Launches the saved project in the Game view. */
    val runProject = EditorCommand(CommandId("play.runProject"), "Run Project", defaultChords = listOf(KeyChord.ctrl(KeyEvent.VK_F5)), playControl = true,
        enabled = { it.projectOpen && !it.projectBusy && it.editable && !it.viewportFailed }) { it.actions.runProject() }

    val all = listOf(play, pause, stop, runProject)
}
