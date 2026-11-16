package sle.editor.project

import sle.editor.ui.EditorFileChooser
import androidx.compose.runtime.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import sle.editor.bridge.NativeEngine
import sle.editor.bridge.PackagedEngine
import java.nio.file.Path
import javax.swing.JFileChooser

internal data class ProjectDistributionCommands(val exportGame: ()->Unit,val message: String?,val buildGame: ()->Unit)

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
    // Both deliveries take a runtime bundle, then a NEW output folder.
    fun deliver(chooserTitle: String,suggested: String,work: (OpenedProject,Path,Path)->String,failure: String) {
        val opened=project.opened
        if(opened!=null && !project.busy) {
            val runtime=runtimeBundle()
            if(runtime!=null) {
                val outputChooser=EditorFileChooser(opened.root.parent.toFile()).apply {
                    dialogTitle=chooserTitle
                    fileSelectionMode=JFileChooser.DIRECTORIES_ONLY
                    selectedFile=opened.root.parent.resolve(suggested).toFile()
                }
                if(outputChooser.showSaveDialog(null)==JFileChooser.APPROVE_OPTION && current==opened && !project.busy) {
                    val output=outputChooser.selectedFile.toPath()
                    project.busy=true;project.error=null;message=null
                    scope.launch {
                        try {
                            val done=withContext(Dispatchers.IO) { work(opened,runtime,output) }
                            if(current==opened) message=done
                        } catch(error: Exception) {
                            if(error is CancellationException) throw error
                            if(current==opened) project.error=error.message ?: failure
                        } finally { project.busy=false }
                    }
                }
            }
        }
    }
    return ProjectDistributionCommands(exportGame={
        deliver("Export saved game to a NEW folder (unsaved edits excluded)","Game Export",{ opened,runtime,output ->
            val descriptor=ProjectDistribution.export(opened,runtime,output) { staged ->
                val prepared=PreparedProject.open(ProjectFiles.open(staged))
                // Synchronous release before the exporter removes staged derivative caches.
                NativeEngine.releaseProject(prepared.handle)
            }
            "Exported saved game to ${descriptor.parent.parent}. Start engine_app.exe."
        },"Game export failed")
    },message=message,buildGame={
        deliver("Build saved game to a NEW folder (unsaved edits excluded)","Game Build",{ opened,runtime,output ->
            val built=GameBuild.build(opened,runtime,output) { staged ->
                // The engine mounts the staged packages exactly as the delivered game will.
                val prepared=Json.parseToJsonElement(NativeEngine.prepareGame(staged.toString().toByteArray(Charsets.UTF_8)).toString(Charsets.UTF_8)).jsonObject
                NativeEngine.releaseProject(prepared.getValue("handle").jsonPrimitive.long)
            }
            "Built game in ${built.folder}. Start ${built.executable.fileName}."
        },"Game build failed")
    })
}
