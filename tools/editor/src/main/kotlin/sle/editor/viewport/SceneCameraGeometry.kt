package sle.editor.viewport

import sle.editor.model.EntityDocument
import sle.editor.model.ScenePose

import kotlinx.serialization.json.*

/** Camera view ignores transform scale, matching the runtime camera projection. */
internal fun cameraOutline(document: EntityDocument): List<Vec2> {
    val camera=document.components["Camera2D"] as? JsonObject ?: return emptyList()
    if(camera["enabled"]?.jsonPrimitive?.booleanOrNull==false) return emptyList()
    val size=camera["viewSize"] as? JsonObject ?: return emptyList()
    val zoom=camera["zoom"]?.jsonPrimitive?.doubleOrNull ?: 1.0
    if(!zoom.isFinite() || zoom<=0) return emptyList()
    val width=size["x"]?.jsonPrimitive?.doubleOrNull ?: return emptyList()
    val height=size["y"]?.jsonPrimitive?.doubleOrNull ?: return emptyList()
    if(width<=0 || height<=0) return emptyList()
    val pose=ScenePose.from(document)
    return listOf(-1 to -1,1 to -1,1 to 1,-1 to 1,-1 to -1).map { (x,y) -> pose.position+Vec2(width*x/(2*zoom),height*y/(2*zoom)).rotate(pose.rotation) }
}
internal fun cameraOutlineHit(document: EntityDocument,camera: PreviewCamera,point: Vec2): Boolean = cameraOutline(document).map(camera::screen).zipWithNext().any { (a,b) ->
    val delta=b-a;val length=delta.x*delta.x+delta.y*delta.y
    val t=if(length<1e-12) 0.0 else (((point-a).x*delta.x+(point-a).y*delta.y)/length).coerceIn(0.0,1.0)
    (point-(a+delta*t)).length()<=6.0
}
