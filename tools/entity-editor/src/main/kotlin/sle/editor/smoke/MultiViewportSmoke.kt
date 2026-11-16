package sle.editor.smoke

import sle.editor.viewport.*
import kotlinx.serialization.json.*
import sle.editor.model.SceneWorkspaceState
import sle.editor.model.readComponentCatalog
import sle.editor.files.createCameraScene
import java.awt.EventQueue
import java.nio.file.Path
import javax.swing.JFrame
import javax.swing.Timer
import javax.imageio.ImageIO
import kotlin.system.exitProcess

/** Real desktop GL lifecycle regression; no second Runtime or synthetic painted stripes. */
internal fun runMultiViewportSmoke(ownerFailure: Boolean = false) {
    EventQueue.invokeLater {
        var engine: EditorEngineSession?=null
        var sceneHost: JFrame?=null
        var gameHost: JFrame?=null
        fun fail(failure: Throwable) {
            failure.printStackTrace()
            try { engine?.close();sceneHost?.dispose();gameHost?.dispose() } catch(cleanup: Throwable) { failure.addSuppressed(cleanup) }
            exitProcess(1)
        }
        try {
            val reports=Path.of(System.getProperty("sle.smokeReport")).parent.toFile().apply { mkdirs() }
            val catalog=readComponentCatalog()
            val scene=SceneWorkspaceState(catalog)
            createCameraScene(scene,catalog,reports.toPath())
            scene.addEntity();scene.selected!!.rename("Tick Counter")
            val counter=scene.selected!!
            counter.addComponent("Script")
            val script=counter.importAsset(Path.of("tests/data/scripts/embedded_multiview.lua"))
            counter.setValue("Script",listOf("scriptAsset"),JsonPrimitive(script.toString().replace('\\','/')))
            val authored=scene.serialize()
            val session=EditorEngineSession(false,onFailure=::fail)
            engine=session
            session.start()
            session.owner.setScene(scene);session.owner.setAuthoring(scene.selected)
            session.owner.setContent(ViewportMode.Scene,authored,Playback.Stopped)
            val sceneView=EngineCanvas(session,ViewId.Scene)
            var gameView=EngineCanvas(session,ViewId.Game)
            fun host(title: String,view: EngineCanvas,x: Int,w: Int,h: Int) = JFrame(title).apply {
                defaultCloseOperation=JFrame.DISPOSE_ON_CLOSE
                add(view);setBounds(x,100,w,h);isVisible=true
            }
            sceneHost=host("SLE Scene",sceneView,30,700,480)
            gameHost=host("SLE Game",gameView,750,620,420)
            var phase=-3
            var fitted: Vec2?=null
            var start=System.nanoTime()
            val opened=start
            var identity=0L
            var creates=0
            var before=""
            var paused=""
            var hiddenSequence=0L
            var startSequence=0L
            fun updates()=session.owner.runtimeTransforms().getValue(counter.document.key).jsonObject.getValue("position").jsonObject.getValue("x").jsonPrimitive.double / 10
            fun elapsed()=(System.nanoTime()-start)/1e9
            fun next() { phase++;start=System.nanoTime() }
            fun transforms()=session.owner.runtimeTransforms().toString()
            val timer=Timer(100,null)
            timer.addActionListener {
                try {
                    check((System.nanoTime()-opened)/1e9<40) { "Multi viewport phase $phase timed out" }
                    when(phase) {
                        -3 -> if(session.latestFrame(ViewId.Scene)!=null) {
                            fitted=sceneView.previewCamera()!!.size
                            sceneView.zoomEntity(.5f);next()
                        }
                        -2 -> if(elapsed()>.3) {
                            check(sceneView.previewCamera()!!.size!=fitted) { "Scene zoom ignored" }
                            sceneView.fitEntity();next()
                        }
                        -1 -> if(elapsed()>.3) {
                            check(sceneView.previewCamera()!!.size==fitted) { "Scene Fit ignored by draw-only API" }
                            next()
                        }
                        0 -> if(session.latestFrame(ViewId.Scene)!=null && session.latestFrame(ViewId.Game)!=null) {
                            if(ownerFailure) {
                                session.onFailure={ failure ->
                                    check(failure is IllegalArgumentException) { "Unexpected owner failure: $failure" }
                                    check(session.owner.contextIdentity()==0L && session.owner.creates==session.owner.destroys)
                                    check(session.latestFrame(ViewId.Scene)==null && session.latestFrame(ViewId.Game)==null)
                                    timer.stop();sceneHost!!.dispose();gameHost!!.dispose()
                                    println("OWNER_FAILURE_SMOKE_PASS callback/stop/cleanup")
                                    exitProcess(0)
                                }
                                session.setContent(ViewportMode.Scene,"{invalid",Playback.Stopped)
                                next();return@addActionListener
                            }
                            identity=session.owner.contextIdentity();creates=session.owner.creates
                            before=transforms()
                            startSequence=session.latestFrame(ViewId.Game)!!.sequence
                            session.owner.setContent(ViewportMode.Scene,authored,Playback.Running)
                            next()
                        }
                        1 -> if(elapsed()>1.5) {
                            check(transforms()!=before) { "Simulation/physics did not advance" }
                            check(session.owner.scenePlayback()==Playback.Running)
                            val a=session.latestFrame(ViewId.Scene)!!;val b=session.latestFrame(ViewId.Game)!!
                            check(a.sequence==b.sequence && a.texture!=b.texture)
                            check(updates()==(b.sequence-startSequence).toDouble()) { "Simulation advanced more than once per owner tick" }
                            check(a.request.pixelWidth!=b.request.pixelWidth)
                            check(b.camera?.size==Vec2(480.0,270.0)) { "Game frame published editor-camera metrics" }
                            check(a.camera!=b.camera) { "Scene and Game cameras unexpectedly identical" }
                            sceneView.captureFile=reports.resolve("multi-scene.png")
                            gameView.captureFile=reports.resolve("multi-game.png")
                            session.owner.setContent(ViewportMode.Scene,authored,Playback.Paused)
                            next()
                        }
                        2 -> if(elapsed()>.5) { paused=transforms();next() }
                        3 -> if(elapsed()>.5) {
                            check(transforms()==paused) { "Paused multiple draws advanced simulation" }
                            session.owner.setContent(ViewportMode.Scene,authored,Playback.Running)
                            sceneHost!!.dispose();next()
                        }
                        4 -> if(elapsed()>.5) {
                            check(transforms()!=paused && session.owner.creates==creates)
                            sceneHost=host("SLE Scene reopened",sceneView,30,700,480)
                            next()
                        }
                        5 -> if(elapsed()>.5) {
                            gameHost!!.dispose()
                            gameView=EngineCanvas(session,ViewId.Game)
                            gameHost=host("SLE Game recreated",gameView,750,540,360)
                            next()
                        }
                        6 -> if(elapsed()>.5) {
                            check(session.owner.contextIdentity()==identity && session.owner.creates==creates)
                            check(session.latestFrame(ViewId.Scene)!=null && session.latestFrame(ViewId.Game)!=null)
                            sceneView.captureFile=reports.resolve("multi-scene-reopened.png")
                            gameView.captureFile=reports.resolve("multi-game-recreated.png")
                            before=transforms()
                            sceneHost!!.isVisible=false;gameHost!!.isVisible=false
                            hiddenSequence=session.latestFrame(ViewId.Game)!!.sequence
                            next()
                        }
                        7 -> if(elapsed()>.7) {
                            check(transforms()!=before) { "Hidden views stopped simulation" }
                            check(session.latestFrame(ViewId.Scene)==null && session.latestFrame(ViewId.Game)==null)
                            sceneHost!!.isVisible=true;gameHost!!.isVisible=true
                            next()
                        }
                        8 -> if(elapsed()>.5) {
                            check(session.latestFrame(ViewId.Game)!!.sequence>hiddenSequence+1)
                            // Resize and input in one EDT callback, before another owner tick.
                            gameView.setSize(gameView.width+40,gameView.height)
                            gameView.dispatchEvent(java.awt.event.MouseEvent(gameView,java.awt.event.MouseEvent.MOUSE_PRESSED,
                                System.currentTimeMillis(),0,20,20,1,false,java.awt.event.MouseEvent.BUTTON1))
                            check(session.latestFrame(ViewId.Game)==null) { "Resize input used a stale Game frame" }
                            // Capture real scene pixels after peer recreation too.
                            sceneView.captureFile=reports.resolve("multi-scene-reopened.png")
                            gameView.captureFile=reports.resolve("multi-game-recreated.png")
                            next()
                        }
                        9 -> if(elapsed()>.5) {
                            for(name in listOf("multi-scene.png","multi-game.png","multi-scene-reopened.png","multi-game-recreated.png")) {
                                val image=ImageIO.read(reports.resolve(name)) ?: error("Missing $name")
                                var colored=0
                                for(y in 0 until image.height) for(x in 0 until image.width) {
                                    val p=image.getRGB(x,y);val r=(p shr 16) and 255;val g=(p shr 8) and 255;val b=p and 255
                                    if(r>110 && g>90 && b>60 && r>g+3 && g>b+3) colored++
                                }
                                check(colored>25) { "$name has no actual textured scene pixels: $colored" }
                            }
                            check(scene.serialize()==authored)
                            check(session.owner.creates==creates && session.owner.destroys==0)
                            session.setContent(ViewportMode.Scene,authored,Playback.Stopped)
                            next()
                        }
                        10 -> if(elapsed()>.3) {
                            check(session.owner.scenePlayback()==Playback.Stopped && session.owner.authoringReady())
                            check(session.owner.contextIdentity()==identity && updates()==0.0)
                            check(session.owner.creates==creates+1 && session.owner.destroys==creates)
                            timer.stop();session.close();session.close()
                            check(session.owner.destroys==session.owner.creates)
                            sceneHost!!.dispose();gameHost!!.dispose()
                            reports.resolve("multi-viewport.txt").writeText("PASS owner=$identity playCreates=$creates finalCreates=${session.owner.creates} finalDestroys=${session.owner.destroys}; simultaneous distinct views; pause/resume/Stop; Scene peer reopen; Game recreation; zero visible views advance; resize-input invalidation; captures verified; explicit Stop native reset=1\n")
                            println("MULTI_VIEWPORT_SMOKE_PASS owner=$identity playCreates=$creates finalCreates=${session.owner.creates} finalDestroys=${session.owner.destroys} explicitStopReset=1")
                            exitProcess(0)
                        }
                    }
                } catch(failure: Throwable) { timer.stop();fail(failure) }
            }
            timer.start()
        } catch(failure: Throwable) { fail(failure) }
    }
}
