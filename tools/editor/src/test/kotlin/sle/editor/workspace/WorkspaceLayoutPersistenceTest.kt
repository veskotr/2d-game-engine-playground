package sle.editor.workspace

import kotlin.test.*
import org.junit.Test
import java.nio.file.Files

class WorkspaceLayoutPersistenceTest {
    private val registry = PanelRegistry(standardPanels())
    private val codec = WorkspaceLayoutCodec(registry)
    private val base = defaultDockLayout(registry.panels)
    @Test fun roundtripAllPlacements() {
        val layout = minimizePanel(hidePanel(movePanel(base,PanelId("game"),DockTarget.Detached(LogicalBounds(-900f,20f,700f,600f))),PanelId("assets")),PanelId("inspector"))
        assertEquals(layout,codec.decode(codec.encode(layout)))
    }
    @Test fun rejectsMalformedUnsupportedOversizedAndDeepInput() {
        for(value in listOf("{", "{}", codec.encode(base).replace("\"version\":1","\"version\":99"), " ".repeat(1048577), "[".repeat(20000)))
            assertFails { codec.decode(value) }
    }
    @Test fun prunesUnknownAndAddsNewPanelsOnce() {
        val old = PanelRegistry(standardPanels().filter { it.id.value!="game" } + PanelDescriptor(PanelId("retired"),"Retired",PanelMinimum(1f,1f)))
        val restored = codec.decode(WorkspaceLayoutCodec(old).encode(defaultDockLayout(old.panels)))
        assertTrue(validateLayout(restored,registry.byId.keys).isEmpty())
        assertFalse(restored.tabs().any { PanelId("retired") in it.panels })
        assertEquals(1,restored.tabs().sumOf { it.panels.count { id -> id==PanelId("game") } })
    }
    @Test fun rejectsDuplicatePanelsAndEmptyGroups() {
        val text=codec.encode(base)
        assertFails { codec.decode(text.replace("\"game\"","\"scene\"")) }
        assertFails { codec.decode(text.replace("[\"assets\"]","[]")) }
    }
    @Test fun orderedWritesKeepKeysAndUiScaleSeparate() {
        val root=Files.createTempDirectory("sle-layout")
        val a=WorkspaceLayoutKey.project(root.resolve("a"));val b=WorkspaceLayoutKey.project(root.resolve("b"))
        Files.createDirectories(a.path.parent)
        val scale=a.path.resolveSibling("editor-state.json");Files.writeString(scale,"scale")
        WorkspaceLayoutStore(codec).use { store ->
            store.save(a,3,hidePanel(base,PanelId("assets")))
            store.save(a,2,base)
            store.save(b,4,base)
            store.flush()
            assertTrue(PanelId("assets") in store.load(a).get().layout!!.hidden)
            assertEquals(base,store.load(b).get().layout)
            assertEquals("scale",Files.readString(scale))
        }
    }
    @Test fun failedAtomicWritePreservesLastValidFile() {
        val key=WorkspaceLayoutKey.project(Files.createTempDirectory("sle-layout"))
        WorkspaceLayoutStore(codec).use { it.save(key,1,base);it.flush() }
        WorkspaceLayoutStore(codec,beforeReplace={ error("interrupted") }).use { store ->
            store.save(key,2,hidePanel(base,PanelId("assets")));store.flush()
            assertEquals(base,store.load(key).get().layout)
            assertNotNull(store.lastFailure)
        }
    }
    @Test fun enforcesHostNodeAndDepthLimitsBeforeConstruction() {
        val tab="""{"group":"g","panels":["scene"],"active":"scene"}"""
        fun chain(depth: Int): String = if(depth==1) tab else """{"axis":"Horizontal","ratio":0.5,"first":${chain(depth-1)},"second":$tab}"""
        // Unique groups/panels prevent duplicate detection from masking tree limits.
        fun unique(tree: String): String {
            var n=0
            return Regex(Regex.escape(""""group":"g","panels":["scene"],"active":"scene"""")).replace(tree) {
                val id=++n;"\"group\":\"g$id\",\"panels\":[\"p$id\"],\"active\":\"p$id\""
            }
        }
        fun document(tree: String) = """{"version":1,"mainHost":"main","hosts":[{"id":"main","bounds":[0,0,800,600],"root":$tree}],"hidden":{},"minimized":{}}"""
        assertEquals("Layout tree limit",assertFailsWith<IllegalArgumentException> { codec.decode(document(unique(chain(33)))) }.message)
        fun wide(depth: Int): String = if(depth==1) tab else """{"axis":"Vertical","ratio":0.5,"first":${wide(depth-1)},"second":${wide(depth-1)}}"""
        assertEquals("Layout tree limit",assertFailsWith<IllegalArgumentException> { codec.decode(document(unique(wide(9)))) }.message)
        val host="""{"id":"h","bounds":[0,0,800,600],"root":null}"""
        assertFails { codec.decode("""{"version":1,"mainHost":"main","hosts":[${List(34) { host.replace("\"h\"","\"h$it\"") }.joinToString(",")}],"hidden":{},"minimized":{}}""") }
    }
    @Test fun boundedReadAndCorruptFileProduceNotice() {
        val key=WorkspaceLayoutKey.project(Files.createTempDirectory("sle-layout-corrupt"))
        Files.createDirectories(key.path.parent)
        WorkspaceLayoutStore(codec).use { store ->
            for(text in listOf("{broken","x".repeat(1048577))) {
                Files.writeString(key.path,text)
                val result=store.load(key).get()
                assertNull(result.layout);assertNotNull(result.notice)
            }
        }
    }
    @Test fun duplicateObjectKeysCannotHideDuplicatePanelPlacements() {
        val text=codec.encode(hidePanel(base,PanelId("assets")))
        val duplicate=text.replace("\"hidden\":{", "\"hidden\":{\"assets\":{\"host\":\"main\",\"group\":\"assets\",\"index\":0,\"bounds\":[0,0,800,600]},")
        assertFailsWith<IllegalArgumentException> { codec.decode(duplicate) }
    }
    @Test fun retriesFailedRevisionWithoutAllowingOlderWrites() {
        val key=WorkspaceLayoutKey.project(Files.createTempDirectory("sle-layout-retry"))
        var fail=true
        WorkspaceLayoutStore(codec,beforeReplace={ if(fail) error("transient failure") }).use { store ->
            val latest=hidePanel(base,PanelId("assets"))
            store.save(key,3,latest);store.flush();assertNotNull(store.lastFailure)
            fail=false
            store.save(key,2,base);store.save(key,3,latest);store.flush()
            assertEquals(latest,store.load(key).get().layout)
            assertNull(store.lastFailure)
        }
    }
    @Test fun projectSwitchWhileWriteIsBlockedKeepsCapturedDestinations() {
        val root=Files.createTempDirectory("sle-layout-inflight")
        val a=WorkspaceLayoutKey.project(root.resolve("a"));val b=WorkspaceLayoutKey.project(root.resolve("b"))
        val entered=java.util.concurrent.CountDownLatch(1);val release=java.util.concurrent.CountDownLatch(1)
        WorkspaceLayoutStore(codec,beforeReplace={ entered.countDown();check(release.await(5,java.util.concurrent.TimeUnit.SECONDS)) }).use { store ->
            try {
                store.save(a,1,hidePanel(base,PanelId("assets")))
                assertTrue(entered.await(5,java.util.concurrent.TimeUnit.SECONDS))
                store.save(b,2,hidePanel(base,PanelId("game")))
                val next=store.load(b)
                release.countDown()
                assertTrue(PanelId("game") in next.get().layout!!.hidden)
                assertTrue(PanelId("assets") in store.load(a).get().layout!!.hidden)
            } finally { release.countDown() }
        }
    }
}
