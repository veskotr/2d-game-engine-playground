package sle.editor.viewport

import kotlinx.serialization.json.*
import sle.editor.workspace.WorkspaceLayoutKey
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

/** `{"enabled":false,"move":1.0,"rotate":15.0,"scale":0.1}`; rotate is in degrees. */
internal fun SnapSettings.toJson(): JsonObject = buildJsonObject {
    put("enabled", enabled);put("move", move);put("rotate", rotateDegrees);put("scale", scale)
}

/** Missing fields take their defaults; wrong types and out-of-range steps throw. */
internal fun snapSettingsFrom(json: JsonElement): SnapSettings {
    val source = json as? JsonObject ?: throw IllegalArgumentException("Snap settings must be an object")
    fun number(key: String, fallback: Double) = source[key]?.let {
        val value = (it as? JsonPrimitive)?.takeUnless { primitive -> primitive.isString }?.doubleOrNull
        value ?: throw IllegalArgumentException("Snap '$key' must be a number")
    } ?: fallback
    val defaults = SnapSettings()
    val enabled = source["enabled"]?.let {
        (it as? JsonPrimitive)?.takeUnless { primitive -> primitive.isString }?.booleanOrNull ?: throw IllegalArgumentException("Snap 'enabled' must be true or false")
    } ?: defaults.enabled
    return SnapSettings(enabled, number("move", defaults.move), number("rotate", defaults.rotateDegrees), number("scale", defaults.scale))
}

/** Standalone (no project) snap settings, user-local beside the standalone layout; written atomically. */
internal class SnapSettingsStore(directory: Path = WorkspaceLayoutKey.standalone().path.parent) {
    private val file = directory.toAbsolutePath().normalize().resolve("snap-settings.json")
    var lastFailure: String? = null
        private set
    fun load(): SnapSettings {
        lastFailure = null
        if(!Files.exists(file)) return SnapSettings()
        return try {
            val bytes = Files.newInputStream(file).use { it.readNBytes(4097) }
            require(bytes.size <= 4096) { "Snap settings too large" }
            snapSettingsFrom(Json.parseToJsonElement(bytes.toString(Charsets.UTF_8)))
        } catch(failure: Exception) {
            lastFailure = "Snap settings recovered to defaults: ${failure.message}"
            SnapSettings()
        }
    }
    fun save(value: SnapSettings): Boolean {
        var temporary: Path? = null
        return try {
            Files.createDirectories(file.parent)
            temporary = Files.createTempFile(file.parent, "snap-settings-", ".tmp")
            Files.writeString(temporary, value.toJson().toString())
            Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            lastFailure = null;true
        } catch(failure: Exception) { lastFailure = "Could not save snap settings: ${failure.message}";false }
        finally { temporary?.let { runCatching { Files.deleteIfExists(it) } } }
    }
}
