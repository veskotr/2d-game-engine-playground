package sle.editor.project

import sle.editor.ui.EditorFileChooser
import androidx.compose.runtime.*
import kotlinx.coroutines.*
import sle.editor.bridge.NativeEngine
import sle.editor.bridge.PackagedEngine
import java.nio.file.Path
import javax.swing.JFileChooser

internal data class ProjectDistributionCommands(val exportGame: ()->Unit,val message: String?)

@Composable
internal fun projectDistributionControls(project: ProjectWorkspaceState): ProjectDistributionCommands {
    val scope=rememberCoroutineScope()
    val current by rememberUpdatedState(project.opened)
    var message by remember(project.opened?.file) { mutableStateOf<String?>(null) }
    // Packaged editors use their bundled runtime; development runs choose a prepared one.
    fun runtimeBundle(): Path? {
        PackagedEngine.runtimeBundle?.let { return it.toPath() }
        val runtimeChooser=EditorFileChooser(Path.of("build/debug/runtime/Release").toAbsolutePath().toFile()).apply {
            dialogTitle="Select prepared Windows x64 Release runtime bundle"
            fileSelectionMode=JFileChooser.DIRECTORIES_ONLY
        }
        return if(runtimeChooser.showOpenDialog(null)==JFileChooser.APPROVE_OPTION) runtimeChooser.selectedFile.toPath() else null
    }
    fun exportSavedGame() {
        val opened=project.opened ?: return
        if(project.busy) return
        val runtime=runtimeBundle() ?: return
        if(current!=opened || project.busy) return
        project.busy=true;project.error=null;message="Exporting saved files only; unsaved edits are excluded."
        scope.launch {
            try {
                val descriptor=withContext(Dispatchers.IO) {
                    ProjectDistribution.exportAutomatically(opened,runtime) { staged ->
                        val prepared=PreparedProject.open(ProjectFiles.open(staged))
                        // Release before the exporter removes staged derivative caches.
                        NativeEngine.releaseProject(prepared.handle)
                    }
                }
                if(current==opened) message="Exported saved files to ${descriptor.parent.parent}. Unsaved edits excluded. Start engine_app.exe."
            } catch(error: Exception) {
                if(error is CancellationException) throw error
                if(current==opened) { message=null;project.error=error.message ?: "Game export failed" }
            } finally { project.busy=false }
        }
    }
    return ProjectDistributionCommands(exportGame=::exportSavedGame,message=message)
}
