package sle.editor.smoke

import kotlinx.coroutines.delay
import sle.editor.workspace.*
import sle.editor.project.ProjectFiles
import java.nio.file.Files
import java.nio.file.Path
import java.awt.Frame

/** Each phase runs a fresh editor JVM with the production windows and storage lifecycle. */
internal fun runWorkspacePersistenceSmoke() {
    val root=Files.createTempDirectory("sle-layout-desktop")
    for(phase in listOf("write","read","reset","corrupt","recovered")) {
        val javaExecutable=Path.of(System.getProperty("java.home"),"bin","java.exe").toString()
        val command=mutableListOf(javaExecutable,"-cp",System.getProperty("java.class.path"))
        for(name in listOf("sle.nativeLibrary","sle.smokeReport")) System.getProperty(name)?.let { command.add("-D$name=$it") }
        command.addAll(listOf("sle.editor.app.MainKt","--layout-persistence-phase",phase,root.toString()))
        val child=ProcessBuilder(command).inheritIO().start()
        check(child.waitFor(90,java.util.concurrent.TimeUnit.SECONDS)) { child.destroyForcibly();"Layout phase timed out: $phase" }
        check(child.exitValue()==0) { "Layout phase failed: $phase" }
    }
    println("WORKSPACE_PERSISTENCE_SMOKE_PASS $root")
}

internal suspend fun runWorkspacePersistencePhase(phase: String, root: Path, workspace: EditorWorkspace) {
    val manager=workspace.windows
    suspend fun loaded() { repeat(200) { if(!manager.loading) return;delay(25) };error("Layout load timed out") }
    suspend fun project(name: String) {
        val file=root.resolve(name).resolve("$name.sleproject")
        val opened=if(Files.exists(file)) ProjectFiles.open(file) else ProjectFiles.create(root.resolve(name),name)
        workspace.project.activate(opened);loaded();delay(250)
    }
    val assets=PanelId("assets");val game=PanelId("game")
    loaded();delay(600)
    when(phase) {
        "write" -> {
            manager.hidePanel(assets)
            manager.movePanel(game,DockTarget.Detached(LogicalBounds(50f,50f,600f,500f)))
            delay(300)
            val tool=manager.boundWindows().entries.single { it.key!=manager.layout.mainHost }.value as Frame
            tool.extendedState=Frame.ICONIFIED
            manager.flushLayout()
            project("A");manager.hidePanel(game);manager.flushLayout()
            project("B");check(game !in manager.layout.hidden);manager.hidePanel(PanelId("inspector"));manager.flushLayout()
        }
        "read" -> {
            check(assets in manager.layout.hidden && manager.hostFor(game)!=manager.layout.mainHost)
            check(manager.boundWindows().values.filterIsInstance<Frame>().all { it.extendedState and Frame.ICONIFIED==0 })
            project("A");check(game in manager.layout.hidden && assets !in manager.layout.hidden)
            project("B");check(PanelId("inspector") in manager.layout.hidden && game !in manager.layout.hidden)
            val before=workspace.scene.getOrThrow().serialize()
            manager.recoverDisplays(listOf(LogicalBounds(-300f,0f,300f,200f)))
            check(manager.layout.hosts.all { it.bounds.x>=-300f && it.bounds.width<=300f })
            check(workspace.scene.getOrThrow().serialize()==before)
        }
        "reset" -> { check(assets in manager.layout.hidden);manager.resetLayout();manager.flushLayout() }
        "corrupt" -> {
            check(manager.layout.hidden.isEmpty());manager.flushLayout()
            // Write corruption after orderly close, so shutdown cannot replace the fixture.
        }
        "recovered" -> { check(manager.layoutNotice!=null);check(manager.layout.hidden.isEmpty()) }
    }
    workspace.close()
    if(phase=="corrupt") Files.writeString(WorkspaceLayoutKey.standalone().path,"{broken")
    println("WORKSPACE_LAYOUT_PHASE_PASS $phase")
}
