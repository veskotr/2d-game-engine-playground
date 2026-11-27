package sle.editor.model

import sle.editor.viewport.Playback
import sle.editor.viewport.LiveInspection
import sle.editor.project.PreparedProject

import androidx.compose.runtime.*

/** The runtime receives an immutable source snapshot, never the editable model. */
internal class ScenePlaybackState(private val canonicalize: (String)->String = ::canonicalSceneDocument) {
    var playback by mutableStateOf(Playback.Stopped)
        private set
    var source by mutableStateOf<String?>(null)
        private set
    var project by mutableStateOf<PreparedProject?>(null)
        private set
    var playRevision = 0L
        private set
    private var keepBaseline: KeepPlayBaseline? = null
    val keepChangesRevision get() = keepBaseline?.revision
    fun playProject(value: PreparedProject) {
        require(editing) { "Stop playback before launching a project" }
        playRevision++
        project=value;source=value.source;gameView=true;playback=Playback.Running
    }
    private var stepRequested = false
    var gameView by mutableStateOf(false)
    val editing get() = playback==Playback.Stopped
    fun play(scene: SceneWorkspaceState) {
        if(playback==Playback.Running) return
        if(playback==Playback.Stopped) {
            require(scene.entities.all { it.fieldErrors.isEmpty() }) { "Finish invalid fields in every entity before Play" }
            val snapshot=canonicalize(scene.serialize())
            scene.entities.forEach { it.endGesture() }
            keepBaseline = KeepPlayBaseline(++playRevision, scene, SceneDocument.parse(snapshot))
            source=canonicalize(scene.previewSerialize())
            gameView=true
        }
        stepRequested=false
        playback=Playback.Running
    }
    fun pause() { if(playback==Playback.Running) playback=Playback.Paused }
    fun requestStep() { if(playback==Playback.Paused) stepRequested=true }
    fun consumeStep(): Boolean {
        val requested=stepRequested
        stepRequested=false
        return requested && playback==Playback.Paused
    }
    fun previewKeepChanges(scene: SceneWorkspaceState, live: LiveInspection?): KeepPlayPreview? =
        if (playback == Playback.Paused && live != null) keepBaseline?.preview(scene, live) else null

    fun keepChanges(scene: SceneWorkspaceState, live: LiveInspection?, preview: KeepPlayPreview) {
        require(previewKeepChanges(scene, live) == preview) { "The Play preview changed. Review the current values." }
        checkNotNull(keepBaseline).apply(scene, preview)
    }

    fun stop() { stepRequested=false;playback=Playback.Stopped;source=null;project=null;gameView=false;keepBaseline=null }
}
