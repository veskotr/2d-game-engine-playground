package sle.editor.command

/** All commands, once each. Default chords are checked for conflicts on registration. */
internal class CommandRegistry(val commands: List<EditorCommand>) {
    val byId: Map<CommandId, EditorCommand> = commands.associateBy { it.id }
    init {
        require(byId.size == commands.size) {
            "Duplicate command id: " + commands.groupBy { it.id }.filterValues { it.size > 1 }.keys.joinToString { it.value }
        }
        Keymap.requireNoConflicts(commands.associateWith { it.defaultChords })
    }
    operator fun get(id: CommandId) = byId[id] ?: error("Unknown command ${id.value}")
}

/**
 * Default chords plus user overrides (an empty list unbinds a command). L1 has no rebinding UI
 * or override file; the override map is the data model those will write.
 */
internal class Keymap(val registry: CommandRegistry, overrides: Map<CommandId, List<KeyChord>> = emptyMap()) {
    private val bindings: Map<EditorCommand, List<KeyChord>>
    init {
        overrides.keys.forEach { require(it in registry.byId) { "Override for unknown command ${it.value}" } }
        bindings = registry.commands.associateWith { overrides[it.id] ?: it.defaultChords }
        requireNoConflicts(bindings)
    }
    private val byChord: Map<KeyChord, List<EditorCommand>> = bindings.entries
        .flatMap { (command, chords) -> chords.distinct().map { it to command } }
        .groupBy({ it.first }, { it.second })

    fun chords(id: CommandId): List<KeyChord> = bindings.getValue(registry[id])
    /** The chord menus and tooltips show. */
    fun primary(id: CommandId): KeyChord? = chords(id).firstOrNull()
    fun commandsFor(chord: KeyChord): List<EditorCommand> = byChord[chord].orEmpty()
    fun withOverrides(overrides: Map<CommandId, List<KeyChord>>) = Keymap(registry, overrides)
    /** "Undo (Ctrl+Z)" for tooltips. */
    fun hint(id: CommandId, text: String = registry[id].label) = primary(id)?.let { "$text (${it.display()})" } ?: text

    companion object {
        /** Two commands may share a chord only when no panel can have both scopes active. */
        fun conflicts(bindings: Map<EditorCommand, List<KeyChord>>): List<String> {
            val pairs = bindings.entries.flatMap { (command, chords) -> chords.distinct().map { it to command } }
            return pairs.groupBy({ it.first }, { it.second }).flatMap { (chord, commands) ->
                commands.indices.flatMap { i -> (i + 1 until commands.size).mapNotNull { j ->
                    val a = commands[i];val b = commands[j]
                    if(a.scopes.any { left -> b.scopes.any(left::overlaps) })
                        "${chord.display()} is bound to both ${a.id.value} and ${b.id.value} in overlapping scopes" else null
                } }
            }
        }
        fun requireNoConflicts(bindings: Map<EditorCommand, List<KeyChord>>) {
            val found = conflicts(bindings)
            require(found.isEmpty()) { found.joinToString("; ") }
        }
    }
}
