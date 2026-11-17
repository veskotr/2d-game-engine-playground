package sle.editor.ui

import sle.editor.ui.controls.*
import sle.editor.ui.theme.*
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
import androidx.compose.foundation.focusable
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectDragGestures

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
    val focus=remember { FocusRequester() }
    fun choose(key: String?) { state.select(key);runCatching { focus.requestFocus() } }
    fun attempt(action: ()->Unit) { try { state.selected?.endGesture();action() } catch(failure: Exception) { state.error=failure.message ?: "Scene operation failed" } }
    // Delete removes the selected subtree, Ctrl+D duplicates it, arrows move the selection.
    val keys=Modifier.focusRequester(focus).focusable().onPreviewKeyEvent { event ->
        if(event.type!=KeyEventType.KeyDown) return@onPreviewKeyEvent false
        val visible=if(expanded) rows.mapNotNull { it.first.entityKey } else emptyList()
        val index=visible.indexOf(state.selectedKey)
        when {
            editable && state.selected!=null && (event.key==Key.Delete || event.key==Key.Backspace) -> { attempt { deleteSceneSelection(state,editable) };true }
            editable && state.selected!=null && event.isCtrlPressed && event.key==Key.D -> { attempt { state.duplicateSelected() };true }
            event.key==Key.DirectionDown && visible.isNotEmpty() -> { state.select(visible[(index+1).coerceAtMost(visible.lastIndex)]);true }
            event.key==Key.DirectionUp && visible.isNotEmpty() -> { state.select(if(index<=0) null else visible[index-1]);true }
            event.key==Key.Enter && state.selectedKey!=null && onEditEntity!=null && editable -> { onEditEntity(state.selectedKey!!);true }
            event.key==Key.Escape && EntityDrag.dragging!=null -> { EntityDrag.cancel();true }
            else -> false
        }
    }
    Column(modifier.then(keys).editorContextMenu { sceneCreationMenu(state,editable,onEditEntity) }) {
        LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
            item(key=root.id) {
                EditorTreeRow(root.label,state.selectedKey==null,{choose(null)},
                    Modifier.editorContextMenu(beforeOpen={state.select(null)},node=true) { sceneCreationMenu(state,editable,onEditEntity) },
                    help=root.label,leading={ TreeExpander(expanded,true) { expanded=!expanded };TreeIcon("Scene") })
            }
            if(expanded) items(rows,key={it.first.id}) { (node,depth) ->
                val selected=node.entityKey==state.selectedKey
                EditorTreeRow(node.label+(if(state.entities.firstOrNull { it.document.key==node.entityKey }?.fieldErrors?.isNotEmpty()==true) " !" else ""),
                    selected,{choose(node.entityKey)},
                    Modifier.editorContextMenu(beforeOpen={state.select(node.entityKey)},node=true) { sceneCreationMenu(state,editable,onEditEntity) }
                        .onGloballyPositioned { onEntityBounds(node.entityKey!!,it.boundsInWindow()) }
                        .then(if(editable && state.projectCreatePrefab!=null) Modifier.pointerInput(node.entityKey,state.key,state.file) {
                            // Dragging a row onto an Assets folder saves the subtree as a prefab.
                            val payload=EntityDragPayload(state.key,state.file,node.entityKey!!,node.label)
                            fun pointer()=java.awt.MouseInfo.getPointerInfo()?.location ?: java.awt.Point(Int.MIN_VALUE,Int.MIN_VALUE)
                            detectDragGestures(
                                onDragStart={ choose(node.entityKey);EntityDrag.move(payload,pointer()) },
                                onDragEnd={ EntityDrag.release(pointer()) },
                                onDragCancel={ EntityDrag.cancel() },
                            ) { change,_ -> change.consume();if(EntityDrag.dragging!=null) EntityDrag.move(payload,pointer()) }
                        } else Modifier),
                    depth=depth.coerceAtMost(10),help=node.label,leading={
                        TreeExpander(node.id in expandedNodes,node.children.isNotEmpty()) {
                            expandedNodes=if(node.id in expandedNodes) expandedNodes-node.id else expandedNodes+node.id
                        }
                        TreeIcon(if(state.entities.first { it.document.key==node.entityKey }.document.components.containsKey("Camera2D")) "Camera" else "Entity")
                    })
            }
        }
    }
}
