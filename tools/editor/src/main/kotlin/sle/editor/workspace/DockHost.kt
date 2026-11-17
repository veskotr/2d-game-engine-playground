package sle.editor.workspace

import androidx.compose.foundation.background
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import sle.editor.ui.controls.*
import sle.editor.ui.theme.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.*
import java.awt.Cursor

@Composable
internal fun DockHost(
    node: DockNode?, manager: WorkspaceWindowManager, modifier: Modifier = Modifier,
    onBounds: (PanelId, Rect) -> Unit = { _, _ -> }, host: HostId = manager.layout.mainHost,
    path: List<Boolean> = emptyList(), content: @Composable (PanelId) -> Unit,
) {
    val chrome=dockChrome(LocalEditorMetrics.current,LocalDensity.current.fontScale)
    val colors=LocalEditorColors.current
    val metrics=LocalEditorMetrics.current
    if (node == null) { EditorEmptyState("No panels open","Open a panel from the Windows menu.",modifier); return }
    when (node) {
        is DockNode.Split -> {
            var span by remember { mutableStateOf(0f) }
            val density=LocalDensity.current.density
            val measured=modifier.onGloballyPositioned { span=(if(node.axis==DockAxis.Horizontal) it.size.width else it.size.height)/density }
            val a=nodeMinimum(node.first,manager.registry,chrome);val b=nodeMinimum(node.second,manager.registry,chrome)
            val clamped=if(span>0) clampSplitRatio(node.ratio,span,
                if(node.axis==DockAxis.Horizontal) a.width else a.height,
                if(node.axis==DockAxis.Horizontal) b.width else b.height,chrome.splitter) else node.ratio
            LaunchedEffect(span,node.ratio,chrome) { if(span>0 && clamped!=node.ratio) manager.resizeSplit(host,path,clamped,span) }
            val current by rememberUpdatedState(node)
            var ratio=node.ratio
            val splitterSource=remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
            val splitterHovered by splitterSource.collectIsHoveredAsState()
            var dragging by remember { mutableStateOf(false) }
            val horizontal=node.axis==DockAxis.Horizontal
            // The full splitter slot is the hit target; only a 1dp rule is drawn at rest.
            val divider=Modifier.background(colors.background)
                .hoverable(splitterSource)
                .drawBehind {
                    val active=splitterHovered || dragging
                    val t=(if(active) metrics.focusBorderWidth else metrics.borderWidth).toPx()
                    val color=if(active) colors.accent else colors.separator
                    if(horizontal) drawRect(color,Offset((size.width-t)/2,0f),Size(t,size.height))
                    else drawRect(color,Offset(0f,(size.height-t)/2),Size(size.width,t))
                }
                .pointerHoverIcon(PointerIcon(Cursor.getPredefinedCursor(if(node.axis==DockAxis.Horizontal) Cursor.E_RESIZE_CURSOR else Cursor.N_RESIZE_CURSOR)))
                .pointerInput(host,path,density,chrome) {
                    detectDragGestures(onDragStart={ ratio=current.ratio;dragging=true;manager.prepareInteraction() },
                        onDragEnd={ dragging=false },onDragCancel={ dragging=false }) { change,amount ->
                        change.consume()
                        val delta=(if(current.axis==DockAxis.Horizontal) amount.x else amount.y)/density
                        if(span>chrome.splitter) {
                            ratio+=delta/(span-chrome.splitter)
                            manager.resizeSplit(host,path,ratio,span)
                            // Use the clamped ratio for the next delta, avoiding a dead zone at minima.
                            ratio=manager.splitAt(host,path)?.ratio ?: ratio
                        }
                    }
                }
            if(node.axis==DockAxis.Horizontal) Row(measured) {
                DockHost(node.first,manager,Modifier.weight(clamped).fillMaxHeight(),onBounds,host,path+false,content)
                Box(divider.width(chrome.splitter.dp).fillMaxHeight())
                DockHost(node.second,manager,Modifier.weight(1-clamped).fillMaxHeight(),onBounds,host,path+true,content)
            } else Column(measured) {
                DockHost(node.first,manager,Modifier.weight(clamped).fillMaxWidth(),onBounds,host,path+false,content)
                Box(divider.height(chrome.splitter.dp).fillMaxWidth())
                DockHost(node.second,manager,Modifier.weight(1-clamped).fillMaxWidth(),onBounds,host,path+true,content)
            }
        }
        is DockNode.Tabs -> {
            DisposableEffect(host,node.group) { onDispose { manager.forgetGroup(host,node.group) } }
            Column(modifier.onGloballyPositioned { manager.measureGroup(host,node.group,it.boundsInWindow()) }) {
                val singlePanelWindow=manager.layout.isSinglePanelWindow(host)
                SideEffect { if(singlePanelWindow) manager.forgetHeader(host,node.group) }
                // Header rule is drawn inside chrome.tabHeight so dock minima stay exact.
                if(!singlePanelWindow) Row(Modifier.height(chrome.tabHeight.dp).fillMaxWidth().background(colors.surfaceRaised)
                    .drawBehind { val t=metrics.borderWidth.toPx();drawRect(colors.separator,Offset(0f,size.height-t),Size(size.width,t)) },verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
                    Box(Modifier.width(LocalEditorMetrics.current.dockGrip).fillMaxHeight().onGloballyPositioned {
                        manager.measureHandle(host,node.group,it.boundsInWindow())
                    }.semantics { contentDescription="Drag panel group" },contentAlignment=androidx.compose.ui.Alignment.Center) {
                        EditorGlyphIcon(EditorGlyph.Grip,colors.textSecondary)
                    }
                    key(node.panels) {
                        var stripWidth by remember { mutableStateOf(0) }
                        Row(Modifier.weight(1f).fillMaxHeight().onGloballyPositioned { manager.measureStrip(host,node.group,it.boundsInWindow());stripWidth=it.size.width }.horizontalScroll(rememberScrollState())) {
                            node.panels.forEach { id ->
                                val reveal=remember { BringIntoViewRequester() }
                                LaunchedEffect(node.active,id,stripWidth) {
                                    if(node.active==id) { withFrameNanos { };reveal.bringIntoView() }
                                }
                                EditorTab(manager.registry.byId.getValue(id).title,id==node.active,{manager.focusPanel(id)},
                                    modifier=Modifier.bringIntoViewRequester(reveal).onGloballyPositioned {
                                        onBounds(id,it.boundsInWindow());manager.measureTab(host,node.group,id,it.boundsInWindow())
                                    })
                            }
                        }
                    }
                    Row(Modifier.padding(end=LocalEditorMetrics.current.gap/2),horizontalArrangement=Arrangement.spacedBy(metrics.borderWidth)) {
                        DockPanelButton(EditorGlyph.Minimize,"Minimize panel") { manager.minimizePanel(node.active) }
                        DockPanelButton(EditorGlyph.Detach,"Detach panel") { manager.detachPanel(node.active) }
                        DockPanelButton(EditorGlyph.Close,"Hide panel") { manager.hidePanel(node.active) }
                    }
                }
                // Focus-within reports the active panel for scoped shortcuts (Hierarchy, Scene view…).
                val active=node.active
                Box(Modifier.weight(1f).fillMaxWidth().background(colors.surface)
                    .onFocusChanged { manager.focus.report(host,active,it.hasFocus) }) { key(node.active) { content(node.active) } }
            }
        }
    }
}

@Composable
private fun DockPanelButton(glyph: EditorGlyph, help: String, action: ()->Unit) {
    val m=LocalEditorMetrics.current
    EditorGlyphButton(help,action,help=help,size=m.button-m.gap) { tint -> EditorGlyphIcon(glyph,tint) }
}
