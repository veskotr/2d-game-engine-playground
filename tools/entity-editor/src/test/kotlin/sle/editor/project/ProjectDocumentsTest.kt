package sle.editor.project

import kotlinx.serialization.json.*
import sle.editor.project.assets.*
import java.nio.file.Files
import kotlin.test.*

class ProjectDocumentsTest {
    private fun project()=ProjectFiles.create(Files.createTempDirectory("sle-doc-").resolve("Game"),"Game")
    private fun source(texture: String)=buildJsonObject {
        put("entities",buildJsonArray { add(buildJsonObject {
            put("key","actor");put("name",texture)
            put("components",buildJsonObject {
                put("SpriteRenderer",buildJsonObject { put("texture",texture) })
                put("Script",buildJsonObject { put("variables",buildJsonObject { put("texture",texture) }) })
            })
        }) })
    }.toString()
    @Test fun sharesAssetsAndResolvesAfterRelocationWithoutRewritingNamesOrVariables() {
        val p=project();val texture=p.assets.resolve("Textures/shared.png");Files.write(texture,byteArrayOf(1,2,3))
        val store=ProjectDocuments(p);val text=source(texture.toString().replace('\\','/'))
        val first=store.save(p.assets.resolve("Scenes/a.scene.json"),ProjectDocumentKind.Scene,text,emptyMap(),null) {}
        store.save(p.assets.resolve("Scenes/b.scene.json"),ProjectDocumentKind.Scene,text,emptyMap(),null) {}
        val raw=Files.readString(first.path)
        assertTrue(raw.contains("project-asset://"))
        assertEquals(2,Files.list(p.assets.resolve("Textures")).use { it.count() }.toInt()) // payload plus metadata
        val moved=p.root.resolveSibling("Relocated");Files.move(p.root,moved)
        val reopened=ProjectFiles.open(moved.resolve(p.file.fileName))
        val loaded=ProjectDocuments(reopened).open(reopened.assets.resolve("Scenes/a.scene.json"))
        val entity=Json.parseToJsonElement(loaded.source).jsonObject.getValue("entities").jsonArray.single().jsonObject
        assertEquals(reopened.assets.resolve("Textures/shared.png").toString().replace('\\','/'),entity.getValue("components").jsonObject.getValue("SpriteRenderer").jsonObject.getValue("texture").jsonPrimitive.content)
        assertEquals(texture.toString().replace('\\','/'),entity.getValue("name").jsonPrimitive.content)
        assertEquals(texture.toString().replace('\\','/'),entity.getValue("components").jsonObject.getValue("Script").jsonObject.getValue("variables").jsonObject.getValue("texture").jsonPrimitive.content)
    }
    @Test fun externalChangesAndFailedValidationPreserveDisk() {
        val p=project();val store=ProjectDocuments(p);val path=p.assets.resolve("Prefabs/a.prefab.json")
        val opened=store.save(path,ProjectDocumentKind.Prefab,"{\"root\":\"a\",\"entities\":[]}",emptyMap(),null) {}
        val before=Files.readString(path)
        assertFails { store.save(path,opened.kind,"{}",emptyMap(),opened) { error("invalid") } }
        assertEquals(before,Files.readString(path))
        Files.writeString(path,before+" ")
        assertFails { store.save(path,opened.kind,"{}",emptyMap(),opened) {} }
        assertEquals(before+" ",Files.readString(path))
        Files.delete(path)
        assertFails { store.save(path,opened.kind,"{}",emptyMap(),opened) {} }
        assertFalse(Files.exists(path))
    }
    @Test fun missingDuplicateAndOutsideAssetsCannotBeSilentlyBound() {
        val p=project();val store=ProjectDocuments(p);val asset=p.assets.resolve("Textures/a.png");Files.writeString(asset,"image")
        val saved=store.save(p.assets.resolve("Scenes/a.scene.json"),ProjectDocumentKind.Scene,source(asset.toString()),emptyMap(),null) {}
        val duplicate=p.assets.resolve("Textures/b.png");Files.copy(asset,duplicate);Files.copy(AssetIdentity.sidecar(asset),AssetIdentity.sidecar(duplicate))
        assertFails { store.open(saved.path) }
        Files.delete(duplicate);Files.delete(AssetIdentity.sidecar(duplicate));Files.delete(asset)
        assertFails { store.open(saved.path) }
        val external=Files.createTempFile("outside",".png")
        assertFails { store.save(saved.path,ProjectDocumentKind.Scene,source(external.toString()),emptyMap(),saved) {} }
        assertFails { store.save(p.root.resolve("outside.scene.json"),ProjectDocumentKind.Scene,"{}",emptyMap(),null) {} }
    }
    @Test fun invalidDestinationMetadataCannotReplacePayload() {
        val p=project();val store=ProjectDocuments(p);val path=p.assets.resolve("Scenes/a.scene.json")
        val saved=store.save(path,ProjectDocumentKind.Scene,"{}",emptyMap(),null) {}
        Files.writeString(AssetIdentity.sidecar(path),"broken metadata")
        val before=Files.readString(path)
        assertFails { store.save(path,ProjectDocumentKind.Scene,"{\"entities\":[]}",emptyMap(),saved) {} }
        assertEquals(before,Files.readString(path))
    }
    @Test fun fileArrivingDuringValidationIsPreserved() {
        val p=project();val store=ProjectDocuments(p);val path=p.assets.resolve("Scenes/a.scene.json")
        assertFails { store.save(path,ProjectDocumentKind.Scene,"{}",emptyMap(),null) { Files.writeString(path,"external arrival") } }
        assertEquals("external arrival",Files.readString(path))
    }

}
