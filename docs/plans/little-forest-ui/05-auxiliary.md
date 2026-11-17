# Menus, dialogs and remaining editor surfaces Implementation Plan

> **For agentic workers:** Use superpowers:executing-plans to implement this session task-by-task. Track the checkboxes and stop at this session boundary.

**Goal:** Finish consistent theme and control treatment across Compose and Swing auxiliary UI.

**Architecture:** Extend the current Compose Desktop editor through shared tokens and callback-based presentation. Preserve document/services/native ownership and existing workflows.

**Tech Stack:** Existing Kotlin/JVM, Compose Desktop Material, Swing/AWT, JDK 21 and Windows native viewport; no dependency upgrade.

**Spec:** Root `SLE_EDITOR_UI_DESIGN.md`, `SLE_Design_Prompt.txt`, and [scope/shared constraints](README.md).

## Global constraints

Read README.md in this directory for the full shared contract and validation environment. Editor UI and branding only; engine/runtime texture work is deferred. Preserve all pre-existing edits. Never alter serialization compatibility, docking persistence or native engine lifecycle for styling. Paths below use the source/test prefixes defined in README.md.

## Required context

Read the shared handoff first and only prerequisite completion records, then: Sessions 1–4 completion records; `ui/EditorMenuStrip.kt`, `EditorContextMenu.kt`, `MenuAppearance.kt`, `DocumentActions.kt`; `project/ProjectControls.kt`, `ProjectDocumentControls.kt`, `ProjectDistributionControls.kt`, `project/assets/ProjectAssetControls.kt`; search current sources for JOptionPane/JFileChooser/JDialog and appearance literals.

## Files and responsibilities

- Modify menu appearance adapters and dialog presentation owners found by the audit. Add a narrow `ui/EditorDialogAppearance.kt` or reusable dialog helpers if needed.
- Add adapter/presentation behavior tests beside `ui/`; preserve existing project/file command implementations and modal result semantics.

## Interfaces

Consumes token-to-Swing adapter and control APIs. Produces complete editor-owned appearance coverage plus a precise inventory of unavoidable OS/native exceptions.

## Review focus

- Changed document/entity while a control or picker is active: never apply stale edits.
- Detached/recreated panels: preserve drafts, history, selection and runtime owner.
- Small windows or increased font/DPI: keep content and keyboard focus reachable.
- Invalid/cancelled actions: preserve last valid source and existing file integrity.
- Theme switch: propagate to Compose and Swing while preserving game output.

## Ordered work

- [x] Read current files and confirm prerequisite APIs; record deviations before implementing.
- [x] Add focused failing coverage for changed behavior; verify it fails for the intended reason. Styling-only portions use gallery/screenshot checks.

1. Inventory all remaining custom chrome, dialogs, popups, tooltips, settings/import/export/error flows and legacy Sandbox UI reachable from diagnostics. Distinguish editor-owned Swing controls from OS-managed title bars/file chooser chrome.
2. Apply shared semantic appearance to Swing menus, popup submenus, disabled items, focus and icon mapping; ensure currently open editor windows respond to appearance changes. Use the AWT event thread for Swing mutations.
3. Theme editor-owned JOptionPane/JFileChooser content via scoped helpers or component adaptation. Avoid global UIManager changes leaking beyond editor dialogs, and do not replace functioning file/confirmation logic solely for styling. OS-managed title bars/native chooser chrome may follow OS appearance; disclose precise exceptions.
4. Migrate project settings, collision choices, source conflict/unsaved confirmations, creation/import/export progress/error surfaces to shared compact patterns. Preserve cancellation, busy/disabled state, async ownership, file integrity and command results.
5. Remove residual ad hoc light/dark branches and literals in feature presentation; legitimate game/debug content colors are outside UI theme. Keep any documented OS/native exceptions visible in acceptance records.
6. Recheck contrast on actual composed/alpha backgrounds, popup focus and keyboard Alt mnemonics after migration.

- [x] Run the focused checks below and inspect actual output; fix regressions within scope.
- [x] Update affected docs and completion record. Stop; do not start the next session.

## Validation

- Run `.\gradlew.bat test`, then `.\gradlew.bat build`.
- Run `--context-menu-smoke`, `--project-smoke`, `--project-documents-smoke`, and `--asset-browser-smoke` separately using `.\gradlew.bat run --args=<argument>`.
- In both themes test nested menus, keyboard navigation, cancel/accept unsaved changes, project settings, file chooser, asset collision, validation errors and export dialogs. Confirm failed/cancelled operations preserve source files and drafts.

## Completion record

Status: IMPLEMENTED (2026-11-16). Automated checks and the scripted desktop smokes
listed below passed. Manual interactive checks in both themes are still open (see
Blockers).

- Ruling: continue the existing feature checkout, whose sessions 1–4 and supplied
  assets are uncommitted/untracked, as in the prerequisite completion records.
  Moving to a clean worktree would omit the required foundation. Cost: shared edits
  must be reviewed together at integration; no commit or branch change requested.
- Pre-flight: shared token/control APIs match the planned consumers. Existing
  Swing adapter globally changes UIManager; replace it with scoped root styling.
- Inventory: menus/context popups; entity/scene discard and overwrite; project
  create/open/settings; project document conflict/export; asset import/collision,
  browse, rename/move/delete/recover/identity repair; texture chooser; help/about;
  asset issues Compose dialog; diagnostic Sandbox controls. Existing async status
  text/progress already inherits Material tokens. Native title bars, shell file
  icons and look-and-feel file navigation glyphs are platform-owned exceptions.
- Regression: `appearanceDoesNotChangeApplicationSwingDefaults` failed against
  the previous adapter as expected (1 test, 1 failure); passes after scoped adapter.
- Desktop visual finding/fix: dark JFileChooser retained native white combo and
  places controls with light text. `chooserComboRendererUsesEditorColors` failed
  (3 tests, 1 failure) before scoped BasicComboBoxUI/renderer/popup, button/toggle
  and toolbar delegates; focused tests pass afterward. Chooser callbacks, filters
  and shell icons are preserved.
- Fresh reviewer: no Critical/Important correctness findings. Deferred minor:
  button border installed on first styling retains the previous token after a
  live Swing dialog theme switch. New dialogs use the current border; text,
  backgrounds and text selection refresh. Include this in session 6 acceptance.
- Interim verification: focused adapter tests and full JVM suite passed before
  the final smoke run.
- Keyboard mnemonic finding (smoke, not product): the new Alt+F check first
  failed because the smoke called `window.requestFocus()`, which made the bare
  `ComposeWindow` frame the focus owner. A frame has no Swing key-binding path,
  so `JMenu` mnemonics could not fire. The smoke now activates the frame with a
  title-bar click and focuses the content pane, as a user click would. Alt+F
  opened File. The following `check` on Down then raced Robot delivery on the
  EDT; the smoke now uses `await` for Down and Escape.

- Changed files and resulting API/signatures:
  - New `ui/EditorDialogAppearance.kt`: `EditorDialogAppearance.update(EditorAppearance)`,
    `register(JComponent)`, `appearance`; `EditorFileChooser : JFileChooser`
    (same constructors, dialog root registered in `createDialog`);
    `EditorDialogs.showConfirmDialog/showMessageDialog/showOptionDialog/showInputDialog`
    mirror the `JOptionPane` static signatures and return values (CLOSED_OPTION,
    -1 and null for close/cancel).
  - `ui/theme/EditorSwingAppearance.kt`: `applyEditorSwingAppearance` now updates
    registered editor roots and popups only (no `UIManager` writes).
  - `MenuAppearance.kt`, `EditorMenuStrip.kt`, `EditorContextMenu.kt`: menus use
    semantic editor tokens (surface/textPrimary/selectedBackground/accent/
    disabledText) plus density and mode.
  - Project, document, distribution, asset and texture dialog call sites now go
    through `EditorDialogs`/`EditorFileChooser`. No direct
    `JOptionPane.show*`, `JFileChooser(` or `JDialog(` remains outside the helper
    and smokes.
  - `workspace/DockDesktopDrag.kt`: desktop drop preview uses the theme accent
    instead of a literal blue.
  - New `smoke/AuxiliaryAppearanceSmoke.kt` (runs at the start of
    `--context-menu-smoke`); `ContextMenuSmoke.kt` adds Alt+F/Down/Escape checks.
  - Tests: `ui/EditorDialogAppearanceTest.kt` (scoped defaults, chooser renderer).
- Behavior and compatibility checks: the auxiliary smoke runs the real modal
  dialogs in Dark and Light. It checks unsaved NO, collision "Rename" index 0,
  input OK text, input Cancel → null and chooser Cancel → CANCEL_OPTION, and that
  the dialog surface matches the theme token. Project, document, asset and
  docking smokes confirm cancel/conflict/collision/file-integrity paths are
  unchanged. No serialization, docking persistence or JNI changes.
- Commands actually run and results (from `tools/entity-editor`, 2026-11-16):
  - `.\gradlew.bat test`: BUILD SUCCESSFUL, 186 tests, 0 failures, 0 errors.
  - `.\gradlew.bat build`: BUILD SUCCESSFUL.
  - `--context-menu-smoke`: AUXILIARY_APPEARANCE_SMOKE_PASS,
    MENU_KEYBOARD_SMOKE_PASS, CONTEXT_MENU_SMOKE_PASS.
  - `--project-smoke`: PROJECT_FOUNDATION_SMOKE_PASS.
  - `--project-documents-smoke`: PROJECT_DOCUMENTS_SMOKE_PASS.
  - `--asset-browser-smoke`: PROJECT_MANAGED_ASSETS_UI_SMOKE_PASS,
    PROJECT_IMPORT_UI_SMOKE_PASS, ASSET_BROWSER_SMOKE_PASS.
  - `--docking-workspace-smoke` (extra, for the drop-preview change):
    DOCKING_WORKSPACE_SMOKE_PASS and its sub-passes.
  - Logs: `tools/entity-editor/build/session5-final-*.log`,
    `session5-test-final.log`, `session5-build-final.log` (PowerShell UTF-16).
- Screenshots and environment: Windows 11, JDK 21.0.2, interactive desktop.
  `tools/entity-editor/build/reports/session5-{dark,light}-{unsaved,collision,input,input-cancel,file-chooser,settings}.png`.
  Inspected dark chooser, dark collision and light settings: editor surfaces,
  text, buttons and the chooser list follow tokens.
- Documented OS/native exceptions: native window title bars (light on dark mode),
  shell file icons and look-and-feel chooser navigation glyphs; the native
  viewport backing fill `NativeViewportOwner` (23,25,31) matches the GL clear
  color and is game-output backing, not UI chrome.
- Blockers/deferred checks:
  - Manual interactive pass in both themes is still open. This covers nested
    menus by mouse, live theme switch while a Swing dialog is open, and
    contrast at increased OS font/DPI.
  - Deferred minor (from the review above): button borders keep the previous
    token after a live theme switch on an already-open dialog.
- Continuation or next session: session 6 (`06-acceptance.md`) only when requested.
  Do not start it from this record.
