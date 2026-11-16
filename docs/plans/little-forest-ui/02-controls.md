# Compact controls and numeric edit transactions Implementation Plan

> **For agentic workers:** Use superpowers:executing-plans to implement this session task-by-task. Track the checkboxes and stop at this session boundary.

**Goal:** Provide reusable compact controls with intentional interaction states and safe numeric editing.

**Architecture:** Extend the current Compose Desktop editor through shared tokens and callback-based presentation. Preserve document/services/native ownership and existing workflows.

**Tech Stack:** Existing Kotlin/JVM, Compose Desktop Material, Swing/AWT, JDK 21 and Windows native viewport; no dependency upgrade.

**Spec:** Root `SLE_EDITOR_UI_DESIGN.md`, `SLE_Design_Prompt.txt`, and [scope/shared constraints](README.md).

## Global constraints

Read README.md in this directory for the full shared contract and validation environment. Editor UI and branding only; engine/runtime texture work is deferred. Preserve all pre-existing edits. Never alter serialization compatibility, docking persistence or native engine lifecycle for styling. Paths below use the source/test prefixes defined in README.md.

## Required context

Read the shared handoff first and only prerequisite completion records, then: Foundation completion record and token APIs; `ui/EntityWorkspace.kt`, `model/EntityWorkspaceState.kt`, `model/SceneWorkspaceState.kt`, `model/EntityDocument.kt`, existing `model/EntityDocumentTest.kt`; root design sections 4 and 6.

## Files and responsibilities

- Create focused files under `ui/controls/` for button/toggle/icon button, text/number/vector fields, checkbox/select, property row, section, tooltip, panel header and tree row as needed by sessions 3–4. Do not implement unused wizard/docking frameworks.
- Add a pure numeric edit-state/controller file and focused `ui/controls/NumberEditTest.kt`. Modify model transaction integration only where existing gesture APIs cannot safely express the required lifecycle; regression tests belong beside the model.

## Interfaces

Consumes foundation tokens. Produces controls with exact callback signatures and transaction ownership documented in the completion record. Session 3 must reuse these rather than adding a second numeric lifecycle.

## Review focus

- Changed document/entity while a control or picker is active: never apply stale edits.
- Detached/recreated panels: preserve drafts, history, selection and runtime owner.
- Small windows or increased font/DPI: keep content and keyboard focus reachable.
- Invalid/cancelled actions: preserve last valid source and existing file integrity.
- Theme switch: propagate to Compose and Swing while preserving game output.

## Ordered work

- [x] Read current files and confirm prerequisite APIs; record deviations before implementing.
- [x] Add focused failing coverage for changed behavior; verify it fails for the intended reason. Styling-only portions use gallery/screenshot checks.

1. Define callback-based controls that receive value/state and emit edits, commit/cancel and commands. They must not call JNI, serialize files or own editor documents. Expose enabled/read-only/invalid state, accessible labels and tooltip help; use shared token state rendering.
2. Build compact fields from idiomatic Compose primitives; shrinking a Material OutlinedTextField with a fixed height can clip text. Account for actual text/font metrics and let accessibility scaling increase height. Implement 34%/66% property row alignment with a documented narrow-width fallback.
3. Define numeric transaction behavior: intermediate empty, `-`, `0.` and exponent drafts remain visible; Enter commits a valid finite value; Escape restores the focus-entry value; focus loss commits valid input and keeps invalid draft plus error visible and save-blocking, consistent with existing authoring safety. Locale uses JSON decimal syntax. Do not serialize NaN or infinity.
4. Preserve live valid preview while editing, but group one focused edit into one undo step through existing state transactions. Inspect scene history coordination before touching gesture ownership. Undo/redo, tab changes, selection changes, component removal and document replacement must finish or cancel safely without applying a draft to a different entity. Drafts must survive dock/window presenter recreation.
5. Implement number precision/step/range options only when metadata/callers specify them; native catalog currently supplies defaults/enums, not general ranges. Do not infer arbitrary limits or round stored values to display precision. Vector editors share number controls and one property row. Defer label-drag and modifier stepping unless existing behavior requires it.
6. Add focused examples to the theme gallery for normal, hover, pressed, focus, selected, disabled, read-only and invalid states. Use keyboard-visible focus independently from selection.

- [x] Run the focused checks below and inspect actual output; fix regressions within scope.
- [x] Update affected docs and completion record. Stop; do not start the next session.

## Validation

- Tests: negative/decimal/exponent finite values, incomplete drafts, invalid JSON, min/max when supplied, Enter, Escape, valid/invalid blur, one undo per edit, redo, changed entity/component during edit, external undo while focused and draft survival on remount.
- Run `.\gradlew.bat test --tests "sle.editor.ui.controls.*" --tests "sle.editor.model.*"`, then `.\gradlew.bat build`.
- Use the gallery in both themes at 100% and enlarged font scale; verify keyboard traversal, field selection/caret and no clipped glyphs.

## Completion record

Status: IMPLEMENTED (2026-11-16). Automated checks and representative gallery
captures passed; manual keyboard/native-window acceptance remains open below.
Session 3 has not started. No commits or branch changes.

- Files/APIs:
  - `ui/controls/EditorControls.kt`: internal composables `EditorButton(label,
    onClick, modifier, enabled, selected, primary, help, source)`,
    `EditorToggle(label, selected, onSelectedChange, ...)`,
    `EditorIconButton(label, painter, onClick, ...)`, `EditorTooltip(help, modifier,
    content)`, `EditorCheckBox(label, checked, onCheckedChange, ...)`,
    `EditorSelect(label, value, choices: List<String>, onSelect, ...)`.
  - `EditorTextField(value: TextFieldValue, onValueChange, label, modifier,
    enabled, readOnly, invalid, help, placeholder, numeric, onFocusChange,
    onCommit, onCancel)` uses BasicTextField, minimum metrics heights and
    independent focus borders. Caller owns caret/selection and validation.
  - `EditorNumberField(edit: NumberEdit, label, modifier, enabled, readOnly,
    help)`; `EditorVectorField(label, axes: List<Pair<String,NumberEdit>>, ...)`.
    `EditorPropertyRow(label, modifier, help, content: RowScope)` uses 34/66
    columns; below 280dp it stacks. Vector axes share that property row.
  - `EditorPanelHeader(title, modifier, actions: RowScope)`,
    `EditorInspectorSection(title, expanded, onExpandedChange, modifier,
    actions: RowScope, content: ColumnScope)`, `EditorTreeRow(label, selected,
    onClick, modifier, depth, enabled, help, leading)` preserve callback ownership.
  - `ui/controls/NumberEdit.kt`: pure `NumberOptions(min: Double?, max: Double?)`
    validates strict finite JSON numbers without rounding their source spelling.
    Owner-created `NumberEdit` exposes `text`, `invalid`, `active`, `focus()`,
    `input(text)`, `updateText(text, editable)`, `commit(): Boolean`, `blur()`,
    `cancel()`. `updateText` filters selection-only/read-only changes and starts
    a transaction only for a real text edit. No step/precision is invented.
  - `model/EntityWorkspaceState.kt`: `numberEdit(component, path, options)` caches
    handles on the document owner. Existing drafts/errors/snapshots remain the
    authority; no serialization or scene structural-history replacement.
    Gesture identity and document epochs reject stale callbacks. Other entity
    commands finish the numeric gesture before recording their own undo entry.
  - `EditorMetrics.kt` centralizes borders, radius, padding and property layout
    metrics alongside foundation sizes. `EditorThemeGallery.kt` provides real
    controls, Undo/Redo, read-only/invalid examples, state specimens and font
    scaling. Synthetic hover/press/focus specimens demonstrate rendering only.
  - User override: bundled Roboto Regular/Medium/Bold replaces Handjet for all
    Compose headings/body and Swing menus/dialogs. `ui/theme/EditorFonts.kt`
    supplies Compose/AWT families; numeric inputs stay monospace. Font binaries,
    Apache license and SHA-256 provenance are under editor resources. Existing
    Handjet input/resources are preserved unused. Updated theme resource test,
    `EditorTheme.kt`, `MenuAppearance.kt`, `EditorSwingAppearance.kt` and README.

- Behavior checks:
  - Ten numeric regressions in `ui/controls/NumberEditTest.kt`: finite negative,
    decimal/exponent syntax; incomplete/invalid drafts; explicit bounds; live
    preview; Enter; Escape restoring an invalid entry; valid/invalid blur;
    save blocking; one undo step/redo; owner-cached remount; external undo;
    removal/replacement; scene selection; unrelated command history; stale
    presenter refocus; selection-only/read-only event filtering.
  - Enter/valid blur finishes the gesture. Invalid Enter stays active; invalid
    blur finishes history with draft/error retained and last valid JSON intact.
    Escape restores the entry snapshot. Active undo uses existing cancel-first
    semantics, without manufacturing redo for an uncommitted gesture.
  - Structural scene history continues to own its operations. Its existing
    selection/change/restore boundaries end/cancel entity gestures. No runtime,
    file-format, docking or JNI changes. Full inspector adoption is session 3.
  - Review found read-only selection could begin a transaction through the
    TextFieldValue callback. Added updateText guard and focused regression.
    Gallery inspection found tooltip wrappers dropping weighted widths; caller
    modifiers now live on the wrapper and inner fields fill available width.

- Decisions/deviations:
  - Ruling: work in the current feature checkout to preserve untracked session 1
    implementation and supplied inputs. No new worktree or commits.
  - Ruling: owner-cached numeric binding, using existing gesture APIs; session 3
    must reuse it and call `endGesture()` at tab/target boundaries. Disposal does
    not cancel the document owner's edit. Cost: host lifecycle still needs its
    integration checks when inspectors migrate.
  - User requested Roboto during execution; this overrides the supplied Handjet
    visual direction for editor fonts. Original design inputs remain untouched.
  - No precision/step options or label drag: no current metadata supplies them.
    Min/max are explicit caller options; stored numbers are never display-rounded.

- Commands/results:
  - Initial `.\gradlew.bat test --tests "sle.editor.ui.controls.*"` failed on
    absent NumberOptions/numberEdit APIs before implementation. Additional
    regressions reproduced unrelated-command rollback and stale-document refocus
    failures before fixing command boundaries and document epochs. The review
    adapter regression first failed compilation for its missing updateText API.
  - `.\gradlew.bat test --tests "sle.editor.ui.controls.*" --tests "sle.editor.model.*"`:
    passed. `.\gradlew.bat build`: passed, 173 tests, zero failures/errors,
    two skips in AssetBrowserTest/ProjectAssetImportsTest because Windows cannot
    create symlinks without the required host privilege. Ten numeric tests pass.
  - `.\gradlew.bat run --args="--theme-gallery --capture C:/projects/engine/tools/entity-editor/build/session2-gallery"`:
    passed. Four captures: dark/light at UI 100% with font 100% and 150%, Compact,
    900x650 window. Reviewed glyphs, growing field heights, state specimens,
    property alignment and full-width tooltip-backed fields. Capture mode keeps
    its temporary window on top so another application cannot obscure it.
  - Windows JDK 21/desktop invoked from WSL. Initial sandbox PowerShell vsock
    failure required escalation. Classpath painter deprecation warning remains.
    No C++ build needed. `git diff --check`: passed.

- Remaining acceptance/continuation:
  - Actual keyboard traversal/caret/selection and tooltip interactions, narrow
    windows, Comfortable density, mixed DPI and detached native Game/Inspector
    interaction have not been manually validated. Synthetic interaction-state
    captures and controller tests are not substitutes for those GUI checks.
  - Existing feature surfaces still use legacy controls. Session 3 is the next
    bounded task: wire these shared controls into scene/prefab inspectors and
    verify tab/selection/undo integration there. Do not create a second numeric
    edit lifecycle. Stop at this session boundary.
