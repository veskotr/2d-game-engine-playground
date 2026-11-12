package sle.editor.model

import sle.editor.viewport.Playback

import androidx.compose.runtime.*

/** The runtime receives an immutable source snapshot, never the editable model. */
internal class ScenePlaybackState(private val canonicalize: (String)->String = ::canonicalSceneDocument) {
    var playback by mutableStateOf(Playback.Stopped)
        private set
    var source by mutableStateOf<String?>(null)
        private set
    var gameView by mutableStateOf(false)
    val editing get() = playback==Playback.Stopped
    fun play(scene: SceneWorkspaceState) {
        if(playback==Playback.Running) return
        if(playback==Playback.Stopped) {
            require(scene.entities.all { it.fieldErrors.isEmpty() }) { "Finish invalid fields in every entity before Play" }
            val snapshot=canonicalize(scene.serialize())
            source=snapshot
            gameView=true
        }
        playback=Playback.Running
    }
    fun pause() { if(playback==Playback.Running) playback=Playback.Paused }
    fun stop() { playback=Playback.Stopped;source=null;gameView=false }
}
