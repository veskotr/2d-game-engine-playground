package sle.editor.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.em
import kotlin.math.roundToInt

/**
 * Single source for editor text roles. Functional UI uses the conventional sans
 * ([EditorFonts.body]); Handjet is reserved for [brand] (branding and decorative headings).
 */
data class EditorTypography(
    val body: TextStyle,
    val secondary: TextStyle,
    val heading: TextStyle,
    val toolbar: TextStyle,
    val brand: TextStyle,
    val numeric: TextStyle,
)

/**
 * Shared vertical centering for single-line control text: the line box comes from
 * the font's ascent/descent (not rendered glyph bounds) and is centered as a whole,
 * so labels in buttons, tabs, fields and rows share one baseline at any font scale.
 */
fun TextStyle.centeredLine(): TextStyle = copy(
    lineHeight=1.25.em,
    lineHeightStyle=LineHeightStyle(LineHeightStyle.Alignment.Center,LineHeightStyle.Trim.None),
)

fun editorTypography(metrics: EditorMetrics): EditorTypography {
    val base=TextStyle(fontFamily=EditorFonts.body,fontSize=metrics.body,fontWeight=FontWeight.Normal).centeredLine()
    return EditorTypography(
        body=base,
        secondary=base.copy(fontSize=metrics.secondary),
        heading=base.copy(fontSize=metrics.heading,fontWeight=FontWeight.Medium),
        toolbar=base,
        brand=TextStyle(fontFamily=EditorFonts.brand,fontSize=metrics.brand,fontWeight=FontWeight.SemiBold),
        numeric=base.copy(fontFamily=FontFamily.Monospace),
    )
}
val LocalEditorTypography=staticCompositionLocalOf { editorTypography(editorMetrics(EditorDensity.Compact)) }

/**
 * AWT roles mirror [EditorTypography]. [uiScale] is the editor UI scale only: Java2D
 * applies the OS DPI factor to Swing itself, so including it here would scale twice.
 */
enum class EditorAwtRole { Body, Secondary, Heading }
fun editorAwtFont(density: EditorDensity, uiScale: Float, role: EditorAwtRole = EditorAwtRole.Body): java.awt.Font {
    val metrics=editorMetrics(density)
    val size=when(role) { EditorAwtRole.Body -> metrics.body;EditorAwtRole.Secondary -> metrics.secondary;EditorAwtRole.Heading -> metrics.heading }
    val font=if(role==EditorAwtRole.Heading) EditorFonts.awtMedium else EditorFonts.awt
    return font.deriveFont((size.value*uiScale).roundToInt().coerceAtLeast(10).toFloat())
}
