package sle.editor.console

import kotlinx.serialization.json.*
import sle.editor.project.OpenedProject
import sle.editor.project.relativeProjectPath
import sle.editor.project.validateProjectFolderName
import sle.editor.project.assets.AssetIdentity
import sle.editor.project.assets.assetPath
import sle.editor.project.assets.atomicAssetWrite
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.util.UUID

/** Create-only text assets. Both the payload and its stable identity participate in history. */
internal class ConsoleAssets(private val project: OpenedProject, edits: List<JsonObject>) {
    private data class Asset(val relative: String, val contents: ByteArray, val metadata: ByteArray)

    private val assets = edits.map { edit ->
        val relative = edit.getValue("path").jsonPrimitive.also { require(it.isString) }.content
        relativeProjectPath(relative).forEach {
            validateProjectFolderName(it)
            require(!it.endsWith(".slemeta", true)) { "Reserved metadata suffix" }
        }
        val text = edit.getValue("contents").jsonPrimitive.also { require(it.isString) }.content
        Asset(relative, text.encodeToByteArray(), AssetIdentity.encode(UUID.randomUUID().toString()).encodeToByteArray())
    }.also { entries ->
        require(entries.map { it.relative.lowercase() }.distinct().size == entries.size) { "Repeated asset destination" }
    }

    fun create() = update(creating = true)
    fun undo() = update(creating = false)

    private fun update(creating: Boolean) = synchronized(AssetIdentity) {
        val files = assets.flatMap { asset ->
            val target = assetPath(project.root, project.assets, asset.relative)
            require(Files.isDirectory(target.parent, NOFOLLOW_LINKS)) { "Asset parent folder does not exist" }
            listOf(AssetIdentity.sidecar(target) to asset.metadata, target to asset.contents)
        }
        for ((path, bytes) in files) {
            if (creating) require(!Files.exists(path, NOFOLLOW_LINKS)) { "Asset destination exists: ${path.fileName}" }
            else require(Files.isRegularFile(path, NOFOLLOW_LINKS) && Files.size(path) == bytes.size.toLong() &&
                Files.readAllBytes(path).contentEquals(bytes)) { "Created asset has changed: ${path.fileName}" }
        }
        fun change(path: Path, bytes: ByteArray, write: Boolean) {
            if (write) atomicAssetWrite(path, bytes, replace = false)
            else Files.delete(path)
        }
        val completed = mutableListOf<Pair<Path, ByteArray>>()
        try {
            for ((path, bytes) in if (creating) files else files.asReversed()) {
                change(path, bytes, creating)
                completed += path to bytes
            }
        } catch (failure: Exception) {
            for ((path, bytes) in completed.asReversed()) {
                try { change(path, bytes, !creating) }
                catch (rollback: Exception) { failure.addSuppressed(rollback) }
            }
            throw failure
        }
    }
}
