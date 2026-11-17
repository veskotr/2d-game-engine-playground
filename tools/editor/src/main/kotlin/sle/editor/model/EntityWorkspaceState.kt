package sle.editor.model

import sle.editor.ui.controls.NumberEdit
import sle.editor.ui.controls.NumberOptions
import sle.editor.bridge.NativeEngine
import sle.editor.files.PrefabPackages
import sle.editor.project.OpenedProjectDocument
import sle.editor.project.ProjectDocumentKind

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.serialization.json.*
import java.nio.file.Files
import java.nio.file.Path

internal class EntityWorkspaceState(val catalog: Map<String, ComponentDefinition>) {
    val inspectorSections = sle.editor.ui.inspector.InspectorSectionState()
    var projectDocument by mutableStateOf<OpenedProjectDocument?>(null)
        private set
    // Installed by the project UI; standalone actions retain their existing behavior.
    var projectFileAction: ((Boolean,Boolean)->Boolean)? = null
    fun openProject(opened: OpenedProjectDocument) {
        require(opened.kind==ProjectDocumentKind.Prefab) { "Select a project prefab" }
        openResolved(opened.source,opened.assets,opened.path)
        projectDocument=opened
    }
    fun openResolved(source: String,imported: Map<Path,String>,path: Path) {
        val loaded=PrefabDocument.parse(source)
        resetTo(loaded.entities.first { it.key==loaded.root },imported)
        subtree=loaded
        file=path;message="Opened ${path.fileName}"
    }
    fun savedProject(opened: OpenedProjectDocument,source: String=serializePrefab(),imported: Map<Path,String> = assets) {
        savedResolved(opened.path,source,imported);projectDocument=opened
    }
    fun savedResolved(path: Path,source: String=serializePrefab(),imported: Map<Path,String> = assets) {
        val baseline=PrefabDocument.parse(source).let { prefab -> prefab.entities.first { it.key==prefab.root } }
        file=path;projectDocument=null;savedDocument=baseline;savedAssets=imported
        error=null;message="Saved ${path.fileName}"
    }
    private var subtree: PrefabDocument? = null
    fun prefabDocument() = subtree?.let { tree -> tree.copy(entities=tree.entities.map { if(it.key==tree.root) document else it }) }
        ?: PrefabDocument(document.key,listOf(document))
    fun serializePrefab() = prefabDocument().serialize()
    private var target by mutableStateOf("Entity")
    var selectedTarget: String
        get() = target
        set(value) { if(target!=value) endGesture();target=value }
    private var sceneShape by mutableStateOf("Entity")
    /** Shape resized in the Scene view (Inspector "Edit"); separate from the component editor's tool. */
    var sceneTarget: String
        get() = sceneShape
        set(value) { if(sceneShape!=value) endGesture();sceneShape=value }
    private var componentSelection by mutableStateOf<String?>(null)
    var selectedComponent: String?
        get() = componentSelection
        set(value) { if(componentSelection!=value) endGesture();componentSelection=value }
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
    private var gestureToken: Any? = null
    // Invalidates delayed inspector actions at selection/tab/playback boundaries, even without a gesture.
    var editContextVersion = 0L
        private set
    private var numberEditEpoch = 0L
    private val numberEdits = mutableMapOf<Pair<String, NumberOptions>, NumberEdit>()
    val gestureActive get() = gesture != null
    val canUndo get() = past.isNotEmpty()
    val canRedo get() = future.isNotEmpty()
    private fun snapshot() = Snapshot(document, assets, drafts, fieldErrors)
    private fun restore(value: Snapshot) {
        document = value.document; assets = value.assets; drafts = value.drafts; fieldErrors = value.errors; error = null
    }
    private fun change(action: () -> Unit) {
        if (numberEdits.values.any { it.active }) endGesture()
        val before = snapshot()
        action()
        if (gesture == null && before != snapshot()) {
            past = (past + before).takeLast(100)
            future = emptyList()
        }
    }
    fun beginGesture() { if (gesture == null) { gesture = snapshot(); gestureToken = Any() } }
    fun endGesture() {
        editContextVersion++
        val before = gesture ?: return
        gesture = null; gestureToken = null
        if (before != snapshot()) { past = (past + before).takeLast(100); future = emptyList() }
    }
    fun cancelGesture() { editContextVersion++;gesture?.let(::restore); gesture = null; gestureToken = null }
    fun undo() {
        if (gesture != null) { cancelGesture(); return }
        if (past.isEmpty()) return
        future = future + snapshot(); val previous = past.last(); past = past.dropLast(1); restore(previous)
    }
    fun redo() {
        if (gesture != null || future.isEmpty()) return
        past = (past + snapshot()).takeLast(100); val next = future.last(); future = future.dropLast(1); restore(next)
    }
    private fun resetHistory() { editContextVersion++;gesture = null; gestureToken = null; numberEditEpoch++; numberEdits.clear(); past = emptyList(); future = emptyList(); selectedTarget = "Entity"; selectedComponent = null }
    fun rename(name: String) = change { document = document.copy(name = name); error = null }
    fun applyGizmo(value: EntityDocument) = change { applyStructure(value) }

    // Structural scene operations own their undo record; refresh only changed field drafts.
    fun applyStructure(value: EntityDocument) {
        if (numberEdits.values.any { it.active }) endGesture()
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
        subtree=null;document=value; savedDocument=value; assets=imported; savedAssets=imported
        file=null; projectDocument=null; clearDrafts(); resetHistory()
    }

    fun newDocument() {
        subtree=null
        document = freshDocument()
        savedDocument = document
        file = null
        projectDocument = null
        assets = emptyMap()
        savedAssets = emptyMap()
        clearDrafts()
        resetHistory()
    }

    fun open(path: Path) {
        val opened = if (path.toString().endsWith(".sleprefab", true)) PrefabPackages.openTree(path) else null
        val source = opened?.first?.serialize() ?: Files.readString(path)
        val tree=PrefabDocument.parse(canonicalPrefabDocument(source))
        val loaded=tree.entities.first { it.key==tree.root }
        subtree=tree
        document = loaded
        savedDocument = loaded
        file = path
        projectDocument = null
        assets = opened?.second ?: emptyMap()
        savedAssets = assets
        clearDrafts()
        resetHistory()
        message = "Opened ${path.fileName}"
    }

    fun save(path: Path) {
        require(fieldErrors.isEmpty()) { "Correct the highlighted fields before saving" }
        require(document.key.isNotBlank()) { "Entity identity is missing" }
        if(prefabDocument().entities.size>1) {
            if(path.toString().endsWith(".sleprefab",true)) PrefabPackages.saveTree(prefabDocument(),assets,path) { canonicalPrefabDocument(it) }
            else PrefabPackages.exportTreeJson(prefabDocument(),assets,path) { canonicalPrefabDocument(it) }.let { opened ->
                subtree=opened.first;document=opened.first.entities.first { it.key==opened.first.root };assets=opened.second
            }
        } else if (path.toString().endsWith(".sleprefab", true)) PrefabPackages.save(document, assets, path, ::validateEntityDocument)
        else PrefabPackages.exportJson(document, assets, path, ::validateEntityDocument).let {
            document = it.document
            assets = it.assets
            drafts = emptyMap()
        }
        savedDocument = document
        savedAssets = assets
        file = path
        projectDocument = null
        message = "Saved $path"
        error = null
    }

    fun importAsset(path: Path): Path {
        val imported = PrefabPackages.importFile(path)
        change { assets = assets + (imported to path.fileName.toString()); error = null }
        return imported
    }

    fun removeAsset(path: Path) = change { assets = assets - path }

    /** Repoints asset fields and listed assets at relocated copies (e.g. after adoption into a
     * project on save). Not an undoable edit: the content is identical, only its location moved. */
    fun relocateAssets(moved: Map<Path, Path>) {
        if (moved.isEmpty()) return
        fun target(path: Path) = moved[path.toAbsolutePath().normalize()]
        subtree=subtree?.let { tree -> tree.copy(entities=tree.entities.map { it.copy(components=relocateAssetFields(it.components,moved)) }) }
        document = document.copy(components = relocateAssetFields(document.components, moved))
        savedDocument = savedDocument?.let { it.copy(components = relocateAssetFields(it.components, moved)) }
        assets = assets.entries.associate { (path, name) -> (target(path) ?: path) to name }
        savedAssets = savedAssets.entries.associate { (path, name) -> (target(path) ?: path) to name }
    }

    fun addComponent(type: String) = change {
        document = document.addComponent(type, editorComponentDefaults(type, catalog.getValue(type).defaults))
        error = null
    }

    fun removeComponent(type: String) {
        endGesture()
        numberEditEpoch++; numberEdits.clear()
        change {
            if (selectedComponent == type) selectedComponent = null
            if (selectedTarget == type) selectedTarget = "Entity"
            document = document.removeComponent(type)
            drafts = drafts.filterKeys { !it.startsWith("$type/") }
            fieldErrors = fieldErrors.filterNot { it.startsWith("$type/") }.toSet()
            error = null
        }
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

    /** Cached on the document owner, not remember() in a dock presenter. */
    fun numberEdit(component: String, path: List<String>, options: NumberOptions = NumberOptions()): NumberEdit {
        val fieldPath = path.toList()
        val id = component + "/" + fieldPath.joinToString("/")
        fun value(): JsonElement? {
            var result: JsonElement? = document.components[component]
            for (part in fieldPath) result = (result as? JsonObject)?.get(part)
            return result
        }
        return numberEdits.getOrPut(id to options) {
            val epoch = numberEditEpoch
            var expected: JsonElement? = null
            var entityKey = ""
            NumberEdit(options,
                read = { drafts[id] ?: value()?.toString().orEmpty() },
                start = {
                    if (epoch != numberEditEpoch || value() == null) null else {
                        endGesture(); beginGesture(); expected = value(); entityKey = document.key
                        gestureToken
                    }
                },
                current = { it === gestureToken && document.key == entityKey && value() == expected },
                preview = { text, parsed ->
                    drafts = drafts + (id to text)
                    if (parsed == null) fieldErrors = fieldErrors + id else {
                        document = document.setValue(component, fieldPath, parsed)
                        expected = parsed
                        fieldErrors = fieldErrors - id
                    }
                    error = null
                },
                finish = ::endGesture, revert = ::cancelGesture,
            )
        }
    }

    private fun clearDrafts() {
        drafts = emptyMap()
        fieldErrors = emptySet()
        message = null
        error = null
    }
}
