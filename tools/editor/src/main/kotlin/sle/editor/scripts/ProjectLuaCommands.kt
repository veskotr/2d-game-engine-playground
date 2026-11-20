package sle.editor.scripts

import androidx.compose.runtime.*
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import javax.swing.JOptionPane
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import sle.editor.bridge.NativeEngine
import sle.editor.command.*
import sle.editor.project.OpenedProject
import sle.editor.ui.EditorDialogs
import sle.editor.viewport.ViewportMode

internal class ProjectLuaCommand(
    val id: String,
    val menu: String,
    val hasPredicate: Boolean,
    val filename: String,
    private val source: ByteArray,
) {
    fun evaluate(mode: String, snapshot: ByteArray = "{}".encodeToByteArray()): JsonObject =
        evaluateProjectLua(source, filename, mode, snapshot, id)
}

private fun evaluateProjectLua(source: ByteArray, filename: String, mode: String,
    snapshot: ByteArray = "{}".encodeToByteArray(), id: String = ""): JsonObject {
    val response = NativeEngine.runProjectEditorLua(source, snapshot, filename.encodeToByteArray(),
        mode.encodeToByteArray(), id.encodeToByteArray())
    return Json.parseToJsonElement(response.decodeToString()).jsonObject
}

internal fun JsonObject.requireLuaSuccess() {
    val error = getValue("error").jsonPrimitive.content
    require(error.isEmpty()) { error }
}

internal data class RegisteredProjectLuaCommand(val script: ProjectLuaCommand, val command: EditorCommand)

internal class ProjectLuaScripts(val generation: Long) {
    var checkingTrust by mutableStateOf(true)
    var trusted by mutableStateOf<Boolean?>(null)
    var declarations by mutableStateOf(ProjectLuaDeclarations())
    val commands get() = declarations.commands
    var setTrust: (Boolean) -> Unit = {}
}

internal data class ProjectLuaDeclarations(
    val commands: List<RegisteredProjectLuaCommand> = emptyList(),
    val drawers: Map<ProjectDrawerTarget, sle.editor.ui.inspector.PropertyDrawer> = emptyMap(),
    val panels: List<ProjectLuaPanel> = emptyList(),
)

internal fun loadProjectDeclarations(
    project: OpenedProject,
    diagnostics: MutableList<String>,
): ProjectLuaDeclarations {
    val registered = mutableListOf<RegisteredProjectLuaCommand>()
    val uiRows = mutableListOf<Pair<String, JsonObject>>()
    val occupied = standardCommands.commands.associateWithTo(linkedMapOf()) { it.defaultChords }
    for (path in projectScriptPaths(project.root)) {
        val filename = "Editor/${path.fileName}"
        try {
            require(path.toRealPath() == path) { "Editor script points through a link" }
            val source = Files.newInputStream(path, NOFOLLOW_LINKS).use { it.readNBytes(256 * 1024 + 1) }
            require(source.size <= 256 * 1024) { "Editor script is larger than 256 KiB" }
            val response = evaluateProjectLua(source, filename, "discover")
            response.requireLuaSuccess()
            for (descriptor in response.getValue("edits").jsonArray) {
                try {
                    val row = descriptor.jsonObject
                    if ("kind" in row) {
                        uiRows += filename to row
                        continue
                    }
                    val id = row.getValue("id").jsonPrimitive.content
                    val label = row.getValue("label").jsonPrimitive.content
                    val menu = row["menu"]?.jsonPrimitive?.content ?: "Tools"
                    require(id.matches(Regex("[A-Za-z0-9_.-]+"))) { "Invalid command ID '$id'" }
                    require(label.isNotBlank() && menu.isNotBlank() && (label + menu).none(Char::isISOControl)) {
                        "Command '$id' needs a printable label and menu"
                    }
                    val chord = row["shortcut"]?.jsonPrimitive?.content?.let(KeyChord::parse)
                    val script = ProjectLuaCommand(id, menu, row.getValue("predicate").jsonPrimitive.boolean, filename, source)
                    val command = EditorCommand(CommandId("project.lua.$id"), label, defaultChords = listOfNotNull(chord),
                        enabled = { ctx ->
                            ctx.mode == ViewportMode.Scene && ctx.scene != null && ctx.editable &&
                                !ctx.projectBusy && !ctx.modalOpen && ctx.actions.projectLuaEnabled(script)
                        },
                        run = { ctx -> ctx.actions.runProjectLua(script) })
                    require(registered.size < 64) { "A project can register up to 64 Lua commands" }
                    require(occupied.keys.none { it.id == command.id }) { "Duplicate editor command ID '$id'" }
                    Keymap.requireNoConflicts(occupied + (command to command.defaultChords))
                    occupied[command] = command.defaultChords
                    registered += RegisteredProjectLuaCommand(script, command)
                } catch (failure: Exception) {
                    diagnostics += "$filename — ${failure.message}"
                }
            }
        } catch (failure: Exception) {
            diagnostics += "$filename — ${failure.message}"
        }
    }
    val drawers = linkedMapOf<ProjectDrawerTarget, sle.editor.ui.inspector.PropertyDrawer>()
    val panels = linkedMapOf<String, ProjectLuaPanel>()
    val commands = registered.associateBy { it.script.id }
    for ((filename, row) in uiRows) {
        try {
            val descriptor = row.getValue("descriptor").jsonObject
            when (row.getValue("kind").jsonPrimitive.content) {
                "drawer" -> {
                    val (target, drawer) = readProjectDrawer(descriptor)
                    require(drawers.size < 64) { "A project can register up to 64 Lua drawers" }
                    require(target !in drawers) { "Duplicate drawer for ${target.component}.${target.path.joinToString(".")}" }
                    drawers[target] = drawer
                }
                "panel" -> {
                    val panel = readProjectPanel(descriptor, commands)
                    require(panels.size < 16) { "A project can register up to 16 Lua panels" }
                    require(panel.id !in panels) { "Duplicate editor panel ID '${panel.id}'" }
                    panels[panel.id] = panel
                }
            }
        } catch (failure: Exception) {
            diagnostics += "$filename — ${failure.message}"
        }
    }
    return ProjectLuaDeclarations(registered, drawers, panels.values.toList())
}

@Composable
internal fun rememberProjectLuaScripts(
    project: OpenedProject?,
    generation: Long,
    localDirectory: Path?,
    report: (String) -> Unit,
): ProjectLuaScripts {
    val state = remember(generation) { ProjectLuaScripts(generation) }
    val store = remember(localDirectory) { localDirectory?.let(::ProjectScriptTrust) ?: ProjectScriptTrust() }
    val scope = rememberCoroutineScope()
    val preferenceIo = remember(store) { Mutex() }
    val sessionRevocations = remember(store) { mutableSetOf<Pair<Path, String>>() }
    val reportLatest by rememberUpdatedState(report)
    val currentState by rememberUpdatedState(state)
    fun prompt(target: OpenedProject): Boolean {
        val allow = "Trust Editor Scripts"
        val deny = "Keep Disabled"
        return EditorDialogs.showOptionDialog(null,
            "Allow Lua editor scripts from ${target.document.name} (${target.file})? " +
                "These scripts can add editor controls, propose scene edits, and create project assets.",
            "Trust Project Editor Scripts", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE,
            null, arrayOf(allow, deny), deny) == 0
    }

    fun identity(target: OpenedProject) = target.file to target.document.id

    suspend fun persist(target: OpenedProject, allowed: Boolean) {
        preferenceIo.withLock {
            if (!allowed) sessionRevocations += identity(target)
            withContext(Dispatchers.IO) { store.write(target, allowed) }
            sessionRevocations -= identity(target)
        }
    }

    LaunchedEffect(state) {
        if (project == null) return@LaunchedEffect
        try {
            val decision = preferenceIo.withLock {
                if (identity(project) in sessionRevocations) false
                else withContext(Dispatchers.IO) { store.read(project) }
            }
            val hasScripts = decision == null && withContext(Dispatchers.IO) { projectScriptPaths(project.root).isNotEmpty() }
            if (hasScripts) {
                val allowed = prompt(project)
                persist(project, allowed)
                state.trusted = allowed
            } else state.trusted = decision
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            reportLatest("Could not check project editor scripts: ${failure.message}")
        } finally {
            state.checkingTrust = false
        }
    }
    SideEffect {
        state.setTrust = change@{ allow ->
            if (project == null || currentState !== state || state.checkingTrust) return@change
            if (allow && !prompt(project)) return@change
            if (currentState !== state) return@change
            state.checkingTrust = true
            state.trusted = false
            state.declarations = ProjectLuaDeclarations()
            // Take the preference lock before a subsequent project activation can read it.
            scope.launch(start = CoroutineStart.UNDISPATCHED) {
                try {
                    persist(project, allow)
                    state.trusted = allow
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Exception) {
                    reportLatest("Could not save project editor script trust: ${failure.message}")
                } finally {
                    state.checkingTrust = false
                }
            }
        }
    }
    LaunchedEffect(state, state.trusted) {
        if (project == null || state.trusted != true) return@LaunchedEffect
        val diagnostics = mutableListOf<String>()
        try {
            val declarations = withContext(Dispatchers.IO) { loadProjectDeclarations(project, diagnostics) }
            if (state.trusted == true) state.declarations = declarations
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            diagnostics += "Could not load project editor scripts: ${failure.message}"
        }
        diagnostics.forEach(reportLatest)
    }
    return state
}

