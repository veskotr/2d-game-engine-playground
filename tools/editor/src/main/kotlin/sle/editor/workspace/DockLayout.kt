package sle.editor.workspace

enum class DockAxis { Horizontal, Vertical }
enum class DockEdge { Left, Right, Top, Bottom }

data class LogicalBounds(val x: Float, val y: Float, val width: Float, val height: Float) {
    fun isValid(): Boolean = listOf(x, y, width, height).all { it.isFinite() } && width > 0 && height > 0
}

sealed interface DockNode {
    data class Tabs(val group: GroupId, val panels: List<PanelId>, val active: PanelId) : DockNode
    data class Split(val axis: DockAxis, val ratio: Float, val first: DockNode, val second: DockNode) : DockNode
}

/** Bounds are logical screen coordinates; only the retained main host may have no root. */
data class DockHost(val id: HostId, val root: DockNode?, val bounds: LogicalBounds)
data class PanelPlacement(val host: HostId, val group: GroupId, val index: Int, val bounds: LogicalBounds)
data class DockLayout(
    val mainHost: HostId,
    val hosts: List<DockHost>,
    val hidden: Map<PanelId, PanelPlacement> = emptyMap(),
    val minimized: Map<PanelId, PanelPlacement> = emptyMap(),
)

sealed interface DockTarget {
    /** Index in the destination list AFTER removing the source. */
    data class Tab(val group: GroupId, val index: Int) : DockTarget
    data class Split(val group: GroupId, val edge: DockEdge) : DockTarget
    data class Detached(val bounds: LogicalBounds) : DockTarget
}

internal fun DockNode.tabs(): List<DockNode.Tabs> = when (this) {
    is DockNode.Tabs -> listOf(this)
    is DockNode.Split -> first.tabs() + second.tabs()
}
fun DockLayout.tabs(): List<DockNode.Tabs> = hosts.flatMap { it.root?.tabs().orEmpty() }

/**
 * A separate window holding exactly one panel shows no dock header at all: the native
 * caption names it, minimizes and closes it (close hides the panel), and each panel exists
 * only once. Windows → Move to / Restore Default Layout re-dock it. The main window
 * always keeps its headers.
 */
fun DockLayout.isSinglePanelWindow(host: HostId): Boolean =
    host!=mainHost && (hosts.firstOrNull { it.id==host }?.root?.tabs()?.sumOf { it.panels.size } ?: 0)==1

fun defaultDockLayout(panels: List<PanelDescriptor>): DockLayout {
    PanelRegistry(panels)
    val bounds = LogicalBounds(0f, 0f, 1280f, 800f)
    val main = HostId("main")
    fun tabs(name: String, ids: List<PanelId>): DockNode? = ids.takeIf { it.isNotEmpty() }
        ?.let { DockNode.Tabs(GroupId(name), it, it.first()) }
    fun split(axis: DockAxis, ratio: Float, a: DockNode?, b: DockNode?): DockNode? =
        if (a == null) b else if (b == null) a else DockNode.Split(axis, ratio, a, b)
    val visible = panels.filter { it.available && !it.temporary }.map { it.id }
    fun named(id: String) = visible.filter { it.value == id }
    val center = visible.filter { it.value !in setOf("hierarchy", "inspector", "assets", "console", "luaProfiler") }
    val top = split(DockAxis.Horizontal, .2f, tabs("hierarchy", named("hierarchy")),
        split(DockAxis.Horizontal, .75f, tabs("center", center), tabs("inspector", named("inspector"))))
    // Default only: a compact asset strip (toolbar + a few rows). Saved layouts keep their ratios.
    val root = split(DockAxis.Vertical, .74f, top, tabs("assets", named("assets") + named("console") + named("luaProfiler")))
    // Unavailable and temporary panels start hidden; showing one restores it as a center tab.
    return DockLayout(main, listOf(DockHost(main, root, bounds)), hidden = panels.filter { !it.available || it.temporary }
        .associate { it.id to PanelPlacement(main, GroupId("center"), center.size, bounds) })
}

fun validateLayout(layout: DockLayout, panels: Set<PanelId>): List<String> = buildList {
    if (layout.hosts.count { it.id == layout.mainHost } != 1) add("Main host must occur once")
    if (layout.hosts.map { it.id }.distinct().size != layout.hosts.size) add("Duplicate host ID")
    val groups = mutableSetOf<GroupId>()
    val occurrences = mutableListOf<PanelId>()
    fun visit(node: DockNode) {
        when (node) {
            is DockNode.Tabs -> {
                if (!groups.add(node.group)) add("Duplicate group ID: ${node.group.value}")
                if (node.panels.isEmpty()) add("Empty tab group: ${node.group.value}")
                if (node.active !in node.panels) add("Invalid active tab: ${node.group.value}")
                occurrences.addAll(node.panels)
            }
            is DockNode.Split -> {
                if (!node.ratio.isFinite() || node.ratio <= 0 || node.ratio >= 1) add("Invalid split ratio")
                visit(node.first); visit(node.second)
            }
        }
    }
    layout.hosts.forEach {
        if (!it.bounds.isValid()) add("Invalid host bounds: ${it.id.value}")
        if (it.root == null && it.id != layout.mainHost && layout.minimized.values.none { placement -> placement.host == it.id }) add("Empty auxiliary host")
        it.root?.let(::visit)
    }
    listOf(layout.hidden, layout.minimized).forEach { placements ->
        placements.forEach { (panel, placement) ->
            occurrences.add(panel)
            if (placement.index < 0 || !placement.bounds.isValid()) add("Invalid placement: ${panel.value}")
            if (placements === layout.minimized && layout.hosts.none { it.id == placement.host }) add("Missing minimized host: ${panel.value}")
        }
    }
    occurrences.groupingBy { it }.eachCount().forEach { (panel, count) ->
        if (count != 1) add("Duplicate panel: ${panel.value}")
        if (panel !in panels) add("Unknown panel: ${panel.value}")
    }
    (panels - occurrences.toSet()).forEach { add("Missing panel: ${it.value}") }
}
