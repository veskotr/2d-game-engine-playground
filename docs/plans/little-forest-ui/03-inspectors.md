# Shared scene and prefab inspectors Implementation Plan

> **For agentic workers:** Use superpowers:executing-plans to implement this session task-by-task. Track the checkboxes and stop at this session boundary.

**Goal:** Migrate both inspector paths to compact, data-driven property rows without changing source formats.

**Architecture:** Extend the current Compose Desktop editor through shared tokens and callback-based presentation. Preserve document/services/native ownership and existing workflows.

**Tech Stack:** Existing Kotlin/JVM, Compose Desktop Material, Swing/AWT, JDK 21 and Windows native viewport; no dependency upgrade.

**Spec:** Root `SLE_EDITOR_UI_DESIGN.md`, `SLE_Design_Prompt.txt`, and [scope/shared constraints](README.md).

## Global constraints

Read README.md in this directory for the full shared contract and validation environment. Editor UI and branding only; engine/runtime texture work is deferred. Preserve all pre-existing edits. Never alter serialization compatibility, docking persistence or native engine lifecycle for styling. Paths below use the source/test prefixes defined in README.md.

## Required context

Read the shared handoff first and only prerequisite completion records, then: Sessions 1–2 completion/API records; `ui/EntityWorkspace.kt`, `ui/SceneWorkspace.kt`, `model/EntityDocument.kt`, `model/EntityWorkspaceState.kt`, `model/SceneWorkspaceState.kt`, `ui/ProjectAssetSelectionTest.kt`; design sections 5–7.

## Files and responsibilities

- Modify `ui/EntityWorkspace.kt` and `ui/SceneWorkspace.kt`; create `ui/inspector/EditorPropertyDefinition.kt`, `EditorPropertyRenderer.kt`, and `InspectorSectionState.kt` if these responsibilities do not already have homes.
- Extend `ui/ProjectAssetSelectionTest.kt`, relevant model tests, and add `ui/inspector/InspectorPropertiesTest.kt`. Keep file operations/native schema outside the renderer.

## Interfaces

Consumes controls/transactions from session 2. Produces one property rendering path and session-owned section expansion. Record actual schema and state owner for future component authors.

## Review focus

- Changed document/entity while a control or picker is active: never apply stale edits.
- Detached/recreated panels: preserve drafts, history, selection and runtime owner.
- Small windows or increased font/DPI: keep content and keyboard focus reachable.
- Invalid/cancelled actions: preserve last valid source and existing file integrity.
- Theme switch: propagate to Compose and Swing while preserving game output.

## Ordered work

- [x] Read current files and confirm prerequisite APIs; record deviations before implementing.
- [x] Add focused failing coverage for changed behavior; verify it fails for the intended reason. Styling-only portions use gallery/screenshot checks.

1. Adapt native defaults/enums and current JSON values to a presentation-only property definition: component/path, label, kind, choices, validation/read-only state and existing action callbacks. Reuse the existing catalog; no schema fork or speculative prefab override implementation.
2. Route name, booleans, enums, string/numeric values, vectors, resource references and structured JSON through session 2 controls. Transform position/rotation/scale should be three compact rows. Region/nested objects must retain every key; multiline JSON can use taller rows.
3. Reuse the renderer for standalone prefab components and Scene Transform/Camera2D. Preserve Parent selection, Make Active, component selection/removal, Browse/Clear, project import handling, assets list and errors. Keep read-only gameplay restrictions.
4. Replace padded component cards with collapsible 26dp section headers and 28dp typical rows. Store expansion state at workspace lifetime keyed by document/entity identity plus component, so tab/dock remount does not reset it. Session persistence is sufficient; avoid adding cross-session storage unless already straightforward.
5. Maintain stale-result guards in `projectAssetSelection`. A delayed picker result must not write into a changed document/component/draft. Render unsupported actions only as reserved API slots, not clickable placeholders.
6. Add labels/help truncation tooltips and narrow-pane fallback; verify focus/error visibility after collapse, removal and selection changes.

- [x] Run the focused checks below and inspect actual output; fix regressions within scope.
- [x] Update affected docs and completion record. Stop; do not start the next session.

## Validation

- Tests: property classification from actual native-catalog-shaped defaults/enums, boolean/string distinction, vector paths, structured JSON preservation, independent section identity and asset-picker stale callback.
- Run `.\gradlew.bat test --tests "sle.editor.model.*" --tests "sle.editor.ui.*" --tests "sle.editor.project.ProjectDocumentsTest"`, then `.\gradlew.bat build`.
- Run `.\gradlew.bat run --args=--authoring-ui-smoke` and `--gizmo-smoke` separately; exercise valid/invalid edits, undo/redo, save/reopen, switching Scene/Entity and component removal. Compare serialized values before/after a presentation-only operation.

## Completion record

Status: IMPLEMENTED (2026-11-16). Automated checks and representative screenshots
passed. Interactive acceptance remains open below. Session 4 has not started.
No commits or branch changes.

- Files/APIs:
  - `ui/inspector/EditorPropertyDefinition.kt`: `PropertyKind`,
    `EditorPropertyDefinition(component, path, label, value, kind, choices, axes)`,
    `inspectorProperties(component, values, catalog)` and
    `propertyDefinition(component, path, value, choices)` classify current authored
    JSON and existing native enum metadata. No native schema, ranges or defaults
    are invented. Unknown/mixed objects and region objects remain whole JSON.
  - `EditorPropertyRenderer(state, property, readOnly, browseEnabled, onBrowse)`
    shares boolean/enum/text/number/vector/resource/JSON controls. File operations
    remain host callbacks in `ComponentField`. `InspectorTextInput` keeps caret
    state locally and observes authoritative owner drafts on remount.
  - `InspectorSectionState.expanded(entity, component)` /
    `setExpanded(entity, component, expanded)` is owned by
    `EntityWorkspaceState.inspectorSections`, independent of presenter lifetime,
    source serialization and undo. Scene and prefab use `InspectorComponent`.
  - `EntityWorkspace.kt` and `SceneWorkspace.kt`: compact name/property rows,
    collapsible sections, catalog choices, preserved Parent/Make Active,
    component selection/removal, Browse/Clear, imports, assets and field errors.
    Read-only scene controls retain the same rendering without enabling edits.
  - `EditorControls.kt`: multiline `EditorTextField(..., singleLine=false)`;
    visible vector axis labels; tooltip/ellipsis titles and labels; narrow section
    actions wrap beneath the header. Property rows stack below the existing
    280dp threshold. Numeric fields reuse owner `numberEdit`; no new lifecycle.
  - Entity component/tool setters and workspace tab setter finish numeric
    gestures. `editContextVersion` invalidates delayed inspector actions at
    end/cancel/reset boundaries. Scene playback finishes owner gestures after
    validating its snapshot, invalidating pending picker results before entering
    read-only gameplay. Dock remounts do not invalidate context or drafts.
  - `InspectorGallery.kt`, gallery launcher and Main route:
    `--inspector-gallery [--capture <directory>]` uses both real inspector paths
    without JNI/project writes. Captures 900/540px widths and 100%/150% font scale.
    Updated editor README and shared session status.

- Behavior and review:
  - Five `InspectorPropertiesTest` tests cover catalog-shaped boolean/string,
    enum/resource/vector/structured classification, vector child paths, mixed
    objects, unchanged serialized source/history during presentation, independent
    section identity, component/tool history and tab-boundary invalid drafts.
  - Five `ProjectAssetSelectionTest` tests cover typed-path replacement, later
    edits, replacement documents, removal/reattachment, unchanged-source context
    boundaries and playback during asynchronous import.
  - Initial new-property tests failed compilation on missing APIs. Removal/
    reattachment failed its assertion before identity guards. Component/tool and
    tab tests failed before owner boundaries. Review reproduced delayed picker
    acceptance after context/playback changes; both regressions failed before
    the context-version fix and passed afterward.
  - Fresh read-only reviewer found the delayed picker context gap; fixed with
    regressions. Axis visibility and narrow header suggestions were also applied.
    No remaining review findings. Existing model coverage exercises undo/redo,
    invalid/save behavior and source round trips; native gizmo smoke covers
    cancel/save/open. Manual GUI coverage is bounded below.

- Rulings:
  - Continue in the current feature checkout, preserving untracked session 1–2
    dependencies and supplied inputs; no new worktree or commits. Cost: changes
    share the existing feature branch and must be reviewed together at integration.
  - Reject delayed asset selections after any intervening owner document/draft
    replacement or edit-context boundary, including equal-valued component
    reattachment. Cost: an unrelated intervening edit can require Browse again;
    this conservatively prevents stale results overwriting authoring state.

- Commands actually run (Windows JDK 21 via escalated PowerShell from WSL):
  - `.\gradlew.bat test --tests "sle.editor.ui.inspector.*"` and
    `--tests "sle.editor.ui.ProjectAssetSelectionTest"`: observed failures above,
    followed by passing focused runs.
  - `.\gradlew.bat test --tests "sle.editor.model.*" --tests "sle.editor.ui.*" --tests "sle.editor.project.ProjectDocumentsTest"`: passed after final fixes.
  - `.\gradlew.bat build`: passed after final fixes; 181 tests, zero failures or
    errors, two existing symlink privilege skips in AssetBrowserTest and
    ProjectAssetImportsTest. Existing classpath painter deprecation warnings.
  - `.\gradlew.bat run --args="--inspector-gallery --capture C:/projects/engine/tools/entity-editor/build/reports/little-forest/session3-final"`:
    passed; eight captures. Inspected representative dark/light wide and narrow
    captures, including 150% font scale: three-row Transform, visible axes,
    growing controls, wrapped actions, stacked narrow rows and whole region JSON.
  - `.\gradlew.bat run --args=--authoring-ui-smoke`: passed after final fixes;
    EMBEDDED_SESSION_GUARDS_PASS, ENTITY_PREVIEW_EDIT_PASS,
    ENTITY_EDITOR_UI_SMOKE_PASS. Screenshot under `build/reports/entity-editor.png`.
  - `.\gradlew.bat run --args=--gizmo-smoke`: passed after final fixes;
    VIEWPORT_GIZMO_SMOKE_PASS picking/box/circle/independent-sprite/move/snap/
    nudge/undo/redo/cancel/save/open, handles=176. Screenshot under
    `build/reports/gizmo-editor.png`.
  - `git diff --check`: passed. No C++ build: native/JNI source is unchanged.

- Remaining acceptance and next session:
  - Real keyboard traversal/caret/Enter/Escape, tooltip interaction, scrolling to
    every lower section in narrow panes, collapse/removal focus behavior,
    detached Inspector/Game and mixed DPI remain manual acceptance. Captures and
    controller tests do not replace those interactive checks. OS title bars and
    other unmigrated surfaces are outside this session.
  - Session 4 (`04-workspace.md`) is next only when requested. Do not begin it.
