package sle.editor.ui.theme

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.*
import sle.editor.ui.EditorTheme
import sle.editor.ui.EditorIcons
import sle.editor.ui.controls.*
import sle.editor.model.*
import kotlinx.serialization.json.*
import androidx.compose.ui.text.input.TextFieldValue

/** Standalone presentation fixture: no project, JNI, documents or user preference writes. */
fun launchThemeGallery(captureDirectory: java.nio.file.Path? = null, inspectors: Boolean = false) = application {
    var appearance by remember { mutableStateOf(EditorAppearance(scale=1f)) }
    var fontScale by remember { mutableStateOf(1f) }
    val scroll=rememberScrollState()
    Window(onCloseRequest=::exitApplication,title="Little Forest theme gallery",
        state=rememberWindowState(width=900.dp,height=650.dp),
        icon=androidx.compose.ui.res.painterResource("sle/editor/branding/sle-256.png")) {
        androidx.compose.runtime.LaunchedEffect(window) {
            if(captureDirectory!=null) {
                window.isAlwaysOnTop=true
                java.nio.file.Files.createDirectories(captureDirectory)
                for(width in if(inspectors) listOf(900,540) else listOf(900))
                for(scale in listOf(1f,1.5f)) for(mode in EditorThemeMode.entries) {
                    if(inspectors) window.setSize(width,850)
                    appearance=appearance.copy(mode=mode)
                    fontScale=scale
                    window.toFront()
                    java.awt.Robot().mouseMove(window.x+window.width-10,window.y+10)
                    kotlinx.coroutines.delay(400)
                    scroll.scrollTo(0)
                    kotlinx.coroutines.delay(800)
                    val bounds=java.awt.Rectangle(window.locationOnScreen,window.size)
                    javax.imageio.ImageIO.write(java.awt.Robot().createScreenCapture(bounds),"png",captureDirectory.resolve("gallery-${mode.name.lowercase()}-font-$scale${if(inspectors) "-width-$width" else ""}.png").toFile())
                }
                exitApplication()
            }
        }
        val systemDensity=LocalDensity.current
        CompositionLocalProvider(LocalDensity provides Density(systemDensity.density,systemDensity.fontScale*fontScale)) {
        EditorTheme(appearance) {
            Surface(Modifier.fillMaxSize()) {
                Column(Modifier.verticalScroll(scroll).padding(LocalEditorMetrics.current.panelPadding),verticalArrangement=Arrangement.spacedBy(LocalEditorMetrics.current.gap)) {
                    Text("Little Forest",style=MaterialTheme.typography.h5)
                    Row {
                        EditorButton("Font 100%",{fontScale=1f})
                        EditorButton("Font 150%",{fontScale=1.5f})
                    }
                    if(inspectors) sle.editor.ui.inspector.InspectorGallery() else CompactControlsGallery()
                    Row {
                        EditorThemeMode.entries.forEach { mode -> Button(onClick={appearance=appearance.copy(mode=mode)}) { Text(mode.name) } }
                        EditorDensity.entries.forEach { density -> Button(onClick={appearance=appearance.copy(density=density)}) { Text(density.name) } }
                    }
                    Text("System body: Player / Игрок — Position X 128.0",color=LocalEditorColors.current.textPrimary)
                    Text("Secondary metadata",color=LocalEditorColors.current.textSecondary)
                    var text by remember { mutableStateOf("Player") }
                    OutlinedTextField(text,{text=it},label={Text("Name")})
                    var clicks by remember { mutableStateOf(0) }
                    var checked by remember { mutableStateOf(true) }
                    Row { Button({clicks++}) { Text("Primary ($clicks)") };Button({},enabled=false) { Text("Disabled") };Checkbox(checked,{checked=it}) }
                    Row { EditorIcons.kinds.forEach { Icon(EditorIcons.painter(it),it,Modifier.size(LocalEditorMetrics.current.icon),tint=LocalEditorColors.current.accent) } }
                    val c=LocalEditorColors.current
                    listOf(Triple("Error",c.error,c.errorBackground),Triple("Warning",c.warning,c.warningBackground),Triple("Success",c.success,c.successBackground),Triple("Information",c.info,c.infoBackground)).forEach { (text,fg,bg) ->
                        Surface(color=bg) { Text(text,color=fg,modifier=Modifier.padding(8.dp)) }
                    }
                }
            }
        }
    }
    }
}

/** Real controls and commands: use Tab/Shift-Tab, hover, press, Enter and Escape. */
@Composable
private fun CompactControlsGallery() {
    val state=remember { EntityWorkspaceState(mapOf("Transform" to ComponentDefinition(
        buildJsonObject { put("x",0.0);put("y",1.0) },JsonObject(emptyMap())))) }
    var text by remember { mutableStateOf(TextFieldValue("Player / Игрок")) }
    var checked by remember { mutableStateOf(true) }
    var selected by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf(true) }
    var choice by remember { mutableStateOf("Linear") }
    var tab by remember { mutableStateOf("Scene") }
    Row(Modifier.fillMaxWidth().height(LocalEditorMetrics.current.toolbar).background(LocalEditorColors.current.surfaceRaised),
        verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
        Box(Modifier.width(LocalEditorMetrics.current.dockGrip),contentAlignment=androidx.compose.ui.Alignment.Center) { EditorGlyphIcon(EditorGlyph.Grip,LocalEditorColors.current.textSecondary) }
        listOf("Scene","Game","Entity").forEach { EditorTab(it,tab==it,{tab=it}) }
        Spacer(Modifier.weight(1f))
        listOf(EditorGlyph.Minimize,EditorGlyph.Detach,EditorGlyph.Close).forEach { g ->
            EditorGlyphButton(g.name,{},size=LocalEditorMetrics.current.button-LocalEditorMetrics.current.gap) { EditorGlyphIcon(g,it) } }
    }
    EditorPanelHeader("Compact controls") {
        EditorButton("Undo",{state.undo()},enabled=state.canUndo || state.gestureActive,style=EditorButtonStyle.Ghost)
        EditorButton("Redo",{state.redo()},enabled=state.canRedo,style=EditorButtonStyle.Ghost)
    }
    EditorToolbar {
        EditorButton("Play",{},primary=true);EditorButton("Pause",{});EditorButton("Stop",{},enabled=false)
        EditorToolbarDivider()
        EditorGlyphButton("Zoom in",{}) { EditorGlyphIcon(EditorGlyph.Plus,it) }
        EditorGlyphButton("Zoom out",{}) { EditorGlyphIcon(EditorGlyph.Minus,it) }
        EditorButton("Fit",{},style=EditorButtonStyle.Ghost);EditorButton("Ghost disabled",{},enabled=false,style=EditorButtonStyle.Ghost)
    }
    Row(horizontalArrangement=Arrangement.spacedBy(LocalEditorMetrics.current.gap)) {
        EditorButton("Primary",{selected=!selected},primary=true,help="Toggle the selected example")
        EditorToggle("Selected",selected,{selected=it})
        EditorButton("Disabled",{},enabled=false,help="Unavailable example")
        EditorIconButton("Reset",EditorIcons.painter("entity"),{state.newDocument()})
        EditorCheckBox("Enabled",checked,{checked=it})
        EditorSelect("Filter",choice,listOf("Linear","Nearest"),{choice=it})
    }
    EditorPropertyRow("Filter (row)") { EditorSelect("Filter",choice,listOf("Linear","Nearest"),{choice=it},Modifier.weight(1f),showLabel=false) }
    EditorPropertyRow("Visible") { EditorCheckBox("Visible",checked,{checked=it},showLabel=false) }
    Row(horizontalArrangement=Arrangement.spacedBy(LocalEditorMetrics.current.gap)) {
        listOf("Normal","Hover","Pressed","Focus").forEach { label ->
            val source=remember { MutableInteractionSource() }
            LaunchedEffect(source) {
                kotlinx.coroutines.delay(200)
                when(label) {
                    "Hover" -> source.emit(HoverInteraction.Enter())
                    "Pressed" -> source.emit(PressInteraction.Press(Offset.Zero))
                    "Focus" -> source.emit(FocusInteraction.Focus())
                }
            }
            EditorButton(label,{selected=!selected},source=source,help="Interaction-state specimen; click toggles selection")
        }
    }
    EditorInspectorSection("Transform",expanded,{expanded=it}) {
        EditorPropertyRow("Name",help="System body font and editable selection") {
            EditorTextField(text,{text=it},"Name",Modifier.weight(1f))
        }
        EditorPropertyRow("Read only") {
            EditorTextField(TextFieldValue("Inherited"),{},"Read only",Modifier.weight(1f),readOnly=true)
        }
        EditorPropertyRow("Disabled") {
            EditorTextField(TextFieldValue("Unavailable"),{},"Disabled",Modifier.weight(1f),enabled=false)
        }
        EditorVectorField("Position",listOf("X" to state.numberEdit("Transform",listOf("x")),
            "Y" to state.numberEdit("Transform",listOf("y"))))
        EditorPropertyRow("Invalid") {
            EditorTextField(TextFieldValue("1e"),{},"Invalid example",Modifier.weight(1f),readOnly=true,invalid=true,help="Finish the exponent before saving")
        }
    }
    EditorTreeRow("Selected tree row (Tab shows independent focus)",selected,{selected=!selected},depth=1)
}
