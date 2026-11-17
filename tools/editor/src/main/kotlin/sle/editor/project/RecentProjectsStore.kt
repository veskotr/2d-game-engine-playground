package sle.editor.project

import kotlinx.serialization.json.*
import java.nio.file.*
import sle.editor.workspace.WorkspaceLayoutKey

internal data class RecentProjects(val paths: List<Path>,val notice: String?=null)

/** User-local history. Missing projects remain visible and fail safely through ordinary Open. */
internal class RecentProjectsStore(
    directory: Path=WorkspaceLayoutKey.standalone().path.parent,
    private val beforeReplace: ()->Unit={},
) {
    private val file=directory.toAbsolutePath().normalize().resolve("recent-projects.json")
    fun load(): RecentProjects {
        if(!Files.exists(file)) return RecentProjects(emptyList())
        return try {
            val bytes=Files.newInputStream(file).use { it.readNBytes(65537) }
            require(bytes.size<=65536) { "Recent projects state is too large" }
            val root=Json.parseToJsonElement(bytes.toString(Charsets.UTF_8)).jsonObject
            require(root["version"]?.jsonPrimitive?.int==1) { "Unsupported recent projects version" }
            val paths=root.getValue("projects").jsonArray.map {
                val value=it.jsonPrimitive;require(value.isString)
                Path.of(value.content).also { path -> require(path.isAbsolute) }.normalize()
            }.distinct().take(10)
            RecentProjects(paths)
        } catch(failure: Exception) { RecentProjects(emptyList(),"Recent projects could not be read: ${failure.message}") }
    }
    fun record(path: Path): List<Path> {
        val normalized=path.toAbsolutePath().normalize()
        val paths=(listOf(normalized)+load().paths).distinct().take(10)
        val source=buildJsonObject { put("version",1);put("projects",JsonArray(paths.map { JsonPrimitive(it.toString()) })) }.toString()
        require(source.toByteArray(Charsets.UTF_8).size<=65536) { "Recent projects state is too large" }
        Files.createDirectories(file.parent)
        val temporary=Files.createTempFile(file.parent,".sle-recent-",".tmp")
        try {
            Files.writeString(temporary,source);beforeReplace()
            Files.move(temporary,file,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING)
        } finally { Files.deleteIfExists(temporary) }
        return paths
    }
}
