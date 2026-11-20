package sle.editor.scripts

import kotlinx.serialization.json.*
import sle.editor.model.*
import sle.editor.project.*
import java.nio.file.Files
import kotlin.test.*
import org.junit.Rule
import org.junit.rules.TemporaryFolder

class ScriptCreationTest {
    @get:Rule val temp = TemporaryFolder()
    private fun workspace() = ProjectWorkspaceState().apply {
        activate(ProjectFiles.create(temp.root.toPath().resolve("Game"), "Game"))
    }

    @Test fun projectTemplatesReplaceBuiltInsAndCopyRawBytes() {
        val workspace = workspace()
        val directory = Files.createDirectory(workspace.opened!!.root.resolve("ScriptTemplates"))
        val raw = byteArrayOf(0xff.toByte(), 13, 10, 0)
        Files.write(directory.resolve("Basic.lua"), raw)
        Files.writeString(directory.resolve("Camera Rig.lua"), "this is deliberately invalid Lua")
        Files.writeString(directory.resolve("note.txt"), "")
        Files.createDirectory(directory.resolve("folder.lua"))
        Files.writeString(Files.createDirectory(directory.resolve("nested")).resolve("ignored.lua"), "")
        Files.newByteChannel(directory.resolve("oversize.lua"), java.nio.file.StandardOpenOption.CREATE_NEW,
            java.nio.file.StandardOpenOption.WRITE).use { channel ->
            channel.position(1024L * 1024)
            channel.write(java.nio.ByteBuffer.wrap(byteArrayOf(0)))
        }
        val action = ScriptCreation(workspace, EntityWorkspaceState(emptyMap())) { true }
        val templates = action.templates()
        assertEquals(listOf("Basic.lua", "ExposedProperty.lua", "Camera Rig.lua"), templates.map { it.filename })
        val destination = action.create("Scripts/Raw.lua", templates.first())
        assertContentEquals(raw, Files.readAllBytes(destination))
        val extra = action.create("Scripts/Camera.lua", templates.last())
        assertEquals("this is deliberately invalid Lua", Files.readString(extra))
        Files.delete(directory.resolve("Basic.lua"))
        assertFailsWith<java.io.IOException> { action.create("Scripts/Missing.lua", templates.first()) }
        assertFalse(Files.exists(workspace.opened!!.assets.resolve("Scripts/Missing.lua")))
        assertEquals(BuiltInScriptTemplate.Basic, action.templates().first())
    }

    @Test fun templateDiscoverySkipsEscapingLinksAndFallsBackWhenDirectoryIsUnavailable() {
        val workspace = workspace()
        val folder = workspace.opened!!.root.resolve("ScriptTemplates")
        val action = ScriptCreation(workspace, EntityWorkspaceState(emptyMap())) { true }
        assertEquals(BuiltInScriptTemplate.entries, action.templates())
        Files.writeString(folder, "not a directory")
        assertEquals(BuiltInScriptTemplate.entries, action.templates())
        Files.delete(folder)
        val outsideDirectory = Files.createDirectory(temp.root.toPath().resolve("outside"))
        val outside = Files.writeString(outsideDirectory.resolve("Basic.lua"), "return 1")
        try {
            Files.createSymbolicLink(folder, outsideDirectory)
        } catch (failure: java.nio.file.FileSystemException) {
            org.junit.Assume.assumeNoException(failure)
        }
        assertEquals(BuiltInScriptTemplate.entries, action.templates())
        Files.delete(folder)
        Files.createDirectory(folder)
        Files.createSymbolicLink(folder.resolve("Basic.lua"), outside)
        assertEquals(BuiltInScriptTemplate.entries, action.templates())
    }

    @Test fun appendsOneUndoableAttachmentAndPreservesSourceEdits() {
        val workspace = workspace()
        val target = EntityWorkspaceState(emptyMap())
        fun existing(name: String): String {
            val path = Files.writeString(workspace.opened!!.assets.resolve("Scripts/$name.lua"), "return {}")
            return "project-asset://${sle.editor.project.assets.AssetIdentity.ensure(path)}"
        }
        val legacy = existing("legacy")
        val other = existing("other")
        val script = Json.parseToJsonElement("""{"scriptAsset":"$legacy","behaviours":[{"key":"Player","scriptAsset":"$other","parameters":{"speed":7}}]}""").jsonObject
        target.resetTo(target.document.copy(components = JsonObject(mapOf("Script" to script))))
        val action = ScriptCreation(workspace, target) { true }
        val source = action.create("Scripts/Player.lua", BuiltInScriptTemplate.ExposedProperty)
        action.attach(source)
        val entries = target.document.components.getValue("Script").jsonObject.behaviourEntries()
        assertEquals(script.behaviourEntries().single(), entries.first())
        assertEquals("Player_2", entries.last().behaviourText("key"))
        assertEquals(legacy, target.document.components.getValue("Script").jsonObject.behaviourText("scriptAsset"))
        val docs = ProjectDocuments(workspace.opened!!)
        assertEquals(source, java.nio.file.Path.of(docs.resolvePropertyAsset(entries.last().behaviourText("scriptAsset")!!)))
        assertContains(Files.readString(source), "properties")
        val path = workspace.opened!!.assets.resolve("Prefabs/Player.prefab.json")
        docs.save(path, ProjectDocumentKind.Prefab, target.serializePrefab(), emptyMap(), null) { PrefabDocument.parse(it) }
        val reopened = EntityWorkspaceState(emptyMap()).apply { openProject(docs.open(path)) }
        assertEquals("Player_2", reopened.document.components.getValue("Script").jsonObject.behaviourEntries().last().behaviourText("key"))
        Files.writeString(source, "return {} -- IDE edit")
        target.undo()
        assertEquals(script, target.document.components["Script"])
        target.redo()
        assertEquals(entries, target.document.components.getValue("Script").jsonObject.behaviourEntries())
        assertEquals("return {} -- IDE edit", Files.readString(source))
    }

    @Test fun sceneWithoutScriptUsesAResolvedDraftPathAndSavesAPortableReference() {
        val workspace = workspace()
        val project = workspace.opened!!
        val docs = ProjectDocuments(project)
        val scene = SceneWorkspaceState(emptyMap()) { it }
        scene.addEntity()
        val target = scene.selected!!
        val action = ScriptCreation(workspace, target) { scene.selected === target }
        val templates = Files.createDirectory(project.root.resolve("ScriptTemplates"))
        Files.writeString(templates.resolve("Actor.lua"), "return {} -- project template\r\n")
        val selected = action.templates().single { it.label == "Actor.lua" }
        val source = action.create("Scripts/Nested/Scene actor.lua", selected)
        action.attach(source)
        val attachment = target.document.components.getValue("Script").jsonObject.behaviourEntries().single()
        assertEquals(source, java.nio.file.Path.of(attachment.behaviourText("scriptAsset")!!))
        val destination = project.assets.resolve("Scenes/Authored.scene.json")
        docs.save(destination, ProjectDocumentKind.Scene, scene.serialize(), emptyMap(), null) { SceneDocument.parse(it) }
        assertContains(Files.readString(destination), "project-asset://")
        val reopened = SceneWorkspaceState(emptyMap()) { it }
        reopened.openProject(docs.open(destination))
        assertEquals(target.document, reopened.entities.single().document)
        scene.undo()
        assertNull(target.document.components["Script"])
        scene.redo()
        assertEquals(attachment, target.document.components.getValue("Script").jsonObject.behaviourEntries().single())
        assertEquals("return {} -- project template\r\n", Files.readString(source))
    }

    @Test fun rejectsCollisionsAndEscapesAndKeepsFileWhenTargetChanges() {
        val workspace = workspace()
        val target = EntityWorkspaceState(emptyMap())
        var selected = true
        val action = ScriptCreation(workspace, target) { selected }
        assertFails { action.create("../Player.lua", BuiltInScriptTemplate.Basic) }
        val source = action.create("Scripts/Player.lua", BuiltInScriptTemplate.Basic)
        val original = Files.readString(source)
        assertFails { action.create("Scripts/Player.lua", BuiltInScriptTemplate.ExposedProperty) }
        assertEquals(original, Files.readString(source))
        selected = false
        assertFails { action.attach(source) }
        assertNull(target.document.components["Script"])
        assertTrue(Files.exists(source))
        selected = true
        workspace.close()
        assertFails { action.attach(source) }
    }
}
