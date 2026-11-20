package sle.editor.model

import kotlinx.serialization.json.*
import sle.editor.viewport.LiveEntity
import sle.editor.viewport.LiveInspection
import sle.editor.viewport.LiveSelection
import sle.editor.viewport.Vec2
import java.nio.file.Files
import kotlin.test.*

class KeepPlayChangesTest {
    private val scene = SceneWorkspaceState(emptyMap()) { it }.apply { addEntity(Vec2(1.0, 2.0)) }
    private val entity = scene.selected!!
    private val key = entity.document.key
    private val playback = ScenePlaybackState { it }.apply { play(scene); pause() }
    private val initial = ScenePose.from(entity.document)
    private val moved = initial.copy(position = Vec2(8.0, 2.0), rotation = 30.0)

    private fun live(pose: ScenePose = moved) = LiveInspection(
        3, 5, listOf(LiveEntity(7, 0, key, null)), LiveSelection(3, 5, 7),
        buildJsonObject { put("Transform", pose.fields()) }, playback.keepChangesRevision,
    )

    @Test fun keepsChangedAxesInOneHistoryEntryAndSavesThem() {
        entity.setValue("Transform", listOf("position", "y"), JsonPrimitive(9.0))
        val preview = assertNotNull(playback.previewKeepChanges(scene, live()))
        assertEquals(listOf("Position X", "Rotation"), preview.fields.map { it.label })
        playback.keepChanges(scene, live(), preview)
        val expected = moved.copy(position = Vec2(8.0, 9.0))
        assertEquals(expected, ScenePose.from(entity.document))
        scene.undo()
        assertEquals(initial.copy(position = Vec2(1.0, 9.0)), ScenePose.from(entity.document))
        scene.redo()
        assertEquals(expected, ScenePose.from(entity.document))
        val path = Files.createTempDirectory("sle-keep-changes").resolve("kept.slescene")
        scene.save(path)
        val reopened = SceneWorkspaceState(emptyMap()) { it }.apply { open(path) }
        assertEquals(expected, ScenePose.from(reopened.selected!!.document))
    }

    @Test fun rejectsConflictsAndInvalidValuesBeforeWritingAnyFields() {
        val preview = assertNotNull(playback.previewKeepChanges(scene, live()))
        entity.setValue("Transform", listOf("rotation"), JsonPrimitive(90.0))
        val edited = entity.document
        assertFails { playback.keepChanges(scene, live(), preview) }
        assertEquals(edited, entity.document)
        scene.undo()
        assertEquals(initial, ScenePose.from(entity.document))

        val fields = moved.fields() + ("rotation" to JsonPrimitive(1.0e100))
        val invalid = live().copy(details = buildJsonObject { put("Transform", JsonObject(fields)) })
        assertNull(playback.previewKeepChanges(scene, invalid))
        assertFails { playback.keepChanges(scene, invalid, preview) }
        assertEquals(initial, ScenePose.from(entity.document))
    }

    @Test fun rejectsStalePreviewsAndUnrelatedEntities() {
        val snapshot = live()
        val preview = assertNotNull(playback.previewKeepChanges(scene, snapshot))
        val cases = listOf(
            snapshot.copy(entities = emptyList()),
            snapshot.copy(entities = listOf(snapshot.entities.single().copy(authoredKey = null))),
            snapshot.copy(scene = 6, selection = LiveSelection(3, 6, 7), playRevision = null),
            live(moved.copy(rotation = 45.0)),
        )
        cases.forEach { assertFails { playback.keepChanges(scene, it, preview) } }

        val path = Files.createTempDirectory("sle-keep-other").resolve("other.slescene")
        scene.save(path)
        val other = SceneWorkspaceState(emptyMap()) { it }.apply { open(path) }
        assertEquals(key, other.selectedKey)
        assertFails { playback.keepChanges(other, snapshot, preview) }
        scene.open(path)
        assertFails { playback.keepChanges(scene, snapshot, preview) }
        assertEquals(initial, ScenePose.from(scene.selected!!.document))
        playback.play(scene)
        assertNull(playback.previewKeepChanges(scene, snapshot))
        playback.stop()
        assertNull(playback.previewKeepChanges(scene, snapshot))
    }

    @Test fun unchangedFloatValuesDoNotProduceAChange() {
        playback.stop()
        entity.setValue("Transform", listOf("rotation"), JsonPrimitive(0.1))
        playback.play(scene)
        playback.pause()
        assertNull(playback.previewKeepChanges(scene, live(initial.copy(rotation = 0.1f.toDouble()))))
    }
}
