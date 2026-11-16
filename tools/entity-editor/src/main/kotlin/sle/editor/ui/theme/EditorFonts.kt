package sle.editor.ui.theme

import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.Font

/** Bundled fonts keep Compose and Swing independent of installed OS fonts. */
internal object EditorFonts {
    /** Conventional UI sans for every functional control. */
    val body by lazy {
        FontFamily(
            Font("sle/editor/fonts/roboto/Roboto-Regular.ttf",FontWeight.Normal),
            Font("sle/editor/fonts/roboto/Roboto-Medium.ttf",FontWeight.Medium),
            Font("sle/editor/fonts/roboto/Roboto-Bold.ttf",FontWeight.Bold),
        )
    }
    /** Branding and large decorative headings only. */
    val brand by lazy {
        FontFamily(
            Font("sle/editor/fonts/handjet/Handjet-Medium.ttf",FontWeight.Medium),
            Font("sle/editor/fonts/handjet/Handjet-SemiBold.ttf",FontWeight.SemiBold),
        )
    }
    private fun awtFont(path: String) = checkNotNull(javaClass.getResourceAsStream(path)).use {
        java.awt.Font.createFont(java.awt.Font.TRUETYPE_FONT,it)
    }
    val awt by lazy { awtFont("/sle/editor/fonts/roboto/Roboto-Regular.ttf") }
    val awtMedium by lazy { awtFont("/sle/editor/fonts/roboto/Roboto-Medium.ttf") }
}
