package sle.editor.workspace

import kotlin.test.*

class DockSizingTest {
    private val a = PanelId("a")
    private val b = PanelId("b")
    private val registry = PanelRegistry(listOf(PanelDescriptor(a, "A", PanelMinimum(100f, 80f)), PanelDescriptor(b, "B", PanelMinimum(200f, 40f))))
    private val first = DockNode.Tabs(GroupId("a"), listOf(a), a)
    private val second = DockNode.Tabs(GroupId("b"), listOf(b), b)
    private val chrome = DockChrome(tabHeight = 20f, splitter = 4f, restoreStrip = 24f)
    @Test fun recursiveMinimaIncludeChromeAndAllTabs() {
        assertEquals(PanelMinimum(200f, 100f), nodeMinimum(first.copy(panels = listOf(a, b)), registry, chrome))
        assertEquals(PanelMinimum(304f, 100f), nodeMinimum(DockNode.Split(DockAxis.Horizontal, .5f, first, second), registry, chrome))
        assertEquals(PanelMinimum(200f, 164f), nodeMinimum(DockNode.Split(DockAxis.Vertical, .5f, first, second), registry, chrome))
    }
    @Test fun splitRatiosRespectBothChildrenAndRejectNonFiniteValues() {
        assertEquals(.25f, clampSplitRatio(-1f, 404f, 100f, 200f, 4f))
        assertEquals(.5f, clampSplitRatio(2f, 404f, 100f, 200f, 4f))
        assertEquals(.5f, clampSplitRatio(Float.NaN, 404f, 100f, 200f, 4f))
        assertEquals(.5f, clampSplitRatio(.1f, 100f, 100f, 200f, 4f))
    }
    @Test fun asymmetricPositiveMinimaAreNeverReducedByEndpointSafeguards() {
        assertEquals(.9995f, clampSplitRatio(.5f, 1006f, 999.5f, .5f))
        val ratio = clampSplitRatio(.5f, 1006f, .5f, 999.5f)
        assertTrue(ratio * 1000f >= .5f)
        assertTrue((1f - ratio) * 1000f >= 999.5f)
    }
    @Test fun zeroMinimumChildSignalsImpossibleStrictlyInteriorRatio() {
        assertEquals(.5f, clampSplitRatio(1f, 104f, 100f, 0f, 4f))
        assertEquals(.5f, clampSplitRatio(0f, 104f, 0f, 100f, 4f))
    }
    @Test fun smallDisplaysRecoverToTabsWithoutLosingPanelsOrActiveTab() {
        val layout = DockLayout(HostId("main"), listOf(DockHost(HostId("main"), DockNode.Split(DockAxis.Horizontal, .5f, first, second), LogicalBounds(0f, 0f, 600f, 400f))))
        val fitted = fitHostToWorkArea(layout, HostId("main"), LogicalBounds(-300f, 0f, 250f, 200f), registry, chrome)
        assertEquals(emptyList(), validateLayout(fitted, setOf(a, b)))
        assertEquals(listOf(a, b), fitted.tabs().single().panels)
        assertEquals(a, fitted.tabs().single().active)
        assertEquals(-300f, fitted.hosts.single().bounds.x)
        val tiny = fitHostToWorkArea(layout, HostId("main"), LogicalBounds(0f, 0f, 50f, 40f), registry, chrome)
        assertEquals(50f, tiny.hosts.single().bounds.width)
        assertEquals(40f, tiny.hosts.single().bounds.height)
    }
    @Test fun hostMinimaIncludeRestoreStripAndInsetsAndScaleIsExplicit() {
        val host = DockHost(HostId("main"), first, LogicalBounds(0f, 0f, 400f, 300f))
        assertEquals(PanelMinimum(110f, 154f), hostMinimum(host, registry, chrome, hasMinimized = true, insets = PanelMinimum(10f, 30f)))
        assertEquals(PanelMinimum(150f, 150f), nodeMinimum(first, registry, chrome, scale = 1.5f))
        assertFailsWith<IllegalArgumentException> { nodeMinimum(first, registry, chrome, scale = 0f) }
    }
    @Test fun dividerResizeClampsBothEndsAndRejectsStalePath() {
        val main=HostId("main")
        val layout=DockLayout(main,listOf(DockHost(main,DockNode.Split(DockAxis.Horizontal,.5f,first,second),LogicalBounds(0f,0f,600f,400f))))
        val left=resizeDockSplit(layout,main,emptyList(),-1f,406f,registry)
        assertEquals(.25f,(left.hosts.single().root as DockNode.Split).ratio)
        val right=resizeDockSplit(layout,main,emptyList(),2f,406f,registry)
        assertEquals(.5f,(right.hosts.single().root as DockNode.Split).ratio)
        assertEquals(layout,resizeDockSplit(layout,main,listOf(true),.9f,406f,registry))
        assertEquals(layout,resizeDockSplit(layout,HostId("stale"),emptyList(),.9f,406f,registry))
    }
}
