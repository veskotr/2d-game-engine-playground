package sle.editor.project

import sle.editor.ui.EditorDialogs
import sle.editor.ui.EditorFileChooser
import androidx.compose.runtime.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.text.input.TextFieldValue
import sle.editor.ui.EditorTheme
import sle.editor.ui.controls.*
import sle.editor.ui.theme.*
import androidx.compose.material.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.rememberDialogState
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.nio.file.Path
import javax.swing.JFileChooser
import javax.swing.JOptionPane
import javax.swing.filechooser.FileNameExtensionFilter
import sle.editor.ui.EditorMenuEntry

internal data class ProjectCommands(
    val create: ()->Unit,val open: ()->Unit,val close: ()->Unit,val settings: ()->Unit,
    val createAt: (Path,String)->Unit,val openPath: (Path)->Unit,
    val recent: ()->List<Path> = { emptyList() },
)

@Composable
internal fun projectControls(
    state: ProjectWorkspaceState,confirmSwitch: ()->Boolean,resetWorkspace: ()->Unit,
    scriptTrust: Boolean? = null, scriptTrustBusy: Boolean = false, setScriptTrust: (Boolean) -> Unit = {},
    workspaceRevision: ()->Any,uiScale: ()->Float,setUiScale: (Float)->Unit,startupScenes: List<Pair<String,String>> = emptyList(),recentDirectory: Path?=null,onBounds: (String,Rect)->Unit={_,_->},
): ProjectCommands {
    val scope=rememberCoroutineScope()
    val recentStore=remember { RecentProjectsStore(recentDirectory ?: sle.editor.workspace.WorkspaceLayoutKey.standalone().path.parent) }
    val recentWrites=remember { Mutex() }
    val initialRecents=remember { recentStore.load() }
    var recents by remember { mutableStateOf(initialRecents.paths) }
    LaunchedEffect(Unit) { initialRecents.notice?.let { state.error=it } }
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
                if(revision()!=initialRevision && !confirmProjectChange { confirm() }) return@launch
                state.activate(result.first);settingsOpen=false
                reset();applyScale(result.second.uiScale)
                result.third?.let { state.error="Project opened; previous local editor state could not be saved: $it" }
                scope.launch {
                    try { recentWrites.withLock { recents=withContext(Dispatchers.IO) { recentStore.record(result.first.file) } } }
                    catch(failure: Exception) {
                        if(failure is CancellationException) throw failure
                        if(state.opened?.file==result.first.file) state.error="Project opened; recent projects could not be saved: ${failure.message}"
                    }
                }
            } catch(failure: Exception) {
                if(failure is CancellationException) throw failure
                state.error=failure.message ?: "Could not open project"
            } finally { state.busy=false }
        }
    }
    val commands=remember(state) { ProjectCommands(
        create={
            val chooser=EditorFileChooser(state.opened?.root?.parent?.toFile() ?: sle.editor.ui.EditorFileLocations.folder(sle.editor.ui.EditorFileLocations.Kind.Project)).apply {
                dialogTitle="Choose parent folder for the new project";fileSelectionMode=JFileChooser.DIRECTORIES_ONLY
            }
            if(chooser.showOpenDialog(null)==JFileChooser.APPROVE_OPTION) {
                sle.editor.ui.EditorFileLocations.remember(sle.editor.ui.EditorFileLocations.Kind.Project,java.io.File(chooser.selectedFile,"_"))
                val name=EditorDialogs.showInputDialog(null,"Project name","New Project",JOptionPane.PLAIN_MESSAGE)?.trim()
                if(name!=null) try {
                    validateProjectFolderName(name)
                    val choices = ProjectArtStyle.entries.map { it.label }.toTypedArray()
                    val selected = EditorDialogs.showInputDialog(null, "Art style", "New Project",
                        JOptionPane.PLAIN_MESSAGE, null, choices, ProjectArtStyle.Smooth.label)
                    val style = ProjectArtStyle.entries.firstOrNull { it.label == selected }
                    if (style != null) load { ProjectFiles.create(chooser.selectedFile.toPath().resolve(name), name, style) }
                } catch(failure: Exception) { state.error=failure.message }
            }
        },
        open={
            val chooser=EditorFileChooser(state.opened?.root?.parent?.toFile() ?: sle.editor.ui.EditorFileLocations.folder(sle.editor.ui.EditorFileLocations.Kind.Project)).apply {
                dialogTitle="Open Project";fileFilter=FileNameExtensionFilter("SLE Project (*.sleproject)","sleproject")
            }
            if(chooser.showOpenDialog(null)==JFileChooser.APPROVE_OPTION) {
                sle.editor.ui.EditorFileLocations.remember(sle.editor.ui.EditorFileLocations.Kind.Project,chooser.selectedFile.parentFile)
                load { ProjectFiles.open(chooser.selectedFile.toPath()) }
            }
        },
        close={
            val current=state.opened
            if(current!=null && !state.busy && confirm()) {
                val initialRevision=revision()
                val currentScale=scale();state.busy=true;state.error=null
                scope.launch {
                    try {
                        val warning=withContext(Dispatchers.IO) { runCatching { ProjectFiles.saveEditorState(current,ProjectEditorState(currentScale)) }.exceptionOrNull()?.message }
                        if(revision()!=initialRevision && !confirmProjectChange { confirm() }) return@launch
                        state.close();settingsOpen=false;reset()
                        warning?.let { state.error="Project closed; local editor state could not be saved: $it" }
                    } finally { state.busy=false }
                }
            }
        },
        settings={ if(state.opened!=null && !state.busy) { state.error=null;settingsOpen=true } },
        createAt={folder,name -> load { ProjectFiles.create(folder,name) }},
        openPath={path -> load { ProjectFiles.open(path) }},
        recent={recents},
    ) }
    if(settingsOpen) state.opened?.let { current ->
        ProjectSettingsDialog(current,state.busy,state.error,startupScenes,onBounds,scriptTrust,scriptTrustBusy,setScriptTrust,onDismiss={if(!state.busy) settingsOpen=false},onSave={ document ->
            state.busy=true;state.error=null
            scope.launch {
                try {
                    val saved=withContext(Dispatchers.IO) { ProjectFiles.save(current,document) }
                    state.refreshSettings(saved);settingsOpen=false
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
    EditorMenuEntry("Open Recent",editable && !state.busy && commands.recent().isNotEmpty(),children=commands.recent().map { path ->
        EditorMenuEntry(path.toString(),action={commands.openPath(path)})
    }),
    EditorMenuEntry("Close Project",editable && !state.busy && state.opened!=null,action=commands.close),
    EditorMenuEntry("Project Settings…",editable && !state.busy && state.opened!=null,action=commands.settings),
)

@Composable
private fun ProjectSettingsDialog(
    project: OpenedProject,busy: Boolean,error: String?,startupScenes: List<Pair<String,String>>,onBounds: (String,Rect)->Unit,
    scriptTrust: Boolean?, scriptTrustBusy: Boolean, setScriptTrust: (Boolean) -> Unit,
    onDismiss: ()->Unit,onSave: (ProjectDocument)->Unit,
) {
    var startup by remember(project) { mutableStateOf(project.document.startupScene) }
    var collisionLayers by remember(project) { mutableStateOf(project.document.settings.collisionLayers) }
    var sortingLayers by remember(project) { mutableStateOf(project.document.settings.sortingLayers) }
    var inputActions by remember(project) { mutableStateOf(project.document.settings.inputActions) }
    var tags by remember(project) { mutableStateOf(project.document.settings.tags) }
    var artStyle by remember(project) { mutableStateOf(project.document.settings.artStyle) }
    var name by remember(project) { mutableStateOf(project.document.name) }
    var title by remember(project) { mutableStateOf(project.document.game.title) }
    var width by remember(project) { mutableStateOf(project.document.game.width.toString()) }
    var height by remember(project) { mutableStateOf(project.document.game.height.toString()) }
    var fps by remember(project) { mutableStateOf(project.document.game.targetFPS.toString()) }
    var vsync by remember(project) { mutableStateOf(project.document.game.vsync) }
    var mode by remember(project) { mutableStateOf(project.document.game.mode) }
    var formError by remember { mutableStateOf<String?>(null) }
    fun measured(id: String)=Modifier.onGloballyPositioned { onBounds(id,it.boundsInWindow()) }
    val appearance=LocalEditorAppearance.current
    DialogWindow(onCloseRequest=onDismiss,title="Project Settings",resizable=true,state=rememberDialogState(width=500.dp,height=560.dp)) {
        EditorTheme(appearance) {
            val metrics=LocalEditorMetrics.current
            Surface(Modifier.fillMaxSize()) {
                Column(Modifier.padding(metrics.panelPadding),verticalArrangement=Arrangement.spacedBy(metrics.gap)) {
                    Column(Modifier.weight(1f).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(metrics.gap)) {
                        EditorLabel("Startup", style = MaterialTheme.typography.subtitle2)
                        SettingsField("Project name",name,{name=it},!busy,measured("ProjectName"))
                        Box(measured("StartupScene")) {
                            var expanded by remember { mutableStateOf(false) }
                            EditorButton("Startup: "+(startupScenes.firstOrNull { it.first==startup }?.second ?: startup ?: "None"),{expanded=true},enabled=!busy)
                            DropdownMenu(expanded && !busy,onDismissRequest={expanded=false}) {
                                EditorTooltip("Start without opening a scene", Modifier.fillMaxWidth()) {
                                    DropdownMenuItem(onClick={startup=null;expanded=false}) { Text("None") }
                                }
                                startupScenes.forEach { (id,label) ->
                                    EditorTooltip("Open $label at startup", Modifier.fillMaxWidth()) {
                                        DropdownMenuItem(onClick={startup=id;expanded=false}) { Text(label) }
                                    }
                                }
                            }
                        }
                        EditorLabel("Assets: ${project.document.assetRoot}",style=MaterialTheme.typography.caption)
                        Divider()
                        EditorLabel("Game", style = MaterialTheme.typography.subtitle2)
                        SettingsField("Game window title",title,{title=it},!busy,measured("GameTitle"))
                        SettingsField("Width",width,{width=it},!busy,measured("GameWidth"),numeric=true)
                        SettingsField("Height",height,{height=it},!busy,numeric=true)
                        EditorSelect("Window mode",mode,listOf("Windowed","Fullscreen","BorderlessFullscreen"),{mode=it},enabled=!busy)
                        EditorCheckBox("VSync",vsync,{vsync=it},enabled=!busy)
                        SettingsField("Target FPS",fps,{fps=it},!busy,numeric=true,help="0 = unlimited")
                        Divider()
                        EditorLabel("Art Style", style = MaterialTheme.typography.subtitle2)
                        val legacy = "Legacy defaults"
                        EditorSelect("Preset", artStyle?.label ?: legacy, listOf(legacy) + ProjectArtStyle.entries.map { it.label },
                            { selected -> artStyle = ProjectArtStyle.entries.firstOrNull { it.label == selected } },
                            enabled = !busy)
                        Divider()
                        EditorLabel("Sorting Layers", style = MaterialTheme.typography.subtitle2)
                        SortingLayerSettings(sortingLayers, { sortingLayers = it }, enabled = !busy)
                        Divider()
                        EditorLabel("Tags", style = MaterialTheme.typography.subtitle2)
                        TagSettings(tags, { tags = it }, enabled = !busy)
                        Divider()
                        EditorLabel("Collision", style = MaterialTheme.typography.subtitle2)
                        CollisionLayerSettings(collisionLayers, { collisionLayers = it }, enabled = !busy)
                        Divider()
                        EditorLabel("Input Actions", style = MaterialTheme.typography.subtitle2)
                        InputActionSettings(inputActions, { inputActions = it }, enabled = !busy)
                        Divider()
                        EditorLabel("Project Editor Scripts", style = MaterialTheme.typography.subtitle2)
                        EditorButton(if (scriptTrust == true) "Revoke Trust" else "Trust Editor Scripts",
                            { setScriptTrust(scriptTrust != true) }, enabled = !busy && !scriptTrustBusy)
                        (formError ?: error)?.let { EditorLabel(it,color=LocalEditorColors.current.error) }
                    }
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(metrics.gap,androidx.compose.ui.Alignment.End)) {
                        EditorButton("Cancel",onDismiss,enabled=!busy)
                        EditorButton(if(busy) "Saving…" else "Save",{
                            try {
                                val candidate=project.document.copy(name=name.trim(),startupScene=startup,game=ProjectGameSettings(title,width.toInt(),height.toInt(),vsync,mode,fps.toInt()),
                                    settings=project.document.settings.copy(artStyle=artStyle,collisionLayers=collisionLayers, sortingLayers=sortingLayers, tags=tags, inputActions=inputActions))
                                candidate.validate();formError=null;onSave(candidate)
                            } catch(failure: Exception) { formError=failure.message ?: "Enter valid settings" }
                        },enabled=!busy,modifier=measured("SaveProjectSettings"),primary=true)
                    }
                }
            }
        }
    }
}

@Composable
internal fun SettingsField(label: String,value: String,change: (String)->Unit,enabled: Boolean,
    modifier: Modifier=Modifier,numeric: Boolean=false,help: String?=null) {
    var field by remember { mutableStateOf(TextFieldValue(value)) }
    if(field.text!=value) field=field.copy(text=value)
    EditorPropertyRow(label,help=help) {
        EditorTextField(field,{field=it;change(it.text)},label,modifier.weight(1f),enabled=enabled,numeric=numeric,help=help)
    }
}
