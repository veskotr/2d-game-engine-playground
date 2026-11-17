package sle.editor.project.assets

import androidx.compose.runtime.*
import kotlinx.coroutines.channels.Channel
import sle.editor.project.OpenedProject
import java.awt.Window
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent

@Composable
internal fun rememberAssetBrowser(project: OpenedProject?,window: Window): AssetBrowserState? {
    val key=project?.let { it.file to it.document.assetRoot }
    val state=remember(key) { if(project==null) null else AssetBrowserState() }
    val events=remember(key) { Channel<AssetSnapshot>(Channel.CONFLATED) }
    DisposableEffect(key) {
        val service=if(project!=null && state!=null) AssetBrowserService(project.root,project.root.resolve(project.document.assetRoot),onSnapshot={events.trySend(it)}) else null
        state?.requestRefresh={service?.refresh()}
        val listener=object: WindowAdapter() { override fun windowGainedFocus(event: WindowEvent) { state?.refresh() } }
        window.addWindowFocusListener(listener)
        onDispose { window.removeWindowFocusListener(listener);service?.close();events.close();state?.requestRefresh={} }
    }
    LaunchedEffect(key) { for(snapshot in events) state?.accept(snapshot) }
    return state
}
