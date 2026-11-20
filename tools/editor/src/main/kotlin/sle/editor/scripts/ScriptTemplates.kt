package sle.editor.scripts

import java.io.IOException
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.nio.file.StandardOpenOption.READ

private const val MAX_TEMPLATE_BYTES = 1024 * 1024

internal interface ScriptTemplate {
    val filename: String
    val label: String
    fun read(): ByteArray
}

internal fun scriptTemplates(projectRoot: Path): List<ScriptTemplate> {
    val templates = BuiltInScriptTemplate.entries.associateByTo(linkedMapOf<String, ScriptTemplate>()) { it.filename }
    val directory = projectRoot.resolve("ScriptTemplates")
    val projectFiles = runCatching {
        if (!Files.isDirectory(directory, NOFOLLOW_LINKS)) return templates.values.toList()
        Files.newDirectoryStream(directory).use { entries ->
            entries.filter { it.fileName.toString().endsWith(".lua", ignoreCase = true) }
                .sortedBy { it.fileName.toString() }
        }
    }.getOrDefault(emptyList())
    for (path in projectFiles) {
        val filename = path.fileName.toString()
        val file = runCatching { resolveTemplate(directory, filename) }.getOrNull() ?: continue
        if (!runCatching { Files.size(file) <= MAX_TEMPLATE_BYTES }.getOrDefault(false)) continue
        templates[filename] = ProjectScriptTemplate(directory, filename, templates[filename]?.label ?: filename)
    }
    return templates.values.toList()
}

private fun resolveTemplate(directory: Path, filename: String): Path {
    if (!Files.isDirectory(directory, NOFOLLOW_LINKS)) throw IOException("ScriptTemplates is unavailable.")
    val root = directory.toRealPath()
    if (root != directory.parent.toRealPath().resolve(directory.fileName)) {
        throw IOException("ScriptTemplates must be inside the project root.")
    }
    val file = root.resolve(filename).toRealPath()
    if (!file.startsWith(root) || !Files.isRegularFile(file, NOFOLLOW_LINKS)) {
        throw IOException("Template $filename must resolve to a file inside ScriptTemplates.")
    }
    return file
}

private class ProjectScriptTemplate(
    private val directory: Path,
    override val filename: String,
    override val label: String,
) : ScriptTemplate {
    override fun read(): ByteArray {
        val file = resolveTemplate(directory, filename)
        val bytes = Files.newInputStream(file, READ, NOFOLLOW_LINKS).use { it.readNBytes(MAX_TEMPLATE_BYTES + 1) }
        require(bytes.size <= MAX_TEMPLATE_BYTES) { "Template $filename exceeds 1 MiB." }
        return bytes
    }
}
