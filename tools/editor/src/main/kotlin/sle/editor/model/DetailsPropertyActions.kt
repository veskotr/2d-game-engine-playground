package sle.editor.model

import kotlinx.serialization.json.*
import sle.editor.files.PrefabPackages
import sle.editor.ui.inspector.EditorPropertyDefinition
import sle.editor.ui.inspector.PropertyKind
import sle.editor.ui.inspector.propertyDefinition
import java.nio.file.Files
import java.nio.file.Path

internal data class PropertyClipboardValue(val text: String, val kind: PropertyKind? = null)

internal class DetailsPropertyActions(
    private val validatePrefab: (String) -> String = ::canonicalPrefabDocument,
) {
    fun replace(
        state: EntityWorkspaceState,
        property: EditorPropertyDefinition,
        text: String,
        verb: String,
        sourceKind: PropertyKind? = null,
        resolveProjectAsset: ((String) -> String)? = null,
    ) {
        require(!property.readOnly && !property.hasReadOnlyDescendants) { "This property is read-only" }
        val value = Json.parseToJsonElement(text)
        require(value.isClipboardJson()) { "Enter a JSON value (quote strings)." }
        require(sourceKind == null || (sourceKind == PropertyKind.Resource) == (property.kind == PropertyKind.Resource)) {
            "Clipboard resource type does not match this property"
        }
        validateShape(property, value)
        if (value == property.value) return

        val resolve = assetResolver(state, resolveProjectAsset)
        val replacement = rewriteChangedAssets(value, property.value, property.path, resolve,
            property.kind == PropertyKind.Resource)
        val candidate = state.document.setValue(property.component, property.path, replacement)
        publish(state, candidate, "$verb ${property.displayLabel}")
    }

    fun copyComponent(state: EntityWorkspaceState, component: String): String {
        val current = state.document.components.getValue(component).jsonObject
        val values = ComponentValueMerge(component, state.catalog.getValue(component))
            .apply(current, JsonObject(emptyMap()), ComponentValueMode.Copy)
        return buildJsonObject {
            put("component", component)
            put("values", values)
        }.toString()
    }

    fun replaceComponent(
        state: EntityWorkspaceState,
        component: String,
        clipboard: String? = null,
        resolveProjectAsset: ((String) -> String)? = null,
    ) {
        val current = state.document.components.getValue(component).jsonObject
        val catalog = state.catalog.getValue(component)
        val supplied = if (clipboard == null) catalog.defaults else {
            val envelope = Json.parseToJsonElement(clipboard) as? JsonObject
            require(envelope != null && envelope.keys == setOf("component", "values") &&
                envelope["component"] == JsonPrimitive(component) && envelope.isClipboardJson()) {
                "Clipboard must contain a $component component"
            }
            val values = envelope["values"] as? JsonObject ?: error("Clipboard component values must be an object")
            if (component == "Script") canonicalizeBehaviourAssets(values) else values
        }
        val mode = if (clipboard == null) ComponentValueMode.Reset else ComponentValueMode.Paste
        val merged = ComponentValueMerge(component, catalog).apply(supplied, current, mode)
        val resolve = assetResolver(state, resolveProjectAsset)
        val rewritten = rewriteChangedAssets(merged, current, emptyList(), resolve)
        val candidate = state.document.copy(components = JsonObject(state.document.components + (component to rewritten)))
        publish(state, candidate, "${if (clipboard == null) "Reset" else "Paste"} $component")
    }

    private fun publish(state: EntityWorkspaceState, candidate: EntityDocument, label: String) {
        if (candidate == state.document) return
        val scene = state.timeline as? SceneWorkspaceState
        if (scene != null) scene.validatePropertyCandidate(state, candidate)
        else validatePrefab(state.prefabDocument(candidate).serialize())
        state.publishPropertyCandidate(candidate, label)
    }

    private fun assetResolver(
        state: EntityWorkspaceState,
        resolveProjectAsset: ((String) -> String)?,
    ): (String) -> String {
        val scene = state.timeline as? SceneWorkspaceState
        val file = if (scene != null) scene.file else state.file
        val projectDocument = if (scene != null) scene.projectDocument else state.projectDocument
        val projectResolver = resolveProjectAsset.takeIf { file == null || projectDocument != null }
        return projectResolver ?: { raw: String ->
            require(!raw.startsWith("asset://") && !raw.startsWith("project-asset://")) { "Unresolved asset: $raw" }
            val path = Path.of(raw)
            val resolved = listOfNotNull(file?.toAbsolutePath()?.parent?.resolve(path), path.toAbsolutePath())
                .firstOrNull(Files::isRegularFile) ?: error("Unresolved asset: $raw")
            resolved.normalize().toString().replace('\\', '/')
        }
    }
}

private fun canonicalizeBehaviourAssets(values: JsonObject): JsonObject {
    val behaviours = values["behaviours"] as? JsonArray ?: return values
    val normalized = behaviours.map { entry ->
        val fields = entry as? JsonObject ?: return@map entry
        val asset = fields["asset"] ?: return@map fields
        require("scriptAsset" !in fields) { "Behaviour asset conflicts with scriptAsset" }
        JsonObject(fields - "asset" + ("scriptAsset" to asset))
    }
    return JsonObject(values + ("behaviours" to JsonArray(normalized)))
}

private enum class ComponentValueMode { Copy, Reset, Paste }

private class ComponentValueMerge(private val component: String, private val catalog: ComponentDefinition) {
    fun apply(input: JsonObject, current: JsonObject, mode: ComponentValueMode): JsonObject =
        merge(input, current, catalog.defaults, emptyList(), mode)

    private fun merge(
        input: JsonObject,
        current: JsonObject,
        defaults: JsonObject,
        parent: List<String>,
        mode: ComponentValueMode,
        dynamic: Boolean = false,
    ): JsonObject {
        val result = current.toMutableMap()
        for ((key, value) in input) {
            val path = parent + key
            val default = defaults[key]
            val protected = catalog.fields[path]?.readOnly == true ||
                (component == "Animator" && path == listOf("targetEntities"))
            if (protected || (default == null && !dynamic)) {
                require(mode != ComponentValueMode.Paste) { "Unsupported component field: ${path.joinToString(".")}" }
                continue
            }

            val previous = current[key]
            val writable = if (default is JsonObject) {
                if (value !is JsonObject || (previous != null && previous !is JsonObject)) {
                    require(mode != ComponentValueMode.Paste) { "Expected an object at ${path.joinToString(".")}" }
                    continue
                }
                val mapField = default.isEmpty()
                val existing = (previous as? JsonObject).orEmpty()
                val base = if (mapField && mode == ComponentValueMode.Reset)
                    existing.filterKeys { hasProtectedDescendant(path + it) || catalog.fields[path + it]?.readOnly == true }
                else existing
                val nested = merge(value, JsonObject(base), default, path, mode, mapField)
                if (nested.isEmpty() && previous == null && !mapField) continue
                nested
            } else {
                if (hasProtectedDescendant(path)) {
                    require(mode != ComponentValueMode.Paste) { "Component field contains read-only values: ${path.joinToString(".")}" }
                    continue
                }
                if (mode == ComponentValueMode.Paste && default != null) {
                    validateShape(propertyDefinition(component, path, default, catalog = catalog), value)
                }
                value
            }
            result[key] = writable
        }
        return JsonObject(result)
    }

    private fun hasProtectedDescendant(path: List<String>): Boolean =
        catalog.fields.values.any { it.readOnly && it.path.size > path.size && it.path.take(path.size) == path }
}

private val jsonNumber = Regex("-?(0|[1-9][0-9]*)(\\.[0-9]+)?([eE][+-]?[0-9]+)?")

private fun JsonElement.isClipboardJson(): Boolean = when (this) {
    is JsonObject -> values.all { it.isClipboardJson() }
    is JsonArray -> all { it.isClipboardJson() }
    is JsonPrimitive -> isString || content in setOf("null", "true", "false") ||
        (jsonNumber.matches(content) && doubleOrNull?.isFinite() == true)
}

private fun validateShape(property: EditorPropertyDefinition, value: JsonElement) {
    val scalar = value as? JsonPrimitive
    val number = scalar?.takeUnless { it.isString }?.doubleOrNull
    val compatible = when (property.kind) {
        PropertyKind.String, PropertyKind.Resource -> scalar?.isString == true
        PropertyKind.Boolean -> scalar?.takeUnless { it.isString }?.booleanOrNull != null
        PropertyKind.Integer -> scalar?.takeUnless { it.isString }?.longOrNull != null
        PropertyKind.Number -> number != null
        PropertyKind.Enum -> value in property.choices
        PropertyKind.Vector -> value is JsonObject && value.keys == property.value.jsonObject.keys &&
            value.values.all { it is JsonPrimitive && !it.isString && it.doubleOrNull != null }
        PropertyKind.Object -> value is JsonObject
        PropertyKind.Array -> value is JsonArray
        PropertyKind.Json -> when (property.value) {
            is JsonObject -> value is JsonObject
            is JsonArray -> value is JsonArray
            else -> true
        }
    }
    require(compatible) { "Clipboard value does not match ${property.displayLabel}" }
    property.descriptor?.hard?.let { bounds ->
        if (number != null) {
            require(bounds.min?.let { number > it || (!bounds.minExclusive && number == it) } != false &&
                bounds.max?.let { number < it || (!bounds.maxExclusive && number == it) } != false) {
                "${property.displayLabel} is outside its allowed range"
            }
        }
    }
    if (value is JsonObject) property.axes.forEach { axis ->
        validateShape(axis, value.getValue(axis.path.last()))
    }
}

private fun rewriteChangedAssets(
    value: JsonElement,
    previous: JsonElement?,
    path: List<String>,
    resolve: (String) -> String,
    resource: Boolean = false,
): JsonElement {
    if (value == previous || path.firstOrNull() in setOf("variables", "targetEntities")) return value
    return when (value) {
        is JsonObject -> JsonObject(value.mapValues { (key, child) ->
            rewriteChangedAssets(child, (previous as? JsonObject)?.get(key), path + key, resolve)
        })
        is JsonArray -> JsonArray(value.mapIndexed { index, child ->
            rewriteChangedAssets(child, (previous as? JsonArray)?.getOrNull(index), path, resolve, resource)
        })
        is JsonPrimitive -> if (value.isString && value.content.isNotBlank() &&
            (resource || PrefabPackages.isAssetField(path.last()) || path.dropLast(1).lastOrNull() == "stateClipMap"))
            JsonPrimitive(resolve(value.content)) else value
    }
}
