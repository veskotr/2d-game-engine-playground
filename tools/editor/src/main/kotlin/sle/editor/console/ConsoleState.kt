package sle.editor.console

import androidx.compose.runtime.*
import kotlinx.serialization.json.*
import java.nio.file.Path
import java.util.prefs.Preferences

internal enum class ConsoleSeverity { INFO, WARN, ERROR, DEBUG }

internal data class LuaSource(
    val file: String,
    val line: Int,
    val entity: Long,
    val behaviour: String,
    val traceback: String,
) {
    val location: String get() = if (line > 0) "$file:$line" else file
    val path: Path? get() = file.takeIf { it.isNotBlank() }?.let {
        runCatching { Path.of(it).toAbsolutePath().normalize() }.getOrNull()
    }
}

internal data class ConsoleMessage(val severity: ConsoleSeverity, val message: String, val lua: LuaSource? = null)
internal data class ConsoleRow(val entry: ConsoleMessage, val repeats: Int = 1)

internal class ConsoleState(private val capacity: Int = 2000, persistPreferences: Boolean = true) {
    private val prefs = if (persistPreferences) runCatching { Preferences.userRoot().node("sle/editor/console") }.getOrNull() else null
    var clearOnPlay by mutableStateOf(runCatching { prefs?.getBoolean("clearOnPlay", true) ?: true }.getOrDefault(true))
        private set
    var pauseOnLuaError by mutableStateOf(runCatching { prefs?.getBoolean("pauseOnLuaError", false) ?: false }.getOrDefault(false))
        private set
    var rows by mutableStateOf<List<ConsoleRow>>(emptyList())
        private set
    var dropped by mutableStateOf(0L)
        private set
    var query by mutableStateOf("")
    var severities by mutableStateOf(ConsoleSeverity.entries.toSet())

    var commandHistory by mutableStateOf<List<String>>(emptyList())
        private set
    var commandDraft by mutableStateOf("")
    var commandRunning by mutableStateOf(false)
    var inputRequest by mutableStateOf(0)
        private set

    fun focusCommand() { inputRequest++ }

    fun recordCommand(source: String) {
        if (commandHistory.lastOrNull() != source) commandHistory = (commandHistory + source).takeLast(50)
        commandMessage("> " + source.take(1024))
    }

    fun commandMessage(message: String, severity: ConsoleSeverity = ConsoleSeverity.INFO) =
        append(listOf(ConsoleMessage(severity, message)), 0)

    fun updateClearOnPlay(value: Boolean) {
        clearOnPlay = value
        runCatching { prefs?.putBoolean("clearOnPlay", value) }
    }

    fun updatePauseOnLuaError(value: Boolean) {
        pauseOnLuaError = value
        runCatching { prefs?.putBoolean("pauseOnLuaError", value) }
    }

    fun append(entries: List<ConsoleMessage>, lost: Long) {
        if (entries.isEmpty() && lost == 0L) return
        val updated = ArrayDeque(rows)
        dropped += lost
        for (entry in entries) {
            val last = updated.lastOrNull()
            if (last?.entry == entry) {
                updated.removeLast()
                updated.addLast(last.copy(repeats = last.repeats + 1))
            } else {
                updated.addLast(ConsoleRow(entry))
                if (updated.size > capacity) dropped += updated.removeFirst().repeats
            }
        }
        rows = updated.toList()
    }

    fun accept(batch: ByteArray) {
        val root = Json.parseToJsonElement(batch.decodeToString()).jsonObject
        fun JsonObject.field(name: String) = getValue(name).jsonPrimitive
        val entries = root.getValue("messages").jsonArray.map {
            val entry = it.jsonObject
            val lua = entry["lua"]?.jsonObject?.let { source ->
                LuaSource(source.field("file").content, source.field("line").int,
                    source.field("entity").long, source.field("behaviour").content, source.field("traceback").content)
            }
            ConsoleMessage(ConsoleSeverity.valueOf(entry.field("level").content), entry.field("message").content, lua)
        }
        append(entries, root.field("dropped").long)
    }

    fun clearHistory() {
        rows = emptyList()
        dropped = 0
    }
}
