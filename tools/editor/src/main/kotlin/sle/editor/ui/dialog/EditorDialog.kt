package sle.editor.ui.dialog

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.dp
import sle.editor.ui.EditorTheme
import sle.editor.ui.controls.*
import sle.editor.ui.theme.*

/** What a footer button does when the keyboard is used: Enter runs [Default], Esc runs [Cancel]. */
internal enum class DialogActionRole { Default, Cancel, Other }

internal data class DialogAction(
    val label: String,
    val role: DialogActionRole = DialogActionRole.Other,
    val style: EditorButtonStyle = when (role) {
        DialogActionRole.Default -> EditorButtonStyle.Primary
        else -> EditorButtonStyle.Secondary
    },
    val enabled: Boolean = true,
    val run: () -> Unit,
)

/**
 * Footer order, left to right: other actions, then Cancel, then the default action, so the
 * primary button is always rightmost with Cancel beside it.
 */
internal fun footerOrder(actions: List<DialogAction>): List<DialogAction> =
    actions.filter { it.role == DialogActionRole.Other } +
        actions.filter { it.role == DialogActionRole.Cancel } +
        actions.filter { it.role == DialogActionRole.Default }

/** The key a dialog handles itself, or null to let the focused control have it. */
internal fun dialogKeyAction(actions: List<DialogAction>, key: Key, enterSubmits: Boolean = true): DialogAction? = when (key) {
    Key.Escape -> actions.firstOrNull { it.role == DialogActionRole.Cancel }
    Key.Enter, Key.NumPadEnter -> if (enterSubmits) actions.firstOrNull { it.role == DialogActionRole.Default && it.enabled } else null
    else -> null
}

/**
 * The one dialog layout: a padded, scrollable body above a right-aligned footer.
 * Esc runs the cancel action and Enter the default action (unless [enterSubmits] is false,
 * e.g. while a multi-line field has focus).
 */
@Composable
internal fun EditorDialogLayout(
    actions: List<DialogAction>,
    modifier: Modifier = Modifier,
    enterSubmits: Boolean = true,
    scrollBody: Boolean = true,
    body: @Composable ColumnScope.() -> Unit,
) {
    val metrics = LocalEditorMetrics.current
    val current by rememberUpdatedState(actions)
    Column(modifier.onPreviewKeyEvent { event ->
        if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
        val action = dialogKeyAction(current, event.key, enterSubmits) ?: return@onPreviewKeyEvent false
        action.run()
        true
    }) {
        Column(Modifier.heightIn(max = 520.dp).then(if (scrollBody) Modifier.verticalScroll(rememberScrollState()) else Modifier)
            .padding(metrics.panelPadding * 1.5f), verticalArrangement = Arrangement.spacedBy(metrics.gap * 1.5f), content = body)
        // Keeps the footer at the bottom of a fixed-size window; zero height when the window packs to its content.
        Spacer(Modifier.weight(1f))
        EditorSeparator()
        Row(Modifier.fillMaxWidth().padding(horizontal = metrics.panelPadding * 1.5f, vertical = metrics.panelPadding),
            horizontalArrangement = Arrangement.spacedBy(metrics.gap, Alignment.End), verticalAlignment = Alignment.CenterVertically) {
            footerOrder(actions).forEach { action ->
                EditorButton(action.label, action.run, enabled = action.enabled, style = action.style)
            }
        }
    }
}

/** Window content for a dialog: the editor theme, a surface and [EditorDialogLayout]. */
@Composable
internal fun EditorDialogSurface(appearance: EditorAppearance, content: @Composable () -> Unit) {
    EditorTheme(appearance) { Surface(color = LocalEditorColors.current.surface) { content() } }
}

/** The kind of answer a prompt collects besides its button. */
internal enum class PromptKind { Message, Confirm, Choice, Input, Select, List }

internal data class PromptButton(val label: String, val role: DialogActionRole, val danger: Boolean = false)

/** Everything a prompt shows. Pure data, so tests and smokes can inspect it. */
internal data class PromptRequest(
    val title: String,
    val message: String,
    val kind: PromptKind,
    val buttons: List<PromptButton>,
    val label: String = "",
    val initial: String = "",
    /** Select choices or List items. */
    val items: List<String> = emptyList(),
    val initialItem: Int = 0,
    val validate: (String) -> String? = { null },
    /** Extra content below the message (e.g. the shortcut table). */
    val body: (@Composable () -> Unit)? = null,
)

/** The answer: the index of the pressed button in [PromptRequest.buttons], or -1 when closed. */
internal data class PromptResult(val button: Int, val text: String = "", val item: Int = -1)

/** One open prompt. Smokes find it as [EditorPrompts.current] and answer it like a user would. */
internal class PromptHandle(val request: PromptRequest) {
    var text by mutableStateOf(TextFieldValue(request.initial, TextRange(0, request.initial.length)))
    var item by mutableStateOf(request.initialItem)
    var result: PromptResult? = null
        private set
    internal var close: () -> Unit = {}
    val error get() = if (request.kind == PromptKind.Input) request.validate(text.text) else null
    fun canAnswer(button: Int) = button < 0 || request.buttons[button].role == DialogActionRole.Cancel || error == null &&
        (request.kind != PromptKind.List || item in request.items.indices || request.buttons[button].role != DialogActionRole.Default)
    /** Presses the button at [button] (-1 closes the window). Ignored while the answer is invalid. */
    fun answer(button: Int) {
        if (result != null || !canAnswer(button)) return
        result = PromptResult(button, text.text, item)
        close()
    }
    /** Presses the button labelled [label], first setting the text field when [text] is given. */
    fun answer(label: String, text: String? = null) {
        if (text != null) this.text = TextFieldValue(text)
        answer(request.buttons.indexOfFirst { it.label == label }.also { require(it >= 0) { "No $label button in ${request.title}" } })
    }
}

/**
 * Modal editor prompts on the [EditorDialogLayout] shell. Each call blocks the event-dispatch
 * thread in a nested loop, as a Swing modal dialog does, and returns the answer.
 */
internal object EditorPrompts {
    /** The prompt on screen, if any (read by smokes). */
    @Volatile var current: PromptHandle? = null
        private set
    /** Shows a prompt and returns once it is answered. Tests replace it. */
    internal var presenter: (PromptHandle) -> Unit = ::presentPrompt

    internal fun show(request: PromptRequest): PromptResult {
        val handle = PromptHandle(request)
        val previous = current
        current = handle
        try { presenter(handle) } finally { current = previous }
        return handle.result ?: PromptResult(-1, handle.text.text, handle.item)
    }

    private fun cancel(label: String = "Cancel") = PromptButton(label, DialogActionRole.Cancel)
    private fun ok(label: String, danger: Boolean = false) = PromptButton(label, DialogActionRole.Default, danger)

    /** True when the confirm button is pressed. */
    fun confirm(title: String, message: String, confirm: String = "OK", cancel: String = "Cancel", danger: Boolean = false): Boolean =
        show(PromptRequest(title, message, PromptKind.Confirm, listOf(ok(confirm, danger), cancel(cancel)))).button == 0

    fun message(title: String, message: String, close: String = "Close", body: (@Composable () -> Unit)? = null) {
        show(PromptRequest(title, message, PromptKind.Message, listOf(PromptButton(close, DialogActionRole.Default)), body = body))
    }

    /**
     * Index into [options] of the pressed button, or -1 for [cancel] or closing the window.
     * [default] is the Enter button; it is drawn as the primary (or [danger]) button.
     */
    fun choose(title: String, message: String, options: List<String>, default: Int = 0, cancel: String? = "Cancel",
        danger: Boolean = false): Int {
        require(default in options.indices)
        val buttons = options.mapIndexed { index, label ->
            if (index == default) ok(label, danger) else PromptButton(label, DialogActionRole.Other)
        } + listOfNotNull(cancel?.let(::cancel))
        return show(PromptRequest(title, message, PromptKind.Choice, buttons)).button.takeIf { it in options.indices } ?: -1
    }

    /** The entered text, or null when cancelled. [validate] returns an error to show, which also disables OK. */
    fun input(title: String, label: String, initial: String = "", message: String = "", confirm: String = "OK",
        validate: (String) -> String? = { null }): String? {
        val result = show(PromptRequest(title, message, PromptKind.Input, listOf(ok(confirm), cancel()), label, initial, validate = validate))
        return result.text.takeIf { result.button == 0 }
    }

    /** One of [choices], or null when cancelled. */
    fun select(title: String, label: String, choices: List<String>, initial: String? = null, message: String = "",
        confirm: String = "OK"): String? {
        require(choices.isNotEmpty())
        val result = show(PromptRequest(title, message, PromptKind.Select, listOf(ok(confirm), cancel()), label,
            items = choices, initialItem = choices.indexOf(initial).coerceAtLeast(0)))
        return choices.getOrNull(result.item).takeIf { result.button == 0 }
    }

    /** Index of the chosen [items] entry when [confirm] is pressed, or null. */
    fun pickFromList(title: String, message: String, items: List<String>, confirm: String, cancel: String = "Close"): Int? {
        val result = show(PromptRequest(title, message, PromptKind.List, listOf(ok(confirm), cancel(cancel)), items = items))
        return result.item.takeIf { result.button == 0 && it in items.indices }
    }
}

/** The body of a prompt: message, then the field the kind needs. */
@Composable
internal fun PromptContent(handle: PromptHandle) {
    val request = handle.request
    val colors = LocalEditorColors.current
    val metrics = LocalEditorMetrics.current
    val rootFocus = remember { FocusRequester() }
    val fieldFocus = remember { FocusRequester() }
    val actions = request.buttons.mapIndexed { index, button ->
        DialogAction(button.label, button.role,
            style = when {
                button.role == DialogActionRole.Default && button.danger -> EditorButtonStyle.Danger
                button.role == DialogActionRole.Default -> EditorButtonStyle.Primary
                else -> EditorButtonStyle.Secondary
            }, enabled = handle.canAnswer(index)) { handle.answer(index) }
    }
    EditorDialogLayout(actions, Modifier.widthIn(min = 380.dp, max = 560.dp).focusRequester(rootFocus).focusable(),
        scrollBody = request.kind != PromptKind.List) {
        if (request.message.isNotBlank()) EditorLabel(request.message)
        when (request.kind) {
            PromptKind.Input -> {
                EditorFormRow(request.label) {
                    EditorTextField(handle.text, { handle.text = it }, request.label, Modifier.weight(1f),
                        invalid = handle.error != null, focusRequester = fieldFocus)
                }
                handle.error?.let { EditorLabel(it, color = colors.error) }
            }
            PromptKind.Select -> EditorFormRow(request.label) {
                EditorSelect(request.label, request.items[handle.item], request.items,
                    { handle.item = request.items.indexOf(it) }, Modifier.weight(1f), showLabel = false)
            }
            PromptKind.List -> LazyColumn(Modifier.fillMaxWidth().heightIn(max = 240.dp)) {
                itemsIndexed(request.items) { index, item ->
                    EditorTreeRow(item, handle.item == index, { handle.item = index }, help = item)
                }
            }
            else -> Unit
        }
        request.body?.invoke()
    }
    // Input prompts focus their field; the rest focus the dialog so Enter and Esc work at once.
    LaunchedEffect(Unit) {
        withFrameNanos { }
        runCatching { (if (request.kind == PromptKind.Input) fieldFocus else rootFocus).requestFocus() }
    }
}
