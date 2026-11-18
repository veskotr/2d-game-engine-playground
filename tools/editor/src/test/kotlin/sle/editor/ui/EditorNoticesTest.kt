package sle.editor.ui

import sle.editor.command.CommandContext
import sle.editor.model.ComponentDefinition
import sle.editor.model.EntityWorkspaceState
import sle.editor.model.HISTORY_CLEARED_NOTICE
import sle.editor.model.SceneWorkspaceState
import sle.editor.ui.theme.EditorDensity
import sle.editor.ui.theme.editorMetrics
import sle.editor.viewport.ViewportMode
import androidx.compose.ui.unit.dp
import kotlinx.serialization.json.*
import kotlin.test.*

class EditorNoticesTest {
    private var now=1_000L
    private fun notices()=EditorNotices(capacity=3).also { it.clock={ now } }

    @Test fun infoAndSuccessExpireAfterTheirTimeWhileWarningsAndErrorsStay() {
        val notices=notices()
        val info=notices.info("Saved level.scene.json")
        assertEquals(EditorNotices.DEFAULT_TTL_MILLIS,notices.remainingMillis())
        now+=EditorNotices.DEFAULT_TTL_MILLIS-1;notices.expire()
        assertSame(info,notices.current)
        now+=1;notices.expire()
        assertNull(notices.current)
        assertEquals(listOf(info),notices.recent,"an expired notice stays in the recent list")

        notices.success("Exported",).let { assertEquals(now+EditorNotices.DEFAULT_TTL_MILLIS,it.expiresAt) }
        val error=notices.error("Could not save")
        now+=60_000;notices.expire()
        assertSame(error,notices.current,"errors are sticky")
        assertNull(notices.remainingMillis())
        notices.dismiss()
        assertNull(notices.current)
        val warning=notices.warning("Layout recovered")
        now+=60_000;notices.expire()
        assertSame(warning,notices.current,"warnings are sticky")
        // An explicit time overrides the level's default, also for sticky levels.
        val timed=notices.post(NoticeLevel.Error,"Timed error",ttlMillis=500)
        now+=500;notices.expire()
        assertNull(notices.current);assertSame(timed,notices.recent.first())
    }

    @Test fun newerNoticesReplaceOlderOnesAndTheRecentListIsShortAndNewestFirst() {
        val notices=notices()
        val error=notices.error("Sticky error")
        val info=notices.info("Newer info")
        assertSame(info,notices.current,"a newer notice replaces even a sticky one")
        notices.dismiss(error)
        assertSame(info,notices.current,"dismissing a replaced notice leaves the current one")
        val third=notices.warning("Third");val fourth=notices.info("Fourth")
        assertEquals(listOf(fourth,third,info),notices.recent)
        assertEquals(listOf(fourth,third),notices.postedAfter(info))
        notices.clearRecent()
        assertEquals(listOf(fourth),notices.recent,"clearing keeps the notice on show")
        notices.dismiss();notices.clearRecent()
        assertTrue(notices.recent.isEmpty())
    }

    @Test fun actionsRunOnceWhileAvailableAndTheirOwnResultStays() {
        val notices=notices()
        var available=true
        var runs=0
        val undo=notices.info("Deleted Player",NoticeAction("Undo",available={ available }) { runs++;notices.error("Undo failed") })
        assertTrue(notices.invoke(undo))
        assertEquals(1,runs)
        assertEquals("Undo failed",notices.current?.text,"the notice is dismissed before its action posts a result")
        val stale=notices.info("Deleted Crate",NoticeAction("Undo",available={ available }) { runs++ })
        available=false
        assertFalse(notices.invoke(stale))
        assertEquals(1,runs);assertSame(stale,notices.current)
        assertFalse(notices.invoke(notices.info("No action")))
    }

    @Test fun bridgeConsumesDocumentFieldsAndWatchesOwnedNotices() {
        val notices=notices()
        val bridge=NoticeBridge(notices)
        var error: String?="Stop the game before adding to the scene"
        var layout: String?=null
        fun sources()=listOf(
            NoticeSource("scene/error",{ NoticeLevel.Error },{ error },{ error=null }),
            NoticeSource("layout",{ NoticeLevel.Warning },{ layout },null))
        bridge.deliver(sources())
        assertNull(error,"a document field is cleared once posted")
        assertEquals(NoticeLevel.Error,notices.current?.level)
        error="Stop the game before adding to the scene";bridge.deliver(sources())
        assertEquals(2,notices.recent.size,"the same text set again is reported again")
        layout="Workspace layout recovered to defaults";bridge.deliver(sources());bridge.deliver(sources())
        assertEquals(3,notices.recent.size,"a watched notice posts once per change")
        assertEquals("Workspace layout recovered to defaults",layout,"a watched field keeps its value")
        assertEquals(NoticeLevel.Warning,notices.current?.level)
        layout=null;bridge.deliver(sources());layout="Workspace layout recovered to defaults";bridge.deliver(sources())
        assertEquals(3,notices.recent.size,"capacity 3")
        assertEquals("Workspace layout recovered to defaults",notices.recent.first().text)
    }

    private val catalog=mapOf(
        "Transform" to ComponentDefinition(Json.parseToJsonElement("""{"position":{"x":0,"y":0},"rotation":0,"scale":{"x":1,"y":1}}""").jsonObject,JsonObject(emptyMap())),
    )

    @Test fun deletionPostsAnUndoThatRunsTheRegisteredUndoOnlyWhileItIsTheLatestStep() {
        val notices=notices()
        val scene=SceneWorkspaceState(catalog) { it }
        val prefab=EntityWorkspaceState(catalog)
        var mode=ViewportMode.Scene
        val context={ CommandContext(mode=mode,scene=scene,prefab=prefab) }
        scene.onDeleted={ subject -> reportDeletion(notices,scene,subject,context) }
        scene.addEntity();scene.selected!!.rename("Player")
        scene.addEntity();scene.selected!!.rename("Crate")
        scene.selectAll()
        assertTrue(deleteSceneSelection(scene,modal=false))
        val deleted=notices.current!!
        assertEquals("Deleted 2 entities",deleted.text)
        assertEquals(NoticeLevel.Info,deleted.level)
        assertEquals("Undo",deleted.action?.label)
        // Another active document hides the button: Undo would act on the prefab instead.
        mode=ViewportMode.Entity
        assertFalse(deleted.action!!.available())
        mode=ViewportMode.Scene
        assertTrue(notices.invoke(deleted))
        assertEquals(listOf("Player","Crate"),scene.entities.map { it.document.name })
        assertEquals(listOf("Player","Crate"),scene.selectedEntities.map { it.document.name },"undo restores the selection")
        assertFalse(deleted.action!!.available(),"once undone, the button no longer applies")

        // A later step makes an older deletion notice stale.
        scene.select(scene.entities.first().document.key)
        assertTrue(deleteSceneSelection(scene,modal=false))
        val single=notices.current!!
        assertEquals("Deleted Player",single.text)
        scene.addEntity()
        assertFalse(single.action!!.available())
        assertFalse(notices.invoke(single))
        assertEquals(2,scene.entities.size,"a stale Undo does not revert the later step")
    }

    @Test fun documentMessagesAreSuccessesUnlessUndoHistoryWasLost() {
        assertEquals(NoticeLevel.Success,documentMessageLevel("Saved level.scene.json"))
        assertEquals(NoticeLevel.Warning,documentMessageLevel(HISTORY_CLEARED_NOTICE))
        assertEquals(NoticeLevel.Warning,documentMessageLevel("Applied instance changes to box.prefab.json. $HISTORY_CLEARED_NOTICE"))
    }

    @Test fun statusBarMetricFollowsDensity() {
        assertEquals(24.dp,editorMetrics(EditorDensity.Compact).statusBar)
        assertEquals(28.dp,editorMetrics(EditorDensity.Comfortable).statusBar)
    }
}
