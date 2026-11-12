package sle.editor.model

import sle.editor.files.PrefabPackages

import kotlinx.serialization.json.*
import java.nio.file.Files
import kotlin.test.*

class SceneDocumentTest {
    @Test fun entityCapacityIsRejectedBeforePublishingInvalidState() {
        val scene=SceneWorkspaceState(emptyMap()) { it }
        repeat(4096) { scene.addEntity() }
        val selected=scene.selectedKey
        assertFails { scene.addEntity() }
        assertFails { scene.duplicateSelected() }
        val failure=assertFails { scene.importPrefab(java.nio.file.Path.of("not-read-at-capacity.sleprefab")) }
        assertTrue(failure.message.orEmpty().contains("4096"))
        assertEquals(4096,scene.entities.size)
        assertEquals(selected,scene.selectedKey)
        SceneDocument.parse(scene.serialize())
    }
    @Test fun deletionAndStructureUndoCannotSilentlyRetargetAnimators() {
        val scene=SceneWorkspaceState(emptyMap()) { it }
        scene.addEntity();val first=scene.selected!!
        scene.addEntity();val second=scene.selected!!
        scene.addEntity()
        first.document=first.document.addComponent("Animator",buildJsonObject { put("targetEntities",buildJsonObject { put("other",2) }) })
        scene.select(second.document.key)
        assertFails { scene.removeSelected() }
        assertEquals(3,scene.entities.size)
        first.document=first.document.setValue("Animator",listOf("targetEntities","other"),JsonPrimitive(scene.entities.last().document.key))
        scene.select(scene.entities.last().document.key)
        assertFails { scene.undoStructure() }
        assertEquals(3,scene.entities.size)
        assertTrue(scene.canUndoStructure)
    }
    @Test fun sceneStructureHistoryKeepsEntityEditsAndSelection() {
        val scene=SceneWorkspaceState(emptyMap()) { it }
        assertFalse(scene.dirty)
        scene.addEntity();val first=scene.selected!!
        first.rename("First")
        scene.addEntity();val second=scene.selected!!
        second.rename("Second")
        scene.select(first.document.key)
        assertEquals("First",scene.selected!!.document.name)
        assertTrue(first.canUndo)
        scene.select(second.document.key);scene.removeSelected()
        assertEquals(1,scene.entities.size)
        scene.undoStructure()
        assertEquals("Second",scene.selected!!.document.name)
        scene.redoStructure();assertEquals(1,scene.entities.size)
        scene.newScene();assertFalse(scene.dirty);assertFalse(scene.canUndoStructure)
    }

    @Test fun numericPrefabSelfReferencesBecomeTheNewEntityKey() {
        val original=EntityDocument().addComponent("Animator",buildJsonObject { put("targetEntities",buildJsonObject { put("self",1) }) })
        val copy=copySceneEntity(original,listOf(original.key))
        assertEquals(copy.key,copy.components.getValue("Animator").jsonObject.getValue("targetEntities").jsonObject.getValue("self").jsonPrimitive.content)
    }
    @Test fun scenesRoundTripMultipleEntitiesWithStableIdentityAndRejectUnsupportedContent() {
        val first=EntityDocument(name="First")
        val second=EntityDocument(name="Second")
        val scene=SceneDocument(name="Level",entities=listOf(first,second))
        assertEquals(scene,SceneDocument.parse(scene.serialize()))
        assertFails { SceneDocument.parse("""{"entities":[],"tiledMap":"map.json"}""") }
        assertFails { SceneDocument.parse("""{"entities":[{"key":"a","parent":"b","components":{}}]}""") }
        assertFails { SceneDocument(entities=listOf(first,first)).serialize() }
    }

    @Test fun copiedPrefabRemapsItsOwnReferencesWithoutChangingOtherTargets() {
        val original=EntityDocument().addComponent("Animator",buildJsonObject {
            put("targetEntities",buildJsonObject { put("self","PLACEHOLDER");put("other","other") })
        })
        val fixed=original.setValue("Animator",listOf("targetEntities","self"),JsonPrimitive(original.key))
        val copy=copySceneEntity(fixed)
        assertNotEquals(fixed.key,copy.key)
        val targets=copy.components.getValue("Animator").jsonObject.getValue("targetEntities").jsonObject
        assertEquals(copy.key,targets.getValue("self").jsonPrimitive.content)
        assertEquals("other",targets.getValue("other").jsonPrimitive.content)
    }

    @Test fun scenePackagesRetainAllEntityTexturesAfterMovingAndDeletingOriginals() {
        val folder=Files.createTempDirectory("sle-scene-test")
        val image=folder.resolve("image.png").also { Files.write(it,byteArrayOf(1,2,3)) }
        val entity=EntityDocument().addComponent("SpriteRenderer",buildJsonObject { put("texture",image.toString()) })
        val scene=SceneDocument(name="Portable scene",entities=listOf(entity,EntityDocument()))
        val path=folder.resolve("level.slescene")
        PrefabPackages.saveScene(scene.serialize(),emptyMap(),path) {}
        val moved=folder.resolve("moved.slescene")
        Files.move(path,moved);Files.delete(image)
        val loaded=PrefabPackages.openScene(moved)
        val restored=SceneDocument.parse(loaded.document.toString())
        assertEquals(scene.key,restored.key)
        assertEquals(2,restored.entities.size)
        val texture=restored.entities.first().components.getValue("SpriteRenderer").jsonObject.getValue("texture").jsonPrimitive.content
        assertContentEquals(byteArrayOf(1,2,3),Files.readAllBytes(java.nio.file.Path.of(texture)))
        assertFails { PrefabPackages.open(moved) }
    }
}
