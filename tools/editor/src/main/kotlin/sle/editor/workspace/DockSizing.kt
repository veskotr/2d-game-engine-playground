package sle.editor.workspace

import kotlin.math.max

fun nodeMinimum(node: DockNode, registry: PanelRegistry, chrome: DockChrome = DockChrome(), scale: Float = 1f): PanelMinimum {
    require(scale.isFinite() && scale > 0)
    return when (node) {
        is DockNode.Tabs -> {
            val sizes = node.panels.map { registry.byId.getValue(it).minimum }
            PanelMinimum((sizes.maxOfOrNull { it.width } ?: 0f) * scale,
                ((sizes.maxOfOrNull { it.height } ?: 0f) + chrome.tabHeight) * scale)
        }
        is DockNode.Split -> {
            val a = nodeMinimum(node.first, registry, chrome, scale)
            val b = nodeMinimum(node.second, registry, chrome, scale)
            if (node.axis == DockAxis.Horizontal) PanelMinimum(a.width + b.width + chrome.splitter * scale, max(a.height, b.height))
            else PanelMinimum(max(a.width, b.width), a.height + b.height + chrome.splitter * scale)
        }
    }
}

/** Insets are logical native decorations; UI scale applies to content/chrome, not OS insets. */
fun hostMinimum(host: DockHost, registry: PanelRegistry, chrome: DockChrome = DockChrome(), hasMinimized: Boolean = false,
    insets: PanelMinimum = PanelMinimum(0f, 0f), scale: Float = 1f): PanelMinimum {
    require(scale.isFinite() && scale > 0)
    val size = host.root?.let { nodeMinimum(it, registry, chrome, scale) } ?: PanelMinimum(0f, 0f)
    return PanelMinimum(size.width + insets.width, size.height + insets.height + if (hasMinimized) chrome.restoreStrip * scale else 0f)
}

/** Ratio divides the span remaining after the splitter; .5 signals an impossible split. */
fun clampSplitRatio(ratio: Float, span: Float, firstMinimum: Float, secondMinimum: Float, splitter: Float = 6f): Float {
    require(listOf(span, firstMinimum, secondMinimum, splitter).all { it.isFinite() && it >= 0 })
    val available = span - splitter
    if (available <= 0 || firstMinimum + secondMinimum > available) return .5f
    val desired = if (ratio.isFinite()) ratio else .5f
    val lower = max(Float.MIN_VALUE, firstMinimum / available)
    val upper = minOf(Math.nextDown(1f), 1f - secondMinimum / available)
    // A zero-size sibling cannot make a ratio of exactly 0/1 a valid split.
    if (lower > upper) return .5f
    return desired.coerceIn(lower, upper)
}

/** Collapse impossible splits into one tab group, then constrain bounds to the work area.
 * A display smaller than a single panel still wins over its minimum: the shell must scroll content.
 */
fun fitHostToWorkArea(layout: DockLayout, host: HostId, workArea: LogicalBounds, registry: PanelRegistry,
    chrome: DockChrome = DockChrome(), insets: PanelMinimum = PanelMinimum(0f, 0f), scale: Float = 1f): DockLayout {
    if (!workArea.isValid()) return layout
    val original = layout.hosts.singleOrNull { it.id == host } ?: return layout
    val hasMinimized = layout.minimized.values.any { it.host == host }
    val minimum = hostMinimum(original, registry, chrome, hasMinimized, insets, scale)
    val root = if (minimum.width > workArea.width || minimum.height > workArea.height) {
        original.root?.tabs()?.let { tabs ->
            tabs.firstOrNull()?.let { first -> first.copy(panels = tabs.flatMap { it.panels }) }
        }
    } else original.root
    val preferred = original.copy(root = root)
    val min = hostMinimum(preferred, registry, chrome, hasMinimized, insets, scale)
    val width = max(original.bounds.width, min.width).coerceAtMost(workArea.width)
    val height = max(original.bounds.height, min.height).coerceAtMost(workArea.height)
    val bounds = LogicalBounds(original.bounds.x.coerceIn(workArea.x, workArea.x + workArea.width - width),
        original.bounds.y.coerceIn(workArea.y, workArea.y + workArea.height - height), width, height)
    return layout.copy(hosts = layout.hosts.map { if (it.id == host) preferred.copy(bounds = bounds) else it })
}

/** A path uses false for first, true for second. Missing/stale paths leave the tree unchanged. */
fun resizeDockSplit(layout: DockLayout, host: HostId, path: List<Boolean>, ratio: Float,
    span: Float, registry: PanelRegistry, scale: Float = 1f, chrome: DockChrome = DockChrome()): DockLayout {
    fun resize(node: DockNode, depth: Int): DockNode {
        if(node !is DockNode.Split) return node
        if(depth<path.size) return if(path[depth]) node.copy(second=resize(node.second,depth+1))
            else node.copy(first=resize(node.first,depth+1))
        val a=nodeMinimum(node.first,registry,chrome,scale);val b=nodeMinimum(node.second,registry,chrome,scale)
        return node.copy(ratio=clampSplitRatio(ratio,span,
            if(node.axis==DockAxis.Horizontal) a.width else a.height,
            if(node.axis==DockAxis.Horizontal) b.width else b.height,chrome.splitter*scale))
    }
    return layout.copy(hosts=layout.hosts.map { if(it.id==host) it.copy(root=it.root?.let { root -> resize(root,0) }) else it })
}
