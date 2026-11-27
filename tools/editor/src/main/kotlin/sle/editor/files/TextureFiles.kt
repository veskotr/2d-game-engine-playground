package sle.editor.files

import java.nio.file.Path

internal fun textureAssetPath(selected: Path, projectRoot: Path = Path.of("")): String {
    val root = projectRoot.toAbsolutePath().normalize()
    val image = selected.toAbsolutePath().normalize()
    return (if (image.startsWith(root)) root.relativize(image) else image).toString().replace('\\', '/')
}

internal fun chooseTexture(current: String, imageOnly: Boolean = true): Path? {
    val existing = current.takeIf { it.isNotBlank() }?.let { Path.of(it).toFile() }
    val chosen = sle.editor.ui.EditorFilePicker.open(if (imageOnly) "Choose Entity Texture" else "Choose Component Asset",
        sle.editor.ui.EditorFileLocations.folder(sle.editor.ui.EditorFileLocations.Kind.Texture, existing?.toPath()),
        if (imageOnly) sle.editor.ui.EditorFilePicker.images else null, existing?.takeIf { it.isFile }?.name) ?: return null
    sle.editor.ui.EditorFileLocations.remember(sle.editor.ui.EditorFileLocations.Kind.Texture, chosen)
    return chosen.toPath()
}
