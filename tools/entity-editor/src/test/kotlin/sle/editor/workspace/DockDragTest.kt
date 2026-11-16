package sle.editor.workspace

import kotlin.test.*

class DockDragTest {
    private val scene = PanelId("scene")
    private val inspector = PanelId("inspector")
    private val registry = PanelRegistry(standardPanels())
    private fun layout() = defaultDockLayout(registry.panels)

    @Test fun negativeScreenCoordinatesAndUnequalDpiUseTargetTransform() {
        val transform = DockScreenTransform(-1600f, -200f, 2f, 1.5f)
        assertEquals(DockPoint(100f, 60f), transform.toLocal(DockPoint(-1550f, -160f)))
        assertEquals(DockPoint(-1550f, -160f), transform.toScreen(DockPoint(100f, 60f)))
    }
    @Test fun dropCommitsOnceAndStaleRevisionPreservesSource() {
        val layout = layout()
        val target = layout.tabs().single { scene in it.panels }
        val controller = DockDragController()
        controller.begin(layout, 7, DockDragSource.Panel(inspector))
        val next = controller.drop(layout, 7, DockTarget.Tab(target.group, 1))
        assertEquals(listOf(scene, inspector, PanelId("game"), PanelId("entity")), next.tabs().single { it.group == target.group }.panels)
        assertEquals(next, controller.drop(next, 8, DockTarget.Detached(LogicalBounds(0f,0f,500f,400f))))
        controller.begin(layout, 7, DockDragSource.Panel(inspector))
        assertEquals(layout, controller.drop(layout, 8, DockTarget.Tab(target.group, 0)))
        assertFalse(controller.active)
    }
    @Test fun cancellationInvalidSelfAndGroupDetachKeepUniquePanelsAndActiveTab() {
        val layout = layout()
        val group = layout.tabs().single { scene in it.panels }
        val controller = DockDragController()
        controller.begin(layout, 1, DockDragSource.Group(group.group))
        controller.cancel()
        assertEquals(layout, controller.drop(layout, 1, DockTarget.Detached(LogicalBounds(0f,0f,500f,400f))))
        for(target in listOf(DockTarget.Split(group.group,DockEdge.Left), DockTarget.Tab(GroupId("missing"),0))) {
            controller.begin(layout, 1, DockDragSource.Group(group.group))
            assertEquals(layout, controller.drop(layout, 1, target))
        }
        controller.begin(layout, 1, DockDragSource.Group(group.group))
        val next=controller.drop(layout, 1, DockTarget.Detached(LogicalBounds(-800f,20f,500f,400f)))
        assertEquals(group, next.hosts.last().root)
        assertTrue(validateLayout(next,registry.byId.keys).isEmpty())
    }
    @Test fun sameGroupInsertionUsesPostRemovalIndexAndPreservesActiveTab() {
        val layout=layout(); val group=layout.tabs().single { scene in it.panels }
        val drag=DockDragController();drag.begin(layout,0,DockDragSource.Panel(scene))
        val next=drag.drop(layout,0,DockTarget.Tab(group.group,2))
        assertEquals(listOf(PanelId("game"),PanelId("entity"),scene),next.tabs().single { it.group==group.group }.panels)
        assertEquals(scene,next.tabs().single { it.group==group.group }.active)
    }
    @Test fun targetHitTestingSeparatesOutsideFromInvalidHostAndEdges() {
        val group=GroupId("target")
        val area=DockDropArea(HostId("target"),group,LogicalBounds(-400f,0f,400f,300f),emptyList())
        assertEquals(DockTarget.Split(group,DockEdge.Left),dockDropTarget(DockPoint(-395f,100f),listOf(area),emptySet()))
        assertEquals(DockTarget.Tab(group,0),dockDropTarget(DockPoint(-200f,150f),listOf(area),emptySet()))
        assertNull(dockDropTarget(DockPoint(100f,150f),listOf(area),emptySet()))
    }
    @Test fun minimizingAndRestoringUsesLayoutBoundaryAndKeepsHostAlive() {
        var boundaries=0
        val manager=WorkspaceWindowManager(beforeLayout={boundaries++})
        manager.movePanel(scene,DockTarget.Detached(LogicalBounds(0f,0f,600f,400f)))
        val host=manager.hostFor(scene)
        manager.minimizePanel(scene)
        assertEquals(host,manager.layout.minimized.getValue(scene).host)
        assertTrue(manager.layout.hosts.any { it.id==host })
        manager.showPanel(scene)
        assertEquals(host,manager.hostFor(scene))
        assertEquals(3,boundaries)
    }
    @Test fun foregroundHostChromeBlocksBackgroundDropTargets() {
        val front=HostId("front");val back=HostId("back");val group=GroupId("back")
        val area=DockDropArea(back,group,LogicalBounds(0f,0f,600f,500f),emptyList())
        val hosts=listOf(front to LogicalBounds(100f,100f,300f,300f),back to LogicalBounds(0f,0f,600f,500f))
        assertNull(dockDropTarget(DockPoint(200f,110f),listOf(area),emptySet(),hosts))
        assertEquals(DockTarget.Tab(group,0),dockDropTarget(DockPoint(300f,450f),listOf(area),emptySet(),hosts))
    }
    @Test fun scrolledTabInsertionCountsOffscreenTabsAndExcludesMovingSource() {
        val a=PanelId("a");val b=PanelId("b");val c=PanelId("c");val group=GroupId("scroll")
        val area=DockDropArea(HostId("main"),group,LogicalBounds(0f,0f,200f,300f),
            listOf(b to LogicalBounds(0f,0f,90f,32f),c to LogicalBounds(90f,0f,90f,32f)),listOf(a,b,c))
        assertEquals(DockTarget.Tab(group,1),dockDropTarget(DockPoint(10f,16f),listOf(area),emptySet()))
        assertEquals(DockTarget.Tab(group,0),dockDropTarget(DockPoint(10f,16f),listOf(area),setOf(a)))
        assertEquals(DockTarget.Tab(group,3),dockDropTarget(DockPoint(100f,150f),listOf(area),emptySet()))
    }
    @Test fun offscreenTabBoundsCannotStartDragThroughPanelControls() {
        val strip=androidx.compose.ui.geometry.Rect(20f,0f,120f,32f)
        assertNull(clipDockTab(androidx.compose.ui.geometry.Rect(130f,0f,220f,32f),strip))
        assertEquals(androidx.compose.ui.geometry.Rect(20f,0f,70f,32f),
            clipDockTab(androidx.compose.ui.geometry.Rect(-20f,0f,70f,32f),strip))
    }
}
