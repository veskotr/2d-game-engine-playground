package sle.editor.project

import sle.editor.ui.EditorFileChooser
import androidx.compose.runtime.*
import kotlinx.coroutines.*
import sle.editor.bridge.NativeEngine
import sle.editor.bridge.PackagedEngine
import java.nio.file.Path
import javax.swing.JFileChooser

/** [exporting] is true while an export runs; its results are posted to the notices. */
internal data class ProjectDistributionCommands(val exportGame: ()->Unit,val exporting: Boolean)

@Composable
internal fun projectDistributionControls(project: ProjectWorkspaceState,notices: sle.editor.ui.EditorNotices): ProjectDistributionCommands {
    val scope=rememberCoroutineScope()
    val current by rememberUpdatedState(project.opened)
    var exporting by remember(project.opened?.file) { mutableStateOf(false) }
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
        project.busy=true;exporting=true;notices.info("Exporting saved files only; unsaved edits are excluded.")
        scope.launch {
            try {
                val descriptor=withContext(Dispatchers.IO) {
                    ProjectDistribution.exportAutomatically(opened,runtime) { staged ->
                        val prepared=PreparedProject.open(ProjectFiles.open(staged), validateTileAssets = true)
                        // Release before the exporter removes staged derivative caches.
                        NativeEngine.releaseProject(prepared.handle)
                    }
                }
                if(current==opened) notices.post(sle.editor.ui.NoticeLevel.Success,"Exported saved files to ${descriptor.parent.parent}. Unsaved edits excluded. Start engine_app.exe.",ttlMillis=EXPORT_RESULT_MILLIS)
            } catch(error: Exception) {
                if(error is CancellationException) throw error
                if(current==opened) project.error=error.message ?: "Game export failed"
            } finally { project.busy=false;exporting=false }
        }
    }
    return ProjectDistributionCommands(exportGame=::exportSavedGame,exporting=exporting)
}

/** The export result names a folder to open, so it stays longer than an ordinary success. */
private const val EXPORT_RESULT_MILLIS=15_000L
