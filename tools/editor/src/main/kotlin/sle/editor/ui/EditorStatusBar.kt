package sle.editor.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import kotlinx.coroutines.delay
import sle.editor.ui.controls.*
import sle.editor.ui.theme.*

/** What the main window reports beside the notice; [hint] fills the left slot. */
data class EditorStatus(
    /** The active tool or context; [EditorNotices.hint] overrides it while set. */
    val hint: String? = null,
    /** A running background task ("Exporting game…"); shows an indeterminate spinner. */
    val progress: String? = null,
    val selectionCount: Int = 0,
    /** "Editing", "Playing" or "Paused"; null hides it. */
    val playState: String? = null,
    /** The active document has unsaved changes. */
    val dirty: Boolean = false,
)

/** Foreground and background tokens for a notice level; each pair is a tested contrast pair. */
internal fun EditorColors.notice(level: NoticeLevel): Pair<Color,Color> = when(level) {
    NoticeLevel.Info -> info to infoBackground
    NoticeLevel.Success -> success to successBackground
    NoticeLevel.Warning -> warning to warningBackground
    NoticeLevel.Error -> error to errorBackground
}
internal val NoticeLevel.glyph get() = when(this) {
    NoticeLevel.Info -> EditorGlyph.Info
    NoticeLevel.Success -> EditorGlyph.Success
    NoticeLevel.Warning -> EditorGlyph.Warning
    NoticeLevel.Error -> EditorGlyph.Error
}

/** Recent-messages popup rows: newest first; clicking one copies its text. */
internal fun recentNoticeEntries(notices: EditorNotices, copy: (String)->Unit): List<EditorMenuEntry> {
    if(notices.recent.isEmpty()) return listOf(EditorMenuEntry("No messages yet",enabled=false))
    val time=java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss")
    return notices.recent.map { notice ->
        val text=notice.text.let { if(it.length>RECENT_LABEL_LENGTH) it.take(RECENT_LABEL_LENGTH-1)+"…" else it }
        val at=java.time.Instant.ofEpochMilli(notice.postedAt).atZone(java.time.ZoneId.systemDefault()).format(time)
        EditorMenuEntry("${notice.level.name}: $text",help="$at · click to copy: ${notice.text}") { copy(notice.text) }
    } + listOf(EditorMenuEntry.Separator,EditorMenuEntry("Clear Messages") { notices.clearRecent() })
}
private const val RECENT_LABEL_LENGTH=90

private fun copyToClipboard(text: String) {
    runCatching { java.awt.Toolkit.getDefaultToolkit().systemClipboard.setContents(java.awt.datatransfer.StringSelection(text),null) }
}

/**
 * The main window's bottom bar (main window only): the context hint on the left, the latest
 * notice in the centre (level icon and colour, an optional action, Dismiss for sticky ones, and
 * the recent-messages popup), and background progress, selection count, play state and the dirty
 * marker on the right.
 */
@Composable
internal fun EditorStatusBar(notices: EditorNotices, status: EditorStatus, modifier: Modifier = Modifier) {
    val c=LocalEditorColors.current
    val m=LocalEditorMetrics.current
    val t=LocalEditorTypography.current
    val notice=notices.current
    // Info and success notices clear themselves; each new notice restarts the wait.
    LaunchedEffect(notice) {
        while(true) { val wait=notices.remainingMillis() ?: break;delay(wait);notices.expire() }
    }
    val recent=editorMenuLauncher { recentNoticeEntries(notices,::copyToClipboard) }
    Column(modifier.fillMaxWidth().background(c.surfaceRaised)) {
        EditorSeparator()
        Row(Modifier.fillMaxWidth().heightIn(min=m.statusBar).padding(horizontal=m.gap),
            verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(m.gap)) {
            val hint=notices.hint ?: status.hint
            Box(Modifier.weight(1f)) {
                if(hint!=null) EditorTooltip(hint) {
                    Text(hint,color=c.textSecondary,style=t.secondary,maxLines=1,overflow=TextOverflow.Ellipsis)
                }
            }
            Row(Modifier.weight(2f),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(m.gap/2)) {
                if(notice!=null) NoticeChip(notices,notice,Modifier.weight(1f,fill=false))
                EditorGlyphButton("Recent messages",{
                    val pointer=java.awt.MouseInfo.getPointerInfo().location
                    recent(pointer.x,pointer.y)
                },enabled=notices.recent.isNotEmpty(),size=m.statusBar-m.gap) { tint -> EditorGlyphIcon(EditorGlyph.ChevronUp,tint) }
            }
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(m.gap)) {
                val items=mutableListOf<@Composable ()->Unit>()
                status.progress?.let { label -> items+={
                    Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(m.gap/2)) {
                        CircularProgressIndicator(Modifier.size(m.iconSmall),color=c.accent,strokeWidth=m.focusBorderWidth)
                        StatusText(label)
                    }
                } }
                if(status.selectionCount>0) items+={ StatusText("${status.selectionCount} selected") }
                status.playState?.let { state -> items+={ StatusText(state) } }
                if(status.dirty) items+={ StatusText("Unsaved",c.textPrimary, help = "The active document has unsaved changes") }
                items.forEachIndexed { index,item ->
                    if(index>0) EditorSeparator(Modifier.height(m.iconSmall),vertical=true)
                    item()
                }
            }
        }
    }
}

@Composable
private fun StatusText(text: String, color: Color = LocalEditorColors.current.textSecondary, help: String = text) =
    EditorLabel(text,color=color,style=LocalEditorTypography.current.secondary,maxLines=1,softWrap=false,help=help)

@Composable
private fun NoticeChip(notices: EditorNotices, notice: EditorNotice, modifier: Modifier) {
    val c=LocalEditorColors.current
    val m=LocalEditorMetrics.current
    val (foreground,background)=c.notice(notice.level)
    Row(modifier.background(background,RoundedCornerShape(m.controlRadius)).padding(start=m.controlPadding),
        verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(m.gap)) {
        EditorGlyphIcon(notice.level.glyph,foreground)
        EditorTooltip(notice.text,Modifier.weight(1f,fill=false)) {
            Text(notice.text,color=foreground,style=LocalEditorTypography.current.secondary,maxLines=1,overflow=TextOverflow.Ellipsis)
        }
        val action=notice.action
        if(action!=null && action.available()) EditorButton(action.label,{ notices.invoke(notice) },style=EditorButtonStyle.Ghost,minHeight=m.statusBar-m.gap)
        if(notice.level.sticky) EditorGlyphButton("Dismiss",{ notices.dismiss(notice) },size=m.statusBar-m.gap) { tint -> EditorGlyphIcon(EditorGlyph.Close,tint) }
        else Spacer(Modifier.width(m.gap))
    }
}
