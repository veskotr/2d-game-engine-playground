package sle.editor.smoke

import sle.editor.ui.postedAfter
import androidx.compose.ui.geometry.Rect
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import sle.editor.model.*
import sle.editor.project.*
import sle.editor.project.assets.*
import sle.editor.viewport.*
import java.awt.*
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import java.nio.file.*
import java.util.concurrent.TimeUnit
import javax.imageio.ImageIO
import javax.swing.*

internal suspend fun runProjectRuntimeSmoke(window: JFrame,canvas: ()->EngineCanvas,project: ProjectWorkspaceState,
    projects: ProjectCommands,browser: ()->AssetBrowserState?,bounds: Map<String,Rect>,fields: Map<String,Rect>,
    scene: SceneWorkspaceState,playback: ScenePlaybackState,notices: sle.editor.ui.EditorNotices,distribution: Boolean=false,
) {
    val robot=Robot().apply { autoDelay=15 }
    suspend fun await(message: String,condition: ()->Boolean) {
        repeat(320) { if(condition()) { delay(100);return };delay(25) }
        error("$message; project=${project.error}; scene=${scene.error}")
    }
    fun click(point: Point) { robot.mouseMove(point.x,point.y);robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK) }
    fun key(code: Int) { robot.keyPress(code);robot.keyRelease(code) }
    suspend fun control(id: String,dialog: JDialog?=null) {
        await("Missing control $id") { id in (if(dialog!=null) fields else bounds) }
        val point=(if(dialog!=null) fields else bounds).getValue(id).center
        val at=dialog?.contentPane?.locationOnScreen ?: window.contentPane.locationOnScreen
        click(Point(at.x+point.x.toInt(),at.y+point.y.toInt()));delay(180)
    }
    suspend fun settings(): JDialog {
        val file=window.jMenuBar.components.filterIsInstance<JMenu>().single { it.text=="File" }
        val origin=file.locationOnScreen;click(Point(origin.x+file.width/2,origin.y+file.height/2))
        await("File menu did not open") { file.popupMenu.isShowing }
        val item=file.popupMenu.components.filterIsInstance<JMenuItem>().single { it.text=="Project Settings…" }
        val at=item.locationOnScreen;click(Point(at.x+item.width/2,at.y+item.height/2))
        await("Settings did not open") { Window.getWindows().filterIsInstance<JDialog>().any { it.isShowing && it.title=="Project Settings" } }
        return Window.getWindows().filterIsInstance<JDialog>().single { it.isShowing && it.title=="Project Settings" }
    }
    val reports=Path.of(System.getProperty("sle.smokeReport")).parent
    val first=withContext(Dispatchers.IO) {
        val p=ProjectFiles.create(Files.createTempDirectory(reports,"project-runtime-").resolve("Source Game"),"Source Game")
        ProjectAssetImports(p.root,p.assets).importAsset(Path.of("assets/textures/tile2.png"),"Textures")
        Files.createDirectories(p.assets.resolve("Scripts/modules"))
        Files.writeString(p.assets.resolve("Scripts/modules/value.lua"),"return 123")
        Files.writeString(p.assets.resolve("Scripts/main.lua"),"""
            local value=require('modules.value')
            return { update=function(entity,dt)
                local actor=Engine.findEntityByKey('runtime-actor')
                if actor then Engine.setTransformPosition(actor,value,135)
                else Engine.switchScene('second-scene') end
            end }
        """.trimIndent())
        fun document(key: String,actor: String)=buildJsonObject {
            put("key",key);put("name",key)
            put("entities",buildJsonArray { add(buildJsonObject {
                put("key",actor);put("name",actor)
                put("components",buildJsonObject {
                    put("Transform",buildJsonObject { put("position",buildJsonObject { put("x",240);put("y",135) }) })
                    put("Script",buildJsonObject { put("scriptAsset",p.assets.resolve("Scripts/main.lua").toString().replace('\\','/')) })
                    put("SpriteRenderer",buildJsonObject {
                        put("texture",p.assets.resolve("Textures/tile2.png").toString().replace('\\','/'))
                        put("size",buildJsonObject { put("x",64);put("y",64) })
                    })
                })
            }) })
        }.toString()
        val store=ProjectDocuments(p)
        store.save(p.assets.resolve("Scenes/start.scene.json"),ProjectDocumentKind.Scene,document("startup","startup-actor"),emptyMap(),null) { canonicalSceneDocument(it) }
        store.save(p.assets.resolve("Scenes/second.scene.json"),ProjectDocumentKind.Scene,document("second-scene","runtime-actor"),emptyMap(),null) { canonicalSceneDocument(it) }
        ProjectFiles.save(p,p.document.copy(game=ProjectGameSettings("Project Runtime",640,360,true,targetFPS=30)))
    }
    projects.openPath(first.file)
    await("Project did not open") { !project.busy && browser()?.snapshot?.idsByPath?.containsKey("Scenes/start.scene.json")==true }
    val startId=browser()!!.snapshot!!.idsByPath.getValue("Scenes/start.scene.json")
    // Exercise the actual startup selector: None, Main, second, start (sorted file paths).
    val dialog=settings()
    control("StartupScene",dialog)
    key(KeyEvent.VK_DOWN);key(KeyEvent.VK_DOWN);key(KeyEvent.VK_DOWN);key(KeyEvent.VK_DOWN);key(KeyEvent.VK_ENTER);delay(150)
    control("SaveProjectSettings",dialog)
    await("Startup selection did not save") { !project.busy && !dialog.isShowing }
    check(project.opened!!.document.startupScene==startId) { "Wrong startup selection: ${project.opened!!.document.startupScene}, expected $startId" }
    scene.addEntity();scene.selected!!.rename("Keep this unsaved draft")
    val draft=scene.serialize();val context=canvas().contextIdentity()
    control("RunProject")
    await("Embedded project launch failed") { canvas().scenePlayback()==Playback.Running && playback.project!=null }
    check(scene.serialize()==draft && scene.dirty)
    canvas().runInContext {
        if(org.lwjgl.opengl.GL.getCapabilitiesWGL().WGL_EXT_swap_control)
            check(org.lwjgl.opengl.WGLEXTSwapControl.wglGetSwapIntervalEXT()==1) { "Project VSync setting ignored" }
    }
    await("Deferred project Lua scene/module failed") {
        canvas().runtimeTransforms()["runtime-actor"]?.jsonObject?.get("position")?.jsonObject?.get("x")?.jsonPrimitive?.doubleOrNull==123.0
    }
    val viewport=canvas().gameViewport();check(viewport[4]==640f && viewport[5]==360f) { "Game resolution ignored: ${viewport.toList()}" }
    playback.pause();await("Project pause failed") { canvas().scenePlayback()==Playback.Paused }
    playback.play(scene);await("Project resume failed") { canvas().scenePlayback()==Playback.Running }
    check(canvas().contextIdentity()==context)
    ImageIO.write(robot.createScreenCapture(Rectangle(window.locationOnScreen,window.size)),"png",reports.resolve("project-runtime-editor.png").toFile())
    playback.stop();await("Project Stop failed") { canvas().scenePlayback()==Playback.Stopped && canvas().authoringReady() }
    check(scene.serialize()==draft && scene.dirty)
    canvas().runInContext {
        if(org.lwjgl.opengl.GL.getCapabilitiesWGL().WGL_EXT_swap_control)
            check(org.lwjgl.opengl.WGLEXTSwapControl.wglGetSwapIntervalEXT()==0) { "Stop retained game VSync" }
    }
    scene.newScene();projects.close();await("Project close failed") { !project.busy && project.opened==null }
    val moved=withContext(Dispatchers.IO) { first.root.resolveSibling("Relocated Game").also { Files.move(first.root,it) } }
    val movedFile=moved.resolve(first.file.fileName)
    projects.openPath(movedFile)
    await("Relocated project did not open") { !project.busy && project.opened?.root==moved.toRealPath() }
    control("RunProject")
    await("Relocated embedded project did not run") { canvas().scenePlayback()==Playback.Running && canvas().runtimeTransforms().containsKey("runtime-actor") }
    playback.stop();await("Relocated Stop failed") { canvas().scenePlayback()==Playback.Stopped }
    // Actual standalone command, with project and engine resources outside cwd.
    val standalone=withContext(Dispatchers.IO) {
        val executable=Path.of("build/debug/engine_app/Debug/engine_app.exe").toAbsolutePath()
        val process=ProcessBuilder(executable.toString(),"--project",movedFile.toAbsolutePath().toString(),"--frames","8")
            .directory(reports.toFile()).redirectErrorStream(true).redirectOutput(reports.resolve("project-runtime-standalone.log").toFile()).start()
        check(process.waitFor(30,TimeUnit.SECONDS)) { process.destroyForcibly();"Standalone project did not finish" }
        process.exitValue() to Files.readString(reports.resolve("project-runtime-standalone.log"))
    }
    check(standalone.first==0 && standalone.second.contains("scene: second-scene") && !standalone.second.contains("[ERROR]")) { standalone.toString() }
    if(distribution) runProjectDistributionSmoke(window,canvas,project,scene)
    // Missing startup must preserve the dirty draft and stopped runtime.
    val active=project.opened!!
    project.refreshSettings(withContext(Dispatchers.IO) { ProjectFiles.save(active,active.document.copy(startupScene=null)) })
    scene.addEntity();val before=scene.serialize()
    val beforeMissing=notices.recent.firstOrNull()
    control("RunProject")
    await("Missing startup was not reported") { !project.busy && notices.postedAfter(beforeMissing).any { it.level==sle.editor.ui.NoticeLevel.Error } }
    check(scene.serialize()==before && playback.editing && canvas().scenePlayback()==Playback.Stopped)
    println("PROJECT_RUNTIME_SMOKE_PASS startup-selector/settings/dirty-draft/RunProject/deferred-scene/Lua-module/resolution/pause/resume/Stop/relocation/standalone-unrelated-cwd/missing-startup")
}
