package sle.editor.project.assets

import java.nio.file.*
import com.sun.nio.file.ExtendedWatchEventModifier
import java.nio.file.StandardWatchEventKinds.*
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/** Watch notifications schedule authoritative scans; reconciliation handles lost events. */
internal class AssetBrowserService(
    private val projectRoot: Path,private val assetRoot: Path,
    private val reconcileMillis: Long=3000,private val watchEnabled: Boolean=true,
    private val onSnapshot: (AssetSnapshot)->Unit,
): AutoCloseable {
    private val closed=AtomicBoolean(false)
    private val requested=AtomicBoolean(true)
    private val worker=Thread({run()},"SLE Asset Browser").apply { isDaemon=true;start() }
    fun refresh() { requested.set(true) }
    override fun close() { closed.set(true);worker.interrupt() }
    fun awaitStopped(milliseconds: Long): Boolean { worker.join(milliseconds);return !worker.isAlive }
    private fun run() {
        var watch=if(watchEnabled) runCatching { assetRoot.fileSystem.newWatchService() }.getOrNull() else null
        val keys=mutableMapOf<Path,WatchKey>()
        var lastScan=0L;var dirty=true
        try {
            while(!closed.get()) {
                val now=System.nanoTime()
                if(requested.getAndSet(false)) dirty=true
                if((dirty && now-lastScan>=TimeUnit.MILLISECONDS.toNanos(150)) || now-lastScan>=TimeUnit.MILLISECONDS.toNanos(reconcileMillis)) {
                    val snapshot=AssetScanner.scan(projectRoot,assetRoot) { closed.get() }
                    if(closed.get()) break
                    // Windows needs one recursive root handle: handles on children can
                    // prevent Explorer from renaming their parent directories.
                    val windows=System.getProperty("os.name").startsWith("Windows")
                    val dirs=(if(windows) emptySet() else snapshot.entries.filter { it.kind==AssetKind.Directory }.map { assetRoot.resolve(it.path) }.toSet())+setOf(assetRoot,assetRoot.parent)
                    keys.keys.filter { it !in dirs || !keys.getValue(it).isValid || !Files.isDirectory(it) }.toList().forEach { keys.remove(it)?.cancel() }
                    if(watch!=null) {
                        for(dir in dirs) if(dir !in keys) runCatching {
                            if(Files.isDirectory(dir) && !Files.isSymbolicLink(dir) && dir.toRealPath().startsWith(projectRoot.toRealPath()))
                                keys[dir]=if(windows && dir==assetRoot)
                                    dir.register(watch,arrayOf(ENTRY_CREATE,ENTRY_DELETE,ENTRY_MODIFY),ExtendedWatchEventModifier.FILE_TREE)
                                else dir.register(watch,ENTRY_CREATE,ENTRY_DELETE,ENTRY_MODIFY)
                        }
                    }
                    onSnapshot(snapshot)
                    lastScan=System.nanoTime();dirty=false
                }
                if(watch!=null) {
                    try {
                        var key=watch.poll(200,TimeUnit.MILLISECONDS)
                        while(key!=null) {
                            if(key.pollEvents().isNotEmpty()) dirty=true
                            if(!key.reset()) keys.entries.removeIf { it.value===key }
                            key=watch.poll()
                        }
                    } catch(_: ClosedWatchServiceException) { watch=null;keys.clear();dirty=true }
                } else Thread.sleep(200)
            }
        } catch(_: InterruptedException) {
            // Closing the browser interrupts its worker and releases native watcher handles.
        } catch(failure: Exception) {
            if(!closed.get()) onSnapshot(AssetSnapshot(emptyList(),listOf(failure.message ?: "Asset watcher failed")))
        } finally { watch?.close() }
    }
}
