package sle.editor.ui

import androidx.compose.runtime.*

enum class NoticeLevel { Info, Success, Warning, Error;
    /** Info and success clear themselves; warnings and errors stay until dismissed or replaced. */
    val sticky get() = this == Warning || this == Error
}

/**
 * A button on a notice, such as Undo. [available] is read while the notice shows: the button
 * hides once the action no longer applies (for Undo, once another step became the latest).
 */
class NoticeAction(val label: String, val available: () -> Boolean = { true }, val run: () -> Unit)

class EditorNotice internal constructor(
    val level: NoticeLevel,
    val text: String,
    val action: NoticeAction?,
    /** Milliseconds from [EditorNotices.clock]. */
    val postedAt: Long,
    /** Null: stays until dismissed or replaced. */
    val ttlMillis: Long?,
) {
    val expiresAt: Long? get() = ttlMillis?.let { postedAt + it }
}

/**
 * The editor's one feedback channel for results (rule R8): the status bar shows [current], and
 * [recent] keeps a short history until the Console (layer L4) takes it over. Field validation
 * stays inline in the Inspector, and a panel explains its own empty state; neither posts here.
 *
 * A newer notice replaces the current one. Info and success clear after [DEFAULT_TTL_MILLIS];
 * warnings and errors stay until dismissed or replaced. Runs on the UI thread.
 */
class EditorNotices(private val capacity: Int = RECENT_CAPACITY) {
    /** Milliseconds; tests replace it. */
    var clock: () -> Long = System::currentTimeMillis
    var current by mutableStateOf<EditorNotice?>(null)
        private set
    /** Newest first, including the current notice. */
    var recent by mutableStateOf<List<EditorNotice>>(emptyList())
        private set
    /**
     * The status bar's left slot: what the active tool or context does. The owner of the context
     * (S5's viewport tools) sets it while active and clears it after; null shows the window's
     * default hint.
     */
    var hint by mutableStateOf<String?>(null)

    /** [ttlMillis] overrides the level's default; null keeps it (sticky for warnings and errors). */
    fun post(level: NoticeLevel, text: String, ttlMillis: Long? = null, action: NoticeAction? = null): EditorNotice {
        val notice = EditorNotice(level, text, action, clock(),
            ttlMillis ?: if(level.sticky) null else DEFAULT_TTL_MILLIS)
        current = notice
        recent = (listOf(notice) + recent).take(capacity)
        return notice
    }
    fun info(text: String, action: NoticeAction? = null) = post(NoticeLevel.Info, text, action = action)
    fun success(text: String, action: NoticeAction? = null) = post(NoticeLevel.Success, text, action = action)
    fun warning(text: String, action: NoticeAction? = null) = post(NoticeLevel.Warning, text, action = action)
    fun error(text: String, action: NoticeAction? = null) = post(NoticeLevel.Error, text, action = action)

    /** Hides [notice] (default: the current one); it stays in [recent]. */
    fun dismiss(notice: EditorNotice? = current) { if(notice != null && current === notice) current = null }
    /** Clears the current notice once its time is up. The status bar calls this when the time passes. */
    fun expire(now: Long = clock()) { current?.expiresAt?.let { if(now >= it) current = null } }
    /** Milliseconds until the current notice expires, or null when it is sticky or there is none. */
    fun remainingMillis(now: Long = clock()) = current?.expiresAt?.let { (it - now).coerceAtLeast(0) }
    /** Runs the notice's action if it still applies. The notice is dismissed first, so a result the action posts stays. */
    fun invoke(notice: EditorNotice): Boolean {
        val action = notice.action?.takeIf { it.available() } ?: return false
        dismiss(notice)
        action.run()
        return true
    }
    fun clearRecent() { recent = listOfNotNull(current) }

    companion object {
        const val DEFAULT_TTL_MILLIS = 6_000L
        const val RECENT_CAPACITY = 20
    }
}

/** Document `message` fields are results (success); losing undo history is worth a sticky warning. */
internal fun documentMessageLevel(text: String) =
    if(text.endsWith(sle.editor.model.HISTORY_CLEARED_NOTICE)) NoticeLevel.Warning else NoticeLevel.Success

/** Notices posted after [since] (the newest one when a step began), newest first. */
internal fun EditorNotices.postedAfter(since: EditorNotice?) = recent.takeWhile { it !== since }

/** Notices posted from composables that are not given the service directly (null in tests and the gallery). */
val LocalEditorNotices = staticCompositionLocalOf<EditorNotices?> { null }

/**
 * A model field the status bar reports. Document `error`/`message` fields are **consumed**: posted,
 * then cleared, so the same text set again later is reported again. Appearance and layout notices
 * belong to their owners and are **watched** ([clear] null): posted each time they change to a new text.
 */
internal class NoticeSource(val key: Any, val level: (String) -> NoticeLevel, val read: () -> String?, val clear: (() -> Unit)?)

/** Moves [NoticeSource] texts into [notices]; [ReportNotices] calls it whenever a source changes. */
internal class NoticeBridge(private val notices: EditorNotices) {
    private val watched = HashMap<Any, String?>()
    fun deliver(sources: List<NoticeSource>) {
        for(source in sources) {
            val text = source.read()
            val clear = source.clear
            if(clear != null) {
                if(text != null) { notices.post(source.level(text), text); clear() }
            } else if(text != watched[source.key]) {
                watched[source.key] = text
                if(text != null) notices.post(source.level(text), text)
            }
        }
    }
}

/** Posts every source's text to [notices] as it appears. Composed once, by the main window. */
@Composable
internal fun ReportNotices(notices: EditorNotices, sources: () -> List<NoticeSource>) {
    val current by rememberUpdatedState(sources)
    val bridge = remember(notices) { NoticeBridge(notices) }
    LaunchedEffect(bridge) {
        snapshotFlow { current().map { it.read() } }.collect { bridge.deliver(current()) }
    }
}
