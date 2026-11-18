package sle.editor.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.*

/**
 * Minimum heights: consumers must allow content/font scaling to grow beyond them.
 * [toolbar] is the dock/tab header height; [button] the toolbar/command button height;
 * [statusBar] the main window's bottom status bar.
 */
data class EditorMetrics(
    val toolbar: Dp, val button: Dp, val field: Dp, val treeRow: Dp,
    val propertyRow: Dp, val sectionHeader: Dp, val panelPadding: Dp,
    val gap: Dp, val icon: Dp, val body: TextUnit, val secondary: TextUnit,
    val heading: TextUnit = body, val brand: TextUnit = 20.sp,
    val iconSmall: Dp = 14.dp, val tabUnderline: Dp = 2.dp,
    val controlRadius: Dp = 3.dp, val borderWidth: Dp = 1.dp, val focusBorderWidth: Dp = 2.dp,
    val controlPadding: Dp = 8.dp, val controlVerticalPadding: Dp = 2.dp,
    val treeIndent: Dp = 14.dp, val statusBar: Dp = 24.dp,
    val compactListMaxHeight: Dp = 100.dp, val emptyStateMinHeight: Dp = 120.dp,
    val dockSplitter: Dp = 6.dp, val dockGrip: Dp = 16.dp,
    val assetDetailsBreakpoint: Dp = 900.dp, val assetTypeBreakpoint: Dp = 550.dp,
    val assetSizeBreakpoint: Dp = 700.dp, val assetFolderMinimum: Dp = 88.dp,
    val assetFolderMaximum: Dp = 200.dp, val assetDetailsWidth: Dp = 240.dp,
    val assetTypeWidth: Dp = 90.dp, val assetSizeWidth: Dp = 70.dp,
    val assetBreadcrumbWidth: Dp = 320.dp,
    val propertyStackThreshold: Dp = 240.dp, val propertyLabelFraction: Float = 0.36f,
)
fun editorMetrics(density: EditorDensity) = when(density) {
    EditorDensity.Compact -> EditorMetrics(28.dp,26.dp,26.dp,24.dp,28.dp,26.dp,8.dp,4.dp,16.dp,13.sp,12.sp,heading=13.sp)
    EditorDensity.Comfortable -> EditorMetrics(34.dp,32.dp,30.dp,28.dp,34.dp,30.dp,10.dp,6.dp,18.dp,14.sp,13.sp,heading=14.sp,brand=22.sp,
        controlPadding=10.dp,controlVerticalPadding=3.dp,treeIndent=16.dp,statusBar=28.dp)
}
val LocalEditorMetrics=staticCompositionLocalOf { editorMetrics(EditorDensity.Compact) }
