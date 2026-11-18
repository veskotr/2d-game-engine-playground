package sle.editor.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import sle.editor.ui.EditorIcons
import kotlin.test.*
import org.junit.Test

class EditorAppearanceTest {
    private fun contrast(a: Color,b: Color): Float {
        val x=a.luminance();val y=b.luminance()
        return (maxOf(x,y)+.05f)/(minOf(x,y)+.05f)
    }
    @Test fun requiredContrastPairs() {
        for(mode in EditorThemeMode.entries) {
            val c=editorColors(mode)
            for(bg in listOf(c.background,c.surface,c.surfaceRaised,c.inputBackground,c.selectedBackground,c.hoverBackground,c.pressedBackground,c.disabledBackground)) {
                for(fg in listOf(c.textPrimary,c.textSecondary,c.placeholderText,c.disabledText))
                    assertTrue(contrast(fg,bg)>=4.5f,"$mode text $fg on $bg: ${contrast(fg,bg)}")
                for(fg in listOf(c.border,c.focusRing)) assertTrue(contrast(fg,bg)>=3f,"$mode boundary: ${contrast(fg,bg)}")
            }
            assertTrue(contrast(c.accent,c.accentOnPrimary)>=4.5f)
            for((fg,bg) in listOf(c.error to c.errorBackground,c.warning to c.warningBackground,c.success to c.successBackground,c.info to c.infoBackground,c.tooltipText to c.tooltipBackground))
                assertTrue(contrast(fg,bg)>=4.5f,"$mode status: ${contrast(fg,bg)}")
            // Status bar: level icons and text in the recent list sit on the bar and popup surfaces;
            // a notice's Ghost action, Dismiss glyph and focus ring sit on its level background.
            val levels=listOf(c.errorBackground,c.warningBackground,c.successBackground,c.infoBackground)
            for(fg in listOf(c.error,c.warning,c.success,c.info)) for(bg in listOf(c.surfaceRaised,c.surface))
                assertTrue(contrast(fg,bg)>=4.5f,"$mode notice level on bar: ${contrast(fg,bg)}")
            for(bg in levels) {
                for(fg in listOf(c.textPrimary,c.textSecondary)) assertTrue(contrast(fg,bg)>=4.5f,"$mode notice action: ${contrast(fg,bg)}")
                assertTrue(contrast(c.focusRing,bg)>=3f,"$mode notice focus: ${contrast(c.focusRing,bg)}")
            }
        }
    }
    @Test fun bundledResourcesAndUnknownFallback() {
        assertEquals("Roboto",EditorFonts.awt.family)
        assertEquals("resource",EditorIcons.kind("future-component"))
        assertEquals("camera",EditorIcons.kind("Camera2D"))
        for(mode in EditorThemeMode.entries) for(kind in EditorIcons.kinds)
            assertNotNull(javaClass.getResource("/"+EditorIcons.path(kind,mode)))
        for(path in listOf("fonts/roboto/Roboto-Regular.ttf","fonts/roboto/Roboto-Medium.ttf","fonts/roboto/Roboto-Bold.ttf","licenses/Roboto-APACHE-2.0.txt","branding/sle-256.png"))
            assertNotNull(javaClass.getResource("/sle/editor/$path"))
    }
}
