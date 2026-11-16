package sle.editor.workspace

import sle.editor.ui.EditorMenuEntry

internal fun windowsMenu(manager: WorkspaceWindowManager): Pair<String, List<EditorMenuEntry>> {
    val layout = manager.layout
    val detached = DockTarget.Detached(LogicalBounds(100f, 100f, 900f, 650f))
    return "Windows" to manager.registry.panels.map { panel ->
        val host = manager.hostFor(panel.id)
        val group = layout.tabs().firstOrNull { panel.id in it.panels }
        val state = when {
            panel.id in layout.hidden -> "Hidden"
            panel.id in layout.minimized -> "Minimized"
            group?.active == panel.id -> "Active"
            else -> "Open"
        }
        EditorMenuEntry("${panel.title} ($state)", panel.available, children = listOf(
            EditorMenuEntry("Show / Focus") { manager.focusPanel(panel.id) },
            EditorMenuEntry("Minimize", host != null) { manager.minimizePanel(panel.id) },
            EditorMenuEntry("Hide", host != null) { manager.hidePanel(panel.id) },
            EditorMenuEntry("Detach") { manager.movePanel(panel.id, detached) },
            EditorMenuEntry("Move to", children = layout.tabs().map { target ->
                EditorMenuEntry(target.panels.joinToString(" / ") { manager.registry.byId.getValue(it).title }, children = listOf(
                    EditorMenuEntry("As tab") { manager.movePanel(panel.id, DockTarget.Tab(target.group, target.panels.count { it != panel.id })) },
                ) + DockEdge.entries.map { edge -> EditorMenuEntry(edge.name) { manager.movePanel(panel.id, DockTarget.Split(target.group, edge)) } })
            }),
            EditorMenuEntry("Detach group", group != null) { group?.let { manager.moveGroup(it.group, detached) } },
            EditorMenuEntry("Move group to", group != null, children = layout.tabs().filter { it.group != group?.group }.map { target ->
                EditorMenuEntry(target.panels.joinToString(" / ") { manager.registry.byId.getValue(it).title }) {
                    group?.let { manager.moveGroup(it.group, DockTarget.Tab(target.group, target.panels.size)) }
                }
            }),
        ))
    } + listOf(
        EditorMenuEntry("Focus Host", children = layout.hosts.map { host ->
            EditorMenuEntry(if (host.id == layout.mainHost) "Main" else host.root?.tabs()?.flatMap { it.panels }?.joinToString(" / ") { manager.registry.byId.getValue(it).title } ?: host.id.value) { manager.focusHost(host.id) }
        }),
        EditorMenuEntry("Restore Default Layout", action = manager::resetLayout),
    )
}
