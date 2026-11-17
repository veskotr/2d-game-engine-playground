package sle.editor.project

import java.nio.file.Files
import kotlin.test.*

class RecentProjectsTest {
    @Test fun persistsNormalizedDeduplicatedBoundedProjects() {
        val directory=Files.createTempDirectory("recent-projects")
        val store=RecentProjectsStore(directory)
        for(i in 0..11) store.record(directory.resolve("Game$i.sleproject"))
        store.record(directory.resolve("folder/../Game5.sleproject"))
        val paths=RecentProjectsStore(directory).load().paths
        assertEquals(10,paths.size);assertEquals(directory.resolve("Game5.sleproject"),paths.first())
        assertEquals(1,paths.count { it.fileName.toString()=="Game5.sleproject" })
    }
    @Test fun malformedStateRecoversAndFailedReplacePreservesPreviousHistory() {
        val directory=Files.createTempDirectory("recent-projects")
        Files.writeString(directory.resolve("recent-projects.json"),"broken")
        val store=RecentProjectsStore(directory)
        assertTrue(store.load().paths.isEmpty());assertNotNull(store.load().notice)
        val missing=directory.resolve("Missing.sleproject");store.record(missing)
        val failing=RecentProjectsStore(directory) { error("Disk unavailable") }
        assertFails { failing.record(directory.resolve("Other.sleproject")) }
        assertEquals(listOf(missing),store.load().paths)
        val project=ProjectFiles.create(directory.resolve("Game"),"Game")
        val state=ProjectWorkspaceState();state.activate(project)
        assertFails { state.open(missing) };assertEquals(project,state.opened)
    }
}
