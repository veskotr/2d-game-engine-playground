package sle.editor.ui.theme

import androidx.compose.runtime.*

enum class EditorThemeMode { Dark, Light }
enum class EditorDensity { Compact, Comfortable }

data class EditorAppearance(
    val mode: EditorThemeMode = EditorThemeMode.Dark,
    val density: EditorDensity = EditorDensity.Compact,
    val scale: Float = 1f,
) {
    init { require(scale.isFinite() && scale in listOf(.75f,.85f,1f)) { "Unsupported UI scale" } }
}

/** Application-owned presentation only; never owns documents, windows or the engine. */
class EditorAppearanceState(private val store: EditorAppearanceStore = EditorAppearanceStore()) {
    var value by mutableStateOf(store.load())
        private set
    var notice by mutableStateOf(store.lastFailure)
        private set
    fun update(next: EditorAppearance) {
        if(next==value) return
        value=next
        store.save(next)
        notice=store.lastFailure
    }
}
