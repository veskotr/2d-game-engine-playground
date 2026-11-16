package sle.editor.workspace

import androidx.compose.runtime.Composable
import kotlin.test.*

class WorkspacePanelsTest {
    private val future = PanelId("future")
    private val registry = PanelRegistry(standardPanels() +
        PanelDescriptor(future, "Future", PanelMinimum(120f, 80f)))
    private fun bindings(): Map<PanelId, @Composable () -> Unit> =
        registry.panels.associate { it.id to @Composable {} }

    @Test fun registeredFuturePanelUsesTheSameBindingAndWindowLifecycle() {
        WorkspacePanels(bindings(), registry)
        val manager = WorkspaceWindowManager(registry)
        try {
            manager.movePanel(future, DockTarget.Detached(LogicalBounds(10f, 20f, 500f, 400f)))
            manager.minimizePanel(future)
            manager.showPanel(future)
            manager.closeHost(manager.hostFor(future)!!)
            assertTrue(future in manager.layout.hidden)
            manager.showPanel(future)
            assertEquals(1, manager.layout.tabs().sumOf { it.panels.count { id -> id == future } })
            assertTrue(validateLayout(manager.layout, registry.byId.keys).isEmpty())
        } finally { manager.close() }
    }

    @Test fun missingOrUnregisteredBindingsAreRejected() {
        assertFailsWith<IllegalArgumentException> { WorkspacePanels(bindings() - future, registry) }
        assertFailsWith<IllegalArgumentException> { WorkspacePanels(bindings() + (PanelId("unknown") to @Composable {}), registry) }
    }
}
