package sle.editor.model

import kotlinx.serialization.json.*
import sle.editor.viewport.LiveInspection
import sle.editor.viewport.LiveSelection

internal data class KeepPlayField(
    val label: String,
    val path: List<String>,
    val authored: JsonPrimitive,
    val runtime: JsonPrimitive,
)

internal data class KeepPlayPreview(
    val selection: LiveSelection,
    val key: String,
    val fields: List<KeepPlayField>,
)

internal class KeepPlayBaseline(val revision: Long, private val scene: SceneWorkspaceState, source: SceneDocument) {
    private data class Entry(
        val owner: EntityWorkspaceState,
        val authored: JsonObject,
        val initial: JsonObject,
    )

    private val parents = source.parents
    private val entries = source.entities.mapNotNull { document ->
        val owner = scene.entity(document.key) ?: return@mapNotNull null
        if (scene.linkedInstance(document.key) != null) return@mapNotNull null
        val authored = owner.document.transform ?: return@mapNotNull null
        val initial = document.transform ?: return@mapNotNull null
        document.key to Entry(owner, authored, initial)
    }.toMap()

    fun preview(currentScene: SceneWorkspaceState, live: LiveInspection): KeepPlayPreview? {
        if (currentScene !== scene || live.playRevision != revision) return null
        val selection = live.selection ?: return null
        if (selection.run != live.run || selection.scene != live.scene) return null
        val selected = live.entities.firstOrNull { it.id == selection.entity } ?: return null
        val key = selected.authoredKey ?: return null
        val entry = entries[key] ?: return null
        if (scene.entity(key) !== entry.owner || scene.linkedInstance(key) != null) return null
        val runtimeParent = live.entities.firstOrNull { it.id == selected.parent }?.authoredKey
        val authoredParent = parents[key]
        if (scene.parents[key] != authoredParent || runtimeParent != authoredParent ||
            (selected.parent != 0L && runtimeParent == null)) return null
        val current = entry.owner.document.transform ?: return null
        val runtime = live.details?.get("Transform") as? JsonObject ?: return null
        val fields = transformFields.mapNotNull { (label, path) ->
            val before = entry.initial.field(path) ?: return null
            val initialValue = before.number() ?: return null
            val value = runtime.field(path)?.number() ?: return null
            if (initialValue.toFloat() == value.toFloat()) null
            else KeepPlayField(label, path, before, JsonPrimitive(value.toFloat()))
        }
        if (fields.all { current.field(it.path)?.number() == it.runtime.number() }) return null
        return KeepPlayPreview(selection, key, fields)
    }

    fun apply(currentScene: SceneWorkspaceState, preview: KeepPlayPreview) {
        val entry = entries.getValue(preview.key)
        val owner = entry.owner
        val current = checkNotNull(owner.document.transform)
        for (field in preview.fields) {
            val id = "Transform/" + field.path.joinToString("/")
            require(current.field(field.path) == entry.authored.field(field.path) &&
                owner.fieldErrors.none { it == id || id.startsWith("$it/") }) {
                "${field.label} was edited after Play started."
            }
        }
        var candidate = owner.document
        for (field in preview.fields) {
            if (candidate.transform?.field(field.path) == null) {
                // Canonical Play data supplies defaults for omitted authored fields.
                val group = field.path.first()
                val transform = checkNotNull(candidate.transform)
                candidate = candidate.copy(components = JsonObject(candidate.components +
                    ("Transform" to JsonObject(transform + (group to entry.initial.getValue(group))))))
            }
            candidate = candidate.setValue("Transform", field.path, field.runtime)
        }
        currentScene.validatePropertyCandidate(owner, candidate)
        owner.publishPropertyCandidate(candidate, "Keep Play Transform")
    }
}

private val transformFields = listOf(
    "Position X" to listOf("position", "x"),
    "Position Y" to listOf("position", "y"),
    "Rotation" to listOf("rotation"),
    "Scale X" to listOf("scale", "x"),
    "Scale Y" to listOf("scale", "y"),
)

private val EntityDocument.transform get() = components["Transform"] as? JsonObject

private fun JsonObject.field(path: List<String>): JsonPrimitive? =
    path.fold<String, JsonElement?>(this) { value, key -> (value as? JsonObject)?.get(key) } as? JsonPrimitive

private fun JsonPrimitive.number(): Double? =
    if (isString) null else doubleOrNull?.takeIf { it.toFloat().isFinite() }
