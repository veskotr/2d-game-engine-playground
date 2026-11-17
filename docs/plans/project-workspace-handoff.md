# Project workspace handoff — 2026-11-13

## Resume here

Read this file, then [the approved plan](project-workspace-assets.md).
Steps **1–6 are implemented**. Managed operations and runnable-folder distribution
are implemented and validated; see the step 6 ledger and
[distribution plan](project-distribution.md) for current checks. Do not redo earlier steps.
All work remains saved on disk, uncommitted and unpushed.

## Git and workspace

- Repository: `/mnt/c/projects/engine` (Windows path `C:/projects/engine`).
- Branch: `feat/project-foundation`.
- Base/last pushed commit: `2718c4a` on `origin/main` (earlier editor/engine work).
- **Project foundation and asset-browser work is saved on disk but NOT committed
  or pushed.** Preserve all modified and untracked files. Do not reset or clean.
- Relevant new files are under `tools/entity-editor/.../sle/editor/project/`,
  `smoke/ProjectSmoke.kt`, `smoke/AssetBrowserSmoke.kt`, project tests, and docs.
- Current documentation: [Projects](../topics/Projects.md), editor README,
  approved plan and its execution/validation ledgers.

## Approved design

A project is a normal folder with a plain JSON `.sleproject` descriptor. The
filesystem is authoritative; the descriptor does NOT list visible files.
Default `Assets/` subfolders: Scenes, Prefabs, Scripts, Textures, Audio, UI,
Animations, Data. `.sle/` is ignored local state/cache. Retain `.sle/trash` while
deleted assets may need recovery; only the other caches are disposable.

Existing `.sleprefab` and `.slescene` archives remain import/export formats.
Project source files should eventually be editable plain files referencing shared
assets. Step 3 uses UUID identity in adjacent `.slemeta` files, not payload
hashes (editing a script must not change its identity). External imports copy
sources into the project; do not delete their originals. Preserve relative module
folders. Internal moves/deletes are later operations with reference handling.

## Completed step 1

- `project/ProjectDocument.kt`: versioned JSON descriptor, UUID/name, relative
  asset root, nullable startup-scene UUID, stored game/window settings.
- `ProjectFiles.kt`: ordinary folder creation, relocation-safe paths, atomic saves,
  no existing-directory overwrite, external settings conflict checks, local UI
  scale kept separately in `.sle/editor-state.json` on switch/close.
- `ProjectWorkspaceState.kt`, `ProjectControls.kt`: File New/Open/Close Project,
  Project Settings dialog, background disk IO, failed-open preservation, dirty
  confirmation and revision recheck for edits made during asynchronous loading.
- Main supports `--project <path>`. Project changes reset authoring workspaces only
  after successful open/create and confirmation. Existing standalone files work.
- Game settings are STORED ONLY; runtime application is step 5.

## Completed step 2

`project/assets/`:

- `AssetSnapshot.kt`: scanner and immutable indexes, built on worker thread.
  Includes unknown file types. Links shown without following. Canonical Windows
  path handling avoids 8.3 short-name mismatches.
- `AssetBrowserService.kt`: background WatchService plus 3-second full rescan;
  Windows uses `ExtendedWatchEventModifier.FILE_TREE` on asset root, avoiding
  child handles that prevented external parent-directory renames. Parent watch
  handles asset-root changes. Manual/focus refresh and shutdown are implemented.
- `AssetBrowserState.kt`: navigation/selection and safe stale-row handling when
  external removals race UI input.
- `RememberAssetBrowser.kt`: conflated channel publication to Compose, focus
  listener, project lifecycle disposal. Settings edits do not restart browser.
- `AssetBrowserPanel.kt`: resizable bottom dock, folder tree, file list,
  breadcrumbs, details, Refresh, View show/hide/refresh. Read-only currently.
- Main/EditorMenuStrip/TreeIcons integration preserves one native GL canvas.
  Disk updates do not alter authored scenes or reload gameplay.

Latest user question asked whether auto-refresh is possible. Explained it is
already implemented via filesystem events, a 3-second fallback and focus refresh.
No concrete stale-refresh bug has been reported; do not rebuild it speculatively.

## Completed step 3

- `AssetIdentity.kt`: versioned adjacent `.slemeta` JSON UUIDs. Scanner assigns
  missing identities, preserves existing metadata, reports invalid/orphan/duplicate
  metadata and excludes ambiguous IDs from both lookup indexes. Ordinary dotfiles
  remain visible. Payload edits and payload+sidecar/project moves preserve UUIDs.
- `ProjectAssetImports.kt`: copies files/folders preserving nested module paths;
  reuses assets already beneath the project asset root. Name conflicts offer
  rename/skip/replace; folder replace merges and retains unmentioned files, file
  replace retains destination UUID. Metadata/ID/path preflight precedes writes;
  each payload replacement uses a streamed temporary file and atomic move.
- `ProjectAssetControls.kt`: background import/repair/reveal/open operations, native
  choosers and explicit collision/UUID-repair prompts. Dock Import targets selected
  folder. Inspector Browse reuses project files or copies images to Textures, Lua
  to Scripts and other files to selected folder. Standalone temporary/package
  workflows remain available. Browse guards edits and document/project switches
  during asynchronous copying.
- Browser detail actions expose UUID, Reveal, external open and confirmed new UUID;
  issues button shows all metadata problems. Sidecars are reserved metadata rather
  than asset rows. Scan/import/repair synchronization prevents internal temporary
  payloads from acquiring identities mid-write.
- Important boundary: existing component/preview/package codecs still receive
  resolved filesystem paths. At the step 3 checkpoint, plain project documents with UUID references
  remained step 4. No runtime project resolver/configuration change was made.
- Folder/batch writes are not one transaction: disk errors can leave earlier
  successful files. Symbolic links are rejected; two Windows symlink tests skip
  because this account lacks symlink privileges. Native C++ code was unchanged.

### Step 3 current verification (2026-11-13)

- Windows JDK 21 Gradle `build`: **73 tests, 0 failures/errors, 2 skipped**.
- RED/GREEN identity/import tests; review regressions reproduced manual-draft Browse
  ignoring selection and hidden `.sle-write-*` assets, then passed after fixes.
- `run --args=--asset-browser-smoke`: actual selected-folder Import/chooser, nested
  modules, source removal, collision rename, UUID stability, script reuse, texture
  Browse, plus all existing browser/gameplay/lifecycle checks passed. Initial UI
  runs failed during test development; user requested rerun after closing a window.
  Final full rerun passed, screenshot inspected.
- `run --args=--project-smoke`, `--camera-smoke`, `--context-menu-smoke`: passed.
- Independent read-only review found two issues; both reproduced, fixed and
  re-reviewed with no further confirmed defects.
- Everything remains uncommitted/unpushed, including prior steps 1/2.

## Completed step 4

- `project/ProjectDocuments.kt`: ordinary `.scene.json` / `.prefab.json` envelopes,
  version 1, `sle-project-scene` / `sle-project-prefab`, ordinary `document`, and
  explicit extra `assets` UUID list. Known component asset fields store
  `project-asset://<UUID>`; native codecs receive resolved paths. Script variables,
  names and entity keys remain data. Package SHA-256 references stay separate.
- `ProjectDocumentControls.kt`: worker-side source read/resolve/canonicalize/save,
  browser double-click, project File Open/Save/Save As, Export Package retaining
  source binding, and project prefab insertion into scenes. Existing standalone
  package workflows remain. No automatic conversion of packages/external assets;
  source saves require project-owned resources.
- Workspaces retain resolved documents for preview/Play and track source revisions.
  Stale saves refuse external edits/deletion; Reopen or Save As recovers. Loading
  rechecks draft edits and document identity. Saving records the written snapshot,
  keeping later edits dirty and its revision current. No automatic draft/runtime
  reload on disk changes. Missing/ambiguous references preserve the open workspace.
- New source names use the dedicated suffixes above. Source Save As refuses an
  existing destination; open that source first. Known asset fields become UUIDs,
  but nested asset formats/dynamic script/XML links still use existing loaders.
  Lua module search paths and native runtime project support remain unchanged.
- Current full Windows Gradle build: **82 tests, 0 failures/errors, 2 skipped**
  symlink fixtures (account lacks privileges). Full project-document desktop smoke
  passed including held-read/New-document preservation, Save, browser opens,
  shared assets, prefab insertion/undo, conflicts/discard, Lua Play, export and
  full-project relocation. Screenshot inspected. Fresh reviewer found one save
  race; reproduced/fixed with regression coverage. See plan ledger for other fixes.
- Existing `--scene-smoke`, `--camera-smoke`, `--context-menu-smoke`,
  `--project-smoke`, `--asset-browser-smoke` passed. Camera's first run failed
  its initial pan assertion; the unchanged rerun passed. Documentation links and
  `git diff --check` passed.
- All steps 1–4 remain uncommitted/unpushed. No C++ changes or native rebuild.

## Completed step 5

- New Systems `ProjectRuntime` reads the same descriptor/sidecars and portable
  scene/prefab envelopes, resolves UUID fields and validates the launch snapshot.
  It registers scenes by document key and UUID. Runtime owns the project context
  for deferred switches and dynamic Lua engine asset operations.
- `engine_app --project <descriptor>` applies game/window configuration without
  relying on cwd. CMake copies engine-owned shaders/UI/fonts beside the executable;
  `EngineConfig.engineAssetRoot` keeps those separate from project assets.
  `--frames N` is a bounded diagnostic run reporting its final active scene.
  Legacy `--config` and default engine.json paths remain supported.
- Project Settings exposes startup-scene selection. **Run Project** prepares the
  native snapshot on a worker and launches the saved startup in the existing
  canvas. Ordinary Play still runs current drafts. Dirty drafts survive project
  launch/Stop; logical dimensions/FPS/VSync apply in embedded playback. Window
  title/fullscreen mode belong to the standalone window; editor shell is retained.
- Lua require prepends configured asset-root `Scripts` and asset-root module
  patterns. Dynamic engine texture/audio/animation/component/prefab paths resolve
  root-relative strings/UUIDs. Nested XML/resource formats and raw Lua IO retain
  their loader contracts. Scene documents are snapshots; payload freezing/hot
  reload and distribution are excluded.
- Derived runtime prefab JSON lives in unique disposable `.sle/runtime` folders
  shared-owned by the launch context and removed on release. Native launch never
  assigns metadata or rewrites sources. A crash can leave disposable caches.
- Review found one P2: prefab override assets bypassed resolution. Reproduced with
  actual Lua UUID/relative Script overrides; component maps now resolve before
  instantiation, with errors contained in existing result semantics. The
  regression and complete post-review native suite passed.
- Native full build/CTest passed 71/71 after the review fix. Current Gradle full
  build passed 83 tests, 0 failures/errors, 2 Windows symlink fixtures skipped.
  Actual whole-project desktop smoke passed startup selector, dirty drafts,
  deferred scenes/modules, resolution/VSync, pause/resume/Stop, relocation,
  standalone unrelated cwd and missing-startup preservation. Screenshot inspected.
- Existing scene, context-menu, project settings, asset browser, project-document
  and camera desktop smokes passed, plus whole-project runtime after the review
  fix. Python
  scripting tool tests passed 21/21, Lua API stub check and diff whitespace passed.
- Legacy `engine_app.exe --config engine.json --frames 3` passed actual startup,
  Lua initialization, bounded frames and teardown after the final build.
- All work remains uncommitted/unpushed, including inherited steps 1–4.

## Step 6 managed operations (current session)

- `ProjectAssetOperations.kt`: create folder; collision-safe rename/move carrying
  sidecars; current descriptor startup and conservative saved-source UUID reference
  inspection; explicitly confirmed referenced deletion; receipts and payloads in
  `.sle/trash`, restore across restart/project relocation without overwrite.
- Scanner recognizes interrupted sidecar-only deletion before generating metadata.
  Native runtime never touches this recovery storage. Keep trash until recovery is
  no longer needed; do not treat it as a disposable cache.
- Browser New Folder/Recover Deleted and selected Rename/Move/Delete run filesystem
  work off-thread. Stop and use New in both authoring workspaces before managed
  mutation; owner input stays disabled through worker/asset-lock waits. Dynamic
  Lua/XML/module paths are warned about and require manual updates.
- Nine filesystem regressions added. Focused RED/GREEN and full Windows Gradle
  `build run --args=--asset-browser-smoke` passed: **92 tests, no failures/errors,
  2 skipped symlink fixtures**, plus actual dialogs/buttons and held-lock input
  exclusion. Screenshot inspected. Two fresh-review P2s reproduced and fixed.
- Current camera, context-menu, scene, project settings, project-document and
  whole-project runtime desktop regressions all passed. Camera initially failed its
  paused-image equality assertion; the unchanged rerun passed. Native code has not
  changed or been rebuilt in this resumed milestone. Documentation paths and
  `git diff --check` passed.
- Moves attempt rollback on ordinary sidecar failure; a process crash between
  file/sidecar moves may require manual repair. Recoverable deletion has persisted
  receipts and interrupted-deletion/restore coverage. Distribution followed in the
  next subsection.

## Step 6 runnable-folder distribution (current session)

- Build `sle_runtime_bundle` with Windows CMake `--config Release`. Prepared bundle:
  `build/debug/runtime/Release`, containing standalone exe, Release CRT/dependent
  DLLs, engine resources, notices and a checked SHA-256 runtime manifest.
- File → Export Game selects that prepared bundle then a NEW output folder.
  `ProjectDistribution.kt` copies saved descriptor/all assets/sidecars into sibling
  staging, validates through native ProjectRuntime, rechecks source/runtime/staged
  hashes, removes only staged .sle caches and publishes without overwrite.
  Source editor state/cache/trash stay in place; drafts/playback are not replaced.
- Delivery runtime at root, project at `game/game.sleproject` with configured asset
  root. No-argument executable selects its adjacent delivered project independently
  of cwd; explicit --config/--project preserve priority. No archive or asset pruning.
- Seven filesystem regressions; native no-config/explicit-config CTest; actual
  `--project-distribution-smoke` with menu/choosers, collisions, dirty preservation,
  native prefab-cache cleanup/source trash preservation, relocation, unrelated cwd,
  no-config Release launch and deliberate source-script failure independence.
- Current full native build/CTest: **72/72 passed**. Release bundle build passed.
  Full editor build: **99 tests, no failures/errors, 2 skipped** Windows symlink
  fixtures. Final distribution desktop smoke passed; screenshot inspected. Fresh
  review found no additional correctness defects after manifest integrity fix.
- Current compatibility desktop checks all passed: camera, context-menu, scene,
  project settings, project documents and asset browser/managed operations, plus
  whole-project runtime with distribution. Documentation paths and diff whitespace
  checks passed. Keep all changes uncommitted/unpushed.
- Limits: Windows x64 Release folder, saved files, writable game directory for
  derived prefab caches. No clean-machine test, installer, pruning, encryption,
  streaming or multiple runtime packs. Optional dedicated link/junction and case
  collision export fixtures are deferred; production checks reject these inputs.

## Remaining steps

No remaining implementation in the approved project foundation/step-6 scope.
All current desktop compatibility checks passed. Future distribution formats,
installers and streaming require their own task.

Follow the plan's acceptance criteria. Do not turn source projects back into
archives or make a central inventory control browser visibility.

## Historical verification of steps 1/2

Windows JDK 21 Gradle `build`: **63 tests, 0 failures, 1 skipped**. Symlink fixture
skips because this Windows user cannot create symbolic links. All other tests pass.

Actual desktop commands passed:

- `run --args=--asset-browser-smoke`: external add/move/delete, unknown files,
  tree/breadcrumb/double-click, refresh/show-hide, 400-file scan during gameplay,
  project switch/close/reopen.
- `run --args=--project-smoke`: settings invalid values and saving, local state,
  relocation, failed-open preservation, async dirty-switch rejection.
- `run --args=--camera-smoke`: camera/script/physics/pause/resume/stop.
- `run --args=--context-menu-smoke`: existing menus/entity/prefab navigation.

Fresh read-only reviews of both milestones returned no confirmed defects.
Screenshots inspected: `build/reports/asset-browser-editor.png` and
`project-foundation-editor.png` beneath `tools/entity-editor`.
No C++ behavior changed during these two milestones; no native rebuild needed.

## Tooling and live demo

Use Windows tooling; never run Linux CMake in the Windows build tree. Example:

```bash
/mnt/c/java/jdk-21.0.2/bin/java.exe -cp C:/projects/engine/tools/entity-editor/gradle/wrapper/gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain -p C:/projects/engine/tools/entity-editor -g C:/projects/engine/tools/entity-editor/.gradle-user-home build --console plain --no-daemon
```

Windows JDK GUI commands and `.git` writes have required tool escalation. Read
root AGENTS.md/CONTRIBUTING and affected topic docs. Native DLL is already built
at `build/debug/tools/editor-native/Debug/sle_editor_native.dll`.

Latest desktop smoke used this ignored test project (ordinary directories/files):
`tools/entity-editor/build/reports/asset-browser-1057719117988299230/Browser Game/Browser Game.sleproject`.
Launch via Gradle `run` with the project path quoted inside `--args`.
It has sample scripts, unknown file types and a 400-file Data/Large directory.
Smoke GUI closed itself after passing. Preserve any other editor's unsaved changes
and close normally if a
future native DLL rebuild requires it. No need to rebuild native code for Kotlin
asset import/UI changes alone.
