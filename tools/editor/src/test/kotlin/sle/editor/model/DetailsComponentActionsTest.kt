package sle.editor.model

import kotlinx.serialization.json.*
import kotlin.test.*

class DetailsComponentActionsTest {
    private val definition = decodeComponentCatalog("""
        {"Animator":{"defaults":{"speed":1,"blend":{"x":0,"y":0},"targetEntities":{}},
          "enums":{},"fields":[{"path":["blend","y"],"readOnly":true}]}}
    """.trimIndent()).getValue("Animator")

    private fun owner() = EntityWorkspaceState(mapOf("Animator" to definition)).apply {
        val root = EntityDocument().addComponent("Animator", objectValue(
            """{"speed":2,"blend":{"x":3,"y":4,"legacy":5},"targetEntities":{"hero":"local"},"legacy":6}"""
        ))
        val child = EntityDocument(key = "local")
        val template = PrefabDocument(root.key, listOf(root, child), mapOf(child.key to root.key))
        openResolved(template.serialize(), emptyMap(), java.nio.file.Path.of("test.sleprefab"))
    }

    @Test fun copyAndResetRespectProtectedAndLegacyPaths() {
        val owner = owner()
        val actions = DetailsPropertyActions(validatePrefab = {
            val candidate = PrefabDocument.parse(it)
            assertEquals(owner.prefabDocument().parents, candidate.parents)
            assertEquals(owner.prefabDocument().entities.last(), candidate.entities.last())
            it
        })
        val before = owner.document
        assertEquals(objectValue("""{"component":"Animator","values":{"speed":2,"blend":{"x":3}}}"""),
            Json.parseToJsonElement(actions.copyComponent(owner, "Animator")))
        actions.replaceComponent(owner, "Animator")
        val reset = owner.document
        assertEquals(objectValue("""{"speed":1,"blend":{"x":0,"y":4,"legacy":5},"targetEntities":{"hero":"local"},"legacy":6}"""),
            reset.components["Animator"])
        owner.undo()
        assertEquals(before, owner.document)
        owner.redo()
        assertEquals(reset, owner.document)
    }

    @Test fun pasteStagesWholeCandidateAndRejectsUnknownKeys() {
        val owner = owner()
        val before = owner.document
        val actions = DetailsPropertyActions(validatePrefab = { error("Native rejection") })
        assertFailsWith<IllegalArgumentException> {
            actions.replaceComponent(owner, "Animator", """{"component":"Animator","values":{"blend":{"foreign":9}}}""")
        }
        assertFailsWith<IllegalArgumentException> {
            actions.replaceComponent(owner, "Animator", """{"component":"Camera2D","values":{"speed":3}}""")
        }
        assertFailsWith<IllegalArgumentException> {
            actions.replaceComponent(owner, "Animator", """{"component":"Animator","values":{"blend":{"y":9}}}""")
        }
        assertEquals("Native rejection", assertFailsWith<IllegalStateException> {
            actions.replaceComponent(owner, "Animator", """{"component":"Animator","values":{"speed":3,"blend":{"x":8}}}""")
        }.message)
        actions.replaceComponent(owner, "Animator", """{"component":"Animator","values":{"speed":2}}""")
        assertSame(before, owner.document)
    }

    @Test fun transformPastePreservesParentAndAddsMissingDefaultOnReset() {
        val transform = ComponentDefinition(objectValue("""{"position":{"x":0,"y":0},"rotation":0}"""), objectValue("{}"))
        val scene = SceneWorkspaceState(mapOf("Transform" to transform)) { it }
        val parent = EntityDocument(key = "parent").addComponent("Transform", transform.defaults)
        val child = EntityDocument(key = "child").addComponent("Transform", objectValue("""{"position":{"x":2,"y":3}}"""))
        val source = SceneDocument(entities = listOf(parent, child), parents = mapOf(child.key to parent.key))
        scene.openResolved(source.serialize(), emptyMap(), java.nio.file.Path.of("test.slescene"))
        val target = checkNotNull(scene.entity(child.key))
        val actions = DetailsPropertyActions()
        actions.replaceComponent(target, "Transform", """{"component":"Transform","values":{"position":{"x":8}}}""")
        assertEquals(objectValue("""{"position":{"x":8,"y":3}}"""), target.document.components["Transform"])
        assertEquals(source.parents, scene.document().parents)
        assertEquals(parent, scene.document().entities.first())
        assertEquals("Paste Transform", scene.history.undoLabel)
        actions.replaceComponent(target, "Transform")
        assertEquals(transform.defaults, target.document.components["Transform"])
        scene.undo()
        scene.undo()
        assertEquals(source.entities, scene.document().entities)
    }

    @Test fun rigidBodyResetPreservesLegacyMass() {
        val rigidBody = ComponentDefinition(objectValue("""{"gravityScale":1}"""), objectValue("{}"))
        val owner = EntityWorkspaceState(mapOf("RigidBody" to rigidBody)).apply {
            resetTo(EntityDocument().addComponent("RigidBody", objectValue("""{"gravityScale":2,"mass":8}""")))
        }
        val actions = DetailsPropertyActions(validatePrefab = { it })
        assertFalse(actions.copyComponent(owner, "RigidBody").contains("mass"))
        actions.replaceComponent(owner, "RigidBody")
        assertEquals(objectValue("""{"gravityScale":1,"mass":8}"""), owner.document.components["RigidBody"])
    }

    @Test fun pasteResolvesAssetsInsideMapsAndBehaviourArrays() {
        val catalog = mapOf(
            "Animator" to ComponentDefinition(objectValue("""{"stateClipMap":{}}"""), objectValue("{}")),
            "Script" to ComponentDefinition(objectValue("""{"behaviours":[]}"""), objectValue("{}")),
        )
        val owner = EntityWorkspaceState(catalog).apply {
            addComponent("Animator")
            addComponent("Script")
        }
        val before = owner.document
        val actions = DetailsPropertyActions(validatePrefab = { it })
        for ((component, envelope) in listOf(
            "Animator" to """{"component":"Animator","values":{"stateClipMap":{"variables":"missing.anim.json"}}}""",
            "Script" to """{"component":"Script","values":{"behaviours":[{"key":"test","asset":"missing.lua"}]}}""",
        )) {
            assertEquals("Unresolved asset", assertFailsWith<IllegalArgumentException> {
                actions.replaceComponent(owner, component, envelope) { throw IllegalArgumentException("Unresolved asset") }
            }.message)
            assertSame(before, owner.document)
        }
    }

    private fun objectValue(text: String) = Json.parseToJsonElement(text).jsonObject
}
