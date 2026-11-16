package sle.editor.project

import sle.editor.ui.EditorFileChooser
import androidx.compose.runtime.*
import kotlinx.coroutines.*
import sle.editor.bridge.NativeEngine
import java.nio.file.Path
import javax.swing.JFileChooser

internal data class ProjectDistributionCommands(val exportGame: ()->Unit,val message: String?)

@Composable
internal fun projectDistributionControls(project: ProjectWorkspaceState): ProjectDistributionCommands {
    val scope=rememberCoroutineScope()
    val current by rememberUpdatedState(project.opened)
    var message by remember(project.opened?.file) { mutableStateOf<String?>(null) }
    return ProjectDistributionCommands(exportGame={
        val opened=project.opened
        if(opened!=null && !project.busy) {
            val runtimeChooser=EditorFileChooser(Path.of("build/debug/runtime/Release").toAbsolutePath().toFile()).apply {
                dialogTitle="Select prepared Windows x64 Release runtime bundle"
                fileSelectionMode=JFileChooser.DIRECTORIES_ONLY
            }
            if(runtimeChooser.showOpenDialog(null)==JFileChooser.APPROVE_OPTION) {
                val runtime=runtimeChooser.selectedFile.toPath()
                val outputChooser=EditorFileChooser(opened.root.parent.toFile()).apply {
                    dialogTitle="Export saved game to a NEW folder (unsaved edits excluded)"
                    fileSelectionMode=JFileChooser.DIRECTORIES_ONLY
                    selectedFile=opened.root.parent.resolve("Game Export").toFile()
                }
                if(outputChooser.showSaveDialog(null)==JFileChooser.APPROVE_OPTION && current==opened && !project.busy) {
                    val output=outputChooser.selectedFile.toPath()
                    project.busy=true;project.error=null;message=null
                    scope.launch {
                        try {
                            val descriptor=withContext(Dispatchers.IO) {
                                ProjectDistribution.export(opened,runtime,output) { staged ->
                                    val prepared=PreparedProject.open(ProjectFiles.open(staged))
                                    // Synchronous release before the exporter removes staged derivative caches.
                                    NativeEngine.releaseProject(prepared.handle)
                                }
                            }
                            if(current==opened) message="Exported saved game to ${descriptor.parent.parent}. Start engine_app.exe."
                        } catch(failure: Exception) {
                            if(failure is CancellationException) throw failure
                            if(current==opened) project.error=failure.message ?: "Game export failed"
                        } finally { project.busy=false }
                    }
                }
            }
        }
    },message=message)
}
