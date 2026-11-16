package sle.editor.project.assets

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.draw.drawBehind
import sle.editor.ui.*
import sle.editor.ui.controls.*
import sle.editor.ui.theme.*
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
    val colors=MaterialTheme.colors
    val tokens=LocalEditorColors.current
    val metrics=LocalEditorMetrics.current
    val border=tokens.separator
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
    var showDetails by remember { mutableStateOf(false) }
    @Composable fun details(modifier: Modifier) {
        Column(modifier.drawBehind { drawRect(border,size=androidx.compose.ui.geometry.Size(metrics.borderWidth.toPx(),size.height)) }.padding(metrics.panelPadding).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(metrics.gap)) {
            val selected=state.selected?.let(entryMap::get)
            if(selected!=null) {
                if(commands!=null && selected.kind!=AssetKind.Link) Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(metrics.gap)) {
                    EditorButton("Rename…",onClick=commands.rename,enabled=!commands.busy,modifier=measured("AssetRename"))
                    EditorButton("Move…",onClick=commands.move,enabled=!commands.busy,modifier=measured("AssetMove"))
                    EditorButton("Delete…",onClick=commands.delete,enabled=!commands.busy,modifier=measured("AssetDelete"))
                }
                Text(selected.name,style=LocalEditorTypography.current.heading)
                Text(selected.path,style=MaterialTheme.typography.caption)
                state.snapshot?.idsByPath?.get(selected.path)?.let { Text("UUID: $it",style=MaterialTheme.typography.caption) }
                if(commands!=null && selected.kind!=AssetKind.Link) {
                    EditorButton("Reveal in Explorer",onClick=commands.reveal,enabled=!commands.busy,style=EditorButtonStyle.Ghost)
                    if(selected.kind==AssetKind.File) {
                        EditorButton("Open Externally",onClick=commands.open,enabled=!commands.busy,style=EditorButtonStyle.Ghost)
                        EditorButton("Assign New UUID…",onClick=commands.repair,enabled=!commands.busy,style=EditorButtonStyle.Ghost)
                    }
                }
                Text(type(selected),style=MaterialTheme.typography.caption)
                if(selected.kind==AssetKind.File) Text(size(selected.size),style=MaterialTheme.typography.caption)
                Text(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(selected.modified)),style=MaterialTheme.typography.caption)
            }
        }
    }
    BoxWithConstraints(modifier) {
        val wide=maxWidth>=metrics.assetDetailsBreakpoint
        val showType=maxWidth>=metrics.assetTypeBreakpoint
        val showSize=maxWidth>=metrics.assetSizeBreakpoint
        val detailHeight=maxHeight*.35f
        val folderWidth=(maxWidth*.25f).coerceIn(metrics.assetFolderMinimum,metrics.assetFolderMaximum)
        Column(Modifier.fillMaxSize().editorContextMenu {
            listOf(EditorMenuEntry("Refresh",action=state::refresh),EditorMenuEntry("Up",state.folder.isNotEmpty()) { state.navigate(state.folder.substringBeforeLast('/',"")) })
        }) {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal=metrics.gap,vertical=metrics.gap/2),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(metrics.gap/2)) {
                if(commands!=null) EditorButton(if(commands.busy) "Working…" else "Import…",onClick=commands.importFiles,enabled=!commands.busy,modifier=measured("AssetImport"))
                if(commands!=null) {
                    EditorButton("New Folder…",onClick=commands.createFolder,enabled=!commands.busy,modifier=measured("AssetNewFolder"))
                    EditorButton("Recover Deleted…",onClick=commands.recover,enabled=!commands.busy,modifier=measured("AssetRecover"),style=EditorButtonStyle.Ghost)
                }
                EditorToolbarDivider()
                EditorButton("Up",onClick={state.navigate(state.folder.substringBeforeLast('/',""))},enabled=state.folder.isNotEmpty(),modifier=measured("AssetUp"),style=EditorButtonStyle.Ghost)
                EditorButton(if(state.refreshing) "Scanning…" else "Refresh",onClick=state::refresh,modifier=measured("AssetRefresh"),style=EditorButtonStyle.Ghost)
                if(!wide) EditorToggle("Details",showDetails,{showDetails=it},enabled=state.selected!=null,style=EditorButtonStyle.Ghost)
                EditorToolbarDivider()
                Row(Modifier.widthIn(max=metrics.assetBreadcrumbWidth).horizontalScroll(rememberScrollState()),verticalAlignment=Alignment.CenterVertically) {
                    EditorButton(assetRootName,onClick={state.navigate("")},modifier=measured("AssetCrumb:"),style=EditorButtonStyle.Ghost)
                    var path=""
                    state.folder.split('/').filter { it.isNotEmpty() }.forEach { part ->
                        path=if(path.isEmpty()) part else "$path/$part"
                        val target=path
                        EditorGlyphIcon(EditorGlyph.ChevronRight,tokens.textSecondary)
                        EditorButton(part,onClick={state.navigate(target)},modifier=measured("AssetCrumb:$target"),style=EditorButtonStyle.Ghost)
                    }
                }
                Text("${children.size} ${if(children.size==1) "item" else "items"}",color=tokens.textSecondary,style=MaterialTheme.typography.caption)
            }
            EditorSeparator()
            commands?.message?.let { Text(it,style=MaterialTheme.typography.caption,modifier=Modifier.padding(horizontal=metrics.panelPadding)) }
            state.snapshot?.errors?.let { errors -> if(errors.isNotEmpty()) {
                var showErrors by remember { mutableStateOf(false) }
                EditorButton("${errors.size} asset issue(s): ${errors.first()}",{showErrors=true},style=EditorButtonStyle.Ghost)
                val dialogAppearance=sle.editor.ui.theme.LocalEditorAppearance.current
                if(showErrors) androidx.compose.ui.window.DialogWindow(onCloseRequest={showErrors=false},title="Asset issues") {
                    sle.editor.ui.EditorTheme(dialogAppearance) { Surface(Modifier.fillMaxSize()) {
                        Column(Modifier.fillMaxSize().padding(metrics.panelPadding).verticalScroll(rememberScrollState())) { errors.forEach { Text(it);Spacer(Modifier.height(metrics.gap)) } }
                    } }
                }
            } }
            if(state.snapshot==null) LinearProgressIndicator(Modifier.fillMaxWidth())
            Row(Modifier.weight(1f).fillMaxWidth()) {
                LazyColumn(Modifier.width(folderWidth).fillMaxHeight().drawBehind { val t=metrics.borderWidth.toPx();drawRect(border,androidx.compose.ui.geometry.Offset(size.width-t,0f),androidx.compose.ui.geometry.Size(t,size.height)) }.then(measured("AssetFolders"))) {
                    items(rows,key={it.first}) { (path,depth) ->
                        val selected=state.folder==path
                        EditorTreeRow(if(path.isEmpty()) assetRootName else path.substringAfterLast('/'),selected,{state.navigate(path)},
                            measured("AssetFolder:$path"),depth=depth.coerceAtMost(6),help=path.ifEmpty { assetRootName },leading={
                                TreeExpander(path in state.expanded,folderChildren[path].orEmpty().isNotEmpty()) { state.toggle(path) }
                                TreeIcon("Folder")
                            })
                    }
                }
                Column(Modifier.weight(1f).fillMaxHeight()) {
                    Row(Modifier.fillMaxWidth().padding(horizontal=metrics.panelPadding,vertical=metrics.gap)) {
                        Text("Name",Modifier.weight(1f),color=tokens.textSecondary,style=MaterialTheme.typography.caption)
                        if(showType) Text("Type",Modifier.width(metrics.assetTypeWidth),color=tokens.textSecondary,style=MaterialTheme.typography.caption)
                        if(showSize) Text("Size",Modifier.width(metrics.assetSizeWidth),color=tokens.textSecondary,style=MaterialTheme.typography.caption)
                    }
                    EditorSeparator()
                    Box(Modifier.weight(1f).fillMaxWidth()) {
                    if(snapshot!=null && children.isEmpty()) EditorEmptyState("Empty folder",
                        if(commands!=null) "Import files or create a folder to get started." else null)
                    LazyColumn(Modifier.fillMaxSize().then(measured("AssetFiles"))) {
                        items(children,key={it.path}) { entry ->
                            Row(Modifier.fillMaxWidth().then(measured("AssetFile:${entry.path}")).background(if(state.selected==entry.path) tokens.selectedBackground else androidx.compose.ui.graphics.Color.Transparent)
                                .onPointerEvent(PointerEventType.Press) { if(it.buttons.isPrimaryPressed) state.select(entry.path) }
                                .combinedClickable(onClick={state.select(entry.path)},onDoubleClick={if(entry.kind==AssetKind.Directory) state.navigate(entry.path) else if(entry.kind==AssetKind.File) onOpen(entry.path)})
                                .heightIn(min=metrics.treeRow).padding(horizontal=metrics.panelPadding,vertical=metrics.gap/2),verticalAlignment=Alignment.CenterVertically) {
                                TreeIcon(if(entry.kind==AssetKind.Directory) "Folder" else if(entry.kind==AssetKind.Link) "Link" else "File")
                                Spacer(Modifier.width(metrics.gap))
                                Text(entry.name,Modifier.weight(1f),maxLines=1,overflow=TextOverflow.Ellipsis,style=MaterialTheme.typography.body2)
                                if(showType) Text(type(entry),Modifier.width(metrics.assetTypeWidth),color=tokens.textSecondary,style=MaterialTheme.typography.caption)
                                if(showSize) Text(if(entry.kind==AssetKind.File) size(entry.size) else "—",Modifier.width(metrics.assetSizeWidth),color=tokens.textSecondary,style=MaterialTheme.typography.caption)
                            }
                        }
                    }
                    }
                }
                if(wide) details(Modifier.width(metrics.assetDetailsWidth).fillMaxHeight())
            }
            if(!wide && showDetails && state.selected!=null) { EditorSeparator();details(Modifier.fillMaxWidth().heightIn(max=detailHeight)) }
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
