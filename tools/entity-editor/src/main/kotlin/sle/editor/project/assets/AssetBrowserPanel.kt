package sle.editor.project.assets

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import sle.editor.ui.*
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

internal fun assetBrowserMenu(state: AssetBrowserState?,visible: Boolean,onVisible: ()->Unit): List<EditorMenuEntry> = listOf(
    EditorMenuEntry(if(visible) "Hide Asset Browser" else "Show Asset Browser",state!=null,action=onVisible),
    EditorMenuEntry("Refresh Assets",state!=null) { state?.refresh() },
)

@OptIn(ExperimentalFoundationApi::class,ExperimentalComposeUiApi::class)
@Composable
internal fun AssetBrowserPanel(
    state: AssetBrowserState,assetRootName: String,modifier: Modifier=Modifier,
    commands: ProjectAssetCommands?=null,onOpen: (String)->Unit={},onBounds: (String,Rect)->Unit={_,_->},
) {
    var height by remember { mutableStateOf(220f) }
    val density=LocalDensity.current.density
    val colors=MaterialTheme.colors
    val border=colors.onSurface.copy(alpha=0.16f)
    fun measured(id: String)=Modifier.onGloballyPositioned { onBounds(id,it.boundsInWindow()) }
    val snapshot=state.snapshot
    val entryMap=snapshot?.byPath.orEmpty()
    val folderChildren=snapshot?.folderChildren.orEmpty()
    val children=remember(snapshot,state.folder) { state.children }
    val rows=remember(folderChildren,state.expanded) { buildList {
        val queue=java.util.ArrayDeque<Pair<String,Int>>()
        queue.add("" to 0)
        while(queue.isNotEmpty()) {
            val item=queue.removeLast();add(item)
            if(item.first in state.expanded) folderChildren[item.first].orEmpty().asReversed().forEach { queue.add(it.path to item.second+1) }
        }
    } }
    Column(modifier.height(height.dp).border(1.dp,border).editorContextMenu {
        listOf(EditorMenuEntry("Refresh",action=state::refresh),EditorMenuEntry("Up",state.folder.isNotEmpty()) { state.navigate(state.folder.substringBeforeLast('/',"")) })
    }) {
        Box(Modifier.fillMaxWidth().height(5.dp).background(border).pointerInput(density) {
            detectVerticalDragGestures { change,amount -> change.consume();height=(height-amount/density).coerceIn(150f,420f) }
        })
        Row(Modifier.fillMaxWidth().padding(horizontal=6.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(4.dp)) {
            Text("Assets",style=MaterialTheme.typography.subtitle2)
            if(commands!=null) TextButton(onClick=commands.importFiles,enabled=!commands.busy,modifier=measured("AssetImport")) { Text(if(commands.busy) "Working…" else "Import…") }
            if(commands!=null) {
                TextButton(onClick=commands.createFolder,enabled=!commands.busy,modifier=measured("AssetNewFolder")) { Text("New Folder…") }
                TextButton(onClick=commands.recover,enabled=!commands.busy,modifier=measured("AssetRecover")) { Text("Recover Deleted…") }
            }
            TextButton(onClick={state.navigate(state.folder.substringBeforeLast('/',""))},enabled=state.folder.isNotEmpty(),modifier=measured("AssetUp")) { Text("Up") }
            TextButton(onClick=state::refresh,modifier=measured("AssetRefresh")) { Text(if(state.refreshing) "Scanning…" else "Refresh") }
            Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()),verticalAlignment=Alignment.CenterVertically) {
                TextButton(onClick={state.navigate("")},modifier=measured("AssetCrumb:")) { Text(assetRootName) }
                var path=""
                state.folder.split('/').filter { it.isNotEmpty() }.forEach { part ->
                    path=if(path.isEmpty()) part else "$path/$part"
                    val target=path
                    Text("/")
                    TextButton(onClick={state.navigate(target)},modifier=measured("AssetCrumb:$target")) { Text(part) }
                }
            }
            Text("${children.size} ${if(children.size==1) "item" else "items"}",style=MaterialTheme.typography.caption)
        }
        commands?.message?.let { Text(it,style=MaterialTheme.typography.caption,modifier=Modifier.padding(horizontal=8.dp)) }
        state.snapshot?.errors?.let { errors -> if(errors.isNotEmpty()) {
            var showErrors by remember { mutableStateOf(false) }
            TextButton(onClick={showErrors=true}) { Text("${errors.size} asset issue(s): ${errors.first()}",color=colors.error,maxLines=1,overflow=TextOverflow.Ellipsis) }
            if(showErrors) androidx.compose.ui.window.DialogWindow(onCloseRequest={showErrors=false},title="Asset issues") {
                Column(Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())) { errors.forEach { Text(it);Spacer(Modifier.height(8.dp)) } }
            }
        } }
        if(state.snapshot==null) LinearProgressIndicator(Modifier.fillMaxWidth())
        Row(Modifier.weight(1f).fillMaxWidth()) {
            LazyColumn(Modifier.width(200.dp).fillMaxHeight().border(1.dp,border).then(measured("AssetFolders"))) {
                items(rows,key={it.first}) { (path,depth) ->
                    val selected=state.folder==path
                    Row(Modifier.fillMaxWidth().then(measured("AssetFolder:$path")).background(if(selected) colors.primary.copy(alpha=0.16f) else colors.surface)
                        .clickable { state.navigate(path) }.padding(start=(depth*12).coerceAtMost(100).dp,end=4.dp,top=2.dp,bottom=2.dp),verticalAlignment=Alignment.CenterVertically) {
                        TreeExpander(path in state.expanded,folderChildren[path].orEmpty().isNotEmpty()) { state.toggle(path) }
                        TreeIcon("Folder");Spacer(Modifier.width(5.dp))
                        Text(if(path.isEmpty()) assetRootName else path.substringAfterLast('/'),maxLines=1,overflow=TextOverflow.Ellipsis,style=MaterialTheme.typography.body2)
                    }
                }
            }
            Column(Modifier.weight(1f).fillMaxHeight()) {
                Row(Modifier.fillMaxWidth().padding(horizontal=8.dp,vertical=4.dp)) {
                    Text("Name",Modifier.weight(1f),style=MaterialTheme.typography.caption)
                    Text("Type",Modifier.width(90.dp),style=MaterialTheme.typography.caption)
                    Text("Size",Modifier.width(70.dp),style=MaterialTheme.typography.caption)
                }
                Divider()
                LazyColumn(Modifier.weight(1f).fillMaxWidth().then(measured("AssetFiles"))) {
                    items(children,key={it.path}) { entry ->
                        Row(Modifier.fillMaxWidth().then(measured("AssetFile:${entry.path}")).background(if(state.selected==entry.path) colors.primary.copy(alpha=0.16f) else colors.surface)
                            .onPointerEvent(PointerEventType.Press) { if(it.buttons.isPrimaryPressed) state.select(entry.path) }
                            .combinedClickable(onClick={state.select(entry.path)},onDoubleClick={if(entry.kind==AssetKind.Directory) state.navigate(entry.path) else if(entry.kind==AssetKind.File) onOpen(entry.path)})
                            .padding(horizontal=8.dp,vertical=5.dp),verticalAlignment=Alignment.CenterVertically) {
                            TreeIcon(if(entry.kind==AssetKind.Directory) "Folder" else if(entry.kind==AssetKind.Link) "Link" else "File")
                            Spacer(Modifier.width(6.dp))
                            Text(entry.name,Modifier.weight(1f),maxLines=1,overflow=TextOverflow.Ellipsis,style=MaterialTheme.typography.body2)
                            Text(type(entry),Modifier.width(90.dp),style=MaterialTheme.typography.caption)
                            Text(if(entry.kind==AssetKind.File) size(entry.size) else "—",Modifier.width(70.dp),style=MaterialTheme.typography.caption)
                        }
                    }
                }
            }
            Column(Modifier.width(240.dp).fillMaxHeight().border(1.dp,border).padding(8.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(5.dp)) {
                val selected=state.selected?.let(entryMap::get)
                if(selected!=null) {
                    if(commands!=null && selected.kind!=AssetKind.Link) Row {
                        TextButton(onClick=commands.rename,enabled=!commands.busy,modifier=measured("AssetRename"),contentPadding=PaddingValues(2.dp)) { Text("Rename…") }
                        TextButton(onClick=commands.move,enabled=!commands.busy,modifier=measured("AssetMove"),contentPadding=PaddingValues(2.dp)) { Text("Move…") }
                        TextButton(onClick=commands.delete,enabled=!commands.busy,modifier=measured("AssetDelete"),contentPadding=PaddingValues(2.dp)) { Text("Delete…") }
                    }
                    Text(selected.name,style=MaterialTheme.typography.subtitle2)
                    Text(selected.path,style=MaterialTheme.typography.caption)
                    snapshot?.idsByPath?.get(selected.path)?.let { Text("UUID: $it",style=MaterialTheme.typography.caption) }
                    if(commands!=null && selected.kind!=AssetKind.Link) {
                        TextButton(onClick=commands.reveal,enabled=!commands.busy) { Text("Reveal in Explorer") }
                        if(selected.kind==AssetKind.File) {
                            TextButton(onClick=commands.open,enabled=!commands.busy) { Text("Open Externally") }
                            TextButton(onClick=commands.repair,enabled=!commands.busy) { Text("Assign New UUID…") }
                        }
                    }
                    Text(type(selected),style=MaterialTheme.typography.caption)
                    if(selected.kind==AssetKind.File) Text(size(selected.size),style=MaterialTheme.typography.caption)
                    Text(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(selected.modified)),style=MaterialTheme.typography.caption)
                }
            }
        }
    }
}

private fun type(entry: AssetEntry): String = when(entry.kind) {
    AssetKind.Directory -> "Folder"
    AssetKind.Link -> "Link"
    AssetKind.File -> if(entry.name.endsWith(".scene.json",true)) "Scene" else if(entry.name.endsWith(".prefab.json",true)) "Prefab" else when(entry.name.substringAfterLast('.').lowercase()) {
        "lua" -> "Script";"slescene" -> "Scene";"sleprefab" -> "Prefab";"json" -> "JSON"
        "png","jpg","jpeg","bmp","tga","gif","psd","hdr" -> "Image"
        "ogg","wav","mp3","flac" -> "Audio";"xml" -> "UI / XML"
        else -> "File"
    }
}
private fun size(bytes: Long): String = when {
    bytes>=1024*1024 -> "%.1f MiB".format(bytes/(1024.0*1024))
    bytes>=1024 -> "%.1f KiB".format(bytes/1024.0)
    else -> "$bytes B"
}
