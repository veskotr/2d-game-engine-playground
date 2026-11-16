package sle.editor.smoke

import sle.editor.ui.*
import sle.editor.ui.theme.*
import androidx.compose.ui.graphics.toArgb
import java.awt.*
import java.nio.file.Path
import javax.imageio.ImageIO
import javax.swing.*

/**
 * Real modal Swing models, screenshots and cancel/accept results in both themes. Runs on EDT.
 * Dialogs are opened exactly as production code does (no explicit parent) and must be
 * owned by the editor window, use the editor chooser UI and carry a themed caption.
 */
internal fun runAuxiliaryAppearanceSmoke(owner: Window) {
    check(SwingUtilities.isEventDispatchThread())
    fun descendants(c: Component): List<Component> = listOf(c)+(if(c is Container) c.components.flatMap(::descendants) else emptyList())
    val report=Path.of(System.getProperty("sle.smokeReport")).parent
    val robot=Robot()
    owner.toFront()
    for(mode in EditorThemeMode.entries) {
        applyEditorSwingAppearance(EditorAppearance(mode=mode))
        var failure: Throwable?=null
        fun answer(name: String,action: (JDialog)->Unit): Timer = Timer(1000) {
            val dialog=Window.getWindows().filterIsInstance<JDialog>().lastOrNull { it.isShowing }
            if(dialog!=null) {
                try {
                    check(dialog.owner===owner) { "$name dialog is not owned by the editor window: ${dialog.owner}" }
                    check(dialog.contentPane.background.rgb==editorColors(mode).surface.toArgb())
                    EditorWindowChrome.darkCaption(dialog)?.let { dark ->
                        check(dark==(mode==EditorThemeMode.Dark)) { "$name caption is not themed for $mode" }
                    }
                    ImageIO.write(robot.createScreenCapture(Rectangle(dialog.locationOnScreen,dialog.size)),"png",report.resolve("session5-${mode.name.lowercase()}-$name.png").toFile())
                    action(dialog)
                } catch(t: Throwable) { failure=t;dialog.dispose() }
            }
        }.apply { isRepeats=false;start() }
        answer("unsaved") { d -> descendants(d).filterIsInstance<JOptionPane>().single().value=JOptionPane.NO_OPTION }
        check(EditorDialogs.showConfirmDialog(null,"Discard unsaved scene changes?","Unsaved scene",JOptionPane.YES_NO_OPTION)==JOptionPane.NO_OPTION)
        failure?.let { throw it }
        answer("collision") { d ->
            val pane=descendants(d).filterIsInstance<JOptionPane>().single()
            check(pane.icon is EditorActionIcon) { "Message icon is the system icon" }
            pane.value="Rename"
        }
        check(EditorDialogs.showOptionDialog(null,"tree.png already exists. Replace retains existing asset IDs.","Import name conflict",JOptionPane.DEFAULT_OPTION,JOptionPane.QUESTION_MESSAGE,null,arrayOf("Rename","Skip","Replace"),"Rename")==0)
        failure?.let { throw it }
        answer("input") { d ->
            descendants(d).filterIsInstance<JTextField>().single().text="new-folder"
            descendants(d).filterIsInstance<JButton>().first { it.text=="OK" }.doClick()
        }
        check(EditorDialogs.showInputDialog(null,"New folder name")=="new-folder")
        failure?.let { throw it }
        answer("input-cancel") { d -> descendants(d).filterIsInstance<JButton>().first { it.text=="Cancel" }.doClick() }
        check(EditorDialogs.showInputDialog(null,"New folder name")==null)
        failure?.let { throw it }
        answer("file-chooser") { d ->
            val chooser=descendants(d).filterIsInstance<JFileChooser>().single()
            check(chooser.ui is EditorFileChooserUI) { "Chooser uses the system UI: ${chooser.ui}" }
            check(descendants(d).none { it.javaClass.name.contains("PlacesBar") }) { "System places bar is present" }
            check(descendants(d).filterIsInstance<JScrollBar>().all { it.ui is EditorScrollBarUI }) { "System scrollbar in chooser" }
            chooser.cancelSelection()
        }
        check(EditorFileChooser(report.toFile()).showOpenDialog(null)==JFileChooser.CANCEL_OPTION)
        failure?.let { throw it }
    }
    applyEditorSwingAppearance(EditorAppearance())
    println("AUXILIARY_APPEARANCE_SMOKE_PASS owner/chooser-ui/icons/caption")
}
