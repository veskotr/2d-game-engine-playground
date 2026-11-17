package sle.editor.workspace

import androidx.compose.runtime.Composable

/** Content bindings are shared by every host; there is exactly one binding per registered ID. */
internal class WorkspacePanels(private val content: Map<PanelId, @Composable () -> Unit>, registry: PanelRegistry = PanelRegistry(standardPanels())) {
    init { require(content.keys == registry.byId.keys) }
    @Composable fun Render(id: PanelId) { content.getValue(id).invoke() }
}
