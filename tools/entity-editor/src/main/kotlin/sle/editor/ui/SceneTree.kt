package sle.editor.ui

import sle.editor.model.EntityDocument
import sle.editor.model.SceneDocument
import sle.editor.model.SceneWorkspaceState

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp

internal data class SceneTreeNode(val id: String,val label: String,val entityKey: String?=null,
    val children: List<SceneTreeNode> = emptyList())

internal fun sceneTree(document: SceneDocument): SceneTreeNode {
    val children=document.entities.groupBy { document.parents[it.key] }
    val built=mutableMapOf<String,SceneTreeNode>()
    val queue=java.util.ArrayDeque<Pair<EntityDocument,Boolean>>()
    children[null]?.asReversed()?.forEach { queue.addLast(it to false) }
    while(queue.isNotEmpty()) {
        val (entity,visited)=queue.removeLast()
        if(visited) built[entity.key]=SceneTreeNode("entity:${entity.key}",entity.name.ifBlank { "(Unnamed entity)" },entity.key,children[entity.key].orEmpty().map { built.getValue(it.key) })
        else { queue.addLast(entity to true);children[entity.key]?.asReversed()?.forEach { queue.addLast(it to false) } }
    }
    return SceneTreeNode("scene:${document.key}",document.name.ifBlank { "(Unnamed scene)" },children=children[null].orEmpty().map { built.getValue(it.key) })
}
internal fun flattenSceneTree(root: SceneTreeNode,expanded: Set<String>): List<Pair<SceneTreeNode,Int>> {
    val result=mutableListOf<Pair<SceneTreeNode,Int>>();val queue=java.util.ArrayDeque<Pair<SceneTreeNode,Int>>()
    root.children.asReversed().forEach { queue.addLast(it to 0) }
    while(queue.isNotEmpty()) { val row=queue.removeLast();result.add(row);if(row.first.id in expanded) row.first.children.asReversed().forEach { queue.addLast(it to row.second+1) } }
    return result
}

@Composable
internal fun SceneTree(state: SceneWorkspaceState,modifier: Modifier=Modifier,
    onEntityBounds: (String,Rect)->Unit={_,_->},editable: Boolean=true,onEditEntity: ((String)->Unit)?=null) {
    val root=sceneTree(state.document())
    var expanded by remember(state.key) { mutableStateOf(true) }
    var expandedNodes by remember(state.key) { mutableStateOf<Set<String>>(emptySet()) }
    LaunchedEffect(state.selectedKey,state.parents) {
        var parent=state.parents[state.selectedKey];val ancestors=mutableSetOf<String>()
        while(parent!=null && ancestors.add("entity:$parent")) parent=state.parents[parent]
        expandedNodes=expandedNodes+ancestors
        if(state.selectedKey!=null) expanded=true
    }
    val rows=flattenSceneTree(root,expandedNodes)
    Column(modifier.editorContextMenu { sceneCreationMenu(state,editable,onEditEntity) }) {
        Text("Scene Tree",style=MaterialTheme.typography.subtitle1)
        LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
            item(key=root.id) {
                Row(verticalAlignment=androidx.compose.ui.Alignment.CenterVertically,modifier=Modifier.fillMaxWidth().editorContextMenu(beforeOpen={state.select(null)},node=true) { sceneCreationMenu(state,editable,onEditEntity) }.background(if(state.selectedKey==null) MaterialTheme.colors.primary.copy(alpha=0.16f) else MaterialTheme.colors.surface)
                    .selectable(selected=state.selectedKey==null,onClick={state.select(null)})) {
                    TreeExpander(expanded,true) { expanded=!expanded }
                    TreeIcon("Scene")
                    Spacer(Modifier.width(6.dp))
                    Text(root.label,Modifier.weight(1f).padding(vertical=7.dp),style=MaterialTheme.typography.subtitle2)
                }
            }
            if(expanded) items(rows,key={it.first.id}) { (node,depth) ->
                val selected=node.entityKey==state.selectedKey
                Row(verticalAlignment=androidx.compose.ui.Alignment.CenterVertically,modifier=Modifier.fillMaxWidth().editorContextMenu(beforeOpen={state.select(node.entityKey)},node=true) { sceneCreationMenu(state,editable,onEditEntity) }.onGloballyPositioned { onEntityBounds(node.entityKey!!,it.boundsInWindow()) }
                    .background(if(selected) MaterialTheme.colors.primary.copy(alpha=0.2f) else MaterialTheme.colors.surface)
                    .selectable(selected=selected,onClick={state.select(node.entityKey)})
                    .padding(start=(depth*16).coerceAtMost(160).dp,top=4.dp,bottom=4.dp,end=8.dp)) {
                    TreeExpander(node.id in expandedNodes,node.children.isNotEmpty()) {
                        expandedNodes=if(node.id in expandedNodes) expandedNodes-node.id else expandedNodes+node.id
                    }
                    TreeIcon(if(state.entities.first { it.document.key==node.entityKey }.document.components.containsKey("Camera2D")) "Camera" else "Entity")
                    Spacer(Modifier.width(6.dp))
                    Text(node.label,Modifier.weight(1f),color=if(selected) MaterialTheme.colors.primary else MaterialTheme.colors.onSurface)
                    if(state.entities.firstOrNull{it.document.key==node.entityKey}?.fieldErrors?.isNotEmpty()==true)
                        Text(" !",color=MaterialTheme.colors.error)
                }
            }
        }
    }
}
