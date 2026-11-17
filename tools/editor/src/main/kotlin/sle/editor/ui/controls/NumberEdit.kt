package sle.editor.ui.controls

import kotlinx.serialization.json.JsonPrimitive

/** Constraints are caller supplied, never inferred from the component catalog. */
data class NumberOptions(val min: Double? = null, val max: Double? = null) {
    init {
        require(min == null || min.isFinite())
        require(max == null || max.isFinite())
        require(min == null || max == null || min <= max)
    }
    fun parse(text: String): JsonPrimitive? {
        if (!jsonNumber.matches(text)) return null
        val value = text.toDoubleOrNull()?.takeIf { it.isFinite() } ?: return null
        if (min != null && value < min || max != null && value > max) return null
        // Preserve the entered precision/exponent spelling, without a Double round trip.
        return kotlinx.serialization.json.Json.parseToJsonElement(text) as? JsonPrimitive
    }
    companion object {
        private val jsonNumber = Regex("-?(0|[1-9][0-9]*)(\\.[0-9]+)?([eE][+-]?[0-9]+)?")
    }
}

/** Presenter-independent handle. The owner stores drafts/history; stale handles do nothing. */
internal class NumberEdit(
    val options: NumberOptions,
    private val read: () -> String,
    private val start: () -> Any?,
    private val current: (Any) -> Boolean,
    private val preview: (String, JsonPrimitive?) -> Unit,
    private val finish: () -> Unit,
    private val revert: () -> Unit,
) {
    private var token: Any? = null
    val text get() = read()
    val invalid get() = options.parse(text) == null
    val active get() = token?.let(current) == true
    fun focus() { if (!active) token = start() }
    /** BasicTextField also reports caret/selection changes, including in read-only mode. */
    fun updateText(text: String, editable: Boolean) {
        if (!editable || text == this.text) return
        if (!active) focus()
        input(text)
    }
    fun input(text: String) { if (active) preview(text, options.parse(text)) }
    fun commit(): Boolean {
        if (!active || invalid) return false
        finish();token = null;return true
    }
    fun blur() { if (active) finish();token = null }
    fun cancel() { if (active) revert();token = null }
}
