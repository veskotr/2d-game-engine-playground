package sle.editor.smoke

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import sle.editor.model.ScenePlaybackState
import sle.editor.model.SceneWorkspaceState
import sle.editor.project.ProjectCommands
import sle.editor.project.ProjectFiles
import sle.editor.project.ProjectWorkspaceState
import sle.editor.project.assets.AssetBrowserState
import sle.editor.project.assets.ProjectAssetImports
import sle.editor.ui.EditorWindowChrome
import sle.editor.ui.theme.*
import sle.editor.viewport.EngineCanvas
import sle.editor.workspace.EditorWorkspace
import sle.editor.workspace.PanelId
import java.awt.Rectangle
import java.awt.Robot
import java.awt.Window
import java.awt.image.BufferedImage
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO
import javax.swing.JFrame

/**
 * Finite Little Forest acceptance capture (session 6). One representative project
 * fixture is captured in both themes: Scene and Entity inspectors, a validation
 * state, the status bar (a Delete notice with Undo, and a sticky error), read-only
 * fields during Play, and a detached Inspector window. It then
 * proves that a theme switch during paused Play changes only editor chrome.
 *
 * System properties: sle.appearance.window=WxH (logical window size),
 * sle.appearance.density=Compact|Comfortable, sle.appearance.tag=<capture folder>.
 * OS scaling is simulated by the caller with -Dsun.java2d.uiScale.
 */
internal suspend fun runEditorAppearanceSmoke(
    window: JFrame, canvas: ()->EngineCanvas, project: ProjectWorkspaceState, projects: ProjectCommands,
    browser: ()->AssetBrowserState?, scene: SceneWorkspaceState, playback: ScenePlaybackState,
    workspace: EditorWorkspace, editEntity: (String)->Unit, showScene: (Boolean)->Unit,
    statusBar: ()->androidx.compose.ui.geometry.Rect?,
) {
    suspend fun await(message: String,condition: ()->Boolean) {
        repeat(240) { if(condition()) { delay(120);return };delay(25) }
        error(message)
    }
    val reports=Path.of(System.getProperty("sle.smokeReport")).parent
    val tag=System.getProperty("sle.appearance.tag") ?: "default"
    val out=reports.resolve("little-forest/session6/$tag").also { Files.createDirectories(it) }
    val density=EditorDensity.valueOf(System.getProperty("sle.appearance.density") ?: "Compact")
    fun applySize() { System.getProperty("sle.appearance.window")?.split('x')?.map(String::toInt)?.let { (w,h) -> window.setSize(w,h);window.setLocation(0,0) } }
    applySize()
    val robot=Robot()
    val log=StringBuilder()
    fun capture(target: Window,name: String) {
        val file=out.resolve("$name.png")
        ImageIO.write(robot.createScreenCapture(Rectangle(target.locationOnScreen,target.size)),"png",file.toFile())
        log.appendLine("$name window=${target.width}x${target.height} uiScale=${target.graphicsConfiguration.defaultTransform.scaleX} density=$density")
    }
    // The status bar is the main window's last row; its Compose bounds are in device pixels.
    fun captureStatusBar(name: String) {
        val bar=statusBar() ?: error("Status bar was not laid out")
        val pane=window.contentPane
        val height=kotlin.math.ceil(bar.height/window.graphicsConfiguration.defaultTransform.scaleX).toInt()
        val origin=pane.locationOnScreen
        ImageIO.write(robot.createScreenCapture(Rectangle(origin.x,origin.y+pane.height-height,pane.width,height)),"png",out.resolve("$name.png").toFile())
        log.appendLine("$name status-bar=${pane.width}x$height notice=${workspace.notices.current?.level}:${workspace.notices.current?.text}")
    }
    val notices=workspace.notices

    // Representative fixture: imported texture and script, a Player with Transform + Sprite, and a camera.
    val fixture=withContext(Dispatchers.IO) {
        val p=ProjectFiles.create(Files.createTempDirectory(reports,"appearance-").resolve("Forest Game"),"Forest Game")
        ProjectAssetImports(p.root,p.assets).importAsset(Path.of("assets/textures/tile2.png"),"Textures")
        Files.writeString(p.assets.resolve("Scripts/player.lua"),"return {}")
        p
    }
    projects.openPath(fixture.file)
    await("Fixture project did not open") { !project.busy && browser()?.snapshot?.idsByPath?.containsKey("Textures/tile2.png")==true }
    fun vector(x: Int,y: Int)=buildJsonObject { put("x",x);put("y",y) }
    scene.rename("Forest Scene");scene.addEntity()
    val player=scene.selected!!;player.rename("Player")
    player.setValue("Transform",listOf("position"),vector(128,64))
    player.addComponent("SpriteRenderer")
    player.setValue("SpriteRenderer",listOf("size"),vector(64,64))
    player.setValue("SpriteRenderer",listOf("texture"),JsonPrimitive(fixture.assets.resolve("Textures/tile2.png").toString().replace('\\','/')))
    scene.addCamera();scene.select(player.document.key)
    val key=player.document.key
    fun setAppearance(mode: EditorThemeMode)=workspace.appearance.update(EditorAppearance(mode=mode,density=density))

    for(mode in EditorThemeMode.entries) {
        val m=mode.name.lowercase()
        setAppearance(mode);showScene(false);scene.select(key);delay(700)
        check(EditorWindowChrome.darkCaption(window)?.let { it==(mode==EditorThemeMode.Dark) } ?: true) { "Main caption not themed for $mode" }
        capture(window,"scene-$m")
        // Validation: an intermediate numeric draft is invalid but keeps the last valid value.
        val before=scene.document()
        scene.selected!!.editField("Transform",listOf("rotation"),JsonPrimitive(0.0),"-")
        delay(400);capture(window,"validation-$m")
        check(scene.selected!!.fieldErrors.isNotEmpty() && scene.document()==before) { "Invalid draft changed the scene" }
        scene.selected!!.editField("Transform",listOf("rotation"),JsonPrimitive(0.0),"0.0")
        await("Draft did not clear") { scene.selected!!.fieldErrors.isEmpty() }
        // Status bar: a Delete posts "Deleted <name>" whose Undo runs the registered Undo command.
        val beforeDelete=scene.document()
        scene.addEntity();val crate=scene.selected!!.document.name
        check(sle.editor.ui.deleteSceneSelection(scene)) { "Delete failed: ${scene.error}" }
        await("Delete notice did not appear") { notices.current?.text=="Deleted $crate" && notices.current?.action?.available()==true }
        delay(300);captureStatusBar("status-bar-undo-$m")
        check(notices.invoke(notices.current!!)) { "Undo action did not run" }
        check(scene.entities.any { it.document.name==crate } && notices.current?.action==null) { "Undo notice did not restore the entity" }
        scene.undo()
        check(scene.document()==beforeDelete) { "Undoing the delete and the create did not restore the fixture" }
        // A document error is consumed into a sticky error notice with Dismiss.
        scene.error="Could not save Forest Scene: the folder is read-only (sample)"
        await("Scene error did not reach the status bar") { scene.error==null && notices.current?.level==sle.editor.ui.NoticeLevel.Error }
        delay(300);captureStatusBar("status-bar-error-$m")
        notices.dismiss();scene.select(key);delay(200)
        // Entity tab: prefab-style inspector with Transform and Sprite Renderer sections.
        editEntity(key);delay(700);capture(window,"entity-$m")
        showScene(false);scene.select(key);delay(300)
    }

    // Detached Inspector: theme switch reaches the second window and its caption.
    workspace.windows.detachPanel(PanelId("inspector"))
    await("Details did not detach") { Window.getWindows().count { it is JFrame && it.isShowing }>=2 }
    val detached=Window.getWindows().filterIsInstance<JFrame>().first { it.isShowing && it!==window }
    // The harness keeps the main window on top; lift the detached window above it for capture.
    window.isAlwaysOnTop=false;detached.isAlwaysOnTop=true;detached.setBounds(window.x+window.width/3,window.y+60,420,640);detached.toFront();delay(400)
    for(mode in EditorThemeMode.entries) {
        setAppearance(mode);delay(600)
        check(EditorWindowChrome.darkCaption(detached)?.let { it==(mode==EditorThemeMode.Dark) } ?: true) { "Detached caption not themed for $mode" }
        capture(detached,"detached-inspector-${mode.name.lowercase()}")
    }
    workspace.windows.resetLayout()
    await("Layout did not reset") { Window.getWindows().count { it is JFrame && it.isShowing }==1 }
    window.isAlwaysOnTop=true;window.toFront()
    // Restore Default Layout also restores the default main-window bounds.
    applySize();delay(500)

    // Theme switch during paused Play: chrome only. Same document, dirty flag,
    // render context and game pixels; read-only inspector captured in both themes.
    setAppearance(EditorThemeMode.Dark);delay(300)
    playback.play(scene);showScene(true)
    await("Play did not start") { !playback.editing }
    delay(1200);playback.pause();delay(800)
    fun gamePixels(): BufferedImage { val c=canvas();return robot.createScreenCapture(Rectangle(c.locationOnScreen,c.size)) }
    val document=scene.document();val dirty=scene.dirty;val context=canvas().contextIdentity()
    val darkFrame=gamePixels();capture(window,"play-readonly-dark")
    setAppearance(EditorThemeMode.Light);delay(900)
    val lightFrame=gamePixels();capture(window,"play-readonly-light")
    check(scene.document()==document && scene.dirty==dirty) { "Theme switch changed the scene document or dirty state" }
    check(canvas().contextIdentity()==context) { "Theme switch rebuilt the runtime/render context" }
    check(darkFrame.width==lightFrame.width && darkFrame.height==lightFrame.height) { "Game view resized on theme switch" }
    var differing=0
    for(y in 0 until darkFrame.height) for(x in 0 until darkFrame.width) if(darkFrame.getRGB(x,y)!=lightFrame.getRGB(x,y)) differing++
    val ratio=differing.toDouble()/(darkFrame.width*darkFrame.height)
    log.appendLine("play-theme-switch game pixels differing=$differing ratio=$ratio context=$context")
    check(ratio<0.001) { "Theme switch changed rendered game pixels: $differing differ" }
    playback.stop();setAppearance(EditorThemeMode.Dark)

    withContext(Dispatchers.IO) { Files.writeString(out.resolve("captures.txt"),log.toString()) }
    println("EDITOR_APPEARANCE_SMOKE_PASS tag=$tag density=$density window=${window.width}x${window.height} game-pixels-differing=$differing dir=$out")
}
