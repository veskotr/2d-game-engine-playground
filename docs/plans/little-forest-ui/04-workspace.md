# Compact workspace, trees and asset browser Implementation Plan

> **For agentic workers:** Use superpowers:executing-plans to implement this session task-by-task. Track the checkboxes and stop at this session boundary.

**Goal:** Apply shared density and interaction patterns to existing docks, toolbar, hierarchy and assets.

**Architecture:** Extend the current Compose Desktop editor through shared tokens and callback-based presentation. Preserve document/services/native ownership and existing workflows.

**Tech Stack:** Existing Kotlin/JVM, Compose Desktop Material, Swing/AWT, JDK 21 and Windows native viewport; no dependency upgrade.

**Spec:** Root `SLE_EDITOR_UI_DESIGN.md`, `SLE_Design_Prompt.txt`, and [scope/shared constraints](README.md).

## Global constraints

Read README.md in this directory for the full shared contract and validation environment. Editor UI and branding only; engine/runtime texture work is deferred. Preserve all pre-existing edits. Never alter serialization compatibility, docking persistence or native engine lifecycle for styling. Paths below use the source/test prefixes defined in README.md.

## Required context

Read the shared handoff first and only prerequisite completion records, then: Sessions 1–3 completion records; `docs/topics/EditorWorkspace.md`; `app/Main.kt`, `workspace/DockChrome.kt`, `DockHost.kt`, `DockSizing.kt`, `WorkspacePanels.kt`, `ui/SceneTree.kt`, `EntityOutline.kt`, `SceneViewControls.kt`, `GizmoControls.kt`, `project/assets/AssetBrowserPanel.kt`.

## Files and responsibilities

- Modify the audited chrome/tree/toolbar/asset panel files and `ui/TreeIcons.kt`. Keep `WorkspaceWindowManager`, layout codec and native viewport lifecycle intact unless a proven styling integration requires a narrow change.
- Extend `workspace/DockSizingTest.kt`, `ui/SceneTreeTest.kt`, and asset selection tests only for changed observable behavior.

## Interfaces

Consumes shared rows/headers/metrics. Produces compact workspace surfaces without a layout format migration. Record any native-owned grid token limitation explicitly.

## Review focus

- Changed document/entity while a control or picker is active: never apply stale edits.
- Detached/recreated panels: preserve drafts, history, selection and runtime owner.
- Small windows or increased font/DPI: keep content and keyboard focus reachable.
- Invalid/cancelled actions: preserve last valid source and existing file integrity.
- Theme switch: propagate to Compose and Swing while preserving game output.

## Ordered work

- [x] Read current files and confirm prerequisite APIs; record deviations before implementing.
- [x] Add focused failing coverage for changed behavior; verify it fails for the intended reason. Styling-only portions use gallery/screenshot checks.

1. Use shared panel headers, buttons, tree rows, icons, metrics and separators. Preserve right-click-select ordering, expanders, keyboard selection and all current callbacks. Do not invent hide/lock/prefab commands.
2. Consolidate redundant toolbar bands within the existing dock structure. Keep Scene/Game/Entity tab actions and Play/Pause/Stop/Run Project accessible. Use tooltip-backed icons where understandable, with overflow/scroll fallback at small widths.
3. Keep existing splitters, minimize/restore, tab drag, group move, detach, saved placement and layout recovery. Default widths may follow 220–260dp hierarchy / 300–360dp inspector / 160–240dp assets only for fresh/reset layouts; do not overwrite saved user arrangements.
4. Share metrics between displayed chrome and dock minimum/hit-target calculations. `DockChrome` and `DockSizing` currently use explicit heights/splitter constants; ensure density changes do not make visual and computed bounds disagree or double-apply OS scale.
5. Compact the existing asset details list/folder tree, retaining import, rename/move/delete, UUID repair, selection details, errors, background refresh and context menus. Adapt fixed-width details columns progressively in narrow docks.
6. Keep native canvas/game pixels untouched. Audit editor-owned overlay/grid colors and document ownership; migrate Kotlin overlays through semantic tokens if separable. If grid/axes are baked into native rendering and need engine changes, record that as deferred rather than changing C++ under this scope.

- [x] Run the focused checks below and inspect actual output; fix regressions within scope.
- [x] Update affected docs and completion record. Stop; do not start the next session.

## Validation

- Run `.\gradlew.bat test --tests "sle.editor.workspace.*" --tests "sle.editor.ui.*" --tests "sle.editor.project.assets.*"`, then `.\gradlew.bat build`.
- Run GUI smokes separately with `--docking-workspace-smoke`, `--asset-browser-smoke`, `--context-menu-smoke` and `--gizmo-smoke` after confirming their current argument dispatch.
- Check saved custom layout and fresh layout, detached Scene/Game together, active Play while moving/minimizing panels, keyboard selection, small window and 100–200% display scaling. Appearance changes must preserve documents, drafts, history and runtime continuity.

## Completion record

Status: IMPLEMENTED (2026-11-16). Automated desktop checks and representative
captures passed; manual acceptance remains open below. No commits or branch changes.

- Prerequisites: sessions 1–3 APIs confirmed against current source. Continue in
  the current feature checkout to preserve their untracked resources and code;
  no worktree relocation or commits. Existing design and session plan govern.
- Pre-flight: shared metrics feed controls and dock bounds. Dock resizing currently
  hardcodes 6dp and default chrome; extend its optional chrome argument and manager
  propagation without changing layout serialization. Asset browser has fixed
  200/240dp side columns and an internal height drag even inside its dock; remove
  the redundant internal splitter and adapt column presentation to available width.
- Styling uses existing callback controls; focused behavior coverage targets dock
  clamping and responsive asset column policy. No native lifecycle changes.
- Native grid/axes audit and actual validation results will be recorded below.

- Files/APIs:
  - `ui/theme/EditorMetrics.kt`: shared dock splitter/grip and responsive asset
    thresholds/column widths. `dockChrome(metrics, fontScale)` derives logical
    tab/restore heights with font growth; Compose density applies UI/OS scale once.
  - `DockHost` uses compact callback buttons, semantic tab selection, scrolling,
    active-tab reveal on selection/strip resize, token separators and shared
    splitter dimensions. `DockSurface` shares these
    heights with the manager through `updateChrome(next: DockChrome)`.
  - `resizeDockSplit(..., scale=1f, chrome=DockChrome())` now uses the supplied
    chrome for nested child minima and divider span. Layout schema is unchanged.
  - `SceneTree`/`EntityOutline` reuse `EditorTreeRow`/`EditorPanelHeader`, retain
    right-click selection ordering and callbacks. Shared tree labels ellipsize;
    `TreeIcon` uses `EditorIcons`, expanders use row metrics and secondary tokens.
  - `SceneViewControls`, `GizmoControls` and Main share compact command controls
    in one wrapping viewport band. Removed redundant inspector gizmo band.
    All Play/Pause/Resume/Stop, Run Project, tool, undo/redo, snap and zoom callbacks
    retain their owners. Scene/Game/Entity navigation remains in dock tabs/menus.
  - `AssetBrowserPanel` uses shared toolbar/folder controls and row metrics;
    toolbar scrolls, columns adapt, Details toggles metadata/actions beneath
    narrow lists. Wide lists retain the sidebar. Existing dock owns height;
    redundant internal height drag removed. No services, watcher or UUID changes.
  - `NativeViewportOwner.editorSelectionColor` feeds `GizmoOverlay.draw(...,
    color)` shader uniform from the appearance token. This changes editor handles
    only. Native grid/axes/entity markers are in
    `EngineModules/Systems/src/EntityPreviewSystem.cpp:59` and remain deferred.
  - `DockingWorkspaceSmoke` adds theme/density captures and source/draft/runtime
    continuity checks; `GizmoSmoke` checks selection-token pixels.
- Final commands/results and limitations follow below.
- Current evidence: focused workspace/UI/assets tests passed; docking and asset
  browser desktop smokes passed. Selection-token pixel regression failed against
  old magenta handles as intended. Custom-chrome regression initially failed to
  compile on missing API; an incorrect expected nested height was corrected from
  260 to 250 logical dp, then the focused suite passed.
- No native/C++ build: native code and JNI signatures are unchanged.
- Remaining manual acceptance: real keyboard/focus traversal, tooltip behavior,
  every narrow dock file action, mixed-DPI/100–200% scaling and long labels.
  Screenshots and controller smokes do not establish those interactive results.
- Next session is 5 only when requested; do not start it.

- Review: fresh read-only reviewer found offscreen active tabs after command
  activation in overflowing groups. Added a real narrow-window smoke; initial
  fixture did not shrink because the previous native minimum was still active.
  Waiting for native sizing and asserting fixture width reproduced the failure.
  The reveal effect also observes strip width so resizing an already active tab
  triggers scrolling. The regression and full docking smoke then passed.
- Rulings: continue the existing feature checkout to preserve earlier untracked
  work (shared review scope at integration); retain saved/default layout ratios
  instead of forcing new widths; defer C++ grid/axis colors to engine work.

- Commands actually run (Windows JDK 21 via escalated PowerShell from WSL):
  - `.\gradlew.bat test --tests "sle.editor.workspace.*" --tests "sle.editor.ui.*" --tests "sle.editor.project.assets.*"`: passed.
  - `.\gradlew.bat build`: passed; 182 tests, zero failures/errors, two existing
    symlink-privilege skips (`AssetBrowserTest` and `ProjectAssetImportsTest`).
  - `.\gradlew.bat run --args=--docking-workspace-smoke`: passed after the final
    tab reveal fix; ACTIVE_DOCK_TAB_VISIBLE_PASS, INTERACTIVE_DOCKING_SMOKE_PASS,
    SEPARATE_MONITORS_SMOKE_PASS, GAME_PLAY_REDOCK_SMOKE_PASS,
    DOCKING_WORKSPACE_SMOKE_PASS. Exercises saved custom arrangements during
    movement, reset defaults, native windows, simultaneous Scene/Game, active Play,
    draft retention, scene-copy Save and teardown.
  - `.\gradlew.bat run --args=--asset-browser-smoke`: passed; import chooser,
    folder/module copy, UUIDs, rename/move/delete/recover, selection/breadcrumbs,
    double-click, background gameplay, project switch/close/reopen.
  - `.\gradlew.bat run --args=--context-menu-smoke`: passed; real right-click
    selection order, nested menus, duplicate/component commands, tab navigation.
  - `.\gradlew.bat run --args=--gizmo-smoke`: passed after the selection color
    migration; 176 token-colored handle pixels plus picking/move/snap/nudge,
    undo/redo/cancel/save/open. Pixel assertion failed with zero matching handles
    before replacing the old shader literal.
  - `git diff --check`: passed. No engine/runtime source changes or C++ rebuild.
  - Logs: `tools/entity-editor/build/session4-*.log`. Initial sandbox Windows
    calls failed WSL vsock binding; escalated calls ran successfully.
- Screenshots/environment:
  - Windows desktop, JDK 21.0.2; production editor screenshots under
    `tools/entity-editor/build/reports/workspace-{dark,light}-{compact,comfortable}.png`.
    Inspected light compact and dark comfortable captures: compact shared toolbar,
    visible hierarchy/tab actions, preserved native grid and scene content.
  - Also inspected `asset-browser-editor.png`: compact folders/details columns,
    selected file and narrow-dock Details action. `gizmo-editor.png` verifies the
    migrated handles. Captures do not prove mixed-DPI or keyboard acceptance.
- Final review: one fresh reviewer; active-tab visibility finding fixed with
  desktop regression. No other confirmed findings or deferred review minors.
- Session boundary: session 4 implementation complete; session 5 remains unstarted.
