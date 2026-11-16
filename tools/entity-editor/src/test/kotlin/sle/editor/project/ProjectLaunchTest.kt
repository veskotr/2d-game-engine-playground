package sle.editor.project

import kotlin.test.*
import org.junit.Test
import sle.editor.model.ScenePlaybackState
import sle.editor.model.SceneWorkspaceState
import sle.editor.viewport.Playback

class ProjectLaunchTest {
    @Test fun projectPlayKeepsTheOpenDraftAndStopDropsItsLaunchContext() {
        val scene=SceneWorkspaceState(emptyMap(),canonicalize={it})
        val before=scene.serialize()
        val playback=ScenePlaybackState { it }
        val project=PreparedProject(7,"{\"key\":\"startup\",\"entities\":[]}",ProjectGameSettings(width=640,height=360))
        playback.playProject(project)
        assertEquals(before,scene.serialize())
        assertEquals(project.source,playback.source)
        assertEquals(7L,playback.project?.handle)
        assertFalse(playback.editing)
        playback.pause();playback.play(scene)
        assertEquals(7L,playback.project?.handle)
        playback.stop()
        assertEquals(Playback.Stopped,playback.playback)
        assertNull(playback.project)
        assertNull(playback.source)
        assertEquals(before,scene.serialize())
    }
}
