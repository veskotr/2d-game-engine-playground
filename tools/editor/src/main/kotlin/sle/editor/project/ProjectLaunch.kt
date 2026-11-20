package sle.editor.project

import androidx.compose.runtime.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import sle.editor.bridge.NativeEngine
import sle.editor.model.ScenePlaybackState
import java.util.concurrent.CompletableFuture

internal data class PreparedProject(
    val handle: Long, val source: String, val settings: ProjectGameSettings, val boundKeys: Set<Int> = emptySet(),
) {
    fun release() { CompletableFuture.runAsync { NativeEngine.releaseProject(handle) } }
    companion object {
        fun open(project: OpenedProject): PreparedProject {
            // A changed descriptor requires reopen, just as settings Save does.
            require(ProjectFiles.open(project.file).document==project.document) { "Project settings changed on disk; reopen before Run Project" }
            val json=Json.parseToJsonElement(NativeEngine.prepareProject(project.file.toString().toByteArray(Charsets.UTF_8)).toString(Charsets.UTF_8)).jsonObject
            return PreparedProject(json.getValue("handle").jsonPrimitive.long,json.getValue("source").jsonPrimitive.content,
                ProjectGameSettings(json.getValue("title").jsonPrimitive.content,json.getValue("width").jsonPrimitive.int,
                    json.getValue("height").jsonPrimitive.int,json.getValue("vsync").jsonPrimitive.boolean,
                    targetFPS=json.getValue("targetFPS").jsonPrimitive.int), project.document.settings.boundKeys())
        }
    }
}

@Composable
internal fun projectRunAction(project: ProjectWorkspaceState,playback: ScenePlaybackState,onLaunch: ()->Unit): ()->Unit {
    val scope=rememberCoroutineScope()
    val launchView by rememberUpdatedState(onLaunch)
    val active=playback.project
    DisposableEffect(active?.handle) { onDispose { active?.release() } }
    return {
        val current=project.opened
        if(current!=null && !project.busy && playback.editing) {
            project.busy=true;project.error=null
            scope.launch {
                var prepared: PreparedProject?=null
                try {
                    withContext(Dispatchers.IO) { prepared=PreparedProject.open(current) }
                    if(project.opened!=current || !playback.editing) return@launch
                    val snapshot=prepared ?: error("Missing project launch snapshot")
                    playback.playProject(snapshot);prepared=null;launchView()
                } catch(failure: Exception) {
                    if(failure is CancellationException) throw failure
                    project.error=failure.message ?: "Could not launch project"
                } finally { prepared?.release();project.busy=false }
            }
        }
    }
}
