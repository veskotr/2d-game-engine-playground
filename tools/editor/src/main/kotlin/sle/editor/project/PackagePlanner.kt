package sle.editor.project

import kotlinx.serialization.json.*
import sle.editor.project.assets.AssetIdentity
import java.nio.file.Files
import java.nio.file.Path
import java.util.Locale

internal data class PackageReference(
    val targetId: String?,
    val spelling: String = targetId.orEmpty(),
    val colocate: Boolean = false,
)

internal data class PackageAsset(
    val id: String,
    val path: String,
    val companions: List<String> = emptyList(),
    val references: List<PackageReference> = emptyList(),
    val dynamic: Boolean = false,
)

internal data class PackageOwner(val sceneId: String?) {
    companion object {
        val Shared = PackageOwner(null)
    }
}

internal enum class PackageOwnershipReason {
    Scene, SoleConsumer, MultipleConsumers, Unreferenced, Dynamic, PriorShared, PriorPrivate, Colocation
}

internal data class PackageAssignment(val owner: PackageOwner, val name: String, val paths: List<String>)
internal data class PackageDependencyError(val source: String, val reference: String)
internal data class PackagePlan(
    val packages: List<PackageAssignment>,
    val owners: Map<String, PackageOwner>,
    val reasons: Map<String, PackageOwnershipReason>,
    val errors: List<PackageDependencyError>,
)

/** Package identity is the owner key. The display name and payload paths can change independently. */
internal object PackagePlanner {
    fun plan(
        assets: List<PackageAsset>,
        scenes: Map<String, String>,
        priorOwners: Map<String, PackageOwner> = emptyMap(),
    ): PackagePlan {
        val byId = assets.associateBy { it.id }.toSortedMap()
        require(byId.size == assets.size) { "Duplicate asset UUID in package inventory" }
        require(scenes.keys.all { it in byId }) { "Missing scene in package inventory" }
        val paths = mutableSetOf<String>()
        for (asset in byId.values) for (path in listOf(asset.path) + asset.companions) {
            GamePackageWriter.requirePortable(path)
            require(paths.add(path.lowercase(Locale.ROOT))) { "Duplicate package path: $path" }
        }

        for (path in paths) {
            var separator = path.indexOf('/')
            while (separator >= 0) {
                require(path.substring(0, separator) !in paths) { "Package path uses a file as a directory: $path" }
                separator = path.indexOf('/', separator + 1)
            }
        }

        val errors = byId.values.flatMap { asset ->
            asset.references.filter { it.targetId == null || it.targetId !in byId }.map { PackageDependencyError(asset.path, it.spelling) }
        }.distinct().sortedWith(compareBy({ it.source }, { it.reference }))
        val consumers = mutableMapOf<String, MutableSet<String>>()
        for (scene in scenes.keys.sorted()) {
            val visited = mutableSetOf<String>()
            val pending = ArrayDeque(listOf(scene))
            while (pending.isNotEmpty()) {
                val id = pending.removeLast()
                if (!visited.add(id)) continue
                val asset = byId.getValue(id)
                consumers.getOrPut(id, ::mutableSetOf).add(scene)
                asset.references.mapNotNull { it.targetId }.filter { it in byId }.forEach(pending::addLast)
            }
        }

        val neighbors = mutableMapOf<String, MutableSet<String>>()
        for (asset in byId.values) for (reference in asset.references) {
            val target = reference.targetId
            if (!reference.colocate || target == null || target !in byId) continue
            neighbors.getOrPut(asset.id, ::mutableSetOf).add(target)
            neighbors.getOrPut(target, ::mutableSetOf).add(asset.id)
        }
        val owners = mutableMapOf<String, PackageOwner>()
        val reasons = mutableMapOf<String, PackageOwnershipReason>()
        for (id in byId.keys) {
            if (id in owners) continue
            val group = mutableSetOf<String>()
            val pending = ArrayDeque(listOf(id))
            while (pending.isNotEmpty()) {
                val member = pending.removeLast()
                if (group.add(member)) neighbors[member].orEmpty().forEach(pending::addLast)
            }
            val pinned = group.filter { it in scenes }
            require(pinned.size <= 1) { "Cannot colocate multiple scene packages: ${pinned.sorted().joinToString()}" }
            val usedBy = group.flatMap { consumers[it].orEmpty() }.toSet()
            val previous = group.mapNotNull(priorOwners::get).toSet()
            val privateOwners = previous.filter { it.sceneId != null && it.sceneId in scenes }
            val (owner, reason) = when {
                pinned.isNotEmpty() -> PackageOwner(pinned.single()) to PackageOwnershipReason.Scene
                group.any { byId.getValue(it).dynamic } -> PackageOwner.Shared to PackageOwnershipReason.Dynamic
                usedBy.size > 1 -> PackageOwner.Shared to PackageOwnershipReason.MultipleConsumers
                PackageOwner.Shared in previous -> PackageOwner.Shared to PackageOwnershipReason.PriorShared
                privateOwners.size > 1 -> PackageOwner.Shared to PackageOwnershipReason.Colocation
                privateOwners.size == 1 -> privateOwners.single() to PackageOwnershipReason.PriorPrivate
                usedBy.isEmpty() -> PackageOwner.Shared to PackageOwnershipReason.Unreferenced
                else -> PackageOwner(usedBy.single()) to PackageOwnershipReason.SoleConsumer
            }
            for (member in group) {
                owners[member] = owner
                reasons[member] = if (group.size > 1 && member !in scenes) PackageOwnershipReason.Colocation else reason
            }
        }
        val pathsByOwner = byId.values.groupBy { owners.getValue(it.id) }
        val packages = (listOf(PackageOwner.Shared) + scenes.keys.sorted().map(::PackageOwner)).map { owner ->
            PackageAssignment(owner, owner.sceneId?.let(scenes::getValue) ?: "Shared",
                pathsByOwner[owner].orEmpty().flatMap { listOf(it.path) + it.companions }.sorted())
        }
        return PackagePlan(packages, owners.toSortedMap(), reasons.toSortedMap(), errors)
    }
}

internal data class SavedPackageInventory(
    val assets: List<PackageAsset>,
    val scenes: Map<String, String>,
    val files: ProjectDistribution.Inventory,
) {
    companion object {
        private const val PREFIX = "project-asset://"

        /** Inventories saved bytes and existing metadata without assigning identities or opening editor drafts. */
        fun read(root: Path): SavedPackageInventory {
            val files = ProjectDistribution.inventory(root)
            ProjectDistribution.identities(root, files)
            val ids = files.files.keys.filterNot { AssetIdentity.isMetadata(root.resolve(it)) }
                .associateWith { AssetIdentity.read(AssetIdentity.sidecar(root.resolve(it))) }
            val byUuid = ids.values.toSet()
            val scenes = sortedMapOf<String, String>()
            val tilesets = mutableSetOf<String>()
            val edges = mutableMapOf<String, MutableList<PackageReference>>()

            fun uuid(path: String, spelling: String, bare: Boolean = false): PackageReference {
                val id = when {
                    bare -> spelling
                    spelling.startsWith(PREFIX) -> spelling.removePrefix(PREFIX)
                    else -> null
                }?.lowercase(Locale.ROOT)
                return PackageReference(id, spelling).also { edges.getOrPut(path, ::mutableListOf).add(it) }
            }

            fun relative(path: String, spelling: String) {
                val raw = spelling.replace('\\', '/')
                val target = if (raw.startsWith('/') || ':' in raw) null else {
                    val base = Path.of(path).parent ?: Path.of("")
                    val resolved = base.resolve(raw).normalize().toString().replace('\\', '/')
                    ids[resolved]
                }
                edges.getOrPut(path, ::mutableListOf).add(PackageReference(target, spelling, colocate = true))
            }

            fun readJson(path: String, limit: Long): JsonObject = try {
                val file = root.resolve(path)
                require(Files.size(file) <= limit) { "Source exceeds ${limit / (1024 * 1024)} MiB" }
                Json.parseToJsonElement(Files.readString(file).removePrefix("\uFEFF")).jsonObject
            } catch (failure: IllegalArgumentException) {
                throw IllegalArgumentException("Cannot inventory $path. ${failure.message}", failure)
            }

            for ((path, id) in ids) {
                val kind = ProjectDocumentKind.entries.firstOrNull { path.endsWith(it.suffix, true) } ?: continue
                val envelope = readJson(path, 16L * 1024 * 1024)
                require(envelope["format"] == JsonPrimitive(kind.format) && envelope["version"] == JsonPrimitive(1)) {
                    "Unsupported project document: $path"
                }
                val document = envelope.getValue("document").jsonObject
                if (kind == ProjectDocumentKind.Scene) {
                    scenes[id] = document.text("name") ?: path.substringAfterLast('/').dropLast(kind.suffix.length)
                }
                projectDocumentAssetFields(document) { spelling ->
                    uuid(path, spelling)
                    spelling
                }
                for (extra in envelope.getValue("assets").jsonArray) uuid(path, extra.jsonPrimitive.content, bare = true)
                for (layer in (document["tileLayers"] as? JsonArray).orEmpty()) {
                    val reference = uuid(path, layer.jsonObject.getValue("tileset").jsonPrimitive.content)
                    reference.targetId?.takeIf { it in byUuid }?.let(tilesets::add)
                }
                for (entity in (document["entities"] as? JsonArray).orEmpty()) {
                    behaviourReferences(entity.jsonObject["components"] as? JsonObject).forEach { uuid(path, it) }
                }
                for (instance in (document["prefabs"] as? JsonArray).orEmpty()) {
                    val patches = instance.jsonObject["overrides"] as? JsonObject
                    for (patch in patches.orEmpty().values) {
                        behaviourReferences(patch as? JsonObject).forEach { uuid(path, it) }
                    }
                }
            }
            for ((path, id) in ids) {
                if (id in tilesets || path.endsWith(".tileset.json", true)) {
                    val tileset = readJson(path, 4L * 1024 * 1024)
                    require(tileset["format"] == JsonPrimitive("sle-tileset") && tileset["version"] in listOf(JsonPrimitive(1), JsonPrimitive(2))) {
                        "Unsupported tileset: $path"
                    }
                    uuid(path, tileset.getValue("spritesheet").jsonPrimitive.content)
                } else if (path.endsWith(".tilemap.json", true)) {
                    val map = sle.editor.model.TilemapDocument.parse(readJson(path, 16L * 1024 * 1024).toString())
                    map.tilesets.values.forEach { uuid(path, it) }
                } else if (path.endsWith(".tmj", true)) {
                    val map = readJson(path, 16L * 1024 * 1024)
                    for (entry in (map["tilesets"] as? JsonArray).orEmpty()) {
                        val tileset = entry.jsonObject
                        // The runtime supports embedded Tiled tilesets.
                        if ("source" !in tileset) tileset.text("image")?.let { relative(path, it) }
                    }
                }
            }
            val assets = ids.map { (path, id) ->
                PackageAsset(id, path, listOf("$path.slemeta"), edges[path].orEmpty().distinct(),
                    dynamic = path.endsWith(".lua", true))
            }
            return SavedPackageInventory(assets, scenes, files)
        }

        private fun JsonObject.text(field: String): String? =
            (get(field) as? JsonPrimitive)?.takeIf { it.isString && it.content.isNotBlank() }?.content

        private fun behaviourReferences(components: JsonObject?): List<String> {
            val script = components?.get("Script") as? JsonObject
            return (script?.get("behaviours") as? JsonArray).orEmpty().flatMap { behaviour ->
                val parameters = (behaviour as? JsonObject)?.get("parameters") as? JsonObject
                parameters.orEmpty().values.mapNotNull {
                    (it as? JsonPrimitive)?.takeIf { value -> value.isString && value.content.startsWith(PREFIX) }?.content
                }
            }
        }
    }
}
