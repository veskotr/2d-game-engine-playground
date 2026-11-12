package sle.editor.ui

import java.awt.*
import java.awt.geom.Path2D
import javax.swing.Icon
import javax.swing.plaf.basic.BasicMenuItemUI
import javax.swing.plaf.basic.BasicMenuUI
import kotlin.math.roundToInt

internal data class MenuAppearance(
    val surface: Color = Color(0x202329),
    val foreground: Color = Color(0xE0E3EA),
    val selection: Color = Color(0x383E4D),
    val accent: Color = Color(0xA6B8EE),
    val scale: Float = 0.85f,
) {
    val disabled = Color(foreground.red, foreground.green, foreground.blue, 95)
    val font = Font(Font.DIALOG, Font.PLAIN, (13*scale).roundToInt().coerceAtLeast(10))
}

internal class EditorMenuItemUI(private val theme: MenuAppearance) : BasicMenuItemUI() {
    override fun installDefaults() {
        super.installDefaults()
        selectionBackground=theme.selection;selectionForeground=theme.foreground;disabledForeground=theme.disabled
    }
}

internal class EditorSubmenuUI(private val theme: MenuAppearance) : BasicMenuUI() {
    override fun installDefaults() {
        super.installDefaults()
        selectionBackground=theme.selection;selectionForeground=theme.foreground;disabledForeground=theme.disabled
        arrowIcon=MenuGlyph("arrow",theme)
    }
}

/** Small vector glyphs share the editor accent and scale without external bitmap assets. */
internal class MenuGlyph(private val label: String, private val theme: MenuAppearance) : Icon {
    override fun getIconWidth() = (18*theme.scale).roundToInt().coerceAtLeast(14)
    override fun getIconHeight() = iconWidth
    override fun paintIcon(component: Component?, graphics: Graphics, x: Int, y: Int) {
        val g=graphics.create() as Graphics2D
        try {
            g.translate(x.toDouble(),y.toDouble());g.scale(iconWidth/18.0,iconHeight/18.0)
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON)
            g.color=if(component?.isEnabled==false) theme.disabled else theme.accent
            g.stroke=BasicStroke(1.35f,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND)
            fun line(x1: Int,y1: Int,x2: Int,y2: Int) = g.drawLine(x1,y1,x2,y2)
            when {
                label=="arrow" -> { line(7,5,11,9);line(11,9,7,13) }
                label.contains("Camera") || label=="Rendering" -> {
                    g.drawRoundRect(2,5,14,10,2,2);g.drawOval(7,7,5,5);line(5,5,6,3);line(6,3,11,3)
                }
                label=="Import" || label.contains("Assets") || label.contains("Prefab") -> {
                    val path=Path2D.Double().apply { moveTo(2.0,6.0);lineTo(2.0,14.0);lineTo(16.0,14.0);lineTo(16.0,5.0);lineTo(9.0,5.0);lineTo(7.0,3.0);lineTo(2.0,3.0);lineTo(2.0,6.0) }
                    g.draw(path);line(9,6,9,11);line(6,9,9,12);line(9,12,12,9)
                }
                label.contains("Duplicate") -> { g.drawRect(2,2,10,10);g.drawRect(6,6,10,10) }
                label=="As Child" -> { line(3,2,3,10);line(3,10,9,10);g.drawRect(9,7,6,6) }
                label=="Physics" || label.contains("Collider") || label.contains("RigidBody") -> { g.drawRect(3,3,12,12);g.fillOval(1,1,4,4);g.fillOval(13,13,4,4) }
                label=="Scripting" || label.contains("Script") -> { line(5,5,2,9);line(2,9,5,13);line(13,5,16,9);line(16,9,13,13);line(10,3,8,15) }
                label=="Audio" || label.contains("Audio") -> { line(7,4,7,13);line(7,4,14,2);line(14,2,14,11);g.drawOval(2,11,5,4);g.drawOval(9,9,5,4) }
                label=="Zones" || label.contains("Zone") -> { g.drawOval(2,2,14,14);line(9,5,9,13);line(5,9,13,9) }
                label=="Transform" -> { line(3,14,3,3);line(3,14,15,14);line(3,3,1,6);line(3,3,5,6);line(15,14,12,12);line(15,14,12,16) }
                label=="New" || label=="Create" -> { g.drawRoundRect(2,2,14,14,2,2);line(9,5,9,13);line(5,9,13,9) }
                else -> { g.drawRect(3,3,12,12);line(3,7,15,7);line(7,7,7,15) }
            }
        } finally { g.dispose() }
    }
}
