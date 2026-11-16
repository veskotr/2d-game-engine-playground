# Visual polish pass (between sessions 5 and 6)

Requested 2026-11-16 by the user before session 6: a consistency and ergonomics
pass over the existing Compose/Swing editor UI. Layout, docking, persistence,
viewport rendering, project formats and command behavior stay unchanged.

## Audit (current code, before changes)

- One `controlFrame` in `ui/controls/EditorControls.kt` draws a 1dp `border`
  outline on every button, toggle, tab, tree row, checkbox and dock action, so
  every surface reads as an outlined push button.
- `separator` equals `border` (high-contrast); dock splitters fill 6dp with it.
- Panel headers use `h6` (20sp); inspector section titles likewise; panel content
  has no inner padding; Hierarchy shows a redundant "Scene Tree" header.
- `EditorSelect` repeats its label in the button ("Parent: Scene root ▾").
- Typography sizes live in `EditorTheme.kt` (h5/h6 literals) and in
  `EditorMetrics` (body/secondary); AWT fonts derive sizes separately. Handjet is
  bundled but unused.
- Dark surfaces are fairly green-saturated; green is also hover/pressed.
- Native grid/axes are drawn by the C++ editor bridge: out of scope here.

## Changes

1. Tokens: `EditorTypography` (body, secondary, heading, toolbar, brand) beside
   metrics; `EditorMetrics` gains dock header 28dp, toolbar button 26dp, input
   26dp, tree row 24dp, inspector row 28dp, radius 3dp, 4dp unit. AWT menus and
   dialogs size from the same body token.
2. Colors: more neutral surfaces, subtle `separator`, a `borderSubtle` for
   secondary controls; green reserved for selection, focus, active tab and
   primary actions. Existing contrast test keeps passing.
3. Components: `EditorButtonStyle` Primary/Secondary/Ghost; flat `EditorTab`
   with 2dp accent underline; `EditorDockHeader` actions as ghost icon buttons;
   tree rows flat (fill on hover/selection only); inspector section headers as
   flat chevron rows; `EditorSelect` value-only when inside a property row.
4. Surfaces: dock tabs/headers, viewport toolbar (Play primary, zoom/fit ghost),
   hierarchy (drop "Scene Tree"), inspector padding/headings, asset panel
   borders → separators, centered empty state, default assets split shorter.
5. Splitters: 1dp line in the existing 6dp hit slot, accent on hover/drag.

## Validation

`.\gradlew.bat test`, `.\gradlew.bat build`, the context-menu, project,
project-documents, asset-browser, docking-workspace and gizmo smokes; before/after
screenshots in both themes; UI scale 75/85/100% (OS DPI 125/150% cannot be
switched from the shell; record as unverified unless checked manually).

## Record

Status: IMPLEMENTED (2026-11-16). Automated checks, smokes and screenshots below.
Manual hover/drag feel and real 125%/150% Windows display settings are still open.

- Tokens: `ui/theme/EditorTypography.kt` (new): `EditorTypography`
  body/secondary/heading/toolbar/brand/numeric, `LocalEditorTypography`,
  `TextStyle.centeredLine()` (font ascent/descent line box, centered), and
  `editorAwtFont(density, uiScale, role)` for Swing. `EditorMetrics` compact
  values: dock header 28, button 26, field 26, tree row 24, property row 28,
  section 26, icon 16, `iconSmall` 14, radius 3, gap 4, `treeIndent`,
  `tabUnderline` 2dp. `EditorFonts.brand` (Handjet) and `awtMedium`.
  `EditorTheme` maps Material h5–caption onto these roles.
- Colors: neutral surfaces in both modes, subtle `separator`, new `borderSubtle`.
  Green is limited to accent, selection, focus, the active tab and Primary. The
  existing contrast test passes unchanged.
- Components (`ui/controls/EditorControls.kt`): `EditorButtonStyle` Primary,
  Secondary and Ghost, with distinct hover/pressed/selected/focus/disabled states;
  `EditorTab`; `EditorGlyph`/`EditorGlyphIcon`/`EditorGlyphButton` (drawn icons
  replace "_ ↗ × ⠿ ✓ □ ▾"); `EditorToolbar`, `EditorToolbarDivider`,
  `EditorSeparator`, `EditorEmptyState(brand=)`. Tree rows are flat. Inspector
  sections are full-row chevron toggles (no Collapse button). `EditorSelect(showLabel=false)`
  renders like an input that fills the value column. `EditorCheckBox(showLabel=)`
  draws a real box. Existing signatures are kept; `primary=` maps to Primary.
- Surfaces: flat dock headers with ghost actions; splitters draw a 1dp rule
  inside the unchanged 6dp hit slot, accent on hover/drag; header and restore-strip
  rules are drawn inside `DockChrome` heights, so minima are unchanged. The
  viewport toolbar is Play (Primary), Pause/Stop (Secondary), then zoom/Fit and
  Undo/Redo as Ghost, with a Tool select and dividers. Hierarchy no longer has the
  "Scene Tree" heading. The inspector is padded under a small Scene/Entity/Prefab
  heading, with humanized labels (`displayLabel`, keys unchanged) and Parent as a
  value-only select. Assets: no redundant heading, separators instead of boxes,
  ghost navigation, chevron breadcrumbs, centered empty folder and branded
  empty-project states. The default assets split is .65 → .74 (default layout only).
  Menus/context menus/dialogs share `MenuAppearance.of(...)` (subtle outline and
  separator, secondary-colored title).
- Fix found during DPI checks: Swing menu fonts previously used Compose density
  (OS DPI × UI scale) while Java2D also applies OS DPI, so menus were scaled
  twice at 125/150%. They now use the UI scale only.
- Smoke adjustments: the asset-browser and project-documents `click` helpers
  scroll lazy rows into their list before clicking. The shorter assets dock
  exposed stale-bounds clicks (3/6 asset-browser runs failed before the fix,
  3/3 passed after). `-Dsle.smokeTheme=Light` seeds the temporary smoke
  appearance store for light screenshots.
- Out of scope / unchanged: native C++ grid and axes (not dimmed); engine, file
  formats, docking persistence, command behavior.
- Default UI scale (user decision, same day): 85% → 100%, so 13sp body text
  renders at 13px at 100% DPI. Changed the defaults in `EditorAppearance`,
  the missing-key fallback in `EditorAppearanceStore`, `ProjectEditorState`
  (missing or unreadable project editor state) and `MenuAppearance`. Stored values
  are kept; 75/85/100% stay selectable and readable. Tests updated for the new
  defaults. Rerun: 187 tests pass, build passes, context-menu, project,
  project-documents, asset-browser and docking-workspace smokes pass. The gizmo
  smoke failed once ("Circle radius handle failed", a robot drag tolerance), then
  passed 3/3 reruns. Capture: `after/editor/editor-dark-100-scale100.png`.
- Commands (from `tools/entity-editor`): `.\gradlew.bat test` 187 tests, 0
  failures; `.\gradlew.bat build` successful. `--context-menu-smoke`,
  `--project-smoke`, `--docking-workspace-smoke` and `--gizmo-smoke` pass;
  `--project-documents-smoke` and `--asset-browser-smoke` pass 3/3 after the
  click fix. Logs: `build/polish-*.log`.
- DPI and theme: with `JAVA_TOOL_OPTIONS=-Dsun.java2d.uiScale=1.0|1.25|1.5` and
  `-Dsle.smokeTheme=Light`, captures at dark 100%, light 100%, dark 125% and
  light 150% show consistent baselines, tab alignment and single-scaled menus.
  The light runs stop at the smoke's dark-only menu-bar assertion, after the
  capture. The 125% run fails a later right-click step: the smoke combines
  Compose pixel bounds with logical Robot coordinates. That is a smoke DPI
  limitation, recorded but not fixed here.
- Screenshots: `build/reports/polish/before/` and `build/reports/polish/after/`
  (`theme/`, `inspector/`, `editor/editor-{dark-100,light-100,dark-125,light-150}.png`).

### Follow-up: dialogs behind the editor and system-looking dialogs (2026-11-16)

User report: some dialogs and file explorers looked like default OS UI, and some
dialogs appeared behind the main window.

- Cause (behind): every editor dialog and chooser was opened with a `null` parent,
  so Swing owned it with its hidden shared frame, not the editor window; Windows
  could then stack it behind the editor. The session-5 auxiliary smoke passed an
  explicit owner, so it never exercised this path.
- Cause (OS look): Compose Desktop installs the system look-and-feel for all of
  Swing. The scoped adapter only recolored parts, leaving the Windows chooser
  (places bar, shell icons, XP toolbar glyphs), scrollbars, table headers,
  option-pane icons, combo arrows and white native title bars.
- Fix: `EditorDialogOwner.resolve` (active editor window, else the main window
  registered in `Main.kt`) is used by `EditorDialogs` and `EditorFileChooser`.
  `EditorFileChooserUI` (Metal layout + `EditorAssetIcon`/`EditorActionIcon`)
  replaces the system chooser UI only for editor choosers; filter and
  approve/cancel semantics match `JFileChooser` (tested side by side).
  Scoped `EditorScrollBarUI`, table header renderer, scroll-pane border, flat
  combo chevron and editor message icons. `EditorWindowChrome` (LWJGL
  `DwmSetWindowAttribute`; HWND from `ComposeWindow`/`ComposeDialog.windowHandle`
  or JAWT) themes captions for all windows on open and on theme switch.
  No `UIManager` writes, new dependencies or native rebuilds.
- Coverage: `EditorDialogAppearanceTest` adds chooser UI/filter parity, file-kind
  icons, message icons, COLORREF and explicit-parent tests (191 tests, 0
  failures). The auxiliary smoke now opens dialogs with `null` parents and asserts
  the editor-window owner, `EditorFileChooserUI`, no places bar, editor scrollbars,
  editor message icon and the Windows-reported dark-caption flag per theme. The
  project smoke asserts Project Settings is owned by the editor window and its
  caption follows the theme.
- Commands: `.\gradlew.bat test`, `.\gradlew.bat build`; `--context-menu-smoke`,
  `--project-smoke`, `--project-documents-smoke`, `--asset-browser-smoke`,
  `--docking-workspace-smoke`, `--gizmo-smoke`: all pass (logs
  `build/dialogs-*.log`). Captures: `build/reports/session5-{dark,light}-*.png`;
  the main window caption samples equal to the menu-bar surface.
- Remaining: drive/computer icons in the chooser's Look-in list are OS shell
  icons; captions on Windows versions before 10 2004 stay native; not checked
  by hand on a second monitor or with multiple detached windows open.

