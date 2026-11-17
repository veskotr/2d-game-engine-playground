package sle.editor.model

import sle.editor.files.textureAssetPath

import kotlinx.serialization.json.*
import java.nio.file.Files
import kotlin.test.*

class EntityDocumentTest {
    @Test fun selectedTexturesUsePortableProjectPathsAndPreserveExternalPaths() {
        val root = java.nio.file.Path.of(System.getProperty("sle.repoRoot")).toAbsolutePath()
        assertEquals("assets/textures/my image 😀.png",
            textureAssetPath(root.resolve("assets/textures/../textures/my image 😀.png"), root))
        val external = root.parent.resolve("external/image.png")
        assertEquals(external.toString().replace('\\', '/'), textureAssetPath(external, root))
        val document = EntityDocument().addComponent("SpriteRenderer", buildJsonObject { put("texture", "") })
            .setValue("SpriteRenderer", listOf("texture"), JsonPrimitive(textureAssetPath(root.resolve("assets/textures/tile2.png"), root)))
        assertEquals(document, EntityDocument.parse(document.serialize()))
    }

    @Test fun newEntitiesHaveDistinctStableUuidKeysAndRenamingKeepsIdentity() {
        val first = EntityDocument()
        val second = EntityDocument()
        assertNotEquals(first.key, second.key)
        assertEquals(first.key, java.util.UUID.fromString(first.key).toString())
        val renamed = first.copy(name = "Player")
        assertEquals(first.key, renamed.key)
        assertEquals(first.key, EntityDocument.parse(renamed.serialize()).key)
    }

    @Test fun newDocumentReplacesIdentityAndClearsIncompleteEdits() {
        val state = EntityWorkspaceState(mapOf("Transform" to ComponentDefinition(buildJsonObject {
            put("rotation", 0.0)
        }, JsonObject(emptyMap()))))
        val originalKey = state.document.key
        state.document = state.document.copy(name = "Changed")
        state.editField("Transform", listOf("rotation"), JsonPrimitive(0.0), "-")
        state.error = "Previous error"
        state.newDocument()
        assertNotEquals(originalKey, state.document.key)
        assertEquals(state.document.key, java.util.UUID.fromString(state.document.key).toString())
        assertEquals("New Entity", state.document.name)
        assertTrue("Transform" in state.document.components)
        assertTrue(state.drafts.isEmpty())
        assertTrue(state.fieldErrors.isEmpty())
        assertNull(state.error)
        assertNull(state.file)
        assertFalse(state.dirty)
    }

    @Test fun existingNonUuidKeysSurviveRenameAndRoundTrip() {
        val loaded = EntityDocument.parse("""{"root":"legacy-player","entities":[{"key":"legacy-player","name":"Old","components":{}}]}""")
        val renamed = loaded.copy(name = "New")
        assertEquals("legacy-player", renamed.key)
        assertEquals(renamed, EntityDocument.parse(renamed.serialize()))
    }

    @Test fun suggestedFilenameUsesNameAndSanitizesWindowsNames() {
        assertEquals("Player.prefab.json", prefabFileName("Player"))
        assertEquals("Player.prefab.json", prefabFileName("Player.prefab.json"))
        assertEquals("bad_________name.prefab.json", prefabFileName("bad<>:\"/\\|?*name"))
        assertEquals("entity.prefab.json", prefabFileName(" ... "))
        assertEquals("_CON.prefab.json", prefabFileName("CON"))
        assertEquals("Player.prefab.json", prefabFileName(" Player. "))
    }

    @Test fun incompleteNumberStaysVisibleAndBlocksSaveUntilCorrected() {
        val state = EntityWorkspaceState(mapOf("Transform" to ComponentDefinition(buildJsonObject {
            put("rotation", 0.0)
        }, JsonObject(emptyMap()))))
        state.editField("Transform", listOf("rotation"), JsonPrimitive(0.0), "-")
        assertEquals("-", state.drafts["Transform/rotation"])
        assertTrue(state.fieldErrors.isNotEmpty())
        assertEquals(0.0, state.document.components["Transform"]!!.jsonObject["rotation"]!!.jsonPrimitive.double)
        state.editField("Transform", listOf("rotation"), JsonPrimitive(0.0), "1.25")
        assertTrue(state.fieldErrors.isEmpty())
        assertEquals(1.25, state.document.components["Transform"]!!.jsonObject["rotation"]!!.jsonPrimitive.double)
    }

    @Test fun editComponentAndRoundTripThroughFile() {
        val original = EntityDocument()
        val edited = original.addComponent("Transform", buildJsonObject {
            put("position", buildJsonObject { put("x", 0); put("y", 0) })
        }).setValue("Transform", listOf("position", "x"), JsonPrimitive(42))
        val path = Files.createTempDirectory("sle-entity-test").resolve("actor.prefab.json")
        try {
            EntityFiles.save(edited, path) { text ->
                val json = Json.parseToJsonElement(text).jsonObject
                assertEquals(edited.key, json["root"]!!.jsonPrimitive.content)
            }
            assertEquals(edited, EntityFiles.open(path))
            assertTrue(original.components.isEmpty())
            assertEquals(42, edited.components["Transform"]!!.jsonObject["position"]!!.jsonObject["x"]!!.jsonPrimitive.int)
            assertTrue(edited.removeComponent("Transform").components.isEmpty())
        } finally {
            Files.deleteIfExists(path)
            Files.delete(path.parent)
        }
    }

    @Test fun failedValidationPreservesExistingFile() {
        val path = Files.createTempFile("sle-existing", ".json")
        try {
            Files.writeString(path, "original")
            assertFailsWith<IllegalArgumentException> {
                EntityFiles.save(EntityDocument(), path) { throw IllegalArgumentException("Invalid radius") }
            }
            assertEquals("original", Files.readString(path))
        } finally { Files.delete(path) }
    }

    @Test fun refusesHierarchyAndUnsupportedMetadataRatherThanDroppingIt() {
        assertFailsWith<IllegalArgumentException> {
            EntityDocument.parse("""{"root":"a","entities":[{"key":"a","parent":"other","components":{}}]}""")
        }
        assertFailsWith<IllegalArgumentException> {
            EntityDocument.parse("""{"root":"a","prefabs":[],"entities":[{"key":"a","components":{}}]}""")
        }
        assertFailsWith<IllegalArgumentException> {
            EntityDocument.parse("""{"root":"a","entities":[{"key":"a","components":{}},{"key":"b"}]}""")
        }
    }

    @Test fun fieldsKeepTheirJsonTypesAndDuplicateComponentsAreRejected() {
        val document = EntityDocument().addComponent("Script", buildJsonObject {
            put("enabled", true)
            put("scriptAsset", "assets/scripts/player_move.lua")
            put("behaviours", JsonArray(emptyList()))
        })
        assertFailsWith<IllegalArgumentException> { document.addComponent("Script", JsonObject(emptyMap())) }
        assertEquals(document, EntityDocument.parse(document.serialize()))
        val edited = document.setValue("Script", listOf("enabled"), JsonPrimitive(false))
        assertFalse(edited.components["Script"]!!.jsonObject["enabled"]!!.jsonPrimitive.boolean)
        assertFailsWith<IllegalArgumentException> { document.setValue("Missing", listOf("x"), JsonPrimitive(1)) }
    }
}
