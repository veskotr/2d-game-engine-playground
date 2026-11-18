package sle.editor.model

import sle.editor.command.CommandContext
import sle.editor.command.EditCommands
import sle.editor.command.menuEntry
import sle.editor.ui.SINGLE_ENTITY_REASON
import sle.editor.ui.TreeClick
import sle.editor.ui.canApplyToPrefab
import sle.editor.ui.treeSelect
import sle.editor.viewport.PreviewCamera
import sle.editor.viewport.Vec2
import sle.editor.viewport.ViewportGizmos

import kotlinx.serialization.json.*
import java.awt.Canvas
import java.awt.event.KeyEvent
import java.awt.event.MouseEvent
import kotlin.test.*

class SceneSelectionTest {
    private val catalog=mapOf(
        "Transform" to ComponentDefinition(Json.parseToJsonElement("""{"position":{"x":0,"y":0},"rotation":0,"scale":{"x":1,"y":1}}""").jsonObject,JsonObject(emptyMap())),
    )
    private fun scene()=SceneWorkspaceState(catalog) { it }
    private fun SceneWorkspaceState.create(name: String,x: Double,asChild: Boolean=false): String {
        if(asChild) addChild(Vec2(x,0.0)) else addEntity(Vec2(x,0.0))
        selected!!.rename(name);return selectedKey!!
    }
    private fun SceneWorkspaceState.worldX(key: String)=ScenePose.from(worldDocument(key)).position.x
    private val canvas=Canvas()
    private val camera=PreviewCamera(Vec2(-10.0,-10.0),Vec2(20.0,20.0),400,400)
    private fun ViewportGizmos.drag(from: Vec2,to: Vec2) {
        val a=camera.screen(from);val b=camera.screen(to)
        press(MouseEvent(canvas,MouseEvent.MOUSE_PRESSED,0,0,a.x.toInt(),a.y.toInt(),1,false,1),camera)
        move(MouseEvent(canvas,MouseEvent.MOUSE_DRAGGED,0,0,b.x.toInt(),b.y.toInt(),1,false,0))
        finish()
    }
    private fun ViewportGizmos.nudge(code: Int)=key(KeyEvent(canvas,KeyEvent.KEY_PRESSED,0,0,code,KeyEvent.CHAR_UNDEFINED))

    @Test fun selectionTogglesKeepsOrderAndMovesTheActiveKey() {
        val selection=SceneSelection()
        selection.select("a");selection.toggle("c");selection.toggle("b")
        assertEquals(listOf("a","c","b"),selection.keys);assertEquals("b",selection.active)
        selection.toggle("b") // removing the active key activates the latest remaining one
        assertEquals(listOf("a","c"),selection.keys);assertEquals("c",selection.active)
        selection.toggle("a");assertEquals(listOf("c"),selection.keys);assertEquals("c",selection.active)
        selection.activate("missing");assertEquals("c",selection.active)
        selection.toggle("c");assertTrue(selection.isEmpty);assertNull(selection.active)
        selection.set(listOf("x","y","x"),"x");assertEquals(listOf("x","y"),selection.keys);assertEquals("x",selection.active)
        selection.select(null);assertTrue(selection.isEmpty)
    }

    @Test fun selectionRangesFollowTheVisibleOrderFromTheAnchor() {
        val order=listOf("a","b","c","d","e")
        val selection=SceneSelection()
        selection.select("b");selection.extendTo("d",order)
        assertEquals(listOf("b","c","d"),selection.keys);assertEquals("d",selection.active)
        selection.extendTo("a",order) // the anchor stays at b
        assertEquals(listOf("a","b"),selection.keys);assertEquals("a",selection.active)
        selection.toggle("e");selection.extendTo("c",order,additive=true) // Ctrl+Shift adds from the new anchor e
        assertEquals(setOf("a","b","c","d","e"),selection.keys.toSet());assertEquals("c",selection.active)
        val fresh=SceneSelection();fresh.extendTo("c",order) // no anchor: selects the key alone
        assertEquals(listOf("c"),fresh.keys)
    }

    @Test fun selectionPrunesAndRestoresOnlyExistingKeys() {
        val selection=SceneSelection()
        selection.set(listOf("a","b","c"),"b")
        selection.prune(setOf("a","c"));assertEquals(listOf("a","c"),selection.keys);assertEquals("c",selection.active)
        selection.restore(HistorySelection(listOf("a","b","c"),"b"),setOf("b","c"))
        assertEquals(listOf("b","c"),selection.keys);assertEquals("b",selection.active)
        selection.restore(HistorySelection(listOf("a","gone"),"gone"),setOf("a"))
        assertEquals(listOf("a"),selection.keys);assertEquals("a",selection.active)
        assertEquals(HistorySelection(listOf("k"),"k"),HistorySelection("k"))
    }

    @Test fun sceneSelectionIsNotAnUndoStepButUndoRestoresTheRecordedSet() {
        val scene=scene();val a=scene.create("A",0.0);val b=scene.create("B",2.0);val c=scene.create("C",4.0)
        val steps=scene.history.undoLabels.size
        scene.select(a);scene.toggleSelection(c);scene.toggleSelection(b);scene.selectAll();scene.select(null)
        assertEquals(steps,scene.history.undoLabels.size) // selection changes alone are never steps
        scene.select(a);scene.toggleSelection(c)
        scene.removeSelected();assertEquals(listOf(b),scene.entities.map { it.document.key });assertTrue(scene.selection.isEmpty)
        scene.select(b);scene.undo()
        assertEquals(listOf(a,c),scene.selection.keys);assertEquals(c,scene.selectedKey)
        scene.redo();assertTrue(scene.selection.isEmpty)
        scene.undo();scene.selectAll() // Ctrl+A keeps the active entity
        assertEquals(listOf(a,b,c),scene.selection.keys);assertEquals(c,scene.selectedKey)
        scene.undo();scene.undo() // Rename C, then Create C: the removed entity leaves the selection
        assertTrue(scene.entities.none { it.document.key==c });assertFalse(c in scene.selection)
    }

    @Test fun hierarchyClicksToggleSelectRangesAndPlainClickReplaces() {
        val scene=scene();val a=scene.create("A",0.0);val b=scene.create("B",1.0);val c=scene.create("C",2.0);val d=scene.create("D",3.0)
        val visible=listOf(a,b,c,d)
        scene.treeSelect(b,TreeClick.Replace,visible);scene.treeSelect(d,TreeClick.Range,visible)
        assertEquals(listOf(b,c,d),scene.selection.keys)
        scene.treeSelect(c,TreeClick.Toggle,visible);assertEquals(listOf(b,d),scene.selection.keys)
        scene.treeSelect(a,TreeClick.AddRange,visible);assertEquals(setOf(a,b,c,d),scene.selection.keys.toSet())
        scene.treeSelect(c,TreeClick.Replace,visible);assertEquals(listOf(c),scene.selection.keys)
        scene.treeSelect(null,TreeClick.Toggle,visible);assertTrue(scene.selection.isEmpty) // the scene row clears
    }

    @Test fun deleteRemovesTheUnionOfNestedSelectedSubtreesAsOneStep() {
        val scene=scene();val keep=scene.create("Keep",0.0)
        val parent=scene.create("Parent",1.0);val child=scene.create("Child",2.0,asChild=true);val grandchild=scene.create("Grandchild",3.0,asChild=true)
        val loose=scene.create("Loose",4.0)
        scene.select(child);scene.toggleSelection(parent);scene.toggleSelection(loose)
        assertEquals(listOf(parent,loose),scene.selectionRoots())
        val before=scene.document();val steps=scene.history.undoLabels.size
        scene.removeSelected()
        assertEquals(listOf(keep),scene.entities.map { it.document.key });assertTrue(scene.parents.isEmpty())
        assertEquals(steps+1,scene.history.undoLabels.size);assertEquals("Delete 4 entities",scene.history.undoLabel)
        scene.undo()
        assertEquals(before,scene.document());assertEquals(listOf(child,parent,loose),scene.selection.keys);assertEquals(loose,scene.selectedKey)
        assertEquals(child,scene.parents[grandchild])
    }

    @Test fun duplicateCopiesOnlyTopMostSelectedRootsAndSelectsTheCopies() {
        val scene=scene();val parent=scene.create("Parent",0.0);val child=scene.create("Child",1.0,asChild=true)
        val other=scene.create("Other",3.0)
        scene.select(parent);scene.toggleSelection(other);scene.toggleSelection(child) // child is active, under a selected parent
        val count=scene.entities.size;val steps=scene.history.undoLabels.size
        scene.duplicateSelected()
        assertEquals(count+3,scene.entities.size) // parent, child and other once each
        assertEquals(steps+1,scene.history.undoLabels.size);assertEquals("Duplicate 3 entities",scene.history.undoLabel)
        val copies=scene.selection.keys
        assertEquals(2,copies.size);assertTrue(copies.none { it in setOf(parent,child,other) })
        assertEquals(listOf("Parent Copy","Other Copy"),copies.map { key -> scene.entity(key)!!.document.name })
        assertEquals(copies.first(),scene.selectedKey) // the copy of the root holding the active child
        val childCopy=scene.parents.entries.single { it.value==copies.first() }.key
        assertEquals("Child Copy",scene.entity(childCopy)!!.document.name)
        assertNull(scene.parents[copies.last()])
        scene.undo();assertEquals(count,scene.entities.size);assertEquals(listOf(parent,other,child),scene.selection.keys);assertEquals(child,scene.selectedKey)
    }

    @Test fun multiMoveDragIsOneUndoStepAndRestoresTheSelection() {
        val scene=scene();val a=scene.create("A",0.0);val b=scene.create("B",4.0)
        scene.select(a);scene.toggleSelection(b)
        val steps=scene.history.undoLabels.size
        ViewportGizmos(scene.selected!!,true,scene).drag(Vec2(4.0,0.0),Vec2(7.0,0.0))
        assertEquals(3.0,scene.worldX(a),0.2);assertEquals(7.0,scene.worldX(b),0.2)
        assertEquals(steps+1,scene.history.undoLabels.size);assertEquals("Move 2 entities",scene.history.undoLabel)
        scene.select(null)
        scene.undo()
        assertEquals(0.0,scene.worldX(a),1e-9);assertEquals(4.0,scene.worldX(b),1e-9)
        assertEquals(listOf(a,b),scene.selection.keys);assertEquals(b,scene.selectedKey)
        scene.redo();assertEquals(3.0,scene.worldX(a),0.2);assertEquals(7.0,scene.worldX(b),0.2)
    }

    @Test fun movingAChildWhoseParentIsSelectedMovesTheParentOnce() {
        val scene=scene();val parent=scene.create("Parent",0.0);val child=scene.create("Child",2.0,asChild=true)
        scene.select(parent);scene.toggleSelection(child) // the child is active
        val local=scene.entity(child)!!.document
        ViewportGizmos(scene.selected!!,true,scene).drag(Vec2(2.0,0.0),Vec2(5.0,0.0))
        assertEquals(3.0,scene.worldX(parent),0.2);assertEquals(5.0,scene.worldX(child),0.2)
        assertEquals(local,scene.entity(child)!!.document) // the child follows; its local pose is untouched
        assertEquals("Move Parent",scene.history.undoLabel)
        scene.undo();assertEquals(0.0,scene.worldX(parent),1e-9)
    }

    @Test fun nudgingASelectionMergesTheBurstIntoOneStep() {
        val scene=scene();val a=scene.create("A",0.0);val b=scene.create("B",4.0)
        var now=10_000L;scene.history.clock={ now }
        scene.select(a);scene.toggleSelection(b)
        val gizmos=ViewportGizmos(scene.selected!!,true,scene)
        val steps=scene.history.undoLabels.size
        repeat(3) { gizmos.nudge(KeyEvent.VK_RIGHT);now+=300 }
        assertEquals(0.3,scene.worldX(a),1e-9);assertEquals(4.3,scene.worldX(b),1e-9)
        assertEquals(steps+1,scene.history.undoLabels.size);assertEquals("Move 2 entities",scene.history.undoLabel)
        now+=History.MERGE_WINDOW_MILLIS;gizmos.nudge(KeyEvent.VK_RIGHT)
        assertEquals(steps+2,scene.history.undoLabels.size)
        scene.undo();scene.undo()
        assertEquals(0.0,scene.worldX(a),1e-9);assertEquals(4.0,scene.worldX(b),1e-9);assertEquals(listOf(a,b),scene.selection.keys)
        scene.select(b);gizmos.nudge(KeyEvent.VK_RIGHT) // one selected entity keeps the single-entity label
        assertEquals("Move B",scene.history.undoLabel);assertEquals(0.0,scene.worldX(a),1e-9)
    }

    @Test fun linkedPrefabInstancesInAMultiSelection() {
        val prefab=PrefabDocument("root",listOf(EntityDocument(key="root",name="Actor"),EntityDocument(key="child",name="Hand")),mapOf("child" to "root"))
        val scene=SceneWorkspaceState(emptyMap()) { it }
        scene.insertLinkedPrefab(prefab,"asset-id",emptyMap());val first=scene.selectedKey!!
        scene.insertLinkedPrefab(prefab,"asset-id",emptyMap());val second=scene.selectedKey!!
        scene.addEntity();val loose=scene.selectedKey!!
        scene.projectApplyPrefab={}
        // A linked descendant cannot be deleted or duplicated on its own, even inside a set.
        scene.select(loose);scene.toggleSelection("$first/child")
        val before=scene.document()
        assertFails { scene.removeSelected() };assertFails { scene.duplicateSelected() }
        assertEquals(before,scene.document())
        // Whole instances can: one step each, with their links.
        scene.select(first);scene.toggleSelection("$first/child");scene.toggleSelection(second)
        assertEquals(listOf(first,second),scene.selectionRoots())
        assertFalse(canApplyToPrefab(scene)) // Apply works on one instance
        scene.duplicateSelected()
        assertEquals(4,scene.document().prefabInstances.size);assertEquals(8+1,scene.entities.size)
        val copies=scene.selection.keys
        copies.forEach { copy -> assertEquals(copy,scene.parents["$copy/child"]) }
        scene.select(first);assertTrue(canApplyToPrefab(scene))
        scene.select(first);scene.toggleSelection(second);scene.toggleSelection(loose)
        scene.removeSelected()
        assertEquals(2,scene.document().prefabInstances.size);assertEquals(copies.toSet(),scene.document().prefabInstances.map { it.key }.toSet())
        scene.undo();assertEquals(4,scene.document().prefabInstances.size);assertEquals(listOf(first,second,loose),scene.selection.keys)
    }

    @Test fun singleEntityCommandsAreDisabledWithAReasonForSeveralEntities() {
        val scene=scene();val a=scene.create("A",0.0);scene.create("B",1.0)
        fun ctx()=CommandContext(scene=scene)
        scene.select(a)
        assertTrue(EditCommands.editEntity.enabled(ctx()));assertNull(EditCommands.editEntity.menuEntry(ctx()).help)
        EditCommands.selectAll.run(ctx());assertEquals(2,scene.selection.size);assertEquals(a,scene.selectedKey)
        assertFalse(EditCommands.editEntity.enabled(ctx()))
        assertEquals(SINGLE_ENTITY_REASON,EditCommands.editEntity.menuEntry(ctx()).help)
        assertEquals(SINGLE_ENTITY_REASON,EditCommands.applyToPrefab.menuEntry(ctx()).help)
        assertTrue(EditCommands.delete.enabled(ctx()));assertTrue(EditCommands.duplicate.enabled(ctx()))
        EditCommands.delete.run(ctx());assertTrue(scene.entities.isEmpty())
        assertFalse(EditCommands.selectAll.enabled(ctx()))
    }
}
