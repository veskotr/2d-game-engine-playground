package sle.editor.scripts

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import sle.editor.command.CommandContext
import sle.editor.ui.controls.EditorButton
import sle.editor.ui.theme.LocalEditorColors
import sle.editor.ui.theme.LocalEditorMetrics

@Composable
internal fun ProjectLuaPanelContent(
    panel: ProjectLuaPanel,
    context: CommandContext,
    liveContext: () -> CommandContext,
) {
    val metrics = LocalEditorMetrics.current
    Surface(Modifier.fillMaxSize(), color = LocalEditorColors.current.background) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(metrics.panelPadding),
            verticalArrangement = Arrangement.spacedBy(metrics.gap),
        ) {
            for (widget in panel.widgets) {
                when (widget) {
                    is ProjectPanelWidget.Label -> Text(widget.text)
                    is ProjectPanelWidget.Button -> {
                        val command = widget.command.command
                        EditorButton(command.label, {
                            val current = liveContext()
                            if (command.enabled(current)) command.run(current)
                        }, enabled = command.enabled(context))
                    }
                }
            }
        }
    }
}
