package sle.editor.workspace

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import java.awt.Cursor

@Composable
internal fun DockHost(
    node: DockNode?, manager: WorkspaceWindowManager, modifier: Modifier = Modifier,
    onBounds: (PanelId, Rect) -> Unit = { _, _ -> }, host: HostId = manager.layout.mainHost,
    path: List<Boolean> = emptyList(), content: @Composable (PanelId) -> Unit,
) {
    if (node == null) { Box(modifier) { Text("Open a panel from Windows.") }; return }
    when (node) {
        is DockNode.Split -> {
            var span by remember { mutableStateOf(0f) }
            val density=LocalDensity.current.density
            val measured=modifier.onGloballyPositioned { span=(if(node.axis==DockAxis.Horizontal) it.size.width else it.size.height)/density }
            val a=nodeMinimum(node.first,manager.registry);val b=nodeMinimum(node.second,manager.registry)
            val clamped=if(span>0) clampSplitRatio(node.ratio,span,
                if(node.axis==DockAxis.Horizontal) a.width else a.height,
                if(node.axis==DockAxis.Horizontal) b.width else b.height) else node.ratio
            LaunchedEffect(span,node.ratio) { if(span>0 && clamped!=node.ratio) manager.resizeSplit(host,path,clamped,span) }
            val current by rememberUpdatedState(node)
            var ratio=node.ratio
            val divider=Modifier.background(MaterialTheme.colors.primary.copy(alpha=.25f))
                .pointerHoverIcon(PointerIcon(Cursor.getPredefinedCursor(if(node.axis==DockAxis.Horizontal) Cursor.E_RESIZE_CURSOR else Cursor.N_RESIZE_CURSOR)))
                .pointerInput(host,path,density) {
                    detectDragGestures(onDragStart={ ratio=current.ratio;manager.prepareInteraction() }) { change,amount ->
                        change.consume()
                        val delta=(if(current.axis==DockAxis.Horizontal) amount.x else amount.y)/density
                        if(span>6f) {
                            ratio+=delta/(span-6f)
                            manager.resizeSplit(host,path,ratio,span)
                            // Use the clamped ratio for the next delta, avoiding a dead zone at minima.
                            ratio=manager.splitAt(host,path)?.ratio ?: ratio
                        }
                    }
                }
            if(node.axis==DockAxis.Horizontal) Row(measured) {
                DockHost(node.first,manager,Modifier.weight(clamped).fillMaxHeight(),onBounds,host,path+false,content)
                Box(divider.width(6.dp).fillMaxHeight())
                DockHost(node.second,manager,Modifier.weight(1-clamped).fillMaxHeight(),onBounds,host,path+true,content)
            } else Column(measured) {
                DockHost(node.first,manager,Modifier.weight(clamped).fillMaxWidth(),onBounds,host,path+false,content)
                Box(divider.height(6.dp).fillMaxWidth())
                DockHost(node.second,manager,Modifier.weight(1-clamped).fillMaxWidth(),onBounds,host,path+true,content)
            }
        }
        is DockNode.Tabs -> {
            DisposableEffect(host,node.group) { onDispose { manager.forgetGroup(host,node.group) } }
            Column(modifier.onGloballyPositioned { manager.measureGroup(host,node.group,it.boundsInWindow()) }) {
                Row(Modifier.height(32.dp).fillMaxWidth()) {
                    Box(Modifier.width(20.dp).fillMaxHeight().onGloballyPositioned {
                        manager.measureHandle(host,node.group,it.boundsInWindow())
                    }) { Text("⠿") }
                    key(node.panels) {
                        ScrollableTabRow(node.panels.indexOf(node.active),edgePadding=0.dp,modifier=Modifier.weight(1f).onGloballyPositioned { manager.measureStrip(host,node.group,it.boundsInWindow()) }) {
                            node.panels.forEach { id ->
                                Tab(id==node.active,onClick={manager.focusPanel(id)},
                                    modifier=Modifier.onGloballyPositioned {
                                        onBounds(id,it.boundsInWindow());manager.measureTab(host,node.group,id,it.boundsInWindow())
                                    },text={Text(manager.registry.byId.getValue(id).title,maxLines=1)})
                            }
                        }
                    }
                    DockPanelButton("_", { manager.minimizePanel(node.active) })
                    DockPanelButton("↗", { manager.detachPanel(node.active) })
                    DockPanelButton("×", { manager.hidePanel(node.active) })
                }
                Box(Modifier.weight(1f).fillMaxWidth()) { key(node.active) { content(node.active) } }
            }
        }
    }
}

@Composable
private fun DockPanelButton(label: String, action: ()->Unit) {
    TextButton(onClick=action,modifier=Modifier.width(24.dp).fillMaxHeight(),contentPadding=PaddingValues(0.dp)) { Text(label) }
}
