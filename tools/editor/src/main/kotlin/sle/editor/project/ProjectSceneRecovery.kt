package sle.editor.project

import sle.editor.project.assets.AssetScanner

internal data class RecoveredProjectScene(val document: OpenedProjectDocument?,val notice: String?=null)

/** Reads candidates completely before a caller replaces its current scene. Never rewrites scenes. */
internal object ProjectSceneRecovery {
    fun load(project: OpenedProject,canonical: (String)->String): RecoveredProjectScene {
        val local=ProjectFiles.editorState(project)
        val notices=mutableListOf<String>()
        local.notice?.let(notices::add)
        fun open(path: java.nio.file.Path): OpenedProjectDocument = ProjectDocuments(project).open(path).also {
            require(it.kind==ProjectDocumentKind.Scene) { "Recovery target is not a scene" }
        }.let { it.copy(source=canonical(it.source)) }
        local.lastScene?.let { relative ->
            try { return RecoveredProjectScene(open(ProjectFiles.scenePath(project,relative)),notices.takeIf { it.isNotEmpty() }?.joinToString("; ")) }
            catch(failure: Exception) { notices.add("Could not restore last scene ($relative): ${failure.message}") }
        }
        val startup=project.document.startupScene
        if(startup!=null) try {
            val snapshot=AssetScanner.scan(project.root,project.assets)
            val relative=snapshot.pathsById[startup] ?: error("Startup scene UUID is missing or ambiguous")
            val document=open(ProjectFiles.scenePath(project,project.document.assetRoot+"/"+relative))
            return RecoveredProjectScene(document,notices.takeIf { it.isNotEmpty() }?.joinToString("; "))
        } catch(failure: Exception) { notices.add("Could not open startup scene: ${failure.message}") }
        return RecoveredProjectScene(null,notices.takeIf { it.isNotEmpty() }?.joinToString("; "))
    }
}
