package sle.editor.workspace

import androidx.compose.ui.graphics.toArgb
import java.awt.*
import java.awt.event.*
import javax.swing.JWindow
import javax.swing.SwingUtilities
import javax.swing.Timer
import kotlin.math.abs

/** One workspace-wide AWT capture, including events delivered by heavyweight viewport peers.
 * Geometry and MouseEvent xOnScreen/yOnScreen both use AWT screen user coordinates.
 * A nonfocusable transparent native window paints above heavyweight OpenGL content. */
internal class DockDesktopDrag(private val manager: WorkspaceWindowManager) : AutoCloseable {
    private var installed=false
    private var pressed: DockPoint?=null
    private var origin: Window?=null
    private var source: DockDragSource?=null
    private var overlay: JWindow?=null
    private var lastTarget: DockTarget?=null
    private var dragging=false
    private val escape=KeyEventDispatcher { event ->
        if(event.id==KeyEvent.KEY_PRESSED && event.keyCode==KeyEvent.VK_ESCAPE && pressed!=null) { cancel();true } else false
    }
    private val events=AWTEventListener { event ->
        when(event) {
            is MouseEvent -> mouse(event)
            is WindowEvent -> {
                if(event.id==WindowEvent.WINDOW_GAINED_FOCUS || event.id==WindowEvent.WINDOW_ACTIVATED) manager.noteActivation(event.window)
                if(event.id==WindowEvent.WINDOW_CLOSING || event.id==WindowEvent.WINDOW_ICONIFIED ||
                event.id==WindowEvent.WINDOW_CLOSED || (event.id==WindowEvent.WINDOW_LOST_FOCUS && event.oppositeWindow !in manager.boundWindows().values)) {
                if(event.window===origin) cancel()
                }
            }
        }
    }
    // Native capture loss normally delivers an out-of-workspace focus transition.
    // Also cancel a capture whose source peer disappears without a window event.
    private val captureWatch=Timer(100) {
        if(pressed!=null && (origin?.isShowing!=true || manager.closed)) cancel()
    }
    fun install() {
        if(installed) return
        installed=true
        Toolkit.getDefaultToolkit().addAWTEventListener(events,AWTEvent.MOUSE_EVENT_MASK or AWTEvent.MOUSE_MOTION_EVENT_MASK or AWTEvent.WINDOW_EVENT_MASK or AWTEvent.WINDOW_FOCUS_EVENT_MASK)
        KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(escape)
        captureWatch.start()
    }
    private fun mouse(event: MouseEvent) {
        if(!installed) return
        val point=DockPoint(event.xOnScreen.toFloat(),event.yOnScreen.toFloat())
        when(event.id) {
            MouseEvent.MOUSE_PRESSED -> {
                if(event.button!=MouseEvent.BUTTON1) { cancel();return }
                cancel()
                val window=SwingUtilities.getWindowAncestor(event.component)
                val host=manager.boundWindows().entries.firstOrNull { it.value===window }?.key ?: return
                val candidate=manager.dragSource(host,point) ?: return
                pressed=point;source=candidate;origin=window
            }
            MouseEvent.MOUSE_DRAGGED -> {
                val start=pressed ?: return
                if(!dragging && abs(point.x-start.x)+abs(point.y-start.y)<6f) return
                if(!dragging) {
                    manager.prepareInteraction()
                    manager.drag.begin(manager.layout,manager.revision,source ?: return)
                    dragging=manager.drag.active
                }
                if(!manager.drag.active) { cancel();return }
                if(event.modifiersEx and InputEvent.BUTTON1_DOWN_MASK==0) { cancel();return }
                preview(point);event.consume()
            }
            MouseEvent.MOUSE_RELEASED -> {
                if(event.button!=MouseEvent.BUTTON1) return
                if(dragging) {
                    preview(point)
                    val target=lastTarget
                    // Clear capture/overlay before layout mutation destroys the source peer.
                    clearCapture();manager.commitDrop(target);event.consume()
                } else cancel()
            }
        }
    }
    private fun preview(point: DockPoint) {
        val areas=manager.dropAreas()
        val moving=manager.drag.moving()
        val hosts=manager.visibleHostBounds()
        var target=dockDropTarget(point,areas,moving,hosts)
        val inside=hosts.any { it.second.contains(point) }
        if(target==null && !inside) target=DockTarget.Detached(LogicalBounds(point.x-100,point.y-16,640f,480f))
        // Preview only proposals which the atomic layout operation can actually commit.
        val proposal=when(val source=manager.drag.source) {
            is DockDragSource.Panel -> target?.let { movePanel(manager.layout,source.id,it) }
            is DockDragSource.Group -> target?.let { moveGroup(manager.layout,source.id,it) }
            null -> null
        }
        if(proposal==null || proposal==manager.layout) target=null
        lastTarget=target
        val area=when(target) {
            is DockTarget.Tab -> areas.firstOrNull { it.group==target.group }?.let { a ->
                val remaining=a.tabs.filterNot { it.first in moving }
                val order=a.panelOrder.filterNot { it in moving }
                val anchor=order.getOrNull(target.index)
                val x=remaining.firstOrNull { it.first==anchor }?.second?.x
                    ?: if(remaining.isNotEmpty() && target.index<=order.indexOf(remaining.first().first)) remaining.first().second.x
                    else remaining.lastOrNull()?.second?.let { it.x+it.width } ?: a.bounds.x+20
                LogicalBounds(x,a.bounds.y,4f,32f)
            }
            is DockTarget.Split -> areas.firstOrNull { it.group==target.group }?.bounds?.let { b -> when(target.edge) {
                DockEdge.Left -> b.copy(width=b.width/2)
                DockEdge.Right -> b.copy(x=b.x+b.width/2,width=b.width/2)
                DockEdge.Top -> b.copy(height=b.height/2)
                DockEdge.Bottom -> b.copy(y=b.y+b.height/2,height=b.height/2)
            } }
            is DockTarget.Detached -> target.bounds
            null -> null
        }
        if(area==null) { overlay?.isVisible=false;return }
        val preview=overlay ?: JWindow().apply {
            focusableWindowState=false;isAlwaysOnTop=true
            background=Color(0,0,0,0)
            contentPane=object: javax.swing.JComponent() {
                override fun paintComponent(graphics: Graphics) {
                    val accent=Color(sle.editor.ui.theme.editorColors(sle.editor.ui.EditorDialogAppearance.appearance.mode).accent.toArgb(),true)
                    graphics.color=Color(accent.red,accent.green,accent.blue,80);graphics.fillRect(0,0,width,height)
                    graphics.color=accent;graphics.drawRect(0,0,width-1,height-1)
                    graphics.drawRect(1,1,(width-3).coerceAtLeast(0),(height-3).coerceAtLeast(0))
                }
            }
        }.also { overlay=it }
        preview.setBounds(area.x.toInt(),area.y.toInt(),area.width.toInt().coerceAtLeast(4),area.height.toInt().coerceAtLeast(4))
        preview.isVisible=true;preview.repaint()
    }
    private fun clearCapture() {
        pressed=null;source=null;origin=null;dragging=false;lastTarget=null
        overlay?.dispose();overlay=null
    }
    fun cancel() { clearCapture();manager.drag.cancel() }
    override fun close() {
        cancel()
        if(!installed) return
        installed=false;captureWatch.stop()
        Toolkit.getDefaultToolkit().removeAWTEventListener(events)
        KeyboardFocusManager.getCurrentKeyboardFocusManager().removeKeyEventDispatcher(escape)
    }
}
