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
import sle.editor.command.*

/** Install a real window menu bar, including native menu traversal and Alt mnemonics. */
@Composable
internal fun EditorMenuStrip(
    groups: List<Pair<String,List<EditorMenuEntry>>>,window: JFrame,title: String,
    onMenuBounds: (String,Rect)->Unit={_,_->},
) {
    val editorAppearance=sle.editor.ui.theme.LocalEditorAppearance.current
    val appearance=MenuAppearance.of(editorAppearance.mode,editorAppearance.density,editorAppearance.scale)
    val currentGroups by rememberUpdatedState(groups)
    val currentBounds by rememberUpdatedState(onMenuBounds)
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

/** The window menus. Command entries take label, enabled state and shortcut from the registry. */
internal fun editorApplicationMenus(
    ctx: CommandContext,
    onScale: (Float)->Unit,onExit: ()->Unit,projectEntries: List<EditorMenuEntry> = emptyList(),assetEntries: List<EditorMenuEntry> = emptyList(),projectAssets: sle.editor.project.assets.ProjectAssetCommands?=null,projectDocuments: sle.editor.project.ProjectDocumentCommands?=null,
    onTheme: (sle.editor.ui.theme.EditorThemeMode)->Unit = {},
    onDensity: (sle.editor.ui.theme.EditorDensity)->Unit = {},
    keymap: Keymap = standardKeymap,
): List<Pair<String,List<EditorMenuEntry>>> {
    fun entry(command: EditorCommand) = command.menuEntry(ctx,keymap)
    val entityMode=ctx.mode==sle.editor.viewport.ViewportMode.Entity
    val scene=ctx.scene;val entity=ctx.entity
    val creation=if(entityMode) entity?.let { entityCreationMenu(it,ctx.linked,projectAssets) } ?: emptyList() else scene?.let { sceneCreationMenu(it,ctx.editable,onEditEntity=ctx.actions::editEntity) } ?: emptyList()
    val files=ctx.editable && !ctx.projectBusy
    return listOf(
        "File" to projectEntries + listOf(
            EditorMenuEntry("New",files,listOfNotNull(
                ctx.actions.project?.let { entry(DocumentCommands.newProject) },
                entry(DocumentCommands.newScene),entry(DocumentCommands.newPrefab),
            )),
            EditorMenuEntry("Open",files,listOfNotNull(
                ctx.actions.project?.let { entry(DocumentCommands.openProject) },
                entry(DocumentCommands.openScene),entry(DocumentCommands.openPrefab),
            )),
            entry(DocumentCommands.save),
            entry(DocumentCommands.saveAs),
            EditorMenuEntry("Export Package…",ctx.editable && projectDocuments!=null) { projectDocuments?.exportPackage(if(ctx.sceneDocument) sle.editor.project.ProjectDocumentKind.Scene else sle.editor.project.ProjectDocumentKind.Prefab) },
            EditorMenuEntry.Separator,
            EditorMenuEntry("Exit",action=onExit),
        ),
        "Edit" to listOf(
            EditCommands.undo.menuEntry(ctx,keymap,EditCommands.undoLabel(ctx)),EditCommands.redo.menuEntry(ctx,keymap,EditCommands.redoLabel(ctx)),
            EditorMenuEntry.Separator,
            entry(EditCommands.selectAll),entry(EditCommands.editEntity),entry(EditCommands.duplicate),entry(EditCommands.delete),entry(EditCommands.applyToPrefab),
            EditorMenuEntry.Separator,
            entry(PlayCommands.play),entry(PlayCommands.pause),entry(PlayCommands.stop),entry(PlayCommands.runProject),
        ),
        "Create" to creation.flatMap { when(it.label) { "Create" -> it.children.map { child -> child.copy(enabled=it.enabled && child.enabled) };"Add Component","Import" -> listOf(it);else -> emptyList() } },
        "View" to listOf(
            EditorMenuEntry("Scene") { ctx.actions.showScene(false) },EditorMenuEntry("Game") { ctx.actions.showScene(true) },
            EditorMenuEntry("Prefab Workspace",ctx.editable) { ctx.actions.openPrefabWorkspace() },
            entry(ViewCommands.fit),
            EditorMenuEntry.Separator,
            EditorMenuEntry("Appearance",children=sle.editor.ui.theme.EditorThemeMode.entries.map { mode -> EditorMenuEntry(mode.name) { onTheme(mode) } }),
            EditorMenuEntry("Density",children=sle.editor.ui.theme.EditorDensity.entries.map { density -> EditorMenuEntry(density.name) { onDensity(density) } }),
            EditorMenuEntry("UI Scale",children=listOf(0.75f,0.85f,1f).map { scale -> EditorMenuEntry("${(scale*100).toInt()}%") { onScale(scale) } }),
        ) + assetEntries,
        "Help" to listOf(
            entry(ViewCommands.keyboardShortcuts),
            EditorMenuEntry("About SLE Editor…") { EditorDialogs.showMessageDialog(null,"SLE Editor\nKotlin Compose Desktop · SLE native renderer\nScene and prefab authoring", "About",JOptionPane.INFORMATION_MESSAGE) },
        ),
    )
}

/** Help → Keyboard Shortcuts, generated from the command registry. */
internal fun showKeyboardShortcuts(keymap: Keymap = standardKeymap) {
    EditorDialogs.showMessageDialog(null,JLabel(keyboardShortcutsHtml(keymap)),"Keyboard Shortcuts",JOptionPane.PLAIN_MESSAGE)
}
