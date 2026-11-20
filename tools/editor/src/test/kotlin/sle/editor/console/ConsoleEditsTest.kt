package sle.editor.console

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import sle.editor.model.ScenePose
import sle.editor.model.SceneWorkspaceState
import sle.editor.viewport.Vec2
import sle.editor.project.ProjectFiles
import sle.editor.project.assets.AssetIdentity
import java.nio.file.Files
import kotlin.test.*

class ConsoleEditsTest {
    private val scene = SceneWorkspaceState(emptyMap()) { it }.apply { addEntity(Vec2(1.0, 2.0)) }
    private val entity = scene.selected!!
    private val before = scene.document()

    @Test fun appliesFieldAndStructureChangesInOneUndoStep() {
        scene.applyLuaConsoleEdits(before, Json.parseToJsonElement("""
            [
                {"op":"setField","entity":"${entity.document.key}","component":"Transform","path":["position","x"],"value":8},
                {"op":"createEntity","entity":"generated","name":"Generated"},
                {"op":"reparentEntity","entity":"generated","parent":"${entity.document.key}"}
            ]
        """.trimIndent()).jsonArray)
        val after = scene.document()
        assertEquals(Vec2(8.0, 2.0), ScenePose.from(entity.document).position)
        assertEquals(entity.document.key, after.parents["generated"])
        assertEquals("Run Lua", scene.history.undoLabel)
        scene.undo()
        assertEquals(before, scene.document())
        scene.redo()
        assertEquals(after, scene.document())
    }

    @Test fun invalidLaterEditLeavesSceneAndUndoHeadUntouched() {
        val undoHead = scene.history.latestStep
        assertFails {
            scene.applyLuaConsoleEdits(before, Json.parseToJsonElement("""
                [
                    {"op":"createEntity","entity":"generated","name":"Generated"},
                    {"op":"reparentEntity","entity":"generated","parent":"generated"}
                ]
            """.trimIndent()).jsonArray)
        }
        assertEquals(before, scene.document())
        assertSame(undoHead, scene.history.latestStep)
    }

    @Test fun changedSceneRejectsAnOldSnapshot() {
        scene.addEntity()
        val current = scene.document()
        assertFails {
            scene.applyLuaConsoleEdits(before, Json.parseToJsonElement("[]").jsonArray)
        }
        assertEquals(current, scene.document())
    }
    @Test fun assetCreationSharesSceneUndoAndKeepsItsIdentityOnRedo() {
        val root = Files.createTempDirectory("sle-console-assets")
        try {
            val project = ProjectFiles.create(root.resolve("Project"), "Project")
            val edits = Json.parseToJsonElement("""
                [
                    {"op":"createAsset","path":"generated.lua","contents":"return true"},
                    {"op":"createEntity","entity":"generated","name":"Generated"}
                ]
            """.trimIndent()).jsonArray
            val asset = project.assets.resolve("generated.lua")
            val sidecar = AssetIdentity.sidecar(asset)
            scene.applyLuaConsoleEdits(before, edits, project)
            assertEquals("return true", Files.readString(asset))
            val id = AssetIdentity.read(sidecar)
            scene.undo()
            assertEquals(before, scene.document())
            assertFalse(Files.exists(asset))
            assertFalse(Files.exists(sidecar))
            scene.redo()
            assertNotNull(scene.entity("generated"))
            assertEquals(id, AssetIdentity.read(sidecar))

            Files.writeString(asset, "edited externally")
            assertFails { scene.undo() }
            assertNotNull(scene.entity("generated"))
            assertEquals("edited externally", Files.readString(asset))
        } finally { root.toFile().deleteRecursively() }
    }

    @Test fun badAssetDestinationRejectsWholeBatch() {
        val root = Files.createTempDirectory("sle-console-assets")
        try {
            val project = ProjectFiles.create(root.resolve("Project"), "Project")
            val edits = Json.parseToJsonElement("""
                [
                    {"op":"createAsset","path":"valid.lua","contents":"return true"},
                    {"op":"createAsset","path":"../outside.lua","contents":"return false"},
                    {"op":"createEntity","entity":"generated","name":"Generated"}
                ]
            """.trimIndent()).jsonArray
            val head = scene.history.latestStep
            assertFails { scene.applyLuaConsoleEdits(before, edits, project) }
            assertEquals(before, scene.document())
            assertSame(head, scene.history.latestStep)
            assertFalse(Files.exists(project.assets.resolve("valid.lua")))
            assertFalse(Files.exists(project.root.resolve("outside.lua")))
        } finally { root.toFile().deleteRecursively() }
    }
}
