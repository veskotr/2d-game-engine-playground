package sle.editor.smoke

import androidx.compose.ui.geometry.Rect
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import sle.editor.model.SceneWorkspaceState
import sle.editor.project.*
import java.awt.*
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import java.nio.file.*
import javax.imageio.ImageIO
import javax.swing.*

internal suspend fun runProjectSmoke(
    window: JFrame,state: ProjectWorkspaceState,commands: ProjectCommands,
    scene: SceneWorkspaceState,fields: Map<String,Rect>,documents: ()->ProjectDocumentCommands?,
    setTheme: (sle.editor.ui.theme.EditorThemeMode)->Unit,
) {
    val robot=Robot().apply { autoDelay=12 }
    suspend fun await(message: String,condition: ()->Boolean) {
        repeat(180) { if(condition()) { delay(60);return };delay(25) }
        error(message)
    }
    fun popup()=MenuSelectionManager.defaultManager().selectedPath.filterIsInstance<JPopupMenu>().lastOrNull { it.isShowing }
    fun click(point: Point) {
        robot.mouseMove(point.x,point.y);robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK)
    }
    suspend fun menu(label: String,vararg path: String) {
        delay(150) // Let the menu model publish the final busy/editable state.
        val header=window.jMenuBar.components.filterIsInstance<JMenu>().single { it.text==label }
        val point=header.locationOnScreen;click(Point(point.x+header.width/2,point.y+header.height/2))
        await("Menu did not open: $label") { popup()!=null }
        for((index,text) in path.withIndex()) {
            val item=popup()!!.components.filterIsInstance<JMenuItem>().single { it.text==text }
            check(item.isEnabled) { "$text is disabled; busy=${state.busy}; activation=${state.activationRevision}; scene=${scene.name}; error=${state.error}" }
            val at=item.locationOnScreen
            robot.mouseMove(at.x+item.width/2,at.y+item.height/2)
            if(index<path.lastIndex) await("Submenu missing: $text") { (item as JMenu).popupMenu.isShowing }
            else { click(Point(at.x+item.width/2,at.y+item.height/2));delay(120) }
        }
    }
    fun settingsWindow()=Window.getWindows().filterIsInstance<JDialog>().firstOrNull { it.isShowing && it.title=="Project Settings" }
    suspend fun setText(id: String,text: String) {
        val dialog=settingsWindow() ?: error("Settings closed unexpectedly")
        val at=fields.getValue(id).center;val origin=dialog.contentPane.locationOnScreen
        click(Point(origin.x+at.x.toInt(),origin.y+at.y.toInt()))
        delay(80)
        robot.keyPress(KeyEvent.VK_CONTROL);robot.keyPress(KeyEvent.VK_A);robot.keyRelease(KeyEvent.VK_A);robot.keyRelease(KeyEvent.VK_CONTROL)
        delay(80)
        for(character in text) {
            val key=KeyEvent.getExtendedKeyCodeForChar(character.uppercaseChar().code)
            if(character.isUpperCase()) robot.keyPress(KeyEvent.VK_SHIFT)
            robot.keyPress(key);robot.keyRelease(key)
            if(character.isUpperCase()) robot.keyRelease(KeyEvent.VK_SHIFT)
        }
        delay(80)
    }
    suspend fun saveSettings() {
        val dialog=settingsWindow()!!;val origin=dialog.contentPane.locationOnScreen
        val at=fields.getValue("SaveProjectSettings").center
        click(Point(origin.x+at.x.toInt(),origin.y+at.y.toInt()));delay(100)
    }
    val reports=Path.of(System.getProperty("sle.smokeReport")).parent
    val parent=Files.createTempDirectory(reports,"project-smoke-")
    commands.createAt(parent.resolve("My Game"),"My Game")
    await("Project creation failed: ${state.error}") { !state.busy && state.opened!=null }
    val created=state.opened!!;check(Files.isDirectory(created.assets.resolve("Scripts")))
    check(scene.file==created.assets.resolve("Scenes/Main.scene.json") && scene.entities.isEmpty() && !scene.dirty) { "New project did not open its saved empty Main scene" }
    scene.rename("Settings must keep this draft")
    menu("File","Project Settings…")
    await("Settings dialog did not open") { settingsWindow()!=null && "SaveProjectSettings" in fields }
    check(settingsWindow()!!.owner===window) { "Project Settings is not owned by the editor window: ${settingsWindow()!!.owner}" }
    setText("ProjectName","Theme Draft")
    for(mode in sle.editor.ui.theme.EditorThemeMode.entries) {
        setTheme(mode);delay(450)
        val dialog=settingsWindow()!!
        sle.editor.ui.EditorWindowChrome.darkCaption(dialog)?.let { dark ->
            check(dark==(mode==sle.editor.ui.theme.EditorThemeMode.Dark)) { "Project Settings caption is not themed for $mode" }
        }
        ImageIO.write(robot.createScreenCapture(Rectangle(dialog.locationOnScreen,dialog.size)),"png",reports.resolve("session5-${mode.name.lowercase()}-settings.png").toFile())
    }
    setText("GameWidth","0")
    saveSettings()
    check(settingsWindow()!=null && state.opened!!.document.game.width==1280) { "Invalid settings saved: dialog=${settingsWindow()!=null} document=${state.opened!!.document}" }
    setText("ProjectName","Project Demo");setText("GameWidth","960");saveSettings()
    await("Project settings failed: ${state.error}") { !state.busy && settingsWindow()==null }
    check(ProjectFiles.open(created.file).document.name=="Project Demo") { "Saved settings: ${ProjectFiles.open(created.file).document}; bounds=$fields" }
    check(state.opened!!.document.game.width==960)
    check(scene.name=="Settings must keep this draft" && scene.dirty) { "Settings refresh replaced the scene" }
    scene.newScene();scene.rename("Remembered Scene");scene.addEntity();scene.selected!!.rename("Saved Actor")
    val remembered=created.assets.resolve("Scenes/Remembered.scene.json")
    documents()!!.savePath(ProjectDocumentKind.Scene,remembered)
    await("Scene did not save") { !state.busy && !scene.dirty && scene.file==remembered }
    check(ProjectFiles.editorState(created).lastScene=="Assets/Scenes/Remembered.scene.json")
    menu("View","UI Scale","75%")
    menu("File","Close Project")
    await("Project did not close") { !state.busy && state.opened==null }
    check(ProjectFiles.editorState(created).uiScale==0.75f)
    val moved=parent.resolve("Moved Game")
    Files.move(created.root,moved)
    val movedFile=moved.resolve(created.file.fileName)
    commands.openPath(movedFile)
    await("Moved project did not reopen") { !state.busy && state.opened?.document?.name=="Project Demo" }
    val active=state.opened!!
    check(scene.name=="Remembered Scene" && scene.entities.single().document.name=="Saved Actor") { "Reopen lost saved scene content" }
    menu("File","Close Project")
    await("Close before recent open failed") { !state.busy && state.opened==null }
    await("Project not listed in recents") { movedFile.toAbsolutePath().normalize() in commands.recent() }
    menu("File","Open Recent",movedFile.toAbsolutePath().normalize().toString())
    await("Recent project failed to restore scene") { !state.busy && scene.name=="Remembered Scene" }
    // Hold recovery IO while a new draft is created; the late result must not replace it.
    val locked=kotlinx.coroutines.CompletableDeferred<Unit>()
    val release=java.util.concurrent.CountDownLatch(1)
    val blocker=kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
        synchronized(sle.editor.project.assets.AssetIdentity) { locked.complete(Unit);check(release.await(10,java.util.concurrent.TimeUnit.SECONDS)) }
    }
    locked.await()
    try {
        val activation=state.activationRevision
        commands.openPath(movedFile)
        await("Recovery activation did not start") { state.activationRevision!=activation && state.busy }
        scene.rename("Keep recovery draft")
    } finally { release.countDown();blocker.join() }
    await("Recovery did not finish") { !state.busy }
    check(scene.name=="Keep recovery draft") { "Late recovery discarded a new draft" }
    scene.newScene()
    val malformed=parent.resolve("bad.sleproject")
    Files.writeString(malformed,"{bad")
    commands.openPath(malformed)
    await("Rejected open did not finish") { !state.busy }
    check(state.opened==active && state.error!=null)
    state.error=null
    // An asynchronous open must not discard edits typed after its first confirmation.
    commands.openPath(movedFile)
    scene.addEntity();scene.selected!!.rename("Keep this new edit")
    val dismiss=Timer(150,null)
    fun findPane(component: Component): JOptionPane? {
        if(component is JOptionPane) return component
        return (component as? Container)?.components?.firstNotNullOfOrNull(::findPane)
    }
    dismiss.addActionListener {
        Window.getWindows().filter { it.isShowing }.firstNotNullOfOrNull(::findPane)?.let {
            it.value=JOptionPane.NO_OPTION;dismiss.stop()
        }
    }
    dismiss.start()
    await("Project switch did not finish") { !state.busy }
    dismiss.stop()
    check(state.opened==active && scene.selected?.document?.name=="Keep this new edit") { "Project open discarded edits made while loading" }
    scene.newScene()
    menu("File","Project Settings…")
    await("Settings did not reopen") { settingsWindow()!=null }
    ImageIO.write(robot.createScreenCapture(Rectangle(window.locationOnScreen,window.size)),"png",reports.resolve("project-foundation-editor.png").toFile())
    settingsWindow()!!.dispatchEvent(java.awt.event.WindowEvent(settingsWindow(),java.awt.event.WindowEvent.WINDOW_CLOSING))
    println("PROJECT_FOUNDATION_SMOKE_PASS create-main/settings-preserve-scene/save/recover/recent/stale-recovery/invalid-values/local-state/close/relocate/reopen/failed-open/dirty-switch file=$movedFile")
}
