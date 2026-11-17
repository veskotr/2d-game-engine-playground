package sle.editor.project.assets

import java.nio.file.*
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.test.*

class AssetBrowserTest {
    private fun root()=Files.createTempDirectory("sle-browser-test-")
    @Test fun scannerShowsDiskContentsAndUnknownFilesWithoutAnyManifest() {
        val project=root();val assets=Files.createDirectories(project.resolve("Assets"))
        Files.createDirectories(assets.resolve("Scripts/Nested"))
        Files.writeString(assets.resolve("Scripts/Nested/main.lua"),"return {}")
        Files.writeString(assets.resolve("custom.unknown"),"abc")
        Files.createDirectories(project.resolve(".sle/cache"));Files.writeString(project.resolve(".sle/cache/generated.txt"),"cache")
        val result=AssetScanner.scan(project,assets)
        assertTrue(result.errors.isEmpty(),result.errors.joinToString())
        assertEquals(setOf("Scripts","Scripts/Nested","Scripts/Nested/main.lua","custom.unknown"),result.entries.map { it.path }.toSet())
        assertEquals(3L,result.entries.single { it.path=="custom.unknown" }.size)
        assertTrue(result.entries.single { it.path=="Scripts" }.kind==AssetKind.Directory)
    }
    @Test fun rescansReflectExternalRenameMoveDeleteAndRepairSelection() {
        val project=root();val assets=Files.createDirectories(project.resolve("Assets"))
        Files.createDirectories(assets.resolve("A/Nested"));Files.createDirectories(assets.resolve("B"))
        Files.writeString(assets.resolve("A/Nested/item.txt"),"before")
        val state=AssetBrowserState();state.accept(AssetScanner.scan(project,assets))
        state.navigate("A/Nested");state.select("A/Nested/item.txt")
        Files.move(assets.resolve("A/Nested/item.txt"),assets.resolve("B/renamed.txt"))
        Files.delete(AssetIdentity.sidecar(assets.resolve("A/Nested/item.txt")))
        Files.delete(assets.resolve("A/Nested"))
        state.accept(AssetScanner.scan(project,assets))
        assertEquals("A",state.folder);assertNull(state.selected)
        state.navigate("B");assertEquals(listOf("B/renamed.txt"),state.children.map { it.path })
        Files.delete(assets.resolve("B/renamed.txt"));state.accept(AssetScanner.scan(project,assets))
        assertTrue(state.children.isEmpty())
        assertFails { state.navigate("../outside") }
    }
    @Test fun staleUiClicksAfterExternalRemovalAreIgnored() {
        val state=AssetBrowserState()
        state.accept(AssetSnapshot(listOf(AssetEntry("Gone",AssetKind.Directory),AssetEntry("Gone/file.txt",AssetKind.File))))
        state.accept(AssetSnapshot(emptyList()))
        state.navigate("Gone");state.select("Gone/file.txt")
        assertEquals("",state.folder);assertNull(state.selected)
        assertFails { state.navigate("../outside") }
    }
    @Test fun missingAssetRootHasVisibleErrorAndRecovers() {
        val project=root();val assets=project.resolve("Assets")
        assertTrue(AssetScanner.scan(project,assets).errors.isNotEmpty())
        Files.createDirectory(assets)
        assertTrue(AssetScanner.scan(project,assets).errors.isEmpty())
        assertTrue(AssetScanner.scan(project,project.parent).errors.isNotEmpty())
    }
    @Test fun linksAreShownWithoutTraversingExternalContents() {
        val project=root();val assets=Files.createDirectories(project.resolve("Assets"));val outside=root()
        Files.writeString(outside.resolve("private.txt"),"outside")
        try { Files.createSymbolicLink(assets.resolve("linked"),outside) }
        catch(failure: Exception) { org.junit.Assume.assumeNoException("Host cannot create symbolic links",failure) }
        val result=AssetScanner.scan(project,assets)
        assertEquals(listOf("linked"),result.entries.map { it.path })
        assertEquals(AssetKind.Link,result.entries.single().kind)
    }
    @Test fun watcherDiscoversNewSubdirectoriesAndCloses() {
        val project=root();val assets=Files.createDirectories(project.resolve("Assets"))
        val queue=LinkedBlockingQueue<AssetSnapshot>()
        val service=AssetBrowserService(project,assets,reconcileMillis=3000,onSnapshot={queue.offer(it)})
        try {
            assertNotNull(queue.poll(5,TimeUnit.SECONDS))
            Files.createDirectories(assets.resolve("New/Deep"));Files.writeString(assets.resolve("New/Deep/a.lua"),"return {}")
            await(queue) { it.entries.any { entry -> entry.path=="New/Deep/a.lua" } }
            // Windows may briefly deny rename while the index enumerates the directory.
            val until=System.nanoTime()+TimeUnit.SECONDS.toNanos(5)
            while(true) {
                try { Files.move(assets.resolve("New"),assets.resolve("Renamed"));break }
                catch(failure: FileSystemException) { if(System.nanoTime()>=until) throw failure;Thread.sleep(25) }
            }
            await(queue) { it.entries.any { entry -> entry.path=="Renamed/Deep/a.lua" } && it.entries.none { entry -> entry.path.startsWith("New") } }
        } finally { service.close() }
        assertTrue(service.awaitStopped(3000))
    }
    @Test fun reconciliationAndRefreshWorkWithoutNotifications() {
        val project=root();val assets=Files.createDirectories(project.resolve("Assets"));val queue=LinkedBlockingQueue<AssetSnapshot>()
        val service=AssetBrowserService(project,assets,reconcileMillis=250,watchEnabled=false,onSnapshot={queue.offer(it)})
        try {
            assertNotNull(queue.poll(5,TimeUnit.SECONDS))
            Files.writeString(assets.resolve("external.dat"),"data")
            await(queue) { it.entries.any { entry -> entry.path=="external.dat" } }
            Files.delete(assets.resolve("external.dat"));service.refresh()
            await(queue) { it.entries.isEmpty() }
        } finally { service.close() }
        assertTrue(service.awaitStopped(3000))
    }
    private fun await(queue: LinkedBlockingQueue<AssetSnapshot>,condition: (AssetSnapshot)->Boolean): AssetSnapshot {
        val until=System.nanoTime()+TimeUnit.SECONDS.toNanos(6)
        while(System.nanoTime()<until) { val value=queue.poll(100,TimeUnit.MILLISECONDS);if(value!=null && condition(value)) return value }
        error("Asset browser did not reflect filesystem change")
    }
}
