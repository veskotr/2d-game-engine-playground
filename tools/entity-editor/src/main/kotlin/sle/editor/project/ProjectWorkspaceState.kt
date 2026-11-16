package sle.editor.project

import androidx.compose.runtime.*
import java.nio.file.Path

internal class ProjectWorkspaceState(private val onRootChanged: (Path?)->Unit = {}) {
    var opened by mutableStateOf<OpenedProject?>(null)
        private set
    var busy by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    fun activate(project: OpenedProject) { val previous=opened?.root;opened=project;error=null;if(previous!=project.root) onRootChanged(project.root) }
    fun open(path: Path) { activate(ProjectFiles.open(path)) }
    fun saveSettings(document: ProjectDocument) { activate(ProjectFiles.save(opened ?: error("No project open"),document)) }
    fun close() { opened=null;error=null;onRootChanged(null) }
}
