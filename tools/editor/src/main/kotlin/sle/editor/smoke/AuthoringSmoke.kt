package sle.editor.smoke

import sle.editor.files.textureAssetPath
import sle.editor.model.DetailsPropertyActions
import sle.editor.model.EntityWorkspaceState
import sle.editor.model.SceneWorkspaceState
import sle.editor.ui.inspector.PropertyDrawer
import sle.editor.ui.inspector.PropertyKind
import sle.editor.ui.inspector.propertyDefinition
import sle.editor.model.readComponentCatalog

import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.nio.file.Files
import java.nio.file.Path

internal fun runAuthoringSmoke() {
    val catalog = readComponentCatalog()
    val state = EntityWorkspaceState(catalog)
    val scene = SceneWorkspaceState(catalog)
    scene.addEntity()
    for (owner in listOf(state, checkNotNull(scene.selected))) {
        owner.addComponent("Camera2D")
        val camera = owner.document.components.getValue("Camera2D").jsonObject
        val zoom = propertyDefinition("Camera2D", listOf("zoom"), camera.getValue("zoom"),
            catalog = owner.catalog["Camera2D"])
        check(zoom.kind == PropertyKind.Number && zoom.descriptor?.help == "Sets the camera zoom multiplier.")
        check(zoom.defaultValue == zoom.value)
        val edit = owner.numberEdit("Camera2D", zoom.path)
        edit.focus()
        edit.input("6")
        check(edit.commit())
        check(owner.document.components.getValue("Camera2D").jsonObject["zoom"] == JsonPrimitive(6))
        owner.undo()
        check(owner.document.components.getValue("Camera2D").jsonObject["zoom"] == zoom.value)

        val actions = DetailsPropertyActions()
        fun property(component: String, vararg path: String) = propertyDefinition(component, path.toList(),
            path.fold(owner.document.components.getValue(component)) { node, key -> node.jsonObject.getValue(key) },
            catalog = catalog[component])
        owner.addComponent("RigidBody")
        owner.addComponent("SpriteRenderer")
        actions.replace(owner, property("RigidBody", "bodyType"), "\"Dynamic\"", "Paste")
        check(property("RigidBody", "bodyType").value == JsonPrimitive("Dynamic"))
        check(property("SpriteRenderer", "texture").drawer == PropertyDrawer.Texture)
        check(property("SpriteRenderer", "color").drawer == PropertyDrawer.Color)
        check(property("SpriteRenderer", "region").drawer == PropertyDrawer.Structured)
        check(property("SpriteRenderer", "region", "uv").drawer == PropertyDrawer.Uv)
        actions.replace(owner, property("SpriteRenderer", "color", "w"), "0.6", "Paste")
        check(property("SpriteRenderer", "color", "w").value.jsonPrimitive.content == "0.6")
        actions.replace(owner, property("SpriteRenderer", "region", "uv"),
            """{"x":0.2,"y":0.3,"z":0.9,"w":1}""", "Paste")
        val u = property("SpriteRenderer", "region", "uv", "x")
        actions.replace(owner, u, u.defaultValue.toString(), "Reset")
        check(property("SpriteRenderer", "region", "uv", "x").value == u.defaultValue)
        check(property("SpriteRenderer", "region", "uv", "y").value == JsonPrimitive(0.3))
        owner.addComponent("BoxCollider")
        val rotation = property("Transform", "rotation")
        check(rotation.displayLabel == "Rotation (rad)" && rotation.descriptor?.ui?.unit == "rad")
        val mask = property("BoxCollider", "maskBits")
        check(mask.kind == PropertyKind.Integer && mask.descriptor?.hard?.max == 65535.0)
        check(mask.descriptor.ui?.step == 1.0 && mask.defaultValue == JsonPrimitive(65535))
        check(runCatching { actions.replace(owner, mask, "65536", "Paste") }.isFailure)
        val invalid = runCatching { actions.replace(owner, property("BoxCollider", "size", "x"), "-2", "Paste") }
        check(invalid.isFailure && owner.history.undoLabel == "Add BoxCollider")
        owner.undo()
        owner.undo()
        check(property("SpriteRenderer", "region", "uv", "x").value == JsonPrimitive(0.2))
        owner.removeComponent("SpriteRenderer")
        owner.removeComponent("RigidBody")
    }
    val sceneCamera = checkNotNull(scene.selected)
    val componentActions = DetailsPropertyActions()
    componentActions.replaceComponent(state, "Camera2D",
        """{"component":"Camera2D","values":{"zoom":2,"viewSize":{"x":640}}}""")
    val copiedCamera = componentActions.copyComponent(state, "Camera2D")
    componentActions.replaceComponent(sceneCamera, "Camera2D", copiedCamera)
    check(sceneCamera.document.components["Camera2D"] == state.document.components["Camera2D"])
    val pasted = sceneCamera.document
    val wrongType = runCatching { componentActions.replaceComponent(sceneCamera, "Transform", copiedCamera) }
    check(wrongType.isFailure && sceneCamera.document == pasted && scene.history.undoLabel == "Paste Camera2D")
    componentActions.replaceComponent(sceneCamera, "Camera2D")
    check(scene.history.undoLabel == "Reset Camera2D")
    sceneCamera.undo()
    check(sceneCamera.document == pasted)
    sceneCamera.redo()
    check(sceneCamera.document.components["Camera2D"] == catalog.getValue("Camera2D").defaults)
    scene.addCamera()
    val secondCamera = checkNotNull(scene.selected)
    componentActions.replaceComponent(secondCamera, "Camera2D", copiedCamera)
    scene.setActiveCamera(secondCamera.document.key)
    check(scene.activeCamera === secondCamera)
    scene.undo()
    check(scene.activeCamera === sceneCamera)
    val identity=state.document.key
    state.document = state.document.copy(name = "Authored Entity 😀")
    state.addComponent("BoxCollider")
    state.addComponent("RigidBody")
    val body = state.document.components.getValue("RigidBody").jsonObject
    val bodyType = propertyDefinition("RigidBody", listOf("bodyType"), body.getValue("bodyType"),
        catalog = catalog["RigidBody"])
    check(bodyType.choices.map { it.jsonPrimitive.content } == listOf("Static", "Dynamic", "Kinematic"))
    state.addComponent("Script")
    state.addComponent("SpriteRenderer")
    val texture = textureAssetPath(Path.of("assets/textures/tile2.png"))
    state.setValue("SpriteRenderer", listOf("texture"), JsonPrimitive(texture))
    state.editField("Transform", listOf("position", "x"), JsonPrimitive(0.0), "12")
    state.editField("Transform", listOf("position", "y"), JsonPrimitive(0.0), "34")
    state.editField("BoxCollider", listOf("size", "x"), JsonPrimitive(1.0), "20")
    state.editField("BoxCollider", listOf("size", "y"), JsonPrimitive(1.0), "30")
    val path = Path.of(System.getProperty("sle.authoringReport"))
    Files.createDirectories(path.parent)
    state.save(path)
    check(!state.dirty)
    val original = Files.readString(path)
    check(!original.contains("\"fields\"") && !original.contains("\"groups\""))
    state.editField("BoxCollider", listOf("size", "x"), JsonPrimitive(1.0), "-2")
    check(runCatching { state.save(path) }.isFailure)
    check(Files.readString(path) == original)
    state.open(path)
    val savedTexture = state.document.components.getValue("SpriteRenderer").jsonObject.getValue("texture").jsonPrimitive.content
    check(Files.readAllBytes(Path.of(savedTexture)).contentEquals(Files.readAllBytes(Path.of(texture))))
    check(state.document.name == "Authored Entity 😀" && state.document.key == identity && !state.dirty)
    state.save(path) // Successful replacement of an existing file, not just creation.
    val packagePath = path.resolveSibling("authored-entity.sleprefab")
    val sourceImage = Files.createTempFile("sle-imported-texture", ".png")
    Files.copy(Path.of("assets/textures/tile2.png"), sourceImage, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
    val importedImage = state.importAsset(sourceImage)
    state.setValue("SpriteRenderer", listOf("texture"), JsonPrimitive(importedImage.toString().replace('\\', '/')))
    Files.delete(sourceImage)
    val extra = Files.createTempFile("sle-extra", ".txt")
    Files.writeString(extra, "Extra package content")
    state.importAsset(extra)
    Files.delete(extra)
    state.save(packagePath)
    val relocated = packagePath.resolveSibling("relocated-entity.sleprefab")
    Files.copy(packagePath, relocated, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
    state.newDocument()
    state.open(relocated)
    check(state.document.key == identity && state.assets.size >= 2 && !state.dirty)
    state.save(relocated)
    println("PREFAB_PACKAGE_AUTHORING_SMOKE_PASS file=$relocated")
    val starter = SceneWorkspaceState(catalog)
    starter.open(Path.of("assets/scenes/editor_starter.slescene"))
    val expectedStarter = starter.document()
    val starterCopy = path.resolveSibling("roundtrip-starter.slescene")
    starter.save(starterCopy)
    starter.newScene()
    starter.open(starterCopy)
    val reopenedStarter = starter.document()
    check(reopenedStarter.key == expectedStarter.key && reopenedStarter.parents == expectedStarter.parents)
    check(reopenedStarter.entities.map { it.key } == expectedStarter.entities.map { it.key } && !starter.dirty)
    for ((before, after) in expectedStarter.entities.zip(reopenedStarter.entities))
        for (component in listOf("Transform", "RigidBody", "BoxCollider", "Camera2D"))
            check(before.components[component] == after.components[component])
    println("STARTER_SCENE_ROUNDTRIP_SMOKE_PASS file=$starterCopy")
    runLinkedPrefabSmoke()
    println("ENTITY_AUTHORING_SMOKE_PASS components=${state.document.components.keys} file=$path")
}
