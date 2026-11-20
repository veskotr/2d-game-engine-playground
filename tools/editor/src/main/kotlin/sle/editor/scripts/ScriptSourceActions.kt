package sle.editor.scripts

import androidx.compose.runtime.staticCompositionLocalOf
import sle.editor.bridge.NativeEngine
import sle.editor.project.ProjectDocuments
import sle.editor.project.ProjectWorkspaceState
import sle.editor.project.assets.assetPath
import java.nio.file.Path

internal val LocalOpenScriptAttachment = staticCompositionLocalOf<((String) -> Unit)?> { null }

internal class ScriptSourceActions(
    private val workspace: ProjectWorkspaceState,
    private val editor: ScriptEditor,
) {
    private val activation = workspace.activationRevision
    private val assetRoot = workspace.opened?.document?.assetRoot

    private fun currentProject() = workspace.opened.also {
        require(workspace.activationRevision == activation && it?.document?.assetRoot == assetRoot) {
            "The project changed. Select the Lua source again."
        }
    }

    fun openAttachment(reference: String): Process {
        val project = currentProject()
        val path = when {
            reference.startsWith("project-asset://") -> {
                requireNotNull(project) { "Open the project to resolve Lua source: $reference" }
                Path.of(ProjectDocuments(project).resolvePropertyAsset(reference))
            }
            reference.startsWith("asset://") -> error("Unresolved package reference: $reference")
            else -> {
                val local = Path.of(reference)
                when {
                    local.isAbsolute -> local
                    project != null -> assetPath(project.root, project.assets, reference.replace('\\', '/'))
                    else -> Path.of(NativeEngine.resolveStandaloneAsset(reference.encodeToByteArray()).decodeToString())
                }
            }
        }
        return editor.open(path)
    }

    fun openAsset(relative: String, id: String?): Process {
        val project = requireNotNull(currentProject()) { "Open the project to select a Lua asset." }
        if (id != null) return openAttachment("project-asset://$id")
        return editor.open(assetPath(project.root, project.assets, relative))
    }
}
