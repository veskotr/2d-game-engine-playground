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
        canonicalSceneDocument(source) else NativeEngine.canonicalPrefab(source.toByteArray(Charsets.UTF_8)).toString(Charsets.UTF_8)
    fun launch(kind: ProjectDocumentKind?,action: suspend ()->Unit) {
        if(opened==null || project.busy || !canEdit()) return
        project.busy=true;failure(kind,null)
        scope.launch {
            try { action() }
            catch(f: Exception) { if(f is CancellationException) throw f;failure(kind,f.message ?: "Document operation failed") }
            finally { project.busy=false;browser?.refresh() }
        }
    }
    fun sameProject()=current?.file==opened?.file && current?.document?.assetRoot==opened?.document?.assetRoot
    /** Captures the document on the UI thread; the returned writer performs the save. Null when
     * there is nothing valid to save (the reason is reported on the document). */
    fun prepareSave(kind: ProjectDocumentKind,path: Path,export: Boolean=false): (suspend ()->Unit)? {
        if(opened==null) return null
        val target=identity(kind)
        val source=(if(kind==ProjectDocumentKind.Scene) scene?.serialize() else prefab?.document?.serialize()) ?: return null
        val valid=if(kind==ProjectDocumentKind.Scene) scene?.entities?.all { it.fieldErrors.isEmpty() }==true else prefab?.fieldErrors?.isEmpty()==true
        if(!valid) { failure(kind,"Correct invalid fields before saving");return null }
        val assets=if(kind==ProjectDocumentKind.Scene) scene!!.allAssets() else prefab!!.assets
        val expected=if(kind==ProjectDocumentKind.Scene) scene!!.projectDocument else prefab!!.projectDocument
        return writer@{
            val packaged=path.fileName.toString().endsWith(".slescene",true) || path.fileName.toString().endsWith(".sleprefab",true)
            // Project documents reference assets by UUID, so files from opened/imported packages
            // (extracted to a temporary folder) or picked outside the project are copied in first.
            val adopted=if(packaged) AdoptedAssets(source,assets,emptyMap())
                else withContext(Dispatchers.IO) { ProjectDocuments(opened).adoptExternalAssets(source,assets) }
            val saved=withContext(Dispatchers.IO) {
                val normalized=canonical(kind,adopted.source)
                if(packaged) {
                    if(kind==ProjectDocumentKind.Scene) PrefabPackages.saveScene(normalized,adopted.assets,path) { canonical(kind,it) }
                    else PrefabPackages.save(EntityDocument.parse(normalized),adopted.assets,path) { canonical(kind,it) }
                    null
                } else ProjectDocuments(opened).save(path,kind,normalized,adopted.assets,expected) { canonical(kind,it) }
            }
            if(!sameProject() || identity(kind)!=target) return@writer
            if(kind==ProjectDocumentKind.Scene) scene!!.relocateAssets(adopted.moved) else prefab!!.relocateAssets(adopted.moved)
            if(!export) {
                // Record the snapshot that reached disk, even if later edits remain dirty.
                val source=adopted.source;val assets=adopted.assets
                if(kind==ProjectDocumentKind.Scene) { if(saved!=null) scene!!.savedProject(saved,source,assets) else scene!!.savedResolved(path,source,assets) }
                else { if(saved!=null) prefab!!.savedProject(saved,source,assets) else prefab!!.savedResolved(path,source,assets) }
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
        val saves=ProjectDocumentKind.entries.filter { !(reopening && it==requested) }.mapNotNull(::autosave)
        val replacedDirty=if(requested==ProjectDocumentKind.Scene) scene?.dirty==true else prefab?.dirty==true
        val replacedSaved=!reopening && autosave(requested)!=null
        if(replacedDirty && !replacedSaved && !confirm(requested)) return
        launch(requested) {
            saves.forEach { it() }
            val before=revision(requested);val target=identity(requested)
            val prepared=withContext(Dispatchers.IO) {
                if(path.fileName.toString().endsWith(".slescene",true) || path.fileName.toString().endsWith(".sleprefab",true)) {
                    val packet=if(requested==ProjectDocumentKind.Scene) PrefabPackages.openScene(path) else PrefabPackages.open(path).let { OpenedAssetDocument(Json.parseToJsonElement(it.document.serialize()).jsonObject,it.assets) }
                    OpenedProjectDocument(path,requested,canonical(requested,packet.document.toString()),packet.assets,"")
                } else if(hint!=null && !Json.parseToJsonElement(Files.readString(path)).jsonObject.containsKey("format")) {
                    OpenedProjectDocument(path,requested,canonical(requested,Files.readString(path)),emptyMap(),"")
                } else ProjectDocuments(opened!!).open(path).let { value ->
                    require(value.kind==requested) { "Document kind does not match its extension" }
                    value.copy(source=canonical(value.kind,value.source))
                }
            }
            if(sameProject() && canEdit() && identity(requested)==target && (revision(requested)==before || confirm(requested))) {
                if(prepared.kind==ProjectDocumentKind.Scene) {
                    if(prepared.revision.isEmpty()) scene?.openResolved(prepared.source,prepared.assets,prepared.path) else scene?.openProject(prepared)
                } else {
                    if(prepared.revision.isEmpty()) prefab?.openResolved(prepared.source,prepared.assets,prepared.path) else prefab?.openProject(prepared)
                }
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
                } else if(path.fileName.toString().endsWith(".sleprefab",true)) PrefabPackages.open(path).let {
                    OpenedProjectDocument(path,ProjectDocumentKind.Prefab,canonical(ProjectDocumentKind.Prefab,it.document.serialize()),it.assets,"")
                } else OpenedProjectDocument(path,ProjectDocumentKind.Prefab,canonical(ProjectDocumentKind.Prefab,Files.readString(path)),emptyMap(),"")
            }
            if(sameProject() && canEdit() && revision(ProjectDocumentKind.Scene)==before)
                scene?.insertPrefab(EntityDocument.parse(imported.source),imported.assets,position)
            else failure(ProjectDocumentKind.Scene,"Scene changed during prefab import; try again")
        }
    }
    SideEffect {
        scene?.projectImportPrefab=if(opened==null) null else importPrefab
        scene?.projectFileAction=if(opened==null) null else { save,saveAs -> choose(ProjectDocumentKind.Scene,save,saveAs) }
        prefab?.projectFileAction=if(opened==null) null else { save,saveAs -> choose(ProjectDocumentKind.Prefab,save,saveAs) }
    }
    DisposableEffect(scene,prefab) { onDispose { scene?.projectImportPrefab=null;scene?.projectFileAction=null;prefab?.projectFileAction=null } }
    if(opened==null) return null
    return ProjectDocumentCommands({openFile(it)},{kind,path -> save(kind,path)},{ kind -> choose(kind,true,true,true) })
}
