package sle.editor.workspace

private fun DockNode.mapGroups(transform: (DockNode.Tabs) -> DockNode?): DockNode? = when (this) {
    is DockNode.Tabs -> transform(this)
    is DockNode.Split -> {
        val a = first.mapGroups(transform)
        val b = second.mapGroups(transform)
        if (a == null) b else if (b == null) a else copy(first = a, second = b)
    }
}

private fun DockLayout.mapGroups(transform: (DockNode.Tabs) -> DockNode?): DockLayout = copy(
    hosts = hosts.map { it.copy(root = it.root?.mapGroups(transform)) }
        .filter { it.id == mainHost || it.root != null || minimized.values.any { placement -> placement.host == it.id } },
)

private fun DockLayout.placement(panel: PanelId): PanelPlacement? {
    hosts.forEach { host -> host.root?.tabs()?.forEach { tabs ->
        val index = tabs.panels.indexOf(panel)
        if (index >= 0) return PanelPlacement(host.id, tabs.group, index, host.bounds)
    } }
    return hidden[panel] ?: minimized[panel]
}

private fun DockLayout.remove(panels: Set<PanelId>): DockLayout = mapGroups { tabs ->
    val remaining = tabs.panels.filterNot { it in panels }
    when {
        remaining.isEmpty() -> null
        tabs.active in remaining -> tabs.copy(panels = remaining)
        else -> tabs.copy(panels = remaining, active = remaining[minOf(tabs.panels.indexOf(tabs.active), remaining.lastIndex)])
    }
}.let { removed ->
    val minimized = removed.minimized - panels
    removed.copy(hidden = removed.hidden - panels, minimized = minimized,
        hosts = removed.hosts.filter { host -> host.id == mainHost || host.root != null || minimized.values.any { it.host == host.id } })
}

private fun DockLayout.freshGroup(): GroupId {
    val used = tabs().map { it.group }.toSet() + hidden.values.map { it.group } + minimized.values.map { it.group }
    return generateSequence(1) { it + 1 }.map { GroupId("group-$it") }.first { it !in used }
}
private fun DockLayout.freshHost(): HostId {
    val used = hosts.map { it.id }.toSet() + hidden.values.map { it.host } + minimized.values.map { it.host }
    return generateSequence(1) { it + 1 }.map { HostId("host-$it") }.first { it !in used }
}

private fun DockLayout.validTarget(target: DockTarget, moving: Set<PanelId>): Boolean = when (target) {
    is DockTarget.Tab -> tabs().singleOrNull { it.group == target.group }?.let {
        target.index in 0..it.panels.count { panel -> panel !in moving }
    } ?: false
    is DockTarget.Split -> tabs().singleOrNull { it.group == target.group }?.panels?.any { it !in moving } == true
    is DockTarget.Detached -> target.bounds.isValid()
}

private fun DockLayout.insert(source: DockNode.Tabs, target: DockTarget, activate: Boolean): DockLayout = when (target) {
    is DockTarget.Tab -> mapGroups { tabs ->
        if (tabs.group != target.group) tabs else {
            val panels = tabs.panels.toMutableList().apply { addAll(target.index, source.panels) }
            tabs.copy(panels = panels, active = if (activate) source.active else tabs.active)
        }
    }
    is DockTarget.Split -> mapGroups { tabs ->
        if (tabs.group != target.group) tabs else {
            val before = target.edge == DockEdge.Left || target.edge == DockEdge.Top
            DockNode.Split(
                if (target.edge == DockEdge.Left || target.edge == DockEdge.Right) DockAxis.Horizontal else DockAxis.Vertical,
                .5f, if (before) source else tabs, if (before) tabs else source,
            )
        }
    }
    is DockTarget.Detached -> copy(hosts = hosts + DockHost(freshHost(), source, target.bounds))
}

/** Operations are proposals: callers commit the returned value; cancellation commits nothing. */
fun movePanel(layout: DockLayout, panel: PanelId, target: DockTarget): DockLayout {
    val placement = layout.placement(panel) ?: return layout
    if (!layout.validTarget(target, setOf(panel))) return layout
    val oldGroup = layout.tabs().singleOrNull { it.group == placement.group && panel in it.panels }
    val sameGroup = target is DockTarget.Tab && target.group == oldGroup?.group
    // Same-group reorder must retain identity even when this is its only tab.
    if (sameGroup) return layout.mapGroups { tabs ->
        if (tabs.group != oldGroup.group) tabs else tabs.copy(panels = tabs.panels.filterNot { it == panel }
            .toMutableList().apply { add(target.index, panel) })
    }
    val source = DockNode.Tabs(if (oldGroup?.panels?.size == 1) oldGroup.group else layout.freshGroup(), listOf(panel), panel)
    return layout.remove(setOf(panel)).insert(source, target, activate = true)
}

fun moveGroup(layout: DockLayout, group: GroupId, target: DockTarget): DockLayout {
    val source = layout.tabs().singleOrNull { it.group == group } ?: return layout
    if (target is DockTarget.Tab && target.group == group) return layout
    if (!layout.validTarget(target, source.panels.toSet())) return layout
    return layout.remove(source.panels.toSet()).insert(source, target, activate = true)
}

fun hidePanel(layout: DockLayout, panel: PanelId): DockLayout {
    if (panel in layout.hidden) return layout
    val placement = layout.placement(panel) ?: return layout
    return layout.remove(setOf(panel)).let { it.copy(hidden = it.hidden + (panel to placement)) }
}

fun minimizePanel(layout: DockLayout, panel: PanelId): DockLayout {
    if (panel in layout.minimized || panel in layout.hidden) return layout
    val placement = layout.placement(panel) ?: return layout
    val removed = layout.remove(setOf(panel))
    val hosts = if (removed.hosts.any { it.id == placement.host }) removed.hosts else
        removed.hosts + layout.hosts.single { it.id == placement.host }.copy(root = null)
    return removed.copy(hosts = hosts, minimized = removed.minimized + (panel to placement))
}

fun restorePanel(layout: DockLayout, panel: PanelId): DockLayout {
    val placement = layout.hidden[panel] ?: layout.minimized[panel] ?: return layout
    val destination = layout.tabs().singleOrNull { it.group == placement.group }
    if (destination != null) return movePanel(layout, panel, DockTarget.Tab(destination.group, placement.index.coerceIn(0, destination.panels.size)))
    val cleared = layout.remove(setOf(panel))
    val host = cleared.hosts.singleOrNull { it.id == placement.host }
    val group = if (cleared.tabs().none { it.group == placement.group }) placement.group else cleared.freshGroup()
    val source = DockNode.Tabs(group, listOf(panel), panel)
    if (host == null) return cleared.copy(hosts = cleared.hosts + DockHost(placement.host, source, placement.bounds))
    return cleared.copy(hosts = cleared.hosts.map {
        if (it.id != host.id) it else it.copy(root = it.root?.let { root -> DockNode.Split(DockAxis.Horizontal, .5f, root, source) } ?: source)
    })
}

fun activatePanel(layout: DockLayout, panel: PanelId): DockLayout = layout.mapGroups {
    if (panel in it.panels) it.copy(active = panel) else it
}
