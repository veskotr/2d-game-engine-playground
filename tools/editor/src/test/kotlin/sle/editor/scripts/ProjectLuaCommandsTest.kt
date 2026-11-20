package sle.editor.scripts

import java.nio.file.Files
import kotlin.test.*
import sle.editor.project.OpenedProject
import sle.editor.project.ProjectDocument

class ProjectLuaCommandsTest {
    @Test
    fun trustIsLocalToIdentityAndDescriptorPath() {
        val root = Files.createTempDirectory("sle-script-trust")
        val directory = Files.createDirectory(root.resolve("project"))
        val descriptor = Files.createFile(directory.resolve("Game.sleproject"))
        val project = OpenedProject(descriptor, ProjectDocument(name = "Game"))
        val store = ProjectScriptTrust(root.resolve("local"))
        assertNull(store.read(project))
        store.write(project, true)
        assertEquals(true, store.read(project))
        assertNull(store.read(project.copy(document = ProjectDocument(name = "Other"))))
        assertNull(store.read(project.copy(file = Files.createFile(directory.resolve("Copy.sleproject")))))
        store.write(project, false)
        assertEquals(false, store.read(project))
        assertFalse(Files.exists(directory.resolve(".sle")))
    }

    @Test
    fun discoveryIncludesOnlyDirectRegularEditorScripts() {
        val root = Files.createTempDirectory("sle-script-discovery")
        val editor = Files.createDirectory(root.resolve("Editor"))
        Files.writeString(editor.resolve("b.editor.lua"), "return {}")
        Files.writeString(editor.resolve("a.editor.lua"), "return {}")
        Files.writeString(editor.resolve("ordinary.lua"), "return {}")
        Files.writeString(Files.createDirectory(editor.resolve("Nested")).resolve("nested.editor.lua"), "return {}")
        assertEquals(listOf("a.editor.lua", "b.editor.lua"), projectScriptPaths(root).map { it.fileName.toString() })
    }
}
