package sle.editor.viewport

import sle.editor.model.EntityDocument

import kotlinx.serialization.json.*
import kotlin.math.*

internal data class Vec2(val x: Double, val y: Double) {
    operator fun plus(other: Vec2) = Vec2(x + other.x, y + other.y)
    operator fun minus(other: Vec2) = Vec2(x - other.x, y - other.y)
    operator fun times(factor: Double) = Vec2(x * factor, y * factor)
    fun length() = hypot(x, y)
    fun rotate(angle: Double) = Vec2(cos(angle)*x-sin(angle)*y, sin(angle)*x+cos(angle)*y)
}
internal data class PreviewCamera(val origin: Vec2, val size: Vec2, val width: Int, val height: Int) {
    fun world(pixel: Vec2) = origin + Vec2(pixel.x / width * size.x, (1.0 - pixel.y / height) * size.y)
    fun screen(world: Vec2) = Vec2((world.x-origin.x)/size.x*width, (1-(world.y-origin.y)/size.y)*height)
}
/** A move constraint: a Move-tool axis arrow, or Shift's dominant axis. */
internal enum class DragAxis(val snapAxes: SnapAxes) {
    Free(SnapAxes.Both), X(SnapAxes.X), Y(SnapAxes.Y);
    fun constrain(start: Vec2, end: Vec2) = when(this) { Free -> end; X -> Vec2(end.x,start.y); Y -> Vec2(start.x,end.y) }
    companion object {
        /** The axis along which [delta] moved most (X on a tie). */
        fun dominant(delta: Vec2) = if (abs(delta.y) > abs(delta.x)) Y else X
    }
}
internal data class GizmoHandle(val kind: String, val local: Vec2, val x: Int = 0, val y: Int = 0)
internal data class GizmoShape(val target: String, val position: Vec2, val rotation: Double, val scale: Vec2,
    val offset: Vec2, val size: Vec2, val radius: Double) {
    val circle get() = target.startsWith("Circle")
    val editable get() = listOf(position.x,position.y,rotation,scale.x,scale.y,offset.x,offset.y).all { it.isFinite() } &&
        abs(scale.x) > 1e-6 && abs(scale.y) > 1e-6 &&
        (target == "Entity" || if(circle) radius.isFinite() && radius > 0 else
            size.x.isFinite() && size.y.isFinite() && size.x > 0 && size.y > 0)
    fun world(local: Vec2) = position + Vec2(local.x*scale.x, local.y*scale.y).rotate(rotation)
    fun local(world: Vec2): Vec2 {
        val rotated = (world-position).rotate(-rotation)
        return Vec2(rotated.x/scale.x, rotated.y/scale.y)
    }
    val center get() = world(offset)
    /** Physics uses an eight-vertex polygon for nonuniformly scaled circles. */
    fun outline(): List<Vec2> {
        if(!editable) return emptyList()
        if(circle) {
            val segments=if(abs(scale.x)==abs(scale.y)) 64 else 8
            return (0..segments).map { i ->
                val angle=i*2*PI/segments
                center+Vec2(cos(angle)*radius*abs(scale.x),sin(angle)*radius*abs(scale.y)).rotate(rotation)
            }
        }
        return listOf(-1 to -1,1 to -1,1 to 1,-1 to 1,-1 to -1).map { (x,y) ->
            world(offset+Vec2(size.x*x/2,size.y*y/2))
        }
    }
    fun contains(world: Vec2): Boolean {
        if (!editable) return false
        if(circle) {
            if(abs(scale.x)==abs(scale.y)) return (local(world)-offset).length() <= radius
            return outline().zipWithNext().all { (a,b) ->
                val edge=b-a;val delta=world-a
                edge.x*delta.y-edge.y*delta.x >= -1e-10
            }
        }
        val local = local(world)-offset
        return abs(local.x) <= size.x/2 && abs(local.y) <= size.y/2
    }
    companion object {
        val targets = listOf("Entity", "SpriteRenderer", "BoxCollider", "CircleCollider", "BoxZone", "CircleZone")
        fun available(doc: EntityDocument) = targets.filter { if (it == "Entity") "Transform" in doc.components else it in doc.components && "Transform" in doc.components }
        fun from(doc: EntityDocument, target: String): GizmoShape? {
            val transform = doc.components["Transform"]?.jsonObject ?: return null
            if (target != "Entity" && target !in doc.components) return null
            fun JsonObject.number(key: String, fallback: Double) = get(key)?.jsonPrimitive?.doubleOrNull ?: fallback
            fun JsonObject.vector(key: String, fallback: Vec2) = (get(key) as? JsonObject)?.let {
                Vec2(it.number("x", fallback.x), it.number("y", fallback.y))
            } ?: fallback
            val values = doc.components[target] as? JsonObject ?: JsonObject(emptyMap())
            return GizmoShape(target, transform.vector("position", Vec2(0.0,0.0)), transform.number("rotation",0.0),
                if(target=="Entity" || target=="SpriteRenderer") transform.vector("scale",Vec2(1.0,1.0)) else doc.parentWorldScale, values.vector("offset",Vec2(0.0,0.0)),
                values.vector("size",Vec2(1.0,1.0)), values.number("radius",1.0))
        }
    }
}
internal object GizmoMath {
    fun pick(doc: EntityDocument, camera: PreviewCamera, point: Vec2, selected: String): String? {
        val world=camera.world(point)
        fun distance(a: Vec2,b: Vec2): Double {
            val delta=b-a
            val length=delta.x*delta.x+delta.y*delta.y
            val t=if (length<1e-12) 0.0 else (((point-a).x*delta.x+(point-a).y*delta.y)/length).coerceIn(0.0,1.0)
            return (point-(a+delta*t)).length()
        }
        val outlines=GizmoShape.available(doc).filter { it!="Entity" && it!="SpriteRenderer" }
            .sortedBy { if (it==selected) 0 else 1 }
        for (target in outlines) {
            if (doc.components[target]?.jsonObject?.get("enabled")?.jsonPrimitive?.booleanOrNull == false) continue
            val shape=GizmoShape.from(doc,target) ?: continue
            if (!shape.editable) continue
            val polygon=shape.outline().map(camera::screen)
            if (polygon.zipWithNext().any { (a,b) -> distance(a,b)<=6.0 }) return target
        }
        val sprite=GizmoShape.from(doc,"SpriteRenderer")
        val visible=doc.components["SpriteRenderer"]?.jsonObject?.get("visible")?.jsonPrimitive?.booleanOrNull != false
        return if (visible && sprite?.contains(world)==true) "SpriteRenderer" else null
    }
    fun handles(shape: GizmoShape): List<GizmoHandle> {
        if (shape.target == "Entity") return listOf(GizmoHandle("move", Vec2(0.0,0.0)))
        if (!shape.editable) return emptyList()
        val center = GizmoHandle("move",shape.offset)
        if (shape.circle) return listOf(center) + listOf(1 to 0, 0 to 1, -1 to 0, 0 to -1).map { (x,y) ->
            GizmoHandle("radius", shape.offset,x,y)
        }
        return listOf(center) + listOf(-1 to -1,0 to -1,1 to -1,1 to 0,1 to 1,0 to 1,-1 to 1,-1 to 0).map { (x,y) ->
            GizmoHandle("size",shape.offset + Vec2(shape.size.x*x/2,shape.size.y*y/2),x,y)
        }
    }
    fun handleWorld(shape: GizmoShape, handle: GizmoHandle) = if (handle.kind == "radius")
        shape.world(shape.offset+Vec2(handle.x*shape.radius,handle.y*shape.radius)) else shape.world(handle.local)

    /**
     * [snap] is the move step while snapping applies (null: free). [axis] keeps a move on one world
     * axis (a Move-tool arrow, or Shift's dominant axis); only the moved axes are snapped. An
     * entity's position is snapped as given: in the Scene view that is its world position, which
     * the caller converts to local through the parent pose.
     */
    fun drag(doc: EntityDocument, target: String, handle: GizmoHandle, start: Vec2, pointer: Vec2, snap: Double? = null,
        axis: DragAxis = DragAxis.Free): EntityDocument {
        val shape = GizmoShape.from(doc,target) ?: return doc
        val end = axis.constrain(start,pointer)
        if ((end-start).length() < 1e-12) return doc
        fun precise(value: Double) = snap?.let { SnapMath.value(value,it) } ?: value
        fun vector(doc: EntityDocument, component: String, field: String, value: Vec2): EntityDocument {
            if (!value.x.isFinite() || !value.y.isFinite() || max(abs(value.x),abs(value.y)) > 1e9) return doc
            val vector=buildJsonObject { put("x",value.x); put("y",value.y) }
            val values=doc.components.getValue(component).jsonObject
            return doc.copy(components=JsonObject(doc.components+(component to JsonObject(values+(field to vector)))))
        }
        if (target == "Entity") {
            val next = shape.position+end-start
            return vector(doc,"Transform","position",snap?.let { SnapMath.point(next,it,axis.snapAxes) } ?: next)
        }
        if (!shape.editable) return doc
        if (handle.kind == "move") {
            val next = shape.offset + shape.local(end)-shape.local(start)
            return vector(doc,target,"offset",Vec2(precise(next.x),precise(next.y)))
        }
        // Hit targets extend beyond the drawn center. Preserve where the user
        // grabbed the handle so the first drag event cannot jump its geometry.
        val endpoint = handleWorld(shape,handle) + end - start
        if (handle.kind == "radius") {
            val radius = max(0.001,precise((shape.local(endpoint)-shape.offset).length()))
            if (!radius.isFinite() || radius>1e9) return doc
            return doc.setValue(target,listOf("radius"),JsonPrimitive(radius))
        }
        val point = shape.local(endpoint)
        fun axis(offset: Double, size: Double, direction: Int, mouse: Double): Pair<Double,Double> {
            if (direction == 0) return offset to size
            val opposite=offset-size*direction/2
            val edge=if (direction>0) max(opposite+0.001,precise(mouse)) else min(opposite-0.001,precise(mouse))
            return (edge+opposite)/2 to abs(edge-opposite)
        }
        val x=axis(shape.offset.x,shape.size.x,handle.x,point.x)
        val y=axis(shape.offset.y,shape.size.y,handle.y,point.y)
        return vector(vector(doc,target,"offset",Vec2(x.first,y.first)),target,"size",Vec2(x.second,y.second))
    }
}
