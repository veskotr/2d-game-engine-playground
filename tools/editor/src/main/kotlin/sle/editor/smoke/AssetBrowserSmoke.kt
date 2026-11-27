package sle.editor.smoke

import androidx.compose.ui.geometry.Rect
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import sle.editor.model.canonicalSceneDocument
import sle.editor.model.SceneWorkspaceState
import sle.editor.model.ScenePlaybackState
import sle.editor.project.*
import sle.editor.project.assets.*
import sle.editor.viewport.EngineCanvas
import sle.editor.viewport.Playback
import sle.editor.viewport.Vec2
import java.awt.*
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
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
        repeat(220) { if(condition()) { delay(80);return };delay(25) }
        Window.getWindows().filterIsInstance<JDialog>().firstOrNull {
            it.isShowing && it.title.startsWith("Spritesheet — ")
        }?.let {
            val report = Path.of(System.getProperty("sle.smokeReport")).resolveSibling("spritesheet-smoke-failure.png")
            ImageIO.write(robot.createScreenCapture(it.bounds), "png", report.toFile())
        }
        error("$message; asset operation: ${assetCommands()?.message}; browser errors: ${browser()?.snapshot?.errors}")
    }
    fun has(path: String)=browser()?.snapshot?.entries?.any { it.path==path }==true
    fun spritesheet() = Window.getWindows().filterIsInstance<JDialog>()
        .single { it.isShowing && it.title.startsWith("Spritesheet — ") }
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
        if (id == "AssetOpenSpritesheet") {
            val details = bounds.getValue("AssetDetails")
            repeat(12) {
                val button = bounds.getValue(id)
                if (button.height >= 20f && button.top >= details.top && button.bottom <= details.bottom) return@repeat
                val origin = window.contentPane.locationOnScreen
                robot.mouseMove(origin.x + details.center.x.toInt(), origin.y + details.center.y.toInt())
                robot.mouseWheel(if (button.height > 0 && button.top <= details.top) -1 else 1)
                delay(100)
            }
        }
        val at=bounds.getValue(id).center
        val host = if (id.startsWith("Spritesheet")) spritesheet() else window
        val origin = (host as RootPaneContainer).contentPane.locationOnScreen
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
    fun writeThumbnailSource(file: Path, color: Color) {
        val image = java.awt.image.BufferedImage(12, 8, java.awt.image.BufferedImage.TYPE_INT_RGB)
        image.setRGB(0, 0, color.rgb)
        val bytes = java.io.ByteArrayOutputStream()
        check(ImageIO.write(image, "png", bytes))
        Files.write(file, bytes.toByteArray().copyOf(1024))
    }
    fun descendants(component: Component): List<Component> = listOf(component)+(if(component is Container) component.components.flatMap { descendants(it) } else emptyList())
    val reports=Path.of(System.getProperty("sle.smokeReport")).parent
    val parent=withContext(Dispatchers.IO) { Files.createTempDirectory(reports,"asset-browser-") }
    val first=withContext(Dispatchers.IO) {
        ProjectFiles.create(parent.resolve("Browser Game"),"Browser Game").also {
            Files.createDirectories(it.assets.resolve("Scripts/Nested"))
            Files.writeString(it.assets.resolve("Scripts/main.lua"),"return {}")
            Files.writeString(it.assets.resolve("unknown.custom"),"custom data")
            val texture = it.assets.resolve("Textures/thumbnail-smoke.png")
            writeThumbnailSource(texture, Color.BLACK)
            ProjectDocuments(it).save(it.assets.resolve("Scenes/Thumbnail.scene.json"), ProjectDocumentKind.Scene,
                sle.editor.model.SceneDocument(name = "Thumbnail").serialize(), mapOf(texture to "thumbnail"), null) {}
        }
    }
    commands.openPath(first.file)
    await("Project browser did not load: ${project.error}") { !project.busy && has("Scripts/main.lua") && has("unknown.custom") }
    val initial=browser()!!;val context=canvas().contextIdentity()
    initial.navigate("Textures")
    await("Texture thumbnail did not appear in the asset browser") { "AssetThumbnail:Textures/thumbnail-smoke.png" in bounds }
    val cache = first.root.resolve(".sle/cache/thumbnails")
    withContext(Dispatchers.IO) { Files.list(cache).use { Files.delete(it.toList().single()) } }
    initial.refresh()
    await("Refreshing did not regenerate the deleted thumbnail") {
        Files.list(cache).use { it.anyMatch { file -> file.fileName.toString().endsWith(".png") } }
    }
    withContext(Dispatchers.IO) {
        val texture = first.assets.resolve("Textures/thumbnail-smoke.png")
        val timestamp = Files.getLastModifiedTime(texture)
        writeThumbnailSource(texture, Color.MAGENTA)
        Files.setLastModifiedTime(texture, timestamp)
    }
    await("Replacing the texture did not refresh its thumbnail") {
        Files.list(cache).use { it.filter { file -> file.fileName.toString().endsWith(".png") }.count() == 2L }
    }
    println("Asset browser smoke: thumbnail displayed, regenerated, and refreshed after source replacement")
    val thumbnailPath = "Textures/thumbnail-smoke.png"
    initial.select(thumbnailPath)
    var referenceItems: List<String> = emptyList()
    val references = answerPrompts("Find References", { prompt ->
        referenceItems = prompt.request.items
        prompt.answer("Open")
    })
    checkNotNull(assetCommands()).findReferences(thumbnailPath)
    await("Find References dialog did not open") { !references.isAlive }
    check(referenceItems == listOf("Scenes/Thumbnail.scene.json")) { "Find References listed $referenceItems" }
    await("Find References did not open the referring scene") {
        scene.file == first.assets.resolve("Scenes/Thumbnail.scene.json")
    }
    println("PROJECT_FIND_REFERENCES_UI_SMOKE_PASS saved-consumer/open")
    // Exercise the actual selected-folder Import button and native chooser.
    val source=withContext(Dispatchers.IO) {
        Files.createDirectories(parent.resolve("ExternalBundle/modules")).parent.also {
            Files.writeString(it.resolve("main.lua"),"return require('modules.util')")
            Files.writeString(it.resolve("modules/util.lua"),"return {}")
        }
    }
    click("AssetFolder:Scripts")
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
                answerPrompts("Import collision", press("Rename")).join()
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
    sle.editor.ui.EditorFilePicker.answerNext { listOf(existingScript.toAbsolutePath().toFile()) }
    assetCommands()!!.browse("",false) { selectedAsset=it }
    await("Project script Browse did not reuse existing source") { selectedAsset!=null }
    check(selectedAsset!!.toRealPath()==existingScript.toRealPath())
    check(initial.snapshot!!.idsByPath["Scripts/ExternalBundle/main.lua"]==importedId)
    selectedAsset=null
    sle.editor.ui.EditorFilePicker.answerNext { listOf(Path.of("assets/textures/tile2.png").toAbsolutePath().toFile()) }
    assetCommands()!!.browse("",true) { selectedAsset=it }
    await("Texture Browse did not copy to project storage") { selectedAsset!=null && has("Textures/tile2.png") }
    check(selectedAsset!!.toRealPath()==first.assets.resolve("Textures/tile2.png").toRealPath())
    initial.navigate("Textures")
    click("AssetFile:Textures/tile2.png")
    scene.addSprite(selectedAsset!!.toString(), "Filtered sprite", Vec2(160.0, 160.0), null)
    val documentBeforeFilter = scene.serialize()
    suspend fun chooseFilter(filter: AssetTextureFilter?, steps: Int) {
        click("AssetTextureFilter")
        repeat(steps) { robot.keyPress(KeyEvent.VK_DOWN); robot.keyRelease(KeyEvent.VK_DOWN); delay(60) }
        robot.keyPress(KeyEvent.VK_ENTER); robot.keyRelease(KeyEvent.VK_ENTER)
        await("Texture filter change did not reach Details") {
            assetCommands()?.busy == false && initial.snapshot?.textureFilters?.get("Textures/tile2.png") == filter
        }
        check(scene.dirty && scene.serialize() == documentBeforeFilter) { "Texture filter change altered the scene draft" }
    }
    chooseFilter(AssetTextureFilter.Nearest, 2)
    chooseFilter(AssetTextureFilter.Linear, 3)
    chooseFilter(null, 1)
    chooseFilter(AssetTextureFilter.Nearest, 2)
    println("PROJECT_TEXTURE_FILTER_UI_SMOKE_PASS nearest/linear/default/dirty-draft")
    // Actual managed-operation buttons and native prompts, with UUID/recovery checks.
    val texturePath = "Textures/tile2.png"
    fun key(code: Int) { robot.keyPress(code); robot.keyRelease(code) }
    suspend fun enterField(id: String, text: String) {
        click(id)
        key(KeyEvent.VK_END)
        repeat(16) { key(KeyEvent.VK_BACK_SPACE) }
        for (char in text) key(KeyEvent.getExtendedKeyCodeForChar(char.code))
    }
    suspend fun choose(id: String, steps: Int) {
        click(id)
        repeat(steps) { key(KeyEvent.VK_DOWN); delay(60) }
        key(KeyEvent.VK_ENTER)
    }
    click("AssetOpenSpritesheet")
    enterField("SpritesheetCell", "366, 364")
    enterField("SpritesheetMargin", "1, 2")
    enterField("SpritesheetSpacing", "1, 2")
    click("SpritesheetAdd")
    await("Grid slicing did not publish four padded regions") {
        assetCommands()?.busy == false && initial.snapshot?.spriteRegions?.get(texturePath)?.size == 4
    }
    click("SpritesheetClose")
    val firstRegion = initial.snapshot!!.spriteRegions.getValue(texturePath).first()
    check(firstRegion.x == 1 && firstRegion.y == 2 && firstRegion.width == 366 && firstRegion.height == 364)
    val regionId = firstRegion.id
    scene.entities.single().setSpriteRegion(regionId)
    val documentWithRegion = scene.serialize()
    click("AssetOpenSpritesheet")
    choose("SpritesheetMode", 2)
    choose("SpritesheetRegion", 2)
    enterField("SpritesheetName", "idle")
    val preview = bounds.getValue("SpritesheetCanvas")
    val origin = spritesheet().contentPane.locationOnScreen
    val dragX = origin.x + preview.left.toInt() + 30
    val dragY = origin.y + preview.top.toInt() + 30
    robot.mouseMove(dragX, dragY)
    robot.mousePress(InputEvent.BUTTON1_DOWN_MASK)
    robot.mouseMove(dragX + 80, dragY + 65)
    delay(80)
    robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK)
    click("SpritesheetSave")
    await("Region edit did not preserve the selected ID") {
        val region = initial.snapshot?.spriteRegions?.get(texturePath)?.firstOrNull()
        assetCommands()?.busy == false && region?.id == regionId && region.name == "idle" && region.width in 30..150 && region.height in 25..130
    }
    val editedRegion = initial.snapshot!!.spriteRegions.getValue(texturePath).first()
    click("SpritesheetClose")
    check(scene.dirty && scene.serialize() == documentWithRegion) { "Region edit altered the scene draft" }
    withContext(Dispatchers.IO) {
        val documents = ProjectDocuments(first)
        val path = first.assets.resolve("Scenes/Region.scene.json")
        documents.save(path, ProjectDocumentKind.Scene, documentWithRegion, emptyMap(), null) {
            canonicalSceneDocument(it)
        }
        val reopened = Json.parseToJsonElement(documents.open(path).source).jsonObject
        val sprite = reopened.getValue("entities").jsonArray.single().jsonObject.getValue("components")
            .jsonObject.getValue("SpriteRenderer").jsonObject
        check(sprite.getValue("regionId").jsonPrimitive.content == regionId)
    }
    println("PROJECT_SPRITE_REGIONS_UI_SMOKE_PASS grid/edit/stable-id/dirty-draft/save/reopen")
    // Opening a project opens its saved Main scene. Managed operations require New in both
    // workspaces (docs/topics/Projects.md, Managed assets), so start a scene draft as a user would.
    check(scene.file!=null) { "Project open did not restore its saved scene" }
    scene.newScene()
    await("Scene draft did not replace the saved scene") { scene.file==null && !scene.dirty }
    click("AssetFolder:Data")
    answerPrompts("New Folder",press("Create","Managed"));click("AssetNewFolder")
    await("Managed folder missing") { has("Data/Managed") && assetCommands()?.busy==false }
    click("AssetFile:Data/Managed")
    answerPrompts("Rename",press("Rename","Renamed"),press("Move"));click("AssetRename")
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
        answerPrompts("Move",press("Move","Data/Bundle"),press("Move"));click("AssetMove")
        await("Managed move was not queued") { project.busy }
        check(!window.isEnabled) { "Managed mutation left authoring/playback input enabled while waiting for asset lock" }
    } finally { release.countDown() }
    withContext(Dispatchers.IO) { blocker.join(5000) }
    await("Managed folder move missing") { has("Data/Bundle/main.lua") && assetCommands()?.busy==false }
    check(initial.snapshot!!.idsByPath["Data/Bundle/main.lua"]==importedId)
    click("AssetFolder:Data")
    click("AssetFile:Data/Bundle")
    answerPrompts("Delete",press("Move to Trash"));click("AssetDelete")
    await("Managed delete missing") { !has("Data/Bundle") && assetCommands()?.busy==false }
    answerPrompts("Recover",press("Restore"));click("AssetRecover")
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
    await("Browser did not reopen with the saved texture filter") {
        !project.busy && has("unknown.custom") &&
            browser()?.snapshot?.textureFilters?.get(texturePath) == AssetTextureFilter.Nearest &&
            browser()?.snapshot?.spriteRegions?.get(texturePath)?.firstOrNull()?.let {
                it == editedRegion
            } == true
    }
    println("ASSET_BROWSER_SMOKE_PASS disk-discovery/unknown-files/tree/breadcrumb/double-click/external-move/delete/refresh/show-hide/background-gameplay/project-switch/close/reopen file=${first.file}")
}
