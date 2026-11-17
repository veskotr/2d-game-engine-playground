package sle.editor.workspace

import java.awt.GraphicsEnvironment
import java.awt.Toolkit

internal fun workspaceDisplays(): List<LogicalBounds> = GraphicsEnvironment.getLocalGraphicsEnvironment().screenDevices.map { device ->
    val gc=device.defaultConfiguration;val b=gc.bounds;val i=Toolkit.getDefaultToolkit().getScreenInsets(gc)
    LogicalBounds((b.x+i.left).toFloat(),(b.y+i.top).toFloat(),(b.width-i.left-i.right).toFloat(),(b.height-i.top-i.bottom).toFloat())
}

/** AWT bounds are already logical coordinates. Do not multiply saved geometry by display DPI. */
fun recoverLayout(layout: DockLayout, displays: List<LogicalBounds>, panels: List<PanelDescriptor>, scale: Float = 1f): DockLayout {
    val registry=PanelRegistry(panels)
    val areas=displays.filter { it.isValid() }
    require(areas.isNotEmpty()) { "No usable displays" }
    // Validate shape iteratively before calling any recursive model/sizing helpers.
    val pending=java.util.ArrayDeque<Pair<DockNode,Int>>()
    layout.hosts.forEach { it.root?.let { node -> pending.add(node to 1) } }
    var nodes=0
    var bounded=layout.hosts.size in 1..33
    while(bounded && pending.isNotEmpty()) {
        val (node,depth)=pending.removeLast()
        if(++nodes>256 || depth>32) { bounded=false;break }
        if(node is DockNode.Split) { pending.add(node.first to depth+1);pending.add(node.second to depth+1) }
    }
    val valid=bounded && validateLayout(layout,registry.byId.keys).isEmpty()
    var next=if(valid) layout else defaultDockLayout(panels)
    fun workArea(bounds: LogicalBounds): LogicalBounds = areas.maxBy { area ->
        val width=(minOf(area.x+area.width,bounds.x+bounds.width)-maxOf(area.x,bounds.x)).coerceAtLeast(0f)
        val height=(minOf(area.y+area.height,bounds.y+bounds.height)-maxOf(area.y,bounds.y)).coerceAtLeast(0f)
        width*height
    }
    for(host in next.hosts) next=fitHostToWorkArea(next,host.id,workArea(host.bounds),registry,scale=scale)
    // Hidden hosts may no longer exist. Their remembered restore bounds need the
    // same recovery before a later Show action constructs a native window.
    fun placements(values: Map<PanelId,PanelPlacement>) = values.mapValues { (panel,p) ->
        val host=DockHost(p.host,DockNode.Tabs(p.group,listOf(panel),panel),p.bounds)
        val fitted=fitHostToWorkArea(DockLayout(p.host,listOf(host)),p.host,workArea(p.bounds),registry,scale=scale)
        p.copy(bounds=fitted.hosts.single().bounds)
    }
    return next.copy(hidden=placements(next.hidden),minimized=placements(next.minimized))
}
