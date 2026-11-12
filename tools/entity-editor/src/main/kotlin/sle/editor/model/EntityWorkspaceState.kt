package sle.editor.model

import sle.editor.bridge.NativeEngine
import sle.editor.files.PrefabPackages

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.serialization.json.*
import java.nio.file.Files
import java.nio.file.Path

internal class EntityWorkspaceState(val catalog: Map<String, ComponentDefinition>) {
    var selectedTarget by mutableStateOf("Entity")
    var selectedComponent by mutableStateOf<String?>(null)
    var snap by mutableStateOf(false)
    var document by mutableStateOf(freshDocument())
    private var savedDocument by mutableStateOf<EntityDocument?>(document)
    var assets by mutableStateOf<Map<Path, String>>(emptyMap())
        private set
    private var savedAssets by mutableStateOf<Map<Path, String>>(emptyMap())
    var file by mutableStateOf<Path?>(null)
        private set
    var drafts by mutableStateOf<Map<String, String>>(emptyMap())
        private set
    var fieldErrors by mutableStateOf<Set<String>>(emptySet())
        private set
    var message by mutableStateOf<String?>(null)
    var error by mutableStateOf<String?>(null)
    val dirty get() = document != savedDocument || assets != savedAssets || fieldErrors.isNotEmpty()

    private data class Snapshot(val document: EntityDocument, val assets: Map<Path, String>,
        val drafts: Map<String, String>, val errors: Set<String>)
    private var past by mutableStateOf<List<Snapshot>>(emptyList())
    private var future by mutableStateOf<List<Snapshot>>(emptyList())
    private var gesture: Snapshot? = null
    val gestureActive get() = gesture != null
    val canUndo get() = past.isNotEmpty()
    val canRedo get() = future.isNotEmpty()
    private fun snapshot() = Snapshot(document, assets, drafts, fieldErrors)
    private fun restore(value: Snapshot) {
        document = value.document; assets = value.assets; drafts = value.drafts; fieldErrors = value.errors; error = null
    }
    private fun change(action: () -> Unit) {
        val before = snapshot()
        action()
        if (gesture == null && before != snapshot()) {
            past = (past + before).takeLast(100)
            future = emptyList()
        }
    }
    fun beginGesture() { if (gesture == null) gesture = snapshot() }
    fun endGesture() {
        val before = gesture ?: return
        gesture = null
        if (before != snapshot()) { past = (past + before).takeLast(100); future = emptyList() }
    }
    fun cancelGesture() { gesture?.let(::restore); gesture = null }
    fun undo() {
        if (gesture != null) { cancelGesture(); return }
        if (past.isEmpty()) return
        future = future + snapshot(); val previous = past.last(); past = past.dropLast(1); restore(previous)
    }
    fun redo() {
        if (gesture != null || future.isEmpty()) return
        past = (past + snapshot()).takeLast(100); val next = future.last(); future = future.dropLast(1); restore(next)
    }
    private fun resetHistory() { gesture = null; past = emptyList(); future = emptyList(); selectedTarget = "Entity"; selectedComponent = null }
    fun rename(name: String) = change { document = document.copy(name = name); error = null }
    fun applyGizmo(value: EntityDocument) = change { applyStructure(value) }

    // Structural scene operations own their undo record; refresh only changed field drafts.
    fun applyStructure(value: EntityDocument) {
        val changed = mutableListOf<String>()
        fun compare(before: JsonElement?, after: JsonElement?, path: String) {
            if (before == after) return
            if (before is JsonObject && after is JsonObject) {
                (before.keys + after.keys).forEach { compare(before[it], after[it], "$path/$it") }
            } else changed += path
        }
        (document.components.keys + value.components.keys).forEach {
            compare(document.components[it], value.components[it], it)
        }
        fun affected(id: String) = changed.any { id == it || id.startsWith("$it/") || it.startsWith("$id/") }
        drafts = drafts.filterKeys { !affected(it) }
        fieldErrors = fieldErrors.filterNot(::affected).toSet()
        document = value
        error = null
    }

    private fun freshDocument() = catalog["Transform"]?.let { EntityDocument().addComponent("Transform", it.defaults) } ?: EntityDocument()

    fun resetTo(value: EntityDocument, imported: Map<Path, String> = emptyMap()) {
        document=value; savedDocument=value; assets=imported; savedAssets=imported
        file=null; clearDrafts(); resetHistory()
    }

    fun newDocument() {
        document = freshDocument()
        savedDocument = document
        file = null
        assets = emptyMap()
        savedAssets = emptyMap()
        clearDrafts()
        resetHistory()
    }

    fun open(path: Path) {
        val opened = if (path.toString().endsWith(".sleprefab", true)) PrefabPackages.open(path) else null
        val source = opened?.document?.serialize() ?: Files.readString(path)
        val loaded = EntityDocument.parse(NativeEngine.canonicalPrefab(source.toByteArray(Charsets.UTF_8)).toString(Charsets.UTF_8))
        document = loaded
        savedDocument = loaded
        file = path
        assets = opened?.assets ?: emptyMap()
        savedAssets = assets
        clearDrafts()
        resetHistory()
        message = "Opened ${path.fileName}"
    }

    fun save(path: Path) {
        require(fieldErrors.isEmpty()) { "Correct the highlighted fields before saving" }
        require(document.key.isNotBlank()) { "Entity identity is missing" }
        if (path.toString().endsWith(".sleprefab", true)) PrefabPackages.save(document, assets, path, ::validateEntityDocument)
        else PrefabPackages.exportJson(document, assets, path, ::validateEntityDocument).let {
            document = it.document
            assets = it.assets
            drafts = emptyMap()
        }
        savedDocument = document
        savedAssets = assets
        file = path
        message = "Saved $path"
        error = null
    }

    fun importAsset(path: Path): Path {
        val imported = PrefabPackages.importFile(path)
        change { assets = assets + (imported to path.fileName.toString()); error = null }
        return imported
    }

    fun removeAsset(path: Path) = change { assets = assets - path }

    fun addComponent(type: String) = change {
        document = document.addComponent(type, catalog.getValue(type).defaults)
        error = null
    }

    fun removeComponent(type: String) = change {
        if (selectedComponent == type) selectedComponent = null
        if (selectedTarget == type) selectedTarget = "Entity"
        document = document.removeComponent(type)
        drafts = drafts.filterKeys { !it.startsWith("$type/") }
        fieldErrors = fieldErrors.filterNot { it.startsWith("$type/") }.toSet()
        error = null
    }

    fun editField(component: String, path: List<String>, template: JsonElement, text: String) = change {
        val id = component + "/" + path.joinToString("/")
        drafts = drafts + (id to text)
        try {
            val value = if (template is JsonPrimitive && template.isString) JsonPrimitive(text) else Json.parseToJsonElement(text)
            when (template) {
                is JsonObject -> require(value is JsonObject) { "Expected object" }
                is JsonArray -> require(value is JsonArray) { "Expected array" }
                is JsonPrimitive -> if (!template.isString) {
                    require(value is JsonPrimitive && !value.isString && value.doubleOrNull?.isFinite() == true) { "Expected finite number" }
                }
            }
            document = document.setValue(component, path, value)
            fieldErrors = fieldErrors - id
        } catch (_: Exception) { fieldErrors = fieldErrors + id }
        error = null
    }

    fun setValue(component: String, path: List<String>, value: JsonElement) = change {
        document = document.setValue(component, path, value)
        val id = component + "/" + path.joinToString("/")
        drafts = drafts - id
        fieldErrors = fieldErrors - id
        error = null
    }

    private fun clearDrafts() {
        drafts = emptyMap()
        fieldErrors = emptySet()
        message = null
        error = null
    }
}
