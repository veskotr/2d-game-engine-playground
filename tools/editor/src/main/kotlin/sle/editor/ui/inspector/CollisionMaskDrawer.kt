package sle.editor.ui.inspector

import androidx.compose.foundation.layout.Box
import androidx.compose.material.DropdownMenu
import androidx.compose.runtime.*
import sle.editor.project.COLLISION_LAYER_COUNT
import sle.editor.project.CollisionLayers
import sle.editor.ui.controls.EditorButton
import sle.editor.ui.controls.EditorCheckBox

internal val LocalCollisionLayers = staticCompositionLocalOf { CollisionLayers() }

@Composable
internal fun CollisionMaskDrawer(label: String, value: Int, enabled: Boolean, onChange: (Int) -> Unit) {
    val layers = LocalCollisionLayers.current
    var expanded by remember { mutableStateOf(false) }
    Box {
        EditorButton("Layers…", { expanded = true }, help = label)
        DropdownMenu(expanded, onDismissRequest = { expanded = false }) {
            for (slot in 0 until COLLISION_LAYER_COUNT) {
                val bit = 1 shl slot
                EditorCheckBox(layers.label(slot), value and bit != 0, { checked ->
                    onChange(if (checked) value or bit else value and bit.inv())
                }, enabled = enabled)
            }
        }
    }
}
