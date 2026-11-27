package sle.editor.ui.controls

import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.key.*
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import sle.editor.ui.theme.*

/**
 * Button emphasis. Primary: filled brand green for the main action of a surface.
 * Secondary: restrained fill and subtle outline. Ghost: no resting chrome, fill on
 * hover/press only (toolbars, zoom, dock actions).
 */
/**
 * The four button variants. Primary: the one action a surface exists for (at most one per
 * footer or toolbar group). Secondary: other actions. Ghost: low-emphasis and toolbar
 * actions. Danger: confirming something destructive, such as Delete in a confirmation.
 */
enum class EditorButtonStyle { Primary, Secondary, Ghost, Danger }

private data class ControlColors(val background: Color, val border: Color?, val content: Color)

@Composable
private fun controlColors(style: EditorButtonStyle, enabled: Boolean, selected: Boolean, hovered: Boolean, pressed: Boolean): ControlColors {
    val c=LocalEditorColors.current
    val dark=LocalEditorAppearance.current.mode==EditorThemeMode.Dark
    if(!enabled) return ControlColors(if(style==EditorButtonStyle.Ghost) Color.Transparent else c.disabledBackground,
        if(style==EditorButtonStyle.Ghost) null else c.borderSubtle,c.disabledText)
    return when(style) {
        EditorButtonStyle.Primary -> ControlColors(when {
            pressed -> lerp(c.accent,Color.Black,if(dark) .18f else .22f)
            hovered -> lerp(c.accent,if(dark) Color.White else Color.Black,.1f)
            else -> c.accent },null,c.accentOnPrimary)
        EditorButtonStyle.Secondary -> ControlColors(when {
            pressed -> c.pressedBackground; selected -> c.selectedBackground
            hovered -> c.hoverBackground; else -> c.surfaceRaised },
            if(selected) c.accent.copy(alpha=.55f) else c.borderSubtle,c.textPrimary)
        EditorButtonStyle.Ghost -> ControlColors(when {
            pressed -> c.pressedBackground; selected -> c.selectedBackground
            hovered -> c.hoverBackground; else -> Color.Transparent },null,c.textPrimary)
        EditorButtonStyle.Danger -> ControlColors(when {
            pressed -> lerp(c.error,Color.Black,if(dark) .18f else .22f)
            hovered -> lerp(c.error,if(dark) Color.White else Color.Black,.1f)
            else -> c.error },null,c.surface)
    }
}

/** Fill, optional outline and an independent keyboard focus ring. */
@Composable
private fun Modifier.controlChrome(colors: ControlColors, focused: Boolean): Modifier {
    val m=LocalEditorMetrics.current
    val shape=RoundedCornerShape(m.controlRadius)
    var result=background(colors.background,shape)
    if(focused) result=result.border(m.focusBorderWidth,LocalEditorColors.current.focusRing,shape)
    else if(colors.border!=null) result=result.border(m.borderWidth,colors.border,shape)
    return result
}

internal val LocalTooltipsAbove = compositionLocalOf { false }

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun EditorTooltip(help: String?, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val placement = if (LocalTooltipsAbove.current) {
        TooltipPlacement.ComponentRect(Alignment.TopCenter, Alignment.TopCenter,
            DpOffset(0.dp, -LocalEditorMetrics.current.gap))
    } else {
        TooltipPlacement.CursorPoint(DpOffset(0.dp, 16.dp))
    }
    if(help.isNullOrBlank()) Box(modifier) { content() } else TooltipArea(modifier=modifier,tooltipPlacement=placement,tooltip={
        Surface(color=LocalEditorColors.current.tooltipBackground,shape=RoundedCornerShape(LocalEditorMetrics.current.controlRadius)) {
            Text(help,color=LocalEditorColors.current.tooltipText,style=LocalEditorTypography.current.secondary,
                modifier=Modifier.widthIn(max=360.dp).padding(horizontal=LocalEditorMetrics.current.controlPadding,vertical=LocalEditorMetrics.current.gap),
                maxLines=12,overflow=TextOverflow.Ellipsis)
        }
    },content=content)
}

/** Full label text stays available on hover when its layout truncates it. */
@Composable
internal fun EditorLabel(text: String, modifier: Modifier = Modifier, color: Color = Color.Unspecified,
    style: androidx.compose.ui.text.TextStyle = LocalTextStyle.current, maxLines: Int = Int.MAX_VALUE,
    softWrap: Boolean = true, overflow: TextOverflow = TextOverflow.Clip, textAlign: TextAlign? = null,
    help: String = text) {
    EditorTooltip(help, modifier) {
        Text(text, color = color, style = style, maxLines = maxLines, softWrap = softWrap, overflow = overflow, textAlign = textAlign)
    }
}

/** [primary] is kept for existing callers and maps to [EditorButtonStyle.Primary]. */
@Composable
internal fun EditorButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier,
    enabled: Boolean = true, selected: Boolean = false, primary: Boolean = false, help: String? = null,
    source: MutableInteractionSource = remember { MutableInteractionSource() },
    style: EditorButtonStyle = if(primary) EditorButtonStyle.Primary else EditorButtonStyle.Secondary,
    /** Smaller only inside a shorter strip, such as the status bar. */
    minHeight: Dp = LocalEditorMetrics.current.button) {
    val m=LocalEditorMetrics.current
    val hovered by source.collectIsHoveredAsState()
    val pressed by source.collectIsPressedAsState()
    val focused by source.collectIsFocusedAsState()
    val colors=controlColors(style,enabled,selected,hovered,pressed)
    EditorTooltip(help?.takeIf { it.isNotBlank() } ?: label,modifier) {
        Box(Modifier.controlChrome(colors,focused)
            .clickable(source,null,enabled,role=Role.Button,onClick=onClick)
            .heightIn(min=minHeight).padding(horizontal=m.controlPadding,vertical=m.controlVerticalPadding),
            contentAlignment=Alignment.Center) {
            Text(label,color=colors.content,style=LocalEditorTypography.current.toolbar,maxLines=1,softWrap=false)
        }
    }
}

@Composable
internal fun EditorToggle(label: String, selected: Boolean, onSelectedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier, enabled: Boolean = true, help: String? = null,
    style: EditorButtonStyle = EditorButtonStyle.Secondary) {
    EditorButton(label,{onSelectedChange(!selected)},modifier.semantics { this.selected=selected },enabled,selected,help=help,style=style)
}

/** Icon-only toggle (viewport tools, snapping): the selected state uses the control's selected chrome. */
@Composable
internal fun EditorIconToggle(label: String, glyph: EditorGlyph, selected: Boolean, onSelectedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier, enabled: Boolean = true, help: String? = label) {
    EditorGlyphButton(label,{onSelectedChange(!selected)},modifier.semantics { this.selected=selected },enabled,selected,help) { tint ->
        EditorGlyphIcon(glyph,tint,size=LocalEditorMetrics.current.icon)
    }
}

@Composable
internal fun EditorIconButton(label: String, painter: Painter, onClick: () -> Unit,
    modifier: Modifier = Modifier, enabled: Boolean = true, selected: Boolean = false, help: String? = label,
    style: EditorButtonStyle = EditorButtonStyle.Ghost) {
    EditorGlyphButton(label,onClick,modifier,enabled,selected,help,style) { tint ->
        Icon(painter,label,Modifier.size(LocalEditorMetrics.current.icon),tint=tint)
    }
}

/** Square icon-only button; the icon is drawn by [icon] with the state-dependent tint. */
@Composable
internal fun EditorGlyphButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier,
    enabled: Boolean = true, selected: Boolean = false, help: String? = label,
    style: EditorButtonStyle = EditorButtonStyle.Ghost, size: Dp = LocalEditorMetrics.current.button,
    icon: @Composable (Color) -> Unit) {
    val source=remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    val pressed by source.collectIsPressedAsState()
    val focused by source.collectIsFocusedAsState()
    val colors=controlColors(style,enabled,selected,hovered,pressed)
    val tint=if(enabled && style==EditorButtonStyle.Ghost && !hovered && !selected) LocalEditorColors.current.textSecondary else colors.content
    EditorTooltip(help?.takeIf { it.isNotBlank() } ?: label,modifier) {
        Box(Modifier.controlChrome(colors,focused)
            .clickable(source,null,enabled,role=Role.Button,onClick=onClick)
            .semantics { contentDescription=label }
            .sizeIn(minWidth=size,minHeight=size),contentAlignment=Alignment.Center) { icon(tint) }
    }
}

/**
 * Small glyphs shared by dock actions, selects, trees and checkboxes. Glyphs with an [iconKey] use
 * the supplied editor icon (`EditorIcons`) as a mask in the state tint; the rest are drawn strokes.
 */
enum class EditorGlyph(internal val iconKey: String? = null) {
    Minimize, Detach, Close, ChevronDown, ChevronRight, ChevronUp, Check, Plus, Minus, Grip,
    /** Notice levels: drawn in the level's token colour, always beside the notice text. */
    Info("info"), Success("success"), Warning("warning"), Error("error"),
    /** Scene view tools and snapping. */
    ToolSelect("tool_select"), ToolMove("tool_move"), ToolRotate("tool_rotate"), ToolScale("tool_scale"), ToolTiles("tilemap"), Snap("snap"),
    /** Rotate/Scale pivot: the active entity's pivot, or the selection center. */
    PivotActive("pivot"), PivotCenter("center"),
    /** Overflow: three dots, for the toolbar's more-actions menu. */
    More }

@Composable
internal fun EditorGlyphIcon(glyph: EditorGlyph, tint: Color, modifier: Modifier = Modifier,
    size: Dp = LocalEditorMetrics.current.iconSmall) {
    glyph.iconKey?.let { key ->
        // The 16px art stays crisp at 100%; larger scales use the 24px art.
        val pixels=with(androidx.compose.ui.platform.LocalDensity.current) { size.toPx() }
        val path=sle.editor.ui.EditorIcons.path(key,LocalEditorAppearance.current.mode,if(pixels<=18f) 16 else 24)
        Icon(androidx.compose.ui.res.painterResource(path),null,modifier.size(size),tint=tint)
        return
    }
    Canvas(modifier.size(size)) {
        val w=this.size.width;val h=this.size.height
        val stroke=(1.4f*density).coerceAtLeast(1f)
        fun line(x1: Float,y1: Float,x2: Float,y2: Float) = drawLine(tint,Offset(x1*w,y1*h),Offset(x2*w,y2*h),stroke,StrokeCap.Round)
        when(glyph) {
            EditorGlyph.Minimize -> line(.25f,.72f,.75f,.72f)
            EditorGlyph.Detach -> {
                drawRect(tint,Offset(.18f*w,.32f*h),androidx.compose.ui.geometry.Size(.5f*w,.5f*h),style=Stroke(stroke))
                line(.48f,.52f,.82f,.18f);line(.58f,.18f,.82f,.18f);line(.82f,.18f,.82f,.42f)
            }
            EditorGlyph.Close -> { line(.28f,.28f,.72f,.72f);line(.72f,.28f,.28f,.72f) }
            EditorGlyph.ChevronDown -> { line(.3f,.4f,.5f,.6f);line(.5f,.6f,.7f,.4f) }
            EditorGlyph.ChevronRight -> { line(.4f,.3f,.6f,.5f);line(.6f,.5f,.4f,.7f) }
            EditorGlyph.Check -> { line(.24f,.52f,.43f,.7f);line(.43f,.7f,.76f,.32f) }
            EditorGlyph.Plus -> { line(.25f,.5f,.75f,.5f);line(.5f,.25f,.5f,.75f) }
            EditorGlyph.Minus -> line(.25f,.5f,.75f,.5f)
            EditorGlyph.Grip -> for(x in listOf(.38f,.62f)) for(y in listOf(.3f,.5f,.7f))
                drawCircle(tint,stroke*.75f,Offset(x*w,y*h))
            EditorGlyph.ChevronUp -> { line(.3f,.6f,.5f,.4f);line(.5f,.4f,.7f,.6f) }
            EditorGlyph.More -> for(x in listOf(.25f,.5f,.75f)) drawCircle(tint,stroke,Offset(x*w,.5f*h))
            else -> Unit // Supplied icons, handled above.
        }
    }
}

/** Flat tab: quiet when inactive, surface fill and a 2dp accent underline when selected. */
@Composable
internal fun EditorTab(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, help: String? = null) {
    val c=LocalEditorColors.current
    val m=LocalEditorMetrics.current
    val source=remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    val focused by source.collectIsFocusedAsState()
    val underline=c.accent
    EditorTooltip(help?.takeIf { it.isNotBlank() } ?: label,modifier) {
        Box(Modifier.fillMaxHeight()
            .background(when { selected -> c.surface;hovered -> c.hoverBackground;else -> Color.Transparent })
            .then(if(focused) Modifier.border(m.borderWidth,c.focusRing) else Modifier)
            .drawBehind {
                if(selected) { val t=m.tabUnderline.toPx();drawRect(underline,Offset(0f,size.height-t),androidx.compose.ui.geometry.Size(size.width,t)) }
            }
            .clickable(source,null,role=Role.Tab,onClick=onClick)
            .semantics { this.selected=selected }
            .padding(horizontal=m.controlPadding+m.gap/2),contentAlignment=Alignment.Center) {
            Text(label,color=if(selected || hovered) c.textPrimary else c.textSecondary,
                style=LocalEditorTypography.current.toolbar,maxLines=1,softWrap=false)
        }
    }
}

/** 1dp low-emphasis divider between regions. */
@Composable
internal fun EditorSeparator(modifier: Modifier = Modifier, vertical: Boolean = false) {
    val color=LocalEditorColors.current.separator
    val hair=LocalEditorMetrics.current.borderWidth
    Box(if(vertical) modifier.fillMaxHeight().width(hair).background(color) else modifier.fillMaxWidth().height(hair).background(color))
}

/** Command strip: consistent height, spacing and wrapping for panel toolbars. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun EditorToolbar(modifier: Modifier = Modifier, tooltipsAbove: Boolean = false,
    content: @Composable FlowRowScope.() -> Unit) {
    val m=LocalEditorMetrics.current
    CompositionLocalProvider(LocalTooltipsAbove provides tooltipsAbove) {
        FlowRow(modifier.fillMaxWidth().padding(horizontal=m.gap,vertical=m.gap/2),
            horizontalArrangement=Arrangement.spacedBy(m.gap/2),verticalArrangement=Arrangement.spacedBy(m.gap/2),content=content)
    }
}

/** Vertical rule separating toolbar groups. */
@Composable
internal fun EditorToolbarDivider() {
    val m=LocalEditorMetrics.current
    Box(Modifier.height(m.button).padding(horizontal=m.gap/2),contentAlignment=Alignment.Center) {
        Box(Modifier.height(m.button-m.gap*2).width(m.borderWidth).background(LocalEditorColors.current.separator))
    }
}

/** Centered, compact explanation for panels with nothing to show. */
@Composable
internal fun EditorEmptyState(title: String, message: String? = null, modifier: Modifier = Modifier,
    brand: Boolean = false, action: (@Composable () -> Unit)? = null) {
    val m=LocalEditorMetrics.current
    val t=LocalEditorTypography.current
    Box(modifier.fillMaxSize().padding(m.panelPadding),contentAlignment=Alignment.Center) {
        Column(horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(m.gap)) {
            // Branding is the only use of the decorative brand face (Handjet).
            if(brand) Image(androidx.compose.ui.res.painterResource("sle/editor/branding/sle-256.png"),null,
                Modifier.size(m.button*1.5f),alpha=.9f)
            EditorLabel(title,style=if(brand) t.brand else t.heading,color=LocalEditorColors.current.textPrimary)
            if(message!=null) EditorLabel(message,style=t.secondary,color=LocalEditorColors.current.textSecondary,
                textAlign=androidx.compose.ui.text.style.TextAlign.Center)
            if(action!=null) Box(Modifier.padding(top=m.gap)) { action() }
        }
    }
}

/** TextFieldValue keeps caret and selection stable. Caller owns value and validation. */
@Composable
internal fun EditorTextField(value: TextFieldValue, onValueChange: (TextFieldValue) -> Unit, label: String,
    modifier: Modifier = Modifier, enabled: Boolean = true, readOnly: Boolean = false,
    invalid: Boolean = false, help: String? = null, placeholder: String = "",
    numeric: Boolean = false, onFocusChange: (Boolean) -> Unit = {},
    onCommit: () -> Unit = {}, onCancel: () -> Unit = {}, singleLine: Boolean = true,
    onEditKey: (KeyEvent) -> Boolean = { false },
    focusRequester: androidx.compose.ui.focus.FocusRequester? = null) {
    val c=LocalEditorColors.current
    val m=LocalEditorMetrics.current
    val t=LocalEditorTypography.current
    val source=remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    val hovered by source.collectIsHoveredAsState()
    val shape=RoundedCornerShape(m.controlRadius)
    val style=(if(numeric) t.numeric else t.body).copy(color=if(enabled) c.textPrimary else c.disabledText)
    // Shortcuts leave plain keys (Delete, arrows, letters) to a focused field.
    val textFocus=remember { Any() }
    DisposableEffect(textFocus) { onDispose { sle.editor.workspace.TextInputFocus.report(textFocus,false) } }
    EditorTooltip(help?.takeIf { it.isNotBlank() } ?: label,modifier) {
        BasicTextField(value,onValueChange,Modifier.fillMaxWidth().then(
            if (focusRequester == null) Modifier else Modifier.focusRequester(focusRequester))
            .onFocusChanged { sle.editor.workspace.TextInputFocus.report(textFocus,it.isFocused);onFocusChange(it.isFocused) }
            .onPreviewKeyEvent {
                if (!enabled || readOnly) false
                else if (onEditKey(it)) true
                else if (it.type != KeyEventType.KeyDown) false
                else when(it.key) { Key.Enter -> {if(singleLine) {onCommit();true} else false};Key.Escape -> {onCancel();true};else -> false }
            }
            .semantics { contentDescription=label;if(invalid) error("Enter a valid value") }
            .hoverable(source,enabled)
            .background(if(!enabled) c.disabledBackground else if(readOnly) c.surface else c.inputBackground,shape)
            .border(if(focused && !readOnly) m.focusBorderWidth else m.borderWidth,when {
                invalid -> c.error; focused && !readOnly -> c.focusRing
                !enabled || readOnly -> c.borderSubtle; hovered -> c.textSecondary; else -> c.border },shape)
            .heightIn(min=m.field).padding(horizontal=m.controlPadding-m.gap/2,vertical=m.controlVerticalPadding),
            enabled=enabled,readOnly=readOnly,singleLine=singleLine,interactionSource=source,
            textStyle=style,cursorBrush=SolidColor(c.accent),decorationBox={ inner ->
                Box(Modifier.fillMaxWidth(),contentAlignment=Alignment.CenterStart) {
                    if(value.text.isEmpty()) Text(placeholder,color=c.placeholderText,style=t.body,maxLines=1)
                    inner()
                }
            })
    }
}

/** No transaction cleanup on disposal: the entity owner survives dock/window recreation. */
@Composable
internal fun EditorNumberField(edit: NumberEdit, label: String, modifier: Modifier = Modifier,
    enabled: Boolean = true, readOnly: Boolean = false, help: String? = null,
    presentation: NumberPresentation? = null, authoredValue: Double = 0.0) {
    var hadFocus by remember(edit) { mutableStateOf(false) }
    val display = presentation?.displayText(edit.text, hadFocus) ?: edit.text
    var field by remember(edit) { mutableStateOf(TextFieldValue(display)) }
    if(field.text!=display) field=field.copy(text=display,selection=androidx.compose.ui.text.TextRange(display.length))
    var stepKey by remember(edit) { mutableStateOf<Key?>(null) }
    EditorTextField(field,{ if (it.text != field.text) edit.updateText(it.text,enabled && !readOnly); field=it },label,modifier,enabled,readOnly,edit.invalid,help,numeric=true,
        onFocusChange={focused ->
            if(enabled && !readOnly && focused!=hadFocus) {if(focused) edit.focus() else edit.blur()}
            if (!focused) stepKey = null
            hadFocus=focused
        },
        onCommit={edit.commit()},onCancel={ edit.cancel() },
        onEditKey = { event ->
            when {
                event.type == KeyEventType.KeyUp && event.key == stepKey -> {
                    stepKey = null
                    edit.commit()
                    true
                }
                presentation?.canStep == true && event.isAltPressed && event.type == KeyEventType.KeyDown &&
                    (event.key == Key.DirectionUp || event.key == Key.DirectionDown) -> {
                    val continuing = stepKey != null
                    if (!continuing || edit.active) {
                        val direction = if (event.key == Key.DirectionUp) 1 else -1
                        presentation.stepText(edit.text, direction)?.let { generated ->
                            if (!continuing) edit.blur()
                            stepKey = event.key
                            edit.updateText(generated, true)
                        }
                    }
                    true
                }
                else -> false
            }
        })
    if (presentation?.hasSlider == true) {
        var sliderEditing by remember(edit) { mutableStateOf(false) }
        EditorLabel(presentation.sliderLabel(authoredValue), style = LocalEditorTypography.current.secondary)
        EditorTooltip(help?.takeIf { it.isNotBlank() } ?: "Adjust $label", Modifier.fillMaxWidth()) {
            Slider(
                value = presentation.sliderPosition(authoredValue),
                onValueChange = { position ->
                    if (enabled && !readOnly && (!sliderEditing || edit.active)) {
                        presentation.sliderText(position)?.let { generated ->
                            if (generated.toDouble() != edit.text.toDoubleOrNull() || edit.invalid) {
                                if (!sliderEditing) edit.blur()
                                sliderEditing = true
                                edit.updateText(generated, true)
                            }
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = "$label slider" }
                    .onPreviewKeyEvent {
                        if (enabled && !readOnly && it.type == KeyEventType.KeyDown && it.key == Key.Escape) {
                            edit.cancel()
                            true
                        } else false
                    },
                enabled = enabled && !readOnly,
                onValueChangeFinished = {
                    if (sliderEditing && enabled && !readOnly) edit.commit()
                    sliderEditing = false
                },
            )
        }
    }
}

/**
 * Label/editor columns ([EditorMetrics.propertyLabelFraction]) on one 28dp row; below
 * [EditorMetrics.propertyStackThreshold] the label stacks so narrow docks remain editable.
 * Editors fill the value column.
 */
@Composable
internal fun EditorPropertyRow(label: String, modifier: Modifier = Modifier, help: String? = null,
    labelModifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit) {
    val m=LocalEditorMetrics.current
    val labelStyle=LocalEditorTypography.current.body
    val labelColor=LocalEditorColors.current.textSecondary
    BoxWithConstraints(modifier.fillMaxWidth()) {
        if(maxWidth<m.propertyStackThreshold) Column(verticalArrangement=Arrangement.spacedBy(m.gap/2)) {
            EditorTooltip(help ?: label,labelModifier) { Text(label,color=labelColor,style=labelStyle,maxLines=1,overflow=TextOverflow.Ellipsis) }
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(m.gap),verticalAlignment=Alignment.CenterVertically,content=content)
        } else Row(Modifier.fillMaxWidth().heightIn(min=m.propertyRow),verticalAlignment=Alignment.CenterVertically) {
            EditorTooltip(help ?: label,labelModifier.width(this@BoxWithConstraints.maxWidth*m.propertyLabelFraction)) {
                Text(label,Modifier.padding(end=m.panelPadding),color=labelColor,style=labelStyle,maxLines=1,overflow=TextOverflow.Ellipsis)
            }
            Row(Modifier.weight(1f),horizontalArrangement=Arrangement.spacedBy(m.gap),verticalAlignment=Alignment.CenterVertically,content=content)
        }
    }
}

/**
 * A form row: the label in a fixed-width column ([EditorMetrics.formLabel]) and the field
 * filling the rest at one field height, so every row of a form lines up. Narrow panels stack
 * the label above the field.
 */
@Composable
internal fun EditorFormRow(label: String, modifier: Modifier = Modifier, help: String? = null,
    content: @Composable RowScope.() -> Unit) {
    val m=LocalEditorMetrics.current
    val labelStyle=LocalEditorTypography.current.body
    val labelColor=LocalEditorColors.current.textSecondary
    BoxWithConstraints(modifier.fillMaxWidth()) {
        if(maxWidth<m.formLabel*2) Column(verticalArrangement=Arrangement.spacedBy(m.gap/2)) {
            EditorTooltip(help ?: label) { Text(label,color=labelColor,style=labelStyle,maxLines=1,overflow=TextOverflow.Ellipsis) }
            Row(Modifier.fillMaxWidth().heightIn(min=m.field),horizontalArrangement=Arrangement.spacedBy(m.gap),verticalAlignment=Alignment.CenterVertically,content=content)
        } else Row(Modifier.fillMaxWidth().heightIn(min=m.field),verticalAlignment=Alignment.CenterVertically) {
            EditorTooltip(help ?: label,Modifier.width(m.formLabel)) {
                Text(label,Modifier.padding(end=m.gap*2),color=labelColor,style=labelStyle,maxLines=1,overflow=TextOverflow.Ellipsis)
            }
            Row(Modifier.weight(1f),horizontalArrangement=Arrangement.spacedBy(m.gap),verticalAlignment=Alignment.CenterVertically,content=content)
        }
    }
}

/** Groups form rows under a heading; sections are separated by space, not boxes. */
@Composable
internal fun EditorSectionHeader(title: String, modifier: Modifier = Modifier) {
    val m=LocalEditorMetrics.current
    Text(title,modifier.fillMaxWidth().padding(top=m.gap,bottom=m.gap/2),color=LocalEditorColors.current.textPrimary,
        style=LocalEditorTypography.current.heading,maxLines=1,overflow=TextOverflow.Ellipsis)
}

@Composable
internal fun EditorVectorField(label: String, axes: List<Pair<String,NumberEdit>>, modifier: Modifier = Modifier,
    enabled: Boolean = true, readOnly: Boolean = false) {
    val m=LocalEditorMetrics.current
    EditorPropertyRow(label,modifier) {
        axes.forEach { (axis,edit) ->
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(m.gap)) {
                    EditorLabel(axis.uppercase(),Modifier.widthIn(min=m.gap*2),color=LocalEditorColors.current.textSecondary,
                        style=LocalEditorTypography.current.secondary,help="$label ($axis)")
                    EditorNumberField(edit,"$label $axis",Modifier.weight(1f),enabled,readOnly,help=axis)
                }
                if(edit.invalid) EditorLabel("$axis: Invalid number",color=LocalEditorColors.current.error,style=LocalEditorTypography.current.secondary)
            }
        }
    }
}

@Composable
internal fun EditorCheckBox(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier, enabled: Boolean = true, help: String? = null, showLabel: Boolean = true) {
    val c=LocalEditorColors.current
    val m=LocalEditorMetrics.current
    val source=remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    val focused by source.collectIsFocusedAsState()
    val shape=RoundedCornerShape(m.controlRadius)
    EditorTooltip(help?.takeIf { it.isNotBlank() } ?: label,modifier) {
        Row(Modifier.background(if(hovered && enabled) c.hoverBackground else Color.Transparent,shape)
            .then(if(focused) Modifier.border(m.focusBorderWidth,c.focusRing,shape) else Modifier)
            .toggleable(checked,source,null,enabled,Role.Checkbox,onCheckedChange)
            .semantics { contentDescription=label }
            .heightIn(min=m.field).padding(horizontal=m.gap),
            verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(m.gap+m.gap/2)) {
            Box(Modifier.size(m.iconSmall)
                .background(if(checked) (if(enabled) c.accent else c.disabledText) else c.inputBackground,shape)
                .then(if(checked) Modifier else Modifier.border(m.borderWidth,if(enabled) c.border else c.borderSubtle,shape)),
                contentAlignment=Alignment.Center) {
                if(checked) EditorGlyphIcon(EditorGlyph.Check,if(enabled) c.accentOnPrimary else c.disabledBackground,size=m.iconSmall)
            }
            if(showLabel) Text(label,color=if(enabled) c.textPrimary else c.disabledText,style=LocalEditorTypography.current.body,maxLines=1)
        }
    }
}

/**
 * Dropdown. With [showLabel] it is a secondary button reading "Label: value"; without,
 * it looks like an input showing the value and fills the property value column.
 */
@Composable
internal fun EditorSelect(label: String, value: String, choices: List<String>, onSelect: (String) -> Unit,
    modifier: Modifier = Modifier, enabled: Boolean = true, help: String? = null, showLabel: Boolean = true) {
    val c=LocalEditorColors.current
    val m=LocalEditorMetrics.current
    var expanded by remember { mutableStateOf(false) }
    val source=remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    val focused by source.collectIsFocusedAsState()
    val shape=RoundedCornerShape(m.controlRadius)
    Box(modifier) {
        EditorTooltip(help?.takeIf { it.isNotBlank() } ?: "$label: $value") {
            Row(Modifier.then(if(showLabel) Modifier else Modifier.fillMaxWidth())
                .background(when { !enabled -> c.disabledBackground;showLabel && hovered -> c.hoverBackground;showLabel -> c.surfaceRaised;else -> c.inputBackground },shape)
                .border(if(focused) m.focusBorderWidth else m.borderWidth,when { focused -> c.focusRing;!enabled || showLabel -> c.borderSubtle;hovered -> c.textSecondary;else -> c.border },shape)
                .clickable(source,null,enabled,role=Role.DropdownList) { expanded=true }
                .semantics { contentDescription=label }
                .heightIn(min=m.field).padding(start=m.controlPadding-m.gap/2,end=m.gap),
                verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(m.gap)) {
                Text(if(showLabel) "$label: $value" else value,if(showLabel) Modifier else Modifier.weight(1f),
                    color=if(enabled) c.textPrimary else c.disabledText,style=LocalEditorTypography.current.body,
                    maxLines=1,overflow=TextOverflow.Ellipsis)
                EditorGlyphIcon(EditorGlyph.ChevronDown,if(enabled) c.textSecondary else c.disabledText)
            }
        }
        DropdownMenu(expanded && enabled,onDismissRequest={expanded=false}) {
            choices.forEach { choice ->
                EditorTooltip(choice, Modifier.fillMaxWidth()) {
                    DropdownMenuItem(onClick={expanded=false;onSelect(choice)},
                        modifier=Modifier.heightIn(min=m.treeRow)) { Text(choice,style=LocalEditorTypography.current.body) }
                }
            }
        }
    }
}

/** Sub-panel heading: heading role on the panel surface with a bottom separator. */
@Composable
internal fun EditorPanelHeader(title: String, modifier: Modifier = Modifier, actions: @Composable RowScope.() -> Unit = {}) {
    val m=LocalEditorMetrics.current
    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().heightIn(min=m.sectionHeader).padding(horizontal=m.panelPadding),
            verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(m.gap)) {
            EditorTooltip(title,Modifier.weight(1f)) {
                Text(title,style=LocalEditorTypography.current.heading,maxLines=1,overflow=TextOverflow.Ellipsis)
            }
            actions()
        }
        EditorSeparator()
    }
}

/** Collapsible inspector section: the whole header row toggles; actions stay on the right. */
@Composable
internal fun EditorInspectorSection(title: String, expanded: Boolean, onExpandedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier, actions: @Composable RowScope.() -> Unit = {},
    headingModifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val c=LocalEditorColors.current
    val m=LocalEditorMetrics.current
    val source=remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    val focused by source.collectIsFocusedAsState()
    Column(modifier.fillMaxWidth()) {
        Row(headingModifier.fillMaxWidth().heightIn(min=m.sectionHeader)
            .background(if(hovered) c.hoverBackground else c.surfaceRaised)
            .then(if(focused) Modifier.border(m.borderWidth,c.focusRing) else Modifier)
            .toggleable(expanded,source,null,role=Role.Button,onValueChange=onExpandedChange)
            .semantics { stateDescription=if(expanded) "Expanded" else "Collapsed" }
            .padding(start=m.gap,end=m.gap),
            verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(m.gap)) {
            EditorGlyphIcon(if(expanded) EditorGlyph.ChevronDown else EditorGlyph.ChevronRight,c.textSecondary)
            EditorTooltip(if (expanded) "Collapse $title" else "Expand $title",Modifier.weight(1f)) {
                Text(title,style=LocalEditorTypography.current.heading,maxLines=1,overflow=TextOverflow.Ellipsis)
            }
            actions()
        }
        if(expanded) Column(Modifier.padding(horizontal=m.panelPadding,vertical=m.gap),
            verticalArrangement=Arrangement.spacedBy(m.gap/2),content=content)
    }
}

@Composable
internal fun EditorTreeRow(label: String, selected: Boolean, onClick: () -> Unit,
    modifier: Modifier = Modifier, depth: Int = 0, enabled: Boolean = true, help: String? = null,
    /** The active row of a multi-selection: an accent marker on its leading edge. */
    active: Boolean = false,
    leading: @Composable () -> Unit = {}) {
    val source=remember { MutableInteractionSource() }
    val c=LocalEditorColors.current
    val m=LocalEditorMetrics.current
    val hovered by source.collectIsHoveredAsState()
    val focused by source.collectIsFocusedAsState()
    EditorTooltip(help?.takeIf { it.isNotBlank() } ?: label,modifier) {
        Row(Modifier.fillMaxWidth()
            .background(when { selected -> c.selectedBackground;hovered && enabled -> c.hoverBackground;else -> Color.Transparent })
            .then(if(active) Modifier.drawBehind { drawRect(c.accent,size=size.copy(width=m.tabUnderline.toPx())) } else Modifier)
            .then(if(focused) Modifier.border(m.borderWidth,c.focusRing) else Modifier)
            .semantics { this.selected=selected }
            .clickable(source,null,enabled,role=Role.Button,onClick=onClick)
            .heightIn(min=m.treeRow)
            .padding(start=m.gap+m.treeIndent*depth.coerceAtLeast(0),end=m.gap),
            verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(m.gap)) {
            leading();Text(label,Modifier.weight(1f),color=if(enabled) c.textPrimary else c.disabledText,
                style=LocalEditorTypography.current.body,maxLines=1,overflow=TextOverflow.Ellipsis)
        }
    }
}
