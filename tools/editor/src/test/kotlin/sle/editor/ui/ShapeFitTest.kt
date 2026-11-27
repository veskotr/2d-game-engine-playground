package sle.editor.ui

import kotlinx.serialization.json.*
import sle.editor.model.*
import kotlin.test.*

class ShapeFitTest {
    private fun obj(text: String) = Json.parseToJsonElement(text).jsonObject
    private val box = obj("""{"size":{"x":7,"y":9},"offset":{"x":3,"y":4}}""")
    private val circle = obj("""{"radius":7,"offset":{"x":3,"y":4}}""")
    private val catalog = mapOf("BoxCollider" to ComponentDefinition(box, obj("{}")),
        "BoxZone" to ComponentDefinition(box, obj("{}")),
        "CircleCollider" to ComponentDefinition(circle, obj("{}")),
        "CircleZone" to ComponentDefinition(circle, obj("{}")))
    private fun state(scale: String? = "32,32", size: String = "1,1", offset: String = "0,0", type: String = "BoxCollider"): EntityWorkspaceState {
        fun pair(values: String) = values.split(',').let { "{\"x\":${it[0]},\"y\":${it[1]}}" }
        val components = mutableMapOf<String, JsonElement>(
            "SpriteRenderer" to obj("""{"size":${pair(size)},"offset":${pair(offset)}}"""),
            type to if(type.startsWith("Circle")) circle else box)
        if(scale != null) components["Transform"] = obj("""{"scale":${pair(scale)}}""")
        return EntityWorkspaceState(catalog).also { it.resetTo(EntityDocument(components=JsonObject(components))) }
    }
    private fun value(state: EntityWorkspaceState, type: String, vararg path: String): Double {
        var result: JsonElement = state.document.components.getValue(type)
        path.forEach { result = result.jsonObject.getValue(it) }
        return result.jsonPrimitive.double
    }
    @Test fun ownScaleIsBakedIntoExplicitBoxDimensions() {
        val state = state()
        assertTrue(fitShapeToSprite(state, "BoxCollider"))
        assertEquals(32.0, value(state, "BoxCollider", "size", "x"))
        assertEquals(32.0, value(state, "BoxCollider", "size", "y"))
    }
    @Test fun mirroredNonuniformScaleKeepsSignedOffsetsForAllShapes() {
        for(type in catalog.keys) {
            val state = state("-3,4", "2,5", "2,-3", type)
            assertTrue(fitShapeToSprite(state, type))
            if(type.startsWith("Circle")) assertEquals(10.0, value(state,type,"radius")) else {
                assertEquals(6.0,value(state,type,"size","x"))
                assertEquals(20.0,value(state,type,"size","y"))
            }
            assertEquals(-6.0,value(state,type,"offset","x"))
            assertEquals(-12.0,value(state,type,"offset","y"))
        }
    }
    @Test fun absentTransformAndIdentityScaleKeepOriginalUnits() {
        for(scale in listOf(null,"1,1")) {
            val state=state(scale,"2,5","2,-3")
            assertTrue(fitShapeToSprite(state,"BoxCollider"))
            assertEquals(2.0,value(state,"BoxCollider","size","x"))
            assertEquals(5.0,value(state,"BoxCollider","size","y"))
            assertEquals(-3.0,value(state,"BoxCollider","offset","y"))
        }
    }
    @Test fun invalidGeometryLeavesEntireShapeAndHistoryUntouched() {
        for((scale,size,offset) in listOf(Triple("0,1","2,3","0,0"),Triple("1,1","0,3","0,0"),
            Triple("1e308,1","2,3","0,0"),Triple("1e38,1","20,3","0,0"),Triple("1,1","2,3","\"NaN\",0"))) {
            val state=state(scale,size,offset)
            val before=state.document
            assertFalse(fitShapeToSprite(state,"BoxCollider"))
            assertEquals(before,state.document)
            assertFalse(state.canUndo)
            assertFalse(state.error.isNullOrBlank())
        }
    }
    @Test fun missingSpriteShapeOrUnsupportedTypeDoesNothing() {
        val state=state()
        val before=state.document
        assertFalse(fitShapeToSprite(state,"SpriteRenderer"))
        assertFalse(fitShapeToSprite(state,"CircleCollider"))
        assertEquals(before,state.document)
        state.resetTo(before.removeComponent("SpriteRenderer"))
        assertFalse(fitShapeToSprite(state,"BoxCollider"))
        assertFalse(state.canUndo)
    }
    @Test fun fitIsOneUndoStepIncludingOffset() {
        val state=state("-3,4","2,5","2,-3")
        val before=state.document
        assertTrue(fitShapeToSprite(state,"BoxCollider"))
        val after=state.document
        state.undo()
        assertEquals(before,state.document)
        assertFalse(state.canUndo)
        state.redo()
        assertEquals(after,state.document)
    }
    @Test fun differentlyScaledParentsDoNotChangeFittedLocalValues() {
        val child=state("-3,4","2,5","2,-3").document
        for(parentScale in listOf("{\"x\":2,\"y\":7}","{\"x\":-9,\"y\":0.5}")) {
            val parent=EntityDocument(key="parent",components=obj("""{"Transform":{"scale":$parentScale}}"""))
            val scene=SceneWorkspaceState(catalog) { it }
            scene.openResolved(SceneDocument(entities=listOf(parent,child),parents=mapOf(child.key to parent.key)).serialize(), emptyMap(), java.nio.file.Path.of("test.slescene"))
            val selected=scene.entity(child.key)!!
            assertTrue(fitShapeToSprite(selected,"BoxCollider"))
            assertEquals(6.0,value(selected,"BoxCollider","size","x"))
            assertEquals(20.0,value(selected,"BoxCollider","size","y"))
            assertEquals(-6.0,value(selected,"BoxCollider","offset","x"))
        }
    }
    private fun addBox(state: EntityWorkspaceState) = addComponentEntries(state).flatMap { it.children }.single { it.label=="BoxCollider" }
    @Test fun autoFitAndComponentAdditionShareOneUndoStep() {
        val state=state(offset="1,2")
        state.resetTo(state.document.removeComponent("BoxCollider"))
        val before=state.document
        addBox(state).action()
        assertEquals(32.0,value(state,"BoxCollider","size","x"))
        val after=state.document
        state.undo()
        assertEquals(before,state.document)
        assertFalse(state.canUndo)
        state.redo()
        assertEquals(after,state.document)
    }
    @Test fun delayedAddCannotEditReplacementDocumentOrExpiredContext() {
        val state=state()
        state.resetTo(state.document.removeComponent("BoxCollider"))
        val entry=addBox(state)
        state.endGesture() // Selection and playback boundaries invalidate delayed UI actions.
        val before=state.document
        entry.action()
        assertEquals(before,state.document)
        assertFalse(state.canUndo)
        val replacementEntry=addBox(state)
        state.newDocument()
        val replacement=state.document
        replacementEntry.action()
        assertEquals(replacement,state.document)
    }
    @Test fun liveReadOnlyGuardBlocksFitAndDelayedAddition() {
        val state=state()
        val before=state.document
        assertFalse(fitShapeToSprite(state,"BoxCollider") { false })
        assertEquals(before,state.document)
        state.resetTo(before.removeComponent("BoxCollider"))
        var editable=true
        val entry=addComponentEntries(state) { editable }.flatMap { it.children }.single { it.label=="BoxCollider" }
        editable=false
        entry.action()
        assertFalse("BoxCollider" in state.document.components)
        assertFalse(state.canUndo)
    }
    @Test fun actualPlaybackInvalidatesDelayedShapeAddition() {
        val child=state().document.removeComponent("BoxCollider")
        val scene=SceneWorkspaceState(catalog) { it }
        scene.openResolved(SceneDocument(entities=listOf(child)).serialize(),emptyMap(),java.nio.file.Path.of("test.slescene"))
        val entity=scene.entity(child.key)!!
        val entry=addBox(entity)
        val playback=ScenePlaybackState { it }
        playback.play(scene)
        entry.action()
        assertFalse("BoxCollider" in entity.document.components)
        assertFalse(entity.canUndo)
        playback.stop()
        entry.action()
        assertFalse("BoxCollider" in entity.document.components)
    }
    @Test fun invalidAutoFitPreservesComponentAbsenceAndReportsNotice() {
        val state=state("0,1")
        state.resetTo(state.document.removeComponent("BoxCollider"))
        val before=state.document
        addBox(state).action()
        assertEquals(before,state.document)
        assertFalse(state.canUndo)
        assertFalse(state.error.isNullOrBlank())
    }
    @Test fun noSpriteComponentAdditionRetainsAuthoringDefaults() {
        val state=state()
        state.resetTo(state.document.removeComponent("BoxCollider").removeComponent("SpriteRenderer"))
        addBox(state).action()
        assertEquals(64.0,value(state,"BoxCollider","size","x"))
        state.undo()
        assertFalse("BoxCollider" in state.document.components)
        assertFalse(state.canUndo)
    }

    @Test fun sceneCreationMenuRechecksLiveEditabilityBeforeAdding() {
        val child=state().document.removeComponent("BoxCollider")
        val scene=SceneWorkspaceState(catalog) { it }
        scene.openResolved(SceneDocument(entities=listOf(child)).serialize(),emptyMap(),java.nio.file.Path.of("test.slescene"))
        scene.select(child.key)
        var editable=true
        val entries=sceneCreationMenu(scene,true,editableNow={ editable })
        val entry=entries.single { it.label=="Add Component" }.children.flatMap { it.children }.single { it.label=="BoxCollider" }
        editable=false
        entry.action()
        assertFalse("BoxCollider" in scene.entity(child.key)!!.document.components)
        assertFalse(scene.history.canUndo)
    }
    @Test fun autoFitEventAreaAndRequiredComponentsUndoTogether() {
        val zone=JsonObject(box + ("zoneId" to JsonPrimitive("")))
        val zoneCatalog=catalog + mapOf("BoxZone" to ComponentDefinition(zone,obj("{}")),
            "Transform" to ComponentDefinition(obj("""{"scale":{"x":1,"y":1}}"""),obj("{}")),
            "RigidBody" to ComponentDefinition(obj("""{"bodyType":"Dynamic"}"""),obj("{}")))
        val initial=state(scale=null).document.removeComponent("BoxCollider")
        val state=EntityWorkspaceState(zoneCatalog).also { it.resetTo(initial) }
        addComponentEntries(state).flatMap { it.children }.single { it.label=="Box Event Area" }.action()
        assertEquals(1.0,value(state,"BoxZone","size","x"))
        assertTrue("Transform" in state.document.components)
        assertTrue("RigidBody" in state.document.components)
        val after=state.document
        state.undo()
        assertEquals(initial,state.document)
        assertFalse(state.canUndo)
        state.redo()
        assertEquals(after,state.document)
    }

    @Test fun persistentFitCallbackSurvivesUntouchedNumericFocusAndBlur() {
        val state=state()
        val before=state.document
        var editable=true
        val callback=entityEditAction(state,{ editable }) { fitShapeToSprite(state,"BoxCollider") }
        val edit=state.numberEdit("BoxCollider",listOf("size","x"))
        edit.focus()
        edit.blur()
        assertSame(before,state.document)
        assertFalse(state.canUndo)
        callback()
        assertEquals(32.0,value(state,"BoxCollider","size","x"))
        state.undo()
        assertEquals(before,state.document)
        assertFalse(state.canUndo)
        editable=false
        callback()
        assertEquals(before,state.document)
        assertFalse(state.canUndo)
        editable=true
        state.resetTo(before.copy(name="Replacement"))
        val replacement=state.document
        callback()
        assertSame(replacement,state.document)
        assertFalse(state.canUndo)
    }

}
