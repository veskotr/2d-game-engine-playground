# SLE editor UI design system (Little Forest)

The implemented reference for the editor's look and behavior. The original brief is
[`SLE_EDITOR_UI_DESIGN.md`](../../SLE_EDITOR_UI_DESIGN.md); this file records what the
code actually provides. To add UI, follow [HOW_TO_ADD_UI_FEATURES.md](HOW_TO_ADD_UI_FEATURES.md).
Paths are relative to `src/main/kotlin/sle/editor/`.

## Principles

- Feature code consumes **tokens and shared components**; it never chooses hex
  colors, font sizes, control heights, paddings or hover styles.
- Compose Desktop UI and the Swing pieces (menus, dialogs, file choosers) read the
  **same tokens**. Swing styling is scoped to editor-owned roots; nothing writes
  global `UIManager` defaults.
- Brand green marks selection, focus, the active tab and primary actions. Large
  surfaces stay neutral. Theme never changes game pixels or document data.

## Tokens (`ui/theme/`)

| Source | Provides |
|---|---|
| `EditorTokens.kt` → `EditorColors`, `editorColors(mode)`, `LocalEditorColors` | The only palette. `background`, `surface`, `surfaceRaised`, `inputBackground`, `viewportBackground`, `textPrimary`, `textSecondary`, `border`, `borderSubtle`, `separator`, `accent`, `accentOnPrimary`, `selectedBackground`, `hoverBackground`, `pressedBackground`, `focusRing`, `disabledText`, `disabledBackground`, `placeholderText`, `error`/`warning`/`success`/`info` (+ `…Background`), `tooltipBackground`/`tooltipText`, viewport overlay tokens, `selectionOutline`. |
| `EditorMetrics.kt` → `editorMetrics(density)`, `LocalEditorMetrics` | Heights, spacing, radii, hairlines, icon sizes and responsive breakpoints. |
| `EditorTypography.kt` → `editorTypography(metrics)`, `LocalEditorTypography`, `editorAwtFont(...)` | Text roles `body`, `secondary`, `heading`, `toolbar`, `brand`, `numeric`; Swing fonts from the same roles. |
| `EditorFonts.kt` | Bundled Roboto (UI) and Handjet (brand only). |
| `EditorAppearanceState.kt` / `EditorAppearanceStore.kt` | `EditorAppearance(mode, density, scale)` and its persistence. |
| `ui/EditorTheme.kt` → `EditorTheme(appearance)` | Provides all locals plus a Material theme mapped onto the same roles. Every window root wraps its content in it. |

**Contrast** is enforced by `EditorAppearanceTest.requiredContrastPairs`: every text
token ≥ 4.5:1 on every surface, hover, pressed, selected and disabled background;
`border` and `focusRing` ≥ 3:1; `accent`/`accentOnPrimary` and each status pair ≥
4.5:1. `borderSubtle` and `separator` are deliberately low contrast and never the
only identifier of a control. Starting hex values from the brief were adjusted at the
token source: surfaces are more neutral, `textSecondary`, `border` and disabled text
are stronger.

### Metrics (Compact / Comfortable)

| Metric | Compact | Comfortable |
|---|---|---|
| Dock/tab header `toolbar` | 28 dp | 34 dp |
| Toolbar/command `button` | 26 dp | 32 dp |
| Input/select `field` | 26 dp | 30 dp |
| `treeRow` (hierarchy, assets) | 24 dp | 28 dp |
| Inspector `propertyRow` | 28 dp | 34 dp |
| Section header `sectionHeader` | 26 dp | 30 dp |
| `panelPadding` / `gap` | 8 / 4 dp | 10 / 6 dp |
| `icon` / `iconSmall` | 16 / 14 dp | 18 / 14 dp |
| Body / secondary / heading text | 13 / 12 / 13 sp | 14 / 13 / 14 sp |
| `controlRadius`, `borderWidth`, `focusBorderWidth`, `tabUnderline` | 3, 1, 2, 2 dp | same |

Heights are minima: controls grow with OS font scaling. UI scale (75/85/**100%**
default) multiplies density once; OS DPI is applied by Compose/Java2D, never again
by editor code. Swing fonts use the UI scale only because Java2D applies OS DPI.

### Typography

Roboto Regular/Medium for every functional label, field and menu; monospace only for
numeric fields (`numeric`). Handjet (`brand`) is reserved for branding, today the
empty-project state. Text uses `TextStyle.centeredLine()`: the line box comes from
the font's ascent/descent and is centered, so labels in buttons, tabs, fields and rows
share one baseline. Feature code never sets `fontSize`.

## Components (`ui/controls/EditorControls.kt`)

| Component | Use |
|---|---|
| `EditorButton(label, onClick, style=…)` | Commands. `EditorButtonStyle.Primary` (filled green, the main action of a surface), `Secondary` (default; restrained fill + subtle outline), `Ghost` (no resting chrome: toolbars, zoom, navigation, dock actions). `primary=true` maps to Primary. |
| `EditorToggle`, `EditorIconButton`, `EditorGlyphButton` + `EditorGlyphIcon(EditorGlyph.*)` | Toggles and icon-only commands; glyphs are drawn, never text symbols. |
| `EditorTab` | Flat tab: active = surface fill + 2 dp accent underline. |
| `EditorToolbar`, `EditorToolbarDivider`, `EditorSeparator` | Command strips and 1-hairline separators. |
| `EditorTextField`, `EditorNumberField`, `EditorVectorField` | Text and numeric input (see contract below). |
| `EditorCheckBox(showLabel=)`, `EditorSelect(showLabel=)` | Booleans and dropdowns; inside a property row pass `showLabel=false`. |
| `EditorPropertyRow` | Label column (`propertyLabelFraction`, 36%) + editor column that fills; stacks below `propertyStackThreshold`. |
| `EditorInspectorSection` | Collapsible section; the whole header toggles; trailing action slot. |
| `EditorPanelHeader`, `EditorTreeRow`, `EditorTooltip`, `EditorEmptyState` | Sub-panel headings, tree/list rows, tooltips, centered empty panels. |

Every interactive control renders **normal, hover, pressed, focused (independent 2 dp
ring), selected, disabled and invalid** from tokens. Dock chrome (`workspace/DockHost.kt`)
uses `EditorTab`, a drawn grip and ghost Minimize/Detach/Hide buttons. A separate window
holding a single panel shows **no dock header** (`DockLayout.isSinglePanelWindow`): its
native caption names, minimizes and closes it (close hides the panel), and each panel
exists once. Re-dock it with **Windows → <panel> → Move to** or **Restore Default
Layout**; it has no tab to drag. Splitters draw one hairline inside a 6 dp hit slot and
highlight on hover/drag.

### Numeric editing contract

Inspectors obtain `state.numberEdit(component, path, NumberOptions(min, max))`, an
owner-cached `NumberEdit` that survives dock/window recreation. Valid text previews
live; **Enter** or valid blur finishes one undo transaction; **Escape** restores the
value from when editing started; invalid text (`-`, `1e`) never reaches the document and
shows an error until corrected. Entered precision is preserved in JSON. Do not create a
second numeric lifecycle.

### Inspector metadata and section state

`ui/inspector/EditorPropertyDefinition.kt` classifies authored JSON plus the native
component catalog (`ComponentDefinition(defaults, enums)`) into `PropertyKind` (Boolean,
String, Number, Enum, Vector, Resource, Json); `EditorPropertyRenderer` draws each kind
with the shared components. Labels display `displayLabel` (`linearDamping` → "Linear
damping"); keys are unchanged. Section expansion lives in
`EntityWorkspaceState.inspectorSections`, keyed by document identity and component, for
the session.

## Swing surfaces (`ui/`)

- Menus: `EditorMenuStrip`, `EditorContextMenu`, `MenuAppearance.of(mode, density, scale)`.
- Dialogs: `EditorDialogs.showConfirmDialog / showMessageDialog / showOptionDialog /
  showInputDialog` (same results as `JOptionPane`) and `EditorFileChooser` (same filters
  and approve/cancel semantics as `JFileChooser`, rendered by `EditorFileChooserUI`).
  Pass `null` or a component as parent: `EditorDialogOwner` makes the active editor
  window the owner, so dialogs open in front of it.
- `EditorDialogAppearance` styles registered roots (fields, buttons, combos, lists,
  tables, headers, scrollbars) and refreshes them on theme change.
- `EditorWindowChrome` themes native captions on Windows (dark mode on Windows 10 2004+,
  caption/text/border colors on Windows 11) for every editor window, best effort.

## Appearance persistence and switching

`appearance.json` (user-local, beside the standalone layout; `-Dsle.workspaceData`
overrides the folder) stores mode, density and scale, written atomically; a failed save
keeps the previous file and shows a notice. **View → Appearance / Density / UI Scale**
apply live to every window without restarting or recreating the runtime. The
`--editor-appearance-smoke` proves a switch during paused Play leaves the scene
document, dirty flag, render context and game pixels unchanged.

## Known exceptions (not themed by the editor)

- Native viewport content, grid and axes (C++ editor bridge). `NativeViewportOwner`'s
  backing fill matches the GL clear color. Game pixels are never themed.
- Native window captions on Windows before 10 2004; caption colors before Windows 11.
- Drive/computer icons in file choosers come from the OS shell.
- System/OS-preference theme detection is not implemented (Dark/Light only).
- At very small logical screens (for example 1080p at 200%, 960×540) docks that cannot
  meet their minimum sizes collapse into tabs by design; that layout is then saved.

## Scene creation and deletion

Hierarchy, viewport context menus and Inspector Delete actions share
`ui/SceneCommands.kt`'s guarded deletion command. Keep key handlers local to the
Hierarchy or native canvas so text fields retain Delete. Deletion clears selection;
Scene Undo restores the subtree. Native input remains gated by stopped Scene authoring.
Viewport creation captures a world point before opening `sceneCreationMenu`; child
placement is converted in `SceneWorkspaceState`. `model/EditorCreationDefaults.kt`
provides authoring sizes when adding components, never when parsing documents.
