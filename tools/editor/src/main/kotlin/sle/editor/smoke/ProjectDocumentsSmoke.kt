package sle.editor.smoke

import sle.editor.ui.postedAfter
import androidx.compose.ui.geometry.Rect
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import sle.editor.model.*
import sle.editor.project.*
import sle.editor.project.assets.*
import sle.editor.viewport.EngineCanvas
import sle.editor.viewport.Playback
import java.awt.*
import java.awt.event.InputEvent
import java.nio.file.*
import javax.imageio.ImageIO
import javax.swing.*

internal suspend fun runProjectDocumentsSmoke(window: JFrame,canvas: ()->EngineCanvas,project: ProjectWorkspaceState,
    projects: ProjectCommands,documents: ()->ProjectDocumentCommands?,browser: ()->AssetBrowserState?,bounds: Map<String,Rect>,
    scene: SceneWorkspaceState,prefab: EntityWorkspaceState,playback: ScenePlaybackState,notices: sle.editor.ui.EditorNotices,
) {
    val robot=Robot().apply { autoDelay=12 }
    suspend fun await(message: String,condition: ()->Boolean) {
        repeat(240) { if(condition()) { delay(100);return };delay(25) }
        runCatching { ImageIO.write(robot.createScreenCapture(Rectangle(Toolkit.getDefaultToolkit().screenSize)),"png",
            Path.of(System.getProperty("sle.smokeReport")).parent.resolve("project-documents-failure.png").toFile()) }
        error("$message; scene=${scene.error}; prefab=${prefab.error}; project=${project.error}; notice=${notices.current?.text}")
    }
    suspend fun click(id: String,double: Boolean=false) {
        await("Missing browser control $id") { id in bounds }
        // Lazy rows keep their last bounds after scrolling away; bring the row inside its list first.
        val list=when { id.startsWith("AssetFolder:") -> "AssetFolders";id.startsWith("AssetFile:") -> "AssetFiles";else -> null }
        if(list!=null) repeat(16) {
            val container=bounds[list] ?: return@repeat
            val row=bounds.getValue(id)
            if(row.top>=container.top && row.bottom<=container.bottom) return@repeat
            val center=container.center;val o=window.contentPane.locationOnScreen
            robot.mouseMove(o.x+center.x.toInt(),o.y+center.y.toInt())
            robot.mouseWheel(if(row.bottom>container.bottom) 1 else -1);delay(120)
        }
        val point=bounds.getValue(id).center;val at=window.contentPane.locationOnScreen
        robot.mouseMove(at.x+point.x.toInt(),at.y+point.y.toInt())
        repeat(if(double) 2 else 1) { robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);delay(65) }
        delay(180)
    }
    suspend fun menu(text: String) {
        val file=window.jMenuBar.components.filterIsInstance<JMenu>().single { it.text=="File" }
        val at=file.locationOnScreen;robot.mouseMove(at.x+file.width/2,at.y+file.height/2)
        robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK)
        await("File menu missing") { file.popupMenu.isShowing }
        val item=file.popupMenu.components.filterIsInstance<JMenuItem>().single { it.text==text }
        check(item.isEnabled);val point=item.locationOnScreen
        robot.mouseMove(point.x+item.width/2,point.y+item.height/2)
        robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);delay(100)
    }
    fun descendants(component: Component): List<Component> = listOf(component)+(if(component is Container) component.components.flatMap(::descendants) else emptyList())
    fun answer(value: Int) = Thread {
        repeat(250) {
            var done=false
            SwingUtilities.invokeAndWait {
                Window.getWindows().filter { it.isShowing }.flatMap(::descendants).filterIsInstance<JOptionPane>().firstOrNull()?.let { it.value=value;done=true }
            }
            if(done) return@Thread
            Thread.sleep(20)
        }
        error("Discard prompt did not appear")
    }.apply { isDaemon=true;start() }
    fun choose(path: Path) = Thread {
        repeat(250) {
            var done=false
            SwingUtilities.invokeAndWait {
                Window.getWindows().filter { it.isShowing }.flatMap(::descendants).filterIsInstance<JFileChooser>().firstOrNull()?.let {
                    it.selectedFile=path.toAbsolutePath().toFile();it.approveSelection();done=true
                }
            }
            if(done) return@Thread
            Thread.sleep(20)
        }
        error("Save chooser did not appear")
    }.apply { isDaemon=true;start() }
    val reports=Path.of(System.getProperty("sle.smokeReport")).parent
    val first=withContext(Dispatchers.IO) {
        val p=ProjectFiles.create(Files.createTempDirectory(reports,"project-documents-").resolve("Source Game"),"Source Game")
        ProjectAssetImports(p.root,p.assets).importAsset(Path.of("assets/textures/tile2.png"),"Textures")
        Files.writeString(p.assets.resolve("Scripts/move.lua"),"""
            return { update = function(entity,dt)
                local p=Engine.getTransformPosition(entity)
                Engine.setTransformPosition(entity,p.x+20*dt,p.y)
            end }
        """.trimIndent())
        p
    }
    projects.openPath(first.file)
    await("Project failed to open") { !project.busy && documents()!=null && browser()?.snapshot?.idsByPath?.containsKey("Textures/tile2.png")==true }
    fun vector(x: Int,y: Int)=buildJsonObject { put("x",x);put("y",y) }
    scene.newScene();scene.rename("Shared Scene");scene.addEntity()
    val actor=scene.selected!!;actor.rename("Actor")
    actor.setValue("Transform",listOf("position"),vector(240,135));actor.addComponent("SpriteRenderer")
    actor.setValue("SpriteRenderer",listOf("size"),vector(64,64))
    actor.setValue("SpriteRenderer",listOf("texture"),JsonPrimitive(first.assets.resolve("Textures/tile2.png").toString().replace('\\','/')))
    actor.addComponent("Script");actor.setValue("Script",listOf("scriptAsset"),JsonPrimitive(first.assets.resolve("Scripts/move.lua").toString().replace('\\','/')))
    scene.addCamera()
    val scenePath=first.assets.resolve("Scenes/shared.scene.json")
    // Real Ctrl+S from native viewport focus uses the project JSON Save chooser.
    // Click empty canvas space as a user would: requestFocusInWindow alone does not activate the
    // window, and an inactive editor would send Ctrl+S to whichever app owns the keyboard.
    canvas().let { view -> val at=view.locationOnScreen;robot.mouseMove(at.x+view.width-12,at.y+view.height-12) }
    robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK)
    await("Native canvas did not gain focus for Ctrl+S") { canvas().isFocusOwner }
    choose(scenePath)
    robot.keyPress(java.awt.event.KeyEvent.VK_CONTROL);robot.keyPress(java.awt.event.KeyEvent.VK_S)
    robot.keyRelease(java.awt.event.KeyEvent.VK_S);robot.keyRelease(java.awt.event.KeyEvent.VK_CONTROL)
    await("Project scene Save failed") { !project.busy && scene.file==scenePath && !scene.dirty }
    val source=withContext(Dispatchers.IO) { Files.readString(scenePath) }
    check(source.contains("project-asset://") && !source.contains(first.root.toString().replace('\\','/')))
    prefab.rename("Shared Prefab");prefab.addComponent("SpriteRenderer")
    prefab.setValue("SpriteRenderer",listOf("texture"),JsonPrimitive(first.assets.resolve("Textures/tile2.png").toString().replace('\\','/')))
    val prefabPath=first.assets.resolve("Prefabs/shared.prefab.json")
    documents()!!.savePath(ProjectDocumentKind.Prefab,prefabPath)
    await("Project prefab save failed") { !project.busy && prefab.file==prefabPath && !prefab.dirty }
    scene.importPrefab(prefabPath)
    await("Project prefab insertion failed") { !project.busy && scene.entities.size==3 }
    check(scene.selected!!.document.components.getValue("SpriteRenderer").jsonObject.getValue("texture").jsonPrimitive.content==first.assets.resolve("Textures/tile2.png").toString().replace('\\','/'))
    scene.undo();check(scene.entities.size==2 && !scene.dirty)
    check(withContext(Dispatchers.IO) { Files.list(first.assets.resolve("Textures")).use { it.count() } }==2L)
    // A clean New document during loading is a switch, not permission to replace it.
    val locked=CompletableDeferred<Unit>()
    val release=java.util.concurrent.CountDownLatch(1)
    val blocker=CoroutineScope(Dispatchers.IO).launch {
        synchronized(AssetIdentity) { locked.complete(Unit);check(release.await(10,java.util.concurrent.TimeUnit.SECONDS)) }
    }
    locked.await()
    documents()!!.openPath(scenePath)
    await("Background open did not start") { project.busy }
    scene.newScene()
    release.countDown();blocker.join()
    await("Background open did not finish") { !project.busy }
    check(scene.entities.isEmpty()) { "Background load replaced the user's new document" }
    click("AssetFolder:Scenes");click("AssetFile:Scenes/shared.scene.json",true)
    await("Browser scene open failed") { !project.busy && scene.projectDocument!=null && canvas().authoringReady() }
    val sceneKey=scene.key;scene.rename("Retain dirty draft")
    answer(JOptionPane.NO_OPTION);click("AssetFile:Scenes/shared.scene.json",true)
    check(scene.name=="Retain dirty draft" && scene.dirty)
    withContext(Dispatchers.IO) { Files.writeString(scenePath,source.replace("Shared Scene","External Scene")) }
    val beforeConflict=notices.recent.firstOrNull()
    menu("Save")
    await("External conflict not surfaced") { !project.busy && notices.postedAfter(beforeConflict).any { it.level==sle.editor.ui.NoticeLevel.Error && "changed" in it.text } }
    check(scene.name=="Retain dirty draft" && scene.dirty)
    check(withContext(Dispatchers.IO) { Files.readString(scenePath) }.contains("External Scene"))
    answer(JOptionPane.YES_OPTION);click("AssetFile:Scenes/shared.scene.json",true)
    await("Explicit reopen failed") { !project.busy && scene.name=="External Scene" && !scene.dirty }
    check(scene.key==sceneKey)
    playback.play(scene)
    await("Project Play failed") { canvas().scenePlayback()==Playback.Running }
    val runtimeSource=playback.source;val authored=scene.serialize()
    await("Project script did not execute") {
        canvas().runtimeTransforms()[actor.document.key]?.jsonObject?.get("position")?.jsonObject?.get("x")?.jsonPrimitive?.doubleOrNull?.let { it>245 }==true
    }
    withContext(Dispatchers.IO) { Files.writeString(scenePath,source) }
    browser()!!.refresh();delay(250)
    check(playback.source==runtimeSource && scene.serialize()==authored)
    playback.stop();await("Stop failed") { canvas().scenePlayback()==Playback.Stopped && canvas().authoringReady() }
    // Export preserves project binding; package remains loadable by the existing codec.
    val packagePath=first.root.parent.resolve("exported.slescene")
    choose(packagePath);menu("Export Package…")
    await("Package export failed") { !project.busy && Files.exists(packagePath) }
    check(scene.file==scenePath && scene.projectDocument!=null)
    val standalone=SceneWorkspaceState(prefab.catalog);withContext(Dispatchers.IO) { standalone.open(packagePath) }
    check(standalone.entities.size==2 && standalone.entities.any { "Script" in it.document.components })
    click("AssetFolder:Prefabs");click("AssetFile:Prefabs/shared.prefab.json",true)
    await("Browser prefab open failed") { !project.busy && prefab.projectDocument!=null && prefab.document.name=="Shared Prefab" }
    projects.close();await("Project close failed") { !project.busy && project.opened==null }
    val moved=withContext(Dispatchers.IO) { first.root.resolveSibling("Relocated Game").also { Files.move(first.root,it) } }
    projects.openPath(moved.resolve(first.file.fileName))
    await("Relocated project open failed") { !project.busy && project.opened?.root==moved.toRealPath() && browser()?.snapshot?.byPath?.containsKey("Scenes/shared.scene.json")==true }
    click("AssetFolder:Scenes");click("AssetFile:Scenes/shared.scene.json",true)
    await("Relocated scene open failed") { !project.busy && scene.name=="Shared Scene" && canvas().authoringReady() }
    val relocatedActor=scene.entities.first { "Script" in it.document.components }
    check(relocatedActor.document.serialize().contains(moved.toString().replace('\\','/')))
    playback.play(scene);await("Relocated script failed") {
        canvas().runtimeTransforms()[relocatedActor.document.key]?.jsonObject?.get("position")?.jsonObject?.get("x")?.jsonPrimitive?.doubleOrNull?.let { it>245 }==true
    }
    ImageIO.write(robot.createScreenCapture(Rectangle(window.locationOnScreen,window.size)),"png",reports.resolve("project-documents-editor.png").toFile())
    playback.stop();await("Relocated stop failed") { canvas().scenePlayback()==Playback.Stopped && canvas().authoringReady() }
    // Switching to another document saves a project scene that already has a file, without a prompt.
    scene.rename("Autosaved Scene");check(scene.dirty)
    click("AssetFolder:Prefabs");click("AssetFile:Prefabs/shared.prefab.json",true)
    await("Autosave on document switch failed") { !project.busy && prefab.projectDocument!=null && !scene.dirty }
    check(withContext(Dispatchers.IO) { Files.readString(moved.resolve(first.document.assetRoot).resolve("Scenes/shared.scene.json")) }.contains("Autosaved Scene"))
    // Save/reopen links through the actual UI commands; source Save refreshes the open scene.
    val linkedPath=moved.resolve(first.document.assetRoot).resolve("Prefabs/shared.prefab.json")
    val linkedScenePath=moved.resolve(first.document.assetRoot).resolve("Scenes/shared.scene.json")
    scene.importPrefab(linkedPath)
    await("Linked prefab import failed") { !project.busy && scene.document().prefabInstances.size==1 }
    val linkedKey=scene.selectedKey!!
    documents()!!.savePath(ProjectDocumentKind.Scene,linkedScenePath)
    await("Linked scene save failed") { !project.busy && !scene.dirty }
    prefab.setValue("SpriteRenderer",listOf("size"),vector(96,96))
    documents()!!.savePath(ProjectDocumentKind.Prefab,linkedPath)
    await("Prefab save did not refresh linked scene") {
        !project.busy && !prefab.dirty && scene.dirty &&
            scene.entities.first { it.document.key==linkedKey }.document.components["SpriteRenderer"]!!.jsonObject["size"]!!.jsonObject["x"]?.jsonPrimitive?.doubleOrNull==96.0
    }
    check(scene.selectedKey==linkedKey && !scene.canUndo)
    documents()!!.savePath(ProjectDocumentKind.Scene,linkedScenePath)
    await("Refreshed linked scene save failed") { !project.busy && !scene.dirty }
    documents()!!.openPath(linkedScenePath)
    await("Linked scene reopen failed") { !project.busy && !scene.dirty && scene.document().prefabInstances.size==1 && scene.entities.size==3 }
    check(scene.entities.first { it.document.key==linkedKey }.document.components["SpriteRenderer"]!!.jsonObject["size"]!!.jsonObject["x"]!!.jsonPrimitive.double==96.0)
    // Switching away with both documents dirty saves the scene first; the prefab save must still
    // refresh the open linked scene instead of discarding it as a stale revision.
    val otherPrefab=linkedPath.resolveSibling("other.prefab.json")
    withContext(Dispatchers.IO) {
        ProjectDocuments(project.opened!!).save(otherPrefab,ProjectDocumentKind.Prefab,
            PrefabDocument("root",listOf(EntityDocument(key="root"))).serialize(),emptyMap(),null) {}
    }
    documents()!!.openPath(linkedPath)
    await("Linked prefab reopen failed") { !project.busy && prefab.file?.toAbsolutePath()==linkedPath.toAbsolutePath() }
    scene.rename("Switch Scene");prefab.setValue("SpriteRenderer",listOf("size"),vector(128,128))
    check(scene.dirty && prefab.dirty)
    documents()!!.openPath(otherPrefab)
    await("Dual autosave on switch did not refresh linked scene") {
        !project.busy && prefab.file?.toAbsolutePath()==otherPrefab.toAbsolutePath() && scene.dirty &&
            scene.entities.first { it.document.key==linkedKey }.document.components["SpriteRenderer"]!!.jsonObject["size"]!!.jsonObject["x"]?.jsonPrimitive?.doubleOrNull==128.0
    }
    check(withContext(Dispatchers.IO) { Files.readString(linkedScenePath) }.contains("Switch Scene"))
    println("PROJECT_DOCUMENTS_SMOKE_PASS plain-scene/prefab/shared-texture/Save/browser-open/discard/conflict/reopen/Play/script/immutable-snapshot/export/relocation/autosave-switch/linked-import-save-refresh-reopen/dual-autosave-refresh")
}
