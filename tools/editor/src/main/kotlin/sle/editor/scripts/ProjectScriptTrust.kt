package sle.editor.scripts

import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.security.MessageDigest
import sle.editor.project.OpenedProject
import sle.editor.workspace.WorkspaceLayoutKey

internal class ProjectScriptTrust(directory: Path = WorkspaceLayoutKey.standalone().path.parent) {
    private val directory = directory.resolve("project-script-trust")

    private fun file(project: OpenedProject): Path {
        val identity = "${project.document.id}\n${project.file}"
        val key = MessageDigest.getInstance("SHA-256").digest(identity.encodeToByteArray())
            .joinToString("") { "%02x".format(it) }
        return directory.resolve(key)
    }

    fun read(project: OpenedProject): Boolean? {
        val path = file(project)
        if (!Files.isRegularFile(path, NOFOLLOW_LINKS)) return null
        return when (Files.newInputStream(path, NOFOLLOW_LINKS).use { it.readNBytes(8).decodeToString() }) {
            "trusted" -> true
            "denied" -> false
            else -> null
        }
    }

    fun write(project: OpenedProject, trusted: Boolean) {
        val path = file(project)
        Files.createDirectories(directory)
        val temporary = Files.createTempFile(directory, ".trust-", ".tmp")
        try {
            Files.writeString(temporary, if (trusted) "trusted" else "denied")
            Files.move(temporary, path, ATOMIC_MOVE, REPLACE_EXISTING)
        } finally {
            Files.deleteIfExists(temporary)
        }
    }
}

internal fun projectScriptPaths(root: Path): List<Path> {
    val editor = root.resolve("Editor")
    if (!Files.isDirectory(editor, NOFOLLOW_LINKS)) return emptyList()
    return Files.newDirectoryStream(editor, "*.editor.lua").use { entries ->
        val paths = entries.asSequence().filter { Files.isRegularFile(it, NOFOLLOW_LINKS) }.take(33).toList()
        require(paths.size <= 32) { "A project can load up to 32 editor scripts" }
        paths.sorted()
    }
}
