package sle.editor.smoke

import androidx.compose.ui.geometry.Rect
import kotlinx.coroutines.*
import sle.editor.model.SceneWorkspaceState
import sle.editor.model.ScenePlaybackState
import sle.editor.project.*
import sle.editor.project.assets.*
import sle.editor.viewport.EngineCanvas
import sle.editor.viewport.Playback
import java.awt.*
import java.awt.event.InputEvent
import java.nio.file.*
import javax.imageio.ImageIO
import javax.swing.*

internal suspend fun runAssetBrowserSmoke(
    window: JFrame,canvas: ()->EngineCanvas,project: ProjectWorkspaceState,commands: ProjectCommands,
    browser: ()->AssetBrowserState?,bounds: Map<String,Rect>,frames: ()->Int,
    scene: SceneWorkspaceState,playback: ScenePlaybackState,assetCommands: ()->ProjectAssetCommands?,
) {
    val robot=Robot().apply { autoDelay=10 }
    suspend fun await(message: String,condition: ()->Boolean) {
        repeat(220) { if(condition()) { delay(80);return };delay(25) };error("$message; asset operation: ${assetCommands()?.message}; browser errors: ${browser()?.snapshot?.errors}")
    }
    fun has(path: String)=browser()?.snapshot?.entries?.any { it.path==path }==true
    suspend fun click(id: String,double: Boolean=false) {
        if(id.startsWith("AssetFolder:") && id !in bounds) {
            await("Missing folder tree") { "AssetFolders" in bounds }
            val tree=bounds.getValue("AssetFolders").center;val origin=window.contentPane.locationOnScreen
            robot.mouseMove(origin.x+tree.x.toInt(),origin.y+tree.y.toInt())
            repeat(8) { if(id !in bounds) { robot.mouseWheel(1);delay(80) } }
        }
        await("Missing browser control: $id") { id in bounds }
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
        val at=bounds.getValue(id).center;val origin=window.contentPane.locationOnScreen
        robot.mouseMove(origin.x+at.x.toInt(),origin.y+at.y.toInt())
        repeat(if(double) 2 else 1) {
            robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);delay(60)
        }
        delay(140)
    }
    suspend fun view(label: String) {
        val menu=window.jMenuBar.components.filterIsInstance<JMenu>().single { it.text=="View" }
        val at=menu.locationOnScreen
        robot.mouseMove(at.x+menu.width/2,at.y+menu.height/2)
        robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK)
        await("View menu missing") { menu.popupMenu.isShowing }
        val item=menu.popupMenu.components.filterIsInstance<JMenuItem>().single { it.text==label }
        val point=item.locationOnScreen;robot.mouseMove(point.x+item.width/2,point.y+item.height/2)
        robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);delay(150)
    }
    val reports=Path.of(System.getProperty("sle.smokeReport")).parent
    val parent=withContext(Dispatchers.IO) { Files.createTempDirectory(reports,"asset-browser-") }
    val first=withContext(Dispatchers.IO) {
        ProjectFiles.create(parent.resolve("Browser Game"),"Browser Game").also {
            Files.createDirectories(it.assets.resolve("Scripts/Nested"))
            Files.writeString(it.assets.resolve("Scripts/main.lua"),"return {}")
            Files.writeString(it.assets.resolve("unknown.custom"),"custom data")
        }
    }
    commands.openPath(first.file)
    await("Project browser did not load: ${project.error}") { !project.busy && has("Scripts/main.lua") && has("unknown.custom") }
    val initial=browser()!!;val context=canvas().contextIdentity()
    // Exercise the actual selected-folder Import button and native chooser.
    val source=withContext(Dispatchers.IO) {
        Files.createDirectories(parent.resolve("ExternalBundle/modules")).parent.also {
            Files.writeString(it.resolve("main.lua"),"return require('modules.util')")
            Files.writeString(it.resolve("modules/util.lua"),"return {}")
        }
    }
    click("AssetFolder:Scripts")
    fun descendants(component: Component): List<Component> = listOf(component)+(if(component is Container) component.components.flatMap { descendants(it) } else emptyList())
    fun chooseImport(collision: Boolean=false,path: Path=source): Thread = Thread {
        try {
            var chooser: JFileChooser?=null
            repeat(250) {
                if(chooser==null) {
                    SwingUtilities.invokeAndWait {
                        chooser=Window.getWindows().filter { it.isShowing }.flatMap { descendants(it) }.filterIsInstance<JFileChooser>().firstOrNull()
                        chooser?.let { it.selectedFiles=arrayOf(path.toAbsolutePath().toFile());it.approveSelection() }
                    }
                    if(chooser==null) Thread.sleep(20)
                }
            }
            check(chooser!=null) { "Import chooser did not open" }
            if(collision) {
                var answered=false
                repeat(250) {
                    if(!answered) {
                        SwingUtilities.invokeAndWait {
                            val option=Window.getWindows().filter { it.isShowing }.flatMap { descendants(it) }.filterIsInstance<JOptionPane>().firstOrNull()
                            if(option!=null) { option.value="Rename";answered=true }
                        }
                        if(!answered) Thread.sleep(20)
                    }
                }
                check(answered) { "Import collision prompt did not open" }
            }
        } catch(failure: Throwable) { failure.printStackTrace() }
    }.apply { isDaemon=true;start() }
    chooseImport();click("AssetImport")
    await("Selected-folder import missing") { has("Scripts/ExternalBundle/modules/util.lua") }
    val importedId=initial.snapshot!!.idsByPath.getValue("Scripts/ExternalBundle/main.lua")
    chooseImport(collision=true);click("AssetImport")
    await("Rename collision choice missing") { has("Scripts/ExternalBundle (1)/main.lua") }
    withContext(Dispatchers.IO) {
        Files.delete(source.resolve("modules/util.lua"));Files.delete(source.resolve("modules"));Files.delete(source.resolve("main.lua"));Files.delete(source)
        Files.writeString(first.assets.resolve("Scripts/ExternalBundle/main.lua"),"return 2")
    }
    initial.refresh()
    await("Imported identity missing") { initial.snapshot?.idsByPath?.get("Scripts/ExternalBundle/main.lua")==importedId }
    check(withContext(Dispatchers.IO) { Files.readString(first.assets.resolve("Scripts/ExternalBundle/modules/util.lua")) }=="return {}")
    var selectedAsset: Path?=null
    val existingScript=first.assets.resolve("Scripts/ExternalBundle/main.lua")
    chooseImport(path=existingScript)
    assetCommands()!!.browse("",false) { selectedAsset=it }
    await("Project script Browse did not reuse existing source") { selectedAsset!=null }
    check(selectedAsset!!.toRealPath()==existingScript.toRealPath())
    check(initial.snapshot!!.idsByPath["Scripts/ExternalBundle/main.lua"]==importedId)
    selectedAsset=null
    chooseImport(path=Path.of("assets/textures/tile2.png"))
    assetCommands()!!.browse("",true) { selectedAsset=it }
    await("Texture Browse did not copy to project storage") { selectedAsset!=null && has("Textures/tile2.png") }
    check(selectedAsset!!.toRealPath()==first.assets.resolve("Textures/tile2.png").toRealPath())
    // Actual managed-operation buttons and native prompts, with UUID/recovery checks.
    fun answerPrompts(vararg answers: Any?): Thread = Thread {
        for(answer in answers) {
            var answered=false
            repeat(350) {
                if(!answered) {
                    SwingUtilities.invokeAndWait {
                        val option=Window.getWindows().filter { it.isShowing }.flatMap { descendants(it) }.filterIsInstance<JOptionPane>().firstOrNull()
                        if(option!=null) {
                            if(option.wantsInput) option.inputValue=answer ?: option.initialSelectionValue
                            option.value=if(option.wantsInput) JOptionPane.OK_OPTION else answer
                            answered=true
                        }
                    }
                    if(!answered) Thread.sleep(20)
                }
            }
            check(answered) { "Managed-operation prompt did not open" }
            Thread.sleep(200)
        }
    }.apply { isDaemon=true;start() }
    click("AssetFolder:Data")
    answerPrompts("Managed");click("AssetNewFolder")
    await("Managed folder missing") { has("Data/Managed") && assetCommands()?.busy==false }
    click("AssetFile:Data/Managed")
    answerPrompts("Renamed",JOptionPane.OK_OPTION);click("AssetRename")
    await("Managed rename missing") { has("Data/Renamed") && !has("Data/Managed") && assetCommands()?.busy==false }
    // Collapse expanded Data so Scripts stays inside the dock's short tree viewport.
    initial.toggle("Data");delay(150)
    click("AssetFolder:Scripts")
    click("AssetFile:Scripts/ExternalBundle")
    check(initial.selected=="Scripts/ExternalBundle")
    val locked=java.util.concurrent.CountDownLatch(1)
    val release=java.util.concurrent.CountDownLatch(1)
    val blocker=Thread { synchronized(AssetIdentity) { locked.countDown();release.await() } }.apply { isDaemon=true;start() }
    withContext(Dispatchers.IO) { check(locked.await(5,java.util.concurrent.TimeUnit.SECONDS)) }
    try {
        answerPrompts("Data/Bundle",JOptionPane.OK_OPTION);click("AssetMove")
        await("Managed move was not queued") { project.busy }
        check(!window.isEnabled) { "Managed mutation left authoring/playback input enabled while waiting for asset lock" }
    } finally { release.countDown() }
    withContext(Dispatchers.IO) { blocker.join(5000) }
    await("Managed folder move missing") { has("Data/Bundle/main.lua") && assetCommands()?.busy==false }
    check(initial.snapshot!!.idsByPath["Data/Bundle/main.lua"]==importedId)
    click("AssetFolder:Data")
    click("AssetFile:Data/Bundle")
    answerPrompts(JOptionPane.YES_OPTION);click("AssetDelete")
    await("Managed delete missing") { !has("Data/Bundle") && assetCommands()?.busy==false }
    answerPrompts(null);click("AssetRecover")
    await("Managed recovery missing") { has("Data/Bundle/main.lua") && assetCommands()?.busy==false }
    check(initial.snapshot!!.idsByPath["Data/Bundle/main.lua"]==importedId)
    // Return the fixture so the rest of the browser regression keeps its original paths.
    withContext(Dispatchers.IO) { ProjectAssetOperations(first).move("Data/Bundle","Scripts/ExternalBundle") }
    initial.refresh()
    await("Managed fixture return missing") { has("Scripts/ExternalBundle/main.lua") }
    if("Data" in initial.expanded) initial.toggle("Data")
    println("PROJECT_MANAGED_ASSETS_UI_SMOKE_PASS create/rename/move/delete/recover/stable-UUID")
    println("PROJECT_IMPORT_UI_SMOKE_PASS selected-folder/folder-copy/modules/source-removal/collision-rename/stable-UUID/project-script-reuse/texture-import")
    click("AssetFolder:Scripts")
    check(initial.folder=="Scripts")
    click("AssetFile:Scripts/Nested",double=true)
    await("Folder double-click failed") { initial.folder=="Scripts/Nested" }
    withContext(Dispatchers.IO) { Files.writeString(first.assets.resolve("Scripts/Nested/added.weird"),"external") }
    await("External file not discovered") { has("Scripts/Nested/added.weird") }
    click("AssetFile:Scripts/Nested/added.weird")
    check(initial.selected=="Scripts/Nested/added.weird")
    withContext(Dispatchers.IO) {
        Files.move(first.assets.resolve("Scripts/Nested/added.weird"),first.assets.resolve("Textures/renamed.weird"))
        Files.move(AssetIdentity.sidecar(first.assets.resolve("Scripts/Nested/added.weird")),AssetIdentity.sidecar(first.assets.resolve("Textures/renamed.weird")))
        Files.delete(first.assets.resolve("Scripts/Nested"))
    }
    await("External move/removal did not reconcile") { has("Textures/renamed.weird") && initial.folder=="Scripts" && initial.selected==null }
    click("AssetCrumb:")
    check(initial.folder.isEmpty())
    view("Refresh Assets")
    await("Refresh did not finish") { !initial.refreshing }
    view("Hide Asset Browser");view("Show Asset Browser")
    await("Browser did not return") { "AssetRefresh" in bounds }
    val authored=scene.serialize()
    playback.play(scene)
    await("Gameplay did not start") { canvas().scenePlayback()==Playback.Running }
    val before=frames()
    withContext(Dispatchers.IO) {
        Files.createDirectories(first.assets.resolve("Data/Large"))
        repeat(400) { Files.writeString(first.assets.resolve("Data/Large/file-$it.dat"),"data $it") }
    }
    await("Background scan missing files") { has("Data/Large/file-399.dat") }
    check(frames()>before+2 && canvas().contextIdentity()==context && canvas().scenePlayback()==Playback.Running && scene.serialize()==authored) { "Asset scan blocked/reset gameplay or altered authored data" }
    playback.stop()
    await("Stop did not restore authoring") { canvas().authoringReady() }
    initial.navigate("Textures");delay(200)
    click("AssetFile:Textures/renamed.weird")
    ImageIO.write(robot.createScreenCapture(Rectangle(window.locationOnScreen,window.size)),"png",reports.resolve("asset-browser-editor.png").toFile())
    val second=withContext(Dispatchers.IO) {
        ProjectFiles.create(parent.resolve("Second Game"),"Second Game").also { Files.writeString(it.assets.resolve("second.txt"),"second") }
    }
    commands.openPath(second.file)
    await("New project browser missing") { !project.busy && browser()!==initial && has("second.txt") }
    withContext(Dispatchers.IO) { Files.writeString(first.assets.resolve("ghost.txt"),"old project") }
    delay(450)
    check(!has("ghost.txt") && !has("unknown.custom")) { "Old project watcher leaked results" }
    commands.close()
    await("Browser did not close with project") { !project.busy && project.opened==null && browser()==null }
    commands.openPath(first.file)
    await("Browser did not reopen") { !project.busy && has("unknown.custom") }
    println("ASSET_BROWSER_SMOKE_PASS disk-discovery/unknown-files/tree/breadcrumb/double-click/external-move/delete/refresh/show-hide/background-gameplay/project-switch/close/reopen file=${first.file}")
}
