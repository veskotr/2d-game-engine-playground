package sle.editor.ui

import androidx.compose.ui.graphics.toArgb
import org.junit.Test
import kotlin.test.*
import javax.swing.*
import sle.editor.ui.theme.*

class EditorDialogAppearanceTest {
    @Test fun appearanceDoesNotChangeApplicationSwingDefaults() {
        SwingUtilities.invokeAndWait {
            val keys=listOf("Panel.background","TextField.foreground","Button.font","ToolTip.background")
            val before=keys.associateWith { UIManager.get(it) }
            for(mode in EditorThemeMode.entries) applyEditorSwingAppearance(EditorAppearance(mode=mode))
            assertEquals(before,keys.associateWith { UIManager.get(it) })
        }
    }
    @Test fun scopedRootsRefreshIncludingNewChooserChildren() {
        SwingUtilities.invokeAndWait {
            val root=JPanel()
            val field=JTextField("draft")
            root.add(field)
            EditorDialogAppearance.register(root)
            for(mode in EditorThemeMode.entries) {
                applyEditorSwingAppearance(EditorAppearance(mode=mode))
                val colors=editorColors(mode)
                assertEquals(colors.inputBackground.toArgb(),field.background.rgb)
                assertEquals(colors.selectedBackground.toArgb(),field.selectionColor.rgb)
                assertEquals("draft",field.text)
                val added=JTextField("new")
                root.add(added)
                assertEquals(colors.inputBackground.toArgb(),added.background.rgb)
            }
        }
    }
    @Test fun chooserComboRendererUsesEditorColors() {
        SwingUtilities.invokeAndWait {
            applyEditorSwingAppearance(EditorAppearance(mode=EditorThemeMode.Dark))
            val combo=JComboBox(arrayOf("Images", "All files"))
            EditorDialogAppearance.register(combo)
            val renderer=combo.renderer.getListCellRendererComponent(JList<String>(),"Images",-1,false,false)
            assertEquals(editorColors(EditorThemeMode.Dark).surface.toArgb(),renderer.background.rgb)
            assertEquals(editorColors(EditorThemeMode.Dark).textPrimary.toArgb(),renderer.foreground.rgb)
        }
    }
    @Test fun editorChooserReplacesSystemUiButKeepsFilterSemantics() {
        SwingUtilities.invokeAndWait {
            val before=UIManager.getLookAndFeel()
            val chooser=EditorFileChooser()
            val system=JFileChooser()
            assertTrue(chooser.ui is EditorFileChooserUI)
            assertEquals(1,chooser.choosableFileFilters.count { it==chooser.acceptAllFileFilter })
            // Filter behavior matches a plain JFileChooser step by step.
            fun state(c: JFileChooser)=Triple(c.choosableFileFilters.map { it.description },c.fileFilter?.description,c.isAcceptAllFileFilterUsed)
            assertEquals(state(system),state(chooser))
            for(c in listOf(chooser,system)) c.addChoosableFileFilter(javax.swing.filechooser.FileNameExtensionFilter("Images","png"))
            assertEquals(state(system),state(chooser))
            for(c in listOf(chooser,system)) c.fileFilter=c.choosableFileFilters.last()
            assertEquals(state(system),state(chooser))
            for(c in listOf(chooser,system)) c.isAcceptAllFileFilterUsed=false
            assertEquals(state(system),state(chooser))
            assertFalse(chooser.acceptAllFileFilter in chooser.choosableFileFilters)
            assertSame(before,UIManager.getLookAndFeel())
            val folder=java.nio.file.Files.createTempDirectory("chooser-icons").toFile()
            assertTrue(chooser.ui.getFileView(chooser).getIcon(folder) is EditorAssetIcon)
            assertTrue(JFileChooser().ui !is EditorFileChooserUI)
        }
    }
    @Test fun chooserFileKindsUseEditorAssetIcons() {
        assertEquals("scene",EditorFileChooserUI.fileKind("Level.scene.json"))
        assertEquals("prefab",EditorFileChooserUI.fileKind("crate.sleprefab"))
        assertEquals("lua_script",EditorFileChooserUI.fileKind("main.LUA"))
        assertEquals("sprite",EditorFileChooserUI.fileKind("tile.png"))
        assertEquals("audio",EditorFileChooserUI.fileKind("jump.ogg"))
        assertEquals("resource",EditorFileChooserUI.fileKind("notes.txt"))
    }
    @Test fun captionColorsAreEditorOwned() {
        assertEquals(0x332211,EditorWindowChrome.colorRef(androidx.compose.ui.graphics.Color(0xFF112233)))
    }
    @Test fun dialogOwnerPrefersExplicitParent() {
        SwingUtilities.invokeAndWait {
            val parent=JPanel()
            assertSame(parent,EditorDialogOwner.resolve(parent))
        }
    }
    @Test fun chooserShortcutSelectionRemainsVisible() {
        SwingUtilities.invokeAndWait {
            applyEditorSwingAppearance(EditorAppearance())
            val shortcut=JToggleButton("Desktop").apply { isSelected=true;setSize(120,60) }
            EditorDialogAppearance.register(shortcut)
            val image=java.awt.image.BufferedImage(120,60,java.awt.image.BufferedImage.TYPE_INT_RGB)
            val graphics=image.createGraphics()
            try { shortcut.paint(graphics) } finally { graphics.dispose() }
            assertEquals(editorColors(EditorThemeMode.Dark).selectedBackground.toArgb(),image.getRGB(5,5))
        }
    }
}
