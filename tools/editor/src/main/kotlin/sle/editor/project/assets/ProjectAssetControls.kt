package sle.editor.project.assets

import sle.editor.ui.dialog.EditorPrompts
import sle.editor.ui.EditorFileChooser
import androidx.compose.runtime.*
import kotlinx.coroutines.*
import sle.editor.project.ProjectWorkspaceState
import java.awt.Desktop
import java.nio.file.*
import javax.swing.JFileChooser

internal data class ProjectAssetCommands(
    val importFiles: ()->Unit,
    val importPaths: (List<Path>)->Unit,
    val browse: (String,Boolean,(Path)->Unit)->Unit,
    val reveal: ()->Unit,val open: ()->Unit,val repair: ()->Unit,
    val busy: Boolean,val message: String?,
    val createFolder: ()->Unit={},val rename: ()->Unit={},val move: ()->Unit={},
    val delete: ()->Unit={},val recover: ()->Unit={},
    val findReferences: (String) -> Unit = {},
    val createStateMachine: ((String, (Path) -> Unit) -> Unit)? = null,
    val snapshot: AssetSnapshot? = null,
    val projectRoot: Path? = null,
    val assetRoot: Path? = null,
    val editRegions: (String, (List<SpriteRegion>) -> List<SpriteRegion>) -> Unit = { _, _ -> },
    val setTextureFilter: (String, AssetTextureFilter?) -> Unit = { _, _ -> },
    /**
     * Adds any missing grid regions to the texture, then writes a v2 tileset at the
     * asset-relative path with one tile per grid cell: (texture, cells, tile width, tile height, path).
     */
    val createTileset: ((String, List<SpriteRectangle>, Int, Int, String, sle.editor.model.TilesetGrid) -> Unit)? = null,
    val openTileset: (String) -> Unit = {},
    /** Starts New Tileset…: slices the selected texture, or asks for one (which can import) first. */
    val newTileset: () -> Unit = {},
    /** Texture whose slicer New Tileset… opened; the window hosts that dialog while it is set. */
    val tilesetTexture: String? = null,
    val closeTileset: () -> Unit = {},
)

/** Default path for a new tileset: next to the texture, named after it, unused in [snapshot]. */
internal fun defaultTilesetPath(texture: String, snapshot: AssetSnapshot?): String {
    val folder = texture.substringBeforeLast('/', "")
    val stem = texture.substringAfterLast('/').substringBeforeLast('.')
    fun candidate(index: Int) = (if (folder.isEmpty()) "" else "$folder/") +
        (if (index == 1) stem else "$stem $index") + ".tileset.json"
    var index = 1
    while (snapshot?.byPath?.containsKey(candidate(index)) == true) index++
    return candidate(index)
}

/** Regions for a tileset's tiles: one per grid cell, the first region that covers it. */
internal fun tilesetRegions(regions: List<SpriteRegion>, cells: List<SpriteRectangle>): List<SpriteRegion> {
    val wanted = cells.toSet()
    return regions.filter { it.rectangle() in wanted }.distinctBy { it.rectangle() }
}
internal val LocalProjectAssets=staticCompositionLocalOf<ProjectAssetCommands?> { null }

@Composable
internal fun projectAssetControls(project: ProjectWorkspaceState,browser: AssetBrowserState?,owner: java.awt.Window,canMutate: ()->Boolean={true},
    onTextureFilterChanged: () -> Unit = {},
    openDocument: (Path) -> Unit = {},
    openProjectSettings: () -> Unit = {},
    /** A tileset was created at this asset-relative path. */
    onTilesetCreated: (String) -> Unit = {},
    openTileset: (String) -> Unit = {},
): ProjectAssetCommands? {
    val opened=project.opened ?: return null
    val assetRoot=opened.root.resolve(opened.document.assetRoot)
    val activation = project.activationRevision
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
            val choice=EditorPrompts.choose("Import name conflict",
                "${path.fileName} already exists in ${folder.ifEmpty { "Assets" }}. Replace merges folders and retains existing asset IDs.",
                listOf("Rename","Skip","Replace"),default=0,cancel=null)
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
        if(!mutable()) { message="Stop playback and use New in both scene and entity template workspaces before managing files; save your drafts first.";return }
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
        val input=(if(rename) EditorPrompts.input("Rename Asset","New name",initial,confirm="Rename") { if(it.isBlank()) "Enter a name" else null }
            else EditorPrompts.input("Move Asset","Destination",initial,"Path beneath Assets, including the name.",confirm="Move") { if(it.isBlank()) "Enter a path" else null })?.trim() ?: return
        val destination=if(rename) selected.substringBeforeLast('/',"").let { if(it.isEmpty()) input else "$it/$input" } else input
        if(!EditorPrompts.confirm("Move Project Asset",
            "Move $selected to $destination? UUID references stay valid. Lua require, dynamic paths and nested XML/animation links are not rewritten; update those yourself.",
            "Move")) return
        managed { store -> withContext(Dispatchers.IO) { store.move(selected,destination) };"Moved to $destination" }
    }
    val browse: (String,Boolean,(Path)->Unit)->Unit = { _,imageOnly,accept ->
        val chosen=sle.editor.ui.EditorFilePicker.open(if(imageOnly) "Choose Project Texture or Import Image" else "Choose Project Asset or Import File",
            assetRoot.toFile(),if(imageOnly) sle.editor.ui.EditorFilePicker.images else null)
        if(chosen!=null) {
            val selected=chosen.toPath()
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
    }
    var tilesetTexture by remember(opened.file) { mutableStateOf<String?>(null) }
    return ProjectAssetCommands(
        projectRoot = opened.root, assetRoot = assetRoot,
        openTileset = openTileset,
        createTileset = { texture, cells, tileWidth, tileHeight, relative, grid ->
            run {
                check(project.activationRevision == activation && current?.assets == assetRoot) {
                    "The project changed."
                }
                require(relative.endsWith(".tileset.json", ignoreCase = true)) {
                    "Use a filename ending in .tileset.json"
                }
                require(cells.isNotEmpty()) { "The grid has no cells to turn into tiles" }
                withContext(Dispatchers.IO) {
                    var regions = emptyList<SpriteRegion>()
                    AssetIdentity.editRegions(opened.root, assetRoot, texture) { existing ->
                        appendGridRegions(existing, cells).also { regions = it }
                    }
                    val textureId = AssetIdentity.ensure(assetPath(opened.root, assetRoot, texture))
                    ProjectAssetOperations(opened).createFile(relative,
                        sle.editor.model.writeTileset(textureId, tileWidth, tileHeight, tilesetRegions(regions, cells), grid = grid))
                }
                if (project.activationRevision == activation && current?.assets == assetRoot) {
                    onTextureFilterChanged()
                    onTilesetCreated(relative)
                    tilesetTexture = null
                    browser?.refresh()
                }
                "Created $relative · open the Tile Palette to paint"
            }
        },
        newTileset = {
            val selected = browser?.selected?.takeIf { isTextureAsset(it) && browser.snapshot?.idsByPath?.containsKey(it) == true }
            if (selected != null) tilesetTexture = selected
            else browse("", true) { path ->
                val relative = if (path.startsWith(assetRoot)) assetRoot.relativize(path)
                    else assetRoot.toRealPath().relativize(path.toRealPath())
                tilesetTexture = relative.toString().replace('\\', '/')
            }
        },
        tilesetTexture = tilesetTexture,
        closeTileset = { tilesetTexture = null },
        createStateMachine = { relative, attach ->
            run {
                check(project.activationRevision == activation && current?.assets == assetRoot) {
                    "The project changed."
                }
                require(relative.endsWith(".statemachine.json", ignoreCase = true)) {
                    "Use a filename ending in .statemachine.json"
                }
                val path = withContext(Dispatchers.IO) {
                    ProjectAssetOperations(opened).createFile(relative, starterStateMachine.encodeToByteArray())
                }
                if (project.activationRevision == activation && current?.assets == assetRoot) attach(path)
                "Created $relative"
            }
        },
        importFiles={
            val chooser=EditorFileChooser(assetRoot.toFile()).apply {
                dialogTitle="Import files or folders into ${browser?.folder?.ifEmpty { "Assets" } ?: "Assets"}"
                fileSelectionMode=JFileChooser.FILES_AND_DIRECTORIES;isMultiSelectionEnabled=true
            }
            if(chooser.showOpenDialog(null)==JFileChooser.APPROVE_OPTION) importPaths(chooser.selectedFiles.map { it.toPath() })
        },importPaths=importPaths,
        browse=browse,
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
        findReferences = { path ->
            run {
                val results = withContext(Dispatchers.IO) { ProjectAssetOperations(opened).findReferences(path) }
                if (project.activationRevision == activation) {
                    val scopeText = "Saved files only: scenes, entity templates, and the project startup scene setting."
                    if (results.isEmpty()) {
                        EditorPrompts.message("Find References", "$path\n$scopeText\nNo direct references found.")
                    } else {
                        val choice = EditorPrompts.pickFromList("Find References", "$path\n$scopeText",
                            results.map { it.source }, "Open")
                        if (choice != null && project.activationRevision == activation) {
                            results.getOrNull(choice)?.let { reference ->
                                val documentPath = reference.documentPath
                                if (documentPath == null) openProjectSettings()
                                else openDocument(assetRoot.resolve(documentPath))
                            }
                        }
                    }
                }
                null
            }
        },
        snapshot = browser?.snapshot,
        editRegions = { path, edit ->
            run {
                withContext(Dispatchers.IO) { AssetIdentity.editRegions(opened.root, assetRoot, path, edit) }
                if (current == opened) onTextureFilterChanged()
                "Sprite regions saved"
            }
        },
        setTextureFilter = { path, filter ->
            run {
                withContext(Dispatchers.IO) { AssetIdentity.writeFilter(opened.root, assetRoot, path, filter) }
                if (current == opened) onTextureFilterChanged()
                "Texture filter saved"
            }
        },
        repair={
            val selected=browser?.selected
            if(selected!=null && EditorPrompts.confirm("Repair Asset Identity",
                    "Assign a NEW UUID to $selected? References to its old identity will need repair.","Assign New UUID",danger=true))
                run { withContext(Dispatchers.IO) { AssetIdentity.repair(opened.root,assetRoot,selected) };"Assigned new asset UUID" }
        },busy=busy || project.busy,message=message,
        createFolder={
            if(!busy && !project.busy) {
                val folder=browser?.folder.orEmpty()
                val name=EditorPrompts.input("New Folder","Name",confirm="Create") {
                    runCatching { sle.editor.project.validateProjectFolderName(it) }.exceptionOrNull()?.message
                }
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
                val confirmed=EditorPrompts.confirm("Delete Project Asset",
                    "Move $selected to project trash?\n$warning\nReferenced documents may stop loading. Dynamic Lua/XML/module paths are not checked.\nRecover Deleted… can restore it; retain .sle/trash until recovery is no longer needed.",
                    "Move to Trash",danger=true)
                if(confirmed) {
                    require(current?.file==opened.file && mutable()) { "Project or workspace changed" }
                    withContext(Dispatchers.IO) { store.delete(selected,allowReferenced=true) };"Moved $selected to .sle/trash"
                } else null
            }
        },recover={managed { store ->
            val receipts=withContext(Dispatchers.IO) { store.deletions() }
            if(receipts.isEmpty()) "No recoverable deletions" else {
                val choices=receipts.map { "${it.original} (${it.token})" }
                val choice=EditorPrompts.select("Recover Deleted Asset","Asset",choices,choices.first(),confirm="Restore")
                val index=choices.indexOf(choice)
                if(index>=0) {
                    require(current?.file==opened.file && mutable()) { "Project or workspace changed" }
                    withContext(Dispatchers.IO) { store.restore(receipts[index]) };"Restored ${receipts[index].original}"
                } else null
            }
        }},
    )
}
