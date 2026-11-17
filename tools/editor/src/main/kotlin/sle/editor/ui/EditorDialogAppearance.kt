package sle.editor.ui

import sle.editor.ui.theme.*
import androidx.compose.ui.graphics.toArgb
import java.awt.*
import java.awt.event.ContainerAdapter
import java.awt.event.ContainerEvent
import java.util.WeakHashMap
import javax.swing.*
import javax.swing.text.JTextComponent

/** Editor-owned Swing roots only. No UIManager overrides or native viewport traversal. */
internal object EditorDialogAppearance {
    var appearance=EditorAppearance()
        private set
    private val roots=WeakHashMap<JComponent,Boolean>()
    fun update(value: EditorAppearance) {
        check(SwingUtilities.isEventDispatchThread())
        appearance=value
        refreshEditorPopups(value)
        EditorWindowChrome.update(value.mode)
        roots.keys.toList().forEach { style(it);it.revalidate();it.repaint() }
    }
    fun register(root: JComponent) {
        check(SwingUtilities.isEventDispatchThread())
        roots[root]=true
        style(root)
    }
    private fun style(component: Component) {
        val colors=editorColors(appearance.mode)
        fun color(value: androidx.compose.ui.graphics.Color)=Color(value.toArgb(),true)
        component.background=color(if(component is JTextComponent) colors.inputBackground else colors.surface)
        component.foreground=color(if(component.isEnabled) colors.textPrimary else colors.disabledText)
        component.font=editorAwtFont(appearance.density,appearance.scale)
        when(component) {
            is AbstractButton -> {
                if(component.getClientProperty("sle.dialog.styled")!=true) {
                    component.isRolloverEnabled=true
                    component.setUI(object: javax.swing.plaf.basic.BasicButtonUI() {
                        override fun paint(g: Graphics,c: JComponent) {
                            val model=(c as AbstractButton).model
                            val tokens=editorColors(appearance.mode)
                            g.color=color(when {
                                model.isPressed -> tokens.pressedBackground
                                model.isSelected -> tokens.selectedBackground
                                model.isRollover -> tokens.hoverBackground
                                else -> tokens.surface
                            })
                            g.fillRect(0,0,c.width,c.height)
                            super.paint(g,c)
                        }
                        override fun paintButtonPressed(g: Graphics,c: AbstractButton) {
                            g.color=color(editorColors(appearance.mode).pressedBackground);g.fillRect(0,0,c.width,c.height)
                        }
                        override fun paintFocus(g: Graphics,b: AbstractButton,view: Rectangle,text: Rectangle,icon: Rectangle) {
                            g.color=color(editorColors(appearance.mode).focusRing);g.drawRect(2,2,b.width-5,b.height-5)
                        }
                        override fun paintText(g: Graphics,c: JComponent,r: Rectangle,text: String) {
                            g.color=color(if(c.isEnabled) editorColors(appearance.mode).textPrimary else editorColors(appearance.mode).disabledText)
                            javax.swing.plaf.basic.BasicGraphicsUtils.drawStringUnderlineCharAt(g,text,(c as AbstractButton).displayedMnemonicIndex,r.x,r.y+g.fontMetrics.ascent)
                        }
                    })
                    component.border=BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(color(colors.borderSubtle)),BorderFactory.createEmptyBorder(4,10,4,10))
                }
                component.isOpaque=true
            }

            is JComboBox<*> -> {
                if(component.getClientProperty("sle.dialog.styled")!=true) {
                    component.setUI(object: javax.swing.plaf.basic.BasicComboBoxUI() {
                        override fun createPopup(): javax.swing.plaf.basic.ComboPopup =
                            javax.swing.plaf.basic.BasicComboPopup(comboBox).also { register(it) }
                        override fun paintCurrentValueBackground(g: Graphics,bounds: Rectangle,hasFocus: Boolean) {
                            g.color=color(editorColors(appearance.mode).surface)
                            g.fillRect(bounds.x,bounds.y,bounds.width,bounds.height)
                        }
                        // Flat chevron instead of the system arrow box.
                        override fun createArrowButton(): JButton = object: JButton() {
                            init { isFocusable=false;isContentAreaFilled=false;isBorderPainted=false;isOpaque=false;name="ComboBox.arrowButton" }
                            override fun paintComponent(g: Graphics) {
                                val g2=g.create() as Graphics2D
                                try {
                                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON)
                                    val tokens=editorColors(appearance.mode)
                                    g2.color=color(if(model.isRollover) tokens.textPrimary else tokens.textSecondary)
                                    g2.stroke=BasicStroke(1.4f,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND)
                                    val cx=width/2.0;val cy=height/2.0;val r=3.5*appearance.scale
                                    g2.draw(java.awt.geom.Line2D.Double(cx-r,cy-r/2,cx,cy+r/2));g2.draw(java.awt.geom.Line2D.Double(cx,cy+r/2,cx+r,cy-r/2))
                                } finally { g2.dispose() }
                            }
                        }
                    })
                    component.border=BorderFactory.createLineBorder(color(colors.borderSubtle))
                    @Suppress("UNCHECKED_CAST")
                    val combo=component as JComboBox<Any>
                    val delegate=combo.renderer
                    combo.renderer=ListCellRenderer { list,value,index,selected,focused ->
                        delegate.getListCellRendererComponent(list,value,index,selected,focused).also { cell ->
                            style(cell)
                            val tokens=editorColors(appearance.mode)
                            cell.background=color(if(selected) tokens.selectedBackground else tokens.surface)
                            cell.foreground=color(if(combo.isEnabled) tokens.textPrimary else tokens.disabledText)
                        }
                    }
                }
            }
            is JToolBar -> {
                if(component.getClientProperty("sle.dialog.styled")!=true)
                    component.setUI(javax.swing.plaf.basic.BasicToolBarUI())
            }

            is JScrollBar -> if(component.getClientProperty("sle.dialog.styled")!=true) component.setUI(EditorScrollBarUI())
            is JScrollPane -> {
                component.border=BorderFactory.createLineBorder(color(colors.borderSubtle))
                component.viewport.background=color(colors.surface)
            }
            is javax.swing.table.JTableHeader -> {
                if(component.getClientProperty("sle.dialog.styled")!=true) component.setUI(javax.swing.plaf.basic.BasicTableHeaderUI())
                component.defaultRenderer=javax.swing.table.TableCellRenderer { table,value,_,_,_,_ ->
                    JLabel(value?.toString() ?: "").apply {
                        val tokens=editorColors(appearance.mode)
                        isOpaque=true;background=color(tokens.surfaceRaised);foreground=color(tokens.textSecondary)
                        font=table?.font ?: font
                        border=BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(0,0,1,1,color(tokens.separator)),BorderFactory.createEmptyBorder(3,6,3,6))
                    }
                }
            }
            is JTextComponent -> {
                component.caretColor=color(colors.accent)
                component.selectionColor=color(colors.selectedBackground)
                component.selectedTextColor=color(colors.textPrimary)
                component.disabledTextColor=color(colors.disabledText)
            }
            is JList<*> -> { component.selectionBackground=color(colors.selectedBackground);component.selectionForeground=color(colors.textPrimary) }
            is JTable -> { component.selectionBackground=color(colors.selectedBackground);component.selectionForeground=color(colors.textPrimary);component.gridColor=color(colors.separator) }
            is JTree -> (component.cellRenderer as? javax.swing.tree.DefaultTreeCellRenderer)?.apply {
                backgroundNonSelectionColor=color(colors.surface);backgroundSelectionColor=color(colors.selectedBackground)
                textNonSelectionColor=color(colors.textPrimary);textSelectionColor=color(colors.textPrimary);borderSelectionColor=color(colors.focusRing)
            }
        }
        if(component is JComponent && component.getClientProperty("sle.dialog.styled")!=true) {
            component.putClientProperty("sle.dialog.styled",true)
            component.addPropertyChangeListener("enabled") { style(component) }
            // File choosers rebuild their children on navigation/view/filter changes.
            component.addContainerListener(object: ContainerAdapter() {
                override fun componentAdded(event: ContainerEvent) { style(event.child) }
            })
        }
        if(component is Container) component.components.forEach(::style)
    }
}

/**
 * Dialogs without an explicit parent are owned by the active editor window (or the
 * main window), never Swing's hidden shared frame, so they always open in front of
 * the editor and stay above it.
 */
internal object EditorDialogOwner {
    @Volatile var main: Window?=null
    fun resolve(parent: Component?): Component? {
        if(parent!=null) return parent
        val active=KeyboardFocusManager.getCurrentKeyboardFocusManager().activeWindow
        if(active!=null && active.isShowing && (active is Frame || active is Dialog)) return active
        return main?.takeIf { it.isShowing } ?: Window.getWindows().firstOrNull { it is Frame && it.isShowing }
    }
}

/**
 * Retain Swing's chooser behavior, filters and approve/cancel semantics, but replace
 * the system look-and-feel presentation with an editor-themed one.
 */
internal class EditorFileChooser : JFileChooser {
    constructor(): super()
    constructor(file: java.io.File?): super(file)
    constructor(path: String): super(path)
    override fun updateUI() {
        // Mirrors JFileChooser.updateUI, but with the editor UI instead of the system one.
        val acceptAll=isAcceptAllFileFilterUsed
        if(acceptAll && ui!=null) removeChoosableFileFilter(acceptAllFileFilter)
        setUI(EditorFileChooserUI(this))
        if(acceptAll) addChoosableFileFilter(acceptAllFileFilter)
    }
    override fun createDialog(parent: Component?): JDialog = super.createDialog(EditorDialogOwner.resolve(parent)).also {
        EditorDialogAppearance.register(it.rootPane)
        it.pack()
        EditorWindowChrome.apply(it)
    }
}

/** Metal layout (no system places bar) with editor icons for folders, files and actions. */
internal class EditorFileChooserUI(chooser: JFileChooser) : javax.swing.plaf.metal.MetalFileChooserUI(chooser) {
    private val view=object: javax.swing.plaf.basic.BasicFileChooserUI.BasicFileView() {
        override fun getIcon(file: java.io.File?): Icon? {
            if(file==null) return null
            val system=chooser.fileSystemView
            if(system.isDrive(file) || system.isComputerNode(file) || system.isFloppyDrive(file)) return super.getIcon(file)
            return EditorAssetIcon(if(chooser.isTraversable(file)) "folder" else fileKind(file.name))
        }
    }
    override fun getFileView(fc: JFileChooser?): javax.swing.filechooser.FileView = view
    override fun installIcons(fc: JFileChooser?) {
        super.installIcons(fc)
        directoryIcon=EditorAssetIcon("folder");fileIcon=EditorAssetIcon("resource")
        upFolderIcon=EditorActionIcon(EditorActionIcon.Kind.Up);homeFolderIcon=EditorActionIcon(EditorActionIcon.Kind.Home)
        newFolderIcon=EditorActionIcon(EditorActionIcon.Kind.NewFolder)
        listViewIcon=EditorActionIcon(EditorActionIcon.Kind.List);detailsViewIcon=EditorActionIcon(EditorActionIcon.Kind.Details)
    }
    companion object {
        fun fileKind(name: String): String {
            val lower=name.lowercase()
            return when {
                lower.endsWith(".scene.json") || lower.endsWith(".slescene") -> "scene"
                lower.endsWith(".prefab.json") || lower.endsWith(".sleprefab") -> "prefab"
                lower.endsWith(".lua") -> "lua_script"
                lower.substringAfterLast('.') in setOf("png","jpg","jpeg","bmp","tga","gif","psd","hdr") -> "sprite"
                lower.substringAfterLast('.') in setOf("ogg","wav","mp3","flac") -> "audio"
                lower.endsWith(".xml") -> "ui"
                else -> "resource"
            }
        }
    }
}

/** Bundled theme PNG, resolved at paint time so a live theme switch updates it. */
internal class EditorAssetIcon(private val kind: String) : Icon {
    private val size get()=(16*EditorDialogAppearance.appearance.scale).toInt().coerceAtLeast(12)
    override fun getIconWidth()=size
    override fun getIconHeight()=size
    override fun paintIcon(c: Component?,g: Graphics,x: Int,y: Int) {
        val url=EditorAssetIcon::class.java.getResource("/"+EditorIcons.path(kind,EditorDialogAppearance.appearance.mode,24)) ?: return
        val g2=g.create() as Graphics2D
        try {
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BILINEAR)
            g2.drawImage(ImageIcon(url).image,x,y,size,size,null)
        } finally { g2.dispose() }
    }
}

/** Stroked action glyphs matching the Compose glyph set. */
internal class EditorActionIcon(private val kind: Kind) : Icon {
    enum class Kind { Up, Home, NewFolder, List, Details, Info, Question, Warning, Error }
    private val size get()=(16*EditorDialogAppearance.appearance.scale).toInt().coerceAtLeast(12).let { if(kind.ordinal>=Kind.Info.ordinal) it*2 else it }
    override fun getIconWidth()=size
    override fun getIconHeight()=size
    override fun paintIcon(c: Component?,g: Graphics,x: Int,y: Int) {
        val tokens=editorColors(EditorDialogAppearance.appearance.mode)
        fun awt(value: androidx.compose.ui.graphics.Color)=Color(value.toArgb(),true)
        val g2=g.create() as Graphics2D
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON)
            g2.translate(x,y);g2.scale(size/16.0,size/16.0)
            g2.stroke=BasicStroke(1.4f,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND)
            g2.color=awt(if(c?.isEnabled==false) tokens.disabledText else tokens.textSecondary)
            fun line(x1: Double,y1: Double,x2: Double,y2: Double)=g2.draw(java.awt.geom.Line2D.Double(x1,y1,x2,y2))
            when(kind) {
                Kind.Up -> { line(8.0,13.0,8.0,3.5);line(4.0,7.5,8.0,3.5);line(8.0,3.5,12.0,7.5) }
                Kind.Home -> { line(2.5,8.0,8.0,3.0);line(8.0,3.0,13.5,8.0);g2.draw(java.awt.geom.Rectangle2D.Double(4.5,7.5,7.0,6.0)) }
                Kind.NewFolder -> { g2.draw(java.awt.geom.RoundRectangle2D.Double(2.0,4.0,12.0,9.0,2.0,2.0));line(8.0,6.5,8.0,11.0);line(5.75,8.75,10.25,8.75) }
                Kind.List -> for(row in listOf(4.0,8.0,12.0)) { line(2.5,row,3.5,row);line(6.0,row,13.5,row) }
                Kind.Details -> { g2.draw(java.awt.geom.Rectangle2D.Double(2.5,3.0,11.0,10.0));line(2.5,6.5,13.5,6.5);line(6.5,6.5,6.5,13.0) }
                else -> {
                    g2.color=awt(when(kind) { Kind.Warning -> tokens.warning;Kind.Error -> tokens.error;Kind.Info -> tokens.info;else -> tokens.accent })
                    g2.draw(java.awt.geom.Ellipse2D.Double(1.5,1.5,13.0,13.0))
                    when(kind) {
                        Kind.Question -> { g2.draw(java.awt.geom.Arc2D.Double(5.5,4.0,5.0,4.5,160.0,-250.0,java.awt.geom.Arc2D.OPEN));line(8.0,8.5,8.0,9.5);line(8.0,11.75,8.0,11.8) }
                        Kind.Info -> { line(8.0,7.5,8.0,11.75);line(8.0,4.75,8.0,4.8) }
                        else -> { line(8.0,4.5,8.0,8.75);line(8.0,11.25,8.0,11.3) }
                    }
                }
            }
        } finally { g2.dispose() }
    }
    companion object {
        fun forMessage(type: Int): Icon? = when(type) {
            JOptionPane.ERROR_MESSAGE -> EditorActionIcon(Kind.Error)
            JOptionPane.WARNING_MESSAGE -> EditorActionIcon(Kind.Warning)
            JOptionPane.INFORMATION_MESSAGE -> EditorActionIcon(Kind.Info)
            JOptionPane.QUESTION_MESSAGE -> EditorActionIcon(Kind.Question)
            else -> null
        }
    }
}

/** Slim thumb on the panel surface; no arrow buttons. */
internal class EditorScrollBarUI : javax.swing.plaf.basic.BasicScrollBarUI() {
    private fun awt(value: androidx.compose.ui.graphics.Color)=Color(value.toArgb(),true)
    private fun noButton()=JButton().apply { preferredSize=Dimension(0,0);minimumSize=Dimension(0,0);maximumSize=Dimension(0,0) }
    override fun createDecreaseButton(orientation: Int)=noButton()
    override fun createIncreaseButton(orientation: Int)=noButton()
    override fun getPreferredSize(c: JComponent): Dimension {
        val thickness=(10*EditorDialogAppearance.appearance.scale).toInt().coerceAtLeast(8)
        return if(scrollbar.orientation==JScrollBar.VERTICAL) Dimension(thickness,48) else Dimension(48,thickness)
    }
    override fun paintTrack(g: Graphics,c: JComponent,r: Rectangle) {
        g.color=awt(editorColors(EditorDialogAppearance.appearance.mode).surface);g.fillRect(r.x,r.y,r.width,r.height)
    }
    override fun paintThumb(g: Graphics,c: JComponent,r: Rectangle) {
        if(r.isEmpty || !scrollbar.isEnabled) return
        val tokens=editorColors(EditorDialogAppearance.appearance.mode)
        val g2=g.create() as Graphics2D
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON)
            g2.color=awt(if(isThumbRollover || isDragging) tokens.textSecondary else tokens.border.copy(alpha=.7f))
            g2.fillRoundRect(r.x+2,r.y+2,r.width-4,r.height-4,6,6)
        } finally { g2.dispose() }
    }
}

/** The same JOptionPane model and modal results, with scoped presentation. */
internal object EditorDialogs {
    private fun show(parent: Component?, title: String, pane: JOptionPane): Any? {
        check(SwingUtilities.isEventDispatchThread())
        if(pane.icon==null) pane.icon=EditorActionIcon.forMessage(pane.messageType)
        val dialog=pane.createDialog(EditorDialogOwner.resolve(parent),title)
        try {
            EditorDialogAppearance.register(dialog.rootPane)
            dialog.pack()
            EditorWindowChrome.apply(dialog)
            dialog.isVisible=true
            return pane.value
        } finally { dialog.dispose() }
    }
    fun showConfirmDialog(parent: Component?,message: Any?,title: String,optionType: Int,messageType: Int=JOptionPane.QUESTION_MESSAGE): Int =
        (show(parent,title,JOptionPane(message,messageType,optionType)) as? Int) ?: JOptionPane.CLOSED_OPTION
    fun showMessageDialog(parent: Component?,message: Any?,title: String,messageType: Int) {
        show(parent,title,JOptionPane(message,messageType))
    }
    fun showOptionDialog(parent: Component?,message: Any?,title: String,optionType: Int,messageType: Int,icon: Icon?,options: Array<out Any>,initial: Any?): Int {
        val selected=show(parent,title,JOptionPane(message,messageType,optionType,icon,options,initial))
        return options.indexOfFirst { it==selected }
    }
    fun showInputDialog(parent: Component?,message: Any?): String? = showInputDialog(parent,message,"")
    fun showInputDialog(parent: Component?,message: Any?,initial: Any?): String? =
        input(parent,message,"Input",JOptionPane.QUESTION_MESSAGE,null,null,initial) as? String
    fun showInputDialog(parent: Component?,message: Any?,title: String,messageType: Int): String? =
        input(parent,message,title,messageType,null,null,null) as? String
    fun showInputDialog(parent: Component?,message: Any?,title: String,messageType: Int,icon: Icon?,values: Array<out Any>,initial: Any?): Any? =
        input(parent,message,title,messageType,icon,values,initial)
    private fun input(parent: Component?,message: Any?,title: String,type: Int,icon: Icon?,values: Array<out Any>?,initial: Any?): Any? {
        val pane=JOptionPane(message,type,JOptionPane.OK_CANCEL_OPTION,icon).apply {
            wantsInput=true;selectionValues=values;initialSelectionValue=initial
        }
        show(parent,title,pane)
        return pane.inputValue.takeUnless { it===JOptionPane.UNINITIALIZED_VALUE }
    }
}
