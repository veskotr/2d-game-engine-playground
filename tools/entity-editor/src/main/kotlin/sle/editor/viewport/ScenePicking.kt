package sle.editor.viewport

import sle.editor.model.SceneWorkspaceState

import kotlinx.serialization.json.*

internal data class ScenePick(val key: String,val target: String)
internal object ScenePicking {
    fun pick(scene: SceneWorkspaceState,camera: PreviewCamera,point: Vec2): ScenePick? {
        val selected=scene.selected
        val shape=selected?.let { GizmoShape.from(scene.worldDocument(it.document.key),"Entity") }
        if(shape!=null && GizmoMath.handles(shape).any {
            (camera.screen(GizmoMath.handleWorld(shape,it))-point).length()<=10
        }) return ScenePick(selected.document.key,"Entity")
        data class Hit(val pick: ScenePick,val layer: Int)
        val hits=scene.entities.asReversed().mapNotNull { entity ->
            (if(cameraOutlineHit(scene.worldDocument(entity.document.key),camera,point)) "Camera2D" else GizmoMath.pick(scene.worldDocument(entity.document.key),camera,point,entity.selectedTarget))?.let { target ->
                val layer=if(target!="SpriteRenderer") Int.MAX_VALUE else
                    (entity.document.components["SpriteRenderer"]?.jsonObject?.get("layer")?.jsonPrimitive?.intOrNull ?: 0).coerceAtLeast(0)
                Hit(ScenePick(entity.document.key,"Entity"),layer)
            }
        }
        hits.maxByOrNull { it.layer }?.let { return it.pick }
        return scene.entities.asReversed().firstOrNull { entity ->
            GizmoShape.from(scene.worldDocument(entity.document.key),"Entity")?.let { (camera.screen(it.position)-point).length()<=10 } == true
        }?.let { ScenePick(it.document.key,"Entity") }
    }
}
