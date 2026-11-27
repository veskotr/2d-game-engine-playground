package sle.editor.ui

import java.awt.Dialog
import java.awt.FileDialog
import java.awt.Frame
import java.awt.Window
import java.io.File
import java.io.FilenameFilter
import javax.swing.SwingUtilities

/**
 * File pickers in the operating system's own dialog (`java.awt.FileDialog`: the native dialog
 * on Windows and macOS, GTK on Linux).
 *
 * Folder pickers stay on [EditorFileChooser]: `FileDialog` cannot pick a folder on Windows.
 * Import… also stays there, because it picks files and folders together.
 */
internal object EditorFilePicker {
    /** Shown as the dialog's file pattern; [extensions] have no dot. */
    data class Filter(val description: String, val extensions: List<String>) {
        constructor(description: String, vararg extensions: String) : this(description, extensions.toList())
        fun accepts(name: String) = extensions.any { name.endsWith(".$it", ignoreCase = true) }
    }
    enum class Mode { Open, OpenMany, Save }
    data class Request(val mode: Mode, val title: String, val directory: File?, val name: String?, val filter: Filter?)

    /** Answers the next requests instead of the OS dialog (smokes cannot drive native dialogs). */
    private val scripted = ArrayDeque<(Request) -> List<File>>()
    internal fun answerNext(respond: (Request) -> List<File>) = synchronized(scripted) { scripted.addLast(respond) }

    val images = Filter("Images", "png", "jpg", "jpeg", "bmp", "tga", "gif", "psd", "hdr", "pic", "pnm", "ppm", "pgm")

    fun open(title: String, directory: File?, filter: Filter? = null, name: String? = null): File? =
        show(Request(Mode.Open, title, directory, name, filter)).firstOrNull()
    fun openMany(title: String, directory: File?, filter: Filter? = null): List<File> =
        show(Request(Mode.OpenMany, title, directory, null, filter))
    /** The chosen file; [extension] (no dot) is appended when the typed name lacks [filter]'s extensions. */
    fun save(title: String, directory: File?, name: String, filter: Filter? = null, extension: String? = filter?.extensions?.firstOrNull()): File? =
        show(Request(Mode.Save, title, directory, name, filter)).firstOrNull()?.let { file ->
            if (extension == null || filter?.accepts(file.name) == true || file.name.endsWith(".$extension", true)) file
            else File(file.parentFile, "${file.name}.$extension")
        }

    private fun show(request: Request): List<File> {
        synchronized(scripted) { scripted.removeFirstOrNull() }?.let { return it(request) }
        check(SwingUtilities.isEventDispatchThread()) { "File pickers run on the event-dispatch thread" }
        val owner = EditorDialogOwner.resolve(null)?.let { it as? Window ?: SwingUtilities.getWindowAncestor(it) }
        val mode = if (request.mode == Mode.Save) FileDialog.SAVE else FileDialog.LOAD
        val dialog = when (owner) {
            is Frame -> FileDialog(owner, request.title, mode)
            is Dialog -> FileDialog(owner, request.title, mode)
            else -> FileDialog(null as Frame?, request.title, mode)
        }
        try {
            request.directory?.let { start -> dialog.directory = (if (start.isDirectory) start else start.parentFile)?.absolutePath }
            dialog.isMultipleMode = request.mode == Mode.OpenMany
            request.filter?.let { filter -> dialog.filenameFilter = FilenameFilter { _, name -> filter.accepts(name) } }
            // Windows ignores FilenameFilter; a pattern in the name box filters instead.
            val windows = System.getProperty("os.name").startsWith("Windows")
            dialog.file = request.name ?: request.filter?.takeIf { windows }?.extensions?.joinToString(";") { "*.$it" }
            dialog.isVisible = true
            return dialog.files.toList()
        } finally {
            dialog.dispose()
        }
    }
}
