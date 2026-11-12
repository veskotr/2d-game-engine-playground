package sle.editor.ui

import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.LocalDensity
import java.awt.Color
import javax.swing.*
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
) {
    val colors=MaterialTheme.colors
    val appearance=MenuAppearance(
        Color(colors.surface.toArgb(),true),Color(colors.onSurface.toArgb(),true),
        Color(colors.primary.copy(alpha=0.18f).compositeOver(colors.surface).toArgb(),true),
        Color(colors.primary.toArgb(),true),LocalDensity.current.density,
    )
    val currentGroups by rememberUpdatedState(groups)
    val currentBounds by rememberUpdatedState(onMenuBounds)
    val titleLabel=remember(appearance) { JLabel().apply {
        isOpaque=true;background=appearance.surface
        foreground=appearance.foreground;font=appearance.font
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
            border=BorderFactory.createMatteBorder(0,0,1,0,appearance.selection)
            groups.forEach { (label,_) ->
                add(JMenu(label).apply {
                    setUI(EditorSubmenuUI(appearance))
                    font=appearance.font;foreground=appearance.foreground;background=appearance.surface
                    isOpaque=true
                    mnemonic=label.first().uppercaseChar().code
                    border=BorderFactory.createEmptyBorder(4,8,4,8)
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
    onScale: (Float)->Unit,onExit: ()->Unit,
): List<Pair<String,List<EditorMenuEntry>>> {
    fun attempt(action: ()->Unit) { try { action() } catch(failure: Exception) { if(entityMode && !linked) entity?.error=failure.message else scene?.error=failure.message } }
    val sceneDocument=!entityMode || linked
    fun save(saveAs: Boolean) { if(sceneDocument) scene?.let { sceneFileAction(it,true,saveAs) } else entity?.let { entityFileAction(it,true,saveAs) } }
    val creation=if(entityMode) entity?.let { entityCreationMenu(it,linked) } ?: emptyList() else scene?.let { sceneCreationMenu(it,editable) { key -> onEntity(key) } } ?: emptyList()
    return listOf(
        "File" to listOf(
            EditorMenuEntry("New",editable,listOf(
                EditorMenuEntry("Scene") { scene?.let { if(confirmDiscardScene(it)) { it.newScene();onScene(false) } } },
                EditorMenuEntry("Prefab") { prefab?.let { if(confirmDiscard(it)) { it.newDocument();onEntity(null) } } },
            )),
            EditorMenuEntry("Open",editable,listOf(
                EditorMenuEntry("Scene…") { scene?.let { if(sceneFileAction(it,false)) onScene(false) else if(entityMode) entity?.error=it.error } },
                EditorMenuEntry("Prefab…") { prefab?.let { if(entityFileAction(it,false)) onEntity(null) else if(sceneDocument) scene?.error=it.error } },
            )),
            EditorMenuEntry("Save",editable && (!entityMode || entity!=null)) { save(false) },
            EditorMenuEntry("Save As…",editable && (!entityMode || entity!=null)) { save(true) },
            EditorMenuEntry("Exit",action=onExit),
        ),
        "Edit" to listOf(
            EditorMenuEntry("Undo",editable && entity?.canUndo==true) { entity?.undo() },
            EditorMenuEntry("Redo",editable && entity?.canRedo==true) { entity?.redo() },
            EditorMenuEntry("Scene Undo",editable && sceneDocument && scene?.canUndoStructure==true) { attempt { scene?.undoStructure() } },
            EditorMenuEntry("Scene Redo",editable && sceneDocument && scene?.canRedoStructure==true) { attempt { scene?.redoStructure() } },
            EditorMenuEntry("Edit Entity",editable && sceneDocument && scene?.selected!=null) { scene?.selectedKey?.let(onEntity) },
            EditorMenuEntry("Duplicate Subtree",editable && sceneDocument && scene?.selected!=null) { attempt { scene?.duplicateSelected() } },
            EditorMenuEntry("Delete Entity",editable && sceneDocument && scene?.selected!=null) { attempt { scene?.removeSelected() } },
        ),
        "Create" to creation.flatMap { when(it.label) { "Create" -> it.children.map { child -> child.copy(enabled=it.enabled && child.enabled) };"Add Component","Import" -> listOf(it);else -> emptyList() } },
        "View" to listOf(
            EditorMenuEntry("Scene") { onScene(false) },EditorMenuEntry("Game") { onScene(true) },
            EditorMenuEntry("Prefab Workspace",editable) { onEntity(null) },
            EditorMenuEntry("Fit",editable,action=onFit),
            EditorMenuEntry("UI Scale",children=listOf(0.75f,0.85f,1f).map { scale -> EditorMenuEntry("${(scale*100).toInt()}%") { onScale(scale) } }),
        ),
        "Help" to listOf(
            EditorMenuEntry("Controls…") { JOptionPane.showMessageDialog(null,"Scene: middle-drag or Alt+left-drag to pan; wheel to zoom.\nRight-click an entity and choose Edit for component editing.\nDrag handles to edit; Shift snaps; Escape cancels.\nCtrl+Z / Ctrl+Y undo and redo entity edits.\nGame shows the active camera. Play runs the scene; Pause freezes it.\nStop discards runtime changes. Click Game to focus input.","Editor Controls",JOptionPane.INFORMATION_MESSAGE) },
            EditorMenuEntry("About SLE Editor…") { JOptionPane.showMessageDialog(null,"SLE Editor\nKotlin Compose Desktop · SLE native renderer\nScene and prefab authoring", "About",JOptionPane.INFORMATION_MESSAGE) },
        ),
    )
}
