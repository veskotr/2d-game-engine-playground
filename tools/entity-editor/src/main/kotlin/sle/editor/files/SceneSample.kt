package sle.editor.files

import sle.editor.model.ComponentDefinition
import sle.editor.model.EntityWorkspaceState
import sle.editor.model.SceneWorkspaceState

import kotlinx.serialization.json.*
import java.nio.file.Files
import java.nio.file.Path

internal fun createStarterScene(scene: SceneWorkspaceState, catalog: Map<String,ComponentDefinition>, folder: Path): Path {
    Files.createDirectories(folder)
    fun vector(x: Double,y: Double) = buildJsonObject { put("x",x);put("y",y) }
    fun configure(state: EntityWorkspaceState,name: String,x: Double,y: Double,width: Double,height: Double,dynamic: Boolean) {
        state.rename(name)
        state.setValue("Transform",listOf("position"),vector(x,y))
        state.addComponent("SpriteRenderer");state.addComponent("BoxCollider");state.addComponent("RigidBody")
        state.setValue("SpriteRenderer",listOf("size"),vector(width,height))
        state.setValue("BoxCollider",listOf("size"),vector(width,height))
        state.setValue("RigidBody",listOf("bodyType"),JsonPrimitive(if(dynamic) "Dynamic" else "Static"))
        val image=state.importAsset(Path.of("assets/textures/tile2.png"))
        state.setValue("SpriteRenderer",listOf("texture"),JsonPrimitive(image.toString().replace('\\','/')))
    }
    scene.newScene();scene.rename("Starter Scene")
    scene.addEntity();configure(scene.selected!!,"Ground",240.0,20.0,420.0,20.0,false)
    scene.selected!!.setValue("SpriteRenderer",listOf("color"),buildJsonObject { put("x",0.35);put("y",0.7);put("z",0.4);put("w",1) })
    val prefab=EntityWorkspaceState(catalog)
    configure(prefab,"Crate",140.0,140.0,32.0,32.0,true)
    val prefabPath=folder.resolve("scene-crate.sleprefab")
    prefab.save(prefabPath)
    scene.importPrefab(prefabPath);scene.selected!!.rename("Crate A")
    scene.importPrefab(prefabPath);scene.selected!!.rename("Crate B")
    scene.selected!!.setValue("Transform",listOf("position"),vector(300.0,170.0))
    scene.select(scene.entities.first().document.key)
    val path=folder.resolve("starter.slescene")
    scene.save(path)
    return prefabPath
}

internal fun createCameraScene(scene: SceneWorkspaceState,catalog: Map<String,ComponentDefinition>,folder: Path) {
    createStarterScene(scene,catalog,folder)
    scene.rename("Camera and Play Demo")
    val player=scene.entities.first { it.document.name=="Crate A" }
    player.rename("Player")
    player.removeComponent("RigidBody")
    player.addComponent("Script")
    val script=player.importAsset(Path.of("assets/scripts/editor_camera_demo.lua"))
    player.setValue("Script",listOf("scriptAsset"),JsonPrimitive(script.toString().replace('\\','/')))
    scene.select(player.document.key)
    scene.addCamera(asChild=true)
    scene.selected!!.rename("Player Camera")
    scene.setActiveCamera(scene.selectedKey!!)
    scene.save(folder.resolve("camera-play.slescene"))
}
