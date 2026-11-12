package sle.editor.smoke

import sle.editor.files.textureAssetPath
import sle.editor.model.EntityWorkspaceState
import sle.editor.model.readComponentCatalog

import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.nio.file.Files
import java.nio.file.Path

internal fun runAuthoringSmoke() {
    val state = EntityWorkspaceState(readComponentCatalog())
    val identity=state.document.key
    state.document = state.document.copy(name = "Authored Entity 😀")
    state.addComponent("BoxCollider")
    state.addComponent("RigidBody")
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
    println("ENTITY_AUTHORING_SMOKE_PASS components=${state.document.components.keys} file=$path")
}
