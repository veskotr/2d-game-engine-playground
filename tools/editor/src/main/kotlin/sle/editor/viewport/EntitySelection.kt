package sle.editor.viewport

import sle.editor.model.EntityDocument

import kotlinx.serialization.json.*

/** Screen-space outline encloses visible geometry, with no component resize handles. */
internal fun entitySelectionBounds(document: EntityDocument,camera: PreviewCamera): List<Vec2> {
    val points=mutableListOf<Vec2>()
    points.addAll(cameraOutline(document).map(camera::screen))
    GizmoShape.from(document,"Entity")?.let { points.add(camera.screen(it.position)) }
    for(target in GizmoShape.available(document).filter { it!="Entity" }) {
        val fields=document.components.getValue(target).jsonObject
        if(fields["enabled"]?.jsonPrimitive?.booleanOrNull==false || fields["visible"]?.jsonPrimitive?.booleanOrNull==false) continue
        val shape=GizmoShape.from(document,target) ?: continue
        if(shape.circle) {
            points.add(camera.screen(shape.center+Vec2(-shape.worldRadius,-shape.worldRadius)))
            points.add(camera.screen(shape.center+Vec2(shape.worldRadius,shape.worldRadius)))
        } else for((x,y) in listOf(-1 to -1,1 to -1,1 to 1,-1 to 1))
            points.add(camera.screen(shape.world(shape.offset+Vec2(shape.size.x*x/2,shape.size.y*y/2))))
    }
    if(points.isEmpty()) return emptyList()
    val left=points.minOf { it.x }-6;val right=points.maxOf { it.x }+6
    val top=points.minOf { it.y }-6;val bottom=points.maxOf { it.y }+6
    return listOf(Vec2(left,top),Vec2(right,top),Vec2(right,bottom),Vec2(left,bottom),Vec2(left,top))
}
