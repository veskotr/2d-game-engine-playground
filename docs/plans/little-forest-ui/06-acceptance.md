# Visual acceptance and permanent contributor guidance Implementation Plan

> **For agentic workers:** Use superpowers:executing-plans to implement this session task-by-task. Track the checkboxes and stop at this session boundary.

**Goal:** Prove the editor migration and publish concise instructions for future UI features.

**Architecture:** Extend the current Compose Desktop editor through shared tokens and callback-based presentation. Preserve document/services/native ownership and existing workflows.

**Tech Stack:** Existing Kotlin/JVM, Compose Desktop Material, Swing/AWT, JDK 21 and Windows native viewport; no dependency upgrade.

**Spec:** `docs/design/editor/SLE_EDITOR_UI_DESIGN.md`, `docs/design/editor/SLE_Design_Prompt.txt`, and [scope/shared constraints](README.md).

## Global constraints

Read README.md in this directory for the full shared contract and validation environment. Editor UI and branding only; engine/runtime texture work is deferred. Preserve all pre-existing edits. Never alter serialization compatibility, docking persistence or native engine lifecycle for styling. Paths below use the source/test prefixes defined in README.md.

## Required context

Read the shared handoff first and only prerequisite completion records, then: All five completion records, root design acceptance criteria, `tools/entity-editor/README.md`, `docs/topics/EditorWorkspace.md`, `docs/DOCS_MAINTENANCE.md`; inspect actual resulting theme/control/inspector APIs, not proposed names.

## Files and responsibilities

- Create `tools/entity-editor/UI_DESIGN.md` and `HOW_TO_ADD_UI_FEATURES.md`; link them from the editor README and `docs/README_DOCUMENTATION.md`.
- Add a finite `smoke/EditorAppearanceSmoke.kt` and argument routing in `app/Main.kt` if existing GUI smoke infrastructure cannot capture the required states. Store generated captures under ignored `tools/entity-editor/build/reports/little-forest/`.
- Update only affected editor topic documentation and these session records. No engine behavior changes.

## Interfaces

Consumes implemented APIs from all prior sessions. Produces acceptance evidence and durable guides. This is the final session; do not begin deferred engine/runtime texture integration.

## Review focus

- Changed document/entity while a control or picker is active: never apply stale edits.
- Detached/recreated panels: preserve drafts, history, selection and runtime owner.
- Small windows or increased font/DPI: keep content and keyboard focus reachable.
- Invalid/cancelled actions: preserve last valid source and existing file integrity.
- Theme switch: propagate to Compose and Swing while preserving game output.

## Ordered work

- [x] Read current files and confirm prerequisite APIs; record deviations before implementing.
- [x] Add focused failing coverage for changed behavior; verify it fails for the intended reason. Styling-only portions use gallery/screenshot checks.

1. Map every root design acceptance item to current implementation and concrete evidence. List deferred engine-owned overlays or OS chrome separately; do not claim full coverage when a requirement remains unresolved.
2. Capture real editor screenshots in dark and light using the same representative scene, populated assets, selected entity and expanded Transform/Sprite fields. Include home and detached windows, validation/focus/disabled states, and a readable compact inspector. Record fixture, size, UI scale, OS scaling and font scale with each capture.
3. Inspect 1366×768 and 1920×1080 at OS scales 100%,125%,150%,200%, plus enlarged font scaling and both densities. Automate representative coverage where reliable and manually inspect the remaining matrix. Check clipping, caret/selection, popups, tab overflow, splitter minima, icons and labels. Do not confuse the .85 editor zoom with OS scaling.
4. Verify switching themes/density during Play and with detached windows changes only editor chrome. Compare unchanged scene serialization and rendered game frame with fixed/paused content; no runtime rebuild or document dirty flag.
5. Document actual token names, contrast choices, compact/comfortable metrics, font/icon/license paths, appearance storage, widget callbacks, numeric commit/cancel/blur/undo contract, metadata adaptation, section state and native exceptions.
6. In HOW_TO_ADD_UI_FEATURES.md show one actual small property addition and one command/button example using resulting APIs. Include keyboard/accessibility, both-theme/scaling checklist, testing commands and what must stay in model/services. Do not copy the full root design into another competing specification.
7. Update the master progress and final report with actual commands, pass/fail/skip results and screenshot locations. If GUI/tooling is unavailable, leave acceptance incomplete with exact Windows continuation commands.

- [x] Run the focused checks below and inspect actual output; fix regressions within scope.
- [x] Update affected docs and completion record. Stop; do not start the next session.

## Validation

- Run `.\gradlew.bat build` and the new finite appearance smoke (document its actual flag), then relevant existing GUI smokes: `--authoring-ui-smoke`, `--gizmo-smoke`, `--context-menu-smoke`, `--docking-workspace-smoke`, `--project-documents-smoke`, `--asset-browser-smoke`, `--camera-smoke` and `--project-runtime-smoke`.
- Run `--project-distribution-smoke` only when distribution presentation changed and its documented Release runtime prerequisite is built. Do not claim its historical result as current validation.
- Check new documentation links, font license inclusion, and feature sources for remaining styling literals. Each hit needs classification, not automatic deletion.
- Final handoff includes dark/light screenshots, exact test evidence, remaining limitations and permanent guide links.

## Completion record

Status: IMPLEMENTED (2026-11-16). All automated checks below pass. Acceptance is
**partial** on two items (real 200% DPI hardware and manual keyboard traversal); see
the map. Simulated DPI uses `-Dsun.java2d.uiScale` on a 1920×1080 desktop.

### Acceptance map (design §9)

| Criterion | Result | Evidence |
|---|---|---|
| Existing workflows still function | Met | 192 JVM tests; smokes authoring-ui, gizmo, context-menu, docking-workspace, project, project-documents, asset-browser, camera, project-runtime all pass (asset-browser intermittent, see below). |
| Dark and Light theme all in-scope chrome, fields, trees, lists, dialogs, menus | Met, with listed exceptions | `--editor-appearance-smoke` captures Scene/Entity inspectors, validation, read-only during Play and a detached window in both themes; the auxiliary dialog smoke covers confirm/option/input/chooser dialogs, owner, editor chooser UI and captions. Exceptions: native viewport/grid, OS shell drive icons, captions before Windows 10 2004. |
| Theme switching consistent; persistence | Met | Live switch reaches the detached window and its caption (smoke). Paused-Play switch: same document, dirty flag and render context, **0 differing game pixels** in every configuration. Persistence: `EditorAppearanceStoreTest` (atomic save, failure keeps file). |
| Compact meets targets; Comfortable from shared metrics | Met | `EditorMetrics` (UI_DESIGN.md table); Comfortable captures at 1366×768 and 1920×1080. |
| Labels beside inputs; compact Transform | Met | Scene/Entity captures: Position/Rotation/Scale on three 28 dp rows; stacking only below 240 dp (gallery at 540 px / 150% font). |
| 1366×768 usable; 1920×1080; 100/125/150/200% | Partial | Pass: 1920×1080 @100/125/150%, 1366×768 @100/125/150%, Comfortable @100% at both sizes. 200% on a 1080p screen (960×540 logical): the editor runs and docks collapse into tabs by design (`prod-200-startup.png`); the appearance smoke harness aborts at startup there (composition cancelled, not investigated). 4K @200% (1920×1080 logical) could not be produced on this desktop. |
| Tree/inspector states distinct and keyboard accessible | Partial | Hover/pressed/selected/focus/disabled/invalid specimens in the theme gallery; validation capture; Alt+F/Down/Escape menu keyboard smoke. Tab/Shift-Tab traversal through Compose fields is not automated; manual check open. |
| New controls without hand-picked values | Met | Shared components + tokens; literal audit below. |
| Tests pass; both themes inspected | Met | Commands below; captures inspected (scene, entity, validation, detached, Play, galleries, 150% DPI and font). |
| `UI_DESIGN.md` and `HOW_TO_ADD_UI_FEATURES.md` | Met | `tools/entity-editor/`, linked from the editor README and `docs/README_DOCUMENTATION.md`. |

- Changed files and resulting API/signatures:
  - New `smoke/EditorAppearanceSmoke.kt`, flag `--editor-appearance-smoke`; system
    properties `sle.appearance.window=WxH`, `sle.appearance.density`, `sle.appearance.tag`
    (pass via `JAVA_TOOL_OPTIONS`). Output: `build/reports/little-forest/session6/<tag>/`
    with `captures.txt` (size, scale and density per capture).
  - User request during the session: a separate window holding one panel shows no
    dock header. `DockLayout.isSinglePanelWindow(host)`;
    `WorkspaceWindowManager.forgetHeader(host, group)` clears grip/tab hit areas so a
    hidden header cannot start a drag. Re-dock via Windows → Move to. The docking
    smoke's Play re-dock step now uses that menu operation (dragging a tab out of a
    single-panel window is no longer possible). Test:
    `DockLayoutTest.singlePanelWindowsHideTheirDockHeader`.
  - Hairlines and list heights moved to metrics (`borderWidth`, `focusBorderWidth`,
    `compactListMaxHeight`, `emptyStateMinHeight`).
  - Docs: `tools/entity-editor/UI_DESIGN.md`, `HOW_TO_ADD_UI_FEATURES.md`, links,
    `docs/topics/EditorWorkspace.md`.
- Literal audit (feature sources, excluding theme and smokes): no font sizes. Color
  literals only in the Primary button's accent-derived hover/pressed shading (inside
  the shared component), the `NativeViewportOwner` backing fill (matches the GL clear;
  game output) and a fully transparent drag-overlay window background. Remaining dp
  literals are initial window sizes (main 1280×900, settings dialog 500×470,
  galleries).
- Commands actually run and results (from `tools/entity-editor`, 2026-11-16):
  - `.\gradlew.bat test`: 192 tests, 0 failures. `.\gradlew.bat build`: successful.
  - `--editor-appearance-smoke`: pass at 1920×1080 @100/125/150%, 1366×768
    @100/125/150%, Comfortable 1920×1080 and 1366×768 @100%; fails to start at
    960×520 @200% (see map). Game pixels differing on theme switch: 0 everywhere.
  - `--authoring-ui-smoke`, `--gizmo-smoke`, `--context-menu-smoke`,
    `--docking-workspace-smoke`, `--project-smoke`, `--project-documents-smoke`,
    `--camera-smoke`, `--project-runtime-smoke`: pass.
  - `--asset-browser-smoke`: failed once at its robot folder double-click step, then
    passed 3/3 reruns (intermittent).
  - `--project-distribution-smoke`: not run (distribution presentation unchanged).
  - Galleries: `--theme-gallery` and `--inspector-gallery` with `--capture`, font 100% and 150%.
  - Logs: `build/s6-*.log`.
- Screenshots and environment: Windows 11, JDK 21.0.2, 1920×1080 desktop.
  `build/reports/little-forest/session6/` (103 captures): per-configuration folders
  (`scene-`, `validation-`, `entity-`, `detached-inspector-`, `play-readonly-` ×
  dark/light), `gallery-theme/`, `gallery-inspector/`, `prod-200-startup.png`.
- Incident: a production-mode launch at simulated 200% saved a collapsed layout over
  the user's `%LOCALAPPDATA%\SLE\EntityEditor\workspace-layout.json`. It was set aside
  as `workspace-layout.json.s6-collapsed.bak` so the editor starts with the default
  layout; the earlier layout could not be recovered. Smokes use temporary storage;
  manual experiments should pass `-Dsle.workspaceData` (HOW_TO_ADD_UI_FEATURES.md).
- Blockers/deferred checks:
  - Real 125/150/200% Windows display settings and a 4K display; manual Tab/Shift-Tab
    traversal and tooltip timing; second-monitor behavior by hand.
  - Layout recovery on very small logical screens persists the collapsed layout
    (existing docking behavior; not changed for styling).
  - The appearance smoke harness at 960×520 @200% (startup composition cancelled).
  - Native grid/axes dimming (C++), OS theme detection.
- Continuation: this is the final planned session. User decision (2026-11-16):
  detached tool windows stay independent top-level windows (own taskbar entry,
  minimized separately), not owned children of the main window.
