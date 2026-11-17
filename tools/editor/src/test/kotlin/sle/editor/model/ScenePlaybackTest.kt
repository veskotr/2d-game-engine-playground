package sle.editor.model

import sle.editor.viewport.Playback

import kotlinx.serialization.json.*
import kotlin.test.*

class ScenePlaybackTest {
    @Test fun playSnapshotsOncePauseResumesAndStopDoesNotApplyRuntimeData() {
        val scene=SceneWorkspaceState(emptyMap()) { it }
        scene.addEntity()
        scene.selected!!.rename("Authored")
        val source=scene.serialize()
        val playback=ScenePlaybackState { it }
        playback.play(scene)
        assertEquals(Playback.Running,playback.playback)
        assertTrue(playback.gameView)
        assertEquals(source,playback.source)
        playback.pause()
        assertEquals(Playback.Paused,playback.playback)
        scene.selected!!.rename("Independent authored change")
        playback.play(scene)
        assertEquals(source,playback.source)
        playback.stop()
        assertEquals(Playback.Stopped,playback.playback)
        assertNull(playback.source)
        assertEquals("Independent authored change",scene.selected!!.document.name)
    }
    @Test fun invalidDraftAnywhereOrValidationFailureCannotStartPlay() {
        val scene=SceneWorkspaceState(mapOf("Transform" to ComponentDefinition(buildJsonObject { put("rotation",0.0) },JsonObject(emptyMap())))) { it }
        scene.addEntity();val first=scene.selected!!
        scene.addEntity()
        first.editField("Transform",listOf("rotation"),JsonPrimitive(0.0),"-")
        val playback=ScenePlaybackState { it }
        assertFails { playback.play(scene) }
        assertEquals(Playback.Stopped,playback.playback)
        assertNull(playback.source)
        first.undo()
        val failing=ScenePlaybackState { error("Invalid scene") }
        assertFails { failing.play(scene) }
        assertEquals(Playback.Stopped,failing.playback)
    }
}
