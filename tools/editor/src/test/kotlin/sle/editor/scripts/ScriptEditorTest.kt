package sle.editor.scripts

import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.add
import sle.editor.console.ConsoleState
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.test.*
import org.junit.Rule
import org.junit.rules.TemporaryFolder

class ScriptEditorTest {
    @get:Rule val temp = TemporaryFolder()

    @Test
    fun savedPreferenceOverridesEnvironmentUntilReset() {
        val store = ScriptEditorSettingsStore(temp.root.toPath())
        val source = Path.of("main script.lua")
        val fallback = ScriptEditor(store, mapOf("SLE_SCRIPT_EDITOR" to "environment editor"), windows = false)
        assertEquals(listOf("environment editor", "--goto", "main script.lua:17"), fallback.command(source, 17))
        store.save(ScriptEditorSettings("local editor", listOf("--open={file}"), listOf("-l", "{line}", "{file}")))
        assertEquals(listOf("local editor", "-l", "17", "main script.lua"), fallback.command(source, 17))
        assertEquals(listOf("local editor", "--open=main script.lua"), fallback.command(source, 0))
        store.reset()
        assertEquals("environment editor", fallback.command(source, 0).first())
        assertEquals("code", ScriptEditor(store, emptyMap(), windows = false).command(source, 0).first())
    }

    @Test
    fun fileArgumentsAlsoServeEditorsWithoutLineSupport() {
        val store = ScriptEditorSettingsStore(temp.root.toPath())
        store.save(ScriptEditorSettings("editor", listOf("{file}"), null))
        assertEquals(listOf("editor", "main script.lua"), ScriptEditor(store).command(Path.of("main script.lua"), 3))
    }

    @Test
    fun invalidSavedSettingsReportAnErrorAndCanBeReset() {
        val directory = temp.root.toPath()
        val store = ScriptEditorSettingsStore(directory)
        assertFailsWith<IllegalArgumentException> { store.save(ScriptEditorSettings("", listOf("{file}"), null)) }
        Files.writeString(directory.resolve("script-editor.json"), """{"executable":"editor","fileArguments":[42]}""")
        assertFailsWith<IllegalArgumentException> { ScriptEditor(store).command(Path.of("main.lua"), 0) }
        store.reset()
        assertNull(store.load())
    }

    @Test
    fun discoversCodeExecutableAboveItsBinFolder() {
        val installation = Files.createDirectory(temp.root.toPath().resolve("VS Code"))
        val bin = Files.createDirectory(installation.resolve("bin"))
        val executable = Files.createFile(installation.resolve("Code.exe"))
        val store = ScriptEditorSettingsStore(temp.root.toPath())
        val editor = ScriptEditor(store, mapOf("PATH" to bin.toString()), windows = true)
        assertEquals(listOf(executable.toString(), "--goto", "main {line}.lua:17"),
            editor.command(Path.of("main {line}.lua"), 17))
    }

    @Test
    fun defaultLineArgumentsKeepPathsWithSpacesTogether() {
        if (!Files.isExecutable(Path.of("/bin/sh"))) return
        val directory = Files.createDirectory(temp.root.toPath().resolve("Path with spaces"))
        val source = Files.writeString(directory.resolve("script with spaces.lua"), "return true")
        val executable = Files.writeString(directory.resolve("fake editor"), """
            #!/bin/sh
            printf '%s\n' "$@" > "${'$'}(dirname "$0")/arguments.txt"
        """.trimIndent())
        assertTrue(executable.toFile().setExecutable(true))
        val editor = ScriptEditor(ScriptEditorSettingsStore(directory), mapOf("SLE_SCRIPT_EDITOR" to executable.toString()))
        val console = ConsoleState(persistPreferences = false)
        val batch = buildJsonObject {
            put("dropped", 0)
            put("messages", buildJsonArray {
                add(buildJsonObject {
                    put("level", "ERROR")
                    put("message", "Lua error")
                    put("lua", buildJsonObject {
                        put("file", source.toString())
                        put("line", 17)
                        put("entity", 1)
                        put("behaviour", "Test")
                        put("traceback", "")
                    })
                })
            })
        }
        console.accept(batch.toString().encodeToByteArray())
        val diagnostic = assertNotNull(console.rows.single().entry.lua)
        val process = editor.open(diagnostic.path, diagnostic.line)
        assertTrue(process.waitFor(5, TimeUnit.SECONDS))
        assertEquals(0, process.exitValue())
        assertEquals(listOf("--goto", "$source:17"), Files.readAllLines(directory.resolve("arguments.txt")))
    }
}
