package sle.editor.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.Density
import org.jetbrains.skia.EncodedImageFormat
import sle.editor.ui.theme.*
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.*

/**
 * Renders the status bar offscreen (no window or desktop needed) in both themes and densities,
 * checks it keeps the `statusBar` height with a notice, an action and every right-hand fact,
 * and writes the images to build/reports/status-bar for review.
 */
@OptIn(ExperimentalComposeUiApi::class)
class EditorStatusBarRenderTest {
    private val SPINNER_FRAME_NANOS=400_000_000L
    @Test fun statusBarKeepsItsMetricHeightInBothThemesAndDensities() {
        val out=Path.of(System.getProperty("user.dir"),"build","reports","status-bar").also { Files.createDirectories(it) }
        val status=EditorStatus(hint="Click to select; Ctrl+click adds; drag to move; middle-drag pans",
            progress="Exporting game…",selectionCount=3,playState="Editing",dirty=true)
        for(mode in EditorThemeMode.entries) for(density in EditorDensity.entries) {
            val heights=mutableListOf<Int>()
            val bars=NoticeLevel.entries.map { level -> EditorNotices().also {
                it.post(level,if(level==NoticeLevel.Info) "Deleted 3 entities" else "${level.name}: could not save forest.scene.json",
                    ttlMillis=60_000,action=if(level==NoticeLevel.Info) NoticeAction("Undo") {} else null)
            } }+EditorNotices()
            val scene=ImageComposeScene(width=1100,height=200,density=Density(1f)) {
                EditorTheme(EditorAppearance(mode=mode,density=density)) {
                    Column { bars.forEach { notices -> EditorStatusBar(notices,status,Modifier.onSizeChanged { heights+=it.height }) } }
                }
            }
            try {
                // A later frame, so the progress spinner shows an arc rather than its start.
                scene.render(0);val image=scene.render(SPINNER_FRAME_NANOS)
                Files.write(out.resolve("status-bar-${mode.name.lowercase()}-${density.name.lowercase()}.png"),
                    image.encodeToData(EncodedImageFormat.PNG)!!.bytes)
            } finally { scene.close() }
            val metrics=editorMetrics(density)
            // One hairline separator above a row of exactly `statusBar` (density 1: dp == px).
            val expected=(metrics.statusBar+metrics.borderWidth).value.toInt()
            assertEquals(List(bars.size) { expected },heights.takeLast(bars.size),"$mode $density status bar height")
        }
    }
}
