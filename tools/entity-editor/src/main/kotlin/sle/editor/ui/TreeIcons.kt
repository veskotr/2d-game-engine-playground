package sle.editor.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

@Composable
internal fun TreeExpander(expanded: Boolean, enabled: Boolean, onToggle: ()->Unit) {
    val color=MaterialTheme.colors.onSurface.copy(alpha=0.65f)
    Box(Modifier.size(22.dp).semantics { contentDescription=if(expanded) "Collapse" else "Expand" }
        .clickable(enabled=enabled,onClick=onToggle)) {
        if(enabled) Canvas(Modifier.size(22.dp)) {
            val points=if(expanded) listOf(Offset(.32f,.4f),Offset(.5f,.6f),Offset(.68f,.4f)) else listOf(Offset(.4f,.32f),Offset(.6f,.5f),Offset(.4f,.68f))
            points.zipWithNext().forEach { (a,b) -> drawLine(color,Offset(a.x*size.width,a.y*size.height),Offset(b.x*size.width,b.y*size.height),1.5.dp.toPx()) }
        }
    }
}

@Composable
internal fun TreeIcon(kind: String) {
    val color=MaterialTheme.colors.primary
    Canvas(Modifier.size(16.dp).semantics { contentDescription=kind }) {
        val w=size.width;val h=size.height;val stroke=Stroke(1.2.dp.toPx())
        when(kind) {
            "Camera" -> { drawRect(color,Offset(.1f*w,.3f*h),Size(.8f*w,.55f*h),style=stroke);drawCircle(color,.16f*w,Offset(.55f*w,.57f*h),style=stroke);drawLine(color,Offset(.3f*w,.18f*h),Offset(.65f*w,.18f*h),stroke.width) }
            "Scene" -> { drawRect(color,Offset(.12f*w,.12f*h),Size(.76f*w,.76f*h),style=stroke);for(y in listOf(.35f,.6f)) drawLine(color,Offset(.28f*w,y*h),Offset(.72f*w,y*h),stroke.width) }
            else -> { drawRect(color,Offset(.18f*w,.18f*h),Size(.64f*w,.64f*h),style=stroke);drawLine(color,Offset(.18f*w,.4f*h),Offset(.82f*w,.4f*h),stroke.width);drawLine(color,Offset(.4f*w,.4f*h),Offset(.4f*w,.82f*h),stroke.width) }
        }
    }
}
