package sle.editor.smoke

import sle.editor.ui.*
import sle.editor.ui.theme.*
import androidx.compose.ui.graphics.toArgb
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.awt.*
import java.nio.file.Path
import javax.imageio.ImageIO
import javax.swing.*
import kotlin.coroutines.CoroutineContext

// Modal dialogs pump AWT events, so open them outside Compose's render callbacks.
private val dialogSmokeDispatcher = object : CoroutineDispatcher() {
    override fun dispatch(context: CoroutineContext, block: Runnable) = SwingUtilities.invokeLater(block)
}

/**
 * Real modal Swing models, screenshots and cancel/accept results in the current theme. Runs on EDT.
 * Dialogs are opened exactly as production code does (no explicit parent) and must be
 * owned by the editor window, use the editor chooser UI and carry a themed caption.
 */
internal suspend fun runAuxiliaryAppearanceSmoke(owner: Window, appearance: EditorAppearanceState) = withContext(dialogSmokeDispatcher) {
    check(SwingUtilities.isEventDispatchThread())
    fun descendants(c: Component): List<Component> = listOf(c)+(if(c is Container) c.components.flatMap(::descendants) else emptyList())
    val report=Path.of(System.getProperty("sle.smokeReport")).parent
    val robot=Robot()
    owner.toFront()
    val expected=appearance.value
    val mode=expected.mode
    withTimeout(5000) {
        while(EditorDialogAppearance.appearance!=expected) delay(20)
    }
    EditorWindowChrome.darkCaption(owner)?.let { dark ->
        check(dark==(mode==EditorThemeMode.Dark)) { "Editor caption is not themed for $mode" }
    }
    var failure: Throwable?=null
    fun answer(name: String,action: (JDialog)->Unit): Timer = Timer(1000) {
        val dialog=Window.getWindows().filterIsInstance<JDialog>().lastOrNull { it.isShowing }
        if(dialog!=null) {
            try {
                check(appearance.value==expected && EditorDialogAppearance.appearance==expected) { "$name changed the editor appearance" }
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
    // Editor prompts: the Compose dialog shell, answered through its handle once it is on screen.
    fun prompt(name: String,respond: (sle.editor.ui.dialog.PromptHandle)->Unit): Timer = Timer(1000) {
        val handle=sle.editor.ui.dialog.EditorPrompts.current
        val dialog=handle?.let(::promptWindow)
        if(handle!=null && dialog!=null) {
            try {
                check(appearance.value==expected && EditorDialogAppearance.appearance==expected) { "$name changed the editor appearance" }
                check((dialog as Dialog).owner===owner) { "$name prompt is not owned by the editor window: ${dialog.owner}" }
                EditorWindowChrome.darkCaption(dialog)?.let { dark ->
                    check(dark==(mode==EditorThemeMode.Dark)) { "$name caption is not themed for $mode" }
                }
                ImageIO.write(robot.createScreenCapture(Rectangle(dialog.locationOnScreen,dialog.size)),"png",report.resolve("session5-${mode.name.lowercase()}-$name.png").toFile())
                val surface=Color(editorColors(mode).surface.toArgb())
                val insets=dialog.insets
                val sample=robot.getPixelColor(dialog.locationOnScreen.x+insets.left+4,dialog.locationOnScreen.y+insets.top+4)
                check(sample==surface) { "$name prompt background $sample is not the $mode surface $surface" }
                respond(handle)
            } catch(t: Throwable) { failure=t;handle.answer(-1) }
        }
    }.apply { isRepeats=false;start() }
    prompt("unsaved") { it.answer("Cancel") }
    check(!sle.editor.ui.dialog.EditorPrompts.confirm("Unsaved Scene","Discard unsaved scene changes?","Discard",danger=true))
    failure?.let { throw it }
    prompt("collision") { it.answer("Rename") }
    check(sle.editor.ui.dialog.EditorPrompts.choose("Import name conflict","tree.png already exists. Replace retains existing asset IDs.",listOf("Rename","Skip","Replace"),cancel=null)==0)
    failure?.let { throw it }
    prompt("input") { it.answer("Create","new-folder") }
    check(sle.editor.ui.dialog.EditorPrompts.input("New Folder","Name",confirm="Create")=="new-folder")
    failure?.let { throw it }
    prompt("input-cancel") { it.answer("Cancel") }
    check(sle.editor.ui.dialog.EditorPrompts.input("New Folder","Name",confirm="Create")==null)
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
    println("AUXILIARY_APPEARANCE_SMOKE_PASS mode=$mode owner/chooser-ui/icons/caption")
}
