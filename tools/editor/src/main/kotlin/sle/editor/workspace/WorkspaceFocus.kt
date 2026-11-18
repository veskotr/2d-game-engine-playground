package sle.editor.workspace

import java.awt.Component
import java.awt.KeyboardFocusManager
import java.awt.Window
import java.util.WeakHashMap

/**
 * Which panel has keyboard focus, for scoped shortcuts. Compose panels report focus-within per
 * host window (Compose keeps its own focus while a heavyweight canvas owns AWT focus, so the two
 * are tracked separately); heavyweight components such as the native canvas register their panel.
 * Works the same in detached windows. EDT only.
 */
internal class WorkspaceFocus {
    private val composePanels = mutableMapOf<HostId, PanelId>()
    private val components = WeakHashMap<Component, PanelId>()

    fun report(host: HostId, panel: PanelId, focused: Boolean) {
        if(focused) composePanels[host] = panel else if(composePanels[host] == panel) composePanels.remove(host)
    }
    fun register(component: Component, panel: PanelId) { components[component] = panel }
    fun forgetHost(host: HostId) { composePanels.remove(host) }

    /** The panel of a registered heavyweight component containing [component], if any. */
    fun componentPanel(component: Component?): PanelId? {
        var current = component
        while(current != null) { components[current]?.let { return it };current = current.parent }
        return null
    }
    fun composePanel(host: HostId): PanelId? = composePanels[host]
    /** The AWT focus owner wins: when it is a registered canvas, Compose focus in that window is stale. */
    fun panelFor(component: Component?, host: HostId?): PanelId? = componentPanel(component) ?: host?.let(composePanels::get)
}

/** Text fields report focus here so shortcuts leave plain keys to them. Process-wide, EDT only. */
internal object TextInputFocus {
    private val owners = WeakHashMap<Any, Window?>()
    fun report(owner: Any, focused: Boolean) {
        if(focused) owners[owner] = KeyboardFocusManager.getCurrentKeyboardFocusManager().focusedWindow else owners.remove(owner)
    }
    fun focusedIn(window: Window?): Boolean = window != null && owners.values.any { it === window }
}
