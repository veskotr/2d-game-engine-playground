package sle.editor.project.assets

import sle.editor.ui.EditorDialogs
import sle.editor.ui.EditorFileChooser
import androidx.compose.runtime.*
import kotlinx.coroutines.*
import sle.editor.project.ProjectWorkspaceState
import java.awt.Desktop
import java.nio.file.*
import javax.swing.JFileChooser
import javax.swing.JOptionPane

internal data class ProjectAssetCommands(
    val importFiles: ()->Unit,
    val importPaths: (List<Path>)->Unit,
    val browse: (String,Boolean,(Path)->Unit)->Unit,
    val reveal: ()->Unit,val open: ()->Unit,val repair: ()->Unit,
    val busy: Boolean,val message: String?,
    val createFolder: ()->Unit={},val rename: ()->Unit={},val move: ()->Unit={},
    val delete: ()->Unit={},val recover: ()->Unit={},
)
internal val LocalProjectAssets=staticCompositionLocalOf<ProjectAssetCommands?> { null }

@Composable
internal fun projectAssetControls(project: ProjectWorkspaceState,browser: AssetBrowserState?,owner: java.awt.Window,canMutate: ()->Boolean={true}): ProjectAssetCommands? {
    val opened=project.opened ?: return null
    val assetRoot=opened.root.resolve(opened.document.assetRoot)
    val scope=rememberCoroutineScope()
    var busy by remember(opened.file) { mutableStateOf(false) }
    var message by remember(opened.file) { mutableStateOf<String?>(null) }
    val current by rememberUpdatedState(project.opened)
    val mutable by rememberUpdatedState(canMutate)
    // Capture the source project and selected destination before launching I/O.
    fun run(action: suspend ()->String?) {
        if(busy || project.busy) return
        busy=true;message=null
        scope.launch {
            try { val result=action();if(current?.file==opened.file) { message=result;browser?.refresh() } }
            catch(failure: Exception) { if(failure is CancellationException) throw failure;message=failure.message ?: "Asset operation failed" }
            finally { busy=false }
        }
    }
    suspend fun importOne(path: Path,folder: String): AssetImportResult {
        val store=ProjectAssetImports(opened.root,assetRoot)
        try { return withContext(Dispatchers.IO) { store.importAsset(path,folder) } }
        catch(failure: IllegalArgumentException) {
            if(failure.message?.startsWith("Name conflict:")!=true) throw failure
            val choice=EditorDialogs.showOptionDialog(null,
                "${path.fileName} already exists in $folder. Replace merges folders and retains existing asset IDs.",
                "Import name conflict",JOptionPane.DEFAULT_OPTION,JOptionPane.QUESTION_MESSAGE,null,
                arrayOf("Rename","Skip","Replace"),"Rename")
            if(choice<0 || choice==1) return AssetImportResult(null)
            return withContext(Dispatchers.IO) { store.importAsset(path,folder,if(choice==0) ImportCollision.Rename else ImportCollision.Replace) }
        }
    }
    val importPaths: (List<Path>)->Unit={ paths ->
        val folder=browser?.folder.orEmpty()
        run {
            var imported=0;var skipped=0
            for(path in paths) { if(importOne(path,folder).path!=null) imported++ else skipped++ }
            "$imported imported/reused; $skipped skipped"
        }
    }
    fun managed(action: suspend (ProjectAssetOperations)->String?) {
        if(!mutable()) { message="Stop playback and use New in both scene and prefab workspaces before managing files; save your drafts first.";return }
        run {
            project.busy=true
            // Exclude all authoring/menu/native-canvas input, including while the
            // worker waits for the asset lock. Child confirmations use their own owner.
            val wasEnabled=owner.isEnabled
            owner.isEnabled=false
            try {
                require(current?.file==opened.file && mutable()) { "Project or workspace changed" }
                action(ProjectAssetOperations(opened))
            } finally { owner.isEnabled=wasEnabled;project.busy=false }
        }
    }
    fun moveSelected(rename: Boolean) {
        val selected=browser?.selected ?: return
        if(busy || project.busy) return
        val initial=if(rename) selected.substringAfterLast('/') else selected
        val input=EditorDialogs.showInputDialog(null,if(rename) "New name" else "Destination path beneath Assets (including the name)",initial) ?: return
        val destination=if(rename) selected.substringBeforeLast('/',"").let { if(it.isEmpty()) input else "$it/$input" } else input
        if(EditorDialogs.showConfirmDialog(null,
            "Move $selected to $destination? UUID references stay valid. Lua require, dynamic paths and nested XML/animation links are not rewritten; update those yourself.",
            "Move project asset",JOptionPane.OK_CANCEL_OPTION)!=JOptionPane.OK_OPTION) return
        managed { store -> withContext(Dispatchers.IO) { store.move(selected,destination) };"Moved to $destination" }
    }
    return ProjectAssetCommands(
        importFiles={
            val chooser=EditorFileChooser(assetRoot.toFile()).apply {
                dialogTitle="Import files or folders into ${browser?.folder?.ifEmpty { "Assets" } ?: "Assets"}"
                fileSelectionMode=JFileChooser.FILES_AND_DIRECTORIES;isMultiSelectionEnabled=true
            }
            if(chooser.showOpenDialog(null)==JFileChooser.APPROVE_OPTION) importPaths(chooser.selectedFiles.map { it.toPath() })
        },importPaths=importPaths,
        browse={display,imageOnly,accept ->
            val chooser=EditorFileChooser(assetRoot.toFile()).apply {
                dialogTitle=if(imageOnly) "Choose project texture or import image" else "Choose project asset or import file"
                fileSelectionMode=JFileChooser.FILES_ONLY
                if(imageOnly) fileFilter=javax.swing.filechooser.FileNameExtensionFilter("Images","png","jpg","jpeg","bmp","tga","gif","psd","hdr","pic","pnm","ppm","pgm")
            }
            if(chooser.showOpenDialog(null)==JFileChooser.APPROVE_OPTION) {
                val selected=chooser.selectedFile.toPath()
                val folder=if(imageOnly) "Textures" else if(selected.fileName.toString().endsWith(".lua",true)) "Scripts" else browser?.folder.orEmpty()
                run {
                    val destination=withContext(Dispatchers.IO) {
                        // Default folders are organizational: a project may remove them.
                        if(Files.isDirectory(assetRoot.resolve(folder))) folder else ""
                    }
                    val imported=importOne(selected,destination)
                    if(current?.file==opened.file) imported.path?.let(accept)
                    null
                }
            }
        },
        reveal={
            val selected=browser?.selected ?: browser?.folder.orEmpty()
            run {
                withContext(Dispatchers.IO) {
                    val path=assetPath(opened.root,assetRoot,selected)
                    val desktop=Desktop.getDesktop()
                    if(desktop.isSupported(Desktop.Action.BROWSE_FILE_DIR)) desktop.browseFileDirectory(path.toFile())
                    else desktop.open((if(Files.isDirectory(path)) path else path.parent).toFile())
                };null
            }
        },
        open={
            val selected=browser?.selected
            if(selected!=null) run {
                withContext(Dispatchers.IO) {
                    val path=assetPath(opened.root,assetRoot,selected)
                    require(Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS)) { "Select a regular file" }
                    require(path.fileName.toString().substringAfterLast('.').lowercase() in setOf("lua","json","xml","txt","md","png","jpg","jpeg","bmp","gif","wav","ogg","mp3","flac")) { "No external-open action for this file type; use Reveal" }
                    Desktop.getDesktop().open(path.toFile())
                };null
            }
        },
        repair={
            val selected=browser?.selected
            if(selected!=null && EditorDialogs.showConfirmDialog(null,
                    "Assign a NEW UUID to $selected? References to its old identity will need repair.","Repair asset identity",JOptionPane.YES_NO_OPTION)==JOptionPane.YES_OPTION)
                run { withContext(Dispatchers.IO) { AssetIdentity.repair(opened.root,assetRoot,selected) };"Assigned new asset UUID" }
        },busy=busy || project.busy,message=message,
        createFolder={
            if(!busy && !project.busy) {
                val folder=browser?.folder.orEmpty()
                val name=EditorDialogs.showInputDialog(null,"New folder name")
                if(name!=null) run {
                    withContext(Dispatchers.IO) {
                        sle.editor.project.validateProjectFolderName(name)
                        ProjectAssetOperations(opened).createFolder(if(folder.isEmpty()) name else "$folder/$name")
                    };"Created folder $name"
                }
            }
        },rename={moveSelected(true)},move={moveSelected(false)},
        delete={
            val selected=browser?.selected
            if(selected!=null) managed { store ->
                val references=withContext(Dispatchers.IO) { store.references(selected) }
                val sources=references.map { it.source }.distinct()
                val warning=if(sources.isEmpty()) "No saved UUID references found." else "Referenced by:\n"+sources.take(20).joinToString("\n")+if(sources.size>20) "\n…" else ""
                val confirmed=EditorDialogs.showConfirmDialog(null,
                    "Move $selected to project trash?\n$warning\nReferenced documents may stop loading. Dynamic Lua/XML/module paths are not checked.\nRecover Deleted… can restore it; retain .sle/trash until recovery is no longer needed.",
                    "Delete project asset",JOptionPane.YES_NO_OPTION,JOptionPane.WARNING_MESSAGE)==JOptionPane.YES_OPTION
                if(confirmed) {
                    require(current?.file==opened.file && mutable()) { "Project or workspace changed" }
                    withContext(Dispatchers.IO) { store.delete(selected,allowReferenced=true) };"Moved $selected to .sle/trash"
                } else null
            }
        },recover={managed { store ->
            val receipts=withContext(Dispatchers.IO) { store.deletions() }
            if(receipts.isEmpty()) "No recoverable deletions" else {
                val choices=receipts.map { "${it.original} (${it.token})" }.toTypedArray()
                val choice=EditorDialogs.showInputDialog(null,"Select deleted asset to restore", "Recover deleted asset",JOptionPane.QUESTION_MESSAGE,null,choices,choices.first())
                val index=choices.indexOf(choice)
                if(index>=0) {
                    require(current?.file==opened.file && mutable()) { "Project or workspace changed" }
                    withContext(Dispatchers.IO) { store.restore(receipts[index]) };"Restored ${receipts[index].original}"
                } else null
            }
        }},
    )
}
