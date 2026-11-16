package sle.editor.workspace

import kotlin.test.*
import org.junit.Test

class WorkspaceDisplayRecoveryTest {
    private val panels=standardPanels()
    private val base=defaultDockLayout(panels)
    @Test fun preservesNegativeConnectedMonitorAndLogicalDpiGeometry() {
        val bounds=LogicalBounds(-1100f,40f,900f,700f)
        val layout=base.copy(hosts=base.hosts.map { it.copy(bounds=bounds) })
        val recovered=recoverLayout(layout,listOf(LogicalBounds(-1280f,0f,1280f,900f)),panels)
        assertEquals(bounds,recovered.hosts.single().bounds)
    }
    @Test fun removedMonitorAndSmallDisplayRecoverAccessibleTabs() {
        val layout=movePanel(base,PanelId("game"),DockTarget.Detached(LogicalBounds(-4000f,0f,1000f,900f)))
        val area=LogicalBounds(0f,0f,300f,200f)
        val recovered=recoverLayout(layout,listOf(area),panels)
        assertTrue(validateLayout(recovered,panels.map { it.id }.toSet()).isEmpty())
        assertTrue(recovered.hosts.all { it.bounds==area && it.root !is DockNode.Split })
    }
    @Test fun invalidGeometryAndRatiosUseDefaults() {
        val invalid=base.copy(hosts=base.hosts.map { it.copy(bounds=it.bounds.copy(x=Float.NaN)) })
        assertTrue(recoverLayout(invalid,listOf(LogicalBounds(0f,0f,1000f,700f)),panels).hosts.all { it.bounds.isValid() })
    }
    @Test fun rejectsDeepInMemoryTreesBeforeRecursiveValidation() {
        var node: DockNode=DockNode.Tabs(GroupId("base"),listOf(PanelId("scene")),PanelId("scene"))
        repeat(10000) { node=DockNode.Split(DockAxis.Horizontal,.5f,node,DockNode.Tabs(GroupId("g$it"),listOf(PanelId("p$it")),PanelId("p$it"))) }
        val layout=base.copy(hosts=listOf(base.hosts.single().copy(root=node)))
        val recovered=recoverLayout(layout,listOf(LogicalBounds(0f,0f,1200f,800f)),panels)
        assertTrue(validateLayout(recovered,panels.map { it.id }.toSet()).isEmpty())
    }
    @Test fun dpiScaleAndInvalidSplitRecoverWithoutLosingPanels() {
        val area=LogicalBounds(0f,0f,1000f,700f)
        val scaled=recoverLayout(base,listOf(area),panels,scale=2f)
        assertTrue(scaled.hosts.single().root is DockNode.Tabs)
        val root=base.hosts.single().root as DockNode.Split
        val invalid=base.copy(hosts=listOf(base.hosts.single().copy(root=root.copy(ratio=Float.NaN))))
        assertTrue(validateLayout(recoverLayout(invalid,listOf(area),panels),panels.map { it.id }.toSet()).isEmpty())
    }
    @Test fun hiddenPanelRestoresInsideConnectedDisplayAfterMonitorRemoval() {
        val game=PanelId("game")
        val layout=hidePanel(movePanel(base,game,DockTarget.Detached(LogicalBounds(-5000f,0f,900f,700f))),game)
        val area=LogicalBounds(0f,0f,800f,600f)
        val recovered=recoverLayout(layout,listOf(area),panels)
        val restored=restorePanel(recovered,game)
        val host=restored.hosts.single { it.root?.tabs()?.any { t -> game in t.panels }==true }
        assertTrue(host.bounds.x>=area.x && host.bounds.y>=area.y && host.bounds.width<=area.width && host.bounds.height<=area.height)
    }
}
