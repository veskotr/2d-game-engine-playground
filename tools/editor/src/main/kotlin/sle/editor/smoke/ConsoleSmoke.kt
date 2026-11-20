package sle.editor.smoke

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import sle.editor.bridge.NativeEngine
import sle.editor.console.ConsoleMessage
import sle.editor.console.editorConsoleSnapshot
import sle.editor.console.ConsoleSeverity
import sle.editor.model.ScenePlaybackState
import sle.editor.model.ScenePose
import sle.editor.model.SceneWorkspaceState
import sle.editor.model.readComponentCatalog
import sle.editor.project.ProjectFiles
import sle.editor.viewport.EditorEngineSession
import sle.editor.viewport.Playback
import sle.editor.viewport.ViewportMode
import sle.editor.workspace.PanelId
import sle.editor.workspace.WorkspaceWindowManager
import java.awt.EventQueue
import java.nio.file.Files
import java.nio.file.Path
import javax.swing.Timer
import kotlin.system.exitProcess

internal fun runConsoleSmoke() = EventQueue.invokeLater {
    val windows = WorkspaceWindowManager()
    val panel = PanelId("console")
    var engine: EditorEngineSession? = null
    val timer = Timer(50, null)
    var reloadDirectory: Path? = null
    fun finish(failure: Throwable?) {
        timer.stop()
        engine?.close()
        windows.close()
        reloadDirectory?.toFile()?.deleteRecursively()
        if (failure != null) failure.printStackTrace()
        else println("CONSOLE_SMOKE_PASS hidden/reopen/repeat/stop/replay/source/pause/step/live-inspection/keep-changes/reload-script/lua-profiler/stopped-lua/play-lua/project-editor-command/project-lua-ui")
        exitProcess(if (failure == null) 0 else 1)
    }
    try {
        val scene = SceneWorkspaceState(readComponentCatalog())
        scene.addEntity()
        val entity = scene.selected!!
        entity.addComponent("Script")
        val source = entity.importAsset(Path.of("tests/data/scripts/console callbacks.lua"))
        entity.setValue("Script", listOf("scriptAsset"), JsonPrimitive(source.toString().replace('\\', '/')))
        val captured = scene.document()
        val snapshot = editorConsoleSnapshot(captured, listOf(entity.document.key), scene.history.revision, 0L, null, null)
        val response = Json.parseToJsonElement(NativeEngine.runEditorLua(
            """
                print(snapshot.scene.entities[1].key)
                return {{op = "createEntity", entity = "console-generated", name = "Generated"}}
            """.trimIndent().encodeToByteArray(),
            snapshot.toString().encodeToByteArray()).decodeToString()).jsonObject
        check(response.getValue("error").jsonPrimitive.content.isEmpty())
        check(response.getValue("output").jsonPrimitive.content.trim() == entity.document.key)
        scene.applyLuaConsoleEdits(captured, response.getValue("edits").jsonArray)
        check(scene.entity("console-generated") != null)
        scene.undo()
        check(scene.document() == captured)
        val commandProject = java.nio.file.Files.createTempDirectory("sle-editor-command-smoke")
        try {
            val editor = java.nio.file.Files.createDirectory(commandProject.resolve("Editor"))
            java.nio.file.Files.writeString(editor.resolve("probe.editor.lua"), """
                return { commands = {{
                    id = "probe", label = "Probe command", menu = "Tools", shortcut = "Ctrl+F24",
                    enabled = function(s) return s.selection[1] ~= nil end,
                    run = function(s) return {
                        {op = "createEntity", entity = "script-generated", name = "Script generated"},
                        {op = "reparentEntity", entity = "script-generated", parent = s.selection[1]},
                    } end,
                }, {
                    id = "conflict", label = "Conflicting command", shortcut = "Ctrl+Z",
                    run = function() end,
                }},
                drawers = {{component = "SpriteRenderer", path = {"region", "uv"}, widget = "uv"}},
                panels = {
                    {id = "tools", label = "Tools", widgets = {{kind = "button", command = "probe"}}},
                    {id = "broken", label = "Broken", widgets = {{kind = "button", command = "missing"}}},
                }}
            """.trimIndent())
            val opened = sle.editor.project.OpenedProject(commandProject.resolve("Probe.sleproject"),
                sle.editor.project.ProjectDocument(name = "Probe"))
            val diagnostics = mutableListOf<String>()
            val declarations = sle.editor.scripts.loadProjectDeclarations(opened, diagnostics)
            val commands = declarations.commands
            val registered = commands.single()
            val button = declarations.panels.single().widgets.single() as sle.editor.scripts.ProjectPanelWidget.Button
            check(button.command === registered)
            val catalog = readComponentCatalog().getValue("SpriteRenderer")
            val uv = catalog.defaults.getValue("region").jsonObject.getValue("uv")
            val property = sle.editor.ui.inspector.propertyDefinition(
                "SpriteRenderer", listOf("region", "uv"), uv, catalog = catalog,
            )
            check(sle.editor.scripts.projectDrawer(property, declarations.drawers) ==
                sle.editor.ui.inspector.PropertyDrawer.Uv)
            check(registered.command.id.value == "project.lua.probe")
            check(diagnostics.size == 2)
            check(diagnostics.any { it.contains("Ctrl+Z") } && diagnostics.any { it.contains("missing") })
            val keymap = sle.editor.command.Keymap(sle.editor.command.CommandRegistry(
                sle.editor.command.standardCommands.commands + commands.map { it.command }))
            check(keymap.commandsFor(sle.editor.command.KeyChord.parse("Ctrl+F24")).single() === registered.command)
            val command = button.command.script.evaluate("run", snapshot.toString().encodeToByteArray())
            check(command.getValue("error").jsonPrimitive.content.isEmpty()) { command.toString() }
            scene.applyLuaConsoleEdits(captured, command.getValue("edits").jsonArray, opened)
            check(scene.document().parents["script-generated"] == entity.document.key)
            scene.undo()
            check(scene.document() == captured)
            scene.redo()
            check(scene.document().parents["script-generated"] == entity.document.key)
            scene.undo()
        } finally {
            commandProject.toFile().deleteRecursively()
        }
        val document = scene.serialize()
        val session = EditorEngineSession(true, onFailure = ::finish)
        engine = session
        session.setScene(scene)
        session.setContent(ViewportMode.Scene, document, Playback.Stopped)
        session.start()
        val owner = session.owner
        owner.luaProfiler.updateCapture(true)
        check(owner.console.clearOnPlay && !owner.console.pauseOnLuaError)
        val playback = ScenePlaybackState()
        owner.consumeSceneStep = playback::consumeStep
        var errorPauses = 0
        owner.onLuaErrorPause = {
            errorPauses++
            playback.pause()
        }
        windows.hidePanel(panel)
        check(panel in windows.layout.hidden)
        session.setContent(ViewportMode.Scene, document, Playback.Running)
        val start = System.nanoTime()
        var phase = 0
        var firstRun: Long? = null
        var reloadFile: Path? = null
        val beforeReplay = ConsoleMessage(ConsoleSeverity.INFO, "Previous Play marker")
        timer.addActionListener {
            try {
                check(System.nanoTime() - start < 30_000_000_000L) { "Console smoke timed out in phase $phase" }
                val row = owner.console.rows.lastOrNull { it.entry.message.contains("update failure") }
                when (phase) {
                    0 -> if (row != null && row.repeats > 1) {
                        check(owner.scenePlayback() == Playback.Running && errorPauses == 0)
                        check(panel in windows.layout.hidden)
                        windows.showPanel(panel)
                        check(panel !in windows.layout.hidden)
                        val lua = checkNotNull(row.entry.lua)
                        check(lua.path == source.toAbsolutePath().normalize() && Files.isRegularFile(lua.path))
                        check(lua.line == 6 && lua.traceback.isNotEmpty())
                        val sample = checkNotNull(owner.luaProfiler.samples.lastOrNull())
                        check(sample.memory > 0 && sample.elapsed > 0)
                        check(sample.rows.any { it.calls[1] == 1L && Path.of(it.source) == source.toAbsolutePath().normalize() })
                        firstRun = checkNotNull(owner.liveInspection).run
                        session.setContent(ViewportMode.Scene, document, Playback.Stopped)
                        phase++
                    }
                    1 -> if (owner.scenePlayback() == Playback.Stopped) {
                        check(row != null) { "Stop lost Console history" }
                        check(owner.luaProfiler.samples.isEmpty())
                        check(owner.liveInspection == null)
                        owner.console.append(listOf(beforeReplay), 0)
                        owner.console.updatePauseOnLuaError(true)
                        windows.hidePanel(panel)
                        playback.play(scene)
                        session.setContent(ViewportMode.Scene, document, Playback.Running)
                        phase++
                    }
                    2 -> if (owner.scenePlayback() == Playback.Paused && row != null) {
                        check(errorPauses == 1 && playback.playback == Playback.Paused && row.repeats == 1)
                        check(owner.luaPauseMessage?.contains("update failure") == true)
                        check(owner.luaPausePosition() != null)
                        check(panel in windows.layout.hidden)
                        check(owner.console.rows.none { it.entry == beforeReplay })
                        val live = checkNotNull(owner.liveInspection)
                        check(live.run != firstRun && live.selection == null)
                        owner.selectLiveEntity(live.selectionFor(live.entities.single { it.authoredKey == scene.selectedKey }))
                        phase++
                    }
                    3 -> if (owner.liveInspection?.details != null) {
                        check(owner.liveInspection?.details?.containsKey("Script") == true)
                        check(owner.scenePlayback() == Playback.Paused)
                        check(owner.luaProfiler.samples.size == 1)
                        playback.requestStep()
                        phase++
                    }
                    4 -> if (row?.repeats == 2) {
                        check(errorPauses == 2 && owner.scenePlayback() == Playback.Paused)
                        check(owner.luaProfiler.samples.size == 2)
                        phase++
                    }
                    5 -> {
                        check(errorPauses == 2 && row?.repeats == 2)
                        session.setContent(ViewportMode.Scene, document, Playback.Stopped)
                        phase++
                    }
                    6 -> if (owner.scenePlayback() == Playback.Stopped) {
                        check(owner.luaPauseMessage == null && owner.luaPausePosition() == null)
                        check(owner.liveInspection == null)
                        playback.stop()
                        val motion = entity.importAsset(Path.of("tests/data/scripts/runtime_transform_update.lua"))
                        entity.setValue("Script", listOf("scriptAsset"), JsonPrimitive(motion.toString().replace('\\', '/')))
                        playback.play(scene)
                        playback.pause()
                        session.setContent(ViewportMode.Scene, playback.source, playback.playback, playback.keepChangesRevision)
                        phase++
                    }
                    7 -> if (owner.liveInspection != null && owner.scenePlayback() == Playback.Paused) {
                        val live = checkNotNull(owner.liveInspection)
                        owner.selectLiveEntity(live.selectionFor(live.entities.single { it.authoredKey == scene.selectedKey }))
                        playback.requestStep()
                        phase++
                    }
                    8 -> {
                        val preview = playback.previewKeepChanges(scene, owner.liveInspection)
                        if (preview != null) {
                            val before = ScenePose.from(entity.document)
                            playback.keepChanges(scene, owner.readPausedPlaySelection(), preview)
                            val after = ScenePose.from(entity.document)
                            check(after.position.x == before.position.x + 60 && after.position.y == before.position.y + 20)
                            scene.undo()
                            check(ScenePose.from(entity.document) == before)
                            scene.redo()
                            check(ScenePose.from(entity.document) == after)
                            playback.stop()
                            session.setContent(ViewportMode.Scene, scene.serialize(), Playback.Stopped)
                            phase++
                        }
                    }
                    9 -> if (owner.scenePlayback() == Playback.Stopped) {
                        check(owner.liveInspection == null && scene.dirty)
                        val root = Files.createTempDirectory("sle reload smoke ").also { reloadDirectory = it }
                        val project = ProjectFiles.create(root.resolve("Game"), "Reload Smoke")
                        val script = project.assets.resolve("Scripts/live reload.lua")
                        Files.createDirectories(script.parent)
                        Files.writeString(script, "return {init=function(self) Engine.setTransformPosition(self, 10, 20) end}")
                        reloadFile = script
                        entity.setValue("Script", listOf("scriptAsset"), JsonPrimitive(script.toString().replace('\\', '/')))
                        owner.setEditorAssetRoot(project.assets)
                        session.setContent(ViewportMode.Scene, scene.serialize(), Playback.Running)
                        phase++
                    }
                    10 -> {
                        val live = owner.liveInspection
                        if (live != null && owner.scenePlayback() == Playback.Running &&
                            owner.runtimeTransforms()[checkNotNull(scene.selectedKey)]?.jsonObject?.get("position")?.jsonObject
                                ?.get("x")?.jsonPrimitive?.double == 10.0) {
                            owner.selectLiveEntity(live.selectionFor(live.entities.single { it.authoredKey == scene.selectedKey }))
                            session.setContent(ViewportMode.Scene, scene.serialize(), Playback.Paused)
                            phase++
                        }
                    }
                    11 -> if (owner.scenePlayback() == Playback.Paused) {
                        val live = checkNotNull(owner.liveInspection)
                        val attachment = live.details?.get("Script")?.jsonArray?.single()?.jsonObject
                        if (attachment != null) {
                            check(attachment.getValue("canReload").jsonPrimitive.boolean)
                            Files.writeString(checkNotNull(reloadFile), "return {")
                            owner.reloadLiveScript(checkNotNull(live.selection), attachment.getValue("key").jsonPrimitive.content,
                                attachment.getValue("scriptAsset").jsonPrimitive.content)
                            phase++
                        }
                    }
                    12 -> if (owner.console.rows.any { it.entry.message.contains("Lua reload compilation failed") }) {
                        val live = checkNotNull(owner.liveInspection)
                        val attachment = checkNotNull(live.details).getValue("Script").jsonArray.single().jsonObject
                        check(attachment.getValue("status").jsonPrimitive.content == "Loaded")
                        Files.writeString(checkNotNull(reloadFile),
                            "return {init=function(self) Engine.setTransformPosition(self, 30, 20) end}")
                        owner.reloadLiveScript(checkNotNull(live.selection), attachment.getValue("key").jsonPrimitive.content,
                            attachment.getValue("scriptAsset").jsonPrimitive.content)
                        phase++
                    }
                    13 -> if (owner.console.rows.any { it.entry.message.startsWith("Reloaded ") }) {
                        check(owner.scenePlayback() == Playback.Paused)
                        check(owner.runtimeTransforms().getValue(checkNotNull(scene.selectedKey)).jsonObject
                            .getValue("position").jsonObject.getValue("x").jsonPrimitive.double == 30.0)
                        owner.queuePlayLua("playConsoleSmokeValue = 42; print('Play console result', playConsoleSmokeValue)")
                        phase++
                    }
                    14 -> if (owner.console.rows.any { it.entry.message == "Play console result\t42" }) {
                        owner.queuePlayLua("assert(playConsoleSmokeValue == 42); error('Play command failure')")
                        phase++
                    }
                    15 -> if (owner.console.rows.any { it.entry.message.contains("Play command failure") }) {
                        check(owner.scenePlayback() == Playback.Paused)
                        owner.setEditorAssetRoot(null)
                        session.setContent(ViewportMode.Scene, scene.serialize(), Playback.Stopped)
                        phase++
                    }
                    16 -> if (owner.scenePlayback() == Playback.Stopped) {
                        check(owner.liveInspection == null)
                        finish(null)
                    }
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
