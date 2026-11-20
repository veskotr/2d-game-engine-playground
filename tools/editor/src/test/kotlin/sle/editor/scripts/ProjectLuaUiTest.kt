package sle.editor.scripts

import kotlin.test.*
import kotlinx.serialization.json.*
import sle.editor.command.CommandId
import sle.editor.command.EditorCommand
import sle.editor.model.decodeComponentCatalog
import sle.editor.ui.inspector.PropertyDrawer
import sle.editor.ui.inspector.propertyDefinition

class ProjectLuaUiTest {
    private fun row(text: String) = Json.parseToJsonElement(text).jsonObject

    @Test fun drawerMatchesOnlyItsFieldAndCompatibleShape() {
        val (target, drawer) = readProjectDrawer(row(
            """{"component":"SpriteRenderer","path":["uv"],"widget":"uv"}"""
        ))
        val drawers = mapOf(target to drawer)
        val vector = row("""{"x":0,"y":0,"z":1,"w":1}""")
        assertEquals(PropertyDrawer.Uv, projectDrawer(propertyDefinition("SpriteRenderer", listOf("uv"), vector), drawers))
        assertNull(projectDrawer(propertyDefinition("SpriteRenderer", listOf("uv"), JsonPrimitive(4)), drawers))
        assertNull(projectDrawer(propertyDefinition("SpriteRenderer", listOf("nested", "uv"), vector), drawers))
        val catalog = decodeComponentCatalog("""{"SpriteRenderer":{"defaults":{},"enums":{},"fields":[
            {"path":["uv"],"drawer":"uv","axisLabels":{"x":"U0","y":"V0","z":"U1","w":"V1"}}
        ]}}""")
        val property = propertyDefinition("SpriteRenderer", listOf("uv"), vector, catalog = catalog.getValue("SpriteRenderer"))
        assertEquals(listOf("U0", "V0", "U1", "V1"), property.axisLabels)
        assertEquals(listOf("R", "G", "B", "A"), property.copy(projectDrawer = PropertyDrawer.Color).axisLabels)
        assertFails { readProjectDrawer(row("""{"component":"SpriteRenderer","path":["uv"],"widget":"custom"}""")) }
    }

    @Test fun panelUsesRegisteredCommandsAndRejectsOtherWidgets() {
        val script = ProjectLuaCommand("probe", "Tools", false, "Editor/probe.editor.lua", byteArrayOf())
        val command = EditorCommand(CommandId("project.lua.probe"), "Probe", run = {})
        val registered = RegisteredProjectLuaCommand(script, command)
        val descriptor = row("""{"id":"tools","label":"Tools","widgets":[
            {"kind":"label","text":"Project tools"},{"kind":"button","command":"probe"}
        ]}""")
        val panel = readProjectPanel(descriptor, mapOf("probe" to registered))
        assertEquals(ProjectPanelWidget.Label("Project tools"), panel.widgets.first())
        assertSame(registered, (panel.widgets.last() as ProjectPanelWidget.Button).command)
        assertFails { readProjectPanel(descriptor, emptyMap()) }
        assertFails { readProjectPanel(row("""{"id":"tools","label":"Tools","widgets":[{"kind":"html"}]}"""), emptyMap()) }
    }
}
