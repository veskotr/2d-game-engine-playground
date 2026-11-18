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

internal class EntityWorkspaceState(val catalog: Map<String, ComponentDefinition>) : UndoableDocument {
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
    private val ownHistory = History()
    private var owner: EntityHistoryOwner? = null
    /** The timeline these edits join: the owning scene's, or this prefab workspace's own. */
    override val history: History get() = owner?.history ?: ownHistory
    /** The document whose Undo/Redo applies here: the owning scene, or this prefab workspace. */
    val timeline: UndoableDocument get() = owner ?: this
    /** Called by the owning scene for every entity it holds; edits then post to the scene's timeline. */
    internal fun attachHistory(value: EntityHistoryOwner) { owner = value }
    private var gesture: Snapshot? = null
    private var gestureLabel: String? = null
    private var gestureToken: Any? = null
    // Invalidates delayed inspector actions at selection/tab/playback boundaries, even without a gesture.
    var editContextVersion = 0L
        private set
    private var numberEditEpoch = 0L
    private val numberEdits = mutableMapOf<Pair<String, NumberOptions>, NumberEdit>()
    override val gestureActive get() = gesture != null
    /** The active document's timeline (the scene's for scene entities). */
    val canUndo get() = history.canUndo
    val canRedo get() = history.canRedo
    private fun snapshot() = Snapshot(document, assets, drafts, fieldErrors)
    private fun restore(value: Snapshot) {
        document = value.document; assets = value.assets; drafts = value.drafts; fieldErrors = value.errors; error = null
    }
    /** Posts one entity edit. A scene entry resolves its entity by key when applied, so it still
     * works after the entity was deleted and restored; applying it selects that entity. */
    private fun post(before: Snapshot, label: String?, mergeKey: Any? = null) {
        val after = snapshot()
        if (before == after) return
        val key = document.key
        val scene = owner
        // A stale presenter editing a deleted entity changes nothing in the document.
        if (scene != null && scene.entity(key) !== this) return
        // Linear history means the entity is back by the time its edit is undone; if not, skip.
        fun target() = if (scene == null) this else scene.entity(key)
        val selection = scene?.historySelection(key)
        history.record(History.Entry(label ?: describeEntityEdit(before.document, after.document), mergeKey, selection, selection,
            undo = { target()?.restore(before) }, redo = { target()?.restore(after) }))
    }
    private fun change(label: String? = null, mergeKey: Any? = null, action: () -> Unit) {
        if (numberEdits.values.any { it.active }) endGesture()
        val before = snapshot()
        action()
        if (gesture == null) post(before, label, mergeKey)
    }
    /** [label] names the step when the edit has no obvious one (the default derives it from the change). */
    fun beginGesture(label: String? = null) { if (gesture == null) { gesture = snapshot(); gestureLabel = label; gestureToken = Any() } }
    fun endGesture() {
        editContextVersion++
        val before = gesture ?: return
        val label = gestureLabel
        gesture = null; gestureLabel = null; gestureToken = null
        post(before, label)
    }
    fun cancelGesture() { editContextVersion++;gesture?.let(::restore); gesture = null; gestureLabel = null; gestureToken = null }
    /** Undo during a drag or numeric edit cancels that edit first. Scene entities use the scene's timeline. */
    override fun undo() {
        owner?.let { it.undo(); return }
        if (gesture != null) { cancelGesture(); return }
        history.undo()
    }
    override fun redo() {
        owner?.let { it.redo(); return }
        if (gesture != null) { cancelGesture(); return }
        history.redo()
    }
    // Scene entities share the scene's timeline, which the scene clears itself.
    private fun resetHistory() { editContextVersion++;gesture = null; gestureLabel = null; gestureToken = null; numberEditEpoch++; numberEdits.clear(); ownHistory.clear(); selectedTarget = "Entity"; selectedComponent = null }
    fun rename(name: String) = change("Rename ${historyName(document)}") { document = document.copy(name = name); error = null }
    /** Viewport edits; equal [mergeKey]s in quick succession (arrow nudges) merge into one step. */
    fun applyGizmo(value: EntityDocument, mergeKey: Any? = null) = change(mergeKey = mergeKey) { applyStructure(value) }

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
        change("Import ${path.fileName}") { assets = assets + (imported to path.fileName.toString()); error = null }
        return imported
    }

    fun removeAsset(path: Path) = change("Remove ${path.fileName}") { assets = assets - path }

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

    fun addComponent(type: String) = change("Add $type") {
        document = document.addComponent(type, editorComponentDefaults(type, catalog.getValue(type).defaults))
        error = null
    }

    fun removeComponent(type: String) {
        endGesture()
        numberEditEpoch++; numberEdits.clear()
        change("Remove $type") {
            if (selectedComponent == type) selectedComponent = null
            if (selectedTarget == type) selectedTarget = "Entity"
            document = document.removeComponent(type)
            drafts = drafts.filterKeys { !it.startsWith("$type/") }
            fieldErrors = fieldErrors.filterNot { it.startsWith("$type/") }.toSet()
            error = null
        }
    }

    fun editField(component: String, path: List<String>, template: JsonElement, text: String) = change(fieldLabel(component, path)) {
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

    fun setValue(component: String, path: List<String>, value: JsonElement) = change(fieldLabel(component, path)) {
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
                        endGesture(); beginGesture(fieldLabel(component, fieldPath)); expected = value(); entityKey = document.key
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

    private fun fieldLabel(component: String, path: List<String>) =
        if (path.isEmpty()) "Edit $component" else "Edit $component ${path.joinToString(".")}"

    private fun clearDrafts() {
        drafts = emptyMap()
        fieldErrors = emptySet()
        message = null
        error = null
    }
}
