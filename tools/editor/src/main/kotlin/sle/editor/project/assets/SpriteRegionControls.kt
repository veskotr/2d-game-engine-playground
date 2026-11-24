package sle.editor.project.assets

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import sle.editor.ui.EditorDialogs
import sle.editor.ui.controls.*
import javax.swing.JOptionPane

internal fun SpriteRegion.caption() = "$name · ($x, $y) ${width} × $height · $id"

@Composable
internal fun SpriteRegionControls(path: String, regions: List<SpriteRegion>, commands: ProjectAssetCommands,
    measured: (String) -> Modifier) {
    var selectedId by remember(path) { mutableStateOf<String?>(null) }
    val selected = regions.firstOrNull { it.id == selectedId } ?: regions.firstOrNull()
    val enabled = !commands.busy
    var showSpritesheet by remember(path, commands.projectRoot) { mutableStateOf(false) }
    EditorButton("Open spritesheet…", { showSpritesheet = true }, enabled = enabled,
        modifier = measured("AssetOpenSpritesheet"))
    if (showSpritesheet) {
        SpritesheetDialog(path, regions, commands, measured) { showSpritesheet = false }
    }
    if (selected == null) return
    val labels = remember(regions) { regions.associateBy { it.caption() } }
    EditorSelect("Regions", selected.caption(), labels.keys.toList(),
        onSelect = { selectedId = labels.getValue(it).id }, enabled = enabled)
    EditorButton("Delete region…", {
        if (EditorDialogs.showConfirmDialog(null, "Delete ${selected.name}? Sprites using it will show the full texture.",
                "Delete sprite region", JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) {
            commands.editRegions(path) { current -> current.filterNot { it.id == selected.id } }
        }
    }, enabled = enabled)
}
