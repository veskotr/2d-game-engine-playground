package sle.editor.ui

import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.LocalDensity
import java.awt.Color
import javax.swing.*
import kotlin.math.roundToInt
import javax.swing.event.MenuEvent
import javax.swing.event.MenuListener
import javax.swing.plaf.basic.BasicMenuBarUI
import sle.editor.model.EntityWorkspaceState
import sle.editor.model.SceneWorkspaceState

/** Install a real window menu bar, including native menu traversal and Alt mnemonics. */
@Composable
internal fun EditorMenuStrip(
    groups: List<Pair<String,List<EditorMenuEntry>>>,window: JFrame,title: String,
    onMenuBounds: (String,Rect)->Unit={_,_->},
    shortcutWindows: ()->Collection<java.awt.Window> = { listOf(window) },
) {
    val editorAppearance=sle.editor.ui.theme.LocalEditorAppearance.current
    val appearance=MenuAppearance.of(editorAppearance.mode,editorAppearance.density,editorAppearance.scale)
    val currentGroups by rememberUpdatedState(groups)
    val currentBounds by rememberUpdatedState(onMenuBounds)
    val currentWindows by rememberUpdatedState(shortcutWindows)
    DisposableEffect(window) {
        val focus=java.awt.KeyboardFocusManager.getCurrentKeyboardFocusManager()
        val shortcut=SaveShortcut(
            { event -> javax.swing.SwingUtilities.getWindowAncestor(event.component) in currentWindows() },
            { java.awt.Window.getWindows().any { it is java.awt.Dialog && it.isModal && it.isVisible } },
            { currentGroups.firstOrNull { it.first=="File" }?.second?.firstOrNull { it.label=="Save" } },
        )
        focus.addKeyEventDispatcher(shortcut)
        onDispose { focus.removeKeyEventDispatcher(shortcut) }
    }
    val titleLabel=remember(appearance) { JLabel().apply {
        isOpaque=true;background=appearance.surface
        foreground=appearance.secondary;font=appearance.secondaryFont
        border=BorderFactory.createEmptyBorder(0,12,0,8)
    } }
    val bar=remember(window,appearance) {
        object: JMenuBar() {
            override fun paintComponent(graphics: java.awt.Graphics) {
                graphics.color=appearance.surface
                graphics.fillRect(0,0,width,height)
            }
        }.apply {
            setUI(BasicMenuBarUI())
            isOpaque=true;background=appearance.surface;foreground=appearance.foreground
            border=BorderFactory.createMatteBorder(0,0,1,0,appearance.separator)
            groups.forEach { (label,_) ->
                add(JMenu(label).apply {
                    setUI(EditorSubmenuUI(appearance))
                    font=appearance.font;foreground=appearance.foreground;background=appearance.surface
                    isOpaque=true
                    mnemonic=label.first().uppercaseChar().code
                    val v=(4*appearance.scale).roundToInt();val h=(8*appearance.scale).roundToInt()
                    border=BorderFactory.createEmptyBorder(v,h,v,h)
                    styleEditorPopup(popupMenu,appearance)
                    addMenuListener(object: MenuListener {
                        override fun menuSelected(event: MenuEvent) {
                            removeAll()
                            val entries=currentGroups.firstOrNull { it.first==label }?.second.orEmpty()
                            buildEditorPopup(entries,appearance).components.toList().forEach { add(it) }
                        }
                        override fun menuDeselected(event: MenuEvent) {}
                        override fun menuCanceled(event: MenuEvent) {}
                    })
                })
            }
            add(Box.createHorizontalGlue().apply {
                (this as JComponent).isOpaque=true;background=appearance.surface
            })
            add(titleLabel)
        }
    }
    val originalBar=remember(window) { window.jMenuBar }
    DisposableEffect(window,bar) {
        var active=true
        // Native layout can synchronously resize Skia; defer until composition finishes.
        SwingUtilities.invokeLater {
            if(active) { window.jMenuBar=bar;window.validate();window.repaint() }
        }
        onDispose {
            active=false
            SwingUtilities.invokeLater {
                if(window.jMenuBar===bar) { window.jMenuBar=originalBar;window.validate();window.repaint() }
            }
        }
    }
    SideEffect {
        if(titleLabel.text!=title) titleLabel.text=title
        SwingUtilities.invokeLater {
            bar.components.filterIsInstance<JMenu>().filter { it.isShowing }.forEach { menu ->
                val point=SwingUtilities.convertPoint(menu,0,0,window.contentPane)
                currentBounds(menu.text,Rect(point.x.toFloat(),point.y.toFloat(),(point.x+menu.width).toFloat(),(point.y+menu.height).toFloat()))
            }
        }
    }
}

internal fun editorApplicationMenus(
    scene: SceneWorkspaceState?,entity: EntityWorkspaceState?,prefab: EntityWorkspaceState?,
    entityMode: Boolean,linked: Boolean,editable: Boolean,
    onScene: (Boolean)->Unit,onEntity: (String?)->Unit,onFit: ()->Unit,
    onScale: (Float)->Unit,onExit: ()->Unit,projectEntries: List<EditorMenuEntry> = emptyList(),assetEntries: List<EditorMenuEntry> = emptyList(),projectAssets: sle.editor.project.assets.ProjectAssetCommands?=null,projectDocuments: sle.editor.project.ProjectDocumentCommands?=null,
    onTheme: (sle.editor.ui.theme.EditorThemeMode)->Unit = {},
    onDensity: (sle.editor.ui.theme.EditorDensity)->Unit = {},
    projectCommands: sle.editor.project.ProjectCommands? = null,projectBusy: Boolean=false,
): List<Pair<String,List<EditorMenuEntry>>> {
    fun attempt(action: ()->Unit) { try { action() } catch(failure: Exception) { if(entityMode && !linked) entity?.error=failure.message else scene?.error=failure.message } }
    val sceneDocument=!entityMode || linked
    fun save(saveAs: Boolean) { if(sceneDocument) scene?.let { sceneFileAction(it,true,saveAs) } else entity?.let { entityFileAction(it,true,saveAs) } }
    val creation=if(entityMode) entity?.let { entityCreationMenu(it,linked,projectAssets) } ?: emptyList() else scene?.let { sceneCreationMenu(it,editable,onEditEntity={ key -> onEntity(key) }) } ?: emptyList()
    return listOf(
        "File" to projectEntries + listOf(
            EditorMenuEntry("New",editable && !projectBusy,listOfNotNull(
                projectCommands?.let { EditorMenuEntry("Project…",action=it.create) },
                EditorMenuEntry("Scene") { scene?.let { if(confirmDiscardScene(it)) { it.newScene();onScene(false) } } },
                EditorMenuEntry("Prefab") { prefab?.let { if(confirmDiscard(it)) { it.newDocument();onEntity(null) } } },
            )),
            EditorMenuEntry("Open",editable && !projectBusy,listOfNotNull(
                projectCommands?.let { EditorMenuEntry("Project…",action=it.open) },
                EditorMenuEntry("Scene…") { scene?.let { if(sceneFileAction(it,false)) onScene(false) else if(entityMode) entity?.error=it.error } },
                EditorMenuEntry("Prefab…") { prefab?.let { if(entityFileAction(it,false)) onEntity(null) else if(sceneDocument) scene?.error=it.error } },
            )),
            EditorMenuEntry("Save",editable && !projectBusy && (!entityMode || entity!=null)) { save(false) },
            EditorMenuEntry("Save As…",editable && !projectBusy && (!entityMode || entity!=null)) { save(true) },
            EditorMenuEntry("Export Package…",editable && projectDocuments!=null) { projectDocuments?.exportPackage(if(sceneDocument) sle.editor.project.ProjectDocumentKind.Scene else sle.editor.project.ProjectDocumentKind.Prefab) },
            EditorMenuEntry("Exit",action=onExit),
        ),
        "Edit" to listOf(
            EditorMenuEntry("Undo",editable && entity?.canUndo==true) { entity?.undo() },
            EditorMenuEntry("Redo",editable && entity?.canRedo==true) { entity?.redo() },
            EditorMenuEntry("Scene Undo",editable && sceneDocument && scene?.canUndoStructure==true) { attempt { scene?.undoStructure() } },
            EditorMenuEntry("Scene Redo",editable && sceneDocument && scene?.canRedoStructure==true) { attempt { scene?.redoStructure() } },
            EditorMenuEntry("Edit Entity",editable && sceneDocument && scene?.selected!=null) { scene?.selectedKey?.let(onEntity) },
            EditorMenuEntry("Duplicate Subtree",editable && sceneDocument && scene?.selected!=null) { attempt { scene?.duplicateSelected() } },
            EditorMenuEntry("Delete Entity",editable && sceneDocument && scene?.selected!=null) { attempt { scene?.let { deleteSceneSelection(it,editable) } } },
            EditorMenuEntry("Apply Changes to Prefab",editable && sceneDocument && scene?.let(::canApplyToPrefab)==true) { attempt { scene?.let(::applySelectionToPrefab) } },
        ),
        "Create" to creation.flatMap { when(it.label) { "Create" -> it.children.map { child -> child.copy(enabled=it.enabled && child.enabled) };"Add Component","Import" -> listOf(it);else -> emptyList() } },
        "View" to listOf(
            EditorMenuEntry("Scene") { onScene(false) },EditorMenuEntry("Game") { onScene(true) },
            EditorMenuEntry("Prefab Workspace",editable) { onEntity(null) },
            EditorMenuEntry("Fit",editable,action=onFit),
            EditorMenuEntry("Appearance",children=sle.editor.ui.theme.EditorThemeMode.entries.map { mode -> EditorMenuEntry(mode.name) { onTheme(mode) } }),
            EditorMenuEntry("Density",children=sle.editor.ui.theme.EditorDensity.entries.map { density -> EditorMenuEntry(density.name) { onDensity(density) } }),
            EditorMenuEntry("UI Scale",children=listOf(0.75f,0.85f,1f).map { scale -> EditorMenuEntry("${(scale*100).toInt()}%") { onScale(scale) } }),
        ) + assetEntries,
        "Help" to listOf(
            EditorMenuEntry("Controls…") { EditorDialogs.showMessageDialog(null,"Scene: middle-drag or Alt+left-drag to pan; wheel to zoom.\nSelect an entity to edit it in the Inspector; right-click it to add components.\nDrag an entity onto an Assets folder to save it as a linked prefab; double-click a prefab to edit it.\nDrag handles to edit; Shift snaps; Escape cancels.\nCtrl+S saves the active scene or prefab.\nCtrl+Z / Ctrl+Y undo and redo entity edits.\nGame shows the active camera. Play runs the scene; Pause freezes it.\nStop discards runtime changes. Click Game to focus input.","Editor Controls",JOptionPane.INFORMATION_MESSAGE) },
            EditorMenuEntry("About SLE Editor…") { EditorDialogs.showMessageDialog(null,"SLE Editor\nKotlin Compose Desktop · SLE native renderer\nScene and prefab authoring", "About",JOptionPane.INFORMATION_MESSAGE) },
        ),
    )
}
