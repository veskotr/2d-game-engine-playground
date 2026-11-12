package sle.editor.files

import sle.editor.model.EntityDocument
import sle.editor.model.EntityFiles

import kotlinx.serialization.json.*
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.*

class PrefabPackageTest {
    private fun storedPackage(entries: Map<String, String>): java.nio.file.Path {
        val path = Files.createTempFile("sle-package-fixture", ".sleprefab")
        ZipOutputStream(Files.newOutputStream(path)).use { zip ->
            entries.forEach { (name, text) ->
                val bytes = text.toByteArray()
                zip.putNextEntry(ZipEntry(name).apply {
                    method = ZipEntry.STORED; size = bytes.size.toLong(); compressedSize = size
                    crc = java.util.zip.CRC32().apply { update(bytes) }.value
                })
                zip.write(bytes); zip.closeEntry()
            }
        }
        return path
    }

    @Test fun refusesUnknownVersionsMissingAssetReferencesAndCorruptPayloads() {
        fun manifest(version: Int) = """{"format":"sle-prefab","version":$version,"assets":[]}"""
        val empty = EntityDocument()
        assertFails { PrefabPackages.open(storedPackage(mapOf("manifest.json" to manifest(2), "entity.json" to empty.serialize()))) }
        val missing = empty.addComponent("SpriteRenderer", buildJsonObject { put("texture", "asset://missing") })
        assertFails { PrefabPackages.open(storedPackage(mapOf("manifest.json" to manifest(1), "entity.json" to missing.serialize()))) }
        val corrupt = storedPackage(mapOf("manifest.json" to manifest(1), "entity.json" to empty.serialize()))
        val bytes = Files.readAllBytes(corrupt)
        // First payload begins after the local header and ASCII manifest filename.
        bytes[30 + "manifest.json".length] = '!'.code.toByte()
        Files.write(corrupt, bytes)
        assertFails { PrefabPackages.open(corrupt) }
    }

    @Test fun importedJsonDependenciesSurviveDeletingTheirOriginalDirectory() {
        val root = Files.createTempDirectory("sle-json-dependencies")
        val dependency = root.resolve("clip.bin")
        Files.writeString(dependency, "dependent payload")
        val source = root.resolve("animation.json")
        Files.writeString(source, """{"clipAsset":"clip.bin","initialState":"Idle","states":[{"name":"Idle"}]}""")
        Files.writeString(root.resolve("Idle"), "Not a dependency")
        val imported = PrefabPackages.importFile(source)
        Files.delete(source)
        Files.delete(dependency)
        val target = root.resolve("actor.sleprefab")
        PrefabPackages.save(EntityDocument(), mapOf(imported to "animation.json"), target) {}
        val loaded = PrefabPackages.open(target)
        val json = loaded.assets.entries.single { it.value == "animation.json" }.key
        val decoded = Json.parseToJsonElement(Files.readString(json)).jsonObject
        assertEquals("Idle", decoded.getValue("initialState").jsonPrimitive.content)
        assertEquals("Idle", decoded.getValue("states").jsonArray.single().jsonObject.getValue("name").jsonPrimitive.content)
        val payload = decoded.getValue("clipAsset").jsonPrimitive.content
        assertEquals("dependent payload", Files.readString(java.nio.file.Path.of(payload)))
        assertEquals(2, loaded.assets.size)
    }

    @Test fun oversizedMetadataAndValidationFailurePreserveThePreviousPackage() {
        val path = Files.createTempFile("sle-metadata", ".sleprefab")
        Files.writeString(path, "original")
        assertFails { PrefabPackages.save(EntityDocument(name = "a".repeat(4 * 1024 * 1024)), emptyMap(), path) {} }
        assertEquals("original", Files.readString(path))
        assertFails { PrefabPackages.save(EntityDocument(), emptyMap(), path) { error("Invalid component") } }
        assertEquals("original", Files.readString(path))
    }

    @Test fun identicalAssetPayloadsAreStoredOnceAndJsonExportDoesNotDependOnTemporaryImports() {
        val root = Files.createTempDirectory("sle-package-dedup")
        val first = root.resolve("first.txt").also { Files.writeString(it, "same content") }
        val second = root.resolve("second.txt").also { Files.writeString(it, "same content") }
        val document = EntityDocument()
        val packagePath = root.resolve("deduplicated.sleprefab")
        PrefabPackages.save(document, mapOf(first to "first.txt", second to "second.txt"), packagePath) {}
        val opened = PrefabPackages.open(packagePath)
        assertEquals(1, opened.assets.size)
        val json = root.resolve("plain.prefab.json")
        val exported = PrefabPackages.exportJson(opened.document, opened.assets, json) {}
        opened.assets.keys.forEach { Files.delete(it) }
        assertTrue(exported.assets.keys.all { Files.exists(it) && it.startsWith(root) })
        assertEquals(document, EntityFiles.open(json))
    }

    @Test fun bundledAssetsSurviveRelocationAndOriginalRemovalIncludingUnusedFiles() {
        val root = Files.createTempDirectory("sle-package-test")
        val texture = root.resolve("image with spaces.png")
        Files.write(texture, byteArrayOf(1, 2, 3, 4))
        val extra = root.resolve("notes.txt")
        Files.writeString(extra, "Additional content")
        val document = EntityDocument(name = "asset://example").addComponent("SpriteRenderer", buildJsonObject { put("texture", texture.toString()) })
        val target = root.resolve("actor.sleprefab")
        PrefabPackages.save(document, mapOf(extra to "notes.txt"), target) {}
        val moved = root.resolve("moved").also { Files.createDirectory(it) }.resolve("renamed.sleprefab")
        Files.move(target, moved)
        Files.delete(texture)
        Files.delete(extra)
        val loaded = PrefabPackages.open(moved)
        val path = java.nio.file.Path.of(loaded.document.components.getValue("SpriteRenderer").jsonObject.getValue("texture").jsonPrimitive.content)
        assertContentEquals(byteArrayOf(1, 2, 3, 4), Files.readAllBytes(path))
        assertEquals(document.key, loaded.document.key)
        assertEquals(document.name, loaded.document.name)
        assertTrue(loaded.assets.values.contains("notes.txt"))
        val second = root.resolve("resaved.sleprefab")
        PrefabPackages.save(loaded.document, loaded.assets, second) {}
        assertEquals(2, PrefabPackages.open(second).assets.size)
    }

    @Test fun missingAssetsDoNotReplaceAnExistingPackage() {
        val target = Files.createTempFile("sle-package", ".sleprefab")
        Files.writeString(target, "original")
        val document = EntityDocument().addComponent("SpriteRenderer", buildJsonObject { put("texture", "missing-texture-12345.png") })
        assertFails { PrefabPackages.save(document, emptyMap(), target) {} }
        assertEquals("original", Files.readString(target))
    }

    @Test fun refusesTraversalAndUnsupportedCompressionWithoutExtracting() {
        for (name in listOf("../outside.txt", "entity.json")) {
            val file = Files.createTempFile("sle-invalid-package", ".sleprefab")
            ZipOutputStream(Files.newOutputStream(file)).use { zip ->
                zip.putNextEntry(ZipEntry(name))
                zip.write("{}".toByteArray())
                zip.closeEntry()
            }
            assertFails { PrefabPackages.open(file) }
        }
    }
}
