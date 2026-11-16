package sle.editor.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.painterResource
import sle.editor.ui.theme.*

/** Semantic lookup shared by component, asset and command presentation. */
object EditorIcons {
    val kinds=setOf("scene","entity","camera","sprite","collider","physics","lua_script","audio","animation","prefab","ui","tilemap","folder","resource","visibility","hidden","lock","warning","transform","add")
    fun kind(type: String): String = when(type.lowercase()) {
        "camera2d" -> "camera"
        "spriterenderer" -> "sprite"
        "boxcollider","circlecollider","polygoncollider" -> "collider"
        "rigidbody","rigidbody2d" -> "physics"
        "script","luascript","luascripts" -> "lua_script"
        "audiosource","audiolistener" -> "audio"
        else -> type.lowercase().takeIf { it in kinds } ?: "resource"
    }
    fun path(type: String,mode: EditorThemeMode,size: Int=16): String {
        require(size==16 || size==24)
        return "sle/editor/icons/png/${mode.name.lowercase()}/$size/${kind(type)}.png"
    }
    @Composable fun painter(type: String,size: Int=16) = painterResource(path(type,LocalEditorAppearance.current.mode,size))
    internal fun commandFallback(label: String,appearance: MenuAppearance): javax.swing.Icon {
        val semantic=kind(label)
        if(semantic=="resource" && label.lowercase() !in kinds) return MenuGlyph(label,appearance)
        // Preserve the token-derived accent, including disabled states, for Swing commands.
        return object: javax.swing.Icon {
            private val size=(16*appearance.scale).toInt().coerceAtLeast(12)
            override fun getIconWidth()=size
            override fun getIconHeight()=size
            override fun paintIcon(component: java.awt.Component?,g: java.awt.Graphics,x: Int,y: Int) {
                val mode=appearance.mode
                val url=EditorIcons::class.java.getResource("/"+path(semantic,mode,24)) ?: return
                val image=javax.swing.ImageIcon(url).image
                if(component?.isEnabled==false) {
                    val disabled=java.awt.image.BufferedImage(size,size,java.awt.image.BufferedImage.TYPE_INT_ARGB)
                    val graphics=disabled.createGraphics()
                    try {
                        graphics.drawImage(image,0,0,size,size,null)
                        graphics.composite=java.awt.AlphaComposite.SrcIn
                        graphics.color=appearance.disabled
                        graphics.fillRect(0,0,size,size)
                    } finally { graphics.dispose() }
                    g.drawImage(disabled,x,y,null)
                } else g.drawImage(image,x,y,size,size,null)
            }
        }
    }
}
