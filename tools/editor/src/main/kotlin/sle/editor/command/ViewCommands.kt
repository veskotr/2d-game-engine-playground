package sle.editor.command

/** View and Help commands. */
internal object ViewCommands {
    /** Frames all content; Frame Selected (F) is in [ToolCommands]. */
    val fit = EditorCommand(CommandId("view.fit"), "Fit", icon = "fit", enabled = { it.editable }) { it.actions.fit() }
    val keyboardShortcuts = EditorCommand(CommandId("help.keyboardShortcuts"), "Keyboard Shortcuts…", icon = "keyboard_shortcuts") { it.actions.showKeyboardShortcuts() }

    val runLua = EditorCommand(CommandId("view.runLua"), "Run Lua…",
        enabled = { it.mode == sle.editor.viewport.ViewportMode.Scene && it.scene != null }) { it.actions.showLuaConsole() }

    val all = listOf(fit, runLua, keyboardShortcuts)
}

/** Every built-in command. */
internal val standardCommands: CommandRegistry by lazy {
    CommandRegistry(DocumentCommands.all + EditCommands.all + PlayCommands.all + ViewCommands.all + ToolCommands.all + DetailsCommands.all)
}

/** Built-in chords. User overrides come with the rebinding UI (roadmap 1.8). */
internal val standardKeymap: Keymap by lazy { Keymap(standardCommands) }
