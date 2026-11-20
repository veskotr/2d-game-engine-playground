package sle.editor.ui.inspector

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import sle.editor.command.CommandContext
import sle.editor.command.EditorCommand
import sle.editor.command.DetailsCommands
import sle.editor.command.DetailsTarget
import sle.editor.command.menuEntry
import sle.editor.model.EntityWorkspaceState
import sle.editor.ui.editorContextMenu

internal val LocalDetailsContext = staticCompositionLocalOf<(() -> CommandContext)?> { null }

@Composable
internal fun detailsPropertyMenu(
    state: EntityWorkspaceState,
    property: EditorPropertyDefinition,
    readOnly: Boolean,
): Modifier {
    return detailsMenu(state, property.component, property.path, readOnly, DetailsCommands.propertyActions) { context, mounted ->
        DetailsTarget(state, property, readOnly, context, mounted)
    }
}

@Composable
internal fun detailsComponentMenu(state: EntityWorkspaceState, component: String, readOnly: Boolean): Modifier {
    if (component !in state.catalog) return Modifier
    return detailsMenu(state, component, emptyList(), readOnly, DetailsCommands.componentActions) { context, mounted ->
        DetailsTarget(state, component, readOnly, context, mounted = mounted)
    }
}

@Composable
private fun detailsMenu(
    state: EntityWorkspaceState,
    component: String,
    path: List<String>,
    readOnly: Boolean,
    commands: List<EditorCommand>,
    createTarget: (CommandContext, () -> Boolean) -> DetailsTarget,
): Modifier {
    val context = LocalDetailsContext.current ?: return Modifier
    val liveContext by rememberUpdatedState(context)
    val mounted = remember(state, component, path, readOnly) { mutableStateOf(true) }
    DisposableEffect(mounted) { onDispose { mounted.value = false } }
    return Modifier.editorContextMenu {
        val base = liveContext()
        val target = createTarget(base) { mounted.value }
        commands.map { command ->
            command.menuEntry(base.copy(details = target), liveContext = { liveContext().copy(details = target) })
        }
    }
}
