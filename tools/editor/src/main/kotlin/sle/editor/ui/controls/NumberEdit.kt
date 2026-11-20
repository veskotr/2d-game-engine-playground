package sle.editor.ui.controls

import kotlinx.serialization.json.JsonPrimitive
import sle.editor.model.NumericConstraints
import sle.editor.model.NumericPresentation
import java.math.BigDecimal
import java.math.RoundingMode

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

/** Suggested limits govern slider movement; only hard limits constrain keyboard steps. */
internal class NumberPresentation(
    private val ui: NumericPresentation,
    private val hard: NumericConstraints?,
    private val integer: Boolean,
) {
    private val min = ui.min?.takeIf { it.isFinite() }?.let(BigDecimal::valueOf)
    private val max = ui.max?.takeIf { it.isFinite() }?.let(BigDecimal::valueOf)
    private val step = ui.step?.takeIf { it.isFinite() && it > 0 && (!integer || it % 1 == 0.0) }
        ?.let(BigDecimal::valueOf)
    val canStep get() = step != null
    val hasSlider get() = ui.slider && min != null && max != null && min < max &&
        (!integer || step != null && min.stripTrailingZeros().scale() <= 0 && max.stripTrailingZeros().scale() <= 0)

    fun sliderPosition(value: Double): Float {
        if (!hasSlider || !value.isFinite()) return 0f
        val start = checkNotNull(min)
        val end = checkNotNull(max)
        return (BigDecimal.valueOf(value).coerceIn(start, end) - start)
            .divide(end - start, java.math.MathContext.DECIMAL64).toFloat()
    }

    fun sliderText(position: Float): String? {
        if (!hasSlider || !position.isFinite()) return null
        val start = checkNotNull(min)
        val end = checkNotNull(max)
        if (position <= 0f) return generatedText(start)
        if (position >= 1f) return generatedText(end)
        val raw = start + (end - start) * BigDecimal(position.toString())
        val snapped = step?.let { start + raw.subtract(start).divide(it, 0, RoundingMode.HALF_UP) * it } ?: rounded(raw)
        return generatedText(snapped.coerceIn(start, end))
    }

    fun stepText(text: String, direction: Int): String? {
        val increment = step ?: return null
        val parsed = NumberOptions().parse(text) ?: return null
        val value = parsed.content.toBigDecimalOrNull()?.takeIf { it.scale() in -400..400 }
            ?: BigDecimal.valueOf(parsed.content.toDouble())
        val stepped = value + increment * BigDecimal.valueOf(direction.toLong())
        val precision = ui.precision?.coerceIn(0, 16)
        val generated = if (integer || precision == null) stepped else
            stepped.setScale(maxOf(precision, increment.stripTrailingZeros().scale()), RoundingMode.HALF_UP)
        return generatedText(generated)
    }

    fun sliderLabel(value: Double): String {
        val formatted = rounded(BigDecimal.valueOf(value))
        return listOfNotNull(formatted.stripTrailingZeros().toPlainString(), ui.unit).joinToString(" ")
    }

    private fun rounded(value: BigDecimal): BigDecimal =
        ui.precision?.coerceIn(0, 16)?.let { value.setScale(it, RoundingMode.HALF_UP) } ?: value

    private fun generatedText(value: BigDecimal): String? {
        val numeric = value.toDouble()
        if (!numeric.isFinite()) return null
        val lower = hard?.min
        val upper = hard?.max
        if (lower != null && (numeric < lower || numeric == lower && hard.minExclusive)) return null
        if (upper != null && (numeric > upper || numeric == upper && hard.maxExclusive)) return null
        if (integer && value.stripTrailingZeros().scale() > 0) return null
        return value.stripTrailingZeros().toPlainString()
    }
}
