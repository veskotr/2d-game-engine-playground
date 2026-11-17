package sle.editor.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.*
import sle.editor.ui.theme.*

/** One scale application per window; native viewports retain their own pixel density. */
@Composable
internal fun EditorTheme(appearance: EditorAppearance,content: @Composable ()->Unit) {
    val systemDensity=LocalDensity.current
    val colors=editorColors(appearance.mode)
    val metrics=editorMetrics(appearance.density)
    val type=editorTypography(metrics)
    CompositionLocalProvider(
        LocalDensity provides Density(systemDensity.density*appearance.scale,systemDensity.fontScale),
        LocalEditorAppearance provides appearance,
        LocalEditorColors provides colors,
        LocalEditorMetrics provides metrics,
        LocalEditorTypography provides type,
    ) {
        // Material roles map onto the editor's few text roles; no feature code sets sizes.
        MaterialTheme(
            colors=colors.material(appearance.mode),
            typography=Typography(defaultFontFamily=EditorFonts.body,
                h5=type.heading,h6=type.heading,subtitle1=type.heading,subtitle2=type.heading,
                body1=type.body,body2=type.body,button=type.toolbar,caption=type.secondary,overline=type.secondary),
            shapes=Shapes(RoundedCornerShape(metrics.controlRadius),RoundedCornerShape(metrics.controlRadius),RoundedCornerShape(metrics.controlRadius)),
        ) {
            CompositionLocalProvider(LocalTextStyle provides type.body,content=content)
        }
    }
}
