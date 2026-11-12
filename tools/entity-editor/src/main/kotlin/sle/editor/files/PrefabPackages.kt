package sle.editor.files

import sle.editor.model.EntityDocument
import sle.editor.model.EntityFiles
import sle.editor.model.documentJson

import kotlinx.serialization.json.*
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption.*
import java.security.MessageDigest
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

internal data class OpenedAssetDocument(val document: JsonObject, val assets: Map<Path, String>)

internal data class OpenedPrefab(val document: EntityDocument, val assets: Map<Path, String>)

internal object PrefabPackages {
    private const val MAX_ENTRY = 128 * 1024 * 1024
    private const val MAX_TOTAL = 512L * 1024 * 1024
    private const val MAX_ENTRIES = 4096
    private val assetFields = setOf("texture", "scriptAsset", "assetPath", "layoutAsset", "fontAsset", "behaviorAsset", "definitionAsset", "clipAsset")
    private val assetId = Regex("[a-f0-9]{64}")
    private val assetEntry = Regex("assets/[a-f0-9]{64}(\\.[a-z0-9]{1,12})?")
    internal fun isAssetField(field: String): Boolean = field in assetFields

    internal fun readBytes(path: Path): ByteArray {
        require(Files.isRegularFile(path) && Files.size(path) <= MAX_ENTRY) { "Missing or oversized asset: $path" }
        return Files.newInputStream(path).use { it.readNBytes(MAX_ENTRY + 1) }.also {
            require(it.size <= MAX_ENTRY) { "Asset exceeds 128 MiB: $path" }
        }
    }

    internal fun importFile(path: Path): Path {
        val directory = temporaryDirectory()
        val imported = mutableMapOf<Path, Path>()
        val visiting = mutableSetOf<Path>()
        var total = 0L
        fun copy(source: Path): Path {
            val absolute = source.toAbsolutePath().normalize()
            require(absolute !in visiting) { "Cyclic JSON asset dependency: $source" }
            imported[absolute]?.let { return it }
            require(imported.size < MAX_ENTRIES - 2) { "Too many imported assets" }
            visiting.add(absolute)
            var bytes = readBytes(absolute)
            total += bytes.size
            require(total <= MAX_TOTAL) { "Imported assets exceed 512 MiB" }
            val target = directory.resolve("${imported.size}-${absolute.fileName}")
            imported[absolute] = target
            if (absolute.toString().endsWith(".json", true)) {
                bytes = assetReferences(Json.parseToJsonElement(bytes.toString(Charsets.UTF_8)), { text ->
                    val dependency = runCatching {
                        val relative = absolute.parent.resolve(text)
                        if (Files.isRegularFile(relative)) relative else Path.of(text).takeIf { Files.isRegularFile(it) }
                    }.getOrNull()
                    require(dependency != null) { "Missing JSON asset dependency: $text" }
                    copy(dependency).toString().replace('\\', '/')
                }).toString().toByteArray(Charsets.UTF_8)
            }
            Files.write(target, bytes)
            target.toFile().deleteOnExit()
            visiting.remove(absolute)
            return target
        }
        return copy(path)
    }

    private fun temporaryDirectory(): Path = Files.createTempDirectory("sle-prefab-").also { it.toFile().deleteOnExit() }
    private fun hash(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    // File discovery is schema-based: state names and other ordinary strings
    // must never change merely because a similarly named file exists.
    private fun assetReferences(value: JsonElement, string: (String) -> String, field: String = ""): JsonElement = when (value) {
        is JsonObject -> JsonObject(value.mapValues { (key, child) ->
            assetReferences(child, string, if (field == "stateClipMap") "clipAsset" else key)
        })
        is JsonArray -> JsonArray(value.map { assetReferences(it, string, field) })
        is JsonPrimitive -> if (value.isString && field in assetFields && value.content.isNotBlank()) JsonPrimitive(string(value.content)) else value
    }

    private data class Bundle(val entity: String, val manifest: String, val files: Map<String, ByteArray>)

    private fun bundle(document: JsonObject, extras: Map<Path, String>, format: String = "sle-prefab"): Bundle {
        val files = linkedMapOf<String, ByteArray>()
        val metadata = linkedMapOf<String, JsonObject>()
        val imported = mutableMapOf<Path, String>()
        val visiting = mutableSetOf<Path>()
        var total = 0L
        fun resolve(text: String, parent: Path): Path? {
            if (text.isBlank() || text.startsWith("asset://")) return null
            return runCatching {
                val path = Path.of(text)
                listOf(parent.resolve(path), path.toAbsolutePath()).firstOrNull { Files.isRegularFile(it) }
            }.getOrNull()
        }
        fun include(path: Path): String {
            val absolute = path.toAbsolutePath().normalize()
            imported[absolute]?.let { return it }
            require(visiting.add(absolute)) { "Cyclic JSON asset dependency: $path" }
            var bytes = readBytes(absolute)
            val name = extras[absolute] ?: extras[path] ?: absolute.fileName.toString()
            if (name.endsWith(".json", true)) {
                val json = Json.parseToJsonElement(bytes.toString(Charsets.UTF_8))
                bytes = assetReferences(json, { text ->
                    require(!text.startsWith("asset://")) { "Unresolved package reference: $text" }
                    include(resolve(text, absolute.parent) ?: throw IllegalArgumentException("Missing JSON asset dependency: $text"))
                }).toString().toByteArray(Charsets.UTF_8)
            }
            require(bytes.size <= MAX_ENTRY) { "Asset exceeds 128 MiB after resolving dependencies: $path" }
            val id = hash(bytes)
            val extension = name.substringAfterLast('.', "").lowercase().takeIf { it.matches(Regex("[a-z0-9]{1,12}")) }
            val entry = "assets/$id" + (extension?.let { ".$it" } ?: "")
            if (id !in metadata) {
                require(files.size + 2 < MAX_ENTRIES) { "Too many package assets" }
                total += bytes.size
                require(total <= MAX_TOTAL - 8 * 1024 * 1024) { "Package exceeds 512 MiB" }
                files[entry] = bytes
                metadata[id] = buildJsonObject { put("id", id); put("path", entry); put("name", name) }
            }
            visiting.remove(absolute)
            return "asset://$id".also { imported[absolute] = it }
        }
        // Do not treat entity names, script variables or animator entity keys as file paths.
        val packaged = assetReferences(document, { text ->
            include(resolve(text, Path.of("").toAbsolutePath()) ?: throw IllegalArgumentException("Missing asset: $text"))
        }).jsonObject
        extras.keys.forEach { include(it) }
        val manifest = buildJsonObject {
            put("format", format); put("version", 1)
            put("assets", JsonArray(metadata.values.toList()))
        }.toString()
        val entity = documentJson.encodeToString(JsonElement.serializer(), packaged) + "\n"
        require(entity.toByteArray(Charsets.UTF_8).size <= 4 * 1024 * 1024 && manifest.toByteArray(Charsets.UTF_8).size <= 4 * 1024 * 1024) {
            "Package metadata exceeds 4 MiB"
        }
        return Bundle(entity, manifest, files)
    }

    fun save(document: EntityDocument, assets: Map<Path, String>, path: Path, validate: (String) -> Unit) =
        saveDocument(document.serialize(), assets, path, "sle-prefab", "entity.json", validate)

    fun saveScene(source: String, assets: Map<Path, String>, path: Path, validate: (String) -> Unit) =
        saveDocument(source, assets, path, "sle-scene", "scene.json", validate)

    private fun saveDocument(source: String, assets: Map<Path, String>, path: Path, format: String,
        documentEntry: String, validate: (String) -> Unit) {
        validate(source)
        val bundle = bundle(Json.parseToJsonElement(source).jsonObject, assets, format)
        val target = path.toAbsolutePath()
        val temporary = Files.createTempFile(target.parent, ".sle-package-", ".tmp")
        try {
            ZipOutputStream(Files.newOutputStream(temporary)).use { zip ->
                fun entry(name: String, bytes: ByteArray) {
                    val crc = CRC32().apply { update(bytes) }.value
                    zip.putNextEntry(ZipEntry(name).apply {
                        method = ZipEntry.STORED; size = bytes.size.toLong(); compressedSize = size; this.crc = crc
                    })
                    zip.write(bytes)
                    zip.closeEntry()
                }
                entry("manifest.json", bundle.manifest.toByteArray(Charsets.UTF_8))
                entry(documentEntry, bundle.entity.toByteArray(Charsets.UTF_8))
                bundle.files.forEach { (name, bytes) -> entry(name, bytes) }
            }
            require(Files.size(temporary) <= MAX_TOTAL) { "Package exceeds 512 MiB" }
            Files.move(temporary, target, ATOMIC_MOVE, REPLACE_EXISTING)
        } finally { Files.deleteIfExists(temporary) }
    }

    fun exportJson(document: EntityDocument, assets: Map<Path, String>, path: Path, validate: (String) -> Unit): OpenedPrefab {
        validate(document.serialize())
        val bundle = bundle(Json.parseToJsonElement(document.serialize()).jsonObject, assets)
        val directory = path.toAbsolutePath().parent.resolve(path.fileName.toString() + ".assets")
            .resolve(hash((bundle.entity + bundle.manifest).toByteArray(Charsets.UTF_8)))
        val descriptors = Json.parseToJsonElement(bundle.manifest).jsonObject.getValue("assets").jsonArray
        val paths = descriptors.associate { item ->
            val descriptor = item.jsonObject
            descriptor.getValue("id").jsonPrimitive.content to directory.resolve(descriptor.getValue("path").jsonPrimitive.content.substringAfter("assets/"))
        }
        fun resolve(value: JsonElement) = assetReferences(value, { text ->
            if (text.startsWith("asset://")) paths.getValue(text.removePrefix("asset://")).toString().replace('\\', '/') else text
        })
        val exported = EntityDocument.parse(resolve(Json.parseToJsonElement(bundle.entity)).toString())
        val names = linkedMapOf<Path, String>()
        if (paths.isNotEmpty()) Files.createDirectories(directory)
        descriptors.forEach { item ->
            val descriptor = item.jsonObject
            val target = paths.getValue(descriptor.getValue("id").jsonPrimitive.content)
            val name = descriptor.getValue("name").jsonPrimitive.content
            val raw = bundle.files.getValue(descriptor.getValue("path").jsonPrimitive.content)
            val bytes = if (name.endsWith(".json", true)) resolve(Json.parseToJsonElement(raw.toString(Charsets.UTF_8))).toString().toByteArray(Charsets.UTF_8) else raw
            if (Files.exists(target)) require(Files.readAllBytes(target).contentEquals(bytes)) { "Exported asset content mismatch" }
            else Files.write(target, bytes)
            names[target] = name
        }
        EntityFiles.save(exported, path, validate)
        return OpenedPrefab(exported, names)
    }

    fun open(path: Path): OpenedPrefab = openDocument(path, "sle-prefab", "entity.json").let {
        OpenedPrefab(EntityDocument.parse(it.document.toString()), it.assets)
    }

    fun openScene(path: Path): OpenedAssetDocument = openDocument(path, "sle-scene", "scene.json")

    private fun openDocument(path: Path, format: String, documentEntry: String): OpenedAssetDocument {
        require(Files.size(path) <= MAX_TOTAL) { "Package exceeds 512 MiB" }
        val entries = linkedMapOf<String, ByteArray>()
        var total = 0L
        ZipFile(path.toFile()).use { zip ->
            val iterator = zip.entries()
            while (iterator.hasMoreElements()) {
                val entry = iterator.nextElement()
                require(entry.name == documentEntry || entry.name == "manifest.json" || assetEntry.matches(entry.name)) { "Unsafe or unsupported package entry: ${entry.name}" }
                require(entry.method == ZipEntry.STORED && entry.size in 0..MAX_ENTRY.toLong()) { "Unsupported compression or oversized entry" }
                require(entries.size < MAX_ENTRIES && entry.name !in entries) { "Duplicate entry or too many entries" }
                total += entry.size
                require(total <= MAX_TOTAL) { "Package exceeds 512 MiB" }
                val bytes = zip.getInputStream(entry).use { it.readNBytes(MAX_ENTRY + 1) }
                require(bytes.size.toLong() == entry.size && CRC32().apply { update(bytes) }.value == entry.crc) { "Corrupt package entry: ${entry.name}" }
                entries[entry.name] = bytes
            }
        }
        fun json(name: String): JsonObject {
            val bytes = entries[name] ?: error("Missing $name")
            require(bytes.size <= 4 * 1024 * 1024) { "Oversized package metadata" }
            return Json.parseToJsonElement(bytes.toString(Charsets.UTF_8)).jsonObject
        }
        val manifest = json("manifest.json")
        require(manifest["format"]?.jsonPrimitive?.content == format && manifest["version"]?.jsonPrimitive?.intOrNull == 1) { "Unsupported prefab package format/version" }
        val directory = temporaryDirectory()
        val paths = linkedMapOf<String, Path>()
        val names = linkedMapOf<Path, String>()
        val listed = mutableSetOf(documentEntry, "manifest.json")
        val descriptors = manifest.getValue("assets").jsonArray
        for (item in descriptors) {
            val descriptor = item.jsonObject
            val id = descriptor.getValue("id").jsonPrimitive.content
            val entry = descriptor.getValue("path").jsonPrimitive.content
            val name = descriptor.getValue("name").jsonPrimitive.content
            require(assetId.matches(id) && assetEntry.matches(entry) && entry.substringAfter("assets/").substringBefore('.') == id) { "Invalid asset descriptor" }
            require(id !in paths && listed.add(entry)) { "Duplicate asset" }
            val bytes = entries[entry] ?: error("Missing asset: $entry")
            require(hash(bytes) == id) { "Corrupt asset: $name" }
            val target = directory.resolve(entry.substringAfter("assets/"))
            paths[id] = target
            names[target] = name
        }
        require(listed == entries.keys) { "Unlisted package entries" }
        fun materialize(value: JsonElement) = assetReferences(value, { text ->
            if (text.startsWith("asset://")) (paths[text.removePrefix("asset://")] ?: error("Missing asset reference: $text")).toString().replace('\\', '/') else text
        })
        // Resolve every reference before publishing files or changing the workspace.
        val payloads = descriptors.associate { item ->
            val descriptor = item.jsonObject
            val id = descriptor.getValue("id").jsonPrimitive.content
            val bytes = entries.getValue(descriptor.getValue("path").jsonPrimitive.content)
            paths.getValue(id) to if (descriptor.getValue("name").jsonPrimitive.content.endsWith(".json", true))
                materialize(Json.parseToJsonElement(bytes.toString(Charsets.UTF_8))).toString().toByteArray(Charsets.UTF_8) else bytes
        }
        val document = materialize(json(documentEntry)).jsonObject
        payloads.forEach { (target, bytes) -> Files.write(target, bytes); target.toFile().deleteOnExit() }
        return OpenedAssetDocument(document, names)
    }
}
