package sle.editor.project

import androidx.compose.runtime.*
import java.nio.file.Path

internal class ProjectWorkspaceState(
    private val onArtStyleChanged: () -> Unit = {},
    private val onRootChanged: (Path?) -> Unit = {},
) {
    var opened by mutableStateOf<OpenedProject?>(null)
        private set
    var activationRevision by mutableStateOf(0L)
        private set
    var busy by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    fun activate(project: OpenedProject) { val previous=opened;opened=project;activationRevision++;error=null;if(previous?.root!=project.root) onRootChanged(project.root) else if(previous.document.settings.artStyle!=project.document.settings.artStyle) onArtStyleChanged() }
    fun open(path: Path) { activate(ProjectFiles.open(path)) }
    fun refreshSettings(project: OpenedProject) {
        check(opened?.file == project.file)
        val artStyleChanged = opened?.document?.settings?.artStyle != project.document.settings.artStyle
        opened = project
        error = null
        if (artStyleChanged) onArtStyleChanged()
    }
    fun saveSettings(document: ProjectDocument) { refreshSettings(ProjectFiles.save(opened ?: error("No project open"),document)) }
    fun close() { activationRevision++;opened=null;error=null;onRootChanged(null) }
}
