package sle.editor.profiler

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.dp
import sle.editor.console.LuaSource
import sle.editor.ui.controls.EditorButton
import sle.editor.ui.controls.EditorButtonStyle
import sle.editor.ui.controls.EditorToggle
import sle.editor.ui.theme.LocalEditorColors
import sle.editor.ui.theme.LocalEditorTypography
import sle.editor.viewport.LiveInspection
import sle.editor.viewport.LiveSelection
import java.util.Locale

@Composable
internal fun LuaProfilerPanel(
    state: LuaProfilerState,
    live: LiveInspection?,
    onEntity: (LiveSelection) -> Unit,
    onOpenSource: (LuaSource) -> Unit,
) {
    val type = LocalEditorTypography.current
    val colors = LocalEditorColors.current
    val samples = state.samples
    val latest = samples.lastOrNull()
    Column(Modifier.fillMaxSize().padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        EditorToggle("Capture Lua", state.capture, state::updateCapture)
        if (latest == null) {
            Text("Enable capture and run or step the scene to collect samples.", color = colors.textSecondary, style = type.body)
            return@Column
        }
        val peak = samples.maxOf { it.elapsed }.coerceAtLeast(1L)
        Text("Lua ${milliseconds(latest.elapsed)} ms · Peak ${milliseconds(peak)} ms · VM ${latest.memory / 1024} KiB",
            color = colors.textPrimary, style = type.body)
        Canvas(Modifier.fillMaxWidth().height(44.dp)) {
            val width = size.width / 120
            samples.forEachIndexed { index, sample ->
                val height = size.height * (sample.elapsed.toDouble() / peak).toFloat()
                val x = size.width - width * (samples.size - index)
                drawRect(colors.info, Offset(x, size.height - height), Size(width, height))
            }
        }
        Text("Explicit GC ${milliseconds(latest.gc)} ms · Automatic GC is included in Lua time",
            color = colors.textSecondary, style = type.body)
        if (latest.omittedRows > 0)
            Text("${latest.omittedRows} lower-time rows omitted", color = colors.textSecondary, style = type.body)
        val entities = live?.takeIf { it.scene == latest.scene }?.entities?.associateBy { it.id }.orEmpty()
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(latest.rows) { row ->
                val entity = entities[row.entity]
                val label = entity?.label ?: if (row.entity == 0L) "Scene" else "Entity #${row.entity}"
                val behaviour = row.behaviour.ifBlank { if (row.entity == 0L) "Globals" else "Primary" }
                Column {
                    Text("$label · $behaviour · ${milliseconds(row.totalNs)} ms", color = colors.textPrimary, style = type.body)
                    val phases = listOf("Init", "Update", "Callback", "Chunk").mapIndexed { index, phase ->
                        "$phase ${row.calls[index]} (${milliseconds(row.elapsed[index])} ms)"
                    }
                    Text(phases.joinToString(" · "), color = colors.textSecondary, style = type.body)
                    Text("Allocated ${row.allocated} B · Freed ${row.freed} B",
                        color = colors.textSecondary, style = type.body)
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (entity != null && live != null)
                            EditorButton("Select entity", { onEntity(live.selectionFor(entity)) }, style = EditorButtonStyle.Ghost)
                        if (row.source.isNotBlank()) {
                            val source = LuaSource(row.source, 0, row.entity, row.behaviour, "")
                            EditorButton("Open ${source.path?.fileName ?: "script"}", {
                                onOpenSource(source)
                            }, help = row.source, style = EditorButtonStyle.Ghost)
                        }
                    }
                }
            }
        }
    }
}

private fun milliseconds(ns: Long) = String.format(Locale.ROOT, "%.3f", ns / 1_000_000.0)
