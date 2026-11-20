# Editor polish implementation plan

**Status:** Sessions 1–5 implemented and validated with Windows build/JUnit, native CTest and desktop smokes (2026-11-17). Session 6 is implemented and validated with JUnit and desktop smokes. Session 7 ran in a shortened form (versions, automated suite, commit); its manual walkthrough and visual checks are deferred until after the planned editor refactor; session 1 fresh-process manual restart validation remains outstanding.
**Request:** Project-first editor workflow, reliable scene persistence and selection commands, one local game export, and linked prefab authoring. Implement in separate sessions in the order below.

**Approach:** Extend the existing Kotlin Compose editor and native bridge. Keep the scene Inspector for ordinary entities; open prefab editing only on demand. Fix lifecycle and input before adding linked prefab behavior. A full shell rewrite would increase regression risk without helping the requested workflow.

**Execution:** Implement sessions inline using the executing-plans skill. Read this plan, repository instructions, and current git diff at each session start. Preserve pre-existing changes. Do not assume a checked-in plan proves a feature works. Update progress and actual validation after each session; leave a precise handoff.

## Acceptance criteria and proposed defaults

- New projects contain one saved, empty `Assets/Scenes/Main.scene.json`, registered as the startup scene and opened immediately. Empty means no authored entities; the independent editor camera remains usable.
- Reopening restores the last successfully opened scene; otherwise opens the configured startup scene. Missing/corrupt remembered paths produce a notice and a safe fallback, never silently replace a saved scene. Existing projects are not rewritten merely by opening them.
- File → New and Open contain Project and Scene; prefab authoring is available explicitly. Open Recent lists projects, persists across editor restarts, deduplicates normalized paths, and handles moved/deleted projects without losing the current workspace. Proposed cap: ten projects.
- Only Export Game appears as a game delivery action. Output goes to `<project>/Exports/<game-name>` with numbered destinations for repeat exports. Export uses saved content, reports that explicitly, and excludes generated exports/caches from input. Keep package support used by the runtime internally.
- Rename the maintained source directory `tools/entity-editor` to `tools/editor`, updating scripts, CI, VS Code, packaging, documentation and live references. Retain application identity and user settings. Do not rename generated build caches or rewrite historical records unnecessarily.
- Delete removes the selected scene entity and descendants as one undoable action. It works from Hierarchy and scene viewport, including native canvas focus and detached windows. Typing Delete in a text field edits text. Playback and modal dialogs block destructive editing.
- New visible entity/component presets use practical editor sizes. Proposed sprite default: 64×64 world units; collider/zone presets use coherent matching dimensions. Empty entities and camera defaults retain their semantics. Existing authored dimensions remain unchanged.
- Right-click empty scene viewport space offers creation at the clicked world position; right-click an entity selects it and offers creation plus entity actions. Share commands with the hierarchy. Do not intercept Game playback input with authoring menus.
- Drag a scene entity from Hierarchy or the scene viewport into an Assets folder to save a prefab and link its source instance. Give clear valid-target feedback; support cancellation and detached windows. Failed writes leave scene and asset index intact.
- No permanent Entity tab. Double-clicking a prefab opens a temporary prefab editing tab; ordinary selection uses Inspector. Closing the prefab tab returns to the scene and respects unsaved changes.
- Saving prefab edits updates linked instances. An instance context command **Apply Changes to Prefab** updates the saved asset and linked instances.
- Proposed override policy (not yet user-confirmed): preserve per-instance placement and explicit custom overrides. Applying an instance change promotes its component edits to the prefab while keeping its scene placement local. Never write unrelated scene files silently; refresh them from the saved prefab when next loaded.
- Prefab creation must preserve selected descendants. The present standalone editor supports only single-entity prefabs; extend that model before allowing subtree drops, rather than flattening or losing children.

## Current evidence and affected code

Paths below are relative to `tools/entity-editor` until session 4 renames it.

- `src/main/kotlin/sle/editor/project/ProjectFiles.kt`: creates folders but no scene; local editor state stores only UI scale.
- `project/ProjectControls.kt` and `app/Main.kt`: project activation resets documents; startup/last-scene restoration must be wired into successful activation rather than inferred from layout persistence.
- `project/ProjectDocumentControls.kt`, `ProjectDocuments.kt`: project document IO, revision checks, asset adoption and automatic save on switching.
- `project/ProjectDistributionControls.kt`, `ProjectDistribution.kt`: two delivery commands; automatic export currently writes beside the project in its parent's `exports` directory.
- `ui/SceneWorkspace.kt`, `ui/SceneTree.kt`, `viewport/ScenePicking.kt`, `app/Main.kt`: selection, deletion controls, canvas input and menu wiring.
- `model/SceneWorkspaceState.kt`, `EntityWorkspaceState.kt`, `SceneDocument.kt`: authoring state and history; examine import serialization before designing persistent instance links.
- `ui/AssetDrag.kt`: currently carries file paths from Assets to Scene, not scene entities to Assets. Extend with typed payloads or a separate entity drag coordinator; never encode entity references as fake paths.
- `project/assets/AssetBrowserPanel.kt`, `workspace/WorkspacePanels.kt`: asset destinations and document panel lifecycle.
- Engine prefab expansion already supports subtrees and overrides (`docs/topics/Prefabs.md`); editor persistence must remain compatible. Add orchestration in Systems only if native behavior changes are required.

## Session 1 — New/open project and scene recovery

- [x] Read project/document tests and trace activation, asynchronous document opens, close, and exit end to end.
- [x] Add failing tests in `ProjectTest.kt` and `ProjectWorkspaceDocumentsTest.kt`: new Main scene exists, its UUID is the startup scene, it is valid and empty; closing/reopening restores saved content; old editor-state files still parse; stale paths fall back; failed opens retain the current document.
- [x] Create Main through existing project document serialization/identity rules during staged project creation, before publishing the folder.
- [x] Extend `ProjectEditorState` with an optional project-relative last-scene path. Preserve the field on scale-only writes, including exit. Validate paths against project boundaries.
- [x] Load the remembered/startup scene after activation with stale-operation guards. Record successful scene opens/saves, not failed requests; separate project settings refresh from project switching.
- [x] Reorganize File New/Open and add a user-local atomic recent-project store (ten entries); add malformed-state, duplicate-path and missing-project coverage.
- [x] Verify focused project tests and automated desktop create/save/close/reopen/relocate/recent/stale-recovery/dirty-switch checks; update Projects and editor workflow docs.
- [ ] Manually verify a full editor process exit/restart and detached-window project switching. Disk state reload and exit-state save are covered by JUnit; these manual checks were not performed.

## Session 2 — Selection, Delete, practical sizes and creation menus

- [x] Trace Compose/AWT focus and keyboard dispatch before changing handlers. Add regressions in `SceneAuthoringUxTest.kt` / `SceneHierarchyRegressionTest.kt` for selected-subtree deletion and undo, including clearing invalid selection.
- [x] Route hierarchy and canvas Delete through one guarded command. Verify text editing, modal dialogs, playback, detached windows and key-repeat behavior interactively.
- [x] Set editor creation defaults (proposed 64×64 sprite and compatible collider/zone dimensions), without changing engine codec defaults or existing files. Cover creation and serialize/reopen dimensions.
- [x] Reuse `EditorContextMenu` creation commands at viewport world coordinates; preserve hierarchy parenting and prevent right-click opening from starting a gizmo gesture.
- [x] Extend context-menu smoke coverage; check zoom, picking and transform undo after creation/deletion. Update affected editor UI docs.

## Session 3 — One game export inside the project

- [x] Add failing `ProjectDistributionTest.kt` cases for project-local output, repeated exports, nested-output exclusion and failed staging cleanup.
- [x] Remove Build Game from editor menus and command UI; keep reusable package/runtime internals where required.
- [x] Change automatic output to `Exports` within project root. Audit source traversal before allowing nested output, including previous exports, caches and links. Never copy an export into itself.
- [x] Keep saved-content messaging and runtime-bundle lookup clear; preserve existing exports on failure.
- [x] Run distribution unit tests and relocated executable smoke with a prepared Release runtime. Update README, Projects and GamePackages descriptions.

## Session 4 — Rename the editor directory

- [x] Inventory live `entity-editor` references with `rg`, including dot directories, workflows, PowerShell, CMake bridge/resource paths and Gradle configuration.
- [x] Move tracked source to `tools/editor`, preserving all existing edits; update executable references and current documentation. Inspect untracked artifacts before moving anything.
- [x] Keep package namespaces, preference locations and stable dock identifiers compatible. Do not move machine-specific build output into source control.
- [x] Run Gradle tests from the new path, check script/task references, and build/package through supported Windows tooling. Document any clean rebuild needed for absolute cached paths.

## Session 5 — Linked prefab model and persistence

- [x] First trace editor prefab import/save against engine prefab declarations. Define stable asset UUID, instance key, local descendant key and explicit override representation using existing schemas where possible.
- [x] Add focused round-trip tests: two instances of one asset, subtree references, placement overrides, missing prefab, asset rename, duplication, scene reload and legacy expanded scenes. Existing unlinked entities remain independent.
- [x] Extend prefab authoring documents to preserve subtrees and internal references. Keep the standalone single-entity workflow compatible.
- [x] Implement prefab asset save and instance reconciliation as validated staged operations. On failure retain the previous asset and scene. Reconcile history/selection so no stale component references survive replacement.
- [x] Implement and test merge behavior: shared component edits propagate, explicit overrides survive, component removal works, root placement stays local, source changes do not recursively apply themselves.
- [x] Validate engine loading of saved scene/prefab output; add native coverage only if native behavior changes. Document persisted semantics and compatibility.

## Session 6 — Prefab drag, temporary editing and apply command

- [x] Build on session 5's model. Add typed entity drag payload with stable document/entity identity and revision validation; resolve entities only at drop time.
- [x] Add Assets folder drop targets for hierarchy and viewport drags, with cancellation, naming/collision handling and project-local asset adoption. Link the source only after save/index success.
- [x] Replace the permanent Entity tab with temporary prefab documents opened by asset double-click. Ordinary entity editing stays in Inspector; restore saved layouts containing the old tab safely.
- [x] Wire prefab Save and instance **Apply Changes to Prefab** to the same validated update service. Update currently open instances immediately, retain explicit overrides and mark changed scene data dirty where necessary. Closed scenes resolve updated assets on load.
- [x] Cover drag cancellation, stale payloads, failed writes, detached windows, save/close prompts, undo/redo, two instances, reopened scenes and cross-project asset isolation in tests/smokes.
- [x] Update Prefabs, editor workspace and authoring documentation.

## Session 7 — Full polish verification and handoff

- [ ] Run the complete editor JUnit suite and build from `tools/editor`.
- [ ] Run relevant native integration tests after building changed targets; run JNI/menu/project/document/distribution/docking smokes with supported graphics/runtime tooling.
- [ ] Manually walk create project → create entity → Delete/undo → drag prefab → duplicate → edit prefab → apply instance → save → close → reopen → Export → launch relocated game.
- [ ] Check dark/light appearance, narrow docks and detached windows for new menus/tabs/drop feedback. Record any untested platform behavior explicitly.
- [ ] Update editor version per CONTRIBUTING; update engine version only if engine behavior changes. Record remaining reproducible bugs as separate scoped follow-ups rather than declaring all possible bugs fixed.

## Validation commands and limitations

Supported Windows commands, from the editor directory (old name before session 4):

```powershell
.\gradlew.bat test --tests sle.editor.project.ProjectTest --tests sle.editor.project.ProjectWorkspaceDocumentsTest --no-daemon --console=plain
.\gradlew.bat test --tests sle.editor.model.SceneAuthoringUxTest --tests sle.editor.model.SceneHierarchyRegressionTest --no-daemon --console=plain
.\gradlew.bat test --tests sle.editor.project.ProjectDistributionTest --no-daemon --console=plain
.\gradlew.bat build --no-daemon --console=plain
.\gradlew.bat run --args=--context-menu-smoke
.\gradlew.bat run --args=--project-distribution-smoke
```

Confirm smoke flag names in current `Main.kt` before execution. Interactive JNI checks require the native bridge and graphics environment; relocated distribution smoke requires the Release runtime bundle. Visual Studio CMake caches must not be reused from Linux. In WSL, discover available Windows tooling and report unavailable checks honestly.

## Progress / handoff

- Planning: inspected repository instructions, README, CONTRIBUTING, constraints, editor docs, project creation/local-state code, export controls/destination, drag payload and relevant test inventory.
- Pre-existing edits: numerous editor files, Projects documentation and `docs/design/editor/SLE_Design_Prompt.txt`; an untracked starter-assets archive. All must be preserved. Capture fresh `git status --short` at execution time.
- Planning-session baseline: no product code changed and no build/test run at that time.
- Session 1 (2026-11-17): implemented staged empty Main creation with asset sidecar/startup UUID; optional project-relative `lastScene` local state with scale-write merging; recovery reads remembered then startup scene with notices and activation/revision guards; settings refresh preserves scene drafts; successful scene opens/saves record recovery state; normal exit saves scale without clearing recovery state. New/Open now include Project/Scene/Prefab; atomic user-local recent-project history keeps ten normalized paths. Smokes use isolated recent-history directories.
- Regression-driven fixes: staging must use `toRealPath()` before existing asset-boundary validation on Windows. Desktop dirty-switch smoke exposed a Compose redraw reentry from asynchronous modal confirmation; `confirmProjectChange` posts the prompt to the Swing event queue. Applied to project and document asynchronous re-confirmation.
- Validation, through Windows PowerShell from WSL, using `tools/entity-editor/gradle.ps1`:
  - `test --tests sle.editor.project.ProjectTest --tests sle.editor.project.ProjectWorkspaceDocumentsTest --tests sle.editor.project.RecentProjectsTest --no-daemon`: passed focused regressions. Initial Main and local-state-retention tests failed before their respective fixes; new service tests initially failed compilation until their APIs existed.
  - `test --no-daemon`: passed the full suite before final exit/legacy coverage.
  - `build --no-daemon`: final run passed; 213 tests discovered, 211 passed, 2 Windows link tests skipped (`AssetBrowserTest.linksAreShownWithoutTraversingExternalContents`, `ProjectAssetImportsTest.linkSourceAndLinkedDestinationAreRejected`).
  - `run --args=--project-smoke --no-daemon`: passed after fixing asynchronous confirmation and allowing menu state publication; checks new saved Main, settings preserving scene edits, scene save/recovery, recent-menu reopening, stale recovery, scale retention, relocation, failed opens and dirty-switch cancellation. Initial attempts failed on menu publication timing and modal redraw reentry; neither is reported as a pass.
  - `run --args=--project-documents-smoke --no-daemon`: passed save/browser-open/conflict/reopen/Play/script/snapshot/export/relocation/autosave-switch coverage. Fixture explicitly starts a new draft now that opening a project opens a saved Main.
  - `git -c core.whitespace=cr-at-eol diff --check`: clean. Existing CRLF files were preserved; plain `git diff --check` reports their CR characters as trailing whitespace.
- Independent read-only session-1 code review found no concrete correctness issues. The subsequent desktop-confirmation fix was verified by the previously failing project smoke and full build; no second review was requested.
- Ruling: execute in the existing `feat/project-foundation` checkout to preserve the current dependent edits; no commits, branch migration or merge. Later sessions and pre-existing export/UI edits remain untouched. Cost if wrong: these edits share one worktree and need scoped staging later.
- Remaining validation: manual full process exit/restart and detached-window switching. No C++ behavior changed, so no native rebuild/CTest was performed; desktop smokes used the available Debug bridge. Existing `painterResource` deprecation appeared during compilation.
- Next implementation session: session 2 only, after reading current status/diff. Session 1's remaining manual checks are recorded above. Override policy and exact new-entity sizes remain proposals; user corrections take precedence.

### Session 2 in progress — 2026-11-17

- Scope: session 2 only; execute inline in the existing dependent checkout and preserve pre-existing changes. No commits or branch migration.
- Ruling: use the proposed 64×64 sprite/box and radius 32 circle editor defaults. Apply only on adding components; parsed documents and native defaults remain unchanged. Cost if wrong: future newly created components need a different default.
- Focus trace: hierarchy has a focusable Compose tree with a local preview-key handler (text inputs live in sibling Inspector); native presenters forward keys to the shared owner after activating the correct view, including detached presenters. Modal AWT windows own keyboard delivery. Owner already gates editing on stopped Scene playback and excludes Game view. Delete currently selects the first survivor, allowing key repeats to delete unrelated entities.
- Ruling: deletion clears selection; undo restores the removed root selection/topology. A held Delete cannot walk into surviving entities. Preserve model reference checks.
- Viewport right-click already selects the hit entity and finishes gestures before opening a menu. Add a captured world position and pass it to shared root/child creation, converting child placement through its parent pose.
- Initial Windows test invocation failed due to sandbox WSL interop (`UtilBindVsockAnyPort`); retried through the required escalation mechanism.

- Implementation: shared `deleteSceneSelection` used by Hierarchy, canvas, Inspector and menu deletion; model removal clears selection, undo restores it. Editor-only component defaults and new Sprite/collider/zone presets; viewport captures the click's world position, model stages root/child creation with inverse parent positioning before history mutation.
- Regression evidence: deletion/size tests failed with assertions on old behavior, then passed. World-position and guarded-command tests initially failed compilation until the new API existed. Focused `SceneAuthoringUxTest` + `SceneHierarchyRegressionTest` passed. Initial full `build --no-daemon` passed: 217 tests, 215 passed, same two pre-existing Windows link skips.
- Desktop smoke work: added native/hierarchy repeated Delete, clicked-position creation, undo, text/modal checks, detached canvas Delete and running/paused menu/deletion guards. Native/hierarchy repeated Delete and world-position creation passed. Text check initially retained `Camera` with the correct entity/count, so no entity was deleted; fixture clicked immediately after structural undo; suspected stale scene-root Inspector input before the entity Inspector remount. Added Inspector remount/focus waits; confirmation rerun was blocked by the Windows lock screen (captured in `build/reports/editor-menubar.png`). Requested desktop unlock; do not count this smoke as passed.
- Read-only independent review of session-2 product changes found no concrete correctness issues. No merge/commit or later-session implementation.

- Final `build --no-daemon` after smoke additions passed (217 tests discovered, 215 passed, 2 existing Windows link skips). Whitespace check `git -c core.whitespace=cr-at-eol diff --check` passed. User unlocked the Windows desktop; smoke rerun resumed.

- Unlocked-desktop retry also captured the Windows lock screen (12:43) and failed the existing dark-menubar pixel check before new input checks. Requested a continuously unlocked session for the next run. Remaining text/modal/detached/playback checks and scene zoom/picking smoke are not yet validated; session 2 is implemented, validation incomplete.

### Session 2 desktop verification resumed — 2026-11-17

- After the user's second unlock, `run --args=--context-menu-smoke --no-daemon` passed. Verified clicked-world-position creation, native/hierarchy repeated Delete without cascading, structural undo, Inspector text Delete, modal focus, selection/cascading menus, child-camera creation, component editing, save/navigation, detached canvas Delete/undo, running/paused deletion guards and absence of authoring menus during playback. The Inspector remount/focus wait resolved the earlier smoke fixture timing failure; authored selection/count were preserved throughout.
- `run --args=--scene-smoke --no-daemon` passed: import-copy, picking/selection, movement, transform undo, structural changes, invalid-draft save rejection, save/relocate/open, UI scales 75/85/100 and component/Scene/runtime handoff. No native code changed; tests used the available Debug bridge.
- Camera smoke for zoom/Scene–Game switching is running. The prior lock-screen failures and text-fixture failure remain recorded as failures, superseded by the successful checks above.

- `run --args=--camera-smoke --no-daemon` passed: hierarchy/pan, Scene zoom while paused and Scene/Game switching, camera follow/input/scripts/physics, pause/resume/stop and save/open. Session 2 is complete; session 3 is next and was not started. Session 1's remaining manual process-restart/project-switch checks remain separate. No C++ rebuild or CTest was needed for these Kotlin-only changes.

### Session 3 in progress — 2026-11-17

- Execute session 3 inline in the existing dependent checkout; preserve prior edits, with no commits or branch migration. The existing plan is the implementation brief; bookkeeping stays here rather than introducing a second generated plan/ledger.
- Pre-flight: menu consumes distribution commands; automatic export consumes the existing staged exporter. Remove the Build Game command surface while retaining `GameBuild` and runtime package support. Session 4 directory rename is excluded.
- Ruling: allow project-local output only beneath the dedicated `Exports` directory, disjoint from the configured asset root; retain explicit external destinations for internal callers. Inventory continues to copy only the asset root, so prior exports, staging and project `.sle` caches are excluded structurally without dropping unknown authored assets. Reject custom asset roots overlapping `Exports`. Cost if wrong: such projects must relocate their asset root before automatic export.
- Baseline Windows distribution tests passed. Initial sandbox invocation failed with WSL interop `UtilBindVsockAnyPort`; rerun through escalation succeeded. New local-output tests failed against sibling output; corrected an overlapping-asset-root fixture to retain the old Assets directory while changing settings.

- Focused distribution run: 11 tests passed after four local-output/exclusion/cleanup/overlap regressions failed against the previous implementation. Full Windows `build --no-daemon` passed: 220 tests, 218 passed, 2 existing Windows symbolic-link skips. Existing `painterResource` deprecation remains. Runtime Release rebuild and relocated desktop smoke are still pending.
- Independent read-only review found no reproducible session-3 correctness issues in containment/link checks, numbering, cleanup, menu wiring, tests or documentation. No deferred findings. Source edits remain uncommitted, consistent with the dependent checkout workflow.

- First distribution smoke failed in its prerequisite `ProjectRuntimeSmoke` startup selector before export: the fixture assumed None/second/start, but session 1 introduced saved Main. Adjusted the actual menu keyboard selection for None/Main/second/start; product settings code is unchanged. Release `sle_runtime_bundle` build passed.

- Retry passed `PROJECT_DISTRIBUTION_SMOKE_PASS`, including actual repeated menu exports and relocated Release launch, then failed the prerequisite runtime fixture's final missing-startup assertion. That fixture used `activate` for a settings-only change, triggering session-1 document recovery and replacing its draft; changed it to the existing `refreshSettings` path used by product settings. Rerun pending; do not count the overall smoke command as passed yet.

- Final validation: Windows `gradle.ps1 build run --args=--project-distribution-smoke --no-daemon` passed (220 JUnit tests: 218 passed, 2 existing Windows link skips). Both `PROJECT_DISTRIBUTION_SMOKE_PASS` and `PROJECT_RUNTIME_SMOKE_PASS` were emitted. Verified sole game-export menu, automatic project-local output, saved content/dirty draft preservation, repeated export preservation, relocation, no-config Release launch from unrelated cwd, Lua module/scene switching and source independence. Runtime fixture fixes are verified by the previously failing checks; no product changes followed independent review.
- Commands actually run: focused distribution tests (baseline, RED, GREEN), full editor build, Windows `cmake --build build/debug --config Release --target sle_runtime_bundle --parallel 4`, distribution smoke retries, final combined build/smoke, and `git -c core.whitespace=cr-at-eol diff --check` (clean). README, Projects, GamePackages, editor README, runtime comment and live VS Code/CONTRIBUTING descriptions reflect the single export action.
- Session 3 complete. Next session is 4 (directory rename), only when requested. Session 1 manual restart/detached switching remains outstanding; clean-machine distribution testing remains outside this session. No C++ behavior changed and no CTest was run. Existing edits and starter archive remain preserved, with no commits/merge/branch migration.

### Session 4 completion / handoff — 2026-11-17

- Scope: rename maintained source to `tools/editor` and add the explicitly requested Ctrl+S shortcut. Session 5 is unstarted. Existing dependent edits and untracked authoring files moved intact; the unrelated starter-assets zip remains untouched. No commits or branch migration.
- Inventory: searched live references including hidden workflows/VS Code, scripts, CMake and Gradle. Updated executable paths and current documentation links. All 276 formerly tracked editor files have counterparts at the new path. Historical plans retain their original paths; this plan records the transition here.
- Ruling: retain `sle-entity-editor` Gradle identity, Kotlin package names, preference paths, screenshot names and dock IDs for compatibility. Old generated build/project/dependency caches stay at `tools/entity-editor`; root ignores keep those local. New outputs were generated in `tools/editor/build`. README documents a fresh build and the scripts' new local Gradle home. Validation reuses the old dependency cache explicitly, without copying absolute project/build caches.
- Ruling: Ctrl+S invokes the current File → Save action for the active scene or prefab; project documents therefore use existing project JSON persistence, validation, asset adoption and conflict handling. It applies to main/detached workspace windows and native viewport focus, excludes modal dialogs and playback, and respects project busy state. Save and Save As menus also disable while busy. Ordinary key repeat invokes Save once per press.
- New `SaveShortcutTest` first failed compilation for the absent shortcut API. After implementation, Windows `build createDistributable` passed: 222 tests, 220 passed, 2 existing Windows link tests skipped. Packaged app: `tools/editor/build/compose/binaries/main/app/SLE Editor/SLE Editor.exe`.
- Actual successful commands (Windows PowerShell from repository root, after dot-sourcing `tools/editor/find-jdk.ps1` and calling `Use-Jdk21`):
  - `tools/editor/gradlew.bat -p tools/editor -g tools/entity-editor/.gradle-user-home test --tests sle.editor.ui.SaveShortcutTest --no-daemon --console=plain` — RED, absent API.
  - `tools/editor/gradlew.bat -p tools/editor -g tools/entity-editor/.gradle-user-home build createDistributable --no-daemon --console=plain` — passed full build/tests/package.
  - `tools/editor/gradlew.bat -p tools/editor -g tools/entity-editor/.gradle-user-home run --args=--project-documents-smoke --no-daemon --console=plain` — passed desktop project/document/export/reopen smoke; first scene save now uses real Robot Ctrl+S with native canvas focus.
- Failed attempts: sandboxed Windows invocation could not open WSL process socket; rerun with approval succeeded. Passing another `-g` through `gradle.ps1` failed because the script already sets it; successful validation used the wrapper directly. These attempts are not counted as passing script execution.
- Independent read-only review: no important findings. Final: minor (deferred): releasing S outside the application after saving can leave repeat suppression armed, swallowing the next Ctrl+S press until release. Main/detached routing is checked in code and unit coverage; detached-window Ctrl+S was not manually exercised.
- No C++ behavior changed; no engine rebuild/CTest was needed. Packaging used existing Release native/runtime resources. Earlier manual process-restart checks remain outstanding. Next session: linked prefab model/persistence only when requested.

### Session 5 completion / handoff — 2026-11-17

- Recovery: an earlier session-5 attempt (13:32–14:30 file times) implemented most of the session but never updated this plan. This session audited that work against the checklist rather than redoing it. Pre-existing edits and the starter-assets zip are preserved; no commits or branch migration.
- Implemented model, from the earlier attempt and verified here: new `model/PrefabDocument.kt` (subtree prefab documents, `PrefabInstance` capture/reconcile, engine-compatible merge), linked instances in `SceneDocument`/`SceneWorkspaceState`, `ProjectDocuments.saveLinked`/`updatePrefab` (staged: scene validated before the asset is written), subtree packages in `PrefabPackages`, standalone subtree open/save in `EntityWorkspaceState`. Persistence uses standard scene `prefabs` declarations with `file: "project-asset://<UUID>"`, instance key, optional parent and local-key overrides. Native `ProjectRuntime::index` expands those declarations after writing derived prefabs (engine behavior change).
- Added this session: `LinkedPrefabPersistenceTest.legacyExpandedSceneStaysIndependentOfMatchingPrefabUpdates`. It is a characterization test and passed on first run (no RED).
- Independent read-only review found two confirmed bugs, both fixed with regressions that were seen failing first:
  1. A field-level `null` override (the editor's representation of a removed field/map key) was removed by the editor but stored as `null` by native `PrefabLoader` `merge`. Removing an Animator `targetEntities` key on an instance made the whole project fail to load (`internal reference must be a key…`). Fix: native merge erases null fields (RFC 7386 semantics, matching component-level null). Regression: `integration_prefabs_path` exit 21/22. Prefabs.md documents the field rule. Engine behavior change: session 7 must bump the engine version per CONTRIBUTING.
  2. Document-switch autosave prepared scene and prefab writers up front. The scene save changed the scene revision, so the prefab writer silently discarded its linked-scene refresh. The open scene then showed stale instance content as clean. Fix: `ProjectDocumentControls.openFile` prepares each autosave just before it runs. Regression: `ProjectDocumentsSmoke` dual-autosave step (failed on the old code, passes now).
- Deferred review suggestions, not confirmed as bugs:
  - Native accepts any `project-asset://` UUID as a prefab source without checking that it is a derived project prefab. The editor is stricter; scene UUIDs are rejected only incidentally by the root check.
  - Linked prefab sources are stored uncanonicalized while expanded entities are canonicalized. Hand-authored prefabs might then gain sticky overrides for fields canonicalization changes.
  - `replaceLinkedScene` resets every entity's asset map to `allAssets()` and drops unsaved field drafts, including entities outside the instance.
- Commands actually run (Windows PowerShell from the repository root; JDK via `. tools/editor/find-jdk.ps1; Use-Jdk21 -RepoRoot <repo>`; Gradle as `tools/editor/gradlew.bat -p tools/editor -g tools/entity-editor/.gradle-user-home …`):
  - Gradle `build`. The first run was up to date with 236 tests (234 passed, 2 skipped). The final run had 237 tests: 235 passed, 0 failed, 2 existing Windows link skips.
  - Focused `LinkedPrefabPersistenceTest`/`LinkedPrefabTest`/`LinkedPrefabWorkspaceTest`: passed.
  - `run --args=--authoring-smoke`: `LINKED_PREFAB_AUTHORING_SMOKE_PASS`, `PREFAB_PACKAGE_AUTHORING_SMOKE_PASS`, `ENTITY_AUTHORING_SMOKE_PASS`, both before and after rebuilding the bridge with the loader fix.
  - `run --args=--project-documents-smoke`: passed before the fix; failed at the new dual-autosave check with the fix reverted; passed with the fix.
  - `cmake --build build/debug --config Debug` (all targets, including `sle_editor_native`), then `ctest --test-dir build/debug -C Debug`: 76/76 passed. `integration_prefabs_path` failed first (RED) on the new null-field case.
- Not done: Release native/packaged rebuild, and Linux/WSL validation. The session 1 manual restart and detached-window checks remain outstanding. Apply Changes to Prefab, entity drag to Assets and temporary prefab tabs belong to session 6.
- Next session: 6 (prefab drag, temporary editing and apply command), only when requested.

### Session 6 in progress — 2026-11-17

- Scope: session 6 in the existing dependent checkout; no commits or branch migration. Pre-existing edits and the starter-assets zip preserved.
- Rulings:
  - The proposed override policy is used as the default (placement stays local; Apply promotes component edits). It is still not user-confirmed.
  - Undoing a link restores the original keys and entities but keeps the saved prefab asset.
  - The `entity` panel ID is kept, so saved layouts load, but it is now the temporary **Prefab** panel. It is hidden by default and listed under Windows only while a prefab is open.
  - Ordinary scene entities are edited only in the Inspector. Right-click **Edit** became **Add Component**; Edit → Edit Entity now selects and reveals the Inspector.
  - The viewport drag only becomes an entity drag once the pointer leaves the canvas. Dropping on a folder cancels the move first.
- Implemented:
  - `prefabFromSceneSubtree`/`SubtreePrefab.withSource`/`uniquePrefabPath`, `applyInstanceToPrefab`/`PrefabInstance.applied` (PrefabDocument.kt).
  - `linkSubtree`, `linkedInstance`, `assetsOf`, draft-preserving `replaceLinkedScene` (SceneWorkspaceState.kt).
  - `createPrefab`/`applyPrefab` reuse `ProjectDocuments.save`/`updatePrefab` (ProjectDocumentControls.kt), with `pathOf` and `requireLinkedSource` in ProjectDocuments.
  - `EntityDrag` (ui/AssetDrag.kt), Hierarchy row drag (SceneTree.kt), viewport hooks (NativeViewportOwner.kt), Assets folder targets (AssetBrowserPanel.kt), `dropEntity` wiring (Main.kt).
  - Temporary panel flag, `closeRequest`, `prefabSession` and `closePrefabTab`.
- Independent read-only review found three confirmed bugs, all fixed:
  1. A committed field left text in `drafts`, so `linkSubtree` refused after the asset was written, leaving orphan prefab files. The check was removed; callers already compare full revisions.
  2. Apply rebuilt the whole prefab from the link's old source, so it could overwrite newer asset content. `requireLinkedSource` now refuses unless the disk content, canonicalized, matches the link source.
  3. An old layout with an active Entity tab still opened an empty Prefab tab through `revealContext`. Activation now switches modes only during a prefab session.
  - Also took its suggestion: numeric Animator targets to the subtree root are refused too.
  - Three regressions were RED with fixes 1, 3 and the numeric change reverted, and GREEN after restoring them. The stale-source guard is new API; its test was not observed RED.
- Deferred review suggestions:
  - Scene Undo of a link restores the old entity states, so later component edits on re-keyed or retargeted entities are lost on undo.
  - `adoptExternalAssets` copies external files before validation, so a failed create/apply can leave copies in Assets (same as the existing Save path).
  - Reset Layout hides the Prefab panel without the close prompt. Nothing is discarded, and it stays reopenable from Windows while the session lasts.
- Commands actually run (Windows PowerShell from the repository root, Gradle as in session 5):
  - `build`: final run 247 tests, 0 failures, 2 existing Windows link skips.
  - The focused model/persistence/lifecycle tests, and the RED run described above.
  - `run --args=--project-documents-smoke`:
    - Attempt 1 failed at an existing early step (discard prompt never appeared).
    - Attempt 2 passed the existing smoke, then the new workflow smoke failed: Hierarchy row bounds were missing because the previous smoke ends in the Prefab tab. Fixed by showing the Scene view first.
    - Attempt 3 failed at an existing early step. Its screenshot was solid black, and `LogonUI` is running: the Windows desktop is locked. Attempt 1 is likely the same cause, but that is not proven.
  - None of these desktop runs count as passing.
- Pending, needs an unlocked desktop: `--project-documents-smoke` (now including `PrefabWorkflowSmoke`), plus `--context-menu-smoke`, `--scene-smoke` and `--docking-workspace-smoke`, all changed for the removed Entity tab. No C++ changed in session 6.

### Session 6 completion / handoff — 2026-11-17

- Desktop validation, after the user unlocked the desktop and closed a stray Chrome dialog:
  - An earlier run's Ctrl+S reached Chrome, because the editor stays on top but does not own the keyboard. Smokes need an idle desktop.
- A real UI bug found by the new smoke, fixed:
  - The entity-drag hint was inserted as a new line in the Assets panel, shifting every folder row under the pointer mid-drag. The first real drop landed in `Data` instead of `Prefabs`.
  - The hint now replaces the toolbar item count, so nothing moves.
  - Create/Apply also now report busy or playing refusals instead of returning silently.
- Docking smoke fixture: its generic per-panel loop needs a prefab session before re-showing the temporary panel, because closing it ends the session.
- Pre-existing fixture regression from session 2, now fixed:
  - Symptom: `--gizmo-smoke` and `--authoring-ui-smoke` failed (collider picking, missing collider pixels).
  - Evidence: the git-HEAD editor (temporary worktree, same Debug bridge) passed both. The current tree also passed with session 2's practical creation sizes temporarily disabled.
  - Cause: the practical 64×64 / radius-32 defaults make those fixtures' sprites dwarf their 2×4 colliders. Session 2 had not run these smokes.
  - Fix: fixtures use `addComponentAtEngineSize`, which keeps the exact catalog defaults. The product defaults are unchanged.
- Final passing commands (Windows PowerShell, repository root, Gradle as in session 5):
  - `run --args=--project-documents-smoke`: `PROJECT_DOCUMENTS_SMOKE_PASS`, then every `PREFAB_WORKFLOW_STAGE` (hierarchy-drag, cancel-stale, viewport-drag, apply, prefab-tab) and `PREFAB_WORKFLOW_SMOKE_PASS`.
  - `--context-menu-smoke`: `CONTEXT_MENU_SMOKE_PASS` (…/no-entity-editor/…), plus auxiliary/keyboard/detached-delete passes.
  - `--scene-smoke`: scene editor, UI scale, `SCENE_TREE_COMPONENT_HANDOFF_PASS` (now Inspector-only), sandbox switch.
  - `--docking-workspace-smoke`: `DOCKING_WORKSPACE_SMOKE_PASS` and its sub-passes.
  - `--camera-smoke`, `--preview-smoke`, `--gizmo-smoke`, `--authoring-ui-smoke`: pass.
  - Final `build`: 247 tests, 0 failures, 2 existing Windows link skips. `git -c core.whitespace=cr-at-eol diff --check` reports only CRLF notices.
- Failed attempts, not counted as passes:
  - Two locked-desktop runs, and one with Chrome focused.
  - The first workflow run (Prefab tab active, so no Hierarchy bounds), and the wrong-folder drop above.
  - The first docking run, and the first gizmo fixture attempt (catalog omits `size`, so patching fields left 64×64).
- Not done:
  - The session 1 manual restart and detached-window checks.
  - Release/package rebuild, and Linux.
  - Deferred review suggestions (listed above).
  - Override policy still not user-confirmed.
  - No C++ changed in session 6. The engine version bump from session 5's merge change is due in session 7.
- Next: session 7 (full verification and handoff), only when requested.

### Session 7 (shortened) — 2026-11-17

- User decision: run a short session 7 now and commit before a large planned refactor. The manual walkthrough and the dark/light, narrow-dock and detached-window visual checks move to after that refactor.
- Versions per CONTRIBUTING:
  - Engine 0.16.0 → 0.17.0: session 5 adds linked-prefab expansion in `ProjectRuntime` and the null-field override rule.
  - Editor 0.8.0 → 0.9.0: session 6 adds drag-to-prefab, Apply and the temporary Prefab tab. The 0.8.0 bump was already in the uncommitted earlier sessions.
  - Example strings in CONTRIBUTING/`Main.kt` and the self-contained `GameBuildTest` fixture versions are unchanged.
- Automated run after the bump:
  - `cmake --build build/debug --config Debug` (all targets), then `ctest --test-dir build/debug -C Debug`: 76/76 passed. The generated `Version.hpp` reads 0.17.0.
  - Editor `build`: 247 tests, 0 failures, 2 existing Windows link skips.
  - Desktop smokes were last run in session 6, before the version bump; not repeated.
- Committed on `feat/project-foundation` as two commits (engine, then editor and docs). Not pushed.
  - Left uncommitted: the starter-assets zip, and the user's new refactor plans (`docs/plans/editor-core-l1.md`, `editor-game-workflows.md`).
- Still open:
  - Session 1 manual restart and detached-window checks; Release/package rebuild; Linux.
  - Deferred review suggestions from sessions 5–6.
  - The override policy, still not user-confirmed.
