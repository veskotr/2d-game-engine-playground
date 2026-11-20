package sle.editor.project

import androidx.compose.foundation.HorizontalScrollbar
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.material.MaterialTheme
import sle.editor.ui.controls.EditorLabel
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import sle.editor.ui.controls.EditorCheckBox

@Composable
internal fun CollisionLayerSettings(layers: CollisionLayers, onChange: (CollisionLayers) -> Unit, enabled: Boolean) {
    val slots = 0 until COLLISION_LAYER_COUNT
    for (slot in slots) {
        SettingsField("Layer ${slot + 1}", layers.customLabel(slot), { onChange(layers.withLabel(slot, it)) }, enabled)
    }
    EditorLabel("Allowed pairs", style = MaterialTheme.typography.subtitle2)
    val scroll = rememberScrollState()
    Row {
        Column(Modifier.width(116.dp)) {
            Spacer(Modifier.height(32.dp))
            for (slot in slots) {
                Box(Modifier.height(32.dp), contentAlignment = Alignment.CenterStart) {
                    EditorLabel("${slot + 1}  ${layers.customLabel(slot)}", maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        Column(Modifier.weight(1f)) {
            Column(Modifier.horizontalScroll(scroll)) {
                Row {
                    for (slot in slots) {
                        Box(Modifier.size(32.dp), contentAlignment = Alignment.Center) {
                            EditorLabel((slot + 1).toString())
                        }
                    }
                }
                for (first in slots) {
                    Row {
                        for (second in slots) {
                            val label = "${layers.label(first)} × ${layers.label(second)}"
                            Box(Modifier.size(32.dp), contentAlignment = Alignment.Center) {
                                EditorCheckBox(label, layers.allows(first, second),
                                    { onChange(layers.withPair(first, second, it)) },
                                    enabled = enabled, help = label, showLabel = false)
                            }
                        }
                    }
                }
            }
            HorizontalScrollbar(rememberScrollbarAdapter(scroll), Modifier.fillMaxWidth())
        }
    }
}
