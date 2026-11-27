package sle.editor.ui.inspector

import androidx.compose.foundation.layout.*
import sle.editor.ui.controls.EditorLabel
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
            "Camera2D" to decodeComponentCatalog("""
                {"Camera2D":{
                  "defaults":{"enabled":true,"zoom":6,"viewSize":{"x":480,"y":270},"projection":"Orthographic"},
                  "enums":{"projection":["Orthographic","Perspective"]},
                  "groups":{"view":{"label":"View","order":1}},
                  "fields":[
                    {"path":["enabled"],"order":1},
                    {"path":["zoom"],"group":"view","order":1,"type":"number",
                     "help":"Camera magnification","hard":{"min":0,"minExclusive":true},
                     "ui":{"min":0.25,"max":4,"step":0.25,"precision":2,"unit":"×","slider":true},
                     "visibleWhen":{"path":["enabled"],"equals":true}},
                    {"path":["viewSize"],"group":"view","order":2},
                    {"path":["viewSize","y"],"readOnly":true},
                    {"path":["projection"],"readOnly":true}
                  ]
                }}
            """).getValue("Camera2D"),
            "SpriteRenderer" to decodeComponentCatalog("""
                {"SpriteRenderer":{
                  "defaults":{"texture":"assets/textures/tile2.png","color":{"x":0.7,"y":0.8,"z":1,"w":0.6},
                    "size":{"x":16,"y":16},"region":{"uv":{"x":0,"y":0,"z":1,"w":1}},"visible":true},
                  "enums":{},
                  "fields":[
                    {"path":["texture"],"type":"resource","drawer":"texture"},
                    {"path":["color"],"type":"vector","drawer":"color"},
                    {"path":["region"],"type":"object","drawer":"structured"},
                    {"path":["region","uv"],"type":"vector","drawer":"uv"}
                  ]
                }}
            """).getValue("SpriteRenderer"),
            "BoxCollider" to decodeComponentCatalog("""
                {"BoxCollider":{
                  "defaults":{"size":{"x":32,"y":32},"offset":{"x":0,"y":0},
                    "friction":0.4000000059604645,"categoryBits":1,"maskBits":65535,"enabled":true},
                  "enums":{},
                  "fields":[
                    {"path":["categoryBits"],"type":"integer"},
                    {"path":["maskBits"],"type":"integer"}
                  ]
                }}
            """).getValue("BoxCollider"),
            "Script" to ComponentDefinition(
                Json.parseToJsonElement("""{"variables":{"speed":3,"enabled":true}}""").jsonObject,
                JsonObject(emptyMap()),
            ),
        )
    }
    val prefab = remember {
        EntityWorkspaceState(catalog).apply {
            rename("Player")
            addComponent("Camera2D")
            addComponent("SpriteRenderer")
            inspectorSections.setExpanded(document.key, "Transform", false)
            inspectorSections.setExpanded(document.key, "Camera2D", false)
        }
    }
    val scene = remember {
        SceneWorkspaceState(catalog) { it }.apply {
            addEntity()
            val owner = checkNotNull(selected)
            owner.addComponent("Camera2D")
            owner.addComponent("Script")
            owner.addComponent("BoxCollider")
            owner.inspectorSections.setExpanded(owner.document.key, "Transform", false)
            owner.inspectorSections.setExpanded(owner.document.key, "Camera2D", false)
            owner.inspectorSections.setExpanded(owner.document.key, "Script", false)
        }
    }
    Row(horizontalArrangement=Arrangement.spacedBy(LocalEditorMetrics.current.gap)) {
        EditorButton("Undo",{prefab.undo()});EditorButton("Redo",{prefab.redo()})
    }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val paneWidth=maxWidth/2
        Row(Modifier.fillMaxWidth().heightIn(min=paneWidth)) {
            Column(Modifier.weight(1f).height(paneWidth*1.5f)) {
                EditorLabel("Entity Template details")
                EntityWorkspace(prefab,embedded=true)
            }
            Column(Modifier.weight(1f).height(paneWidth*1.5f)) {
                EditorLabel("Scene details")
                SceneEntityInspector(scene)
            }
        }
    }
}
