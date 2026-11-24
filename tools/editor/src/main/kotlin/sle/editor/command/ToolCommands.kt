package sle.editor.command

import java.awt.event.KeyEvent
import sle.editor.viewport.ViewportMode
import sle.editor.viewport.tools.PivotMode
import sle.editor.viewport.tools.ViewportToolId

/**
 * Scene view tools (Q Select, W Move, E Rotate, R Scale), the Rotate/Scale pivot, Frame Selected
 * (F) and snapping. Tool keys work while the
 * Scene view or the Hierarchy has focus, as in Unity; the active tool is never an undo step.
 */
internal object ToolCommands {
    private val sceneScopes = setOf(CommandScope.SceneViewport, CommandScope.Hierarchy)
    private fun sceneView(ctx: CommandContext) = ctx.mode == ViewportMode.Scene && ctx.playback?.gameView != true && !ctx.viewportFailed
    private fun sceneReason(ctx: CommandContext) = if(sceneView(ctx)) null else "Show the Scene view to use its tools"

    private fun tool(id: ViewportToolId) = EditorCommand(CommandId("tools.${id.name.lowercase()}"), "${id.label} Tool", icon = if (id == ViewportToolId.Tiles) null else "tool_${id.name.lowercase()}",
        defaultChords = listOf(id.chord), scopes = sceneScopes,
        enabled = { sceneView(it) && it.tools != null && (id != ViewportToolId.Tiles || it.projectOpen && it.editable) }, disabledReason = ::sceneReason) { ctx -> ctx.tools?.activate(id) }
    val select = tool(ViewportToolId.Select)
    val move = tool(ViewportToolId.Move)
    val rotate = tool(ViewportToolId.Rotate)
    val scale = tool(ViewportToolId.Scale)
    val tiles = tool(ViewportToolId.Tiles)
    /** Entity selection and transform tools, in toolbar order. */
    val tools = listOf(select, move, rotate, scale)
    fun command(id: ViewportToolId) = if (id == ViewportToolId.Tiles) tiles else tools.first { it.id.value == "tools.${id.name.lowercase()}" }

    /** Fits the Scene view to the selection; View → Fit still frames everything. */
    val frameSelected = EditorCommand(CommandId("view.frameSelected"), "Frame Selected", icon = "frame_selected", defaultChords = listOf(KeyChord.plain(KeyEvent.VK_F)),
        scopes = sceneScopes, enabled = { sceneView(it) && it.selectionCount > 0 },
        disabledReason = { sceneReason(it) ?: if(it.selectionCount == 0) "Select an entity to frame" else null }) { it.actions.frameSelected() }
    val toggleSnap = EditorCommand(CommandId("tools.toggleSnap"), "Snapping", icon = "snap",
        enabled = { it.snap != null && it.mode != ViewportMode.Sandbox }) { it.snap?.toggle() }
    val configureSnap = EditorCommand(CommandId("tools.configureSnap"), "Configure Snap…",
        enabled = { it.snap != null && !it.modalOpen }) { it.actions.configureSnap() }
    /** Menu label for [toggleSnap]: the action it takes now. */
    fun toggleSnapLabel(ctx: CommandContext) = if(ctx.snap?.settings?.enabled == true) "Turn Snapping Off" else "Turn Snapping On"

    /** Switches what Rotate and Scale turn around: the active entity's pivot or the selection center. */
    val togglePivot = EditorCommand(CommandId("tools.togglePivot"), "Pivot/Center", icon = "pivot",
        enabled = { it.tools != null && it.mode == ViewportMode.Scene }) { ctx ->
        ctx.tools?.let { it.pivot = if(it.pivot == PivotMode.Pivot) PivotMode.Center else PivotMode.Pivot }
    }
    /** Menu label for [togglePivot]: the action it takes now. */
    fun togglePivotLabel(ctx: CommandContext) =
        if(ctx.tools?.pivot == PivotMode.Center) "Rotate and Scale Around the Pivot" else "Rotate and Scale Around the Selection Center"

    private val tileScope = setOf(CommandScope.SceneViewport)
    private fun tileEditing(ctx: CommandContext) = sceneView(ctx) && ctx.editable &&
        ctx.tools?.active == ViewportToolId.Tiles && ctx.tools.tilePainting.palette != null

    val tileModes = sle.editor.model.TilePaintMode.entries.mapIndexed { index, mode ->
        EditorCommand(CommandId("tiles.${mode.name.lowercase()}"), "Tile ${mode.label}",
            defaultChords = listOf(KeyChord.plain(KeyEvent.VK_1 + index)), scopes = tileScope,
            enabled = ::tileEditing) { it.tools?.tilePainting?.selectMode(mode) }
    }

    val copyTiles = EditorCommand(CommandId("tiles.copy"), "Copy Tiles",
        defaultChords = listOf(KeyChord.ctrl(KeyEvent.VK_C)), scopes = tileScope,
        enabled = { ctx -> tileEditing(ctx) &&
            ctx.tools?.tilePainting?.layerId?.let { ctx.scene?.tileSelection(it) } != null }) { ctx ->
        sceneAttempt(ctx) { ctx.tools?.tilePainting?.copy(it) }
    }

    val pasteTiles = EditorCommand(CommandId("tiles.paste"), "Paste Tiles",
        defaultChords = listOf(KeyChord.ctrl(KeyEvent.VK_V)), scopes = tileScope,
        enabled = { ctx -> tileEditing(ctx) && ctx.tools?.tilePainting?.clipboard != null &&
            ctx.tools.tilePainting.layerId?.let { ctx.scene?.tileLayer(it) }?.let { it.visible && !it.locked } == true }) { ctx ->
        sceneAttempt(ctx) { ctx.tools?.tilePainting?.paste(it) }
    }

    val all = tools + listOf(tiles, frameSelected, toggleSnap, configureSnap, togglePivot, copyTiles, pasteTiles) + tileModes
}
