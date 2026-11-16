package sle.editor.workspace

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

/** Logical dp, including the compact panel controls; matches the measured 32dp row. */
data class DockChrome(val tabHeight: Float = 32f, val splitter: Float = 6f, val restoreStrip: Float = 28f) {
    init { require(listOf(tabHeight, splitter, restoreStrip).all { it.isFinite() && it >= 0 }) }
}

@Composable
internal fun DockSurface(host: DockHost, manager: WorkspaceWindowManager, scale: Float, modifier: Modifier = Modifier,
    onBounds: (PanelId, androidx.compose.ui.geometry.Rect)->Unit = { _,_ -> }, content: @Composable (PanelId)->Unit) {
    SideEffect { manager.updateScale(scale) }
    Column(modifier.onGloballyPositioned { manager.measureSurface(host.id,it.boundsInWindow()) }) {
        DockHost(host.root,manager,Modifier.weight(1f).fillMaxWidth(),onBounds,host.id,content=content)
        val minimized=manager.layout.minimized.filterValues { it.host==host.id }.keys
        if(minimized.isNotEmpty()) Row(Modifier.fillMaxWidth().height(28.dp).horizontalScroll(rememberScrollState())) {
            minimized.forEach { id -> TextButton(onClick={manager.showPanel(id)},contentPadding=PaddingValues(horizontal=8.dp,vertical=0.dp)) {
                Text("Restore ${manager.registry.byId.getValue(id).title}")
            } }
        }
    }
}
