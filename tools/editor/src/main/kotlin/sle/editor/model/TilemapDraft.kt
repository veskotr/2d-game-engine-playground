package sle.editor.model

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.security.MessageDigest

/** Shared by all placements of this asset in one workspace; scene history stores its values. */
internal class TilemapDraft private constructor(val path: Path, source: ByteArray) {
    private var disk = source
    private val sidecar = path.resolveSibling("${path.fileName}.slemeta")
    private var identity = if (Files.exists(sidecar, NOFOLLOW_LINKS)) Files.readAllBytes(sidecar) else null
    var document by mutableStateOf(TilemapDocument.parse(source.decodeToString()))
    private var saved by mutableStateOf(document)
    val dirty get() = document != saved
    private var previewFolder: Path? = null

    fun checkSource() {
        require(Files.isRegularFile(path, NOFOLLOW_LINKS) && Files.size(path) <= 16 * 1024 * 1024 && Files.readAllBytes(path).contentEquals(disk)) {
            "${path.fileName} changed on disk; reopen it before saving"
        }
        val current = if (Files.exists(sidecar, NOFOLLOW_LINKS)) Files.readAllBytes(sidecar) else null
        require(if (identity == null) current == null else current != null && identity.contentEquals(current)) { "Tilemap asset identity changed" }
    }

    fun reload() {
        require(Files.isRegularFile(path, NOFOLLOW_LINKS) && Files.size(path) <= 16 * 1024 * 1024) { "Missing or oversized tilemap asset" }
        val bytes = Files.readAllBytes(path)
        val parsed = TilemapDocument.parse(bytes.decodeToString())
        val metadata = if (Files.exists(sidecar, NOFOLLOW_LINKS)) Files.readAllBytes(sidecar) else null
        disk = bytes; identity = metadata; saved = parsed; document = parsed
    }

    fun save() {
        if (!dirty) return
        checkSource()
        val captured = document
        val bytes = captured.serialize().encodeToByteArray()
        val temporary = Files.createTempFile(path.parent, ".tilemap-", ".tmp")
        try {
            Files.write(temporary, bytes)
            checkSource()
            Files.move(temporary, path, ATOMIC_MOVE, REPLACE_EXISTING)
        } finally { Files.deleteIfExists(temporary) }
        disk = bytes; saved = captured
    }

    /** Immutable snapshots prevent a native preview from observing a partially updated map. */
    fun previewPath(): Path {
        val bytes = document.serialize().encodeToByteArray()
        val hash = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        val folder = previewFolder ?: Files.createTempDirectory("sle-map-preview-").also { previewFolder = it; it.toFile().deleteOnExit() }
        return folder.resolve("$hash.tilemap.json").also {
            if (!Files.exists(it)) { Files.write(it, bytes); it.toFile().deleteOnExit() }
        }
    }

    companion object {
        fun open(path: Path): TilemapDraft {
            val absolute = path.toAbsolutePath().normalize()
            require(Files.isRegularFile(absolute, NOFOLLOW_LINKS) && Files.size(absolute) <= 16 * 1024 * 1024) { "Missing or oversized tilemap asset" }
            return TilemapDraft(absolute, Files.readAllBytes(absolute))
        }
    }
}
