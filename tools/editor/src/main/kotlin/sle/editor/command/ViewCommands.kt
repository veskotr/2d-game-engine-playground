package sle.editor.command

/** View and Help commands. */
internal object ViewCommands {
    /** Frames all content. Frame Selected (F) arrives with the viewport tools. */
    val fit = EditorCommand(CommandId("view.fit"), "Fit", enabled = { it.editable }) { it.actions.fit() }
    val keyboardShortcuts = EditorCommand(CommandId("help.keyboardShortcuts"), "Keyboard Shortcuts…") { it.actions.showKeyboardShortcuts() }

    val all = listOf(fit, keyboardShortcuts)
}

/** Every built-in command. */
internal val standardCommands: CommandRegistry by lazy {
    CommandRegistry(DocumentCommands.all + EditCommands.all + PlayCommands.all + ViewCommands.all)
}

/** Built-in chords. User overrides come with the rebinding UI (roadmap 1.8). */
internal val standardKeymap: Keymap by lazy { Keymap(standardCommands) }
