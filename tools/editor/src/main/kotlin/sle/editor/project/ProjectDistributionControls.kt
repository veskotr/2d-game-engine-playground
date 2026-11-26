package sle.editor.project

import sle.editor.ui.EditorDialogs
import sle.editor.ui.EditorFileChooser
import sle.editor.ui.EditorNotices
import sle.editor.ui.NoticeLevel
import androidx.compose.runtime.*
import kotlinx.coroutines.*
import sle.editor.bridge.PackagedEngine
import java.nio.file.Path
import javax.swing.JFileChooser
import javax.swing.JOptionPane

internal data class ProjectDistributionCommands(
    val exportGame: () -> Unit,
    val exportUpdate: () -> Unit,
    val applyUpdate: () -> Unit,
    val progress: String?,
)

@Composable
internal fun projectDistributionControls(
    project: ProjectWorkspaceState,
    notices: EditorNotices,
    dirty: Boolean,
    saveDirty: (suspend () -> Unit)?,
): ProjectDistributionCommands {
    val scope=rememberCoroutineScope()
    val current by rememberUpdatedState(project.opened)
    var progress by remember { mutableStateOf<String?>(null) }
    // Packaged editors use their bundled runtime; development runs choose a prepared one.
    fun runtimeBundle(target: GameTarget): Path? {
        PackagedEngine.runtimeBundle(target)?.let { return it.toPath() }
        val default = if (target == GameTarget.Windows) "build/release/runtime/Release"
            else "build/linux-x64-release/runtime/Release"
        val runtimeChooser=EditorFileChooser(Path.of(default).toAbsolutePath().toFile()).apply {
            dialogTitle="Select prepared ${target.label} Release runtime bundle"
            fileSelectionMode=JFileChooser.DIRECTORIES_ONLY
        }
        return if(runtimeChooser.showOpenDialog(null)==JFileChooser.APPROVE_OPTION) runtimeChooser.selectedFile.toPath() else null
    }
    fun selectFolder(title: String): Path? {
        val chooser = EditorFileChooser(project.opened?.root?.toFile()).apply {
            dialogTitle = title
            fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
        }
        return if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) chooser.selectedFile.toPath() else null
    }

    fun runDistribution(
        opened: OpenedProject?,
        activity: String,
        saveFirst: Boolean = false,
        operation: () -> String?,
    ) {
        if (current != opened || project.busy) return
        project.error = null
        project.busy = true
        progress = activity
        notices.info(if (saveFirst) "Saving documents…" else activity)
        scope.launch {
            try {
                if (saveFirst) checkNotNull(saveDirty).invoke()
                val message = withContext(Dispatchers.IO) { operation() }
                if (current == opened) {
                    if (message == null) notices.info("No content changes since the selected release.")
                    else notices.post(NoticeLevel.Success, message, ttlMillis = EXPORT_RESULT_MILLIS)
                }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                if (current == opened) project.error = error.message ?: "Game distribution failed"
            } finally {
                project.busy = false
                progress = null
            }
        }
    }

    fun exportSavedGame(update: Boolean) {
        val opened = project.opened ?: return
        if (project.busy) return
        val base = if (update) selectFolder("Select the base game delivery") ?: return else null
        var saveFirst = false
        if (dirty) {
            val saved = "Export Saved Versions"
            val save = "Save and Export"
            val options = if (saveDirty == null) arrayOf(saved, "Cancel") else arrayOf(save, saved, "Cancel")
            val selected = EditorDialogs.showOptionDialog(null,
                "Some documents have unsaved changes. Export Saved Versions uses the files on disk.",
                if (update) "Export Content Update" else "Export Game", JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE, null, options, saved)
            when (options.getOrNull(selected)) {
                save -> saveFirst = true
                saved -> Unit
                else -> return
            }
        }
        val targets = if (update) listOf(GameTarget.Windows) else {
            val options = arrayOf(GameTarget.Windows.label, GameTarget.Linux.label, "Both", "Cancel")
            val default = if (System.getProperty("os.name").startsWith("Windows")) options[0] else options[1]
            when (EditorDialogs.showOptionDialog(null, "Choose the game export targets.",
                "Export Game", JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE, null, options, default)) {
                0 -> listOf(GameTarget.Windows)
                1 -> listOf(GameTarget.Linux)
                2 -> GameTarget.entries
                else -> return
            }
        }
        val runtimes = targets.associateWith { runtimeBundle(it) ?: return }
        runDistribution(opened, if (update) "Exporting content update…" else "Exporting game…", saveFirst) {
            if (base == null) {
                val export = GameBuild.exportTargets(opened, runtimes)
                val game = export.games.values.first()
                val sizes = game.packages.joinToString(", ") { "${it.name} (${it.path}, ${it.bytes} bytes)" }
                val compatibility = if (game.colocatedAssets.isEmpty()) "" else
                    " File-relative references require these assets to share a package: ${game.colocatedAssets.joinToString(", ")}."
                val deliveries = export.games.entries.joinToString(", ") { (target, delivery) ->
                    "${target.label} (${delivery.executable.fileName}, ${export.archives.getValue(target).fileName})"
                }
                val updates = if (GameTarget.Linux in export.games) " Linux x64 supports full exports only." else ""
                "Exported to ${export.folder}. $deliveries. ${game.packages.size} packages per target. $sizes.$compatibility$updates"
            } else {
                val runtime = runtimes.getValue(GameTarget.Windows)
                val exports = ProjectDistribution.nextExportDestination(opened, runtime).parent
                val destination = GameContentUpdate.nextSibling(exports.resolve(opened.root.fileName), "Update")
                GameContentUpdate.export(opened, runtime, base, destination)?.let { result ->
                    val delta = result.delta
                    "Exported to ${result.folder}. Added ${delta.added.size}, replaced ${delta.replaced.size}, removed ${delta.removed.size} packages."
                }
            }
        }
    }

    fun applyUpdate() {
        val opened = project.opened
        if (project.busy) return
        val base = selectFolder("Select the base game delivery") ?: return
        val update = selectFolder("Select the content update folder") ?: return
        runDistribution(opened, "Applying content update…") {
            val target = GameContentUpdate.apply(base, update, GameContentUpdate.nextSibling(base, "Updated"))
            "Applied content update to $target."
        }
    }

    return ProjectDistributionCommands({ exportSavedGame(false) }, { exportSavedGame(true) }, ::applyUpdate, progress)
}

/** The export result names a folder to open, so it stays longer than an ordinary success. */
private const val EXPORT_RESULT_MILLIS=15_000L
