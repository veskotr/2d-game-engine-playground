package sle.editor.project.assets

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import sle.editor.ui.EditorDialogs
import sle.editor.ui.controls.*
import sle.editor.ui.theme.LocalEditorMetrics
import javax.swing.JOptionPane

internal fun SpriteRegion.caption() = "$name · ($x, $y) ${width} × $height · $id"

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SpriteRegionControls(path: String, regions: List<SpriteRegion>, commands: ProjectAssetCommands,
    measured: (String) -> Modifier) {
    var selectedId by remember(path) { mutableStateOf<String?>(null) }
    val selected = regions.firstOrNull { it.id == selectedId } ?: regions.firstOrNull()
    val enabled = !commands.busy
    EditorButton("Slice grid…", {
        EditorDialogs.showInputDialog(null, "Cell width and height in pixels", "16, 16")
            ?.let { commands.sliceTexture(path, it) }
    }, enabled = enabled, modifier = measured("AssetSliceGrid"))
    if (selected == null) return
    val labels = remember(regions) { regions.associateBy { it.caption() } }
    EditorSelect("Regions", selected.caption(), labels.keys.toList(),
        onSelect = { selectedId = labels.getValue(it).id }, enabled = enabled)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(LocalEditorMetrics.current.gap)) {
        EditorButton("Edit region…", {
            val name = EditorDialogs.showInputDialog(null, "Region name", selected.name)
            if (name != null) {
                val rectangle = EditorDialogs.showInputDialog(null, "Left, top, width, height in pixels",
                    "${selected.x}, ${selected.y}, ${selected.width}, ${selected.height}")
                if (rectangle != null) commands.editRegions(path) { current ->
                    val values = parseRegionIntegers(rectangle, 4)
                    current.map {
                        if (it.id == selected.id) it.copy(name = name, x = values[0], y = values[1],
                            width = values[2], height = values[3]) else it
                    }
                }
            }
        }, enabled = enabled, modifier = measured("AssetEditRegion"))
        EditorButton("Delete region…", {
            if (EditorDialogs.showConfirmDialog(null, "Delete ${selected.name}? Sprites using it will show the full texture.",
                    "Delete sprite region", JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) {
                commands.editRegions(path) { current -> current.filterNot { it.id == selected.id } }
            }
        }, enabled = enabled)
    }
}
