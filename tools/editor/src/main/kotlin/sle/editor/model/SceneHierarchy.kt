package sle.editor.model

import sle.editor.viewport.GizmoShape
import sle.editor.viewport.Vec2

import kotlinx.serialization.json.*

internal fun validateSceneParents(keys: List<String>, parents: Map<String,String>) {
    val known=keys.toSet()
    require(parents.all { (child,parent) -> child in known && parent in known && child!=parent }) { "Parent must reference a different existing entity" }
    val done=mutableSetOf<String>()
    for(key in keys) {
        val path=mutableSetOf<String>();var cursor: String?=key
        while(cursor!=null && cursor !in done) {
            require(path.add(cursor)) { "Scene hierarchy contains a cycle" };cursor=parents[cursor]
        }
        done.addAll(path)
    }
}
internal data class ScenePose(val position: Vec2=Vec2(0.0,0.0),val rotation: Double=0.0,val scale: Vec2=Vec2(1.0,1.0)) {
    fun compose(local: ScenePose)=ScenePose(position+Vec2(scale.x*local.position.x,scale.y*local.position.y).rotate(rotation),rotation+local.rotation,Vec2(scale.x*local.scale.x,scale.y*local.scale.y))
    fun relativeTo(parent: ScenePose): ScenePose {
        require(parent.scale.x!=0.0 && parent.scale.y!=0.0) { "Cannot preserve world transform under a zero-scale parent" }
        val offset=(position-parent.position).rotate(-parent.rotation)
        return ScenePose(Vec2(offset.x/parent.scale.x,offset.y/parent.scale.y),rotation-parent.rotation,Vec2(scale.x/parent.scale.x,scale.y/parent.scale.y))
    }
    fun fields()=buildJsonObject {
        put("position",buildJsonObject { put("x",position.x);put("y",position.y) });put("rotation",rotation)
        put("scale",buildJsonObject { put("x",scale.x);put("y",scale.y) })
    }
    companion object {
        fun from(document: EntityDocument): ScenePose {
            val shape=GizmoShape.from(document,"Entity") ?: return ScenePose()
            return ScenePose(shape.position,shape.rotation,shape.scale)
        }
    }
}
internal fun sceneWorldPose(key: String,entities: List<EntityDocument>,parents: Map<String,String>): ScenePose {
    val documents=entities.associateBy { it.key };val path=mutableListOf<String>();val visited=mutableSetOf<String>();var cursor: String?=key
    while(cursor!=null) { require(visited.add(cursor)) { "Scene hierarchy contains a cycle" };path.add(cursor);cursor=parents[cursor] }
    return path.asReversed().fold(ScenePose()) { pose,id -> pose.compose(ScenePose.from(documents.getValue(id))) }
}
internal fun EntityDocument.withPose(pose: ScenePose)=copy(components=JsonObject(components+("Transform" to JsonObject((components["Transform"] as? JsonObject ?: JsonObject(emptyMap()))+pose.fields()))))
