package sle.editor.scripts

import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.serialization.json.*
import sle.editor.ui.inspector.EditorPropertyDefinition
import sle.editor.ui.inspector.PropertyDrawer

internal data class ProjectDrawerTarget(val component: String, val path: List<String>)

internal val LocalProjectDrawers = staticCompositionLocalOf<Map<ProjectDrawerTarget, PropertyDrawer>> { emptyMap() }

internal fun projectDrawer(
    property: EditorPropertyDefinition,
    drawers: Map<ProjectDrawerTarget, PropertyDrawer>,
): PropertyDrawer? = drawers[ProjectDrawerTarget(property.component, property.path)]
    ?.takeIf { it.accepts(property) }

internal sealed interface ProjectPanelWidget {
    data class Label(val text: String) : ProjectPanelWidget
    data class Button(val command: RegisteredProjectLuaCommand) : ProjectPanelWidget
}

internal data class ProjectLuaPanel(val id: String, val label: String, val widgets: List<ProjectPanelWidget>)

private fun JsonElement.text(limit: Int): String =
    jsonPrimitive.takeIf { it.isString }?.content
        ?.takeIf { it.isNotBlank() && it.length <= limit && it.none(Char::isISOControl) }
        ?: error("Expected printable text up to $limit characters")

internal fun readProjectDrawer(row: JsonObject): Pair<ProjectDrawerTarget, PropertyDrawer> {
    val path = row.getValue("path").jsonArray
    require(path.size in 1..16) { "Drawer paths need 1 to 16 fields" }
    val component = row.getValue("component").text(80)
    val widget = row.getValue("widget").text(32)
    val drawer = PropertyDrawer.entries.firstOrNull { it.id == widget } ?: error("Unknown drawer widget '$widget'")
    val target = ProjectDrawerTarget(component, path.map { it.text(80) })
    require(drawer != PropertyDrawer.Texture || target == ProjectDrawerTarget("SpriteRenderer", listOf("texture"))) {
        "The texture drawer requires SpriteRenderer.texture"
    }
    return target to drawer
}

internal fun readProjectPanel(
    row: JsonObject,
    commands: Map<String, RegisteredProjectLuaCommand>,
): ProjectLuaPanel {
    val id = row.getValue("id").text(80)
    require(id.matches(Regex("[A-Za-z0-9_.-]+"))) { "Invalid panel ID '$id'" }
    val label = row.getValue("label").text(120)
    val widgets = row.getValue("widgets").jsonArray
    require(widgets.size in 1..16) { "Panels need 1 to 16 widgets" }
    return ProjectLuaPanel(id, label, widgets.map { widget ->
        val spec = widget.jsonObject
        when (val kind = spec.getValue("kind").text(32)) {
            "label" -> ProjectPanelWidget.Label(spec.getValue("text").text(512))
            "button" -> {
                val command = spec.getValue("command").text(80)
                ProjectPanelWidget.Button(commands[command] ?: error("Unknown project command '$command'"))
            }
            else -> error("Unknown panel widget '$kind'")
        }
    })
}
