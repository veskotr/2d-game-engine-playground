package sle.editor.profiler

import androidx.compose.runtime.*
import kotlinx.serialization.json.*

internal data class LuaProfileRow(
    val entity: Long,
    val behaviour: String,
    val source: String,
    val calls: List<Long>,
    val elapsed: List<Long>,
    val allocated: Long,
    val freed: Long,
) {
    val totalNs: Long get() = elapsed.sum()
}

internal data class LuaProfileSample(
    val scene: Long,
    val elapsed: Long,
    val gc: Long,
    val memory: Long,
    val omittedRows: Long,
    val rows: List<LuaProfileRow>,
)

internal class LuaProfilerState {
    var capture by mutableStateOf(false)
        private set
    var samples by mutableStateOf(emptyList<LuaProfileSample>())
        private set

    fun updateCapture(enabled: Boolean) {
        capture = enabled
        if (!enabled) clear()
    }

    fun clear() { samples = emptyList() }

    fun accept(bytes: ByteArray) {
        val fields = Json.parseToJsonElement(bytes.decodeToString()).jsonObject
        val scene = fields.number("scene")
        val rows = fields.getValue("rows").jsonArray.map { element ->
            val row = element.jsonObject
            LuaProfileRow(row.number("entity"), row.getValue("behaviour").jsonPrimitive.content,
                row.getValue("source").jsonPrimitive.content, row.numbers("calls"), row.numbers("elapsed"),
                row.number("allocated"), row.number("freed"))
        }.sortedByDescending { it.totalNs }
        val sample = LuaProfileSample(scene, fields.number("elapsed"), fields.number("gc"), fields.number("memory"),
            fields.number("omittedRows"), rows)
        val previous = samples.takeIf { it.lastOrNull()?.scene == scene }.orEmpty()
        samples = previous.takeLast(119) + sample
    }
}

private fun JsonObject.number(key: String) = getValue(key).jsonPrimitive.long
private fun JsonObject.numbers(key: String) = getValue(key).jsonArray.map { it.jsonPrimitive.long }
