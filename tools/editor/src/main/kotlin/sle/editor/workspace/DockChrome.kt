package sle.editor.workspace

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.ui.draw.drawBehind
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import sle.editor.ui.controls.*
import sle.editor.ui.theme.*

/** Logical dp shared by displayed chrome and minimum/drag calculations. */
data class DockChrome(val tabHeight: Float = 32f, val splitter: Float = 6f, val restoreStrip: Float = 28f) {
    init { require(listOf(tabHeight, splitter, restoreStrip).all { it.isFinite() && it >= 0 }) }
}

internal fun dockChrome(metrics: EditorMetrics, fontScale: Float): DockChrome {
    val growth=(fontScale-1f).coerceAtLeast(0f)*metrics.body.value
    return DockChrome(metrics.toolbar.value+growth,metrics.dockSplitter.value,metrics.button.value+growth)
}

@Composable
internal fun DockSurface(host: DockHost, manager: WorkspaceWindowManager, scale: Float, modifier: Modifier = Modifier,
    onBounds: (PanelId, androidx.compose.ui.geometry.Rect)->Unit = { _,_ -> }, content: @Composable (PanelId)->Unit) {
    val chrome=dockChrome(LocalEditorMetrics.current,LocalDensity.current.fontScale)
    SideEffect { manager.updateScale(scale);manager.updateChrome(chrome) }
    Column(modifier.onGloballyPositioned { manager.measureSurface(host.id,it.boundsInWindow()) }) {
        DockHost(host.root,manager,Modifier.weight(1f).fillMaxWidth(),onBounds,host.id,content=content)
        val minimized=manager.layout.minimized.filterValues { it.host==host.id }.keys
        val separator=LocalEditorColors.current.separator
        val hair=LocalEditorMetrics.current.borderWidth
        // The top rule is drawn inside the strip so chrome.restoreStrip stays the exact height.
        if(minimized.isNotEmpty()) Row(Modifier.fillMaxWidth().height(chrome.restoreStrip.dp).background(LocalEditorColors.current.surfaceRaised)
            .drawBehind { drawRect(separator,size=androidx.compose.ui.geometry.Size(size.width,hair.toPx())) }
            .horizontalScroll(rememberScrollState()),verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
            minimized.forEach { id -> EditorButton("Restore ${manager.registry.byId.getValue(id).title}",{manager.showPanel(id)},style=EditorButtonStyle.Ghost) }
        }
    }
}
