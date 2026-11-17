package sle.editor.ui

import java.io.File
import java.nio.file.Files
import java.nio.file.Path

/** Start folders for file dialogs. Order: the document's own folder (when it is a real, non-temporary
 * location), the open project's matching Assets folder, the folder last used for this kind, the
 * engine's bundled assets folder, then the user's Documents. Previously dialogs fell back to paths
 * relative to the process working directory, which usually do not exist, so Swing opened in an
 * arbitrary default location. */
internal object EditorFileLocations {
    enum class Kind(val projectFolder: String?, val bundledFolder: String?) {
        Scene("Scenes", "scenes"), Prefab("Prefabs", "prefabs"), Texture("Textures", "textures"),
        Asset(null, null), Project(null, null), Build(null, null),
    }
    /** Set while a project is open; its Assets folder. */
    @Volatile var projectAssets: Path? = null
    private val preferences = runCatching { java.util.prefs.Preferences.userRoot().node("sle/editor/file-locations") }.getOrNull()
    private val temporary = runCatching { Path.of(System.getProperty("java.io.tmpdir")).toAbsolutePath().normalize() }.getOrNull()

    private fun usable(path: Path?): Path? = path?.toAbsolutePath()?.normalize()?.takeIf {
        Files.isDirectory(it) && (temporary == null || !it.startsWith(temporary))
    }

    fun folder(kind: Kind, current: Path? = null): File {
        val project = projectAssets
        val candidates = sequence {
            yield(current?.let { if (Files.isDirectory(it)) it else it.parent })
            if (project != null) { kind.projectFolder?.let { yield(project.resolve(it)) }; yield(project) }
            yield(preferences?.get(kind.name, null)?.let { runCatching { Path.of(it) }.getOrNull() })
            kind.bundledFolder?.let { name -> yield(bundledAssets()?.resolve(name)) }
            val home = Path.of(System.getProperty("user.home"))
            yield(home.resolve("Documents")); yield(home)
        }
        return (candidates.firstNotNullOfOrNull(::usable) ?: Path.of(System.getProperty("user.home"))).toFile()
    }

    /** Remember the folder of a file the user chose, for the next dialog of this kind. */
    fun remember(kind: Kind, chosen: File?) {
        val folder = chosen?.let { if (it.isDirectory) it else it.parentFile } ?: return
        if (usable(folder.toPath()) != null) runCatching { preferences?.put(kind.name, folder.absolutePath) }
    }

    /** The engine checkout's assets/ folder, found from the working directory upward. */
    private fun bundledAssets(): Path? {
        var cursor: Path? = Path.of("").toAbsolutePath()
        repeat(5) {
            val candidate = cursor?.resolve("assets")
            if (candidate != null && Files.isDirectory(candidate.resolve("scenes"))) return candidate
            cursor = cursor?.parent
        }
        return null
    }
}
