package sle.editor.workspace

data class DockPoint(val x: Float, val y: Float)
/** AWT screen user-space origin and OS scale; local values are Compose physical pixels.
 * UI scale is already included in measured local pixels and must not be applied twice. */
data class DockScreenTransform(val x: Float, val y: Float, val scaleX: Float, val scaleY: Float) {
    init { require(listOf(x,y,scaleX,scaleY).all { it.isFinite() } && scaleX>0 && scaleY>0) }
    fun toLocal(point: DockPoint) = DockPoint((point.x-x)*scaleX,(point.y-y)*scaleY)
    fun toScreen(point: DockPoint) = DockPoint(x+point.x/scaleX,y+point.y/scaleY)
}
sealed interface DockDragSource {
    data class Panel(val id: PanelId) : DockDragSource
    data class Group(val id: GroupId) : DockDragSource
}
/** No layout mutation until drop. Any intervening layout command invalidates the drag. */
class DockDragController {
    private data class Capture(val layout: DockLayout, val revision: Long, val source: DockDragSource)
    private var capture: Capture? = null
    val active get() = capture != null
    val source get() = capture?.source
    fun begin(layout: DockLayout, revision: Long, source: DockDragSource) {
        capture = if (when(source) {
            is DockDragSource.Panel -> layout.tabs().any { source.id in it.panels }
            is DockDragSource.Group -> layout.tabs().any { source.id==it.group }
        }) Capture(layout,revision,source) else null
    }
    fun cancel() { capture=null }
    fun moving(): Set<PanelId> = capture?.let { captured -> when(val source=captured.source) {
        is DockDragSource.Panel -> setOf(source.id)
        is DockDragSource.Group -> captured.layout.tabs().single { it.group==source.id }.panels.toSet()
    } }.orEmpty()
    fun drop(layout: DockLayout, revision: Long, target: DockTarget?): DockLayout {
        val captured=capture;cancel()
        if(captured==null || captured.revision!=revision || captured.layout!=layout || target==null) return layout
        return when(val source=captured.source) {
            is DockDragSource.Panel -> movePanel(layout,source.id,target)
            is DockDragSource.Group -> moveGroup(layout,source.id,target)
        }
    }
}
