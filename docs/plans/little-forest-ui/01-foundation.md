# Theme, preferences and editor resources Implementation Plan

> **For agentic workers:** Use superpowers:executing-plans to implement this session task-by-task. Track the checkboxes and stop at this session boundary.

**Goal:** Create a shared Little Forest foundation and switch both themes live across every editor window.

**Architecture:** Extend the current Compose Desktop editor through shared tokens and callback-based presentation. Preserve document/services/native ownership and existing workflows.

**Tech Stack:** Existing Kotlin/JVM, Compose Desktop Material, Swing/AWT, JDK 21 and Windows native viewport; no dependency upgrade.

**Spec:** Root `SLE_EDITOR_UI_DESIGN.md`, `SLE_Design_Prompt.txt`, and [scope/shared constraints](README.md).

## Global constraints

Read README.md in this directory for the full shared contract and validation environment. Editor UI and branding only; engine/runtime texture work is deferred. Preserve all pre-existing edits. Never alter serialization compatibility, docking persistence or native engine lifecycle for styling. Paths below use the source/test prefixes defined in README.md.

## Required context

Read the shared handoff first and only prerequisite completion records, then: `ui/EditorTheme.kt`, `app/Main.kt`, `workspace/EditorWorkspace.kt`, `ui/EditorMenuStrip.kt`, `workspace/WorkspaceLayoutStore.kt`, `tools/entity-editor/build.gradle.kts`; bundle README/theme JSON and root design sections 2–4.

## Files and responsibilities

- Modify `ui/EditorTheme.kt`; create focused `ui/theme/EditorTokens.kt`, `EditorMetrics.kt`, `EditorAppearanceState.kt`, `EditorAppearanceStore.kt`, and `ui/EditorIcons.kt` (or equivalent nearby package layout).
- Copy editor-only resources beneath `tools/entity-editor/src/main/resources/sle/editor/`: icons, Handjet, OFL license and selected app icon. Keep archive provenance; exclude runtime textures and duplicate platform icon sizes not consumed by the editor.
- Wire `app/Main.kt`, `workspace/EditorWorkspace.kt`, and View menu appearance commands. Create `ui/theme/EditorAppearanceTest.kt` and `EditorAppearanceStoreTest.kt` under the test prefix.

## Interfaces

Produces application-owned appearance state, typed tokens/metrics, central icon lookup and resource paths. Record actual signatures here. Session 2 consumes these; later feature files must not decode palette JSON independently.

## Review focus

- Changed document/entity while a control or picker is active: never apply stale edits.
- Detached/recreated panels: preserve drafts, history, selection and runtime owner.
- Small windows or increased font/DPI: keep content and keyboard focus reachable.
- Invalid/cancelled actions: preserve last valid source and existing file integrity.
- Theme switch: propagate to Compose and Swing while preserving game output.

## Ordered work

- [x] Read current files and confirm prerequisite APIs; record deviations before implementing.
- [x] Add focused failing coverage for changed behavior; verify it fails for the intended reason. Styling-only portions use gallery/screenshot checks.

1. Capture baseline home/editor screenshots and note the fixture, scale, window size and existing controls. If GUI is unavailable, record the blocker; do not fabricate a baseline.
2. Compare root and bundled design docs and document material differences. Inspect current resource loading before selecting Compose-compatible font/icon loading APIs; no new UI dependency.
3. Define `EditorThemeMode { Dark, Light }`, `EditorDensity { Compact, Comfortable }`, immutable `EditorColors` and `EditorMetrics`. Include all semantic tokens in design section 3, including status backgrounds, focus and viewport overlay tokens. Derive one Material palette and one Swing adapter from the same values. Preserve `EditorTheme` as the public composable entry point with explicit appearance input.
4. Use root design starting colors; calculate small-text contrast >=4.5:1 and meaningful boundaries >=3:1 against actual adjacent backgrounds. Adjust only token definitions. Record final values and justified deviations; the light secondary text and subtle borders require checking. Add System only if reliable OS preference observation already exists; Dark/Light fulfills the brief.
5. Add application-owned observable appearance state and atomic user-local persistence with an injectable test directory. Reuse the standalone user storage convention, not project layout or game files. Default Dark/Compact; preserve .85 UI scale and existing choices. Validate preference values, tolerate absent/corrupt/future settings, preserve a valid file on failed writes and report failure. Verify whether any scale persistence really exists before migrating it.
6. Feed the same state to home and detached window theme roots. Switch View menu mode immediately and persist across launches; changes must not recreate the runtime or modify documents/layout.
7. Centralize semantic type-to-icon mapping, themed resource loading, unknown-kind fallback and Handjet heading/system-body typography. Set the Compose window app icon with the supplied branding. Keep existing vectors as centralized fallbacks for command icons absent from the bundle.
8. Add a small representative theme gallery used by subsequent sessions; it must be runnable without opening user project data.

- [x] Run the focused checks below and inspect actual output; fix regressions within scope.
- [x] Update affected docs and completion record. Stop; do not start the next session.

## Validation

- Unit tests: preferences default/load/round trip, malformed and nonfinite/out-of-range scale rejection, atomic write failure preservation; required contrast pairs; unknown icon fallback and resource availability.
- Run `.\gradlew.bat test --tests "sle.editor.ui.theme.*"` after matching test package names, then `.\gradlew.bat build`.
- Manually switch light/dark with a detached Game or Inspector open; restart and verify persistence; compare serialized scene and history before/after. Record temporary mixed legacy chrome as remaining migration work, not final acceptance.

## Completion record

Status: IMPLEMENTED; focused automated checks and gallery inspection passed.
Native-window interaction acceptance remains open as listed below. Session 2 has
not been started. Updated 2026-11-16.

- Changed files and resulting API/signatures:
  - `ui/theme/EditorAppearanceState.kt`: public `EditorThemeMode { Dark, Light }`,
    `EditorDensity { Compact, Comfortable }`, immutable
    `EditorAppearance(mode, density, scale)`; `EditorAppearanceState(store)` exposes
    observable read-only `value`, `notice`, and `update(next)`.
  - `EditorAppearanceStore(directory: Path, beforeReplace: () -> Unit)` exposes
    `load(): EditorAppearance`, `save(value): Boolean`, `lastFailure`. Uses versioned,
    bounded `appearance.json` in the standalone user directory; injectable test
    directory and replace hook. Strict finite scale choices .75/.85/1, defaults
    Dark/Compact/.85, atomic force-and-replace, no destructive non-atomic fallback.
  - `EditorTokens.kt`: immutable `EditorColors`, `editorColors(mode)`,
    `LocalEditorColors`, `LocalEditorAppearance`, `EditorColors.material(mode)`.
    `EditorMetrics.kt`: immutable metrics, `editorMetrics(density)`,
    `LocalEditorMetrics`; dimensions are minimum targets for later controls.
  - `ui/EditorTheme.kt`: `EditorTheme(appearance: EditorAppearance, content)` is the
    shared composable entry point. Applies UI scale once at each window root,
    retains OS font scale, Handjet headings and system sans-serif body.
  - `EditorSwingAppearance.kt`: `applyEditorSwingAppearance(appearance)` installs
    token-derived Swing defaults on EDT and refreshes open Swing dialogs.
    Native OS title bars are platform-owned. Existing custom menu delegates use
    the same semantic surface, selection, accent, text and disabled tokens.
  - `ui/EditorIcons.kt`: `kind(type)`, `path(type, mode, size=16)`, composable
    `painter(type, size=16)`, centralized existing command vector fallback.
    Unknown types resolve to `resource`. PNG loaders use existing Compose desktop
    classpath APIs (deprecated but available); no dependency changes.
  - `workspace/EditorWorkspace.kt`: injectable application-owned `appearance`;
    `uiScale` delegates to it. `app/Main.kt` supplies identical state and branding
    to home and detached roots; View Appearance/Density commands update it.
    Existing finite smokes use temporary appearance directories.
  - Editor-only resources under `src/main/resources/sle/editor/`: both 16/24px
    icon families, SVG originals, four Handjet weights, full OFL, consumed 256px
    app icon, and archive provenance/SHA-256. Runtime textures excluded.
  - `EditorThemeGallery.kt`: `launchThemeGallery(captureDirectory: Path? = null)`;
    `--theme-gallery` opens an isolated fixture; optional `--capture <directory>`
    captures Dark and Light and exits. No JNI, project or preference writes.
  - Tests: `ui/theme/EditorAppearanceTest.kt`, `EditorAppearanceStoreTest.kt`.
    Updated editor README and EditorWorkspace topic for actual persistence.

- Behavior and compatibility checks:
  - Tests cover absent/default/load/round trip, corrupt/future settings, invalid,
    nonfinite and unsupported scales, injected replacement failure preserving the
    valid file, state switching, contrast pairs, unknown icon/resource lookup.
  - Detached Game layout regression preserves serialized entity, invalid draft,
    undo availability, entity identity and window layout while changing mode.
    It does not initialize or validate the native runtime.
  - Existing project `.sle/editor-state.json` scale persistence was found in
    `ProjectFiles`/`ProjectControls`; retained without migration or schema changes.
    There was no standalone scale file to migrate. Project scale restore keeps
    existing precedence. Appearance does not change document/layout serialization.
  - Root and ZIP design docs compare identical after newline normalization.
    Bundle README clarifies that supplied Handjet and approved mascot supersede
    older prose about future font/branding selection. Its older fallback-texture
    README says no font is bundled; current top-level bundle README governs.
  - Ruling: work in the current feature checkout, preserving untracked supplied
    inputs, rather than relocating an implementation whose inputs are untracked.
    No commits or branch changes requested or performed.
  - Ruling: Dark/Light only; no existing reliable OS preference observer exists.
    All palette literals live in EditorTokens; no feature decodes JSON palettes.
  - Contrast adjustments from root starting values: Dark secondary/placeholder/
    disabled text `#B3C4B6`, border/separator `#8FA895`; Light secondary/placeholder/
    disabled text `#455C4B`, border/separator `#6C7F70`, accent/focus `#2C7547`.
    Dark selected-border originally measured 2.73:1; final token passes >=3:1.
    Tested primary/secondary/placeholder/disabled text against all neutral,
    selected, hover and pressed backgrounds >=4.5:1; focus/borders >=3:1.
    Accent foreground contrast: Dark 8.36:1, Light 5.61:1. Status pairs >=6.2:1.
    Remaining final semantic values are explicitly listed in EditorTokens.kt;
    viewport tokens are provided without changing rendered game content.

- Commands actually run and results:
  - Final `.\gradlew.bat test --tests "sle.editor.ui.theme.*"`: passed, 7 focused
    tests. Final `.\gradlew.bat build`: passed, 163 tests, zero failures.
    Initial new-API tests failed compilation before implementation; contrast test
    then failed on the measured dark selected boundary before its correction.
    Logs retained under `tools/entity-editor/build/reports/foundation/`.
  - Windows `java -version`: OpenJDK 21.0.2. Initial sandboxed PowerShell attempt
    failed WSL vsock binding; escalated Windows calls succeeded.
  - `gradlew.bat test --tests "sle.editor.ui.theme.*"`: first failed to compile
    for missing foundation APIs as intended; then contrast test failed for the
    selected-row boundary; corrected token and reran green. Additional injected
    workspace regression first failed for missing appearance constructor input.
    Final focused run: 7 tests, zero failures.
  - `gradlew.bat build`: final full suite 163 tests, zero failures. No native/C++
    changes or dependency upgrades. Classpath painter deprecation warnings remain.
  - `gradlew.bat run --args="--theme-gallery --capture C:/projects/engine/tools/entity-editor/build/theme-gallery"`:
    succeeded; captured and visually inspected both modes, Handjet heading,
    Cyrillic/system body, field, enabled/disabled button, checkbox, icon family,
    status backgrounds. Capture artifacts: `tools/entity-editor/build/theme-gallery/`.
  - `git diff --check`: passed. Independent final code review reported no confirmed
    correctness findings; detached DPI and live Swing dialogs remain GUI gaps.

- Screenshots and environment:
  - Windows desktop/JDK 21 from WSL, gallery 900x650 physical capture, default .85
    UI scale, Compact, no project fixture. Actual dark/light images are gallery
    checks, not baseline/editor acceptance screenshots.
  - Pre-change home/editor screenshots were not captured. Initial Windows command
    was sandbox-blocked; Windows GUI access subsequently worked after escalation.
    No baseline is fabricated and no before/after comparison is claimed.

- Blockers/deferred checks:
  - Manual switching with native detached Game/Inspector, live Swing file chooser,
    restart of the full native editor and game-pixel comparison remain unexecuted.
    Store reload and compatibility regressions do not substitute for those checks.
  - Legacy feature chrome and Material control state styling remain for sessions
    2–5. Gallery disabled Material styling still uses Material's alpha treatment;
    semantic disabled tokens are available for the upcoming shared controls.
    Complete dark/light visual acceptance, small-screen and mixed-DPI/font-scale
    interaction coverage remains open. This is foundation completion only.

- Continuation or next session:
  - Stop here. Session 2 may consume the APIs above and migrate shared controls;
    do not independently parse theme JSON or reimplement preference persistence.
  - Before final visual acceptance, run the outstanding full-editor GUI checks
    above; no later-session implementation was performed here.
