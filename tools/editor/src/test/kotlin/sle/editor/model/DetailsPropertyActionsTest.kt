package sle.editor.model

import kotlinx.serialization.json.*
import kotlin.test.*
import sle.editor.ui.inspector.propertyDefinition

class DetailsPropertyActionsTest {
    private val state = EntityWorkspaceState(emptyMap()).apply {
        resetTo(EntityDocument().addComponent("Example", buildJsonObject {
            put("vector", buildJsonObject { put("x", 2); put("y", 7) })
        }))
    }
    private val property = propertyDefinition("Example", listOf("vector", "x"), JsonPrimitive(2))

    @Test fun pasteChangesOnlyTheAxisAndCanBeUndoneAndRedone() {
        val actions = DetailsPropertyActions(validatePrefab = { assertTrue(it.contains("Example")); it })
        actions.replace(state, property, "4", "Paste")
        assertEquals(JsonPrimitive(4), value("x"))
        assertEquals(JsonPrimitive(7), value("y"))
        state.undo()
        assertEquals(JsonPrimitive(2), value("x"))
        state.redo()
        assertEquals(JsonPrimitive(4), value("x"))
    }

    @Test fun spriteLayerSelectionCanBeUndoneToAnUnlabeledValue() {
        val sprite = EntityDocument().addComponent("SpriteRenderer", buildJsonObject { put("layer", -4) })
        val scene = SceneWorkspaceState(emptyMap()) { it }
        scene.openResolved(SceneDocument(entities = listOf(sprite)).serialize(), emptyMap(),
            java.nio.file.Path.of("test.slescene"))
        val target = checkNotNull(scene.entity(sprite.key))
        fun layer(document: SceneDocument) =
            document.entities.single().components.getValue("SpriteRenderer").jsonObject.getValue("layer").jsonPrimitive.int

        target.setValue("SpriteRenderer", listOf("layer"), JsonPrimitive(9))
        assertEquals(9, layer(scene.document()))
        scene.undo()
        assertEquals(-4, layer(SceneDocument.parse(scene.document().serialize())))
        scene.redo()
        assertEquals(9, layer(scene.document()))
    }

    @Test fun rejectionAndNoOpPreserveRedo() {
        val actions = DetailsPropertyActions(validatePrefab = { error("Native rejection") })
        state.setValue("Example", listOf("vector", "x"), JsonPrimitive(3))
        state.undo()
        assertFailsWith<IllegalArgumentException> { actions.replace(state, property, "\"4\"", "Paste") }
        assertEquals("Native rejection", assertFailsWith<IllegalStateException> {
            actions.replace(state, property, "4", "Paste")
        }.message)
        actions.replace(state, property, "2", "Paste")
        state.redo()
        assertEquals(JsonPrimitive(3), value("x"))
    }

    @Test fun scenePasteValidatesExpandedDestinationAndKeepsUnrelatedDrafts() {
        var validated: SceneDocument? = null
        val scene = SceneWorkspaceState(emptyMap()) { source ->
            validated = SceneDocument.parse(source)
            source
        }
        val other = EntityDocument(name = "Other")
        val source = SceneDocument(entities = listOf(state.document, other),
            parents = mapOf(other.key to state.document.key))
        scene.openResolved(source.serialize(), emptyMap(), java.nio.file.Path.of("test.slescene"))
        val target = checkNotNull(scene.entity(state.document.key))
        target.editField("Example", listOf("vector", "y"), JsonPrimitive(7), "invalid")
        val before = target.drafts
        DetailsPropertyActions().replace(target, property, "8", "Paste")
        assertEquals(source.parents, validated?.parents)
        assertEquals(other, validated?.entities?.last())
        assertEquals(target.drafts, before)
        assertEquals("Paste X", scene.history.undoLabel)
        scene.undo()
        assertEquals(source.entities, scene.document().entities)
        scene.redo()
        assertEquals(validated?.entities, scene.document().entities)
    }

    @Test fun resourceResolutionIsStagedAndCanResetToBlank() {
        val catalog = ComponentDefinition(buildJsonObject { put("texture", "") }, buildJsonObject {})
        val state = EntityWorkspaceState(mapOf("SpriteRenderer" to catalog)).apply { addComponent("SpriteRenderer") }
        fun property() = propertyDefinition("SpriteRenderer", listOf("texture"),
            state.document.components.getValue("SpriteRenderer").jsonObject.getValue("texture"), catalog = catalog)
        val actions = DetailsPropertyActions(validatePrefab = { it })
        val before = state.document
        assertFailsWith<IllegalStateException> {
            actions.replace(state, property(), "\"elsewhere.png\"", "Paste", resolveProjectAsset = { error("Foreign asset") })
        }
        assertSame(before, state.document)
        actions.replace(state, property(), "\"project-asset://id\"", "Paste", resolveProjectAsset = { "/project/asset.png" })
        assertEquals(JsonPrimitive("/project/asset.png"), property().value)
        actions.replace(state, property(), property().defaultValue.toString(), "Reset",
            resolveProjectAsset = { error("Blank needs no resolution") })
        assertEquals(JsonPrimitive(""), property().value)
        assertTrue(state.assets.isEmpty())
    }

    @Test fun fileBackedTemplateUsesStandaloneAssetPathsInsideAnActiveProject() {
        val folder = java.nio.file.Files.createTempDirectory("sle-details-assets")
        val asset = java.nio.file.Files.writeString(folder.resolve("local.png"), "texture")
        val entity = EntityDocument().addComponent("SpriteRenderer", buildJsonObject { put("texture", "") })
        val owner = EntityWorkspaceState(emptyMap())
        owner.openResolved(entity.serialize(), emptyMap(), folder.resolve("test.sleprefab"))
        val property = propertyDefinition("SpriteRenderer", listOf("texture"), JsonPrimitive(""))
        DetailsPropertyActions(validatePrefab = { it }).replace(owner, property, "\"local.png\"", "Paste",
            resolveProjectAsset = { error("Portable template uses standalone paths") })
        assertEquals(JsonPrimitive(asset.toString().replace('\\', '/')),
            owner.document.components.getValue("SpriteRenderer").jsonObject.getValue("texture"))
    }

    @Test fun linkedReferenceMustBePersistableAsAnInstanceOverride() {
        val scene = SceneWorkspaceState(emptyMap()) { it }
        scene.addEntity()
        val outside = checkNotNull(scene.selectedKey)
        val root = EntityDocument(key = "root").addComponent("Animator", buildJsonObject {
            put("targetEntities", buildJsonObject { put("local", "root") })
        })
        val child = EntityDocument(key = "child")
        scene.insertLinkedPrefab(PrefabDocument(root.key, listOf(root, child), mapOf(child.key to root.key)),
            "asset-id", emptyMap(), null)
        val state = checkNotNull(scene.selected)
        fun property() = propertyDefinition("Animator", listOf("targetEntities"),
            state.document.components.getValue("Animator").jsonObject.getValue("targetEntities"))
        val before = state.document
        val history = scene.history.undoLabels
        val actions = DetailsPropertyActions()
        assertFails {
            actions.replace(state, property(), buildJsonObject { put("local", outside) }.toString(), "Paste")
        }
        assertSame(before, state.document)
        assertEquals(history, scene.history.undoLabels)
        val childKey = "${state.document.key}/child"
        actions.replace(state, property(), buildJsonObject { put("local", childKey) }.toString(), "Paste")
        assertEquals(JsonPrimitive(childKey), property().value.jsonObject["local"])
    }

    private fun value(axis: String) = state.document.components.getValue("Example").jsonObject
        .getValue("vector").jsonObject.getValue(axis)
}
