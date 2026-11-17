package sle.editor.model

import sle.editor.project.ProjectDocumentKind
import sle.editor.project.ProjectDocuments
import sle.editor.project.ProjectFiles
import sle.editor.ui.EditorFileLocations
import sle.editor.ui.inspector.*
import sle.editor.viewport.Vec2

import kotlinx.serialization.json.*
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.*

class SceneAuthoringUxTest {
    private val catalog=mapOf(
        "Transform" to ComponentDefinition(Json.parseToJsonElement("""{"position":{"x":0,"y":0},"rotation":0,"scale":{"x":1,"y":1}}""").jsonObject,JsonObject(emptyMap())),
        "SpriteRenderer" to ComponentDefinition(Json.parseToJsonElement("""{"size":{"x":1,"y":1},"offset":{"x":0,"y":0},"region":{"uv":{"x":0,"y":0,"z":1,"w":1}},"color":{"x":1,"y":1,"z":1,"w":1},"visible":true,"layer":0,"texture":""}""").jsonObject,JsonObject(emptyMap())),
    )
    private fun scene()=SceneWorkspaceState(catalog) { it }
    private fun position(state: EntityWorkspaceState)=ScenePose.from(state.document).position

    @Test fun deleteClearsSelectionAndRepeatedDeleteCannotRemoveTheNextSubtree() {
        val scene=scene()
        scene.addEntity();val survivor=scene.selectedKey!!
        scene.addEntity();val root=scene.selectedKey!!
        scene.addChild();val child=scene.selectedKey!!
        scene.addChild();val grandchild=scene.selectedKey!!
        scene.select(root)
        val before=scene.document()
        scene.removeSelected()
        assertEquals(listOf(survivor),scene.entities.map { it.document.key })
        assertNull(scene.selectedKey);assertNull(scene.selected)
        scene.removeSelected()
        assertEquals(listOf(survivor),scene.entities.map { it.document.key })
        scene.undoStructure()
        assertEquals(before,scene.document());assertEquals(root,scene.selectedKey)
        assertEquals(root,scene.parents[child]);assertEquals(child,scene.parents[grandchild])
        scene.redoStructure();assertNull(scene.selectedKey)
        scene.select("missing");assertNull(scene.selectedKey)
    }

    @Test fun newVisibleComponentsUsePracticalSizesAndExistingDimensionsSurviveReopen() {
        val shapes=mapOf(
            "BoxCollider" to "{\"size\":{\"x\":1,\"y\":1}}",
            "CircleCollider" to "{\"radius\":0.5}",
            "BoxZone" to "{\"size\":{\"x\":1,\"y\":1}}",
            "CircleZone" to "{\"radius\":0.5}"
        ).mapValues { ComponentDefinition(Json.parseToJsonElement(it.value).jsonObject,JsonObject(emptyMap())) }
        val scene=SceneWorkspaceState(catalog+shapes) { it }
        scene.addEntity();val state=scene.selected!!
        for(type in listOf("SpriteRenderer")+shapes.keys) state.addComponent(type)
        val reopened=SceneDocument.parse(scene.serialize()).entities.single()
        for(type in listOf("SpriteRenderer","BoxCollider","BoxZone")) {
            val size=reopened.components.getValue(type).jsonObject.getValue("size").jsonObject
            assertEquals(64.0,size.getValue("x").jsonPrimitive.double)
            assertEquals(64.0,size.getValue("y").jsonPrimitive.double)
        }
        for(type in listOf("CircleCollider","CircleZone")) assertEquals(32.0,reopened.components.getValue(type).jsonObject.getValue("radius").jsonPrimitive.double)
        state.setValue("SpriteRenderer",listOf("size","x"),JsonPrimitive(17))
        val source=scene.serialize()
        scene.openResolved(source,emptyMap(),Path.of("existing.scene.json"))
        assertEquals(17,scene.selected!!.document.components.getValue("SpriteRenderer").jsonObject.getValue("size").jsonObject.getValue("x").jsonPrimitive.int)
        assertEquals(1,catalog.getValue("SpriteRenderer").defaults.getValue("size").jsonObject.getValue("x").jsonPrimitive.int)
    }

    @Test fun creationMenusUseClickedWorldPositionForRootAndTransformedChildren() {
        val scene=scene()
        scene.addEntity();val parent=scene.selectedKey!!
        scene.selected!!.document=scene.selected!!.document.withPose(ScenePose(Vec2(50.0,20.0),0.4,Vec2(2.0,3.0)))
        val point=Vec2(180.0,-70.0)
        fun create(kind: String, destination: String) {
            val menu=sle.editor.ui.sceneCreationMenu(scene,true,position=point)
            menu.single { it.label=="Create" }.children.single { it.label==kind }.children.single { it.label==destination }.action()
            val pose=ScenePose.from(scene.worldDocument(scene.selectedKey!!))
            assertEquals(point.x,pose.position.x,1e-8);assertEquals(point.y,pose.position.y,1e-8)
        }
        create("Entity","As Child");assertEquals(parent,scene.parents[scene.selectedKey])
        scene.select(parent);create("Camera","As Child");assertEquals(parent,scene.parents[scene.selectedKey])
        create("Sprite","At Scene Root");assertNull(scene.parents[scene.selectedKey])
        assertTrue("SpriteRenderer" in scene.selected!!.document.components)
        create("Entity","At Scene Root");assertNull(scene.parents[scene.selectedKey])
        val before=scene.document()
        sle.editor.ui.sceneCreationMenu(scene,false,position=point).single { it.label=="Delete" }.action()
        assertEquals(before,scene.document())
    }

    @Test fun sharedDeleteCommandRejectsPlaybackAndModalEditing() {
        val scene=scene();scene.addEntity();val before=scene.document()
        assertFalse(sle.editor.ui.deleteSceneSelection(scene,editable=false,modal=false))
        assertFalse(sle.editor.ui.deleteSceneSelection(scene,editable=true,modal=true))
        assertEquals(before,scene.document())
        assertTrue(sle.editor.ui.deleteSceneSelection(scene,editable=true,modal=false))
        assertNull(scene.selectedKey);assertTrue(scene.entities.isEmpty())
        assertFalse(sle.editor.ui.deleteSceneSelection(scene,editable=true,modal=false))
        scene.undoStructure();assertEquals(before,scene.document())
    }

    @Test fun droppedPrefabIsPlacedAtTheDropPointAndDeletable() {
        val scene=scene()
        val prefab=EntityDocument(key="crate",name="Crate").withPose(ScenePose(position=Vec2(5.0,6.0)))
        scene.insertPrefab(prefab,emptyMap(),Vec2(120.0,-40.0))
        assertEquals(Vec2(120.0,-40.0),position(scene.selected!!))
        assertNotEquals("crate",scene.selectedKey) // instances get their own identity
        scene.insertPrefab(prefab,emptyMap())
        assertEquals(Vec2(5.0,6.0),position(scene.selected!!))
        scene.removeSelected()
        assertEquals(1,scene.entities.size)
        scene.undoStructure()
        assertEquals(2,scene.entities.size)
    }

    @Test fun droppedImageBecomesSpriteAtNativeSize() {
        val scene=scene()
        scene.addSprite("C:/project/Assets/Textures/hero.png","hero",Vec2(48.0,64.0),Vec2(10.0,20.0))
        val entity=scene.selected!!.document
        assertEquals("hero",entity.name)
        val sprite=entity.components.getValue("SpriteRenderer").jsonObject
        assertEquals("C:/project/Assets/Textures/hero.png",sprite.getValue("texture").jsonPrimitive.content)
        assertEquals(48.0,sprite.getValue("size").jsonObject.getValue("x").jsonPrimitive.double)
        assertEquals(Vec2(10.0,20.0),position(scene.selected!!))
        assertTrue(scene.canUndoStructure)
    }

    @Test fun projectSaveAdoptsAssetsFromExtractedPackagesWithoutDuplicates() {
        val project=ProjectFiles.create(Files.createTempDirectory("sle-adopt-").resolve("Game"),"Game")
        val extracted=Files.createTempDirectory("sle-prefab-").resolve("3f2a.png")
        Files.write(extracted,byteArrayOf(9,8,7))
        val texture=extracted.toString().replace('\\','/')
        val source=buildJsonObject {
            put("entities",buildJsonArray { add(buildJsonObject {
                put("key","crate");put("name","Crate")
                put("components",buildJsonObject {
                    put("SpriteRenderer",buildJsonObject { put("texture",texture) })
                    put("Script",buildJsonObject { put("variables",buildJsonObject { put("texture",texture) }) })
                })
            }) })
        }.toString()
        val store=ProjectDocuments(project)
        val adopted=store.adoptExternalAssets(source,mapOf(extracted to "crate.png"))
        val copy=project.assets.resolve("Textures/crate.png")
        assertEquals(copy.toRealPath(),adopted.moved.getValue(extracted.toAbsolutePath().normalize()).toRealPath())
        assertContentEquals(byteArrayOf(9,8,7),Files.readAllBytes(copy))
        val components=Json.parseToJsonElement(adopted.source).jsonObject.getValue("entities").jsonArray.single().jsonObject.getValue("components").jsonObject
        assertNotEquals(texture,components.getValue("SpriteRenderer").jsonObject.getValue("texture").jsonPrimitive.content)
        assertEquals(texture,components.getValue("Script").jsonObject.getValue("variables").jsonObject.getValue("texture").jsonPrimitive.content)
        // Previously failed with "Asset is outside this project; import it first".
        store.save(project.assets.resolve("Scenes/level.scene.json"),ProjectDocumentKind.Scene,adopted.source,adopted.assets,null) {}
        store.adoptExternalAssets(source,mapOf(extracted to "crate.png"))
        assertEquals(listOf("crate.png","crate.png.slemeta"),Files.list(project.assets.resolve("Textures")).use { it.map { p -> p.fileName.toString() }.sorted().toList() })
    }

    @Test fun relocationRewritesOnlyAssetFieldsAndKeepsSavedState() {
        val state=EntityWorkspaceState(catalog)
        val old=Path.of("old/tex.png").toAbsolutePath().normalize();val new=Path.of("new/tex.png").toAbsolutePath().normalize()
        state.resetTo(state.document.copy(components=buildJsonObject {
            put("SpriteRenderer",buildJsonObject { put("texture",old.toString()) })
            put("Script",buildJsonObject { put("variables",buildJsonObject { put("texture",old.toString()) }) })
        }),mapOf(old to "tex.png"))
        state.relocateAssets(mapOf(old to new))
        assertEquals(new.toString().replace('\\','/'),state.document.components.getValue("SpriteRenderer").jsonObject.getValue("texture").jsonPrimitive.content)
        assertEquals(old.toString(),state.document.components.getValue("Script").jsonObject.getValue("variables").jsonObject.getValue("texture").jsonPrimitive.content)
        assertEquals(mapOf(new to "tex.png"),state.assets)
        assertFalse(state.dirty)
    }

    @Test fun spriteInspectorShowsReadableFieldsInUsefulOrder() {
        val sprite=catalog.getValue("SpriteRenderer").defaults
        assertEquals(listOf("texture","color","size","offset","layer","visible","region"),orderedFields("SpriteRenderer",sprite.keys))
        assertTrue(isStructuredGroup("region",sprite.getValue("region")))
        assertFalse(isStructuredGroup("variables",buildJsonObject { put("speed",buildJsonObject { put("x",1) }) }))
        assertFalse(isStructuredGroup("size",sprite.getValue("size")))
        val color=propertyDefinition("SpriteRenderer",listOf("color"),sprite.getValue("color"))
        assertEquals(listOf("R","G","B","A"),color.axisLabels);assertTrue(color.isColor)
        val uv=propertyDefinition("SpriteRenderer",listOf("region","uv"),sprite.getValue("region").jsonObject.getValue("uv"))
        assertEquals("UV rect",uv.displayLabel);assertEquals(listOf("U0","V0","U1","V1"),uv.axisLabels)
    }

    @Test fun sceneViewResizesTheEditedShapeButCenterDragStillMovesTheEntity() {
        val catalog=this.catalog+("BoxCollider" to ComponentDefinition(Json.parseToJsonElement("""{"size":{"x":1,"y":1},"offset":{"x":0,"y":0}}""").jsonObject,JsonObject(emptyMap())))
        val scene=SceneWorkspaceState(catalog) { it }
        scene.addEntity();val state=scene.selected!!
        state.addComponent("BoxCollider");state.setValue("BoxCollider",listOf("size"),buildJsonObject { put("x",4);put("y",2) })
        val camera=sle.editor.viewport.PreviewCamera(Vec2(-10.0,-10.0),Vec2(20.0,20.0),400,400)
        val canvas=java.awt.Canvas()
        fun drag(from: Vec2,to: Vec2) {
            val gizmos=sle.editor.viewport.ViewportGizmos(state,true,scene)
            val a=camera.screen(from);val b=camera.screen(to)
            gizmos.press(java.awt.event.MouseEvent(canvas,java.awt.event.MouseEvent.MOUSE_PRESSED,0,0,a.x.toInt(),a.y.toInt(),1,false,1),camera)
            gizmos.move(java.awt.event.MouseEvent(canvas,java.awt.event.MouseEvent.MOUSE_DRAGGED,0,0,b.x.toInt(),b.y.toInt(),1,false,0))
            gizmos.finish()
        }
        state.selectedTarget="BoxCollider" // the component editor's tool must not leak into the Scene view
        drag(Vec2(0.0,0.0),Vec2(3.0,0.0))
        assertEquals(3.0,position(state).x,0.2)
        assertEquals(4.0,state.document.components.getValue("BoxCollider").jsonObject.getValue("size").jsonObject.getValue("x").jsonPrimitive.double)
        state.sceneTarget="BoxCollider"
        drag(Vec2(5.0,0.0),Vec2(7.0,0.0)) // right edge handle of the 4-wide box centered at x=3
        assertEquals(6.0,state.document.components.getValue("BoxCollider").jsonObject.getValue("size").jsonObject.getValue("x").jsonPrimitive.double,0.2)
        assertEquals(3.0,position(state).x,0.2)
        drag(Vec2(3.0,0.0),Vec2(5.0,0.0)) // center still moves the entity while the collider is edited
        assertEquals(5.0,position(state).x,0.2)
    }

    @Test fun dialogsNeverStartInTemporaryPackageFolders() {
        val extracted=Files.createTempDirectory("sle-prefab-").resolve("scene.slescene")
        val folder=EditorFileLocations.folder(EditorFileLocations.Kind.Scene,extracted).toPath().toAbsolutePath().normalize()
        assertFalse(folder.startsWith(Path.of(System.getProperty("java.io.tmpdir")).toAbsolutePath().normalize()))
        assertTrue(Files.isDirectory(folder))
    }
}
