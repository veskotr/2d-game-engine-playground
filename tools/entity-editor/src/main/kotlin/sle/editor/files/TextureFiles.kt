package sle.editor.files

import java.nio.file.Path
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter

internal fun textureAssetPath(selected: Path, projectRoot: Path = Path.of("")): String {
    val root = projectRoot.toAbsolutePath().normalize()
    val image = selected.toAbsolutePath().normalize()
    return (if (image.startsWith(root)) root.relativize(image) else image).toString().replace('\\', '/')
}

internal fun chooseTexture(current: String, imageOnly: Boolean = true): Path? {
    val existing = current.takeIf { it.isNotBlank() }?.let { Path.of(it).toFile() }
    val chooser = JFileChooser(existing?.parentFile ?: Path.of("assets/textures").toFile()).apply {
        dialogTitle = if (imageOnly) "Choose entity texture" else "Choose component asset"
        fileSelectionMode = JFileChooser.FILES_ONLY
        if (imageOnly) fileFilter = FileNameExtensionFilter("Images (PNG, JPEG, BMP, TGA, GIF, PSD, HDR, PIC, PNM)",
            "png", "jpg", "jpeg", "bmp", "tga", "gif", "psd", "hdr", "pic", "pnm", "ppm", "pgm")
        if (existing?.isFile == true) selectedFile = existing
    }
    return if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) chooser.selectedFile.toPath() else null
}
