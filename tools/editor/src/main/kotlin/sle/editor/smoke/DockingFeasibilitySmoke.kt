package sle.editor.smoke

import sle.editor.viewport.experimental.FeasibilityOwner
import sle.editor.viewport.experimental.FeasibilityGpuPresenter
import java.awt.*
import java.awt.event.*
import java.io.File
import java.lang.management.ManagementFactory
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.imageio.ImageIO
import javax.swing.*
import kotlin.system.exitProcess

/** Bounded real-desktop experiment; never entered during ordinary editor startup. */
internal fun runDockingFeasibilitySmoke(gpu: Boolean = false) {
    val report = File(System.getProperty("sle.smokeReport")).resolveSibling("docking-feasibility-${if (gpu) "gpu" else "cpu"}.txt")
    report.parentFile.mkdirs()
    val log = mutableListOf<String>()
    val copy = mutableListOf<Long>()
    val paint = mutableListOf<Long>()
    val latency = mutableListOf<Long>()
    val heap = mutableListOf<Long>()
    val events = mutableListOf<String>()
    val probe = Executors.newSingleThreadScheduledExecutor()
    EventQueue.invokeLater {
        val owner = FeasibilityOwner(gpu)
        // pack creates an HWND/HDC without showing a window. Presenters never contain this canvas.
        val ownerHost = JFrame("SLE hidden GL owner").apply {
            isUndecorated = true
            add(owner)
            owner.preferredSize = Dimension(16, 16)
            pack()
        }
        val windows = arrayOfNulls<JFrame>(2)
        val panels = arrayOfNulls<Component>(2)
        var measuring = false
        var ended = false
        var timer: Timer? = null
        var started = 0L
        var benchmarkTicks = 0
        var zeroViewStart = 0
        var inputEvents = 0
        var initialIdentity = 0L
        val stages = mutableSetOf<Int>()
        fun note(text: String) { events += text; println("DOCKING_FEASIBILITY: $text") }
        fun presenter(view: Int) {
            check(EventQueue.isDispatchThread())
            val panel = (if (gpu) FeasibilityGpuPresenter(owner, view, { if (measuring) paint += it }, ::note) else object : JPanel() {
                override fun paintComponent(g: Graphics) {
                    super.paintComponent(g)
                    val image = owner.frames[view].latest ?: return
                    val begin = System.nanoTime()
                    g.drawImage(image, 0, 0, width, height, null)
                    if (measuring) paint += System.nanoTime() - begin
                }
            }).apply { preferredSize = Dimension(1280, 720); isFocusable = true }
            panel.addMouseListener(object : MouseAdapter() {
                override fun mousePressed(e: MouseEvent) { inputEvents++; panel.requestFocusInWindow() }
            })
            val window = JFrame(if (view == 0) "SLE Scene — feasibility" else "SLE Game — feasibility").apply {
                type = Window.Type.NORMAL
                defaultCloseOperation = WindowConstants.DO_NOTHING_ON_CLOSE
                add(panel)
                addWindowListener(object : WindowAdapter() {
                    override fun windowClosing(e: WindowEvent) {
                        owner.request(view, 0, 0)
                        dispose()
                        windows[view] = null; panels[view] = null
                        note("closed presenter $view; owner=${owner.identity}, ticks=${owner.ticks}")
                    }
                })
                pack()
                setLocation(30 + view * 180, 40 + view * 100)
                isVisible = true
                check(jMenuBar == null && !isAlwaysOnTop && isResizable && this.owner == null)
            }
            windows[view] = window; panels[view] = panel
            owner.request(view, 1280, 720)
            note("created presenter $view type=${window.type}, menu=null, alwaysOnTop=false")
        }
        fun close(view: Int) { windows[view]?.dispatchEvent(WindowEvent(windows[view], WindowEvent.WINDOW_CLOSING)) }
        fun stats(name: String, values: List<Long>) {
            check(values.isNotEmpty()) { "No $name samples" }
            val sorted = values.sorted()
            log += "$name samples=${values.size} mean_ms=${values.average()/1e6} p95_ms=${sorted[((sorted.size-1)*0.95).toInt()]/1e6} max_ms=${sorted.last()/1e6}"
        }
        fun finish(failure: Throwable? = null) {
            if (ended) return
            ended = true; measuring = false; timer?.stop(); probe.shutdownNow()
            var problem = failure
            try {
                windows.forEach { it?.dispose() }
                owner.shutdown()
                check(owner.creates == 1 && owner.destroys == 1 && owner.sessionToken == 0L)
                check(owner.frames.all { it.latest == null } && owner.allocatedBytes() == 0L && owner.ownerTargetCount() == 0)
                note("owner-context shutdown; creates=${owner.creates}, destroys=${owner.destroys}; CPU buffers=0; owner GL targets=0")
            } catch (cleanup: Throwable) {
                if (problem == null) problem = cleanup else problem.addSuppressed(cleanup)
            } finally {
                windows.forEach { it?.dispose() }; ownerHost.dispose()
            }
            log += "hardware=${owner.hardware}"
            log += "os=${System.getProperty("os.name")} ${System.getProperty("os.version")}; JVM=${System.getProperty("java.version")}; CPUs=${Runtime.getRuntime().availableProcessors()}"
            log += "owner_context=$initialIdentity; owner_ticks=${owner.ticks}; benchmark_ticks=$benchmarkTicks; requested_hz=60; measurement_seconds=30; warmup_seconds=5; UI=85% font; logical_views=1280x720 each"
            log += "heap_used_bytes_samples=$heap"
            if (copy.isNotEmpty()) stats("two_view_copy_publication", copy)
            if (paint.isNotEmpty()) stats("individual_presenter_${if (gpu) "GPU_blit_swap_finish" else "drawImage"}", paint)
            if (latency.isNotEmpty()) stats("EDT_probe_delay", latency)
            log += events
            if (problem == null) {
                val p95 = copy.sorted()[((copy.size-1)*0.95).toInt()] / 1e6
                log += "${if (gpu) "GPU" else "CPU"}_TRANSPORT_TARGET=${if (p95 <= 8.0) "PASS" else "MISS"} p95_ms=$p95"
                log += "LIFECYCLE=PASS; taskbar/other-app stacking and physical input require manual observation"
            } else log += "FAILED=${problem.stackTraceToString()}"
            report.writeText(log.joinToString("\n") + "\n")
            println(log.joinToString("\n"))
            exitProcess(if (problem == null) 0 else 1)
        }
        try {
            presenter(0); presenter(1)
            owner.render()
            initialIdentity = owner.identity
            check(initialIdentity != 0L && !owner.isShowing && !ownerHost.isVisible)
            note("hidden packed owner context=$initialIdentity; session=${owner.sessionToken}; thread=${Thread.currentThread().name}")
            windows.forEachIndexed { i, w ->
                val transform = w!!.graphicsConfiguration.defaultTransform
                log += "view=$i desktop_scale=${transform.scaleX}x${transform.scaleY}; pixels=${(1280*transform.scaleX).toInt()}x${(720*transform.scaleY).toInt()}; font_scale=0.85"
                panels[i]!!.font = panels[i]!!.font.deriveFont(panels[i]!!.font.size2D * 0.85f)
            }
            started = System.nanoTime()
            var next = started
            probe.scheduleAtFixedRate({
                val posted = System.nanoTime()
                EventQueue.invokeLater { if (measuring) latency += System.nanoTime() - posted }
            }, 0, 50, TimeUnit.MILLISECONDS)
            timer = Timer(1) {
                try {
                    val now = System.nanoTime()
                    if (now >= next) {
                        next += 16_666_667L
                        if (next < now) next = now + 16_666_667L // drop debt; no catch-up spiral
                        val seconds = (now - started) / 1e9
                        measuring = seconds >= 5 && seconds < 35
                        owner.render()
                        check(owner.identity == initialIdentity && owner.creates == 1 && owner.destroys == 0)
                        if (measuring) {
                            panels.forEach { panel ->
                                check(panel!!.width == 1280 && panel.height == 720) { "Invalid benchmark content size: ${panel.width}x${panel.height}" }
                                if (panel is FeasibilityGpuPresenter) check(panel.framebufferWidth == 1280 && panel.framebufferHeight == 720)
                            }
                            copy += owner.copyNanos; benchmarkTicks++
                            if (stages.add(seconds.toInt())) heap += ManagementFactory.getMemoryMXBean().heapMemoryUsage.used
                            check(owner.allocatedBytes() == if (gpu) 0L else 1280L * 720 * 12 * 2)
                            check(owner.ownerTargetCount() == 3)
                        }
                        panels.forEach { panel ->
                            if (panel is FeasibilityGpuPresenter && panel.isShowing) panel.render()
                            else panel?.repaint()
                        }
                        fun stage(id: Int, action: () -> Unit) { if (seconds >= id && stages.add(id)) action() }
                        stage(2) {
                            windows[1]!!.toFront()
                            val point = panels[1]!!.locationOnScreen
                            Robot().apply { autoDelay = 60; mouseMove(point.x + 80, point.y + 80); mousePress(InputEvent.BUTTON1_DOWN_MASK); mouseRelease(InputEvent.BUTTON1_DOWN_MASK) }
                        }
                        stage(3) {
                            note("OS input observation: mouseEvents=$inputEvents canvasFocus=${panels[1]!!.isFocusOwner} windowFocus=${windows[1]!!.isFocused}; Robot delivery is inconclusive if events=0")
                        }
                        stage(35) {
                            measuring = false
                            if (gpu) {
                                panels.forEachIndexed { i, panel ->
                                    (panel as FeasibilityGpuPresenter).captureFile = report.resolveSibling("docking-gpu-view-$i.png")
                                    panel.render()
                                }
                            } else {
                                panels.forEachIndexed { i, panel ->
                                    val image = owner.frames[i].latest!!
                                    ImageIO.write(image, "png", report.resolveSibling("docking-cpu-view-$i.png"))
                                    check(panel != null && image.width == 1280 && image.height == 720)
                                }
                                check(owner.frames[0].latest!!.getRGB(5, 5) != owner.frames[1].latest!!.getRGB(5, 5))
                            }
                            note("30-second measurement complete; bounded CPU storage=${owner.allocatedBytes()}")
                        }
                        stage(36) { close(0) }
                        stage(37) { presenter(0) }
                        stage(38) { close(1) }
                        stage(39) { presenter(1) }
                        stage(40) {
                            // Dispose/recreate an existing visible presenter's native peer.
                            val window = windows[0]!!
                            owner.request(0, 0, 0); window.dispose()
                            check(!window.isDisplayable)
                            window.isVisible = true; owner.request(0, 1280, 720)
                            note("presenter 0 peer recreated; owner=$initialIdentity")
                        }
                        stage(41) {
                            owner.request(0, 0, 0)
                            panels[0]!!.setSize(0, 0)
                            check(owner.frames[0].latest == null)
                            note("zero-size view invalidated")
                        }
                        stage(42) {
                            windows[0]!!.setSize(680, 430); owner.request(0, 640, 360)
                            note("resized view 0 to 640x360 publication")
                        }
                        stage(43) {
                            check(if (gpu) owner.sharedFrames[0].latest!!.width == 640 else owner.frames[0].latest!!.width == 640)
                            windows.forEach { it!!.extendedState = Frame.ICONIFIED }
                            owner.request(0, 0, 0); owner.request(1, 0, 0)
                            zeroViewStart = owner.ticks
                            note("both minimized; no published images")
                        }
                        stage(45) {
                            check(owner.ticks - zeroViewStart >= 60)
                            check(windows.all { it!!.extendedState and Frame.ICONIFIED != 0 })
                            check(owner.frames.all { it.latest == null } && owner.sharedFrames.all { it.latest == null })
                            note("zero visible views: ${owner.ticks-zeroViewStart} owner ticks continued")
                            windows.forEach { it!!.extendedState = Frame.NORMAL }
                            owner.request(0, 1280, 720); owner.request(1, 1280, 720)
                        }
                        stage(46) { close(0); close(1); zeroViewStart = owner.ticks }
                        stage(48) {
                            check(owner.ticks - zeroViewStart >= 60)
                            note("both presenters disposed: ${owner.ticks-zeroViewStart} owner ticks continued")
                            finish()
                        }
                    }
                } catch (failure: Throwable) { finish(failure) }
            }.apply { isCoalesce = true; start() }
        } catch (failure: Throwable) { finish(failure) }
    }
}
