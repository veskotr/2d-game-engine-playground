package sle.editor.workspace

data class DockDropArea(val host: HostId, val group: GroupId, val bounds: LogicalBounds,
    val tabs: List<Pair<PanelId, LogicalBounds>>, val panelOrder: List<PanelId> = tabs.map { it.first })
internal fun LogicalBounds.contains(point: DockPoint) = point.x>=x && point.y>=y && point.x<x+width && point.y<y+height
/** Areas are front-to-back. The caller distinguishes outside-host detach from invalid chrome. */
fun dockDropTarget(point: DockPoint, areas: List<DockDropArea>, moving: Set<PanelId>, hosts: List<Pair<HostId, LogicalBounds>> = areas.map { it.host to it.bounds }): DockTarget? {
    val host=hosts.firstOrNull { it.second.contains(point) }?.first ?: return null
    val area=areas.firstOrNull { it.host==host && it.bounds.contains(point) } ?: return null
    val tabs=area.tabs
    if(tabs.any { it.second.contains(point) }) {
        val anchor=tabs.firstOrNull { point.x < it.second.x+it.second.width/2 }?.first
        val before=if(anchor!=null) area.panelOrder.indexOf(anchor) else
            tabs.lastOrNull()?.first?.let { area.panelOrder.indexOf(it)+1 } ?: area.panelOrder.size
        return DockTarget.Tab(area.group,area.panelOrder.take(before.coerceAtLeast(0)).count { it !in moving })
    }
    val b=area.bounds
    val edge=when {
        point.x < b.x+minOf(48f,b.width*.2f) -> DockEdge.Left
        point.x > b.x+b.width-minOf(48f,b.width*.2f) -> DockEdge.Right
        point.y < b.y+minOf(48f,b.height*.2f) -> DockEdge.Top
        point.y > b.y+b.height-minOf(48f,b.height*.2f) -> DockEdge.Bottom
        else -> null
    }
    return edge?.let { DockTarget.Split(area.group,it) } ?: DockTarget.Tab(area.group,area.panelOrder.count { it !in moving })
}

/** ScrollableTabRow lays out offscreen tabs too; only its clipped strip can initiate a drag. */
internal fun clipDockTab(tab: androidx.compose.ui.geometry.Rect, strip: androidx.compose.ui.geometry.Rect?): androidx.compose.ui.geometry.Rect? {
    strip ?: return null
    val clipped=tab.intersect(strip)
    return clipped.takeIf { it.width>0 && it.height>0 }
}
