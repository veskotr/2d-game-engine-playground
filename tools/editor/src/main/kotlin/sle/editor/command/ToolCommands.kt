package sle.editor.command

import java.awt.event.KeyEvent
import sle.editor.viewport.ViewportMode
import sle.editor.viewport.tools.ViewportToolId

/**
 * Scene view tools (Q Select, W Move), Frame Selected (F) and snapping. Tool keys work while the
 * Scene view or the Hierarchy has focus, as in Unity; the active tool is never an undo step.
 */
internal object ToolCommands {
    private val sceneScopes = setOf(CommandScope.SceneViewport, CommandScope.Hierarchy)
    private fun sceneView(ctx: CommandContext) = ctx.mode == ViewportMode.Scene && ctx.playback?.gameView != true && !ctx.viewportFailed
    private fun sceneReason(ctx: CommandContext) = if(sceneView(ctx)) null else "Show the Scene view to use its tools"

    private fun tool(id: ViewportToolId) = EditorCommand(CommandId("tools.${id.name.lowercase()}"), "${id.label} Tool",
        defaultChords = listOf(id.chord), scopes = sceneScopes,
        enabled = { sceneView(it) && it.tools != null }, disabledReason = ::sceneReason) { ctx -> ctx.tools?.activate(id) }
    val select = tool(ViewportToolId.Select)
    val move = tool(ViewportToolId.Move)
    /** Every tool command, in toolbar order. */
    val tools = listOf(select, move)
    fun command(id: ViewportToolId) = tools.first { it.id.value == "tools.${id.name.lowercase()}" }

    /** Fits the Scene view to the selection; View → Fit still frames everything. */
    val frameSelected = EditorCommand(CommandId("view.frameSelected"), "Frame Selected", defaultChords = listOf(KeyChord.plain(KeyEvent.VK_F)),
        scopes = sceneScopes, enabled = { sceneView(it) && it.selectionCount > 0 },
        disabledReason = { sceneReason(it) ?: if(it.selectionCount == 0) "Select an entity to frame" else null }) { it.actions.frameSelected() }
    val toggleSnap = EditorCommand(CommandId("tools.toggleSnap"), "Snapping",
        enabled = { it.snap != null && it.mode != ViewportMode.Sandbox }) { it.snap?.toggle() }
    val configureSnap = EditorCommand(CommandId("tools.configureSnap"), "Configure Snap…",
        enabled = { it.snap != null && !it.modalOpen }) { it.actions.configureSnap() }
    /** Menu label for [toggleSnap]: the action it takes now. */
    fun toggleSnapLabel(ctx: CommandContext) = if(ctx.snap?.settings?.enabled == true) "Turn Snapping Off" else "Turn Snapping On"

    val all = tools + listOf(frameSelected, toggleSnap, configureSnap)
}
