package sle.editor.workspace

import kotlin.test.*

class DockLayoutTest {
    private val panels = standardPanels() + PanelDescriptor(PanelId("future"), "Future", PanelMinimum(120f, 80f))
    private val ids = panels.map { it.id }.toSet()
    private fun p(id: String) = PanelId(id)
    private fun checked(layout: DockLayout): DockLayout {
        assertEquals(emptyList(), validateLayout(layout, ids))
        return layout
    }
    @Test fun olderLayoutsKeepTheNewTilePaletteHiddenBesideAssets() {
        val previous = panels.filter { it.id != p("tilePalette") }
        val saved = WorkspaceLayoutCodec(PanelRegistry(previous)).encode(defaultDockLayout(previous))
        val codec = WorkspaceLayoutCodec(PanelRegistry(panels))
        val restored = checked(codec.decode(saved))
        val assets = restored.tabs().single { p("assets") in it.panels }
        assertEquals(assets.group, restored.hidden.getValue(p("tilePalette")).group)
        val opened = checked(restorePanel(restored, p("tilePalette")))
        assertTrue(p("tilePalette") in opened.tabs().single { it.group == assets.group }.panels)
        val detached = checked(movePanel(opened, p("tilePalette"),
            DockTarget.Detached(LogicalBounds(100f, 100f, 490f, 620f))))
        assertEquals(detached, codec.decode(codec.encode(detached)))
    }

    @Test fun transitionsKeepEveryPanelExactlyOnce() {
        var layout = checked(defaultDockLayout(panels))
        val group = layout.tabs().first().group
        layout = checked(movePanel(layout, p("game"), DockTarget.Tab(group, 0)))
        layout = checked(minimizePanel(layout, p("game")))
        assertTrue(p("game") in layout.minimized)
        layout = checked(hidePanel(layout, p("game")))
        assertTrue(p("game") in layout.hidden)
        layout = checked(restorePanel(layout, p("game")))
        assertEquals(layout, restorePanel(layout, p("game")))
        assertEquals(group, layout.tabs().single { p("game") in it.panels }.group)
    }
    @Test fun reorderKeepsActiveAndInvalidMovesAreAtomic() {
        val layout = defaultDockLayout(panels)
        val center = layout.tabs().single { p("scene") in it.panels }
        val moved = checked(movePanel(layout, p("game"), DockTarget.Tab(center.group, 0)))
        assertEquals(center.active, moved.tabs().single { it.group == center.group }.active)
        assertEquals(p("game"), moved.tabs().single { it.group == center.group }.panels.first())
        assertEquals(layout, movePanel(layout, p("scene"), DockTarget.Tab(GroupId("missing"), 0)))
        assertEquals(layout, movePanel(layout, p("missing"), DockTarget.Tab(center.group, 0)))
        assertEquals(layout, movePanel(layout, p("game"), DockTarget.Tab(center.group, -1)))
        assertEquals(layout, moveGroup(layout, center.group, DockTarget.Split(center.group, DockEdge.Left)))
        // Drag cancellation discards a proposal: the immutable source remains unchanged.
        val snapshot = defaultDockLayout(panels)
        movePanel(layout, p("scene"), DockTarget.Detached(LogicalBounds(0f, 0f, 400f, 300f)))
        assertEquals(snapshot, layout)
    }
    @Test fun singlePanelWindowsHideTheirDockHeader() {
        var layout = checked(defaultDockLayout(panels))
        val bounds = LogicalBounds(0f, 0f, 400f, 300f)
        assertFalse(layout.isSinglePanelWindow(layout.mainHost))
        layout = checked(movePanel(layout, p("inspector"), DockTarget.Detached(bounds)))
        val detached = layout.hosts.single { it.id != layout.mainHost }
        assertTrue(layout.isSinglePanelWindow(detached.id))
        // A second panel in the same window brings the header back.
        val group = detached.root!!.tabs().single().group
        layout = checked(movePanel(layout, p("assets"), DockTarget.Tab(group, 1)))
        assertFalse(layout.isSinglePanelWindow(detached.id))
        // The main window keeps its header even when it holds a single panel.
        val solo = DockLayout(layout.mainHost, listOf(DockHost(layout.mainHost, DockNode.Tabs(GroupId("only"), listOf(p("scene")), p("scene")), bounds)))
        assertFalse(solo.isSinglePanelWindow(solo.mainHost))
    }
    @Test fun lastTabCollapsesBranchesAndEmptyToolHosts() {
        var layout = defaultDockLayout(panels)
        val hierarchy = layout.tabs().single { p("hierarchy") in it.panels }.group
        layout = checked(movePanel(layout, p("hierarchy"), DockTarget.Detached(LogicalBounds(0f, 0f, 400f, 300f))))
        assertFalse(layout.hosts.single { it.id == layout.mainHost }.root!!.tabs().any { it.group == hierarchy })
        assertEquals(2, layout.hosts.size)
        layout = checked(hidePanel(layout, p("hierarchy")))
        assertEquals(1, layout.hosts.size)
        layout = checked(restorePanel(layout, p("hierarchy")))
        assertEquals(2, layout.hosts.size)
        ids.forEach { layout = checked(hidePanel(layout, it)) }
        assertEquals(1, layout.hosts.size)
        assertNull(layout.hosts.single().root)
    }
    @Test fun wholeGroupPreservesOrderActiveAndIdentityOnDetach() {
        val layout = defaultDockLayout(panels)
        val source = layout.tabs().single { p("scene") in it.panels }
        val detached = checked(moveGroup(layout, source.group, DockTarget.Detached(LogicalBounds(-400f, 0f, 400f, 300f))))
        assertEquals(source, detached.tabs().single { it.group == source.group })
        val target = detached.tabs().first { it.group != source.group }
        val merged = checked(moveGroup(detached, source.group, DockTarget.Tab(target.group, 1)))
        val result = merged.tabs().single { it.group == target.group }
        assertEquals(source.panels, result.panels.subList(1, 1 + source.panels.size))
        assertEquals(source.active, result.active)
    }
    @Test fun restoreUsesFallbackWhenGroupDisappearedAndFutureRegistrationWorks() {
        var layout = defaultDockLayout(panels)
        layout = hidePanel(layout, p("scene"))
        layout = hidePanel(layout, p("game"))
        layout = hidePanel(layout, p("entity"))
        layout = checked(restorePanel(layout, p("scene")))
        assertTrue(layout.tabs().any { p("scene") in it.panels })
        assertTrue(layout.tabs().any { p("future") in it.panels })
        val unavailable = panels.map { if (it.id == p("future")) it.copy(available = false) else it }
        assertTrue(p("future") in defaultDockLayout(unavailable).hidden)
    }
    @Test fun validationRejectsDuplicatePanelGroupHostAndBadActive() {
        val layout = defaultDockLayout(panels)
        val first = layout.tabs().first()
        val invalid = layout.copy(hosts = layout.hosts + layout.hosts.first().copy(root = first.copy(panels = first.panels + first.panels.first(), active = p("unknown"))))
        assertTrue(validateLayout(invalid, ids).size >= 3)
    }
    @Test fun minimizingLastToolTabRetainsHostAndRestoreStrip() {
        var layout = movePanel(defaultDockLayout(panels), p("game"), DockTarget.Detached(LogicalBounds(0f, 0f, 400f, 300f)))
        val host = layout.hosts.last().id
        layout = checked(minimizePanel(layout, p("game")))
        assertEquals(host, layout.hosts.last().id)
        assertNull(layout.hosts.last().root)
        layout = checked(hidePanel(layout, p("game")))
        assertFalse(layout.hosts.any { it.id == host })
    }
    @Test fun activeRemovalChoosesAdjacentTabAndSplitMoveRetainsAllPanels() {
        var layout = defaultDockLayout(panels)
        val center = layout.tabs().single { p("scene") in it.panels }
        layout = checked(hidePanel(layout, p("scene")))
        assertEquals(p("game"), layout.tabs().single { it.group == center.group }.active)
        val target = layout.tabs().first { it.group != center.group }.group
        layout = checked(movePanel(layout, p("future"), DockTarget.Split(target, DockEdge.Bottom)))
        assertTrue(layout.tabs().any { it.panels == listOf(p("future")) })
    }
    @Test fun mixedOperationsKeepInvariantsAcrossStaleAndValidTargets() {
        val random = kotlin.random.Random(2671)
        var layout = defaultDockLayout(panels)
        repeat(500) {
            val panel = ids.elementAt(random.nextInt(ids.size))
            val group = layout.tabs().randomOrNull(random)?.group ?: GroupId("stale")
            layout = checked(when (random.nextInt(7)) {
                0 -> hidePanel(layout, panel)
                1 -> minimizePanel(layout, panel)
                2 -> restorePanel(layout, panel)
                3 -> movePanel(layout, panel, DockTarget.Tab(group, 0))
                4 -> movePanel(layout, panel, DockTarget.Split(group, DockEdge.entries.random(random)))
                5 -> moveGroup(layout, group, DockTarget.Detached(LogicalBounds(-500f, 30f, 500f, 400f)))
                else -> activatePanel(layout, panel)
            })
        }
    }
}
