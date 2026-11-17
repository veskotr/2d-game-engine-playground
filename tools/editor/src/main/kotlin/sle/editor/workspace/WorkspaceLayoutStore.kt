package sle.editor.workspace

import java.nio.file.*
import java.nio.channels.FileChannel
import java.nio.ByteBuffer
import java.util.concurrent.*

data class WorkspaceLayoutKey(val path: Path) {
    companion object {
        fun project(root: Path) = WorkspaceLayoutKey(root.toAbsolutePath().normalize().resolve(".sle/workspace-layout.json"))
        fun standalone(): WorkspaceLayoutKey {
            val override=System.getProperty("sle.workspaceData")
            val base=override?.let(Path::of) ?: System.getenv("LOCALAPPDATA")?.let { Path.of(it,"SLE","EntityEditor") }
                ?: Path.of(System.getProperty("user.home"),".local","share","sle","entity-editor")
            return WorkspaceLayoutKey(base.toAbsolutePath().normalize().resolve("workspace-layout.json"))
        }
    }
}
data class WorkspaceLayoutLoad(val layout: DockLayout?, val notice: String? = null)

/** One FIFO worker owns all reads/writes and revision watermarks, including project-switch barriers. */
class WorkspaceLayoutStore(private val codec: WorkspaceLayoutCodec, private val beforeReplace: ()->Unit = {}) : AutoCloseable {
    private val worker=Executors.newSingleThreadExecutor { r -> Thread(r,"workspace-layout-io").apply { isDaemon=true } }
    private val revisions=mutableMapOf<WorkspaceLayoutKey,Long>()
    private val completed = mutableMapOf<WorkspaceLayoutKey,Long>()
    @Volatile var lastFailure: String? = null
        private set
    fun load(key: WorkspaceLayoutKey): CompletableFuture<WorkspaceLayoutLoad> = CompletableFuture.supplyAsync({
        try {
            if(!Files.exists(key.path)) WorkspaceLayoutLoad(null) else {
                val bytes=Files.newInputStream(key.path).use { it.readNBytes(WorkspaceLayoutCodec.MAX_BYTES+1) }
                require(bytes.size<=WorkspaceLayoutCodec.MAX_BYTES) { "Layout exceeds 1 MiB" }
                WorkspaceLayoutLoad(codec.decode(bytes.toString(Charsets.UTF_8)))
            }
        } catch(e: Exception) { WorkspaceLayoutLoad(null,"Workspace layout recovered to defaults: ${e.message}") }
    },worker)
    fun save(key: WorkspaceLayoutKey, revision: Long, layout: DockLayout) {
        worker.execute {
            if(revision<(revisions[key] ?: Long.MIN_VALUE) || completed[key]==revision) return@execute
            revisions[key]=revision
            var temporary: Path?=null
            try {
                val bytes=codec.encode(layout).toByteArray(Charsets.UTF_8)
                Files.createDirectories(key.path.parent)
                temporary=Files.createTempFile(key.path.parent,"workspace-layout-",".tmp")
                FileChannel.open(temporary,StandardOpenOption.WRITE).use { channel ->
                    val buffer=ByteBuffer.wrap(bytes);while(buffer.hasRemaining()) channel.write(buffer);channel.force(true)
                }
                beforeReplace()
                // No non-atomic fallback: an unsupported/failed replace preserves the valid file.
                Files.move(temporary,key.path,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING)
                completed[key]=revision
                lastFailure=null
            } catch(e: Exception) { lastFailure="Could not save workspace layout: ${e.message}" }
            finally { temporary?.let { runCatching { Files.deleteIfExists(it) } } }
        }
    }
    fun flush() { worker.submit {}.get() }
    override fun close() { worker.shutdown();check(worker.awaitTermination(30,TimeUnit.SECONDS)) { "Workspace IO did not finish" } }
}
