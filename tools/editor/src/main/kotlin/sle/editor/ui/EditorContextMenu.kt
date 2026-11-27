package sle.editor.ui

import androidx.compose.runtime.*
import androidx.compose.material.MaterialTheme
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.LocalDensity
import java.awt.Color
import kotlin.math.roundToInt
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.onPointerEvent
import java.awt.KeyboardFocusManager
import javax.swing.plaf.basic.BasicPopupMenuUI
import java.awt.MouseInfo
import javax.swing.*
import sle.editor.model.EntityWorkspaceState
import sle.editor.model.addEditorComponent
import sle.editor.model.fitShapeToSprite
import sle.editor.model.SceneWorkspaceState
import sle.editor.viewport.GizmoShape
import sle.editor.command.menuEntry

internal data class EditorMenuEntry(
    val label: String,
    val enabled: Boolean = true,
    val children: List<EditorMenuEntry> = emptyList(),
    /** The registered command this entry runs, if any (see `command/CommandMenus.kt`). */
    val command: sle.editor.command.CommandId? = null,
    /** Shown right-aligned; display only. The shortcut dispatcher handles the key. */
    val shortcut: sle.editor.command.KeyChord? = null,
    val separator: Boolean = false,
    /** Tooltip, e.g. why a command is disabled. */
    val help: String? = null,
    /** Icon key from `EditorIcons` (the command's icon); null falls back to the label's glyph. */
    val icon: String? = null,
    val action: () -> Unit = {},
) {
    companion object { val Separator = EditorMenuEntry("", enabled = false, separator = true) }
}

/** Removes leading, trailing and doubled separators left by conditional entries. */
internal fun List<EditorMenuEntry>.tidySeparators(): List<EditorMenuEntry> =
    fold(mutableListOf<EditorMenuEntry>()) { result, entry ->
        if(!entry.separator || (result.isNotEmpty() && !result.last().separator)) result.add(entry);result
    }.dropLastWhile { it.separator }

private val editorPopups=java.util.WeakHashMap<JPopupMenu,MenuAppearance>()

/** Refresh open popups without rebuilding their action listeners or current selection path. */
internal fun refreshEditorPopups(value: sle.editor.ui.theme.EditorAppearance) {
    for((popup,previous) in editorPopups.toMap()) {
        val theme=MenuAppearance.of(value.mode,value.density,previous.scale)
        styleEditorPopup(popup,theme)
        popup.components.filterIsInstance<EditorMenuSeparator>().forEach { it.theme=theme }
        popup.components.filterIsInstance<JMenuItem>().forEach { item ->
            if(item is JMenu) item.setUI(EditorSubmenuUI(theme)) else item.setUI(EditorMenuItemUI(theme))
            item.background=theme.surface;item.foreground=theme.foreground;item.font=theme.font
            item.icon=EditorIcons.menuIcon(item.getClientProperty(EditorIcons.MENU_ICON_KEY) as String?,item.text,theme);item.disabledIcon=item.icon
        }
        popup.revalidate();popup.repaint()
    }
}

internal fun styleEditorPopup(popup: JPopupMenu, appearance: MenuAppearance) {
    editorPopups[popup]=appearance
    popup.setUI(BasicPopupMenuUI())
    popup.isOpaque = true
    popup.isLightWeightPopupEnabled = false
    popup.background = appearance.surface
    popup.foreground = appearance.foreground
    popup.border = BorderFactory.createCompoundBorder(
        BorderFactory.createLineBorder(appearance.outline),
        BorderFactory.createEmptyBorder(4, 0, 4, 0),
    )
}

internal fun buildEditorPopup(entries: List<EditorMenuEntry>, appearance: MenuAppearance = MenuAppearance()): JPopupMenu {
    fun item(entry: EditorMenuEntry): java.awt.Component {
        if(entry.separator) return EditorMenuSeparator(appearance)
        val result = if (entry.children.isEmpty()) JMenuItem(entry.label).apply {
            setUI(EditorMenuItemUI(appearance))
            entry.shortcut?.let { putClientProperty(EditorMenuItemUI.SHORTCUT,it.display()) }
            addActionListener { entry.action() }
        } else JMenu(entry.label).apply {
            setUI(EditorSubmenuUI(appearance))
            entry.children.tidySeparators().forEach { add(item(it)) }
            styleEditorPopup(popupMenu,appearance)
        }
        return result.apply {
            isOpaque=true
            isEnabled=entry.enabled
            toolTipText=entry.help?.takeIf { it.isNotBlank() } ?: entry.label
            background=appearance.surface;foreground=appearance.foreground;font=appearance.font
            putClientProperty(EditorIcons.MENU_ICON_KEY,entry.icon)
            icon=EditorIcons.menuIcon(entry.icon,entry.label,appearance)
            disabledIcon=icon
            iconTextGap=(8*appearance.scale).roundToInt()
            border=BorderFactory.createEmptyBorder(appearance.rowPadding,10,appearance.rowPadding,12)
        }
    }
    return JPopupMenu().apply {
        styleEditorPopup(this,appearance)
        entries.tidySeparators().forEach { add(item(it)) }
    }
}

@Composable
internal fun editorMenuLauncher(entries: () -> List<EditorMenuEntry>): (Int, Int) -> Unit {
    val editorAppearance=sle.editor.ui.theme.LocalEditorAppearance.current
    val appearance=MenuAppearance.of(editorAppearance.mode,editorAppearance.density,editorAppearance.scale)
    val currentAppearance by rememberUpdatedState(appearance)
    val currentEntries by rememberUpdatedState(entries)
    return remember {
        { x: Int, y: Int ->
            val values=currentEntries()
            val theme=currentAppearance
            SwingUtilities.invokeLater {
                val owner=KeyboardFocusManager.getCurrentKeyboardFocusManager().activeWindow
                buildEditorPopup(values,theme).show(owner,x-(owner?.x ?: 0),y-(owner?.y ?: 0))
            }
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun Modifier.editorContextMenu(
    beforeOpen: () -> Unit = {},
    node: Boolean = false,
    entries: () -> List<EditorMenuEntry>,
): Modifier {
    val launcher=editorMenuLauncher(entries)
    val currentBeforeOpen by rememberUpdatedState(beforeOpen)
    return onPointerEvent(PointerEventType.Press, pass=if(node) PointerEventPass.Initial else PointerEventPass.Main) { event ->
        if (event.buttons.isSecondaryPressed && event.changes.none { it.isConsumed }) {
            event.changes.forEach { it.consume() }
            currentBeforeOpen()
            val position=MouseInfo.getPointerInfo().location
            launcher(position.x,position.y)
        }
    }
}

internal fun componentDisplayName(type: String): String = when (type) {
    "BoxZone" -> "Box Event Area"
    "CircleZone" -> "Circle Event Area"
    "StateMachine" -> "State Machine"
    else -> type
}

private fun componentGroup(type: String): String = when {
    type == "Transform" -> "Transform"
    type == "Camera2D" || type.contains("Sprite") || type.contains("Animator") -> "Rendering"
    type.contains("Collider") || type.contains("RigidBody") -> "Physics"
    sle.editor.model.isEventArea(type) -> "Event Areas"
    type.contains("Script") || type.contains("StateMachine") -> "Scripting"
    type.contains("Audio") -> "Audio"
    else -> "Other"
}

/** Persistent buttons survive untouched field focus/blur. Popup actions also require their original edit context. */
internal fun entityEditAction(state: EntityWorkspaceState, editable: () -> Boolean = { true },
    strictContext: Boolean = false, action: () -> Unit): () -> Unit {
    val document = state.document
    val version = state.editContextVersion
    val timeline = state.timeline
    return {
        val scene = timeline as? SceneWorkspaceState
        if (editable() && state.document === document && (!strictContext || state.editContextVersion == version) &&
            state.timeline === timeline && (scene == null || scene.entity(document.key) === state)) action()
    }
}

/** Component types not yet attached, grouped by area. New shapes start fitted to the sprite. */
internal fun addComponentEntries(state: EntityWorkspaceState, editable: () -> Boolean = { true }): List<EditorMenuEntry> {
    val hidden = setOf("WorldTransform", "ChunkMap")
    val groups = state.catalog.keys.filter { it !in state.document.components && it !in hidden }.sorted().groupBy(::componentGroup)
    return groups.map { (group, types) ->
        EditorMenuEntry(group, children = types.map { type ->
            EditorMenuEntry(componentDisplayName(type), enabled = editable(), action = entityEditAction(state, editable, strictContext = true) {
                try {
                    val added = state.document.addEditorComponent(type, state.catalog)
                    val candidate = added.fitShapeToSprite(type) ?: added
                    state.publishPropertyCandidate(candidate, "Add $type")
                    state.selectedComponent = null
                    state.inspectorSections.setExpanded(state.document.key, type, true)
                } catch (failure: Exception) { state.error = failure.message ?: "Could not add $type" }
            })
        })
    }
}

/** Fits all geometry atomically; invalid input leaves the authored shape and history untouched. */
internal fun fitShapeToSprite(state: EntityWorkspaceState, type: String, editable: () -> Boolean = { true }): Boolean {
    if (!editable()) return false
    return try {
        val candidate = state.document.fitShapeToSprite(type) ?: return false
        val fields = state.catalog[type]?.fields.orEmpty()
        if (fields.any { (path, descriptor) -> descriptor.readOnly &&
                path.firstOrNull() in setOf("size", "radius", "offset") }) return false
        state.publishPropertyCandidate(candidate, "Fit $type to sprite")
        true
    } catch (failure: IllegalArgumentException) {
        state.error = failure.message ?: "Could not Fit $type"
        false
    }
}

internal fun entityCreationMenu(state: EntityWorkspaceState, embedded: Boolean = false, projectAssets: sle.editor.project.assets.ProjectAssetCommands?=null, editable: () -> Boolean = { true }): List<EditorMenuEntry> {
    fun attempt(action: () -> Unit) {
        try { state.endGesture(); action() } catch (failure: Exception) { state.error = failure.message ?: "Operation failed" }
    }
    val components = addComponentEntries(state, editable)
    return buildList {
        add(EditorMenuEntry("Add Component", enabled = components.isNotEmpty(), children = components))
        add(EditorMenuEntry("Import", children = listOf(EditorMenuEntry("Assets…") {
            if(projectAssets!=null) projectAssets.importFiles() else {
            val files = EditorFilePicker.openMany("Import Assets", EditorFileLocations.folder(EditorFileLocations.Kind.Asset))
            if (files.isNotEmpty()) {
                EditorFileLocations.remember(EditorFileLocations.Kind.Asset, files.first())
                attempt { files.forEach { state.importAsset(it.toPath()) } }
            }
            }
        })))
        if (!embedded) add(EditorMenuEntry("New", children = listOf(EditorMenuEntry("Entity") {
            if (confirmDiscard(state)) state.newDocument()
        })))
    }
}

/** Apply needs a project scene and a selected entity inside a linked prefab instance. */
internal fun canApplyToPrefab(state: SceneWorkspaceState) = state.projectApplyPrefab!=null && state.selection.size==1 && state.linkedInstance(state.selectedKey)!=null
internal fun applySelectionToPrefab(state: SceneWorkspaceState) { state.selectedKey?.let { key -> state.projectApplyPrefab?.invoke(key) } }

/**
 * What Create → Tile Layer offers: the project's readable tilesets as (menu label, tileset
 * reference) and the actions behind them. Null when no project scene is open.
 */
internal data class TileLayerCreation(
    val tilesets: List<Pair<String, String>>,
    /** Adds a layer using the tileset reference, makes it active and opens the Tile Palette. */
    val create: (String) -> Unit,
    /** New Tileset…: the same flow as the Asset Browser's button. */
    val newTileset: () -> Unit,
    val newMap: (() -> Unit)? = null,
    val existingMap: (() -> Unit)? = null,
    val convertLegacy: (() -> Unit)? = null,
)
internal val LocalTileLayerCreation = compositionLocalOf<TileLayerCreation?> { null }
internal const val TILE_LAYER_UNAVAILABLE = "Open a project scene to add tile layers"

private fun tileLayerMenu(tileLayers: TileLayerCreation?, attempt: (() -> Unit) -> Unit, editableNow: () -> Boolean): EditorMenuEntry =
    if (tileLayers?.newMap != null) EditorMenuEntry("Tilemap", children = listOfNotNull(
        EditorMenuEntry("New Tilemap…") { attempt { if (editableNow()) tileLayers.newMap.invoke() } },
        tileLayers.existingMap?.let { place -> EditorMenuEntry("Place Existing Tilemap…") { attempt { if (editableNow()) place() } } },
        tileLayers.convertLegacy?.let { convert -> EditorMenuEntry("Convert Legacy Tile Layers…") { attempt { if (editableNow()) convert() } } },
    ))
    else if (tileLayers == null) EditorMenuEntry("Tilemap", false, help = TILE_LAYER_UNAVAILABLE)
    else EditorMenuEntry("Tile Layer", children = tileLayers.tilesets.map { (label, reference) ->
        EditorMenuEntry(label, help = "Add a tile layer that paints with $label") {
            attempt { if (editableNow()) tileLayers.create(reference) }
        }
    } + listOfNotNull(EditorMenuEntry.Separator.takeIf { tileLayers.tilesets.isNotEmpty() },
        EditorMenuEntry("New Tileset…", help = "Slice a texture into a tileset", action = tileLayers.newTileset)))

internal fun sceneCreationMenu(state: SceneWorkspaceState, editable: Boolean, onEditEntity: ((String)->Unit)? = null, position: sle.editor.viewport.Vec2?=null, editableNow: () -> Boolean = { editable },
    tileLayers: TileLayerCreation? = null): List<EditorMenuEntry> {
    fun attempt(action: () -> Unit) {
        if(!editable) return
        try { state.selected?.endGesture(); action() } catch (failure: Exception) { state.error = failure.message ?: "Scene operation failed" }
    }
    // Entity actions are the registered commands, so labels and shortcuts match the Edit menu.
    val commands=sle.editor.command.CommandContext(scene=state,editable=editable)
    return listOf(
        // Ordinary entities are edited in the Inspector; components can also be added from here.
        // Components are added to one entity; multi-object editing is a later roadmap item.
        (state.selected?.takeIf { editable && state.selection.size==1 }?.let { addComponentEntries(it, editableNow) } ?: emptyList()).let { components ->
            EditorMenuEntry("Add Component",components.isNotEmpty(),children=components,
                help=if(state.selection.size>1) SINGLE_ENTITY_REASON else null)
        },
        sle.editor.command.EditCommands.duplicate.menuEntry(commands),
        sle.editor.command.EditCommands.delete.menuEntry(commands),
        sle.editor.command.EditCommands.applyToPrefab.menuEntry(commands),
        EditorMenuEntry.Separator,
        EditorMenuEntry("Create", editable, listOf(
            EditorMenuEntry("Entity", children = listOf(
                EditorMenuEntry("At Scene Root") { attempt { state.addEntity(position) } },
                EditorMenuEntry("As Child", state.selected != null) { attempt { state.addChild(position) } },
            )),
            *listOf("Sprite" to "SpriteRenderer", "Box Collider" to "BoxCollider", "Circle Collider" to "CircleCollider",
                "Box Event Area" to "BoxZone", "Circle Event Area" to "CircleZone").map { (label,type) ->
                EditorMenuEntry(label, children=listOf(
                    EditorMenuEntry("At Scene Root") { attempt { state.addPreset(type,position=position) } },
                    EditorMenuEntry("As Child",state.selected!=null) { attempt { state.addPreset(type,true,position) } },
                ))
            }.toTypedArray(),
            EditorMenuEntry("Camera", children = listOf(
                EditorMenuEntry("At Scene Root") { attempt { state.addCamera(position=position) } },
                EditorMenuEntry("As Child", state.selected != null) { attempt { state.addCamera(true,position) } },
            )),
            tileLayerMenu(tileLayers, ::attempt, editableNow),
        )),
        EditorMenuEntry("Import", editable, listOf(EditorMenuEntry("Entity Template…") {
            EditorFilePicker.open("Import Entity Template", EditorFileLocations.folder(EditorFileLocations.Kind.Prefab),
                EditorFilePicker.Filter("Entity templates", "sleprefab", "json"))?.let { file ->
                EditorFileLocations.remember(EditorFileLocations.Kind.Prefab, file)
                attempt { state.importPrefab(file.toPath()) }
            }
        })),
        EditorMenuEntry("New", editable, listOf(EditorMenuEntry("Scene") {
            if (confirmDiscardScene(state)) state.newScene()
        })),
    )
}
