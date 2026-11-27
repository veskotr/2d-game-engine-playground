package sle.editor.project.assets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import sle.editor.ui.dialog.EditorPrompts
import sle.editor.ui.controls.*
import sle.editor.ui.theme.LocalEditorMetrics

internal fun SpriteRegion.caption() = "$name · ($x, $y) ${width} × $height · $id"

@Composable
internal fun SpriteRegionControls(path: String, regions: List<SpriteRegion>, commands: ProjectAssetCommands,
    measured: (String) -> Modifier) {
    var selectedId by remember(path) { mutableStateOf<String?>(null) }
    val selected = regions.firstOrNull { it.id == selectedId } ?: regions.firstOrNull()
    val enabled = !commands.busy
    var showSpritesheet by remember(path, commands.projectRoot) { mutableStateOf(false) }
    Row(horizontalArrangement = Arrangement.spacedBy(LocalEditorMetrics.current.gap)) {
        EditorButton("Open spritesheet…", { showSpritesheet = true }, enabled = enabled,
            modifier = measured("AssetOpenSpritesheet"))
        if (commands.createTileset != null) EditorButton("New Tileset…", commands.newTileset, enabled = enabled,
            help = "Slice this texture into a grid and save it as a tileset", modifier = measured("AssetNewTileset"))
    }
    if (showSpritesheet) {
        SpritesheetDialog(path, regions, commands, measured) { showSpritesheet = false }
    }
    if (selected == null) return
    val labels = remember(regions) { regions.associateBy { it.caption() } }
    EditorSelect("Regions", selected.caption(), labels.keys.toList(),
        onSelect = { selectedId = labels.getValue(it).id }, enabled = enabled)
    EditorButton("Delete region…", {
        if (EditorPrompts.confirm("Delete Sprite Region", "Delete ${selected.name}? Sprites using it will show the full texture.",
                "Delete", danger = true)) {
            commands.editRegions(path) { current -> current.filterNot { it.id == selected.id } }
        }
    }, enabled = enabled)
}
