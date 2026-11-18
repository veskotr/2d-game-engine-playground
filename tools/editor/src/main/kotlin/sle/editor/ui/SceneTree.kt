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
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.isMetaPressed
import androidx.compose.ui.input.pointer.isShiftPressed
import androidx.compose.ui.ExperimentalComposeUiApi
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

/** How a Hierarchy row click changes the selection. */
internal enum class TreeClick { Replace, Toggle, Range, AddRange }

internal fun treeClick(ctrl: Boolean,shift: Boolean)=when {
    ctrl && shift -> TreeClick.AddRange; shift -> TreeClick.Range; ctrl -> TreeClick.Toggle; else -> TreeClick.Replace
}

/** Applies a Hierarchy click on [key]; [visible] is the visible row order used by Shift ranges. */
internal fun SceneWorkspaceState.treeSelect(key: String?,click: TreeClick,visible: List<String>) {
    if(key==null) { select(null);return }
    when(click) {
        TreeClick.Replace -> select(key)
        TreeClick.Toggle -> toggleSelection(key)
        TreeClick.Range -> extendSelection(key,visible)
        TreeClick.AddRange -> extendSelection(key,visible,additive=true)
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun SceneTree(state: SceneWorkspaceState,modifier: Modifier=Modifier,
    onEntityBounds: (String,Rect)->Unit={_,_->},editable: Boolean=true,onEditEntity: ((String)->Unit)?=null) {
    val root=sceneTree(state.document())
    // A tooltip cannot show during a drag, so the refusal goes to the status bar.
    val notices=LocalEditorNotices.current
    fun refuseMultiDrag() { val text="Select a single entity to save it as an entity template";notices?.warning(text) ?: run { state.error=text } }
    var expanded by remember(state.key) { mutableStateOf(true) }
    var expandedNodes by remember(state.key) { mutableStateOf<Set<String>>(emptySet()) }
    LaunchedEffect(state.selection.keys,state.parents) {
        val ancestors=mutableSetOf<String>()
        for(key in state.selection.keys) {
            var parent=state.parents[key]
            while(parent!=null && ancestors.add("entity:$parent")) parent=state.parents[parent]
        }
        expandedNodes=expandedNodes+ancestors
        if(!state.selection.isEmpty) expanded=true
    }
    val rows=flattenSceneTree(root,expandedNodes)
    val focus=remember { FocusRequester() }
    // Modifiers of the latest press, read by the row's click (a keyboard click has none).
    val press=remember { object { var click=TreeClick.Replace } }
    fun visibleKeys()=if(expanded) rows.mapNotNull { it.first.entityKey } else emptyList()
    fun choose(key: String?) {
        val click=press.click;press.click=TreeClick.Replace
        state.treeSelect(key,click,visibleKeys());runCatching { focus.requestFocus() }
    }
    // Arrows move the selection, Enter edits it and Esc clears it. Delete, Ctrl+D and Ctrl+A are registered
    // commands (command/EditCommands.kt) that the shortcut dispatcher runs while the Hierarchy has focus.
    val keys=Modifier.focusRequester(focus).focusable().onPreviewKeyEvent { event ->
        if(event.type!=KeyEventType.KeyDown) return@onPreviewKeyEvent false
        val visible=visibleKeys()
        val index=visible.indexOf(state.selectedKey)
        when {
            event.key==Key.DirectionDown && visible.isNotEmpty() -> { state.select(visible[(index+1).coerceAtMost(visible.lastIndex)]);true }
            event.key==Key.DirectionUp && visible.isNotEmpty() -> { state.select(if(index<=0) null else visible[index-1]);true }
            event.key==Key.Enter && state.selectedKey!=null && state.selection.size==1 && onEditEntity!=null && editable -> { onEditEntity(state.selectedKey!!);true }
            event.key==Key.Escape && EntityDrag.dragging!=null -> { EntityDrag.cancel();true }
            event.key==Key.Escape && !state.selection.isEmpty -> { state.select(null);true }
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
                val selected=node.entityKey in state.selection
                EditorTreeRow(node.label+(if(state.entities.firstOrNull { it.document.key==node.entityKey }?.fieldErrors?.isNotEmpty()==true) " !" else ""),
                    selected,{choose(node.entityKey)},
                    // Right-clicking a selected row keeps the selection, so the menu acts on the set.
                    Modifier.editorContextMenu(beforeOpen={ if(node.entityKey in state.selection) state.activate(node.entityKey!!) else state.select(node.entityKey) },node=true) { sceneCreationMenu(state,editable,onEditEntity) }
                        .onPointerEvent(PointerEventType.Press,PointerEventPass.Initial) { event ->
                            if(event.buttons.isPrimaryPressed) press.click=treeClick(event.keyboardModifiers.isCtrlPressed || event.keyboardModifiers.isMetaPressed,event.keyboardModifiers.isShiftPressed)
                        }
                        .onGloballyPositioned { onEntityBounds(node.entityKey!!,it.boundsInWindow()) }
                        .then(if(editable && state.projectCreatePrefab!=null) Modifier.pointerInput(node.entityKey,state.key,state.file) {
                            // Dragging a row onto an Assets folder saves the subtree as a prefab.
                            val payload=EntityDragPayload(state.key,state.file,node.entityKey!!,node.label)
                            fun pointer()=java.awt.MouseInfo.getPointerInfo()?.location ?: java.awt.Point(Int.MIN_VALUE,Int.MIN_VALUE)
                            detectDragGestures(
                                onDragStart={
                                    // Saving as a prefab works on one entity; dragging a multi-selection does nothing.
                                    if(state.selection.size>1 && node.entityKey in state.selection) refuseMultiDrag()
                                    else { press.click=TreeClick.Replace;choose(node.entityKey);EntityDrag.move(payload,pointer()) }
                                },
                                onDragEnd={ EntityDrag.release(pointer()) },
                                onDragCancel={ EntityDrag.cancel() },
                            ) { change,_ -> change.consume();if(EntityDrag.dragging!=null) EntityDrag.move(payload,pointer()) }
                        } else Modifier),
                    depth=depth.coerceAtMost(10),help=node.label,active=node.entityKey==state.selectedKey,leading={
                        TreeExpander(node.id in expandedNodes,node.children.isNotEmpty()) {
                            expandedNodes=if(node.id in expandedNodes) expandedNodes-node.id else expandedNodes+node.id
                        }
                        TreeIcon(if(state.entities.first { it.document.key==node.entityKey }.document.components.containsKey("Camera2D")) "Camera" else "Entity")
                    })
            }
        }
    }
}
