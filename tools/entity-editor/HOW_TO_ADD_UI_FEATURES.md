# How to add editor UI features

Short recipes for adding UI that automatically matches the Little Forest design system.
Read [UI_DESIGN.md](UI_DESIGN.md) for the token and component reference. Paths are relative
to `src/main/kotlin/sle/editor/`.

## Rules

- **Do:** use `LocalEditorColors`, `LocalEditorMetrics`, `LocalEditorTypography` and the
  components in `ui/controls/EditorControls.kt`; extend those when a genuinely new
  interaction is needed (and add it to the theme gallery).
- **Don't:** write `Color(0x…)`, `fontSize = …`, ad hoc heights/paddings, per-feature
  hover logic, light/dark `if` branches, or raw `JOptionPane.show…`/`JFileChooser`.
- Views emit intents and observe state. Serialization, file I/O, Lua/runtime state,
  renderer lifetime and undo stay in `model/`, `project/` and services. A widget never
  owns them.
- No decorative controls: if a capability does not exist yet, omit the control or show it
  disabled with a tooltip.

## Example 1: a property in an inspector

Native component fields need **no UI code**: `inspectorProperties` reads the catalog and
authored JSON and `EditorPropertyRenderer` picks the editor (bool, number, enum, vector,
resource, JSON). Add a field to the C++ component and its defaults, and it appears with
correct styling, validation and undo.

For an editor-side row, compose a `EditorPropertyRow`. The real Scene inspector
(`ui/SceneWorkspace.kt`) does exactly this for the entity name and parent:

```kotlin
EditorPropertyRow("Name", help = "Entity name") {
    InspectorTextInput(selected.document.name, "Entity name", { selected.rename(it) }, readOnly = !editable)
}
EditorPropertyRow("Parent", help = "Parent entity") {
    EditorSelect("Parent", currentParent, parentChoices, { choice -> attempt { state.reparent(key, choiceKey(choice)) } },
        Modifier.weight(1f), enabled = editable, showLabel = false)
}
```

A numeric pair uses the owner-cached numeric handle, so Enter/Escape/blur and undo work
without extra code:

```kotlin
EditorVectorField("Offset", listOf(
    "x" to state.numberEdit("BoxCollider", listOf("offset", "x")),
    "y" to state.numberEdit("BoxCollider", listOf("offset", "y")),
), readOnly = readOnly)
```

Group rows in `EditorInspectorSection(title, expanded, onExpandedChange, actions = { … })`
and keep expansion in `state.inspectorSections`, not in a local `remember`.

## Example 2: a command button, menu entry and confirmation

The viewport toolbar (`app/Main.kt`, `viewportPanel`) shows the pattern: one strip, Primary
only for the main action, Ghost for frequent tools, dividers between groups.

```kotlin
EditorToolbar {
    SceneViewControls(scenePlayback, enabled, onPlay = { … }, onStop = { … })   // Play is Primary
    EditorToolbarDivider()
    EditorGlyphButton("Zoom in", { panelCanvas?.zoomEntity(.8f) }) { tint -> EditorGlyphIcon(EditorGlyph.Plus, tint) }
    EditorButton("Fit", { panelCanvas?.fitEntity() }, help = "Fit content", style = EditorButtonStyle.Ghost)
}
```

Expose the same command in the menu bar with an `EditorMenuEntry` in
`editorApplicationMenus` (`ui/EditorMenuStrip.kt`), for example
`EditorMenuEntry("Fit", editable, action = onFit)`, so it gets keyboard access, disabled
state and themed Swing rendering for free.

Confirm destructive actions with the shared dialogs. Pass `null`; the active editor window
becomes the owner:

```kotlin
if (EditorDialogs.showConfirmDialog(null, "Delete $name?", "Delete project asset",
        JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE) != JOptionPane.YES_OPTION) return
```

Use `EditorFileChooser(folder)` instead of `JFileChooser`. Empty panels use
`EditorEmptyState(title, message) { optional action }`.

## Keyboard and accessibility

- Every clickable must be focusable through the shared components (they set roles,
  `contentDescription`, and a focus ring independent of selection).
- Give icon-only buttons a label (it becomes the tooltip and accessible name).
- Do not rely on green alone: errors use `error`, warnings `warning`, plus text.
- Labels stay horizontal; long text ellipsizes with a tooltip rather than clipping.

## Checklist before merging a UI change

- [ ] Dark and Light: open View → Appearance and check hover, focus, disabled, invalid.
- [ ] Compact and Comfortable density; UI scale 75/85/100%.
- [ ] A narrow dock (inspector < 240 dp stacks labels) and a detached window.
- [ ] Keyboard: Tab/Shift-Tab, Enter/Escape in fields, Alt menu mnemonics.
- [ ] Undo/redo and file integrity unchanged; no document dirty flag from presentation.
- [ ] New reusable pattern? Add it to `ui/theme/EditorThemeGallery.kt` and UI_DESIGN.md.

## Testing commands (from `tools/entity-editor`)

```powershell
.\gradlew.bat test
.\gradlew.bat build
.\gradlew.bat run --args=--theme-gallery            # interactive controls fixture
.\gradlew.bat run --args=--inspector-gallery        # inspector fixture
.\gradlew.bat run --args=--editor-appearance-smoke  # both themes, states, detached, Play
.\gradlew.bat run --args=--context-menu-smoke       # menus, dialogs, keyboard mnemonics
```

Galleries accept `--capture <dir>` for screenshots. `gradlew run` does not forward `-D`
options, so pass JVM properties through `JAVA_TOOL_OPTIONS`:

```powershell
$env:JAVA_TOOL_OPTIONS='-Dsun.java2d.uiScale=1.5 -Dsle.appearance.window=1280x693 -Dsle.appearance.density=Comfortable -Dsle.appearance.tag=my-check'
.\gradlew.bat run --args=--editor-appearance-smoke
Remove-Item Env:JAVA_TOOL_OPTIONS
```

Captures land in `build/reports/little-forest/session6/<tag>/` with `captures.txt`.
Smokes use temporary layout and appearance storage. For manual experiments add
`-Dsle.workspaceData=<temp dir>` so your real layout and appearance are not changed.
