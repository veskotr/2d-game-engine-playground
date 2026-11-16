# Project folders and asset browser

Session handoff: [current state and resume instructions](project-workspace-handoff.md).

Status: project-folder approach approved. Steps 1–6 are implemented; validation
results are recorded below. Asset identity uses adjacent `.slemeta` sidecars.

## Goal

A game is an ordinary directory containing editable source files and a small
JSON `.sleproject` descriptor. The file browser reflects actual disk contents;
the descriptor does not enumerate which files are allowed to appear. External
editors and filesystem operations remain part of the normal workflow.

## Proposed layout

```text
MyGame/
  MyGame.sleproject
  Assets/
    Scenes/
    Prefabs/
    Scripts/
    Textures/
    Audio/
    UI/
    Animations/
    Data/
  .sle/
    cache/
    editor-state.json
```

These are initial organizational defaults, not required file-placement rules.
Users may create and move folders anywhere beneath Assets. Discover unknown file
types as well as recognized assets. `.sle` is derived/local data excluded from
version control and asset discovery. The project descriptor and Assets are source.

Project JSON contains format/version, project UUID, display name, asset-root
location, startup-scene identity, and game/window settings. It has no authoritative
list of files. Keep editor layout/selection separate from portable game settings.
Project, asset and entity identities are distinct; names are editable metadata.

## Asset references — decision to settle

Recommended: UUID asset identity in a small adjacent `.slemeta` JSON file; the
asset payload retains its ordinary name and extension. The descriptor stores no
inventory. The asset database is a rebuildable UUID-to-relative-path index.
Do not use payload hashes as identity: editing a Lua file must not create a new
asset identity. Hashes may be used for cache invalidation and collision diagnosis.

Alternative: a central source asset registry with stable UUIDs and paths. This
avoids sidecars but requires explicit reconciliation when files move externally;
it must never control visibility in the browser.

Moving an asset together with its sidecar preserves identity automatically.
Moving only a payload externally still displays it at its new location, but may
lose identity. Do not promise that every arbitrary external move can be inferred.
Report missing/duplicate IDs and offer explicit repair; never silently bind a
reference to an ambiguous file. Ordinary path references are a simpler alternative
but require rewriting or repair after moves, so are not recommended long term.

## Filesystem and import behavior

- Import copies external files/folders into the selected project folder by default;
  the user's original files are not deleted. Internal move is a separate operation.
- Existing project files are reused directly, without another temporary import copy.
- Preserve relative folder structure for Lua modules, UI and their dependencies.
  Do not flatten directories or infer arbitrary dependencies from script text.
- Name conflicts offer rename, skip or replace; do not overwrite silently.
- Scan on open, watch for changes, and reconcile after focus regain or Refresh.
  A full scan is the correctness fallback for lost/overflowed watcher events.
- External edits to an open dirty scene/prefab require conflict handling rather
  than silently reloading or replacing the user's drafts. Changes during Play
  update discovery but do not mutate the running snapshot automatically.
- Keep filesystem I/O off the Compose/AWT rendering thread; publish UI snapshots.
- Begin with read-only browsing and imports. Add rename/move/delete only after
  reference updates and conflict behavior have regression coverage.
- Contain project operations within their root; initially show symbolic links
  without recursively following them. Reject escapes and conflicting asset IDs.

## Current code and migration

- `files/PrefabPackages.kt` currently copies imports to temporary storage and bundles
  assets by SHA-256; this remains the package import/export path.
- `model/EntityWorkspaceState.kt` and `SceneWorkspaceState.kt` own current open
  documents and save behavior; introduce project-backed persistence without
  discarding their validation, dirty checks or history.
- `ui/EditorMenuStrip.kt` owns File/Create/View menus; extend those for projects.
- `app/Main.kt` owns the three-column shell; add the asset browser below the
  viewport, independently of the scene hierarchy and component inspector.
- `engine_app/main.cpp` reads engine.json, while Runtime/SceneLoader consume paths;
  introduce project-root resolution at an orchestration boundary, never Renderer.
- Existing `.slescene` and `.sleprefab` packages remain portable import/export files.
  Project scenes/prefabs use ordinary editable JSON with shared external assets.
  Resolve project UUID references before passing canonical documents to legacy
  codecs until native project resolution is introduced. Preserve package hash IDs
  separately; do not overload existing `asset://<sha256>` package references.
- No automatic conversion of user files. Import packages into a project explicitly.
  Ensure scripts and textures survive moving the complete project directory.

## Ordered implementation

### 1. Project foundation

Add `project/ProjectDocument.kt`, project-root/path helpers and workspace state.
Define versioned descriptor validation and atomic writes. Add File → New Project,
Open Project, Close Project and Project Settings entry points. Create default
folders and local-cache ignore rules; preserve existing standalone file workflows.

Acceptance: create/reopen/move a project directory; malformed descriptors preserve
current workspace; project settings and local editor state remain separate.

### 2. Asset browser and disk discovery

Add background filesystem scanning and a bottom dock with folder tree, file list,
breadcrumb, refresh and selection details. Show actual files including unknown
extensions. Add watchers plus reconciliation and safe error states.

Acceptance: files created/renamed/moved externally appear without being listed in
the descriptor; disappeared files are reflected; large scans do not block rendering.

### 3. Imports and asset identity

Implement selected-folder imports, folder copies, metadata/index creation, collision
handling, and existing-in-project reuse. Connect texture/script/asset browsing to
project storage. Provide Reveal in Explorer and Open Externally for suitable files.

Acceptance: imported assets remain after their original source is removed; scripts
retain module folders; conflicting names and duplicate IDs produce clear results.

### 4. Project-backed scene and prefab editing

Double-click scene/prefab files to edit them; persist plain project documents with
shared asset references. Integrate project-root resolution with previews and Play.
Retain unsaved-document confirmations and add external-change conflicts.

Acceptance: shared textures are stored once; save/reopen and full-project relocation
preserve visuals and scripts; existing standalone package workflows still pass.

### 5. Whole-game runtime configuration

Configure startup scene and game/window settings. Launch the current project in
the editor and via `engine_app --project <file.sleproject>`. Discover/load scene
assets through the project resolver; preserve engine.json compatibility.

Acceptance: the same relocated project runs in editor and standalone from an
unrelated working directory, including switching scenes and deferred resources.

### 6. Managed file operations and distribution

Add create-folder, rename, move and delete with reference-aware validation and
recoverable operations. Add project export/build packs separately from authoring;
keep source folders authoritative and allow later multiple runtime asset packs.

Acceptance: managed moves preserve IDs; referenced deletions are explained and
controlled; external metadata loss has a visible repair path.

## Validation

For each step, add focused Kotlin model/filesystem tests and desktop checks for
new user interactions. Test temporary directories, external changes, relocation,
conflicts, missing references, and failed-write preservation. Avoid mirror tests.
When native runtime behavior changes, build affected targets and add focused CTest
coverage per tests/AGENTS.md, then run the required broader suite before handoff.
Keep current camera, scene and context-menu desktop regressions passing.

Execute one numbered step at a time; record actual implemented/validated work and
new decisions here. Project packing, content streaming, linked prefab overrides,
hot reload during Play and runtime state writeback are not part of the foundation.

## Step 1 execution plan (approved 2026-11-12)

Ruling: implement the first numbered step in this delivery, as requested by the
step-by-step plan. Use sidecar UUID metadata beginning in step 3. Work on
`feat/project-foundation` in the existing Windows build workspace; keep the
native build's absolute paths and the user's running editor intact.

Project descriptors use `format: sle-project`, version 1, a UUID `id`, `name`,
relative `assetRoot`, nullable UUID `startupScene`, and `game.window` settings.
Settings save immediately/atomically; unfinished settings stay in the dialog.
Project switching confirms current unsaved documents and resets standalone
workspaces only after a successful open/create. Project-backed asset saves and
runtime settings application remain steps 4/5, not silently implied by step 1.

- [x] Task 1: add project descriptor/path/store/workspace tests, run them RED,
  then implement `project/ProjectDocument.kt`, `ProjectFiles.kt`,
  `ProjectWorkspaceState.kt`; run focused tests GREEN.
  Cover ordinary folders, relocation, malformed/future descriptors preserving
  current project, path escapes, existing-target preservation, atomic settings
  saves and separation of game data from local editor state.
- [x] Task 2: add project New/Open/Close/Settings actions and settings dialog in
  `project/ProjectControls.kt`; wire File menu, workspace lifecycle, project title
  and command-line open. Use background disk I/O. Preserve standalone workflows.
- [x] Task 3: add a project desktop smoke for actual menu/settings/lifecycle,
  project relocation and rejected open; run build, desktop project smoke and
  existing menu/camera regressions. Run one fresh review, fix confirmed defects,
  document current scope and reopen editor.

Ledger: base 2718c4a; branch feat/project-foundation. Pre-existing change is this
plan document only. No engine behavior changes required for this step.


### Step 1 validation ledger (2026-11-13)

- RED: new project model tests failed compilation before implementation.
- GREEN: project filesystem/model tests passed; added external-descriptor conflict
  regression failed first, then passed after stale-save validation was added.
- RED: desktop async-switch regression reproduced lost edits during background
  loading. GREEN: revision recheck/confirmation preserves those edits.
- Windows JDK 21 Gradle build passed all 56 tests.
- `--project-smoke` passed create/settings/invalid-values/local-state/close/
  relocation/reopen/failed-open/dirty-switch; screenshot inspected.
- `--context-menu-smoke` passed existing menu/file/prefab/tab workflows.
- Fresh independent read-only review found no confirmed defects.
- No native engine behavior changed. At this checkpoint, steps 2–6 remained planned.
- `--camera-smoke` passed child-camera follow, Lua input, physics, paused tab switching, Resume/Stop and hierarchy persistence.

## Step 2 execution plan (approved 2026-11-13)

Scope: read-only asset browser and disk discovery. Import, file mutation, UUID
metadata and opening project documents remain later steps. Continue on the
existing feature branch and preserve step 1 changes.

- [x] Task 1: RED/GREEN filesystem scanner and browser-state tests. Add
  `project/assets/AssetSnapshot.kt` and `AssetBrowserState.kt`. Discover all real
  files under the asset root, including unknown types; show links without following
  them; report read failures; repair selections after external removal.
- [x] Task 2: RED/GREEN background watcher service tests. Add
  `project/assets/AssetBrowserService.kt`: recursive directory notifications,
  periodic full reconciliation, manual refresh, cancellation and project cleanup.
  All scanning/registration happens off the rendering thread.
- [x] Task 3: add bottom dock (`AssetBrowserPanel.kt`) with folder tree, file list,
  breadcrumbs and selection details; View menu show/hide/refresh; focus-regain
  reconciliation. Keep one native canvas and do not reload gameplay on disk changes.
- [x] Task 4: desktop smoke for external add/rename/move/remove, navigation, View
  commands, project switching and live viewport frames; build/regressions, fresh
  review, docs and reopened project.

Ruling: the dock spans the window below the hierarchy/viewport/inspector row. It
starts at 220 dp, can be resized, and is only shown for an open project. Discovery
is authoritative; watcher events only schedule scans. A 3-second reconciliation
fallback handles missed notifications. Filesystem paths are selection keys until
asset UUID metadata arrives in step 3.


### Step 2 validation ledger (2026-11-13)

- RED: filesystem/service tests failed before implementation. Windows canonical
  short-path mismatch was reproduced and corrected by comparing canonical roots.
- RED: nested WatchService handles prevented external parent-folder renames.
  GREEN: Windows uses one recursive FILE_TREE root watch plus a parent watch;
  platforms without tree notifications keep reconciliation as the fallback.
- RED/GREEN: stale row callbacks after external removal are ignored safely.
- Gradle build passed: 63 tests, 0 failures, 1 skipped symbolic-link fixture
  because this Windows user cannot create symbolic links.
- `--asset-browser-smoke` passed actual folder/file selection, breadcrumbs,
  double-click, external add/move/delete, refresh/show-hide, a 400-file scan while
  gameplay kept running, and project switch/close/reopen. Screenshot inspected.
- Fresh read-only review found no confirmed defects.
- Imports, asset UUIDs, mutations and project document loading remain later steps.

- Final indexes are constructed on the scanner worker; UI publication and folder
  lookup reuse those indexes. Build and asset desktop smoke passed again afterward.
- Existing `--project-smoke`, `--camera-smoke`, and `--context-menu-smoke`
  regressions passed with the new browser integration.

## Step 3 execution plan (2026-11-13)

Continue the approved design in the existing workspace, preserving steps 1/2.

- [x] Add RED/GREEN filesystem tests for UUID identity across payload edits,
  payload+sidecar moves, relocation, malformed/duplicate metadata and repair.
  Implement `AssetIdentity.kt`; publish unambiguous UUID/path indexes and errors
  with browser snapshots. Metadata is adjacent versioned JSON, never a manifest.
- [x] Add RED/GREEN import tests for independent copies, nested Lua folders,
  in-project reuse, rename/skip/replace, duplicate IDs and escaping/link paths.
  Implement `ProjectAssetImports.kt`. Replacement retains destination identity;
  imported sidecars retain valid IDs unless that would create ambiguity.
- [x] Add selected-folder Import, Reveal/Open and explicit identity repair to the
  dock. Route inspector Browse through project storage on a worker; reuse files
  already inside Assets and preserve standalone package imports.
- [x] Run Windows Gradle build and desktop regressions; update current docs and
  handoff with actual results. Keep plain project scene persistence for step 4.

Pre-flight: step 2 produces immutable `AssetSnapshot` values consumed by the UI;
identity indexes will be built on the same worker. Legacy component documents
still consume filesystem paths, so step 3 resolves selected project assets to
absolute paths for previews; serialized UUID references arrive with step 4.
Ruling: sidecars use reserved `.slemeta` suffix and are not asset rows themselves;
invalid/orphan metadata is reported. A missing sidecar receives a new UUID;
malformed or duplicate existing metadata is never silently rewritten.


### Step 3 validation ledger (2026-11-13)

- RED: missing identity/import APIs failed compilation. GREEN: filesystem identity
  and import tests passed, including nested folders, independent copies, reuse,
  rename/skip/replace, destination UUID preservation, duplicates and invalid
  metadata rejected before copying, edits/moves/relocation and explicit repair.
- Existing browser move fixture now handles the newly introduced sidecar before
  deleting its parent directory; discovery semantics remain filesystem based.
- Review RED/GREEN: manually edited asset-field drafts previously prevented Browse
  from assigning; captured draft/value/document checks now accept unchanged fields
  and reject late edits. Broad temporary-prefix filtering hid ordinary assets;
  removed filtering and synchronize scan/import/repair instead.
- Windows JDK 21 Gradle `build`: 73 tests, 0 failures/errors, 2 skipped symbolic-link
  fixtures (Windows account lacks symlink privileges).
- Final `run --args=--asset-browser-smoke` passed selected-folder Import and chooser,
  nested Lua folders, source removal, collision rename, UUID stability, project
  script reuse and texture selection, all prior browser/lifecycle checks and
  background scanning during gameplay. Screenshot inspected. Earlier UI runs
  failed during smoke development; the requested rerun and final expanded run passed.
- `run --args=--project-smoke`, `--camera-smoke`, `--context-menu-smoke`: passed.
- Fresh independent review identified the two defects above; recheck after their
  fixes found no additional confirmed defects. No native code changed.
- Ruling: individual file replacements are atomic and payload copies stream to
  disk. Folder replacement merges; a folder/batch is not a single transaction,
  and an I/O failure may leave earlier successful files. No source is deleted.
- Step 4 remains future work: project-backed plain documents and portable UUID
  references. Current previews/package exports use resolved filesystem paths.


## Step 4 execution plan (2026-11-13)

Continue in the existing feature checkout and preserve the uncommitted steps 1–3.
The user has now authorized step 4. Execute this numbered milestone before step 5.

- [x] RED/GREEN `ProjectDocumentsTest`: plain versioned scene/prefab JSON,
  UUID references in known asset fields only, shared assets without copies,
  relocation, missing/ambiguous references, path containment, external conflicts
  and validation/failed-write preservation. Implement `project/ProjectDocuments.kt`.
- [x] Integrate resolved documents with workspace open/save and native validation;
  add background document controls, browser double-click, project Save/Save As,
  explicit package export and standalone compatibility. Guard asynchronous results
  against project/document switches and edits. Keep Play snapshots unchanged.
- [x] Add desktop project-document smoke; run Gradle build and relevant desktop
  regressions, fresh read-only review, update Projects/editor docs and handoff.

Pre-flight: the scanner supplies UUID indexes; persistence uses a fresh worker-side
scan on each operation rather than trusting a stale UI snapshot. Workspaces retain
resolved paths for native codecs/preview/Play; only portable sources store UUIDs.
Ruling: project documents use `.scene.json` / `.prefab.json`, versioned envelopes,
with `project-asset://<UUID>` references — distinct from package hash IDs. If wrong,
future source-format migration is needed. No automatic package conversion.
Conflict policy: retain the open draft; Save refuses external modification/deletion,
with Reopen or Save As available. No automatic reload during Play or authoring.


### Step 4 validation ledger (2026-11-13)

- RED/GREEN: new persistence and workspace tests initially failed for missing APIs.
  Shared source assets resolve after relocation; missing/duplicate IDs, external
  edits/deletion, failed validation and concurrent arrival preserve existing files.
- RED/GREEN: malformed destination metadata originally failed after replacing the
  payload; preflight now rejects it before the write. Save As never replaces an
  existing source. Source revisions use complete content hashes, not timestamps.
- Project Scene Import Prefab uses background resolution and independent entity
  copies with existing history. An existing entity-capacity regression caught a
  removed pre-I/O precheck; it was restored and passed in the full suite.
- Fresh independent read-only review found one defect: editing during an async Save
  retained a stale expected disk revision. RED/GREEN workspace regression now
  records the saved snapshot and advances its revision while retaining later edits
  as dirty. Document switches prevent applying that save result to another draft.
- RED/GREEN desktop regression held an actual source read behind the asset lock,
  created a clean New scene during loading and reproduced its replacement. Open
  now checks document identity before re-confirming edits; New is preserved.
- Windows JDK 21 Gradle `build run --args=--project-documents-smoke` passed:
  **82 tests, 0 failures/errors, 2 skipped** Windows symlink fixtures, plus actual
  Save chooser, browser scene/prefab opening, shared texture, prefab insertion/undo,
  dirty cancellation, external conflict/reopen, script Play, unchanged runtime
  snapshots, export, relocation and relocated texture/script loading.
  Earlier failing runs above were regression development, not final validation.
- Screenshot `build/reports/project-documents-editor.png` inspected: relocated
  project texture is visible in Game. No native code changed; no DLL rebuild needed.

- Existing desktop regressions passed: `--scene-smoke`, `--camera-smoke`,
  `--context-menu-smoke`, `--project-smoke`, `--asset-browser-smoke`. The first
  camera run failed its initial editor-pan assertion; a rerun without camera/code
  changes passed hierarchy/pan/script/input/physics/pause/resume/stop/save/open.
- `git diff --check` passed; local documentation links resolve. Source code and
  all previous work remain uncommitted/unpushed on `feat/project-foundation`.
  Step 4 is complete; step 5 is next.

## Step 5 execution plan (2026-11-13)

Continue the approved handoff on `feat/project-foundation` in place: the Windows
native build has absolute paths and steps 1–4 are uncommitted. Preserve them.
Use inline execution and focused regression tests before implementation.

Architecture: Systems owns `ProjectRuntime`, a read-only descriptor/sidecar index
and resolved scene snapshots. Runtime owns that context for deferred switches and
Lua asset operations. Both engine_app and the editor use it; no working-directory
change or lower-module orchestration dependency. Scene names use document keys;
UUIDs are additional stable switch aliases. Project sources are never rewritten.

- [x] Native resolver: add `ProjectRuntime.hpp/.cpp`, strict descriptor/settings
  and UUID index validation, contained paths, resolved component asset fields,
  scene discovery and startup validation. Add `integration_project_runtime_path`
  for relocation/unrelated cwd, shared/deferred paths, missing/duplicate IDs,
  malformed settings and preserved script-variable data. Build and run RED/GREEN.
- [x] Runtime/standalone: add project registration/loading and project-aware Lua
  paths, Lua module roots, `engine_app --project`, independent engine shader paths.
  Preserve `--config`; cover actual scene switching and deferred script resources
  with a windowed integration executable and standalone bounded launch.
- [x] Editor: add startup-scene selection, Run Project action, background launch
  preparation and immutable playback context. Reuse one canvas; Stop restores
  authoring. Cover project launch/Stop/relocation and existing Play behavior through
  Kotlin tests and actual desktop smoke.
- [x] Validate affected native targets and broader CTest/Gradle suites, run desktop
  regressions, fresh read-only review; update current docs and handoff with actual
  commands/results. Leave changes uncommitted as inherited from the handoff.

Review focus: malformed metadata must never choose an arbitrary duplicate; failed
launches preserve drafts; project switches during launch must reject late results;
relative/deferred assets must survive unrelated cwd; settings must not restart an
active game. Managed mutations and distribution remain step 6.

### Step 5 validation ledger (2026-11-13)

- Baseline Windows Gradle `test` succeeded (up-to-date tasks, historical outputs
  not relabeled as a fresh execution). New launch-state regression then failed
  compilation for missing PreparedProject/playProject and passed in full Gradle
  `test` and `build` runs after implementation.
- Native resolver regression failed compilation for missing ProjectRuntime, then
  passed after implementation and fixture correction (native Script has no
  `variables` field; opaque variable traversal is checked directly).
- Windowed native launch regression failed compilation for missing runtime API,
  then passed relocated Lua require + deferred scene switch. Windows temporary
  short paths require filesystem equivalence rather than string equality.
- RED/GREEN: dynamic missing textures escaped the Lua boundary as C++ exceptions;
  project path resolution now logs/rejects invalid paths with existing failure
  return values. New deferred-scene transform inspection regression reproduced
  stale startup keys; the fix passed its focused runtime regression.
- First desktop runtime smoke reached settings and saved a scene UUID, but its
  keyboard sequence selected the second scene rather than startup. Fixture now
  accounts for the initially unfocused menu and uses sorted scene choices; the
  final startup-selector smoke passed.
- Ruling: Run Project launches saved startup sources and preserves unsaved drafts;
  ordinary Play keeps its existing current-scene semantics. Embedded preview uses
  game logical dimensions/FPS; standalone owns the game window title/mode. This
  avoids turning the editor shell into a fullscreen game window.
- Ruling: native startup validates/resolves all plain project scene/prefab sources
  once. Scene switches use launch snapshots. Project prefabs use unique disposable
  `.sle/runtime` derived JSON, shared-owned through the runtime context and removed
  on release; source files/sidecars are never modified. Payload hot reload and
  distribution packs remain out of scope.
- GREEN: deferred-scene transform inspection now uses the active project's scene
  document after a switch. Focused native play test passed.
- RED/GREEN desktop: project VSync remained at editor swap interval 0. The host
  now applies the project interval where WGL_EXT_swap_control is available and
  restores interval 0 and normal editor pacing on Stop/session recreation.
- Windows full native build and full CTest passed **71/71** (including windowed).
- Windows Gradle `build run --args=--project-runtime-smoke` passed the full editor
  test suite and actual startup selector, Run Project, dirty draft retention,
  deferred Lua scene/module loading, logical resolution, VSync, Pause/Resume/Stop,
  relocation, standalone from unrelated cwd and rejected missing startup.
  Screenshot inspected. Standalone bounded runs now report their final scene;
  the earlier smoke wrongly expected a deferred-load log that does not exist.
- Deferred prefab bridge mutation reproduced unresolved relative prefab failures;
  bridge restored and expanded native validation/prefab tests passed.
- Python scripting tool tests: **21 passed**; generated Lua API stub check passed.
- Fresh read-only review found one P2: dynamic prefab override component assets
  bypassed the project resolver. Regression reproduced both unresolved UUID and
  relative Script overrides in actual Lua instances. Resolve each override's
  component map before instantiation and contain resolution errors in its existing
  error result. The new regression and post-review full native suite passed
  **71/71**, including the windowed test.
- Final review scope rulings: nested resource-format dependencies/dynamic XML links
  retain existing loader contracts (no dependency inference); saved-source launch
  and unique derived cache follow the decisions above. Linux executable discovery
  is excluded because the documented supported build/workflow is Windows. These
  limits are documented; broader portability requires a separate platform task.
- Existing `--project-documents-smoke` and `--camera-smoke` passed against the new
  runtime; the latter passed on its first current run.

- Final review fix: project-aware prefab override assets —
  `integration_project_play_path` RED→GREEN for UUID/relative Script overrides;
  Windows full build and complete post-fix CTest **71/71** passed. The reviewer
  reported no other confirmed defects. No deferred minor findings.
- Current editor build: **83 tests, 0 failures/errors, 2 skipped** symlink fixtures
  because this Windows account cannot create symbolic links. Final desktop
  regression batch passed scene, context-menu, project settings, asset browser
  and whole-project runtime smoke after the review fix.


Step 5 complete: current Windows native full build/CTest **71/71**, editor
Gradle build **83 tests (2 skipped symlink fixtures)**, whole-project runtime smoke
and scene/camera/context-menu/project/asset-browser/project-document desktop
regressions passed. Fresh review's single P2 was reproduced and fixed with
regression coverage; no other confirmed findings or deferred minors. Screenshot
inspected; local documentation links and `git diff --check` passed. All changes
remain uncommitted/unpushed. Step 6 remains planned.

Final commands (Windows executables invoked from WSL):

- `cmake.exe --build build/debug --config Debug --parallel 4`: passed.
- `ctest.exe --test-dir build/debug -C Debug --output-on-failure`: 71/71 passed
  after the review fix, including resolver validation, relocated module/scene
  switching, dynamic prefab UUID/path resolution and prefab override assets.
- Windows JDK 21 Gradle wrapper `build run --args=--project-runtime-smoke`:
  build/test and actual project desktop/standalone checks passed; final post-review
  `run --args=--project-runtime-smoke` also passed.
- Gradle `run` with `--scene-smoke`, `--context-menu-smoke`, `--project-smoke`,
  `--asset-browser-smoke`, `--camera-smoke`, `--project-documents-smoke`: passed.
- `engine_app.exe --config engine.json --frames 3`: passed; npc_zone_demo loaded,
  initialized Lua, completed three frames and tore down scripts normally.
- `python3 -m unittest discover -s EngineModules/Scripting/tools/tests`: 21 passed.
- `python3 EngineModules/Scripting/tools/generate_lua_api_stub.py --check`: passed.
- Current local documentation link check and `git diff --check`: passed.

## Step 6 execution plan (2026-11-13)

Resume the existing feature checkout and preserve all earlier uncommitted work.
Use inline execution with filesystem regressions before product code.

- [x] Managed filesystem operations: create folder, collision-safe rename/move
  carrying sidecars, saved-source/startup reference reporting, explicit referenced
  deletion and durable recoverable trash. Cover IDs, resolution after moves,
  conflicts, containment, malformed sources and recovery without overwrite.
- [x] Background browser actions and restart-visible recovery UI. Prevent mutation
  while authoring documents hold resolved filesystem paths or playback is active;
  explain dynamic Lua/XML dependencies are outside static reference analysis.
- [x] Desktop exercise, full Gradle build and relevant editor regression smokes;
  independent review and current documentation/handoff updates.
- [x] Distribution follow-on implemented in [the separate plan](project-distribution.md):
  runnable Windows Release folders with a defined runtime/source contract.

Ruling: use .sle/trash for recoverable deletions, retain receipt and original
relative location across restarts, and never overwrite arrivals during restore.
Unlike caches this directory contains user recovery data and must not be purged
as disposable state. Static reference reporting conservatively includes UUIDs in
saved source documents and the descriptor startup identity; dynamic Lua/XML and
module strings cannot be inferred. Warn at every managed move/delete. Keep moves
from silently invalidating authoring paths by requiring closed authoring documents
and stopped playback. If wrong, more granular workspace rebinding is future work.

Current RED: Windows Gradle focused test compilation fails because
ProjectAssetOperations is absent. Initial sandbox execution failed WSL interop;
rerun with tool escalation reached the expected missing-API compiler failures.

### Step 6 managed-operations validation ledger (2026-11-13)

- RED: new managed-operation tests failed compilation for the absent API. GREEN:
  folder moves preserve UUIDs and portable document resolution; reference reporting,
  explicit referenced delete, restore/no-overwrite, containment, reserved names,
  malformed-source rejection and startup deletion protection passed.
- RED/GREEN: renaming a scene to `.txt` silently removed it from runtime discovery;
  managed scene/prefab moves now preserve their source suffixes.
- Fresh independent read-only review found two P2s. Interrupted sidecar-only deletion
  let the scanner mint a new UUID before recovery; reproduced with a real partial
  filesystem receipt, fixed by recognizing pending recovery before identity ensure.
  Restart/relocation and interrupted restore also pass filesystem regressions.
- Second review P2 reproduced in actual desktop smoke while holding AssetIdentity:
  queued mutation left authoring/Play input enabled. Managed operations now disable
  the owner Window for their whole coroutine, including asset-lock waits, restoring
  its original enabled state in finally. The held-lock desktop regression passes.
- Desktop development initially clicked an offscreen tree row after expanding Data;
  screenshot showed Prefabs selected instead of Scripts. The fixture now collapses
  Data before that click. Managed actions occupy a compact row above details so
  they remain visible in the short dock.
- Windows JDK 21 Gradle wrapper `build run --args=--asset-browser-smoke`: passed,
  **92 tests, 0 failures/errors, 2 skipped** Windows symlink fixtures. Actual buttons
  and native prompts passed create/rename/move/delete/recover, UUID preservation,
  queued-operation input exclusion and all prior import/browser/gameplay/lifecycle
  checks. Screenshot `build/reports/asset-browser-editor.png` inspected.
- No native source changes in this resumed milestone; previous native results are
  historical step-5 validation, not rerun here. Distribution packs remain the later,
  separate delivery phase described by the handoff.

Limitations: `.sle/trash` is recovery data, not a disposable cache; dynamic paths
still require manual changes. Managed moves use ordinary filesystem moves with
rollback on sidecar failure, not a power-loss transaction: an abrupt process crash
between file/sidecar moves may need manual sidecar repair. All work remains
uncommitted/unpushed.

- Current compatibility checks: camera, context-menu, scene, project settings and
  project-document desktop smokes passed. Camera's first run failed its paused
  frame-image equality assertion; the unchanged rerun passed. No camera/runtime
  implementation or fixture changes were made to obtain that pass.
- Local Markdown target paths and `git diff --check` passed. Independent review's
  two P2s are fixed with failing-then-passing regressions; no deferred minor review
  findings. Whole-project runtime regression also passed, including standalone launch from
  an unrelated cwd. The full six-smoke compatibility rerun completed successfully.

Managed-operations milestone complete. Current actual commands: Windows JDK 21
Gradle wrapper `build run --args=--asset-browser-smoke`, plus `run` for
`--camera-smoke`, `--context-menu-smoke`, `--scene-smoke`, `--project-smoke`,
`--project-documents-smoke`, and `--project-runtime-smoke`. All final runs passed;
initial camera pause assertion failure is recorded above. Native code was not
rebuilt in this resumed milestone. Leave the entire worktree uncommitted/unpushed.
At this managed-operations checkpoint the distribution-pack phase was not yet
implemented; its later completion is recorded below.

## Step 6 distribution completion (2026-11-13)

The user authorized implementing runnable delivery folders. See the self-contained
[distribution plan and current validation](project-distribution.md). The Windows
Release bundle target and File → Export Game workflow are implemented. Staged
saved-source export retains UUIDs/module folders and all unknown assets, excludes
source local state/trash, validates natively and publishes without overwrite.
No-config executable discovery uses the adjacent game/game.sleproject; explicit
CLI options and legacy engine.json retain precedence/compatibility.

Current native full build/CTest 72/72; editor full build 99 tests (2 skipped Windows
symlink fixtures); actual relocated Release export launch, deferred Lua modules/
scenes, dirty-draft/failure preservation and source independence passed. Final
compatibility desktop results are recorded in the distribution plan/handoff.
All previous and current work remains uncommitted/unpushed.
