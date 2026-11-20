package sle.editor.smoke

import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import sle.editor.command.CommandContext
import sle.editor.command.GamePanel
import sle.editor.command.ShortcutDispatcher
import sle.editor.command.standardKeymap
import sle.editor.model.ScenePlaybackState
import sle.editor.model.SceneWorkspaceState
import sle.editor.model.readComponentCatalog
import sle.editor.project.KeyboardBinding
import sle.editor.project.ProjectSettings
import sle.editor.viewport.EngineCanvas
import sle.editor.viewport.EditorEngineSession
import sle.editor.viewport.Playback
import sle.editor.viewport.ViewId
import sle.editor.viewport.ViewportMode
import java.awt.EventQueue
import java.awt.KeyboardFocusManager
import java.awt.Robot
import java.awt.event.KeyEvent
import java.nio.file.Files
import javax.swing.JFrame
import javax.swing.Timer
import kotlin.system.exitProcess

/** Runs a bound editor Play shortcut through the real focused canvas, JNI and Lua. */
internal fun runInputActionsSmoke() = EventQueue.invokeLater {
    val root = Files.createTempDirectory("sle-input-actions-")
    val timer = Timer(40, null)
    val focus = KeyboardFocusManager.getCurrentKeyboardFocusManager()
    val window = JFrame("SLE Input Actions")
    var engine: EditorEngineSession? = null
    var dispatcher: ShortcutDispatcher? = null
    var releaseKey: (() -> Unit)? = null
    fun finish(failure: Throwable?) {
        timer.stop()
        releaseKey?.invoke()
        dispatcher?.let(focus::removeKeyEventDispatcher)
        window.dispose()
        engine?.close()
        root.toFile().deleteRecursively()
        if (failure != null) failure.printStackTrace()
        else println("INPUT_ACTIONS_SMOKE_PASS focused F6 reached Lua without pausing Play")
        exitProcess(if (failure == null) 0 else 1)
    }
    try {
        val scene = SceneWorkspaceState(readComponentCatalog())
        scene.addEntity()
        val actor = checkNotNull(scene.selected)
        val actorKey = actor.document.key
        val script = root.resolve("actions.lua")
        Files.writeString(script, """
            return { update = function()
                local actor = Engine.findEntityByKey('$actorKey')
                if Engine.Input.isActionPressed('Jump') then Engine.setTransformPosition(actor, 10, 0) end
                if Engine.Input.isActionReleased('Jump') then Engine.setTransformPosition(actor, 20, 0) end
            end }
        """.trimIndent())
        actor.addComponent("Script")
        actor.setValue("Script", listOf("scriptAsset"), JsonPrimitive(script.toString().replace('\\', '/')))
        val playback = ScenePlaybackState().apply { play(scene) }
        val session = EditorEngineSession(false, onFailure = ::finish)
        engine = session
        session.owner.setEditorAssetRoot(root, ProjectSettings(
            inputActions = mapOf("Jump" to listOf(KeyboardBinding(295))),
        ))
        val canvas = EngineCanvas(session, ViewId.Game)
        val shortcuts = ShortcutDispatcher({ standardKeymap }) { event ->
            if (event.component !== canvas || !canvas.isFocusOwner) null
            else CommandContext(scene = scene, playback = playback, gameRunning = true, focusedPanel = GamePanel,
                gameInputFocused = true, gameActionKeys = session.owner.activeActionKeys)
        }
        dispatcher = shortcuts
        focus.addKeyEventDispatcher(shortcuts)
        canvas.setSceneView(true) { finish(IllegalStateException(it)) }
        canvas.setContent(ViewportMode.Scene, playback.source, Playback.Running)
        session.start()
        window.apply {
            isAlwaysOnTop = true
            add(canvas)
            setSize(520, 360)
            setLocationRelativeTo(null)
            isVisible = true
        }
        val robot = Robot()
        val started = System.nanoTime()
        var phase = 0
        fun x() = canvas.runtimeTransforms().getValue(actorKey).jsonObject
            .getValue("position").jsonObject.getValue("x").jsonPrimitive.double
        timer.addActionListener {
            try {
                check(System.nanoTime() - started < 15_000_000_000L) { "Input actions smoke timed out in phase $phase" }
                check(playback.playback == Playback.Running) { "Bound F6 triggered the editor Pause shortcut" }
                when (phase) {
                    0 -> if (canvas.gameViewActive() && canvas.scenePlayback() == Playback.Running) {
                        canvas.requestFocusInWindow()
                        if (canvas.isFocusOwner) {
                            robot.keyPress(KeyEvent.VK_F6)
                            releaseKey = { robot.keyRelease(KeyEvent.VK_F6) }
                            phase++
                        }
                    }
                    1 -> if (x() == 10.0) {
                        releaseKey?.invoke()
                        releaseKey = null
                        phase++
                    }
                    2 -> if (x() == 20.0) finish(null)
                }
            } catch (failure: Throwable) {
                finish(failure)
            }
        }
        timer.start()
    } catch (failure: Throwable) {
        finish(failure)
    }
}
