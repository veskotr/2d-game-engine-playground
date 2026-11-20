package sle.editor.console

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import sle.editor.ui.controls.EditorLabel
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import sle.editor.ui.controls.EditorButton
import sle.editor.ui.controls.EditorButtonStyle
import sle.editor.ui.controls.EditorTextField
import sle.editor.ui.controls.EditorToggle
import sle.editor.ui.theme.LocalEditorColors
import sle.editor.ui.theme.LocalEditorTypography

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ConsolePanel(
    state: ConsoleState,
    clear: () -> Unit,
    canRun: Boolean,
    submit: (String) -> Unit,
    onOpenSource: (LuaSource) -> Unit,
    onConfigureEditor: () -> Unit,
) {
    val colors = LocalEditorColors.current
    val type = LocalEditorTypography.current
    var search by remember { mutableStateOf(TextFieldValue(state.query)) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(state.inputRequest) { if (state.inputRequest > 0) focus.requestFocus() }
    var input by remember(state) { mutableStateOf(TextFieldValue(state.commandDraft)) }
    var historyIndex by remember(state) { mutableStateOf(state.commandHistory.size) }
    var unsent by remember(state) { mutableStateOf(state.commandDraft) }
    fun replaceInput(text: String) {
        state.commandDraft = text
        input = TextFieldValue(text, TextRange(text.length))
    }
    val runEnabled = canRun && !state.commandRunning && input.text.isNotBlank()
    fun run() {
        if (!runEnabled) return
        val source = input.text
        if (source.encodeToByteArray().size > 256 * 1024) {
            state.commandMessage("Lua command exceeds the size limit", ConsoleSeverity.ERROR)
            return
        }
        state.recordCommand(source)
        historyIndex = state.commandHistory.size
        unsent = ""
        replaceInput("")
        submit(source)
    }
    val visible by remember(state) {
        derivedStateOf {
            state.rows.filter { (entry) ->
                entry.severity in state.severities &&
                    (entry.message.contains(state.query, ignoreCase = true) ||
                        entry.lua?.let { it.location.contains(state.query, true) ||
                            it.traceback.contains(state.query, true) || it.behaviour.contains(state.query, true) } == true)
            }
        }
    }
    Column(Modifier.fillMaxSize().padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            EditorTextField(search, { search = it; state.query = it.text }, "Search console",
                Modifier.width(150.dp), placeholder = "Search")
            for (severity in ConsoleSeverity.entries) {
                EditorToggle(severity.name, severity in state.severities, { show ->
                    state.severities = if (show) state.severities + severity else state.severities - severity
                })
            }
            EditorButton("Clear", clear)
            EditorButton("Configure Script Editor…", onConfigureEditor)
            EditorToggle("Clear on Play", state.clearOnPlay, state::updateClearOnPlay)
            EditorToggle("Pause on Lua Error", state.pauseOnLuaError, state::updatePauseOnLuaError)
        }
        if (state.dropped > 0)
            EditorLabel("${state.dropped} older messages dropped", color = colors.warning, style = type.body)
        if (visible.isEmpty())
            EditorLabel(if (state.rows.isEmpty()) "No engine messages" else "No matching messages",
                color = colors.textSecondary, style = type.body)
        SelectionContainer(Modifier.weight(1f)) {
            LazyColumn(Modifier.fillMaxSize(), reverseLayout = true, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(visible.asReversed()) { (entry, count) ->
                    val tint = when (entry.severity) {
                        ConsoleSeverity.ERROR -> colors.error
                        ConsoleSeverity.WARN -> colors.warning
                        ConsoleSeverity.INFO -> colors.info
                        ConsoleSeverity.DEBUG -> colors.textSecondary
                    }
                    Column {
                        EditorLabel("${entry.severity.name}  ${entry.message}" + if (count > 1) "  ×$count" else "",
                            color = tint, style = type.body)
                        entry.lua?.let { source ->
                            val context = listOfNotNull(source.entity.takeIf { it != 0L }?.let { "Entity $it" },
                                source.behaviour.takeIf { it.isNotEmpty() }).joinToString(" · ")
                            if (context.isNotEmpty()) EditorLabel(context, color = colors.textSecondary, style = type.body)
                            if (source.file.isNotEmpty()) {
                                val filename = remember(source.file) { source.path?.fileName?.toString() ?: source.file }
                                val label = if (source.line > 0) "$filename:${source.line}" else filename
                                EditorButton("Open $label", {
                                    onOpenSource(source)
                                }, help = source.location, style = EditorButtonStyle.Ghost)
                            }
                            if (source.traceback.isNotEmpty())
                                EditorLabel(source.traceback, color = colors.textSecondary, style = type.body)
                        }
                    }
                }
            }
        }
        EditorTextField(input, {
            input = it
            state.commandDraft = it.text
            historyIndex = state.commandHistory.size
        }, "Lua command", Modifier.fillMaxWidth().heightIn(min = 52.dp, max = 120.dp),
            placeholder = "Lua command", singleLine = false, focusRequester = focus, onEditKey = {
                if (it.type == KeyEventType.KeyDown && (it.isCtrlPressed || it.isMetaPressed) && it.key == Key.Enter) {
                    run()
                    true
                } else false
            })
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            EditorButton("Run Lua", ::run, enabled = runEnabled, help = "Ctrl+Enter")
            EditorButton("Previous", {
                if (historyIndex == state.commandHistory.size) unsent = input.text
                historyIndex--
                replaceInput(state.commandHistory[historyIndex])
            }, enabled = historyIndex > 0, help = "Previous Lua command")
            EditorButton("Next", {
                historyIndex++
                replaceInput(state.commandHistory.getOrNull(historyIndex) ?: unsent)
            }, enabled = historyIndex < state.commandHistory.size, help = "Next Lua command")
        }

    }
}
