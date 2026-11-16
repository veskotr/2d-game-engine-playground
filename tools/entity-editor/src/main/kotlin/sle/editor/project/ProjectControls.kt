package sle.editor.project

import androidx.compose.runtime.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.rememberDialogState
import kotlinx.coroutines.*
import java.nio.file.Path
import javax.swing.JFileChooser
import javax.swing.JOptionPane
import javax.swing.filechooser.FileNameExtensionFilter
import sle.editor.ui.EditorMenuEntry

internal data class ProjectCommands(
    val create: ()->Unit,val open: ()->Unit,val close: ()->Unit,val settings: ()->Unit,
    val createAt: (Path,String)->Unit,val openPath: (Path)->Unit,
)

@Composable
internal fun projectControls(
    state: ProjectWorkspaceState,confirmSwitch: ()->Boolean,resetWorkspace: ()->Unit,
    workspaceRevision: ()->Any,uiScale: ()->Float,setUiScale: (Float)->Unit,startupScenes: List<Pair<String,String>> = emptyList(),onBounds: (String,Rect)->Unit={_,_->},
): ProjectCommands {
    val scope=rememberCoroutineScope()
    val confirm by rememberUpdatedState(confirmSwitch)
    val revision by rememberUpdatedState(workspaceRevision)
    val reset by rememberUpdatedState(resetWorkspace)
    val scale by rememberUpdatedState(uiScale)
    val applyScale by rememberUpdatedState(setUiScale)
    var settingsOpen by remember { mutableStateOf(false) }
    fun load(operation: ()->OpenedProject) {
        if(state.busy || !confirm()) return
        val initialRevision=revision()
        val previous=state.opened;val previousScale=scale()
        state.busy=true;state.error=null
        scope.launch {
            try {
                val result=withContext(Dispatchers.IO) {
                    val next=operation()
                    val warning=previous?.let { runCatching { ProjectFiles.saveEditorState(it,ProjectEditorState(previousScale)) }.exceptionOrNull()?.message }
                    Triple(next,ProjectFiles.editorState(next),warning)
                }
                if(revision()!=initialRevision && !confirm()) return@launch
                state.activate(result.first);settingsOpen=false
                reset();applyScale(result.second.uiScale)
                result.third?.let { state.error="Project opened; previous local editor state could not be saved: $it" }
            } catch(failure: Exception) {
                if(failure is CancellationException) throw failure
                state.error=failure.message ?: "Could not open project"
            } finally { state.busy=false }
        }
    }
    val commands=remember(state) { ProjectCommands(
        create={
            val chooser=JFileChooser(state.opened?.root?.parent?.toFile()).apply {
                dialogTitle="Choose parent folder for the new project";fileSelectionMode=JFileChooser.DIRECTORIES_ONLY
            }
            if(chooser.showOpenDialog(null)==JFileChooser.APPROVE_OPTION) {
                val name=JOptionPane.showInputDialog(null,"Project name","New Project",JOptionPane.PLAIN_MESSAGE)?.trim()
                if(name!=null) try {
                    validateProjectFolderName(name)
                    load { ProjectFiles.create(chooser.selectedFile.toPath().resolve(name),name) }
                } catch(failure: Exception) { state.error=failure.message }
            }
        },
        open={
            val chooser=JFileChooser(state.opened?.root?.toFile()).apply {
                dialogTitle="Open Project";fileFilter=FileNameExtensionFilter("SLE Project (*.sleproject)","sleproject")
            }
            if(chooser.showOpenDialog(null)==JFileChooser.APPROVE_OPTION) load { ProjectFiles.open(chooser.selectedFile.toPath()) }
        },
        close={
            val current=state.opened
            if(current!=null && !state.busy && confirm()) {
                val initialRevision=revision()
                val currentScale=scale();state.busy=true;state.error=null
                scope.launch {
                    try {
                        val warning=withContext(Dispatchers.IO) { runCatching { ProjectFiles.saveEditorState(current,ProjectEditorState(currentScale)) }.exceptionOrNull()?.message }
                        if(revision()!=initialRevision && !confirm()) return@launch
                        state.close();settingsOpen=false;reset()
                        warning?.let { state.error="Project closed; local editor state could not be saved: $it" }
                    } finally { state.busy=false }
                }
            }
        },
        settings={ if(state.opened!=null && !state.busy) { state.error=null;settingsOpen=true } },
        createAt={folder,name -> load { ProjectFiles.create(folder,name) }},
        openPath={path -> load { ProjectFiles.open(path) }},
    ) }
    if(settingsOpen) state.opened?.let { current ->
        ProjectSettingsDialog(current,state.busy,state.error,startupScenes,onBounds,onDismiss={if(!state.busy) settingsOpen=false},onSave={ document ->
            state.busy=true;state.error=null
            scope.launch {
                try {
                    val saved=withContext(Dispatchers.IO) { ProjectFiles.save(current,document) }
                    state.activate(saved);settingsOpen=false
                } catch(failure: Exception) {
                    if(failure is CancellationException) throw failure
                    state.error=failure.message ?: "Could not save project settings"
                } finally { state.busy=false }
            }
        })
    }
    return commands
}

internal fun projectFileMenu(state: ProjectWorkspaceState,commands: ProjectCommands,editable: Boolean): List<EditorMenuEntry> = listOf(
    EditorMenuEntry("New Project…",editable && !state.busy,action=commands.create),
    EditorMenuEntry("Open Project…",editable && !state.busy,action=commands.open),
    EditorMenuEntry("Close Project",editable && !state.busy && state.opened!=null,action=commands.close),
    EditorMenuEntry("Project Settings…",editable && !state.busy && state.opened!=null,action=commands.settings),
)

@Composable
private fun ProjectSettingsDialog(
    project: OpenedProject,busy: Boolean,error: String?,startupScenes: List<Pair<String,String>>,onBounds: (String,Rect)->Unit,
    onDismiss: ()->Unit,onSave: (ProjectDocument)->Unit,
) {
    var startup by remember(project) { mutableStateOf(project.document.startupScene) }
    var scenesOpen by remember { mutableStateOf(false) }
    var name by remember(project) { mutableStateOf(project.document.name) }
    var title by remember(project) { mutableStateOf(project.document.game.title) }
    var width by remember(project) { mutableStateOf(project.document.game.width.toString()) }
    var height by remember(project) { mutableStateOf(project.document.game.height.toString()) }
    var fps by remember(project) { mutableStateOf(project.document.game.targetFPS.toString()) }
    var vsync by remember(project) { mutableStateOf(project.document.game.vsync) }
    var mode by remember(project) { mutableStateOf(project.document.game.mode) }
    var modesOpen by remember { mutableStateOf(false) }
    var formError by remember { mutableStateOf<String?>(null) }
    fun measured(id: String)=Modifier.onGloballyPositioned { onBounds(id,it.boundsInWindow()) }
    DialogWindow(onCloseRequest=onDismiss,title="Project Settings",resizable=false,state=rememberDialogState(width=500.dp,height=550.dp)) {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name,{name=it},enabled=!busy,label={Text("Project name")},singleLine=true,modifier=Modifier.fillMaxWidth().then(measured("ProjectName")))
                OutlinedTextField(title,{title=it},enabled=!busy,label={Text("Game window title")},singleLine=true,modifier=Modifier.fillMaxWidth().then(measured("GameTitle")))
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(width,{width=it},enabled=!busy,label={Text("Width")},singleLine=true,modifier=Modifier.weight(1f).then(measured("GameWidth")))
                    OutlinedTextField(height,{height=it},enabled=!busy,label={Text("Height")},singleLine=true,modifier=Modifier.weight(1f))
                }
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    Box {
                        OutlinedButton(onClick={modesOpen=true},enabled=!busy) { Text(mode) }
                        DropdownMenu(modesOpen,onDismissRequest={modesOpen=false}) {
                            listOf("Windowed","Fullscreen","BorderlessFullscreen").forEach { candidate ->
                                DropdownMenuItem(onClick={mode=candidate;modesOpen=false}) { Text(candidate) }
                            }
                        }
                    }
                    Checkbox(vsync,onCheckedChange={vsync=it},enabled=!busy)
                    Text("VSync",Modifier.padding(top=12.dp))
                }
                OutlinedTextField(fps,{fps=it},enabled=!busy,label={Text("Target FPS (0 = unlimited)")},singleLine=true,modifier=Modifier.fillMaxWidth())
                Box {
                    OutlinedButton(onClick={scenesOpen=true},enabled=!busy,modifier=measured("StartupScene")) {
                        Text("Startup: "+(startupScenes.firstOrNull { it.first==startup }?.second ?: startup ?: "None"))
                    }
                    DropdownMenu(scenesOpen,onDismissRequest={scenesOpen=false}) {
                        DropdownMenuItem(onClick={startup=null;scenesOpen=false}) { Text("None") }
                        startupScenes.forEach { (id,label) -> DropdownMenuItem(onClick={startup=id;scenesOpen=false}) { Text(label) } }
                    }
                }
                Text("Assets: ${project.document.assetRoot}",style=MaterialTheme.typography.caption)
                (formError ?: error)?.let { Text(it,color=MaterialTheme.colors.error) }
                Spacer(Modifier.weight(1f))
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.End) {
                    OutlinedButton(onClick=onDismiss,enabled=!busy) { Text("Cancel") }
                    Spacer(Modifier.width(8.dp))
                    Button(onClick={
                        try {
                            val candidate=project.document.copy(name=name.trim(),startupScene=startup,game=ProjectGameSettings(title,width.toInt(),height.toInt(),vsync,mode,fps.toInt()))
                            candidate.validate();formError=null;onSave(candidate)
                        } catch(failure: Exception) { formError=failure.message ?: "Enter valid settings" }
                    },enabled=!busy,modifier=measured("SaveProjectSettings")) { Text(if(busy) "Saving…" else "Save") }
                }
            }
        }
    }
}
