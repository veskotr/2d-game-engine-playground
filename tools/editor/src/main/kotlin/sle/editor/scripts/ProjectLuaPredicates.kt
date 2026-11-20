package sle.editor.scripts

import androidx.compose.runtime.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import sle.editor.console.editorConsoleSnapshot
import sle.editor.model.SceneWorkspaceState
import sle.editor.project.OpenedProject
import sle.editor.project.assets.AssetSnapshot

@Composable
internal fun rememberEnabledProjectLuaCommands(
    commands: List<RegisteredProjectLuaCommand>,
    scene: SceneWorkspaceState?,
    active: Boolean,
    project: OpenedProject?,
    generation: Long,
    assets: AssetSnapshot?,
    report: (String) -> Unit,
): State<Set<ProjectLuaCommand>> {
    val revision = scene?.history?.revision
    val selection = scene?.selection?.keys?.toList().orEmpty()
    val enabled = remember(commands, scene, active, project, generation, assets, revision, selection) {
        mutableStateOf(emptySet<ProjectLuaCommand>())
    }
    val reportLatest by rememberUpdatedState(report)
    LaunchedEffect(enabled) {
        val predicates = commands.map { it.script }.filter { it.hasPredicate }
        if (!active || predicates.isEmpty() || scene == null || revision == null) return@LaunchedEffect
        val captured = scene.document()
        val (accepted, errors) = withContext(Dispatchers.Default) {
            val snapshot = editorConsoleSnapshot(captured, selection, revision,
                generation, project, assets).toString().encodeToByteArray()
            val diagnostics = mutableListOf<String>()
            val matches = predicates.filter { script ->
                ensureActive()
                try {
                    val response = script.evaluate("enabled", snapshot)
                    response.requireLuaSuccess()
                    response.getValue("edits").jsonArray.single().jsonObject.getValue("enabled").jsonPrimitive.boolean
                } catch (failure: Exception) {
                    diagnostics += "${script.filename} (${script.id}) — ${failure.message}"
                    false
                }
            }.toSet()
            matches to diagnostics
        }
        enabled.value = accepted
        errors.forEach(reportLatest)
    }
    return enabled
}
