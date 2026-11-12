package sle.editor.smoke

import kotlinx.coroutines.delay
import kotlinx.serialization.json.*
import sle.editor.model.SceneWorkspaceState
import sle.editor.model.ScenePlaybackState
import sle.editor.viewport.EngineCanvas
import sle.editor.viewport.Playback
import java.awt.Rectangle
import java.awt.Robot
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO
import kotlin.math.abs
import kotlin.math.roundToInt

internal suspend fun runCameraPlaySmoke(canvas: EngineCanvas,scene: SceneWorkspaceState,playback: ScenePlaybackState,
    switchView: suspend (Boolean)->Unit = { playback.gameView=it }, start: ()->Unit) {
    val robot=Robot()
    suspend fun await(message: String,condition: ()->Boolean) {
        repeat(200) { if(condition()) { delay(80);return };delay(25) }
        error(message)
    }
    suspend fun frames() { delay(200) }
    fun image()=robot.createScreenCapture(Rectangle(canvas.locationOnScreen,canvas.size))
    fun centerImage(): java.awt.image.BufferedImage {
        val viewport=canvas.gameViewport()
        val origin=canvas.locationOnScreen
        val x=origin.x+canvas.width/2
        val y=origin.y+canvas.height/2
        check(viewport[2]>0 && viewport[3]>0)
        return robot.createScreenCapture(Rectangle(x-8,y-8,16,16))
    }
    fun textured(image: java.awt.image.BufferedImage): Int {
        var count=0
        for(y in 0 until image.height) for(x in 0 until image.width) {
            val p=image.getRGB(x,y);val r=(p shr 16) and 255;val g=(p shr 8) and 255;val b=p and 255
            if(r>110 && g>90 && b>60 && r>g+3 && g>b+3) ++count
        }
        return count
    }
    await("Initial camera scene not ready") { canvas.authoringReady() }
    val authored=scene.serialize()
    val player=scene.entities.first { it.document.name=="Player" }
    val body=scene.entities.first { it.document.name=="Crate B" }
    val camera=scene.entities.first { "Camera2D" in it.document.components }
    check(scene.parents[camera.document.key]==player.document.key)
    val original=canvas.runtimeTransforms()
    // Editor navigation changes no component data, and it remains independent of Game view.
    val editor=canvas.previewCamera()!!
    val origin=canvas.locationOnScreen
    robot.mouseMove(origin.x+canvas.width/2,origin.y+canvas.height/2)
    robot.mousePress(InputEvent.BUTTON2_DOWN_MASK);delay(60)
    robot.mouseMove(origin.x+canvas.width/2+35,origin.y+canvas.height/2+20);delay(80)
    robot.mouseRelease(InputEvent.BUTTON2_DOWN_MASK);frames()
    check(canvas.previewCamera()!!.origin!=editor.origin && scene.serialize()==authored)
    start()
    await("Play did not start") { canvas.scenePlayback()==Playback.Running && canvas.gameViewActive() }
    delay(850) // Audio initialization is deferred until after the first presented frame.
    check(textured(centerImage())>25) { "Active child camera is not centered on the player" }
    val viewport=canvas.gameViewport()
    check(abs(viewport[2]/viewport[3]-480f/270f)<0.02f) { "Game view did not preserve authored camera aspect" }
    robot.mouseMove(origin.x+canvas.width/2,origin.y+canvas.height/2)
    robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);delay(60)
    val xBefore=canvas.runtimeTransforms().getValue(player.document.key).jsonObject.getValue("position").jsonObject.getValue("x").jsonPrimitive.double
    robot.keyPress(KeyEvent.VK_D);delay(330);robot.keyRelease(KeyEvent.VK_D);frames()
    val running=canvas.runtimeTransforms()
    val playerPosition=running.getValue(player.document.key).jsonObject.getValue("position").jsonObject
    check(playerPosition.getValue("x").jsonPrimitive.double>xBefore+10) { "Focused Game input did not run the Lua script" }
    check(textured(centerImage())>25) { "Child camera did not follow its parent during gameplay" }
    val bodyY=running.getValue(body.document.key).jsonObject.getValue("position").jsonObject.getValue("y").jsonPrimitive.double
    check(bodyY<original.getValue(body.document.key).jsonObject.getValue("position").jsonObject.getValue("y").jsonPrimitive.double-0.1) { "Physics did not run" }
    check(scene.serialize()==authored && !scene.dirty) { "Runtime changed authored scene data" }
    playback.pause()
    await("Pause not applied") { canvas.scenePlayback()==Playback.Paused }
    val paused=canvas.runtimeTransforms()
    val first=image();delay(280);val second=image()
    check(paused==canvas.runtimeTransforms() && first.getRGB(0,0,first.width,first.height,null,0,first.width).contentEquals(second.getRGB(0,0,second.width,second.height,null,0,second.width))) { "Paused scene did not freeze" }
    switchView(false)
    await("Scene view did not activate") { !canvas.gameViewActive() }
    canvas.zoomEntity(0.8f);frames()
    switchView(true)
    await("Game view did not reactivate") { canvas.gameViewActive() }
    check(canvas.runtimeTransforms()==paused && scene.serialize()==authored)
    val reports=Path.of(System.getProperty("sle.smokeReport")).parent
    ImageIO.write(image(),"png",reports.resolve("game-camera.png").toFile())
    playback.play(scene)
    await("Resume not applied") { canvas.scenePlayback()==Playback.Running }
    robot.mouseMove(origin.x+canvas.width/2,origin.y+canvas.height/2)
    robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);delay(60)
    robot.keyPress(KeyEvent.VK_D);delay(180);robot.keyRelease(KeyEvent.VK_D);frames()
    check(canvas.runtimeTransforms()!=paused) { "Resumed gameplay did not accept script input" }
    playback.stop()
    await("Stop did not restore authoring") { canvas.scenePlayback()==Playback.Stopped && canvas.authoringReady() }
    check(canvas.runtimeTransforms()==original && scene.serialize()==authored && !scene.dirty) { "Stop did not restore authoring snapshot" }
    val saved=reports.resolve("camera-hierarchy.slescene")
    scene.save(saved);scene.newScene();scene.open(saved)
    await("Reloaded hierarchy not ready") { canvas.authoringReady() }
    check(scene.parents[camera.document.key]==player.document.key)
    val example=Path.of("assets/scenes/editor_camera_demo.slescene")
    if(!Files.exists(example)) Files.copy(saved,example)
    println("CAMERA_SCENE_PLAY_PASS hierarchy/pan/game-camera/follow/input/scripts/physics/pause/resume/stop/save/open")
}
