package sle.editor.scripts

import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.serialization.json.*
import sle.editor.model.EntityWorkspaceState
import sle.editor.model.behaviourEntries
import sle.editor.model.behaviourText
import sle.editor.project.ProjectWorkspaceState
import sle.editor.project.assets.ProjectAssetOperations
import java.nio.file.Path

internal val LocalNewScriptBehavior = staticCompositionLocalOf<((EntityWorkspaceState) -> Unit)?> { null }

internal enum class BuiltInScriptTemplate(
    override val label: String,
    private val source: String,
) : ScriptTemplate {
    Basic("Basic behavior", """
        return {
            update = function(entity, dt)
            end,
        }
    """.trimIndent()),
    ExposedProperty("Behavior with exposed property", """
        return {
            properties = {
                speed = {type = "number", default = 3},
            },
            init = function(entity, parameters)
                local speed = parameters.speed
            end,
        }
    """.trimIndent()),
    EventArea("Event area listener", """
        return {
            init = function(entity)
                Engine.Events.subscribe("zone.enter", function(event)
                    if event.entityA == entity then
                        Engine.log("Entered event area " .. (event.zoneId or tostring(entity)))
                    end
                end)
                Engine.Events.subscribe("zone.exit", function(event)
                    if event.entityA == entity then
                        Engine.log("Exited event area " .. (event.zoneId or tostring(entity)))
                    end
                end)
            end,
        }
    """.trimIndent());

    override val filename get() = "$name.lua"
    override fun read() = (source + "\n").encodeToByteArray()
}

internal class ScriptCreation(
    private val workspace: ProjectWorkspaceState,
    private val target: EntityWorkspaceState,
    private val targetIsCurrent: () -> Boolean,
) {
    private val project = requireNotNull(workspace.opened) { "Open a project to create a Lua behavior." }
    private val activation = workspace.activationRevision
    private val original = target.document

    fun requireCurrent() {
        check(workspace.activationRevision == activation && workspace.opened?.assets == project.assets) {
            "The project changed."
        }
        check(targetIsCurrent() && target.document === original) {
            "The target entity is no longer current."
        }
    }

    /** File I/O only. The caller checks the editor state before dispatching and after returning. */
    fun templates(): List<ScriptTemplate> = scriptTemplates(project.root)

    fun create(relative: String, template: ScriptTemplate): Path {
        require(relative.endsWith(".lua", ignoreCase = true) && relative.substringAfterLast('/').length > 4) {
            "Choose a destination filename ending in .lua."
        }
        return ProjectAssetOperations(project).createFile(relative, template.read())
    }

    fun attach(source: Path) {
        requireCurrent()
        val script = (target.document.components["Script"] as? JsonObject).orEmpty()
        val entries = (script["behaviours"] as? JsonArray).orEmpty()
        val names = JsonObject(script).behaviourEntries().mapNotNull { it.behaviourText("key") }.toSet()
        val base = source.fileName.toString().substringBeforeLast('.')
        val name = if (base !in names) base else generateSequence(2) { it + 1 }
            .map { "${base}_$it" }.first { it !in names }
        val entry = buildJsonObject {
            put("key", name)
            put("scriptAsset", source.toString().replace('\\', '/'))
            put("enabled", true)
        }
        val updated = JsonObject(script + ("behaviours" to JsonArray(entries + entry)))
        val candidate = target.document.copy(components = JsonObject(target.document.components + ("Script" to updated)))
        target.publishPropertyCandidate(candidate, "Attach Lua behavior $name")
    }
}
