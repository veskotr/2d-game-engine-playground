package sle.editor.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Scale controls/typography together, retaining the OS density for native rendering. */
@Composable
internal fun EditorTheme(scale: Float,content: @Composable ()->Unit) {
    val systemDensity=LocalDensity.current
    CompositionLocalProvider(LocalDensity provides Density(systemDensity.density*scale,systemDensity.fontScale)) {
        MaterialTheme(
            colors=darkColors(primary=Color(0xFFA6B8EE),background=Color(0xFF17191E),surface=Color(0xFF202329),
                onBackground=Color(0xFFE0E3EA),onSurface=Color(0xFFE0E3EA)),
            typography=Typography().let { it.copy(h5=it.h5.copy(fontSize=18.sp),h6=it.h6.copy(fontSize=16.sp),button=it.button.copy(fontSize=13.sp)) },
            shapes=Shapes(RoundedCornerShape(3.dp),RoundedCornerShape(3.dp),RoundedCornerShape(4.dp)),
            content=content,
        )
    }
}
