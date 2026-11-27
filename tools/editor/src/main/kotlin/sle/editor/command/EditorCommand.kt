package sle.editor.command

import sle.editor.model.EntityWorkspaceState
import sle.editor.model.ScenePlaybackState
import sle.editor.model.SceneWorkspaceState
import sle.editor.model.UndoableDocument
import sle.editor.project.ProjectCommands
import sle.editor.viewport.ViewportMode
import sle.editor.workspace.PanelId

internal enum class DocumentKind { Scene, Prefab }

/**
 * Everything a command's enabled rule and action may consult. The application builds the
 * base context from its current state; the dispatcher adds the key event's focus facts.
 */
internal data class CommandContext(
    /** Scene, prefab (Entity) or sandbox workspace. */
    val mode: ViewportMode = ViewportMode.Scene,
    /** A scene entity is open in the prefab workspace (legacy linked editing). */
    val linked: Boolean = false,
    val scene: SceneWorkspaceState? = null,
    /** The entity the Inspector or prefab workspace edits. */
    val entity: EntityWorkspaceState? = null,
    /** The standalone prefab document. */
    val prefab: EntityWorkspaceState? = null,
    val playback: ScenePlaybackState? = null,
    /** Authoring is allowed (scene playback stopped). */
    val editable: Boolean = true,
    val projectOpen: Boolean = false,
    val projectBusy: Boolean = false,
    val viewportFailed: Boolean = false,
    /** Scene or sandbox playback is running or paused, so the Game view owns its keys. */
    val gameRunning: Boolean = false,
    val gameActionKeys: Set<Int> = emptySet(),
    /** The native Game canvas can receive this key event. */
    val gameInputFocused: Boolean = false,
    val focusedPanel: PanelId? = null,
    val modalOpen: Boolean = false,
    val textInputFocused: Boolean = false,
    /** The Scene view's active tool (per-user state). */
    val tools: sle.editor.viewport.tools.ViewportToolState? = null,
    /** Snap steps and toggle (per user, per project). */
    val snap: sle.editor.viewport.SnapService? = null,
    val details: DetailsTarget? = null,
    val detailsClipboard: DetailsClipboard = DesktopDetailsClipboard,
    val projectGeneration: Long = 0,
    val resolveDetailsAsset: ((String) -> String)? = null,
    val actions: EditorActions = EditorActions.None,
) {
    val documentKind get() = if(mode != ViewportMode.Entity || linked) DocumentKind.Scene else DocumentKind.Prefab
    val sceneDocument get() = documentKind == DocumentKind.Scene
    /** The active selected scene entity. */
    val selection get() = scene?.selected
    /** How many scene entities are selected. */
    val selectionCount get() = scene?.selection?.size ?: 0
    /** The active document's undo timeline: the scene (also while one of its entities is open in
     * the Entity tab) or the standalone prefab workspace. */
    val document: UndoableDocument? get() = if(sceneDocument) scene else prefab
}

/** Application operations that live in the window composition rather than in document models. */
internal interface EditorActions {
    val project: ProjectCommands? get() = null
    fun showScene(game: Boolean) {}
    fun openPrefabWorkspace() {}
    fun editEntity(key: String) {}
    fun showPanel(panel: PanelId) {}
    fun showLuaConsole() {}
    fun projectLuaEnabled(script: sle.editor.scripts.ProjectLuaCommand): Boolean = false
    fun runProjectLua(script: sle.editor.scripts.ProjectLuaCommand) {}
    fun finishGesture() {}
    fun fit() {}
    /** Fits the Scene view to the selected entities. */
    fun frameSelected() {}
    /** Opens the Configure Snap dialog. */
    fun configureSnap() {}
    /** Shows and focuses the Tile Palette panel. */
    fun openTilePalette() {}
    fun runProject() {}
    fun showKeyboardShortcuts() {}
    object None : EditorActions
}

/**
 * One editor action, registered once. Menus, context menus, toolbar tooltips, the keyboard
 * dispatcher and Help all read the same definition. A command may have several default chords
 * (Redo: Ctrl+Y and Ctrl+Shift+Z) and several scopes (Delete: Hierarchy and Scene view).
 */
internal class EditorCommand(
    val id: CommandId,
    val label: String,
    /** Shorter text where the parent menu supplies context, e.g. "Scene…" under File → Open. */
    val menuLabel: String? = null,
    val icon: String? = null,
    val defaultChords: List<KeyChord> = emptyList(),
    val scopes: Set<CommandScope> = setOf(CommandScope.Global),
    /** A held key repeats the command; otherwise it runs once per press. */
    val repeatable: Boolean = false,
    /** Play controls still work while the focused Game view receives the game's keys. */
    val playControl: Boolean = false,
    val enabled: (CommandContext) -> Boolean = { true },
    /** Why the command is disabled, shown as the menu item's tooltip; null when there is no useful reason. */
    val disabledReason: (CommandContext) -> String? = { null },
    val run: (CommandContext) -> Unit,
) {
    init { require(scopes.isNotEmpty()) { "${id.value} needs a scope" } }
    override fun toString() = id.value
}

/** Runs a scene edit after committing the selected entity's open gesture; failures become the scene notice. */
internal fun sceneAttempt(ctx: CommandContext, action: (SceneWorkspaceState) -> Unit) {
    val scene = ctx.scene ?: return
    try { scene.selected?.endGesture();action(scene) }
    catch(failure: Exception) { scene.error = failure.message ?: "Scene operation failed" }
}
