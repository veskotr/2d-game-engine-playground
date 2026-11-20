package sle.editor.ui

import javax.swing.JMenu
import javax.swing.JMenuItem
import javax.swing.SwingUtilities
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class EditorMenuTooltipTest {
    @Test
    fun menuItemsUseTheirLabelsUnlessTheyHaveSpecificHelp() {
        SwingUtilities.invokeAndWait {
            val menu = buildEditorPopup(listOf(
                EditorMenuEntry("Save"),
                EditorMenuEntry("Export", enabled = false, help = "Open a project to export it"),
                EditorMenuEntry("New", children = listOf(EditorMenuEntry("Scene"))),
            ))

            assertEquals("Save", (menu.getComponent(0) as JMenuItem).toolTipText)
            val export = menu.getComponent(1) as JMenuItem
            assertFalse(export.isEnabled)
            assertEquals("Open a project to export it", export.toolTipText)
            val submenu = menu.getComponent(2) as JMenu
            assertEquals("New", submenu.toolTipText)
            assertEquals("Scene", submenu.getItem(0).toolTipText)
        }
    }
}
