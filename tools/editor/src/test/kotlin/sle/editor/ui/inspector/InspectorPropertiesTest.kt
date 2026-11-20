package sle.editor.ui.inspector

import kotlinx.serialization.json.*
import sle.editor.model.*
import kotlin.test.*

class InspectorPropertiesTest {
    @Test fun resolvesRegisteredDrawersAndPreservesShapeFallbacks() {
        val catalog = decodeComponentCatalog("""
            {"SpriteRenderer":{"defaults":{"color":{"x":1,"y":1,"z":1,"w":0.5},
              "region":{"uv":{"x":0,"y":0,"z":1,"w":1}},"texture":"sprite.png",
              "other":{"x":1,"y":2},"variables":{"flag":true}},"enums":{},"fields":[
              {"path":["color"],"drawer":"missing"},
              {"path":["region","uv"],"drawer":"uv"},
              {"path":["other"],"drawer":"color"},
              {"path":["texture"],"drawer":"color"}
            ]}}
        """).getValue("SpriteRenderer")
        fun property(vararg path: String): EditorPropertyDefinition {
            val value = path.fold(catalog.defaults as JsonElement) { parent, key -> parent.jsonObject.getValue(key) }
            return propertyDefinition("SpriteRenderer", path.toList(), value, catalog = catalog)
        }
        assertEquals(PropertyDrawer.Color, property("color").drawer)
        assertEquals(PropertyDrawer.Uv, property("region", "uv").drawer)
        assertEquals(listOf("U0", "V0", "U1", "V1"), property("region", "uv").axisLabels)
        assertEquals(PropertyDrawer.Texture, property("texture").drawer)
        assertEquals(PropertyDrawer.Structured, property("region").drawer)
        assertNull(property("other").drawer)
        assertNull(property("variables").drawer)
    }

    @Test fun keepsColorCaptionsWhenTheChooserCannotEditAllChannels() {
        val catalog = decodeComponentCatalog("""
            {"Fixture":{"defaults":{"tint":{"x":1,"y":1,"z":1},
              "rgba":{"x":1,"y":1,"z":1,"w":0.5}},"enums":{},"fields":[
              {"path":["rgba"],"drawer":"color","axisLabels":{"x":"Red"}},
              {"path":["rgba","w"],"visibleWhen":{"path":["x"],"equals":0}}
            ]}}
        """).getValue("Fixture")
        val rows = inspectorProperties("Fixture", catalog.defaults, catalog).associateBy { it.label }
        assertEquals(listOf("R", "G", "B"), rows.getValue("tint").axisLabels)
        assertFalse(rows.getValue("tint").isColor)
        val rgba = rows.getValue("rgba")
        assertEquals(PropertyDrawer.Color, rgba.drawer)
        assertEquals(listOf("Red", "G", "B"), rgba.axisLabels)
        assertFalse(rgba.isColor)
    }

    @Test fun incompatibleTypeKeepsLegacyStructuredRowsAndProtectsWholeValues() {
        val catalog = decodeComponentCatalog("""
            {"SpriteRenderer":{"defaults":{"region":{"uv":{"x":0,"y":1}}},"enums":{},"groups":{"edge":{}},"fields":[
              {"path":["region"],"type":"number"},
              {"path":["region","uv","x"],"readOnly":true,"group":"edge","type":"enum","choices":[0,1]}
            ]}}
        """).getValue("SpriteRenderer")
        val region = inspectorBlocks("SpriteRenderer", catalog.defaults, catalog).single().properties.single()
        assertTrue(region.structured)
        assertTrue(region.hasReadOnlyDescendants)
        val uv = inspectorBlocks("SpriteRenderer", region.value.jsonObject, catalog, region.path)
            .single().properties.single()
        assertFalse(uv.readOnly)
        assertTrue(uv.hasReadOnlyDescendants)
        assertTrue(uv.axes.first().readOnly)
        assertEquals(PropertyKind.Enum, uv.axes.first().kind)
        assertEquals(listOf(null, "edge"), groupInspectorProperties(uv.axes, catalog).map { it.group })
        assertFalse(uv.axes.last().readOnly)
    }

    @Test fun projectsOrderedGroupsFromLiveSiblingValues() {
        val catalog = decodeComponentCatalog("""
            {"Camera":{"defaults":{"enabled":true,"zoom":6,"size":2,"fallback":0},"enums":{},
             "groups":{"view":{"order":1}},"fields":[
               {"path":["zoom"],"group":"view","order":1,"visibleWhen":{"path":["enabled"],"equals":true}},
               {"path":["size"],"group":"view","order":1},
               {"path":["fallback"],"group":"missing","order":2}]}}
        """).getValue("Camera")
        val values = catalog.defaults
        fun paths(values: JsonObject) = inspectorBlocks("Camera", values, catalog)
            .map { it.group to it.properties.map { property -> property.path.single() } }
        assertEquals(listOf(null to listOf("fallback", "enabled"), "view" to listOf("zoom", "size")), paths(values))
        val disabled = JsonObject(values + ("enabled" to JsonPrimitive(false)))
        assertEquals(listOf("size"), paths(disabled).last().second)
        assertEquals(JsonPrimitive(6), disabled["zoom"])
        assertEquals(listOf("zoom", "size"), paths(JsonObject(values - "enabled")).last().second)
    }

    @Test fun usesLivePreviewsAndKeepsHiddenDraftsAndReadOnlyAxes() {
        val catalog = decodeComponentCatalog("""
            {"Fixture":{"defaults":{"mode":1,"speed":2,"offset":{"x":0,"y":0},"legacy":8},"enums":{},"fields":[
              {"path":["speed"],"visibleWhen":{"path":["mode"],"in":[1,"a"]}},
              {"path":["offset"],"readOnly":true},
              {"path":["legacy"],"visibleWhen":{"path":["mode"],"equals":1,"in":[1]}}
            ]}}
        """)
        val state = EntityWorkspaceState(catalog).apply { addComponent("Fixture") }
        fun rows() = inspectorBlocks("Fixture", state.document.components.getValue("Fixture").jsonObject,
            catalog.getValue("Fixture")).flatMap { it.properties }
        val speed = state.numberEdit("Fixture", listOf("speed"))
        speed.updateText("1e", true)
        speed.blur()
        val mode = state.numberEdit("Fixture", listOf("mode"))
        mode.updateText("0", true)
        assertFalse(rows().any { it.path == listOf("speed") })
        assertTrue(rows().any { it.path == listOf("legacy") })
        assertTrue(rows().single { it.path == listOf("offset") }.axes.all { it.readOnly })
        assertEquals("1e", speed.text)
        assertTrue(speed.invalid)
        mode.cancel()
        assertTrue(rows().any { it.path == listOf("speed") })
    }

    @Test fun resolvesCatalogMetadataAgainstAuthoredValues() {
        val catalog = decodeComponentCatalog("""
            {"Legacy":{"defaults":{"a":1},"enums":{}},
             "Lua":{"defaults":{"speed":3,"enabled":true,"caption":"Runner","nested":{"axis":null}},"enums":{},
              "groups":{"motion":{"label":"Motion","order":1},"invalid":false},
              "fields":[
                {"path":["speed"],"type":"number","group":"motion","help":"Movement speed",
                 "ui":{"min":0,"max":10,"step":0.5,"precision":"bad"},
                 "visibleWhen":{"path":["enabled"],"equals":true},"future":{}},
                {"path":["nested","axis"],"type":"json"},
                {"path":["caption"],"type":"string","readOnly":true},
                {"path":["enabled"],"type":"unknown"},
                {"path":["duplicate"],"type":"string"},{"path":["duplicate"],"type":"number"},
                {"path":[1],"type":"number"},false]}}
        """.trimIndent())
        assertTrue(catalog.getValue("Legacy").fields.isEmpty())
        val lua = catalog.getValue("Lua")
        assertEquals(setOf("motion"), lua.groups.keys)
        assertEquals(setOf(listOf("speed"), listOf("enabled"), listOf("nested", "axis"), listOf("caption")), lua.fields.keys)
        val caption = propertyDefinition("Lua", listOf("caption"), JsonPrimitive("Actor"), catalog = lua)
        assertEquals(PropertyKind.String, caption.kind)
        assertEquals(JsonPrimitive("Runner"), caption.defaultValue)
        assertTrue(caption.readOnly)
        val speed = propertyDefinition("Lua", listOf("speed"), JsonPrimitive(12), catalog = lua)
        assertEquals(PropertyKind.Number, speed.kind)
        assertEquals(JsonPrimitive(3), speed.defaultValue)
        assertEquals("Movement speed", speed.descriptor?.help)
        assertEquals(0.5, speed.descriptor?.ui?.step)
        assertNull(speed.descriptor?.ui?.precision)
        assertEquals(JsonPrimitive(true), speed.descriptor?.visibleWhen?.equalsValue)
        assertEquals(PropertyKind.String,
            propertyDefinition("Lua", listOf("speed"), JsonPrimitive("bad"), catalog = lua).kind)
        assertEquals(JsonNull, propertyDefinition("Lua", listOf("nested", "axis"), JsonNull, catalog = lua).defaultValue)
        assertNull(propertyDefinition("Lua", listOf("missing"), JsonNull, catalog = lua).defaultValue)
        assertEquals(PropertyKind.Boolean,
            propertyDefinition("Lua", listOf("enabled"), JsonPrimitive(true), catalog = lua).kind)
    }
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
