package sle.editor.smoke

import kotlinx.coroutines.*
import sle.editor.model.*
import sle.editor.project.*
import sle.editor.viewport.EngineCanvas
import java.awt.*
import java.awt.event.InputEvent
import java.nio.file.*
import java.util.concurrent.TimeUnit
import javax.swing.*

/** Runs after the existing whole-project fixture has loaded/switched/relocated. */
internal suspend fun runProjectDistributionSmoke(window: JFrame,canvas: ()->EngineCanvas,project: ProjectWorkspaceState,scene: SceneWorkspaceState) {
    val opened=project.opened!!
    withContext(Dispatchers.IO) {
        // Force native validation to generate prefab derivatives in the staged copy.
        ProjectDocuments(opened).save(opened.assets.resolve("Prefabs/export.prefab.json"),ProjectDocumentKind.Prefab,
            EntityDocument().serialize(),emptyMap(),null) { sle.editor.bridge.NativeEngine.canonicalPrefab(it.toByteArray()) }
        Files.createDirectories(opened.root.resolve(".sle/trash/export-sentinel"))
        Files.writeString(opened.root.resolve(".sle/trash/export-sentinel/payload"),"keep recovery data")
    }
    val reports=Path.of(System.getProperty("sle.smokeReport")).parent
    val target=Files.createTempDirectory(reports,"distribution-").resolve("Exported Game")
    val runtime=Path.of("build/debug/runtime/Release").toAbsolutePath()
    scene.addEntity();scene.selected!!.rename("Keep unsaved export draft")
    val draft=scene.serialize();val context=canvas().contextIdentity()
    val robot=Robot().apply { autoDelay=15 }
    suspend fun await(message: String,condition: ()->Boolean) {
        repeat(600) { if(condition()) { delay(80);return };delay(25) };error("$message: ${project.error}")
    }
    fun descendants(component: Component): List<Component> = listOf(component)+(if(component is Container) component.components.flatMap { descendants(it) } else emptyList())
    fun chooserAnswers(output: Path): Thread=Thread {
        var previous: JFileChooser?=null
        for(path in listOf(runtime,output)) {
            var answered=false
            repeat(500) {
                if(!answered) {
                    SwingUtilities.invokeAndWait {
                        val chooser=Window.getWindows().filter { it.isShowing }.flatMap(::descendants).filterIsInstance<JFileChooser>().firstOrNull { it!==previous }
                        if(chooser!=null) { chooser.selectedFile=path.toFile();previous=chooser;chooser.approveSelection();answered=true }
                    }
                    if(!answered) Thread.sleep(20)
                }
            }
            check(answered) { "Export chooser did not open" }
        }
    }.apply { isDaemon=true;start() }
    suspend fun export(output: Path) {
        val file=window.jMenuBar.components.filterIsInstance<JMenu>().single { it.text=="File" }
        file.doClick();await("File menu not visible") { file.popupMenu.isShowing }
        val item=file.popupMenu.components.filterIsInstance<JMenuItem>().singleOrNull { it.text=="Export Game…" } ?: error("Export Game menu is missing")
        check(item.isEnabled)
        chooserAnswers(output)
        val at=item.locationOnScreen;robot.mouseMove(at.x+item.width/2,at.y+item.height/2)
        robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK)
        await("Export never started") { project.busy || Files.exists(output) || project.error!=null }
        await("Export never finished") { !project.busy }
    }
    export(target)
    check(project.error==null && Files.exists(target.resolve("sle-distribution.json"))) { "Export failed: ${project.error}" }
    check(scene.serialize()==draft && scene.dirty && canvas().contextIdentity()==context)
    check(!Files.exists(target.resolve("game/.sle"))) { "Export included local state/cache/trash" }
    check(Files.readString(opened.root.resolve(".sle/trash/export-sentinel/payload"))=="keep recovery data")
    javax.imageio.ImageIO.write(robot.createScreenCapture(Rectangle(window.locationOnScreen,window.size)),"png",reports.resolve("project-distribution-editor.png").toFile())
    // Existing destinations must survive the actual chooser/export path.
    val occupied=target.resolveSibling("Existing");Files.createDirectory(occupied);Files.writeString(occupied.resolve("mine.txt"),"keep")
    export(occupied)
    check(project.error!=null && Files.readString(occupied.resolve("mine.txt"))=="keep")
    check(scene.serialize()==draft && scene.dirty && canvas().contextIdentity()==context)
    project.error=null
    val relocated=withContext(Dispatchers.IO) { target.resolveSibling("Relocated Delivery").also { Files.move(target,it) } }
    val sourceScript=opened.assets.resolve("Scripts/main.lua")
    val original=withContext(Dispatchers.IO) { Files.readAllBytes(sourceScript) }
    try {
        withContext(Dispatchers.IO) { Files.writeString(sourceScript,"error('SOURCE_PROJECT_MUST_NOT_BE_USED')") }
        val log=reports.resolve("project-distribution-standalone.log")
        val result=withContext(Dispatchers.IO) {
            val process=ProcessBuilder(relocated.resolve("engine_app.exe").toAbsolutePath().toString(),"--frames","8")
                .directory(reports.toFile()).redirectErrorStream(true).redirectOutput(log.toFile()).start()
            check(process.waitFor(30,TimeUnit.SECONDS)) { process.destroyForcibly();"Exported standalone timed out" }
            process.exitValue() to Files.readString(log)
        }
        check(result.first==0 && result.second.contains("scene: second-scene") && !result.second.contains("[ERROR]")) { result.toString() }
    } finally { withContext(Dispatchers.IO) { Files.write(sourceScript,original) } }
    check(scene.serialize()==draft && scene.dirty && canvas().contextIdentity()==context)
    println("PROJECT_DISTRIBUTION_SMOKE_PASS menu/choosers/saved-sources/dirty-draft/failure-preservation/relocation/no-config-launch/unrelated-cwd/deferred-scene/Lua-module/source-independence delivery=$relocated")
}
