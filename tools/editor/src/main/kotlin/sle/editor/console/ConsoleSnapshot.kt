package sle.editor.console

import kotlinx.serialization.json.*
import sle.editor.model.SceneDocument
import sle.editor.project.OpenedProject
import sle.editor.project.assets.AssetSnapshot

internal fun editorConsoleSnapshot(
    scene: SceneDocument,
    selection: List<String>,
    documentRevision: Long,
    projectRevision: Long,
    project: OpenedProject?,
    assets: AssetSnapshot?,
): JsonObject = buildJsonObject {
    put("revision", documentRevision)
    put("scene", Json.parseToJsonElement(scene.serialize()))
    putJsonArray("selection") { selection.forEach { add(it) } }
    project?.let {
        putJsonObject("project") {
            put("revision", projectRevision)
            put("assetRoot", it.document.assetRoot)
        }
    }
    putJsonArray("assets") {
        for (asset in assets?.entries.orEmpty()) {
            addJsonObject {
                put("path", asset.path)
                put("kind", asset.kind.name.lowercase())
                assets?.idsByPath?.get(asset.path)?.let { put("id", it) }
            }
        }
    }
}
