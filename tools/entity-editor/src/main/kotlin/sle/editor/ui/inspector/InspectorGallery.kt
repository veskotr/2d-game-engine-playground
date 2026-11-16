package sle.editor.ui.inspector

import androidx.compose.foundation.layout.*
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import kotlinx.serialization.json.*
import sle.editor.model.*
import sle.editor.ui.EntityWorkspace
import sle.editor.ui.SceneEntityInspector
import sle.editor.ui.controls.EditorButton
import sle.editor.ui.theme.LocalEditorMetrics

/** Isolated fixture using the real prefab and scene inspector paths, with no JNI. */
@Composable
internal fun InspectorGallery() {
    val catalog=remember {
        mapOf(
            "Transform" to ComponentDefinition(Json.parseToJsonElement("""{"position":{"x":128,"y":64},"rotation":0,"scale":{"x":1,"y":1}}""").jsonObject,JsonObject(emptyMap())),
            "Camera2D" to ComponentDefinition(Json.parseToJsonElement("""{"primary":true,"zoom":1,"projection":"Orthographic"}""").jsonObject,
                Json.parseToJsonElement("""{"projection":["Orthographic","Perspective"]}""").jsonObject),
            "SpriteRenderer" to ComponentDefinition(Json.parseToJsonElement("""{"texture":"player.png","region":{"x":0,"y":0,"width":16,"height":16},"visible":true}""").jsonObject,JsonObject(emptyMap()))
        )
    }
    val prefab=remember { EntityWorkspaceState(catalog).apply { rename("Player");addComponent("SpriteRenderer") } }
    val scene=remember { SceneWorkspaceState(catalog) { it }.apply { addEntity();selected!!.addComponent("Camera2D") } }
    Row(horizontalArrangement=Arrangement.spacedBy(LocalEditorMetrics.current.gap)) {
        EditorButton("Undo",{prefab.undo()});EditorButton("Redo",{prefab.redo()})
    }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val paneWidth=maxWidth/2
        Row(Modifier.fillMaxWidth().heightIn(min=paneWidth)) {
            Column(Modifier.weight(1f).height(paneWidth*1.5f)) {
                Text("Prefab inspector")
                EntityWorkspace(prefab,embedded=true)
            }
            Column(Modifier.weight(1f).height(paneWidth*1.5f)) {
                Text("Scene inspector")
                SceneEntityInspector(scene)
            }
        }
    }
}
