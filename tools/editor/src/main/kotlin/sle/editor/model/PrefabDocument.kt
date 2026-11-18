package sle.editor.model

import kotlinx.serialization.json.*

/** A self-contained subtree. Local keys remain stable across prefab edits. */
internal data class PrefabDocument(val root: String, val entities: List<EntityDocument>, val parents: Map<String,String> = emptyMap()) {
    fun serialize(): String {
        validate()
        val scene=Json.parseToJsonElement(SceneDocument(entities=entities,parents=parents).serialize()).jsonObject
        return documentJson.encodeToString(JsonElement.serializer(),buildJsonObject {
            put("root",root);put("entities",scene.getValue("entities"))
        })+"\n"
    }
    private fun validate() {
        val keys=entities.map { it.key }
        require(root in keys && root !in parents && keys.distinct().size==keys.size) { "Entity template requires one unique root" }
        validateSceneParents(keys,parents)
        for(key in keys) {
            var current=key
            while(current in parents) current=parents.getValue(current)
            require(current==root) { "Every entity in an entity template must descend from its root" }
        }
        entities.forEach { entity -> remapPrefabReferences(entity,keys,keys.associateWith { it }) }
    }
    fun instantiate(key: String, parent: String?=null, overrides: JsonObject=JsonObject(emptyMap())): SceneDocument {
        validate()
        require(key.isNotBlank() && key!="@scene") { "Invalid instance key" }
        val keys=entities.map { it.key }
        val remap=keys.associateWith { if(it==root) key else "$key/$it" }
        require(remap.values.distinct().size==keys.size) { "Instance keys collide" }
        require(overrides.keys.all { it in keys }) { "Override refers to an entity missing from the entity template" }
        val expanded=entities.map { entity ->
            val patch=overrides[entity.key] as? JsonObject ?: JsonObject(emptyMap())
            require(patch.values.all { it is JsonObject || it==JsonNull }) { "Invalid component override" }
            val components=mergePrefabPatch(entity.components,patch).jsonObject
            remapPrefabReferences(entity.copy(components=components),keys,remap).copy(key=remap.getValue(entity.key))
        }
        val hierarchy=parents.map { (child,p) -> remap.getValue(child) to remap.getValue(p) }.toMap() +
            (parent?.let { mapOf(key to it) } ?: emptyMap())
        return SceneDocument(entities=expanded,parents=hierarchy)
    }
    companion object {
        fun parse(source: String): PrefabDocument {
            val json=Json.parseToJsonElement(source).jsonObject
            require(json.keys==setOf("root","entities")) { "Unsupported entity template metadata; no content was discarded" }
            val root=json.getValue("root").jsonPrimitive.also { require(it.isString) }.content
            val scene=SceneDocument.parse(buildJsonObject { put("entities",json.getValue("entities")) }.toString())
            return PrefabDocument(root,scene.entities,scene.parents).also { it.validate() }
        }
        fun fromScene(scene: SceneDocument,root: String): PrefabDocument {
            val selected=mutableSetOf(root)
            do { val before=selected.size;scene.parents.filterValues { it in selected }.keys.forEach(selected::add) } while(selected.size!=before)
            val keys=scene.entities.map { it.key }
            val entities=scene.entities.filter { it.key in selected }.map { remapPrefabReferences(it,keys,selected.associateWith { it }) }
            return PrefabDocument(root,entities,scene.parents.filter { it.key in selected && it.key!=root }).also { it.validate() }
        }
    }
}

internal fun remapPrefabReferences(entity: EntityDocument,ordinals: List<String>,keys: Map<String,String>): EntityDocument {
    val animator=entity.components["Animator"] as? JsonObject ?: return entity
    val targets=animator["targetEntities"] as? JsonObject ?: return entity
    val mapped=JsonObject(targets.mapValues { (_,value) ->
        val primitive=value as? JsonPrimitive ?: error("Invalid Animator target")
        val local=if(primitive.isString) primitive.content else primitive.intOrNull?.let { ordinals.getOrNull(it-1) }
        JsonPrimitive(keys[local] ?: error("Animator target is outside entity template: $value"))
    })
    return entity.copy(components=JsonObject(entity.components+("Animator" to JsonObject(animator+("targetEntities" to mapped)))))
}

/** Engine merge semantics: objects merge, arrays replace, null removes a component/field. */
internal fun mergePrefabPatch(base: JsonElement,patch: JsonElement): JsonElement {
    if(patch !is JsonObject) return patch
    val result=(base as? JsonObject)?.toMutableMap() ?: mutableMapOf()
    patch.forEach { (key,value) -> if(value==JsonNull) result.remove(key) else result[key]=mergePrefabPatch(result[key] ?: JsonNull,value) }
    return JsonObject(result)
}

internal fun prefabDifference(base: JsonObject,value: JsonObject): JsonObject = JsonObject((base.keys+value.keys).mapNotNull { key ->
    val old=base[key];val next=value[key]
    when {
        old==next -> null
        next==null -> key to JsonNull
        old is JsonObject && next is JsonObject -> key to prefabDifference(old,next)
        else -> key to next
    }
}.toMap())

/** The source snapshot is a baseline, never the instance itself: repeated updates cannot feed back. */
internal data class PrefabInstance(val assetId: String,val key: String,val source: PrefabDocument,
    val overrides: JsonObject=JsonObject(emptyMap()),val parent: String?=null) {
    val entityKeys get()=source.entities.map { sceneKey(it.key) }.toSet()
    fun sceneKey(local: String)=if(local==source.root) key else "$key/$local"
    /** Prefab content made from this instance: its component edits become the source, while the
     * root Transform keeps the source's own placement (instance placement always stays local). */
    fun applied(scene: SceneDocument): PrefabDocument {
        capture(scene) // Rejects structural, name and external-reference changes first.
        val reverse=source.entities.associate { sceneKey(it.key) to it.key }
        val ordinals=scene.entities.map { it.key }
        return source.copy(entities=source.entities.map { local ->
            val current=remapPrefabReferences(scene.entities.first { it.key==sceneKey(local.key) },ordinals,reverse)
            val placement=local.components["Transform"]
            local.copy(components=if(local.key!=source.root) current.components
                else JsonObject(current.components-"Transform"+(placement?.let { mapOf("Transform" to it) } ?: emptyMap())))
        })
    }
    fun capture(scene: SceneDocument): PrefabInstance {
        require(scene.entities.map { it.key }.containsAll(entityKeys)) { "Linked entity template subtree changed; unpack before changing its structure" }
        val base=source.instantiate(key,parent)
        require(entityKeys.all { scene.parents[it]==base.parents[it] }) { "Linked entity template hierarchy changed; unpack first" }
        val reverse=source.entities.associate { (if(it.key==source.root) key else "$key/${it.key}") to it.key }
        val ordinals=scene.entities.map { it.key }
        val patches=source.entities.associate { local ->
            val global=if(local.key==source.root) key else "$key/${local.key}"
            val current=remapPrefabReferences(scene.entities.first { it.key==global },ordinals,reverse)
            fun explicit(mask: JsonObject,value: JsonObject): JsonObject = JsonObject(mask.mapValues { (field,old) ->
                val now=value[field]
                if(old is JsonObject && now is JsonObject) explicit(old,now) else now ?: JsonNull
            })
            val previous=overrides[local.key] as? JsonObject ?: JsonObject(emptyMap())
            val retained=explicit(previous,current.components)
            fun overlay(old: JsonObject,new: JsonObject): JsonObject = JsonObject(old+new.mapValues { (field,value) ->
                val previousValue=old[field]
                if(previousValue is JsonObject && value is JsonObject) overlay(previousValue,value) else value
            })
            val difference=overlay(retained,prefabDifference(local.components,current.components)).toMutableMap()
            // Placement is always local, even when equal to the saved source placement.
            if(local.key==source.root) difference["Transform"]=current.components["Transform"] ?: JsonNull
            require(local.name==current.name) { "Rename the entity in the entity template asset, or unpack the instance first" }
            local.key to JsonObject(difference)
        }.filterValues { it.isNotEmpty() }
        return copy(overrides=JsonObject(patches))
    }
    fun reconcile(scene: SceneDocument,updated: PrefabDocument): SceneDocument {
        val captured=capture(scene)
        require(captured.overrides.keys.all { local -> local in updated.entities.map { it.key } }) { "Updated entity template removes an overridden descendant" }
        val replacement=updated.instantiate(key,parent,captured.overrides)
        val combined=scene.copy(entities=scene.entities.filterNot { it.key in entityKeys }+replacement.entities,
            parents=scene.parents.filterKeys { it !in entityKeys }+replacement.parents)
        // Serialization catches collisions and hierarchy references before publishing the result.
        combined.serialize()
        val keys=combined.entities.map { it.key }
        combined.entities.forEach { remapPrefabReferences(it,keys,keys.associateWith { it }) }
        return combined
    }
}

/** Apply Changes to Prefab: returns the new prefab content and [scene] with that instance's link
 * rebased onto it, so the applied edits are not retained as instance overrides. Other instances
 * still hold their previous source and are reconciled by the normal prefab update. */
internal fun applyInstanceToPrefab(scene: SceneDocument,instanceKey: String): Pair<PrefabDocument,SceneDocument> {
    val link=scene.prefabInstances.singleOrNull { it.key==instanceKey } ?: error("Select a linked entity template instance")
    val placed=link.copy(parent=scene.parents[link.key])
    val prefab=placed.applied(scene)
    val rebased=PrefabInstance(link.assetId,link.key,prefab,parent=placed.parent).capture(scene)
    return prefab to scene.copy(prefabInstances=scene.prefabInstances.map { if(it.key==instanceKey) rebased else it })
}

/** An authored scene subtree converted to prefab content, plus the same scene re-keyed as that
 * prefab's linked instance. [renamed] maps each new scene key to the key it replaced. */
internal data class SubtreePrefab(val prefab: PrefabDocument,val scene: SceneDocument,val renamed: Map<String,String>) {
    /** Uses the prefab content as saved (canonicalized, assets adopted), so the new link starts without
     * spurious overrides; the instance entities are re-expanded from it. */
    fun withSource(stored: PrefabDocument): SubtreePrefab {
        require(stored.root==prefab.root && stored.entities.map { it.key }.toSet()==prefab.entities.map { it.key }.toSet() && stored.parents==prefab.parents) {
            "The saved entity template structure differs from the dragged entity"
        }
        val expanded=stored.instantiate(prefab.root,scene.parents[prefab.root]).entities.associateBy { it.key }
        return copy(prefab=stored,scene=scene.copy(entities=scene.entities.map { expanded[it.key] ?: it }))
    }
}

/** `<folder>/<name>.prefab.json`, numbered (`<name> 2.prefab.json`, …) when a file already exists. */
internal fun uniquePrefabPath(folder: java.nio.file.Path,name: String): java.nio.file.Path {
    val stem=prefabFileName(name).removeSuffix(".prefab.json")
    return generateSequence(1) { it+1 }.map { index -> folder.resolve(if(index==1) "$stem.prefab.json" else "$stem $index.prefab.json") }
        .first { !java.nio.file.Files.exists(it,java.nio.file.LinkOption.NOFOLLOW_LINKS) }
}

/** The root keeps its key (it becomes the instance key); descendants become `<root>/<key>`.
 * Parent and Animator references elsewhere in the scene follow the renamed keys; entity order is
 * unchanged, so numeric references keep their positions. Nothing is linked until the caller has
 * saved the asset; see [SceneWorkspaceState.linkSubtree]. */
internal fun prefabFromSceneSubtree(scene: SceneDocument,root: String): SubtreePrefab {
    require(scene.entities.any { it.key==root }) { "The dragged entity no longer exists" }
    val prefab=PrefabDocument.fromScene(scene,root)
    val owned=prefab.entities.map { it.key }.toSet()
    require(scene.prefabInstances.none { link -> link.entityKeys.any { it in owned } }) {
        "Entity templates cannot contain linked entity template instances yet; unpack the instance first"
    }
    val remap=owned.associateWith { if(it==root) root else "$root/$it" }
    val ordinals=scene.entities.map { it.key }
    require(scene.entities.none { entity -> entity.key !in owned &&
        ((entity.components["Animator"] as? JsonObject)?.get("targetEntities") as? JsonObject)?.values.orEmpty().any { value ->
            // Saving moves the instance rows into `prefabs`, so even a numeric target to the root would shift.
            value is JsonPrimitive && !value.isString && value.intOrNull?.let { ordinals.getOrNull(it-1) }?.let { it in owned }==true
        } }) { "A numeric Animator target addresses an entity in this subtree; save and reopen the scene to normalize it first" }
    val others=scene.entities.map { it.key }.toSet()-owned
    require(remap.values.none { it in others }) { "Existing entity keys collide with the new entity template instance" }
    val expanded=prefab.instantiate(root,scene.parents[root]).entities.associateBy { it.key }
    val entities=scene.entities.map { entity ->
        if(entity.key in owned) expanded.getValue(remap.getValue(entity.key)) else renameEntityReferences(entity,remap)
    }
    val parents=scene.parents.entries.associate { (child,parent) -> (remap[child] ?: child) to (remap[parent] ?: parent) }
    val result=scene.copy(entities=entities,parents=parents)
    result.serialize()
    return SubtreePrefab(prefab,result,remap.entries.associate { (old,new) -> new to old })
}

/** Rewrites keyed Animator targets; numeric targets address positions and are left unchanged. */
internal fun renameEntityReferences(entity: EntityDocument,remap: Map<String,String>): EntityDocument {
    val animator=entity.components["Animator"] as? JsonObject ?: return entity
    val targets=animator["targetEntities"] as? JsonObject ?: return entity
    val mapped=JsonObject(targets.mapValues { (_,value) ->
        if(value is JsonPrimitive && value.isString) remap[value.content]?.let(::JsonPrimitive) ?: value else value
    })
    return if(mapped==targets) entity
        else entity.copy(components=JsonObject(entity.components+("Animator" to JsonObject(animator+("targetEntities" to mapped)))))
}

/** Use the scene codec for cross-entity references, keeping single-entity JNI APIs compatible. */
internal fun canonicalPrefabDocument(source: String): String {
    val tree=PrefabDocument.parse(source)
    val scene=SceneDocument(entities=tree.entities,parents=tree.parents)
    val canonical=SceneDocument.parse(canonicalSceneDocument(scene.serialize()))
    return PrefabDocument(tree.root,canonical.entities,canonical.parents).serialize()
}
