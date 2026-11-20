package sle.editor.scripts

import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.test.*
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import sle.editor.project.ProjectFiles
import sle.editor.project.ProjectWorkspaceState
import sle.editor.project.assets.AssetIdentity

class ScriptSourceActionsTest {
    @get:Rule val temp = TemporaryFolder()

    @Test
    fun detailsAndAssetsOpenTheCurrentSourceWithFileArguments() {
        if (!Files.isExecutable(Path.of("/bin/sh"))) return
        val directory = temp.root.toPath()
        val capture = directory.resolve("arguments.txt")
        val executable = Files.writeString(directory.resolve("test editor"), """
            #!/bin/sh
            printf '%s\n' "$@" > "$capture"
        """.trimIndent())
        assertTrue(executable.toFile().setExecutable(true))
        val settings = ScriptEditorSettingsStore(directory)
        settings.save(ScriptEditorSettings.defaults(executable.toString()))
        val workspace = ProjectWorkspaceState()
        val project = ProjectFiles.create(directory.resolve("Game"), "Game")
        workspace.activate(project)
        val source = Files.writeString(project.assets.resolve("Scripts/with spaces.lua"), "return true")
        val id = AssetIdentity.ensure(source)
        val moved = Files.move(source, source.resolveSibling("renamed script.lua"))
        Files.move(AssetIdentity.sidecar(source), AssetIdentity.sidecar(moved))
        val actions = ScriptSourceActions(workspace, ScriptEditor(settings))

        fun verify(process: Process, expected: Path) {
            assertTrue(process.waitFor(5, TimeUnit.SECONDS))
            assertEquals(0, process.exitValue())
            assertEquals(listOf(expected.toString()), Files.readAllLines(capture))
        }

        verify(actions.openAttachment("project-asset://$id"), moved)
        verify(actions.openAttachment(moved.toString()), moved)
        verify(actions.openAttachment("Scripts/renamed script.lua"), moved)
        verify(actions.openAsset("Scripts/with spaces.lua", id), moved)
        verify(actions.openAsset("Scripts/renamed script.lua", null), moved)
        val local = Files.writeString(directory.resolve("local script.lua"), "return true")
        workspace.close()
        verify(ScriptSourceActions(workspace, ScriptEditor(settings)).openAttachment(local.toString()), local)
    }

    @Test
    fun missingAndStaleTargetsFailBeforeLaunch() {
        val directory = temp.root.toPath()
        val workspace = ProjectWorkspaceState()
        val project = ProjectFiles.create(directory.resolve("Game"), "Game")
        workspace.activate(project)
        val actions = ScriptSourceActions(workspace, ScriptEditor(ScriptEditorSettingsStore(directory)))
        assertContains(assertFailsWith<IllegalArgumentException> {
            actions.openAttachment(directory.resolve("absent.lua").toString())
        }.message.orEmpty(), "unavailable")
        assertContains(assertFailsWith<IllegalArgumentException> {
            actions.openAsset("Scripts/absent.lua", null)
        }.message.orEmpty(), "unavailable")
        assertContains(assertFailsWith<IllegalStateException> {
            actions.openAttachment("project-asset://missing")
        }.message.orEmpty(), "Missing")
        workspace.close()
        workspace.activate(project)
        assertContains(assertFailsWith<IllegalArgumentException> {
            actions.openAttachment("project-asset://missing")
        }.message.orEmpty(), "project changed")
        assertContains(assertFailsWith<IllegalArgumentException> {
            actions.openAsset("Scripts/absent.lua", null)
        }.message.orEmpty(), "project changed")
    }
}
