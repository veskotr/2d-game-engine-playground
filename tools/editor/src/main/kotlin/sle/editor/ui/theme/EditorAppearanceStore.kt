package sle.editor.ui.theme

import java.nio.file.*
import java.nio.channels.FileChannel
import java.nio.ByteBuffer
import kotlinx.serialization.json.*
import sle.editor.workspace.WorkspaceLayoutKey

/** Bounded user-local settings. Failed atomic replacement leaves the previous file intact. */
class EditorAppearanceStore(
    directory: Path = WorkspaceLayoutKey.standalone().path.parent,
    private val beforeReplace: ()->Unit = {},
) {
    private val file=directory.toAbsolutePath().normalize().resolve("appearance.json")
    var lastFailure: String? = null
        private set
    fun load(): EditorAppearance {
        lastFailure=null
        if(!Files.exists(file)) return EditorAppearance()
        return try {
            val bytes=Files.newInputStream(file).use { it.readNBytes(8193) }
            require(bytes.size<=8192) { "Appearance settings too large" }
            val obj=Json.parseToJsonElement(bytes.toString(Charsets.UTF_8)).jsonObject
            require(obj["version"]?.jsonPrimitive?.int==1) { "Unsupported appearance version" }
            EditorAppearance(
                obj["mode"]?.jsonPrimitive?.content?.let(EditorThemeMode::valueOf) ?: EditorThemeMode.Dark,
                obj["density"]?.jsonPrimitive?.content?.let(EditorDensity::valueOf) ?: EditorDensity.Compact,
                obj["scale"]?.jsonPrimitive?.float ?: 1f,
            )
        } catch(e: Exception) {
            lastFailure="Appearance settings recovered to defaults: ${e.message}"
            EditorAppearance()
        }
    }
    fun save(value: EditorAppearance): Boolean {
        var temporary: Path?=null
        return try {
            val bytes=buildJsonObject {
                put("version",1);put("mode",value.mode.name);put("density",value.density.name);put("scale",value.scale)
            }.toString().toByteArray(Charsets.UTF_8)
            Files.createDirectories(file.parent)
            temporary=Files.createTempFile(file.parent,"appearance-",".tmp")
            FileChannel.open(temporary,StandardOpenOption.WRITE).use { channel ->
                val buffer=ByteBuffer.wrap(bytes);while(buffer.hasRemaining()) channel.write(buffer);channel.force(true)
            }
            beforeReplace()
            Files.move(temporary,file,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING)
            lastFailure=null;true
        } catch(e: Exception) { lastFailure="Could not save appearance settings: ${e.message}";false }
        finally { temporary?.let { runCatching { Files.deleteIfExists(it) } } }
    }
}
