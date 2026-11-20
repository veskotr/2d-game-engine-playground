package sle.editor.scripts

import java.io.File
import java.nio.file.Files
import java.nio.file.Path

internal data class ScriptEditorSettings(
    val executable: String,
    val fileArguments: List<String>,
    val lineArguments: List<String>?,
) {
    init {
        require(executable.isNotBlank() && '\u0000' !in executable) { "Enter an IDE executable." }
        require(fileArguments.any { "{file}" in it } && fileArguments.none { "{line}" in it }) {
            "File arguments must contain {file} and cannot use {line}."
        }
        require(lineArguments == null ||
            (lineArguments.any { "{file}" in it } && lineArguments.any { "{line}" in it })) {
            "Line arguments must contain {file} and {line}."
        }
        require((fileArguments + lineArguments.orEmpty()).none { '\u0000' in it }) {
            "IDE arguments cannot contain NUL characters."
        }
    }

    companion object {
        fun defaults(executable: String) = ScriptEditorSettings(executable, listOf("{file}"), listOf("--goto", "{file}:{line}"))
    }
}

internal class ScriptEditor(
    private val store: ScriptEditorSettingsStore = ScriptEditorSettingsStore(),
    private val environment: Map<String, String> = System.getenv(),
    private val windows: Boolean = System.getProperty("os.name").startsWith("Windows"),
) {
    fun command(file: Path, line: Int): List<String> {
        val settings = store.load() ?: ScriptEditorSettings.defaults(defaultExecutable())
        val arguments = if (line > 0) settings.lineArguments ?: settings.fileArguments else settings.fileArguments
        return listOf(settings.executable) + arguments.map { argument ->
            placeholders.replace(argument) { match ->
                if (match.value == "{file}") file.toString() else line.toString()
            }
        }
    }

    fun open(file: Path?, line: Int = 0): Process {
        requireNotNull(file) { "This Lua entry has no valid source file." }
        val source = file.toAbsolutePath().normalize()
        require(Files.isRegularFile(source)) { "Source file is unavailable: $source" }
        return ProcessBuilder(command(source, line)).start()
    }

    private fun defaultExecutable(): String {
        environment["SLE_SCRIPT_EDITOR"]?.takeIf { it.isNotBlank() }?.let { return it }
        if (!windows) return "code"

        // VS Code puts code.cmd in bin. Find Code.exe to launch it directly.
        val search = environment["PATH"].orEmpty().split(File.pathSeparatorChar).asSequence()
            .filter { it.isNotBlank() }
            .mapNotNull { runCatching { Path.of(it.trim('"')) }.getOrNull() }
            .flatMap { sequenceOf(it.resolve("Code.exe"), it.resolve("../Code.exe")) }
        val installations = sequenceOf(
            environment["LOCALAPPDATA"]?.let { Path.of(it, "Programs", "Microsoft VS Code", "Code.exe") },
            environment["ProgramFiles"]?.let { Path.of(it, "Microsoft VS Code", "Code.exe") },
        ).filterNotNull()
        return (search + installations).firstOrNull { Files.isRegularFile(it) }?.normalize()?.toString()
            ?: error("VS Code was not found. Configure Script Editor… in the Console or set SLE_SCRIPT_EDITOR.")
    }

    private companion object {
        val placeholders = Regex("""\{(?:file|line)\}""")
    }
}
