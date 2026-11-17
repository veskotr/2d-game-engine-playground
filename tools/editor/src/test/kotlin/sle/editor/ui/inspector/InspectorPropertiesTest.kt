package sle.editor.ui.inspector

import kotlinx.serialization.json.*
import sle.editor.model.*
import kotlin.test.*

class InspectorPropertiesTest {
    @Test fun classifiesCatalogValuesWithoutChangingSource() {
        val defaults=Json.parseToJsonElement("""{"enabled":true,"text":"true","position":{"x":0,"y":1},"region":{"x":0,"y":0,"width":16,"height":32},"mode":"A","texture":"","scripts":["a.lua"]}""").jsonObject
        val catalog=ComponentDefinition(defaults,buildJsonObject { put("mode",buildJsonArray { add("A");add("B") }) })
        val properties=inspectorProperties("SpriteRenderer",defaults,catalog)
        assertEquals(listOf(PropertyKind.Boolean,PropertyKind.String,PropertyKind.Vector,PropertyKind.Json,PropertyKind.Enum,PropertyKind.Resource,PropertyKind.Json),properties.map { it.kind })
        assertEquals(listOf(listOf("position","x"),listOf("position","y")),properties[2].axes.map { it.path })
        assertEquals(defaults["region"],properties[3].value)
        assertEquals(listOf("A","B"),properties[4].choices.map { it.jsonPrimitive.content })
        val state=EntityWorkspaceState(emptyMap())
        state.document=state.document.copy(components=buildJsonObject { put("SpriteRenderer",defaults) })
        val before=state.document.serialize()
        inspectorProperties("SpriteRenderer",defaults,catalog)
        state.inspectorSections.setExpanded(state.document.key,"SpriteRenderer",false)
        assertEquals(before,state.document.serialize())
        assertFalse(state.canUndo)
    }
    @Test fun displayLabelsAreHumanizedButKeysAreUnchanged() {
        assertEquals("Position",humanizePropertyLabel("position"))
        assertEquals("Linear damping",humanizePropertyLabel("linearDamping"))
        assertEquals("Max speed",humanizePropertyLabel("max_speed"))
        assertEquals("Body ID",humanizePropertyLabel("body ID"))
        assertEquals("",humanizePropertyLabel(""))
        val property=propertyDefinition("RigidBody2D",listOf("linearDamping"),JsonPrimitive(0.5))
        assertEquals("linearDamping",property.label)
        assertEquals("Linear damping",property.displayLabel)
        assertEquals(listOf("linearDamping"),property.path)
    }
    @Test fun sectionsSurvivePresenterRecreationAndSeparateEntitiesAndComponents() {
        val state=EntityWorkspaceState(emptyMap())
        val sections=state.inspectorSections
        sections.setExpanded("one","Transform",false)
        assertFalse(state.inspectorSections.expanded("one","Transform"))
        assertTrue(sections.expanded("two","Transform"))
        assertTrue(sections.expanded("one","Camera2D"))
    }
    @Test fun mixedAxisObjectsStayStructured() {
        val values=Json.parseToJsonElement("""{"position":{"x":0,"y":"unknown"},"nested":{"x":0,"y":1,"extra":2}}""").jsonObject
        assertTrue(inspectorProperties("Test",values,null).all { it.kind==PropertyKind.Json })
    }
    private fun numericState()=EntityWorkspaceState(mapOf("Transform" to ComponentDefinition(
        buildJsonObject { put("rotation",0) },JsonObject(emptyMap()))))

    @Test fun componentAndToolChangesFinishNumericHistory() {
        for(change in listOf<(EntityWorkspaceState)->Unit>({it.selectedComponent="Transform"},{it.selectedTarget="SpriteRenderer"})) {
            val state=numericState();val edit=state.numberEdit("Transform",listOf("rotation"))
            edit.focus();edit.input("7");change(state);edit.cancel()
            assertEquals("7",edit.text)
            assertFalse(state.gestureActive)
            state.undo();assertEquals("0",edit.text)
        }
    }
    @Test fun switchingTabsFinishesOwnerEditWithoutDiscardingDraft() {
        val state=numericState()
        val workspace=sle.editor.workspace.EditorWorkspace(Result.success(state),Result.failure(IllegalStateException("unused")))
        try {
            workspace.selectedTab=0
            val edit=state.numberEdit("Transform",listOf("rotation"))
            edit.focus();edit.input("7");edit.input("-")
            workspace.selectedTab=2
            edit.cancel()
            assertFalse(state.gestureActive)
            assertEquals("-",edit.text)
            assertTrue(state.fieldErrors.isNotEmpty())
            state.undo();assertEquals("0",edit.text)
        } finally { workspace.close() }
    }
}
