package sle.editor.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import sle.editor.ui.theme.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

@Composable
internal fun TreeExpander(expanded: Boolean, enabled: Boolean, onToggle: ()->Unit) {
    val m=LocalEditorMetrics.current
    Box(Modifier.size(m.icon,m.treeRow).semantics { contentDescription=if(expanded) "Collapse" else "Expand" }
        .clickable(enabled=enabled,onClick=onToggle),contentAlignment=androidx.compose.ui.Alignment.Center) {
        if(enabled) sle.editor.ui.controls.EditorGlyphIcon(
            if(expanded) sle.editor.ui.controls.EditorGlyph.ChevronDown else sle.editor.ui.controls.EditorGlyph.ChevronRight,
            LocalEditorColors.current.textSecondary)
    }
}

@Composable
internal fun TreeIcon(kind: String) {
    androidx.compose.material.Icon(EditorIcons.painter(when(kind) {
        "File","Link" -> "resource"
        "Component" -> "transform"
        else -> kind
    }),kind,Modifier.size(LocalEditorMetrics.current.icon),tint=LocalEditorColors.current.accent)
}
