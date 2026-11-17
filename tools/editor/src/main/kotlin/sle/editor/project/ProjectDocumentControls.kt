package sle.editor.project

import sle.editor.ui.EditorDialogs
import sle.editor.ui.EditorFileChooser
import androidx.compose.runtime.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import sle.editor.bridge.NativeEngine
import sle.editor.files.PrefabPackages
import sle.editor.files.OpenedAssetDocument
import sle.editor.model.*
import sle.editor.project.assets.AssetBrowserState
import sle.editor.ui.*
import java.nio.file.*
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter

internal data class ProjectDocumentCommands(
    val openPath: (Path)->Unit,
    val savePath: (ProjectDocumentKind,Path)->Unit,
    val exportPackage: (ProjectDocumentKind)->Unit,
)

@Composable
internal fun projectDocumentControls(project: ProjectWorkspaceState,browser: AssetBrowserState?,
    scene: SceneWorkspaceState?,prefab: EntityWorkspaceState?,editable: ()->Boolean,
    onOpen: (ProjectDocumentKind)->Unit,
): ProjectDocumentCommands? {
    val opened=project.opened
    val activation=project.activationRevision
    val scope=rememberCoroutineScope()
    val current by rememberUpdatedState(opened)
    val canEdit by rememberUpdatedState(editable)
    val show by rememberUpdatedState(onOpen)
    fun identity(kind: ProjectDocumentKind): Any? = if(kind==ProjectDocumentKind.Scene)
        scene?.let { Triple(it.key,it.file,it.projectDocument) }
        else prefab?.let { Triple(it.document.key,it.file,it.projectDocument) }
    fun revision(kind: ProjectDocumentKind): Any? = if(kind==ProjectDocumentKind.Scene)
        scene?.let { listOf(it.file,it.projectDocument,it.document(),it.allAssets(),it.entities.map { e -> e.drafts to e.fieldErrors }) }
        else prefab?.let { listOf(it.file,it.projectDocument,it.document,it.assets,it.drafts,it.fieldErrors) }
    fun failure(kind: ProjectDocumentKind?,text: String?) {
        if(kind==ProjectDocumentKind.Prefab) prefab?.error=text else scene?.error=text
    }
    fun confirm(kind: ProjectDocumentKind)=if(kind==ProjectDocumentKind.Scene) confirmDiscardScene(scene) else confirmDiscard(prefab)
    fun canonical(kind: ProjectDocumentKind,source: String): String = if(kind==ProjectDocumentKind.Scene)
        canonicalSceneDocument(source) else canonicalPrefabDocument(source)
    fun launch(kind: ProjectDocumentKind?,action: suspend ()->Unit) {
        if(opened==null || project.busy || !canEdit()) return
        project.busy=true;failure(kind,null)
        scope.launch {
            try { action() }
            catch(f: Exception) { if(f is CancellationException) throw f;failure(kind,f.message ?: "Document operation failed") }
            finally { project.busy=false;browser?.refresh() }
        }
    }
    fun sameProject()=project.activationRevision==activation && current?.file==opened?.file && current?.document?.assetRoot==opened?.document?.assetRoot
    suspend fun rememberScene(path: Path) {
        if(opened==null) return
        try { withContext(Dispatchers.IO) { ProjectFiles.rememberScene(opened,path) } }
        catch(failure: Exception) { if(failure is CancellationException) throw failure;project.error="Scene opened/saved; local recovery state could not be saved: ${failure.message}" }
    }
    /** Captures the document on the UI thread; the returned writer performs the save. Null when
     * there is nothing valid to save (the reason is reported on the document). */
    fun prepareSave(kind: ProjectDocumentKind,path: Path,export: Boolean=false): (suspend ()->Unit)? {
        if(opened==null) return null
        val target=identity(kind)
        val source=(if(kind==ProjectDocumentKind.Scene) scene?.serialize() else prefab?.serializePrefab()) ?: return null
        val valid=if(kind==ProjectDocumentKind.Scene) scene?.entities?.all { it.fieldErrors.isEmpty() }==true else prefab?.fieldErrors?.isEmpty()==true
        if(!valid) { failure(kind,"Correct invalid fields before saving");return null }
        val assets=if(kind==ProjectDocumentKind.Scene) scene!!.allAssets() else prefab!!.assets
        val sceneSnapshot=scene?.document()
        val sceneRevision=revision(ProjectDocumentKind.Scene)
        val expected=if(kind==ProjectDocumentKind.Scene) scene!!.projectDocument else prefab!!.projectDocument
        return writer@{
            val packaged=path.fileName.toString().endsWith(".slescene",true) || path.fileName.toString().endsWith(".sleprefab",true)
            // Project documents reference assets by UUID, so files from opened/imported packages
            // (extracted to a temporary folder) or picked outside the project are copied in first.
            val adopted=if(packaged) AdoptedAssets(source,assets,emptyMap())
                else withContext(Dispatchers.IO) { ProjectDocuments(opened).adoptExternalAssets(source,assets) }
            var updatedScene: SceneDocument?=null
            val saved=withContext(Dispatchers.IO) {
                val normalized=canonical(kind,adopted.source)
                if(packaged) {
                    if(kind==ProjectDocumentKind.Scene) PrefabPackages.saveScene(normalized,adopted.assets,path) { canonical(kind,it) }
                    else PrefabPackages.saveTree(PrefabDocument.parse(normalized),adopted.assets,path) { canonical(kind,it) }
                    null
                } else if(kind==ProjectDocumentKind.Scene && sceneSnapshot!!.prefabInstances.isNotEmpty()) {
                    ProjectDocuments(opened).saveLinked(path,SceneDocument.parse(normalized),sceneSnapshot.prefabInstances,adopted.assets,expected) { canonical(kind,it) }
                } else if(kind==ProjectDocumentKind.Prefab) {
                    ProjectDocuments(opened).updatePrefab(path,PrefabDocument.parse(normalized),adopted.assets,expected,sceneSnapshot,
                        { canonical(kind,it) },{ canonicalSceneDocument(it) }).let { updatedScene=it.scene;it.saved }
                } else ProjectDocuments(opened).save(path,kind,normalized,adopted.assets,expected) { canonical(kind,it) }
            }
            if(!sameProject() || identity(kind)!=target) return@writer
            if(kind==ProjectDocumentKind.Prefab && updatedScene!=null && revision(ProjectDocumentKind.Scene)==sceneRevision)
                scene?.replaceLinkedScene(updatedScene)
            if(kind==ProjectDocumentKind.Scene) scene!!.relocateAssets(adopted.moved) else prefab!!.relocateAssets(adopted.moved)
            if(!export) {
                // Record the snapshot that reached disk, even if later edits remain dirty.
                val source=adopted.source;val assets=adopted.assets
                if(kind==ProjectDocumentKind.Scene) { if(saved!=null) scene!!.savedProject(saved,source,assets) else scene!!.savedResolved(path,source,assets) }
                else { if(saved!=null) prefab!!.savedProject(saved,source,assets) else prefab!!.savedResolved(path,source,assets) }
                if(kind==ProjectDocumentKind.Scene && saved!=null) rememberScene(saved.path)
            }
        }
    }
    fun save(kind: ProjectDocumentKind,path: Path,export: Boolean=false) {
        if(opened==null || project.busy || !canEdit()) return
        val writer=prepareSave(kind,path,export) ?: return
        launch(kind) { writer() }
    }
    /** Writer that saves [kind] in place when it is an unsaved project document with a file; else null. */
    fun autosave(kind: ProjectDocumentKind): (suspend ()->Unit)? {
        val dirty=if(kind==ProjectDocumentKind.Scene) scene?.dirty==true else prefab?.dirty==true
        val document=if(kind==ProjectDocumentKind.Scene) scene?.projectDocument else prefab?.projectDocument
        return if(dirty && document!=null) prepareSave(kind,document.path) else null
    }
    fun openFile(path: Path,hint: ProjectDocumentKind?=null) {
        val requested=when {
            path.fileName.toString().endsWith(".scene.json",true) || path.fileName.toString().endsWith(".slescene",true) -> ProjectDocumentKind.Scene
            path.fileName.toString().endsWith(".prefab.json",true) || path.fileName.toString().endsWith(".sleprefab",true) -> ProjectDocumentKind.Prefab
            else -> hint
        }
        if(requested==null) { failure(null,"Use .scene.json or .prefab.json project documents, or open legacy JSON through standalone File Open.");return }
        if(project.busy || !canEdit()) return
        // Switching documents saves project scenes/prefabs that already have a file instead of
        // asking to discard them. Untitled drafts of the replaced kind still ask first.
        // Reopening the document that is already open keeps the prompt: "No" keeps the draft,
        // "Yes" reloads it from disk (e.g. after an external change).
        fun openPath(kind: ProjectDocumentKind)=(if(kind==ProjectDocumentKind.Scene) scene?.file else prefab?.file)?.toAbsolutePath()?.normalize()
        val reopening=openPath(requested)==path.toAbsolutePath().normalize()
        val saves=ProjectDocumentKind.entries.filter { !(reopening && it==requested) }
        val replacedDirty=if(requested==ProjectDocumentKind.Scene) scene?.dirty==true else prefab?.dirty==true
        val replacedSaved=!reopening && autosave(requested)!=null
        if(replacedDirty && !replacedSaved && !confirm(requested)) return
        launch(requested) {
            // Prepare each save after the previous one: a prefab save must capture the scene revision
            // that the scene save just recorded, or its linked-instance refresh is discarded as stale.
            saves.forEach { autosave(it)?.invoke() }
            val before=revision(requested);val target=identity(requested)
            val prepared=withContext(Dispatchers.IO) {
                if(path.fileName.toString().endsWith(".slescene",true) || path.fileName.toString().endsWith(".sleprefab",true)) {
                    val packet=if(requested==ProjectDocumentKind.Scene) PrefabPackages.openScene(path) else PrefabPackages.openTree(path).let { OpenedAssetDocument(Json.parseToJsonElement(it.first.serialize()).jsonObject,it.second) }
                    OpenedProjectDocument(path,requested,canonical(requested,packet.document.toString()),packet.assets,"")
                } else if(hint!=null && !Json.parseToJsonElement(Files.readString(path)).jsonObject.containsKey("format")) {
                    OpenedProjectDocument(path,requested,canonical(requested,Files.readString(path)),emptyMap(),"")
                } else ProjectDocuments(opened!!).open(path).let { value ->
                    require(value.kind==requested) { "Document kind does not match its extension" }
                    value.copy(source=canonical(value.kind,value.source))
                }
            }
            if(sameProject() && canEdit() && identity(requested)==target && (revision(requested)==before || confirmProjectChange { confirm(requested) })) {
                if(prepared.kind==ProjectDocumentKind.Scene) {
                    if(prepared.revision.isEmpty()) scene?.openResolved(prepared.source,prepared.assets,prepared.path) else scene?.openProject(prepared)
                } else {
                    if(prepared.revision.isEmpty()) prefab?.openResolved(prepared.source,prepared.assets,prepared.path) else prefab?.openProject(prepared)
                }
                if(prepared.kind==ProjectDocumentKind.Scene && prepared.revision.isNotEmpty()) rememberScene(prepared.path)
                show(prepared.kind)
            }
        }
    }
    fun choose(kind: ProjectDocumentKind,save: Boolean,saveAs: Boolean=false,export: Boolean=false): Boolean {
        if(opened==null || project.busy || !canEdit()) return false
        val file=if(kind==ProjectDocumentKind.Scene) scene?.file else prefab?.file
        if(save && !saveAs && !export && file!=null) { save(kind,file);return true }
        val folder=opened.root.resolve(opened.document.assetRoot).resolve(if(kind==ProjectDocumentKind.Scene) "Scenes" else "Prefabs")
        // Start beside the document only when it already lives in this project (not a temp/package copy).
        val assetsRoot=opened.root.resolve(opened.document.assetRoot).toAbsolutePath().normalize()
        val start=file?.parent?.toAbsolutePath()?.normalize()?.takeIf { it.startsWith(assetsRoot) && Files.isDirectory(it) }
            ?: folder.takeIf { Files.isDirectory(it) } ?: assetsRoot
        val chooser=EditorFileChooser(start.toFile())
        val suffix=if(export) { if(kind==ProjectDocumentKind.Scene) ".slescene" else ".sleprefab" } else kind.suffix
        chooser.fileFilter=if(export) FileNameExtensionFilter("Portable package (*$suffix)",suffix.drop(1))
            else FileNameExtensionFilter("Project ${kind.name.lowercase()} (*$suffix)","json")
        if(!save) chooser.addChoosableFileFilter(FileNameExtensionFilter("Portable packages","slescene","sleprefab"))
        if(save) {
            val name=if(kind==ProjectDocumentKind.Scene) scene?.name else prefab?.document?.name
            chooser.selectedFile=java.io.File(prefabFileName(name.orEmpty()).removeSuffix(".prefab.json")+suffix)
        }
        if((if(save) chooser.showSaveDialog(null) else chooser.showOpenDialog(null))!=JFileChooser.APPROVE_OPTION) return false
        var path=chooser.selectedFile.toPath()
        if(save && !path.fileName.toString().endsWith(suffix,true)) path=path.resolveSibling(path.fileName.toString()+suffix)
        if(save) {
            if(export && Files.exists(path) && EditorDialogs.showConfirmDialog(null,"Replace ${path.fileName}?","Export package",javax.swing.JOptionPane.YES_NO_OPTION)!=javax.swing.JOptionPane.YES_OPTION) return false
            save(kind,path,export)
        } else openFile(path,kind)
        return true
    }
    val importPrefab: (Path,sle.editor.viewport.Vec2?)->Unit={ path,position ->
        val before=revision(ProjectDocumentKind.Scene)
        launch(ProjectDocumentKind.Scene) {
            val imported=withContext(Dispatchers.IO) {
                if(path.fileName.toString().endsWith(".prefab.json",true) && Json.parseToJsonElement(Files.readString(path)).jsonObject.containsKey("format")) {
                    val value=ProjectDocuments(opened!!).open(path)
                    require(value.kind==ProjectDocumentKind.Prefab) { "Select a project prefab" }
                    value.copy(source=canonical(value.kind,value.source))
                } else if(path.fileName.toString().endsWith(".sleprefab",true)) PrefabPackages.openTree(path).let {
                    OpenedProjectDocument(path,ProjectDocumentKind.Prefab,canonical(ProjectDocumentKind.Prefab,it.first.serialize()),it.second,"")
                } else OpenedProjectDocument(path,ProjectDocumentKind.Prefab,canonical(ProjectDocumentKind.Prefab,Files.readString(path)),emptyMap(),"")
            }
            if(sameProject() && canEdit() && revision(ProjectDocumentKind.Scene)==before) {
                val tree=PrefabDocument.parse(imported.source)
                if(imported.revision.isNotEmpty()) scene?.insertLinkedPrefab(tree,sle.editor.project.assets.AssetIdentity.read(sle.editor.project.assets.AssetIdentity.sidecar(imported.path)),imported.assets,position)
                else scene?.insertPrefabTree(tree,imported.assets,position)
            } else failure(ProjectDocumentKind.Scene,"Scene changed during prefab import; try again")
        }
    }
    /** Apply Changes to Prefab: the same validated update as a prefab Save, built from one instance. */
    val applyPrefab: (String)->Unit=apply@{ key ->
        val state=scene ?: return@apply
        if(opened==null) return@apply
        if(project.busy || !canEdit()) return@apply failure(ProjectDocumentKind.Scene,if(project.busy) "The project is busy; try again when it finishes" else "Stop the game before changing prefabs")
        val link=state.linkedInstance(key) ?: return@apply failure(ProjectDocumentKind.Scene,"Select a linked prefab instance")
        if(state.entities.any { it.document.key in link.entityKeys && it.fieldErrors.isNotEmpty() }) return@apply failure(ProjectDocumentKind.Scene,"Correct invalid fields before applying")
        val captured=state.document()
        val (applied,rebased)=try { applyInstanceToPrefab(captured,link.key) }
            catch(failure: Exception) { return@apply failure(ProjectDocumentKind.Scene,failure.message ?: "Cannot apply this instance") }
        val assets=state.assetsOf(link.entityKeys)
        val before=revision(ProjectDocumentKind.Scene)
        launch(ProjectDocumentKind.Scene) {
            val store=ProjectDocuments(opened)
            val path=withContext(Dispatchers.IO) { store.pathOf(link.assetId) }
            val openPrefab=prefab?.projectDocument?.path?.toAbsolutePath()?.normalize()==path.toAbsolutePath().normalize()
            if(openPrefab && prefab?.dirty==true) return@launch failure(ProjectDocumentKind.Scene,"Save or discard the open ${path.fileName} before applying instance changes")
            if(!sameProject() || revision(ProjectDocumentKind.Scene)!=before) return@launch failure(ProjectDocumentKind.Scene,"Scene changed; apply again")
            val (update,adopted)=withContext(Dispatchers.IO) {
                val expected=store.requireLinkedSource(path,link.source) { canonical(ProjectDocumentKind.Prefab,it) }
                val adopted=store.adoptExternalAssets(applied.serialize(),assets)
                store.updatePrefab(path,PrefabDocument.parse(canonical(ProjectDocumentKind.Prefab,adopted.source)),adopted.assets,expected,rebased,
                    { canonical(ProjectDocumentKind.Prefab,it) },{ canonicalSceneDocument(it) }) to adopted
            }
            if(!sameProject()) return@launch
            if(revision(ProjectDocumentKind.Scene)==before) {
                val cleared=state.replaceLinkedScene(update.scene ?: rebased)
                state.relocateAssets(adopted.moved)
                state.message="Applied instance changes to ${path.fileName}"+if(cleared) ". ${sle.editor.model.HISTORY_CLEARED_NOTICE}" else ""
            } else failure(ProjectDocumentKind.Scene,"Applied to ${path.fileName}; the scene changed meanwhile, so reopen it to refresh instances")
            if(openPrefab && prefab?.dirty==false) {
                val reloaded=withContext(Dispatchers.IO) { store.open(path).let { it.copy(source=canonical(it.kind,it.source)) } }
                if(sameProject() && prefab?.dirty==false) prefab?.openProject(reloaded)
            }
        }
    }
    /** Entity dropped on an Assets folder: save its subtree as a prefab, then link the source. */
    val createPrefab: (String,Path)->Unit=create@{ key,folder ->
        val state=scene ?: return@create
        if(opened==null) return@create
        if(project.busy || !canEdit()) return@create failure(ProjectDocumentKind.Scene,if(project.busy) "The project is busy; try again when it finishes" else "Stop the game before changing prefabs")
        if(state.entities.any { it.fieldErrors.isNotEmpty() }) return@create failure(ProjectDocumentKind.Scene,"Correct invalid fields before creating a prefab")
        val captured=state.document()
        val created=try { prefabFromSceneSubtree(captured,key) }
            catch(failure: Exception) { return@create failure(ProjectDocumentKind.Scene,failure.message ?: "Cannot create a prefab from this entity") }
        val assets=state.assetsOf(created.renamed.values.toSet())
        val name=captured.entities.first { it.key==key }.name
        val before=revision(ProjectDocumentKind.Scene)
        launch(ProjectDocumentKind.Scene) {
            val store=ProjectDocuments(opened)
            val (saved,adopted)=withContext(Dispatchers.IO) {
                require(Files.isDirectory(folder,LinkOption.NOFOLLOW_LINKS)) { "Drop onto an Assets folder" }
                val adopted=store.adoptExternalAssets(created.prefab.serialize(),assets)
                store.save(uniquePrefabPath(folder,name),ProjectDocumentKind.Prefab,canonical(ProjectDocumentKind.Prefab,adopted.source),adopted.assets,null) {
                    canonical(ProjectDocumentKind.Prefab,it)
                } to adopted
            }
            if(!sameProject()) return@launch
            val id=sle.editor.project.assets.AssetIdentity.read(sle.editor.project.assets.AssetIdentity.sidecar(saved.path))
            if(revision(ProjectDocumentKind.Scene)!=before) return@launch failure(ProjectDocumentKind.Scene,"Saved ${saved.path.fileName}, but the scene changed meanwhile, so the entity was not linked")
            state.linkSubtree(captured,created.withSource(PrefabDocument.parse(saved.source)),id)
            state.relocateAssets(adopted.moved)
            state.message="Saved prefab ${saved.path.fileName} and linked ${name.ifBlank { "the entity" }}"
        }
    }
    SideEffect {
        scene?.projectApplyPrefab=if(opened==null) null else applyPrefab
        scene?.projectCreatePrefab=if(opened==null) null else createPrefab
        scene?.projectImportPrefab=if(opened==null) null else importPrefab
        scene?.projectFileAction=if(opened==null) null else { save,saveAs -> choose(ProjectDocumentKind.Scene,save,saveAs) }
        prefab?.projectFileAction=if(opened==null) null else { save,saveAs -> choose(ProjectDocumentKind.Prefab,save,saveAs) }
    }
    DisposableEffect(scene,prefab) { onDispose {
        scene?.projectImportPrefab=null;scene?.projectApplyPrefab=null;scene?.projectCreatePrefab=null
        scene?.projectFileAction=null;prefab?.projectFileAction=null
    } }
    LaunchedEffect(activation) {
        if(opened!=null && scene!=null) {
            val before=revision(ProjectDocumentKind.Scene)
            project.busy=true
            try {
                val recovered=withContext(Dispatchers.IO) {
                    ProjectSceneRecovery.load(opened) { canonical(ProjectDocumentKind.Scene,it) }
                }
                if(sameProject() && canEdit() && revision(ProjectDocumentKind.Scene)==before) {
                    recovered.document?.let { document ->
                        scene.openProject(document)
                        rememberScene(document.path)
                        show(ProjectDocumentKind.Scene)
                    }
                    recovered.notice?.let { scene.error=it }
                }
            } catch(failure: Exception) {
                if(failure is CancellationException) throw failure
                if(sameProject()) scene.error=failure.message ?: "Could not restore project scene"
            } finally { if(sameProject()) { project.busy=false;browser?.refresh() } }
        }
    }
    if(opened==null) return null
    return ProjectDocumentCommands({openFile(it)},{kind,path -> save(kind,path)},{ kind -> choose(kind,true,true,true) })
}
