package sle.editor.scripts

import kotlinx.serialization.json.*
import sle.editor.workspace.WorkspaceLayoutKey
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING

internal class ScriptEditorSettingsStore(directory: Path = WorkspaceLayoutKey.standalone().path.parent) {
    private val file = directory.toAbsolutePath().normalize().resolve("script-editor.json")

    fun load(): ScriptEditorSettings? {
        if (Files.notExists(file)) return null
        val json = Json.parseToJsonElement(Files.readString(file)).jsonObject

        fun string(value: JsonElement?, field: String): String {
            require(value is JsonPrimitive && value.isString) { "Script editor setting '$field' must contain text." }
            return value.content
        }

        fun arguments(field: String): List<String> {
            val value = json[field]
            require(value is JsonArray) { "Script editor setting '$field' must be an array." }
            return value.map { string(it, field) }
        }

        return ScriptEditorSettings(
            string(json["executable"], "executable"),
            arguments("fileArguments"),
            if ("lineArguments" in json) arguments("lineArguments") else null,
        )
    }

    fun save(settings: ScriptEditorSettings) {
        val json = buildJsonObject {
            put("executable", settings.executable)
            putJsonArray("fileArguments") { settings.fileArguments.forEach { add(it) } }
            settings.lineArguments?.let { arguments ->
                putJsonArray("lineArguments") { arguments.forEach { add(it) } }
            }
        }
        Files.createDirectories(file.parent)
        val temporary = Files.createTempFile(file.parent, "script-editor-", ".tmp")
        try {
            Files.writeString(temporary, json.toString())
            Files.move(temporary, file, ATOMIC_MOVE, REPLACE_EXISTING)
        } finally {
            Files.deleteIfExists(temporary)
        }
    }

    fun reset() {
        Files.deleteIfExists(file)
    }
}
