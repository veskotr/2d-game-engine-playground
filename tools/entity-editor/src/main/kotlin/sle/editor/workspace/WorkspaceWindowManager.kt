package sle.editor.workspace

import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Rect
import java.awt.*
import java.awt.event.*
import javax.swing.SwingUtilities
import kotlin.math.ceil

/** One observable layout and one lifecycle. Native windows are replaceable bindings. */
internal class WorkspaceWindowManager(
    val registry: PanelRegistry = PanelRegistry(standardPanels()),
    private val beforeLayout: () -> Unit = {},
    private val onShutdown: () -> Unit = {},
    private val onActivate: (PanelId) -> Unit = {},
) : AutoCloseable {
    var layout by mutableStateOf(defaultDockLayout(registry.panels))
        private set
    var layoutNotice by mutableStateOf<String?>(null)
        private set
    var loading by mutableStateOf(false)
        private set
    private var store: WorkspaceLayoutStore? = null
    private var storageKey: WorkspaceLayoutKey? = null
    private var loadGeneration = 0L
    private val saveTimer = javax.swing.Timer(350) { persistNow() }.apply { isRepeats=false }
    private var topology: List<Pair<LogicalBounds,Double>> = emptyList()
    private val displayTimer = javax.swing.Timer(1000) {
        val current = workspaceDisplays().zip(GraphicsEnvironment.getLocalGraphicsEnvironment().screenDevices.map { it.defaultConfiguration.defaultTransform.scaleX })
        if(current!=topology) { topology=current; recoverDisplays(current.map { it.first }) }
        store?.lastFailure?.let { layoutNotice=it }
    }
    fun startPersistence(key: WorkspaceLayoutKey) {
        if(store==null) store=WorkspaceLayoutStore(WorkspaceLayoutCodec(registry))
        switchStorage(key)
        if(!GraphicsEnvironment.isHeadless()) displayTimer.start()
    }
    fun switchStorage(key: WorkspaceLayoutKey) {
        if(store==null || storageKey==key) return
        saveTimer.stop();persistNow()
        storageKey=key;loading=true
        val generation=++loadGeneration
        desktop.cancel();beforeLayout()
        store!!.load(key).thenAccept { result -> SwingUtilities.invokeLater {
            if(closed || generation!=loadGeneration) return@invokeLater
            val next=result.layout ?: defaultDockLayout(registry.panels)
            layoutNotice=result.notice
            layout=if(GraphicsEnvironment.isHeadless()) next else recoverLayout(next,workspaceDisplays(),registry.panels,uiScale)
            revision++;loading=false
            revealContext(null);applyWindowBounds();saveTimer.restart()
        } }
    }
    private fun changed() { if(!loading && store!=null) saveTimer.restart() }
    private fun persistNow() {
        if(loading) return
        storageKey?.let { store?.save(it,revision,layout) }
    }
    fun flushLayout() { saveTimer.stop();persistNow();store?.flush() }
    fun recoverDisplays(displays: List<LogicalBounds>) {
        if(closed || loading) return
        desktop.cancel();beforeLayout()
        commit(recoverLayout(layout,displays,registry.panels,uiScale))
        applyWindowBounds()
    }
    private fun applyWindowBounds() {
        for((id,window) in windows) layout.hosts.firstOrNull { it.id==id }?.let { host ->
            if(window is Frame) window.extendedState=Frame.NORMAL
            window.minimumSize=Dimension(0,0)
            val b=host.bounds;window.setBounds(b.x.toInt(),b.y.toInt(),b.width.toInt(),b.height.toInt())
        }
    }
    private var authoringPanel = PanelId("scene")
    private val windows = mutableMapOf<HostId, Window>()
    private val stacking = mutableListOf<HostId>()
    var revision = 0L
        private set
    private var uiScale = 1f
    private var chrome = DockChrome()
    fun updateChrome(next: DockChrome) {
        if(chrome==next) return
        chrome=next;desktop.cancel()
        SwingUtilities.invokeLater { if(!closed) enforceSizing() }
    }
    private val surfaces = mutableMapOf<HostId, Rect>()
    private val listeners = mutableMapOf<HostId, ComponentAdapter>()
    private val desktop by lazy { DockDesktopDrag(this) }
    internal val drag = DockDragController()
    private val measurements = mutableMapOf<Pair<HostId, GroupId>, GroupMeasurement>()
    private data class GroupMeasurement(var bounds: Rect? = null, var handle: Rect? = null, var strip: Rect? = null,
        val tabs: MutableMap<PanelId, Rect> = mutableMapOf())
    var closed by mutableStateOf(false)
        private set
    fun bind(id: HostId, window: Window) {
        windows[id] = window
        if(store!=null && !loading) layout.hosts.firstOrNull { it.id==id }?.bounds?.let { b ->
            if(window is Frame) window.extendedState=Frame.NORMAL
            window.setBounds(b.x.toInt(),b.y.toInt(),b.width.toInt(),b.height.toInt())
        }
        noteActivation(window)
        val listener=object: ComponentAdapter() {
            override fun componentMoved(e: ComponentEvent) { desktop.cancel(); recordBounds(id,window) }
            override fun componentResized(e: ComponentEvent) { recordBounds(id,window) }
        }
        listeners[id]=listener;window.addComponentListener(listener)
        desktop.install(); SwingUtilities.invokeLater { if(windows[id]===window) enforceSizing() }
    }
    fun unbind(id: HostId, window: Window) {
        if(windows[id]!==window) return
        desktop.cancel();listeners.remove(id)?.let(window::removeComponentListener)
        windows.remove(id);stacking.remove(id);surfaces.remove(id);measurements.keys.removeAll { it.first==id }
        if(windows.isEmpty()) desktop.close()
    }
    private fun recordBounds(id: HostId, window: Window) {
        if(closed || !window.isShowing || (window is Frame && window.extendedState and Frame.ICONIFIED!=0)) return
        val b=window.bounds
        val next=layout.copy(hosts=layout.hosts.map { if(it.id==id) it.copy(bounds=LogicalBounds(b.x.toFloat(),b.y.toFloat(),b.width.toFloat(),b.height.toFloat())) else it })
        if(next!=layout) { drag.cancel();layout=next;revision++;changed() }
    }
    internal fun boundWindows(): Map<HostId,Window> = windows.toMap()
    internal fun noteActivation(window: Window) {
        val host=windows.entries.firstOrNull { it.value===window }?.key ?: return
        stacking.remove(host);stacking.add(0,host)
    }
    internal fun visibleHostBounds(): List<Pair<HostId,LogicalBounds>> = stacking.mapNotNull { id ->
        windows[id]?.takeIf { it.isShowing && !(it is Frame && it.extendedState and Frame.ICONIFIED!=0) }?.let {
            id to LogicalBounds(it.x.toFloat(),it.y.toFloat(),it.width.toFloat(),it.height.toFloat())
        }
    }
    internal fun transform(host: HostId): DockScreenTransform? {
        val window=windows[host] ?: return null
        if(!window.isShowing) return null
        val pane=(window as? javax.swing.RootPaneContainer)?.contentPane ?: window
        val origin=pane.locationOnScreen;val scale=window.graphicsConfiguration.defaultTransform
        return DockScreenTransform(origin.x.toFloat(),origin.y.toFloat(),scale.scaleX.toFloat(),scale.scaleY.toFloat())
    }
    internal fun screenBounds(host: HostId, rect: Rect): LogicalBounds? = transform(host)?.let {
        val a=it.toScreen(DockPoint(rect.left,rect.top));val b=it.toScreen(DockPoint(rect.right,rect.bottom))
        LogicalBounds(a.x,a.y,b.x-a.x,b.y-a.y)
    }
    fun measureSurface(host: HostId, rect: Rect) {
        if(surfaces.put(host,rect)!=rect) SwingUtilities.invokeLater { if(!closed) enforceSizing() }
    }
    fun measureGroup(host: HostId, group: GroupId, rect: Rect) { measurements.getOrPut(host to group) { GroupMeasurement() }.bounds=rect }
    fun measureHandle(host: HostId, group: GroupId, rect: Rect) { measurements.getOrPut(host to group) { GroupMeasurement() }.handle=rect }
    fun measureStrip(host: HostId, group: GroupId, rect: Rect) { measurements.getOrPut(host to group) { GroupMeasurement() }.strip=rect }
    fun measureTab(host: HostId, group: GroupId, panel: PanelId, rect: Rect) { measurements.getOrPut(host to group) { GroupMeasurement() }.tabs[panel]=rect }
    fun forgetGroup(host: HostId, group: GroupId) { measurements.remove(host to group) }
    /** Header not drawn (single-panel window): no stale grip/tab hit areas may start a drag. */
    fun forgetHeader(host: HostId, group: GroupId) { measurements[host to group]?.apply { handle=null;strip=null;tabs.clear() } }
    internal fun dropAreas(): List<DockDropArea> = stacking.toList().flatMap { host ->
        val window=windows.getValue(host)
        if(!window.isShowing || (window is Frame && window.extendedState and Frame.ICONIFIED!=0)) emptyList() else
        layout.hosts.firstOrNull { it.id==host }?.root?.tabs().orEmpty().mapNotNull { group ->
            val m=measurements[host to group.group] ?: return@mapNotNull null
            val bounds=m.bounds?.let { screenBounds(host,it) } ?: return@mapNotNull null
            DockDropArea(host,group.group,bounds,group.panels.mapNotNull { id -> m.tabs[id]?.let { clipDockTab(it,m.strip) }?.let { screenBounds(host,it) }?.let { id to it } },group.panels)
        }
    }
    internal fun dragSource(host: HostId, point: DockPoint): DockDragSource? {
        val groups=layout.hosts.firstOrNull { it.id==host }?.root?.tabs().orEmpty()
        for(group in groups) {
            val m=measurements[host to group.group] ?: continue
            if(m.handle?.let { screenBounds(host,it)?.contains(point) }==true) return DockDragSource.Group(group.group)
            group.panels.forEach { panel -> if(m.tabs[panel]?.let { clipDockTab(it,m.strip) }?.let { screenBounds(host,it)?.contains(point) }==true) return DockDragSource.Panel(panel) }
        }
        return null
    }
    fun prepareInteraction() { beforeLayout() }
    internal fun commitDrop(target: DockTarget?) { commit(drag.drop(layout,revision,target)) }
    fun detachPanel(panel: PanelId) {
        val bounds=layout.hosts.firstOrNull { it.id==hostFor(panel) }?.bounds ?: return
        movePanel(panel,DockTarget.Detached(bounds.copy(x=bounds.x+40,y=bounds.y+40)))
    }
    fun updateScale(scale: Float) {
        if(scale==uiScale) return
        require(scale.isFinite() && scale>0);desktop.cancel();uiScale=scale
        SwingUtilities.invokeLater { if(!closed) enforceSizing() }
    }
    fun splitAt(host: HostId, path: List<Boolean>): DockNode.Split? {
        var node=layout.hosts.firstOrNull { it.id==host }?.root
        for(second in path) { val split=node as? DockNode.Split ?: return null;node=if(second) split.second else split.first }
        return node as? DockNode.Split
    }
    fun resizeSplit(host: HostId, path: List<Boolean>, ratio: Float, span: Float) =
        commit(resizeDockSplit(layout,host,path,ratio,span,registry,chrome=chrome))
    private fun enforceSizing() {
        if(closed) return
        var next=layout
        for((id,window) in windows) {
            if(!window.isShowing || (window is Frame && window.extendedState and Frame.ICONIFIED!=0)) continue
            val gc=window.graphicsConfiguration;val area=gc.bounds;val inset=Toolkit.getDefaultToolkit().getScreenInsets(gc)
            val work=LogicalBounds((area.x+inset.left).toFloat(),(area.y+inset.top).toFloat(),
                (area.width-inset.left-inset.right).toFloat(),(area.height-inset.top-inset.bottom).toFloat())
            val surface=surfaces[id]?.let { screenBounds(id,it) }
            val decorations=window.insets
            val overhead=PanelMinimum((window.width-(surface?.width ?: (window.width-decorations.left-decorations.right).toFloat())).coerceAtLeast(0f),
                (window.height-(surface?.height ?: (window.height-decorations.top-decorations.bottom).toFloat())).coerceAtLeast(0f))
            next=fitHostToWorkArea(next,id,work,registry,chrome,insets=overhead,scale=uiScale)
            val host=next.hosts.firstOrNull { it.id==id } ?: continue
            val minimum=hostMinimum(host,registry,chrome,hasMinimized=next.minimized.values.any { it.host==id },insets=overhead,scale=uiScale)
            window.minimumSize=Dimension(ceil(minOf(minimum.width,work.width)).toInt(),ceil(minOf(minimum.height,work.height)).toInt())
            if(window.bounds != Rectangle(host.bounds.x.toInt(),host.bounds.y.toInt(),host.bounds.width.toInt(),host.bounds.height.toInt()))
                window.setBounds(host.bounds.x.toInt(),host.bounds.y.toInt(),host.bounds.width.toInt(),host.bounds.height.toInt())
        }
        if(next!=layout) { drag.cancel();beforeLayout();layout=next;revision++;changed() }
    }
    fun hostFor(panel: PanelId): HostId? = layout.hosts.firstOrNull { host -> host.root?.tabs()?.any { panel in it.panels } == true }?.id
    fun focusHost(id: HostId) {
        windows[id]?.let { window ->
            if (window is Frame) window.extendedState = window.extendedState and Frame.ICONIFIED.inv()
            window.toFront(); window.requestFocus();noteActivation(window)
        }
    }
    private fun commit(next: DockLayout) {
        if (closed || next == layout) return
        check(validateLayout(next, registry.byId.keys).isEmpty())
        desktop.cancel(); beforeLayout(); layout = next; revision++;changed()
        if(windows.isNotEmpty()) SwingUtilities.invokeLater { enforceSizing() }
    }
    fun showPanel(panel: PanelId) {
        if (registry.byId[panel]?.available != true || closed) return
        commit(activatePanel(restorePanel(layout, panel), panel))
        if(panel.value in setOf("scene","game","entity")) authoringPanel=panel
        onActivate(panel)
        hostFor(panel)?.let(::focusHost)
    }
    fun focusPanel(panel: PanelId) = showPanel(panel)
    private fun revealContext(previousGroup: GroupId?) {
        val tabs=layout.tabs()
        val next=tabs.firstOrNull { it.group==previousGroup }?.active?.takeIf { it.value in setOf("scene","game","entity") }
            ?: tabs.firstOrNull { it.active.value in setOf("scene","game","entity") }?.active
        if(next!=null) { authoringPanel=next;onActivate(next) }
    }
    fun hidePanel(panel: PanelId) {
        val group=layout.tabs().firstOrNull { panel in it.panels }?.group
        commit(hidePanel(layout,panel))
        if(authoringPanel==panel) revealContext(group)
    }
    fun movePanel(panel: PanelId, target: DockTarget) = commit(movePanel(layout, panel, target))
    fun moveGroup(group: GroupId, target: DockTarget) = commit(moveGroup(layout, group, target))
    fun minimizePanel(panel: PanelId) {
        val group=layout.tabs().firstOrNull { panel in it.panels }?.group
        commit(minimizePanel(layout,panel))
        if(authoringPanel==panel) revealContext(group)
    }
    fun resetLayout() {
        commit(defaultDockLayout(registry.panels))
        revealContext(null)
    }
    fun closeHost(host: HostId) {
        if (host == layout.mainHost) return // Home exit always takes the confirmation path.
        val panels = layout.hosts.firstOrNull { it.id == host }?.root?.tabs()?.flatMap { it.panels }.orEmpty() +
            layout.minimized.filterValues { it.host == host }.keys
        commit(panels.fold(layout) { next, panel -> hidePanel(next, panel) })
        if(authoringPanel in panels) revealContext(null)
    }
    fun requestExit(confirm: () -> Boolean): Boolean {
        if (closed) return true
        if (!confirm()) return false
        close(); return true
    }
    override fun close() {
        if (closed) return
        saveTimer.stop();displayTimer.stop();persistNow();store?.close()
        desktop.close();drag.cancel();closed = true
        onShutdown()
    }
}
