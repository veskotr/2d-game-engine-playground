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
import sle.editor.model.SceneWorkspaceState
import sle.editor.viewport.GizmoShape
import javax.swing.filechooser.FileNameExtensionFilter

internal data class EditorMenuEntry(
    val label: String,
    val enabled: Boolean = true,
    val children: List<EditorMenuEntry> = emptyList(),
    val action: () -> Unit = {},
)

private val editorPopups=java.util.WeakHashMap<JPopupMenu,MenuAppearance>()

/** Refresh open popups without rebuilding their action listeners or current selection path. */
internal fun refreshEditorPopups(value: sle.editor.ui.theme.EditorAppearance) {
    for((popup,previous) in editorPopups.toMap()) {
        val theme=MenuAppearance.of(value.mode,value.density,previous.scale)
        styleEditorPopup(popup,theme)
        popup.components.filterIsInstance<JMenuItem>().forEach { item ->
            if(item is JMenu) item.setUI(EditorSubmenuUI(theme)) else item.setUI(EditorMenuItemUI(theme))
            item.background=theme.surface;item.foreground=theme.foreground;item.font=theme.font
            item.icon=EditorIcons.commandFallback(item.text,theme);item.disabledIcon=item.icon
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
    fun item(entry: EditorMenuEntry): JMenuItem {
        val result = if (entry.children.isEmpty()) JMenuItem(entry.label).apply {
            setUI(EditorMenuItemUI(appearance))
            addActionListener { entry.action() }
        } else JMenu(entry.label).apply {
            setUI(EditorSubmenuUI(appearance))
            entry.children.forEach { add(item(it)) }
            styleEditorPopup(popupMenu,appearance)
        }
        return result.apply {
            isOpaque=true
            isEnabled=entry.enabled
            background=appearance.surface;foreground=appearance.foreground;font=appearance.font
            icon=EditorIcons.commandFallback(entry.label,appearance)
            disabledIcon=icon
            iconTextGap=(8*appearance.scale).roundToInt()
            border=BorderFactory.createEmptyBorder((7*appearance.scale).roundToInt(),10,(7*appearance.scale).roundToInt(),12)
        }
    }
    return JPopupMenu().apply {
        styleEditorPopup(this,appearance)
        entries.forEach { add(item(it)) }
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

private fun componentGroup(type: String): String = when {
    type == "Transform" -> "Transform"
    type == "Camera2D" || type.contains("Sprite") || type.contains("Animator") -> "Rendering"
    type.contains("Collider") || type.contains("RigidBody") -> "Physics"
    type.contains("Zone") -> "Zones"
    type.contains("Script") || type.contains("StateMachine") -> "Scripting"
    type.contains("Audio") -> "Audio"
    else -> "Other"
}

internal fun entityCreationMenu(state: EntityWorkspaceState, embedded: Boolean = false, projectAssets: sle.editor.project.assets.ProjectAssetCommands?=null): List<EditorMenuEntry> {
    fun attempt(action: () -> Unit) {
        try { state.endGesture(); action() } catch (failure: Exception) { state.error = failure.message ?: "Operation failed" }
    }
    val groups = state.catalog.keys.filter { it !in state.document.components }.sorted().groupBy(::componentGroup)
    val components = groups.map { (group, types) ->
        EditorMenuEntry(group, children = types.map { type ->
            EditorMenuEntry(type) { attempt {
                state.addComponent(type)
                state.selectedComponent = type
                state.selectedTarget = if (type in GizmoShape.targets) type else "Entity"
            } }
        })
    }
    return buildList {
        add(EditorMenuEntry("Add Component", enabled = components.isNotEmpty(), children = components))
        add(EditorMenuEntry("Import", children = listOf(EditorMenuEntry("Assets…") {
            if(projectAssets!=null) projectAssets.importFiles() else {
            val chooser = EditorFileChooser().apply { dialogTitle = "Import assets"; isMultiSelectionEnabled = true }
            if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION)
                attempt { chooser.selectedFiles.forEach { state.importAsset(it.toPath()) } }
            }
        })))
        if (!embedded) add(EditorMenuEntry("New", children = listOf(EditorMenuEntry("Entity") {
            if (confirmDiscard(state)) state.newDocument()
        })))
    }
}

internal fun sceneCreationMenu(state: SceneWorkspaceState, editable: Boolean, onEditEntity: ((String)->Unit)? = null): List<EditorMenuEntry> {
    fun attempt(action: () -> Unit) {
        try { state.selected?.endGesture(); action() } catch (failure: Exception) { state.error = failure.message ?: "Scene operation failed" }
    }
    return listOf(
        EditorMenuEntry("Edit",editable && state.selected!=null && onEditEntity!=null) { state.selectedKey?.let { onEditEntity?.invoke(it) } },
        EditorMenuEntry("Create", editable, listOf(
            EditorMenuEntry("Entity", children = listOf(
                EditorMenuEntry("At Scene Root") { attempt { state.addEntity() } },
                EditorMenuEntry("As Child", state.selected != null) { attempt { state.addChild() } },
            )),
            EditorMenuEntry("Camera", children = listOf(
                EditorMenuEntry("At Scene Root") { attempt { state.addCamera() } },
                EditorMenuEntry("As Child", state.selected != null) { attempt { state.addCamera(true) } },
            )),
        )),
        EditorMenuEntry("Import", editable, listOf(EditorMenuEntry("Prefab…") {
            val chooser = EditorFileChooser("assets/prefabs").apply {
                fileFilter = FileNameExtensionFilter("Prefabs (*.sleprefab, *.json)", "sleprefab", "json")
            }
            if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION)
                attempt { state.importPrefab(chooser.selectedFile.toPath()) }
        })),
        EditorMenuEntry("Entity", editable && state.selected != null, listOf(
            EditorMenuEntry("Duplicate Subtree") { attempt { state.duplicateSelected() } },
        )),
        EditorMenuEntry("New", editable, listOf(EditorMenuEntry("Scene") {
            if (confirmDiscardScene(state)) state.newScene()
        })),
    )
}
