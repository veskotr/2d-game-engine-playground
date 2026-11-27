package sle.editor.model

import sle.editor.bridge.NativeEngine
import sle.editor.files.PrefabPackages
import sle.editor.project.OpenedProjectDocument
import sle.editor.project.ProjectDocumentKind
import sle.editor.viewport.Vec2

import androidx.compose.runtime.*
import kotlinx.serialization.json.*
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID

internal fun canonicalSceneDocument(source: String): String =
    NativeEngine.canonicalScene(source.toByteArray(Charsets.UTF_8)).toString(Charsets.UTF_8)

/** Shown when applying a linked prefab rebuilt the scene and its undo history had to go. */
internal const val HISTORY_CLEARED_NOTICE="Undo history cleared after entity template update"

internal class SceneWorkspaceState(private val catalog: Map<String,ComponentDefinition>,
    private val canonicalize: (String) -> String = ::canonicalSceneDocument) : EntityHistoryOwner {
    var projectDocument by mutableStateOf<OpenedProjectDocument?>(null)
        private set
    var projectImportPrefab: ((Path,Vec2?)->Unit)? = null
    /** Project-only: Apply Changes to Prefab for the instance owning an entity key. */
    var projectApplyPrefab: ((String)->Unit)? = null
    /** Project-only: save an entity subtree as a prefab in an Assets folder and link it. */
    var projectCreatePrefab: ((String,Path)->Unit)? = null
    var projectFileAction: ((Boolean,Boolean)->Boolean)? = null
    /** Editor-only: called after a deletion was recorded, with what it removed ("Player", "3 entities"). */
    var onDeleted: ((String)->Unit)? = null
    fun openProject(opened: OpenedProjectDocument) {
        require(opened.kind==ProjectDocumentKind.Scene) { "Select a project scene" }
        val document = SceneDocument.parse(opened.source).copy(prefabInstances = opened.prefabInstances)
        loadDocument(document, opened.assets, opened.path)
        projectDocument=opened
    }
    fun openResolved(source: String,imported: Map<Path,String>,path: Path) {
        loadDocument(SceneDocument.parse(source),imported,path)
        projectDocument=null
    }
    fun savedProject(opened: OpenedProjectDocument,source: String=serialize(),imported: Map<Path,String> = allAssets()) {
        file=opened.path;projectDocument=opened
        prefabInstances=prefabInstances.map { live -> opened.prefabInstances.firstOrNull { it.key==live.key && it.assetId==live.assetId } ?: live }
        saved=Saved(SceneDocument.parse(source).copy(prefabInstances=opened.prefabInstances),imported)
        error=null;message="Saved ${opened.path.fileName}"
    }
    fun savedResolved(path: Path,source: String=serialize(),imported: Map<Path,String> = allAssets()) {
        val baseline=Saved(SceneDocument.parse(source).copy(prefabInstances=prefabInstances),imported)
        file=path;projectDocument=null;saved=baseline;error=null;message="Saved ${path.fileName}"
    }
    var key by mutableStateOf(UUID.randomUUID().toString())
        private set
    var name by mutableStateOf("New Scene")
        private set
    private var entityStates by mutableStateOf<List<EntityWorkspaceState>>(emptyList())
    /** Every entity held here posts its edits to this scene's [history]. */
    var entities: List<EntityWorkspaceState>
        get()=entityStates
        private set(value) { value.forEach { it.attachHistory(this) };entityStates=value;selection.prune(value.map { it.document.key }.toSet()) }
    var parents by mutableStateOf<Map<String,String>>(emptyMap())
        private set
    /** Ordered multi-selection plus the active entity. Selection changes are never undo steps. */
    val selection=SceneSelection()
    /** The active selected entity's key (the one the Inspector edits and the viewport gives handles). */
    val selectedKey: String? get()=selection.active
    var file by mutableStateOf<Path?>(null)
        private set
    var error by mutableStateOf<String?>(null)
    var message by mutableStateOf<String?>(null)
    private var tileLayers by mutableStateOf<JsonArray?>(null)
    private val tileLayerList by derivedStateOf { tileLayers.orEmpty().map { TileLayerDocument(it.jsonObject) } }
    private var prefabInstances by mutableStateOf<List<PrefabInstance>>(emptyList())
    private var extras by mutableStateOf<Map<Path,String>>(emptyMap())
    private data class Saved(val document: SceneDocument,val assets: Map<Path,String>)
    private fun snapshot() = Saved(document(),allAssets())
    private var saved by mutableStateOf(snapshot())
    private data class Structure(val entities: List<EntityWorkspaceState>,val name: String,val parents: Map<String,String>,val poses: Map<String,JsonElement?>,val cameraFlags: Map<String,JsonElement?>,val poseChanges: Set<String> = emptySet(),val cameraChanges: Set<String> = emptySet(),val links: List<PrefabInstance> = emptyList(),val inlineLayers: JsonArray? = null)
    /** One timeline for structure and every entity's component edits, in the order they happened. */
    override val history=History().apply {
        restoreSelection={ recorded -> selection.restore(recorded,entities.map { it.document.key }.toSet()) }
    }
    var cancelTileGesture by mutableStateOf<(() -> Unit)?>(null)
    override val gestureActive get()=cancelTileGesture != null || entities.any { it.gestureActive }
    override fun entity(key: String)=entities.firstOrNull { it.document.key==key }
    override fun historySelection(key: String)=if(key in selection) selection.snapshot() else HistorySelection(key)
    /** The active selected entity; see [selection] for the whole set. */
    val selected get() = entities.firstOrNull { it.document.key==selectedKey }
    /** Selected entities in scene order. */
    val selectedEntities get() = entities.filter { it.document.key in selection.keys }
    /** Selected keys whose ancestors are not selected, in scene order: the subtrees a set
     * operation (move, delete, duplicate) acts on, so no entity is handled twice. */
    fun selectionRoots(): List<String> {
        val chosen=selection.keys.toSet()
        fun ancestorSelected(key: String): Boolean {
            var parent=parents[key];val seen=mutableSetOf<String>()
            while(parent!=null && seen.add(parent)) { if(parent in chosen) return true;parent=parents[parent] }
            return false
        }
        return entities.map { it.document.key }.filter { it in chosen && !ancestorSelected(it) }
    }
    val dirty get() = snapshot()!=saved || entities.any { it.fieldErrors.isNotEmpty() } || referencedTilemaps().any { it.dirty }
    val canUndo get()=history.canUndo
    val canRedo get()=history.canRedo
    fun document() = SceneDocument(key,name,entities.map { it.document },parents,prefabInstances,tileLayers)
    fun serialize() = document().serialize()

    private val mapDrafts = mutableStateMapOf<Path, TilemapDraft>()
    private fun mapDraft(document: EntityDocument): TilemapDraft? {
        val asset = (document.components["Tilemap"] as? JsonObject)?.get("asset")?.jsonPrimitive?.content ?: return null
        if (asset.isBlank() || asset.startsWith("project-asset://")) return null
        val path = Path.of(asset).toAbsolutePath().normalize()
        return mapDrafts[path] ?: runCatching { TilemapDraft.open(path) }.getOrNull()?.also { mapDrafts[path] = it }
    }
    fun activeTilemap() = selected?.document?.let(::mapDraft)
    fun referencedTilemaps() = entities.mapNotNull { mapDraft(it.document) }.distinct()
    fun saveTilemaps() {
        val pending = referencedTilemaps().filter { it.dirty }
        pending.forEach { it.checkSource() }
        pending.forEach { it.save() }
    }
    fun previewSerialize(): String {
        val preview = document().copy(entities = entities.map { state ->
            val source = state.document
            val draft = mapDraft(source)
            if (draft == null) source else source.copy(components = JsonObject(source.components +
                ("Tilemap" to JsonObject(source.components.getValue("Tilemap").jsonObject +
                    ("asset" to JsonPrimitive(draft.previewPath().toString().replace('\\', '/')))))))
        })
        return preview.serialize()
    }
    private fun editableTileLayers(): JsonArray? {
        val draft = activeTilemap() ?: return if (selected?.document?.components?.containsKey("Tilemap") == true) JsonArray(emptyList()) else tileLayers
        val translation = selectedKey?.let { sceneWorldPose(it, entities.map { e -> e.document }, parents).position } ?: Vec2(0.0, 0.0)
        return draft.document.projectedLayers(draft.path.toString(), translation)
    }
    fun tileLayerDocuments() = if (selected?.document?.components?.containsKey("Tilemap") == true)
        editableTileLayers().orEmpty().map { TileLayerDocument(it.jsonObject) } else tileLayerList
    fun tileLayer(id: String) = tileLayerDocuments().firstOrNull { it.id == id }

    private data class TileWorkspaceView(val layer: String?, val selections: Map<String, TileBounds>)
    private val tileViews = linkedMapOf<Pair<Path, String>, TileWorkspaceView>()
    var activeTileLayer by mutableStateOf<String?>(null)
    private var tileSelections by mutableStateOf<Map<String, TileBounds>>(emptyMap())

    private fun rememberTileView() {
        val path = file?.toAbsolutePath()?.normalize() ?: return
        tileViews[path to key] = TileWorkspaceView(activeTileLayer, tileSelections)
        if (tileViews.size > 32) tileViews.remove(tileViews.keys.first())
    }

    private data class ParkedScene(
        val baseline: Saved,
        val entities: List<EntityWorkspaceState>,
        val selection: HistorySelection,
        val timeline: History.Timeline,
    )
    private val parkedScenes = linkedMapOf<Path, ParkedScene>()

    private fun parkScene(next: Path?) {
        val path = file?.toAbsolutePath()?.normalize() ?: return
        parkedScenes.remove(path)
        if (path == next || dirty || (!history.canUndo && !history.canRedo)) return
        parkedScenes[path] = ParkedScene(saved, entities, selection.snapshot(), history.snapshotTimeline())
        if (parkedScenes.size > 8) parkedScenes.remove(parkedScenes.keys.first())
    }

    fun tileSelection(layer: String) = tileSelections[layer]

    fun selectTiles(layer: String, bounds: TileBounds?) {
        tileSelections = if (bounds == null) tileSelections - layer else tileSelections + (layer to bounds)
    }

    fun setTileLayerVisible(id: String, visible: Boolean) =
        setTileLayerFlag(id, "visible", visible, if (visible) "Show Tile Layer" else "Hide Tile Layer")

    fun setTileLayerCollision(id: String, enabled: Boolean) =
        setTileLayerFlag(id, "collisionEnabled", enabled, if (enabled) "Enable Tile Collision" else "Disable Tile Collision")

    private fun setTileLayerFlag(id: String, flag: String, enabled: Boolean, label: String) {
        val layer = tileLayer(id) ?: return
        if ((layer.source[flag]?.jsonPrimitive?.boolean ?: true) == enabled) return
        cancelTileGesture?.invoke()
        changeTileLayers(label, replacingTileLayer(id, JsonObject(layer.source + (flag to JsonPrimitive(enabled)))))
    }

    fun pasteTiles(id: String, palette: TilePalette, pattern: TilePattern, anchor: TileCell): Boolean {
        val layer = tileLayer(id)?.takeUnless { it.locked || !it.visible } ?: return false
        require(layer.tileset == pattern.tileset && palette.reference == pattern.tileset) { "Clipboard uses a different tileset" }
        val tileIds = palette.tiles.mapTo(hashSetOf()) { it.id }
        require(pattern.tiles.values.all { it in tileIds }) { "Clipboard contains a missing tile" }
        return paintTiles(id, pattern.placed(anchor), "Paste Tiles")
    }

    fun moveTiles(id: String, bounds: TileBounds, anchor: TileCell): Boolean {
        val layer = tileLayer(id)?.takeUnless { it.locked || !it.visible } ?: return false
        if (anchor == bounds.minimum) return false
        val destination = requireNotNull(bounds.at(anchor)) { "Selection exceeds the tile grid" }
        val copy = layer.copy(bounds)
        val delta = layer.cells.keys.filter { it in bounds }.associateWith<TileCell, String?> { null } + copy.placed(anchor)
        return recordTilePaint(id, delta, "Move Tiles", bounds to destination)
    }

    private fun recordMapChange(map: TilemapDraft, before: TilemapDocument, after: TilemapDocument, label: String,
        undoView: () -> Unit = {}, redoView: () -> Unit = {}) {
        if (before == after) return
        map.document = after
        history.record(History.Entry(label,
            undo = {
                require(map.document == after) { "This shared map changed in another scene; undo its newer edits first" }
                map.document = before; undoView()
            },
            redo = {
                require(map.document == before) { "This shared map changed in another scene; redo would overwrite newer edits" }
                map.document = after; redoView()
            }))
    }

    fun reloadActiveTilemap() {
        val map = activeTilemap() ?: return
        cancelTileGesture?.invoke()
        val before = map.document
        map.reload()
        recordMapChange(map, before, map.document, "Reload Tilemap")
    }

    fun addMapTileset(reference: String) {
        val map = activeTilemap() ?: return
        val before = map.document
        val after = before.addTileset(reference)
        if (before == after) return
        recordMapChange(map, before, after, "Add Tileset to Map")
    }

    fun addTileLayer(tileset: String): String {
        val map = activeTilemap()
        if (map != null) {
            val before = map.document
            val afterSet = before.addTileset(tileset)
            val existing = afterSet.projectedLayers(map.path.toString())
            val layerId = UUID.randomUUID().toString()
            val order = (existing.maxOfOrNull { it.jsonObject.getValue("drawOrder").jsonPrimitive.int } ?: -1).toLong() + 1
            require(order <= Int.MAX_VALUE) { "No draw order available" }
            val newLayer = buildJsonObject {
                put("id", layerId); put("name", "Tile Layer ${existing.size + 1}"); put("drawOrder", order)
                put("cells", JsonArray(emptyList()))
            }
            val after = afterSet.withProjectedLayers(JsonArray(existing + newLayer))
            recordMapChange(map, before, after, "Add Tile Layer")
            return layerId
        }
        val layers = tileLayerDocuments()
        val id = UUID.randomUUID().toString()
        val usedOrders = layers.map { it.drawOrder }.toSet()
        val top = layers.maxOfOrNull { it.drawOrder } ?: -1
        val order = if (top < Int.MAX_VALUE) top + 1 else (0..layers.size).first { it !in usedOrders }
        val layer = buildJsonObject {
            put("id", id)
            put("name", "Tile Layer ${layers.size + 1}")
            put("tileset", tileset)
            put("drawOrder", order)
            put("cells", JsonArray(emptyList()))
        }
        changeTileLayers("Add Tile Layer", JsonArray((tileLayers.orEmpty() + layer)
            .sortedBy { it.jsonObject.getValue("drawOrder").jsonPrimitive.int }))
        return id
    }

    fun renameTileLayer(id: String, name: String) {
        val layer = tileLayer(id) ?: return
        val value = name.trim()
        require(value.isNotEmpty() && value.encodeToByteArray().size <= 256) { "Enter a layer name of 1 to 256 bytes" }
        if (layer.name == value) return
        changeTileLayers("Rename Tile Layer", replacingTileLayer(id,
            JsonObject(layer.source + ("name" to JsonPrimitive(value)))))
    }

    fun reorderTileLayer(id: String, direction: Int) {
        val layers = tileLayerDocuments().sortedBy { it.drawOrder }
        val index = layers.indexOfFirst { it.id == id }
        if (index < 0) return
        val other = layers.getOrNull(index + direction) ?: return
        val layer = layers[index]
        val next = JsonArray(layers.map {
            val order = when (it.id) {
                layer.id -> other.drawOrder
                other.id -> layer.drawOrder
                else -> it.drawOrder
            }
            JsonObject(it.source + ("drawOrder" to JsonPrimitive(order)))
        }.sortedBy { it.getValue("drawOrder").jsonPrimitive.int })
        changeTileLayers("Reorder Tile Layers", next)
    }

    fun setTileLayerLocked(id: String, locked: Boolean) {
        val layer = tileLayer(id) ?: return
        if (layer.locked == locked) return
        cancelTileGesture?.invoke()
        changeTileLayers(if (locked) "Lock Tile Layer" else "Unlock Tile Layer",
            replacingTileLayer(id, JsonObject(layer.source + ("locked" to JsonPrimitive(locked)))))
    }

    fun paintTiles(id: String, cells: Map<TileCell, String?>, label: String): Boolean =
        recordTilePaint(id, cells, label)

    private fun recordTilePaint(id: String, cells: Map<TileCell, String?>, label: String,
        selectionChange: Pair<TileBounds, TileBounds>? = null): Boolean {
        val layer = tileLayer(id)?.takeUnless { it.locked || !it.visible } ?: return false
        val after = cells.filter { (cell, tile) -> layer.cells[cell] != tile }
        if (after.isEmpty()) return false
        val map = activeTilemap()
        if (map != null) {
            val before = map.document
            val next = before.withProjectedLayers(replacingTileLayer(id, layer.apply(after)))
            recordMapChange(map, before, next, label,
                undoView = { selectionChange?.let { selectTiles(id, it.first) } },
                redoView = { selectionChange?.let { selectTiles(id, it.second) } })
            selectionChange?.let { selectTiles(id, it.second) }
            return true
        }
        val before = after.keys.associateWith { layer.cells[it] }
        fun apply(delta: Map<TileCell, String?>) {
            tileLayers = replacingTileLayer(id, checkNotNull(tileLayer(id)).apply(delta))
        }
        val next = replacingTileLayer(id, layer.apply(after))
        canonicalize(document().copy(tileLayers = next).serialize())
        tileLayers = next
        selectionChange?.let { selectTiles(id, it.second) }
        history.record(History.Entry(label,
            undo = { apply(before); selectionChange?.let { selectTiles(id, it.first) } },
            redo = { apply(after); selectionChange?.let { selectTiles(id, it.second) } }))
        return true
    }

    private fun replacingTileLayer(id: String, layer: JsonObject) = JsonArray(editableTileLayers().orEmpty().map {
        if (it.jsonObject.getValue("id").jsonPrimitive.content == id) layer else it
    })

    private fun changeTileLayers(label: String, next: JsonArray) {
        val map = activeTilemap()
        if (map != null) {
            val before = map.document
            val after = before.withProjectedLayers(next)
            recordMapChange(map, before, after, label)
            return
        }
        val before = tileLayers
        canonicalize(document().copy(tileLayers = next).serialize())
        tileLayers = next
        history.record(History.Entry(label, undo = { tileLayers = before }, redo = { tileLayers = next }))
    }

    fun validatePropertyCandidate(owner: EntityWorkspaceState, candidate: EntityDocument) {
        require(entity(candidate.key) === owner) { "The scene entity is no longer available" }
        val next = document().copy(entities = entities.map { if (it === owner) candidate else it.document })
        linkedInstance(candidate.key)?.let { link -> link.copy(parent = next.parents[link.key]).capture(next) }
        canonicalize(next.serialize())
    }
    fun applyLuaConsoleEdits(captured: SceneDocument, edits: JsonArray,
        project: sle.editor.project.OpenedProject? = null) {
        require(document() == captured && !gestureActive) { "Scene changed while Lua was running" }
        require(edits.size <= 128) { "Too many console edits" }
        val (assetEdits, sceneEdits) = edits.partition { it.jsonObject["op"] == JsonPrimitive("createAsset") }
        val created = if (assetEdits.isEmpty()) null else sle.editor.console.ConsoleAssets(
            requireNotNull(project) { "Open a project before creating an asset" }, assetEdits.map { it.jsonObject })
        val next = sle.editor.console.applyConsoleEdits(captured, JsonArray(sceneEdits)) { type ->
            requireNotNull(catalog[type]) { "Unknown component: $type" }.defaults
        }
        if (next == captured && created == null) return
        val beforeByKey = captured.entities.associateBy { it.key }
        val afterByKey = next.entities.associateBy { it.key }
        val beforeKeys = beforeByKey.keys.toList()
        val afterKeys = afterByKey.keys.toList()
        for (entity in next.entities) {
            val animator = entity.components["Animator"] as? JsonObject
            val targets = animator?.get("targetEntities") as? JsonObject ?: continue
            require(targets.values.all { value ->
                val target = value as? JsonPrimitive ?: return@all false
                if (target.isString) target.content in afterByKey
                else target.intOrNull?.let { ordinal ->
                    val key = afterKeys.getOrNull(ordinal - 1)
                    key != null && key == beforeKeys.getOrNull(ordinal - 1)
                } == true
            }) { "Console edits would invalidate an Animator target" }
        }
        next.prefabInstances.forEach { it.copy(parent = next.parents[it.key]).capture(next) }
        canonicalize(next.serialize())

        val before = structure()
        val selectionBefore = selection.snapshot()
        val live = before.entities.associateBy { it.document.key }
        val after = before.copy(entities = next.entities.map { entity ->
            live[entity.key] ?: EntityWorkspaceState(catalog).apply { resetTo(entity, emptyMap()) }
        }, parents = next.parents)
        val modified = beforeByKey.keys.intersect(afterByKey.keys).filter { beforeByKey[it] != afterByKey[it] }
        fun publish(target: Structure, documents: Map<String, EntityDocument>) {
            modified.forEach { live.getValue(it).applyStructure(documents.getValue(it)) }
            restore(target)
        }
        created?.create()
        publish(after, afterByKey)
        history.record(History.Entry("Run Lua", undoSelection = selectionBefore, redoSelection = selection.snapshot(),
            undo = { created?.undo(); publish(before, beforeByKey) },
            redo = { created?.create(); publish(after, afterByKey) }))
    }
    fun allAssets() = extras + entities.flatMap { it.assets.entries }.associate { it.key to it.value }
    private fun structure() = Structure(entities,name,parents,entities.associate { it.document.key to it.document.components["Transform"] },entities.associate { it.document.key to (it.document.components["Camera2D"] as? JsonObject)?.get("primary") },links=prefabInstances,inlineLayers=tileLayers)
    private fun requireReferences(states: List<EntityWorkspaceState>) {
        val keys=states.map { it.document.key }.toSet()
        for(state in states) {
            val targets=(state.document.components["Animator"] as? JsonObject)?.get("targetEntities") as? JsonObject ?: continue
            require(targets.values.all { value -> value is JsonPrimitive &&
                (if(value.isString) value.content in keys else value.intOrNull?.let { ordinal ->
                    ordinal in 1..states.size && entities.getOrNull(ordinal-1)?.document?.key==states.getOrNull(ordinal-1)?.document?.key
                }==true) }) {
                "This change would invalidate an Animator target; remove its reference or save/reopen to normalize numeric targets first"
            }
        }
    }
    private fun restore(value: Structure) {
        entities=value.entities;name=value.name;parents=value.parents;prefabInstances=value.links;tileLayers=value.inlineLayers;error=null
    }
    /** One structural step. It keeps the [Structure] snapshots; only the Transform and camera
     * flags the operation itself changed are restored, so other component edits are untouched. */
    private fun change(label: String,action: () -> Unit) {
        selected?.endGesture()
        val selectionBefore=selection.snapshot()
        val before=structure();action()
        val after=structure()
        if (before!=after) {
            val common=before.entities.map { it.document.key }.toSet().intersect(after.entities.map { it.document.key }.toSet())
            val poses=common.filter { before.poses[it]!=after.poses[it] }.toSet();val cameras=common.filter { before.cameraFlags[it]!=after.cameraFlags[it] }.toSet()
            val undoTarget=before.copy(poseChanges=poses,cameraChanges=cameras);val redoTarget=after.copy(poseChanges=poses,cameraChanges=cameras)
            history.record(History.Entry(label,null,selectionBefore,selection.snapshot(),
                undo={ requireReferences(undoTarget.entities);restoreOperation(undoTarget) },
                redo={ requireReferences(redoTarget.entities);restoreOperation(redoTarget) }))
        }
        error=null
    }
    private fun named(key: String?)=entity(key ?: "")?.document?.let(::historyName) ?: "Entity"
    /** "Player" for one entity, "3 entities" for several: undo labels for set operations. */
    fun subjects(keys: Collection<String>)=if(keys.size==1) named(keys.single()) else "${keys.size} entities"
    /** Ctrl+Z: cancels an open drag or numeric edit first, otherwise undoes the latest step. */
    override fun undo() { if(!cancelOpenGesture()) history.undo() }
    override fun redo() { if(!cancelOpenGesture()) history.redo() }
    private fun cancelOpenGesture(): Boolean {
        cancelTileGesture?.let { it(); return true }
        val open=entities.firstOrNull { it.gestureActive } ?: return false
        open.cancelGesture();return true
    }
    /** See [EntityWorkspaceState.relocateAssets]; also updates scene-level assets. */
    fun relocateAssets(moved: Map<Path,Path>) {
        if(moved.isEmpty()) return
        entities.forEach { it.relocateAssets(moved) }
        extras=extras.entries.associate { (path,label) -> (moved[path.toAbsolutePath().normalize()] ?: path) to label }
    }
    fun rename(value: String) = change("Rename Scene") { name=value }
    private fun exists(key: String?)=key!=null && entities.any { it.document.key==key }
    /** Selects [value] alone; null or an unknown key clears the selection. */
    fun select(value: String?) { if(value != selectedKey) cancelTileGesture?.invoke();selected?.endGesture();selection.select(value?.takeIf(::exists)) }
    /** Ctrl-click: adds or removes [key]. */
    fun toggleSelection(key: String) { if(!exists(key)) return;selected?.endGesture();selection.toggle(key) }
    /** Shift-click: the range from the anchor to [key] in the Hierarchy's [visible] order. */
    fun extendSelection(key: String,visible: List<String>,additive: Boolean=false) {
        if(!exists(key)) return;selected?.endGesture();selection.extendTo(key,visible.filter(::exists),additive)
    }
    /** A plain press on an entity that is already selected keeps the set and makes it active. */
    fun activate(key: String) { if(key in selection && key!=selectedKey) { cancelTileGesture?.invoke();selected?.endGesture();selection.activate(key) } }
    /** Box select: [keys] (in scene order) replace the selection, or join it when [additive]. The
     * active entity stays active when it is still selected; selection is never an undo step. */
    fun selectBox(keys: List<String>,additive: Boolean) {
        val hits=keys.filter(::exists).distinct()
        if(additive && hits.isEmpty()) return
        selected?.endGesture()
        when {
            additive -> selection.set((selection.keys+hits).distinct(),selectedKey ?: hits.first())
            hits.isEmpty() -> selection.clear()
            else -> selection.set(hits,selectedKey?.takeIf { it in hits } ?: hits.first())
        }
    }
    /** Ctrl+A: every entity, keeping the active one when there is one. */
    fun selectAll() { selected?.endGesture();selection.set(entities.map { it.document.key },selectedKey ?: entities.firstOrNull()?.document?.key) }
    fun addEntity(position: Vec2?=null) = createEntity(position=position)
    /** [position] places the instance at a world point (e.g. where it was dropped); null keeps the prefab's own. */
    fun importPrefab(path: Path,position: Vec2?=null) {
        require(entities.size<MAX_SCENE_ENTITIES) { "Scene editor supports at most $MAX_SCENE_ENTITIES entities" }
        projectImportPrefab?.let { it(path,position);return }
        val imported=EntityWorkspaceState(catalog).apply { open(path) }
        insertPrefabTree(imported.prefabDocument(),imported.assets,position)
    }
    fun insertPrefabTree(prefab: PrefabDocument,imported: Map<Path,String>,position: Vec2?=null) {
        val instanceKey=UUID.randomUUID().toString()
        var expanded=prefab.instantiate(instanceKey)
        if(position!=null) expanded=expanded.copy(entities=expanded.entities.map {
            if(it.key==instanceKey) it.withPose(ScenePose.from(it).copy(position=position)) else it
        })
        require(entities.size+expanded.entities.size<=MAX_SCENE_ENTITIES) { "Scene entity limit reached" }
        val states=expanded.entities.map { entity -> EntityWorkspaceState(catalog).apply { resetTo(entity,imported) } }
        change("Create ${historyName(expanded.entities.first { it.key==instanceKey })}") { entities=entities+states;parents=parents+expanded.parents;selection.select(instanceKey) }
    }
    fun insertLinkedPrefab(prefab: PrefabDocument,assetId: String,imported: Map<Path,String>,position: Vec2?=null) {
        val instanceKey=UUID.randomUUID().toString()
        var expanded=prefab.instantiate(instanceKey)
        if(position!=null) expanded=expanded.copy(entities=expanded.entities.map {
            if(it.key==instanceKey) it.withPose(ScenePose.from(it).copy(position=position)) else it
        })
        require(entities.size+expanded.entities.size<=MAX_SCENE_ENTITIES) { "Scene entity limit reached" }
        val link=PrefabInstance(assetId,instanceKey,prefab).capture(expanded)
        val states=expanded.entities.map { entity -> EntityWorkspaceState(catalog).apply { resetTo(entity,imported) } }
        change("Create ${historyName(expanded.entities.first { it.key==instanceKey })}") { entities=entities+states;parents=parents+expanded.parents;prefabInstances=prefabInstances+link;selection.select(instanceKey) }
    }
    /** Replacement is staged and validated before any live state/history changes. Entities whose
     * document is unchanged keep their live state, including field drafts. The scene's undo
     * history is cleared, since its steps describe the scene before the prefab changed; returns
     * true (and sets [message] to [HISTORY_CLEARED_NOTICE]) when there were steps to clear. */
    fun replaceLinkedScene(next: SceneDocument): Boolean {
        next.serialize()
        val keys=next.entities.map { it.key }
        next.entities.forEach { remapPrefabReferences(it,keys,keys.associateWith { it }) }
        val live=entities.associateBy { it.document.key }
        val states=next.entities.map { entity ->
            live[entity.key]?.takeIf { it.document==entity } ?: EntityWorkspaceState(catalog).apply { resetTo(entity,allAssets()) }
        }
        if(selected!==states.firstOrNull { it.document.key==selectedKey }) selected?.cancelGesture()
        entities=states;parents=next.parents;prefabInstances=next.prefabInstances
        val cleared=history.canUndo || history.canRedo
        history.clear()
        if(cleared) message=HISTORY_CLEARED_NOTICE
        return cleared
    }
    /** The linked instance that owns [key], as its root or a descendant. */
    fun linkedInstance(key: String?): PrefabInstance? = key?.let { prefabInstances.firstOrNull { link -> it in link.entityKeys } }
    /** Assets referenced by the entities of [keys], e.g. for saving them as prefab content. */
    fun assetsOf(keys: Set<String>): Map<Path,String> = entities.filter { it.document.key in keys }.flatMap { it.assets.entries }.associate { it.key to it.value }
    /** Links [created] (from [prefabFromSceneSubtree] of [captured]) once its asset was saved as [assetId].
     * One undoable change; refuses when the scene changed after [captured] was taken. */
    fun linkSubtree(captured: SceneDocument,created: SubtreePrefab,assetId: String) {
        // Callers also compare full revisions (drafts included) before saving the asset.
        require(document()==captured) { "The scene changed while the entity template was saved; the entity template was saved but not linked" }
        val root=created.prefab.root
        val link=PrefabInstance(assetId,root,created.prefab,parent=created.scene.parents[root]).capture(created.scene)
        val live=entities.associateBy { it.document.key }
        val states=created.scene.entities.map { entity ->
            val previous=live[created.renamed[entity.key] ?: entity.key]
            previous?.takeIf { it.document==entity } ?: EntityWorkspaceState(catalog).apply { resetTo(entity,previous?.assets ?: emptyMap()) }
        }
        change("Create Entity Template ${historyName(created.scene.entities.first { it.key==root })}") { entities=states;parents=created.scene.parents;prefabInstances=prefabInstances+link;selection.select(root) }
    }
    fun insertPrefab(document: EntityDocument,imported: Map<Path,String>,position: Vec2?=null) {
        require(entities.size<MAX_SCENE_ENTITIES) { "Scene editor supports at most $MAX_SCENE_ENTITIES entities" }
        val placed=if(position==null) document else document.withPose(ScenePose.from(document).copy(position=position))
        val copy=copySceneEntity(placed,listOf(document.key))
        val state=EntityWorkspaceState(catalog).apply { resetTo(copy,imported) }
        change("Create ${historyName(copy)}") { entities=entities+state;selection.select(copy.key) }
    }
    fun placeTilemap(path: Path, position: Vec2? = null, convertLegacy: Boolean = false): String {
        require(entities.size < MAX_SCENE_ENTITIES) { "Too many scene entities" }
        val absolute = path.toAbsolutePath().normalize()
        val draft = mapDrafts[absolute] ?: TilemapDraft.open(absolute).also { mapDrafts[absolute] = it }
        val doc = EntityDocument(name = draft.document.name, components = buildJsonObject {
            putJsonObject("Tilemap") { put("asset", absolute.toString().replace('\\', '/')); put("sortingOffset", 0) }
        }).withPose(ScenePose(position = position ?: Vec2(0.0, 0.0)))
        val state = EntityWorkspaceState(catalog).apply { resetTo(doc, emptyMap()) }
        change(if (convertLegacy) "Convert Legacy Tilemap" else "Place Tilemap") {
            entities = entities + state; selection.select(doc.key)
            if (convertLegacy) tileLayers = null
        }
        activeTileLayer = draft.document.projectedLayers(absolute.toString()).firstOrNull()?.jsonObject?.get("id")?.jsonPrimitive?.content
        return doc.key
    }

    /** New root entity showing [texture] at its native size, e.g. an image dropped from Assets. */
    fun addSprite(texture: String,name: String,size: Vec2,position: Vec2?) {
        require(entities.size<MAX_SCENE_ENTITIES) { "Scene editor supports at most $MAX_SCENE_ENTITIES entities" }
        val state=EntityWorkspaceState(catalog)
        val sprite=(catalog["SpriteRenderer"]?.defaults ?: JsonObject(emptyMap()))+mapOf("texture" to JsonPrimitive(texture),
            "size" to buildJsonObject { put("x",size.x);put("y",size.y) })
        state.document=state.document.copy(name=name,components=JsonObject(state.document.components+("SpriteRenderer" to JsonObject(sprite))))
            .withPose(ScenePose(position=position ?: Vec2(0.0,0.0)))
        change("Create ${historyName(state.document)}") { entities=entities+state;selection.select(state.document.key) }
    }
    private fun subtree(root: String): Set<String> {
        val children=parents.entries.groupBy({it.value},{it.key});val result=mutableSetOf<String>();val queue=java.util.ArrayDeque<String>();queue.add(root)
        while(queue.isNotEmpty()) { val key=queue.removeFirst();if(result.add(key)) children[key]?.forEach(queue::add) };return result
    }
    fun worldDocument(key: String): EntityDocument = entities.first { it.document.key==key }.document.let {
        val documents=entities.map { state -> state.document }
        val ancestorScale=parents[key]?.let { parent -> sceneWorldPose(parent,documents,parents).scale } ?: Vec2(1.0,1.0)
        val pose = sceneWorldPose(key, documents, parents)
        val map = mapDraft(it)?.document
        val bounds = map?.let { source ->
            val size = Vec2(source.cellWidth / source.pixelsPerUnit, source.cellHeight / source.pixelsPerUnit)
            val points = source.source.getValue("layers").jsonArray.flatMap { layer ->
                if (layer.jsonObject.getValue("visible").jsonPrimitive.boolean) layer.jsonObject.getValue("cells").jsonArray.flatMap { raw ->
                    val cell = raw.jsonObject
                    val lower = pose.position + Vec2(cell.getValue("x").jsonPrimitive.int * size.x, cell.getValue("y").jsonPrimitive.int * size.y)
                    listOf(lower, lower + size)
                } else emptyList()
            }
            sle.editor.viewport.Bounds.of(points)
        }
        it.withPose(pose).copy(parentWorldScale=ancestorScale, tilemapWorldBounds=bounds)
    }
    fun applyWorldDocument(key: String,value: EntityDocument,mergeKey: Any?=null) {
        val state=entities.first { it.document.key==key }
        val local=parents[key]?.let { ScenePose.from(value).relativeTo(sceneWorldPose(it,entities.map { entity -> entity.document },parents)) } ?: ScenePose.from(value)
        state.applyGizmo(state.document.withPose(local),mergeKey)
    }
    fun reparent(childKey: String,parentKey: String?,keepWorld: Boolean=true) {
        require(entities.any { it.document.key==childKey }) { "Unknown child" }
        val next=parents-childKey+(parentKey?.let { mapOf(childKey to it) } ?: emptyMap())
        validateSceneParents(entities.map { it.document.key },next)
        val state=entities.first { it.document.key==childKey }
        val pose=if(keepWorld) {
            val world=sceneWorldPose(childKey,entities.map { it.document },parents)
            if(parentKey==null) world else world.relativeTo(sceneWorldPose(parentKey,entities.map { it.document },parents))
        } else null
        change("Reparent ${historyName(state.document)}") { parents=next;pose?.let { if("Transform" in state.document.components || it!=ScenePose()) state.applyStructure(state.document.withPose(it)) } }
    }
    fun addChild(position: Vec2?=null) = createEntity(asChild=true,position=position)
    fun addCamera(asChild: Boolean=false,position: Vec2?=null) = createEntity("Camera2D",asChild,position)
    fun addPreset(type: String,asChild: Boolean=false,position: Vec2?=null) {
        require(type in setOf("SpriteRenderer","BoxCollider","CircleCollider","BoxZone","CircleZone")) { "Unknown creation preset" }
        createEntity(type,asChild,position)
    }
    private fun createEntity(type: String?=null,asChild: Boolean=false,position: Vec2?=null) {
        require(entities.size<MAX_SCENE_ENTITIES) { "Scene entity limit reached" }
        val parent=if(asChild) selectedKey else null
        val state=EntityWorkspaceState(catalog)
        if (type != null && isEventArea(type)) {
            val label = if (type == "BoxZone") "Box Event Area" else "Circle Event Area"
            state.document = state.document.copy(name = label).addEditorComponent(type, catalog)
        } else if(type!=null) {
            val defaults=catalog[type]?.defaults ?: if(type=="Camera2D") buildJsonObject {
                put("enabled",true);put("primary",true);put("viewSize",buildJsonObject { put("x",480);put("y",270) });put("zoom",1)
            } else error("Component unavailable: $type")
            state.document=state.document.copy(name=if(type=="Camera2D") "Camera" else type,
                components=JsonObject(state.document.components+(type to editorComponentDefaults(type,defaults))))
        }
        val local=position?.let { point ->
            if(parent==null) point else ScenePose(position=point).relativeTo(ScenePose.from(worldDocument(parent))).position
        } ?: if(type=="Camera2D" && parent==null) Vec2(240.0,135.0) else null
        if(local!=null) state.document=state.document.withPose(ScenePose.from(state.document).copy(position=local))
        change("Create ${historyName(state.document)}") { entities=entities+state;selection.select(state.document.key);if(parent!=null) parents=parents+(state.document.key to parent) }
    }
    val activeCamera get()=entities.firstOrNull { val fields=it.document.components["Camera2D"] as? JsonObject;fields!=null && fields["enabled"]?.jsonPrimitive?.booleanOrNull!=false && fields["primary"]?.jsonPrimitive?.booleanOrNull!=false }
    fun setActiveCamera(key: String) {
        require(entities.any { it.document.key==key && "Camera2D" in it.document.components }) { "Entity has no camera" }
        change("Set Active Camera ${named(key)}") { entities.forEach { state -> (state.document.components["Camera2D"] as? JsonObject)?.let { camera -> state.applyStructure(state.document.copy(components=JsonObject(state.document.components+("Camera2D" to JsonObject(camera+("primary" to JsonPrimitive(state.document.key==key)))))) ) } } }
    }
    /** Duplicates the top-most selected subtrees (a selected entity whose ancestor is also
     * selected is copied once, with its ancestor) as one step, and selects the copies. */
    fun duplicateSelected() {
        val roots=selectionRoots().ifEmpty { return };val copied=LinkedHashSet<String>().apply { roots.forEach { addAll(subtree(it)) } }
        require(roots.none { root -> prefabInstances.any { root in it.entityKeys && root!=it.key } }) { "Duplicate the linked entity template root, or unpack first" }
        require(entities.size+copied.size<=MAX_SCENE_ENTITIES) { "Scene entity limit reached" }
        val remap=copied.associateWith { UUID.randomUUID().toString() }.toMutableMap();val ordinalKeys=entities.map { it.document.key }
        val duplicateLinks=prefabInstances.filter { it.key in copied }.map { link ->
            val nextKey=remap.getValue(link.key)
            link.source.entities.forEach { local -> remap[if(local.key==link.source.root) link.key else "${link.key}/${local.key}"]=if(local.key==link.source.root) nextKey else "$nextKey/${local.key}" }
            link.copy(parent=parents[link.key]).capture(document()).copy(key=nextKey,parent=parents[link.key]?.let { remap[it] ?: it })
        }
        val copies=entities.filter { it.document.key in copied }.map { source ->
            var document=source.document.copy(key=remap.getValue(source.document.key),name=if(prefabInstances.any { source.document.key in it.entityKeys }) source.document.name else source.document.name+" Copy")
            val animator=document.components["Animator"] as? JsonObject;val targets=animator?.get("targetEntities") as? JsonObject
            if(targets!=null) document=document.copy(components=JsonObject(document.components+("Animator" to JsonObject(animator+("targetEntities" to JsonObject(targets.mapValues { (_,value) ->
                val key=(value as? JsonPrimitive)?.let { if(it.isString) it.content else it.intOrNull?.let { ordinal -> ordinalKeys.getOrNull(ordinal-1) } }
                key?.let { JsonPrimitive(remap[it] ?: it) } ?: value
            }))))))
            EntityWorkspaceState(catalog).apply { resetTo(document,source.assets) }
        }
        change("Duplicate ${subjects(copied)}") { prefabInstances=prefabInstances+duplicateLinks;entities=entities+copies;parents=parents+copied.mapNotNull { child -> parents[child]?.let { remap.getValue(child) to (remap[it] ?: it) } }
            val focus=roots.firstOrNull { selectedKey in subtree(it) } ?: roots.last()
            selection.set(roots.map(remap::getValue),remap.getValue(focus)) }
    }
    /** Deletes the union of the selected subtrees as one step. */
    fun removeSelected() {
        val removed=LinkedHashSet<String>().apply { selectionRoots().forEach { addAll(subtree(it)) } }.ifEmpty { return }
        require(prefabInstances.none { link -> link.entityKeys.any { it in removed } && link.key !in removed }) { "Delete the linked entity template root, or unpack first" }
        val remaining=entities.filterNot { it.document.key in removed };requireReferences(remaining)
        val subject=subjects(removed)
        change("Delete $subject") { entities=remaining;parents=parents.filterKeys { it !in removed };prefabInstances=prefabInstances.filterNot { it.key in removed };selection.clear() }
        onDeleted?.invoke(subject)
    }
    private fun restoreOperation(target: Structure) {
        for(state in target.entities) {
            val id=state.document.key
            if(id in target.poseChanges) {
                val transform=target.poses[id];state.applyStructure(state.document.copy(components=JsonObject(if(transform==null) state.document.components-"Transform" else state.document.components+("Transform" to transform))))
            }
            if(id in target.cameraChanges) (state.document.components["Camera2D"] as? JsonObject)?.let { camera ->
                val flag=target.cameraFlags[id];state.applyStructure(state.document.copy(components=JsonObject(state.document.components+("Camera2D" to JsonObject(if(flag==null) camera-"primary" else camera+("primary" to flag))))))
            }
        }
        restore(target)
    }
    fun newScene() {
        cancelTileGesture?.invoke()
        selected?.cancelGesture()
        rememberTileView()
        parkScene(null)
        key=UUID.randomUUID().toString();name="New Scene";entities=emptyList();parents=emptyMap();selection.clear()
        activeTileLayer=null;tileSelections=emptyMap()
        tileLayers=null;extras=emptyMap();prefabInstances=emptyList();file=null;projectDocument=null;history.clear();error=null;message=null;saved=snapshot()
    }
    fun open(path: Path) {
        val packet=if (path.toString().endsWith(".slescene",true)) PrefabPackages.openScene(path) else null
        val loaded=SceneDocument.parse(canonicalize(packet?.document?.toString() ?: Files.readString(path)))
        loadDocument(loaded,packet?.assets ?: emptyMap(),path)
        projectDocument=null
    }
    private fun loadDocument(loaded: SceneDocument,assets: Map<Path,String>,path: Path) {
        val target = path.toAbsolutePath().normalize()
        cancelTileGesture?.invoke()
        selected?.cancelGesture()
        rememberTileView()
        parkScene(target)
        val parked = parkedScenes.remove(target)?.takeIf { previous ->
            val baseline = previous.baseline
            baseline.assets == assets && (baseline.document == loaded || runCatching {
                canonicalize(baseline.document.serialize()) == canonicalize(loaded.serialize())
            }.getOrDefault(false))
        }
        val used = mutableSetOf<Path>()
        val states = loaded.entities.mapIndexed { index, entity ->
            val text = entity.serialize()
            val own = assets.filterKeys { text.contains(it.toString().replace('\\','/')) }
                .also { used.addAll(it.keys) }
            parked?.entities?.get(index) ?: EntityWorkspaceState(catalog).apply { resetTo(entity, own) }
        }
        tileLayers = loaded.tileLayers
        val view = tileViews[target to loaded.key]
        val layerIds = tileLayerDocuments().mapTo(hashSetOf()) { it.id }
        activeTileLayer = view?.layer?.takeIf { it in layerIds }
        tileSelections = view?.selections.orEmpty().filterKeys { it in layerIds }
        prefabInstances = loaded.prefabInstances
        key = loaded.key
        name = loaded.name
        entities = states
        parents = loaded.parents
        if (parked == null) {
            selection.select(states.firstOrNull()?.document?.key)
            history.clear()
        } else {
            selection.restore(parked.selection, states.mapTo(hashSetOf()) { it.document.key })
            history.restoreTimeline(parked.timeline)
        }
        extras = assets.filterKeys { it !in used }
        file = path
        error = null
        message = "Opened ${path.fileName}"
        saved = snapshot()
    }
    fun save(path: Path) {
        require(path.toString().endsWith(".slescene",true)) { "Save scenes with the .slescene extension" }
        require(entities.all { it.fieldErrors.isEmpty() }) { "Finish invalid fields in every entity before saving" }
        val source=canonicalize(serialize())
        PrefabPackages.saveScene(source,allAssets(),path) { canonicalize(it) }
        file=path;projectDocument=null;saved=snapshot();error=null;message="Saved ${path.fileName}"
    }
}
