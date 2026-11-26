# Milestone: Automatic Game Packaging and Content Updates

**Status:** Sessions 1–4 implemented and verified  
**Goal:** Export a runnable game with one editor command. The editor assigns assets to stable packages, builds the delivery, and can export a smaller update against a selected release.  
**Architecture:** Kotlin plans package ownership from saved project dependencies and records a release inventory. The existing C++ runtime mounts the resulting package set and resolves references across it.  
**Tech stack:** Kotlin/Compose Desktop, JNI, C++20, version 1 STORED `.slepkg` archives, prebuilt Windows x64 Release runtime.  
**Design:** The decisions and acceptance criteria below define this milestone.

## Current state

- [Export Game](../topics/Projects.md#export-a-runnable-game) publishes a runnable copy of the loose asset tree. Keep its single menu entry, bundled runtime selection, background job, and numbered output folders.
- [GameBuild](../../tools/editor/src/main/kotlin/sle/editor/project/GameBuild.kt) already writes a reproducible single package, copies and names the executable, writes its config, validates a staged build, and publishes without overwriting.
- [ProjectRuntime](../../EngineModules/Systems/src/ProjectRuntime.cpp) already mounts several packages and indexes project asset UUIDs across them. Duplicate asset UUIDs or scene keys fail. The startup package supplies gameplay settings.
- [Game Packages](../topics/GamePackages.md) documents today's package contract. The earlier [packaged builds plan](game-packages.md) records the initial implementation.

## Design and acceptance criteria

### Package ownership

- Use one scene package per saved scene, identified by the scene UUID. Keep its filename and package ID stable across scene renames. Use the display name in the editor and release report.
- Use a stable shared package. Traverse references from each scene through linked Entity Templates, nested assets, behaviour parameters and overrides, explicit asset lists, tile layers and tilesets, and other supported typed resource references. Track visited assets per scene so cycles terminate and repeated references do not inflate usage.
- An asset reached by one scene belongs to that scene. An asset reached by multiple scenes belongs to Shared, including dependencies reachable through a shared template. Keep unreferenced assets, scripts with dynamic path contracts, and assets with ambiguous ownership in Shared unless they must be colocated to preserve physical file lookups.
- Keep payloads with their identity, sprite-region and other companion metadata. Move each group once. Preserve project-relative names, lookup semantics and texture defaults. Ordinary known missing dependencies fail with the owning file and unresolved reference. Runtime-computed paths retain their existing semantics within the supported project asset root.
- When building against a release, Shared ownership is sticky. Keep prior private ownership if its scene still exists and no new cross-scene or compatibility requirement forces a move. Promote a private asset when it becomes shared. A removed scene releases ownership to a remaining sole consumer or Shared, according to the same rules.
- Only the startup scene's package declares `mainScene`. That package carries project-wide runtime settings. Apply per-package texture defaults consistently across the set. Use a deterministic mount order and reject any emitted path or UUID collision across the new set.
- Keep version 1 package limits. If a planned package exceeds a limit, report the package, limit and offending file where applicable. Automatic size-based subdivision is a separate feature.

### Build and update

- **Export Game…** produces a game-named executable, runtime DLLs/resources/notices, `config.json`, the package set, and a versioned release manifest. The game boots after relocation and from an unrelated working directory. The full delivery uses saved project data and a prebuilt runtime.
- A release manifest records project and runtime identity, package IDs and paths, SHA-256 digests, and ownership by asset UUID. Keep package config and release inventory reproducible for identical saved input and the same ownership baseline. Keep existing folder, single-package and explicit launch paths compatible.
- **Export Content Update…** takes an explicit earlier release as its baseline and uses the same project, Windows target, and exact shipped runtime identity. It emits only new or changed whole packages, the target config and release manifest, the expected base identity, and an explicit list of obsolete package paths. An incompatible baseline directs the user to full export. Repeating an unchanged build reports no content changes.
- Apply the update to a new staged copy of the base delivery, validating the recorded base files and the reconstructed target package set before publishing a sibling runnable delivery. Preserve the base install for rollback. A promotion changes the old owner and Shared together, plus any scene package whose content changed. A removed package must disappear from the target config and the staged delivery.
- Keep packaging local and automatic. The release milestone covers whole-package replacements and a local apply workflow. Remote delivery, runtime hot patching, binary deltas, downloadable DLC, compression and new platform bundles are outside this milestone.

## Review focus

- Two scenes reach a nested template and its texture through different paths. Both assets have a single shared owner.
- A path read relative to its containing XML or other asset must resolve after export, including when that asset and its target would otherwise land in different packages.
- A scene rename, one scene removing a shared reference, and reordered inventory input leave package identities stable.
- A private asset promoted to Shared cannot leave a duplicate UUID in an updated delivery. A removed scene cannot leave a package in the active configuration.
- Corrupt baseline, locked file, and failed target validation leave the base runnable and publish no target directory.

## Sessions

Use the [separate-session handoffs](automatic-game-packaging-handoffs.md) to start each implementation session.

Run the focused tests for each session and add a progress entry with commands, results, decisions and the next session. Add regression tests before each implementation slice. Update topic docs alongside user-visible behavior.

### Session 1 — Dependency inventory and ownership planner

**Files:** Add `tools/editor/src/main/kotlin/sle/editor/project/PackagePlanner.kt` and `tools/editor/src/test/kotlin/sle/editor/project/PackagePlannerTest.kt`. Reuse project reference extraction from `ProjectDocuments.kt` and the asset operations index where their semantics match.

- [x] Define the planner input as the saved asset inventory with UUIDs, companion groups, typed reference edges, scene UUIDs and optional prior owners. Return ordered package assignments, an asset-to-package owner map and diagnostics with ownership reasons. Keep filesystem discovery outside the pure planner.
- [x] Test Forest and Castle referencing Enemy through different nested templates. Assert Enemy and its texture are in Shared once, private props follow their scene, sidecars follow payloads and a cycle terminates.
- [x] Test reordered input, a scene rename, a removed reference to a shared asset, and a removed prior owner. Assert stable output for the first three and deterministic reassignment for the fourth.
- [x] Extract references from scene and template envelopes, linked templates and overrides, behaviour parameters, explicit asset lists, tilesets and supported resource schemas. Test traversal through native tile data and a missing UUID diagnostic.
- [x] Keep unknown and dynamic dependencies conservative. For supported file-relative formats, record the resources that require colocation. Where target discovery is impossible, preserve the needed directory scope as a group or use the compatibility handling in Session 2.
- [x] Run `.\gradlew.bat test --tests sle.editor.project.PackagePlannerTest` from `tools/editor`.

### Session 2 — Multi-package build and runtime compatibility

**Files:** Modify `tools/editor/src/main/kotlin/sle/editor/project/GameBuild.kt`, `ProjectDistribution.kt` as needed for shared staging helpers, and `GameBuildTest.kt` under the matching test tree. Extend the native package tests and use `EditorBridge.cpp`'s existing `prepareGame` entry point.

- [x] Add an internal build path that consumes the planner result and writes the assigned packages using `GamePackageWriter`. Derive the Shared package ID from project identity and each scene package ID from project and scene identities. Publish the release manifest with the full delivery. Retain single-package fixtures as compatibility coverage.
- [x] Audit how Lua modules, Engine asset APIs, and supported nested file formats resolve paths. Test UUID lookup, dynamic project-relative lookup, Lua `require`, a file-relative XML dependency and a tileset in a separate package. For direct filesystem-relative readers, test colocation. Use an engine-side resolver only where necessary to preserve a supported lookup, and add coverage through that reader.
- [x] Stage the exact shipped package set and validate through `NativeEngine.prepareGame`. Check source and staged hashes before publication. Exercise package limits, deterministic output, settings on a non-first startup package, stale sources, collisions and occupied destinations.
- [x] Run `.\gradlew.bat test --tests sle.editor.project.GameBuildTest`. Build `engine_app`, `sle_editor_native` and the relevant tests, then run `ctest --test-dir build/debug -C Debug -R "integration_(game_package|package_discovery|package_launch)_path" --output-on-failure`.

### Session 3 — Editor export with automatic package assignment

**Files:** Modify `ProjectDistributionControls.kt` and the existing distribution and document command wiring under `tools/editor/src/main/kotlin/sle/editor/`. Extend `smoke/ProjectDistributionSmoke.kt`, [Projects](../topics/Projects.md) and [Game Packages](../topics/GamePackages.md).

- [x] Route **Export Game…** through inventory, ownership planning, package build and staged validation. Use the existing runtime selection, worker and notices. Keep numbered destinations inside `Exports/` and enforce the existing asset-root/runtime overlap restrictions there.
- [x] Offer to save dirty documents before exporting or explicitly use the saved versions. Report the output folder, package count and package sizes, with an explanation when a compatibility constraint forces assets together.
- [x] Extend `--project-distribution-smoke` to export a tilemap project with two scenes sharing a template, relocate the delivery, and launch its named executable with `--frames 8` from an unrelated cwd. Assert that the output contains the packages and packaged config and that a failed export preserves earlier deliveries.
- [x] Build the Windows Release `sle_runtime_bundle`. From `tools/editor`, run `.\gradlew.bat test` and `.\gradlew.bat run --args=--project-distribution-smoke` with the prepared bundle.

### Session 4 — Content update export and local apply

**Files:** Add `GameContentUpdate.kt` and `GameContentUpdateTest.kt` alongside the project build files. Extend the distribution controls and packaged-export smoke.

- [x] Compare a target build with the selected release using the versioned inventory. Feed baseline owners into the planner. Derive added, replaced, removed and unchanged package paths by digest, with a checked base identity covering the package set and launch config. Include changed config and the complete target inventory in any nonempty update.
- [x] Test a Forest-only edit, a shared Enemy edit, an ownership promotion, a removed scene package, a config-only change and a no-op. Assert exact package lists and that unchanged packages retain their digests. Reject wrong project/runtime and missing or modified baseline files.
- [x] Implement **Export Content Update…** and **Apply Content Update…** with the existing background worker and project notices. Apply to a sibling delivery by referencing only verified files from the selected base and update. Validate the reconstructed config, package hashes and native mount before no-overwrite publication. Test tampering, missing files and interrupted staging with no published target.
- [x] Run `.\gradlew.bat test --tests sle.editor.project.GameContentUpdateTest` and the distribution smoke. Launch both base and updated deliveries and verify the old build still works after an update.

### Session 5 — Release acceptance

**Files:** Update the export and update workflows in the guide, editor README and topic docs. Record actual results here.

- [x] Run the full editor test suite and Windows CTest suite, including windowed tests. Exercise a two-scene project with a shared template, a shared spritesheet, native tile collisions, Lua module loading and XML UI through build, launch, update and rollback.
- [ ] Verify the packaged Windows editor uses its bundled runtime. Verify a relocated full build and an updated build launch from an unrelated cwd on a Windows machine without the source project or development tools.
- [x] Confirm older loose exports and single-package builds still launch. Document package layout, saved-file behavior, manifests, baseline selection and any remaining compatibility limits.

## Progress

- Planned on 2026-11-25. The current plan uses whole-package replacement with one owner per UUID and an explicit release baseline. Staged sibling delivery is the proposed local update mechanism so that a failed update leaves the original runnable.
- Sessions 4–5 and milestone acceptance tests were pending at planning.

### 2026-11-25 — Session 1 handoff

- `SavedPackageInventory.read` takes the canonical asset root and reads saved
  files and existing identities through the distribution inventory. It returns
  file digests, UUIDs, companion paths, scene names and dependency edges. The
  planner takes that inventory and optional prior owners independently of the
  filesystem. Scene UUIDs are the stable owner keys. Shared has its own owner
  key. Output order is Shared, then scene UUID order, with sorted paths per
  package. The ordered assignments and owner map are the inputs for Session 2
  to derive package IDs, filenames and the release inventory.
- Ownership records the reason for each assignment. Traversal visits each
  asset at most once per scene. Physical-file edges form colocation groups.
  Shared stays sticky and prior private ownership survives while its scene
  exists, subject to sharing and compatibility constraints. An inventory with
  missing known targets yields sorted diagnostics with the source path and
  original reference. Session 2 must reject any such plan before writing
  packages and enforce the version 1 writer limits per assignment.
- Project component references share the existing document field traversal.
  Saved links, template overrides, explicit UUID lists and native tileset to
  spritesheet links add dependency edges. Behaviour parameters are scalar
  values. Only explicit `project-asset://` values count as known dependencies.
  Lua files go to Shared. Embedded Tiled image paths within the saved asset root
  use file-relative colocation. The existing UI reader receives layout, font
  and behaviour as separate component references. Session 2 still needs to
  exercise Lua dynamic paths, module loading, and file-relative reads through
  the shipped runtime, including any fallback path used outside that local
  Tiled image lookup. Nested template edges are supported by the planner;
  runtime compatibility with nested project template sources remains part of
  staged native validation.
- Before implementation, `./gradlew test --offline --tests sle.editor.project.PackagePlannerTest`
  reached test compilation and failed on the missing planner types. During
  implementation, the same command exposed a Shared display-name lookup on a
  sorted scene map. After the fix, `./gradlew test --offline --tests 'sle.editor.project.*'`
  passed on Linux and `cmd.exe /c gradlew.bat test --tests sle.editor.project.PackagePlannerTest`
  passed all three focused tests on Windows. Commands ran from `tools/editor`.
  `git diff --check` passed. Native package mounting and export smoke coverage
  belong to later sessions.
- Next is Session 2, internal multi-package build and staged native validation.


### 2026-11-25 — Session 2 handoff

- `GameBuild.buildPackages(project, runtimeBundle, destination, priorOwners)`
  reads saved files and applies the planner. The prior owner map is keyed by
  asset UUID and uses the planner's Shared or scene owner keys. Shared mounts
  first, followed by one package per saved scene in UUID order. Package IDs
  derive from the canonical project UUID and owner key. Scene names are
  display labels. The startup package alone declares `mainScene` and receives
  gameplay settings. All packages receive the project texture default.
- `sle-release.json` version 1 records project and platform identity, the
  SHA-256 of the selected `sle-runtime.json`, the launch config hash, ordered
  package records with ID, path, SHA-256 and nullable scene UUID, and an
  asset UUID to package ID map. The runtime manifest identifies its complete
  verified bundle contents. The shipped file hashes, including the release
  inventory, remain in `sle-distribution.json`.
- The default validator calls `NativeEngine.prepareGame` on the exact staged
  config and releases the prepared handle synchronously. Validation runs
  before the source and staged hash rechecks and no-replacement publication.
  Tests can inject a validator to exercise stale sources, staged tampering
  and a concurrently occupied destination. The single-package entry point
  remains available for compatibility fixtures.
- Runtime coverage mounts a tileset and its sheet across a package boundary
  from the startup scene, resolves a UUID-linked XML layout and parses it
  through the existing UI reader, and launches Lua that uses `require` and
  a computed project-relative Engine prefab path. The UI reader takes layout,
  font and behaviour paths as separate UUID-capable component fields and
  parses inline XML. It has no XML file-relative include feature. The legacy
  Tiled reader uses a file-relative image path. The build test verifies that
  a TMJ and its image land in the same archive. Canonical project scenes use
  native tile layers and reject the legacy `tiledMap` field, so the build
  test does not exercise the legacy reader on mounted assets. TMJ relative
  paths require matching filename spelling in the saved inventory. Native
  project prefab envelopes accept flat entity hierarchies and scene-level
  links. Nested prefab declarations inside a prefab envelope remain outside
  that runtime format even though the planner can traverse their edges.
- Windows validation built `engine_app`, `sle_editor_native`,
  `integration_game_package_path` and `integration_project_play_path` with
  `cmake --build build/debug --config Debug --target engine_app sle_editor_native integration_game_package_path integration_project_play_path --parallel 4`.
  The package fixture was rebuilt after its changes. `ctest --test-dir build/debug -C Debug -R package.*path --output-on-failure`
  passed all four matches, including the windowed package launch.
  From `tools/editor`, `gradlew.bat -g .gradle-user-home -Pkotlin.incremental=false test --rerun-tasks --tests sle.editor.project.GameBuildTest --tests sle.editor.project.PackagePlannerTest --tests sle.editor.project.ProjectDistributionTest`
  passed 29 tests. The run set `SLE_GAME_BUILD_TEST_NATIVE` to the built
  Debug DLL and the test report confirmed execution through `prepareGame`.
  `git diff --check` passed.
- Next is Session 3, editor export and the relocated packaged-game smoke.

### 2026-11-25 — Session 3 handoff

- File → Export Game now calls `GameBuild.exportAutomatically`. It shares the
  numbered destination selection with the original loose exporter and runs
  packaged native validation before publishing. The result reports the named
  executable, package display names and exact byte counts, and paths grouped
  for file-relative lookups.
- Save and Export uses the normal project document writers. It is offered when
  all dirty documents have project files and editing is available. Export
  Saved Versions uses the saved inventory and keeps the active drafts. Saving
  a template refreshes its open linked scene, so Save and Export saves that
  scene again if the refresh made it dirty. The added desktop case reproduced
  the abort after the template save before this fix. Editor version advanced
  from 0.32.0 to 0.33.0 for the export change.
- The Windows Release bundle built with
  `cmake --build build/release --config Release --target sle_runtime_bundle --parallel 4`.
  The focused offline regression initially failed compilation on the missing
  automatic package export API, as expected. The initial combined Windows
  test/smoke run closed the editor during runtime setup before export. A
  separate desktop run passed the runtime and packaged delivery checks.
- The export smoke covers the real menu, dirty document choices, numbered
  destinations, source-independent Lua, both relocated executables launched
  from an unrelated working directory, a shared template used by two tilemap
  scenes, and an export rejected for a missing spritesheet. The failure check
  compares earlier delivery inventories before and after the attempt.
- The offline HTML guide and editor README are scheduled for the release
  acceptance documentation pass in Session 5.
- Final Windows checks, run separately from `tools/editor`, were
  `gradlew.bat -g .gradle-user-home --console=plain test` (441 passed, three
  Windows link tests skipped, zero failures) and
  `gradlew.bat -g .gradle-user-home --console=plain run --args=--project-distribution-smoke`.
  The latter printed both distribution and runtime pass markers, including the
  linked-template save case. `git diff --check` and a local link check on both
  updated topics passed.
- Next is Session 4, content update export and local apply.

### 2026-11-25 — Session 4 handoff

- Added the editor's Export Content Update and Apply Content Update commands
  and advanced the editor version to 0.34.0. Export uses saved input and the
  selected release's owners. Update version 1 records both base manifest
  hashes, target file hashes and obsolete package paths. Apply verifies the
  clean base and stages a sibling delivery from the target inventory. Native
  mounting and a second staged-file check run before publication.
- A clean delivery is required for a baseline. Unexpected, missing and modified
  files are rejected. The exact shipped runtime is compared by inventory and
  by bundle manifest digest. Update export follows the existing project
  `Exports/` guard. Applying keeps the original delivery available for rollback.
- Windows validation from `tools/editor` used
  `gradlew.bat -g .gradle-user-home --offline --console=plain test --tests sle.editor.project.GameContentUpdateTest --tests sle.editor.project.GameBuildTest --tests sle.editor.project.PackagePlannerTest`
  and passed 21 tests with zero skips or failures. The regressions cover added
  and removed scenes, ownership promotion, config-only and unchanged builds,
  damaged inputs, failure during staged validation, and a destination occupied
  before publication. The tests were authored first. The initial pre-change
  run stopped at Gradle startup because of the sandbox socket restriction.
- The first Windows smoke reached the new flow and rejected the fixture output
  outside `Exports/`. After the fixture used the editor's output location,
  `gradlew.bat -g .gradle-user-home --offline --console=plain run --args=--project-distribution-smoke`
  passed with both distribution and runtime markers. It exercised the two
  update menus and launched both the relocated base and updated tilemap
  deliveries for eight frames from an unrelated working directory.
- Session 5 release acceptance remains open.

### 2026-11-25 — Session 5 release acceptance

- Windows Debug: `cmake --build build/debug --config Debug --parallel 4`
  succeeded. `ctest --test-dir build/debug -C Debug --output-on-failure`
  passed 82 of 82, including all 17 windowed tests. Native coverage included
  legacy loose delivery discovery, package launch and scene switching, native
  tile collision, and loading and parsing a packaged XML layout.
- From `tools/editor`, `cmd.exe /c gradlew.bat -g .gradle-user-home --offline --console=plain test`
  ran 447 editor tests with 444 passes, three skips, and zero failures.
- `cmake --build build/release --config Release --target sle_editor_native sle_runtime_bundle --parallel 4`
  succeeded. From `tools/editor`, `cmd.exe /c gradlew.bat -g .gradle-user-home --offline --console=plain createDistributable run --args=--project-distribution-smoke`
  built the Windows editor app and printed both distribution and runtime pass
  markers. The development editor smoke used the actual export and update menus,
  preserved prior exports, built and updated two tilemap scenes sharing a
  template and spritesheet, and checked Lua module loading and a deferred scene
  transition on the separate project-runtime fixture.
- `python3 build/packaging-session5/prepare.py` verified that the packaged
  editor's app resources contain a byte-for-byte copy of the Release bundle,
  copied the smoke deliveries to a separate directory, and generated a legacy
  single-package compatibility fixture. Windows PowerShell then ran the named
  executables with `--frames 8` from an empty working directory with PATH
  restricted to Windows system directories. Base, updated, base again for
  rollback, and single-package launches all completed eight frames without
  logged errors. The single-package fixture reached its second scene.
- Updated the offline guide, feature index and coverage inventory, editor
  README, and project and package topics. `python3 docs/guide/check_links.py`
  and `git -c core.whitespace=cr-at-eol diff --check` verified the docs while
  preserving the editor README's CRLF endings.
- Release acceptance remains open. The launcher checks ran on the development
  Windows host. A separate Windows machine without the source or developer
  tools was unavailable. The bundled runtime was verified on disk, but the
  packaged editor's export command was not driven end to end. Native collision,
  Lua module loading, and XML loading have evidence across separate fixtures.
  A single interactive build, update and rollback walkthrough covering all
  three in the same game remains unverified.

### 2026-11-25 — Windows acceptance follow-up

- Rebuilt the packaged editor and ran the full editor suite from `tools/editor`
  with `cmd.exe /c gradlew.bat -g .gradle-user-home --offline --console=plain createDistributable test`.
  The JUnit reports recorded 444 passes, three skips, and zero failures.
- Ran Windows `ctest.exe --test-dir build/debug -C Debug --output-on-failure`.
  All 82 tests passed, including all 17 windowed tests and the legacy loose and
  package launch tests.
- Ran `cmd.exe /c powershell.exe -NoProfile -ExecutionPolicy Bypass -File build/packaging-session5/launch-deliveries.ps1`.
  The relocated base, updated, rollback to base, and legacy single-package
  fixtures each completed eight frames from an empty working directory with
  PATH restricted to Windows system directories. This used the Windows
  development host.
- A packaged-editor smoke under a deeply nested report directory reached
  native update validation and failed to open a staged package at a
  264-character Windows path. The project export documentation records
  the missing-package symptom and the shorter project path workaround.
- Updated the smoke to wait for Compose to publish its menu actions, invoke
  the Swing menu items directly, and wait for publication. It uses the
  installed runtime in the packaged editor and answers the runtime chooser
  in a development run.
- Ran `cmd.exe /c powershell.exe -NoProfile -ExecutionPolicy Bypass -File build/packaging-session5/packaged-smoke.ps1`.
  The wrapper launched the packaged `SLE Editor.exe` with
  `--project-distribution-smoke` from the repository root and set
  `JAVA_TOOL_OPTIONS` to supply `-Dsle.smokeReport` under a short
  `build/p5-*` folder. Both smoke pass markers were present. A Python
  SHA-256 comparison verified that the relocated export and the applied
  menu update record the packaged editor's bundled runtime manifest.
- From `tools/editor`, ran
  `cmd.exe /c gradlew.bat -g .gradle-user-home --offline --console=plain run --args=--project-distribution-smoke`.
  Both smoke pass markers were present using the development runtime chooser.
- Still open: launching relocated deliveries on a clean Windows machine
  without the checkout or development tools, and an interactive walkthrough
  of one game combining native tile collision, Lua modules, and XML UI
  through export, update, and rollback. The development Windows host
  verified these features across separate fixtures.

### 2026-11-25 — Session 5 walkthrough handoff

- `git status --short` identified the pre-existing packaging edits.
  `cmd.exe /c hostname` confirmed access to the Windows development host.
  No separate clean Windows machine was available.
- `python3 build/p5-w/fixture.py` created a disposable tilemap room project
  with two scenes, a shared spritesheet, native tile collision, a Lua
  module, and an XML HUD with a Lua click handler. From `tools/editor`,
  `cmd.exe /c gradlew.bat -g .gradle-user-home --offline --console=plain -I ../../build/p5-w/walk.init.gradle session5Walk`
  used the editor's production export and update APIs and the packaged
  editor's bundled runtime. The harness linked one shared template from
  both scenes, exported and relocated the base, changed the Lua module
  and HUD, and exported and applied the update to a sibling delivery.
  It verified the base inventory before and after applying the update
  and moved the source project away from its original location before launch.
  The preceding follow-up supplies the packaged-editor menu evidence.
- `cmd.exe /c powershell.exe -NoProfile -ExecutionPolicy Bypass -File build/p5-w/launch.ps1`
  launched base, updated, then base again for rollback. The first
  attempt ran the raycast before the first tile collision synchronization
  and clicked outside the panel. The probe now starts on the second
  script update. The next launch confirmed the raycast and rendered HUD.
  The click coordinates were then adjusted to the rendered panel in the
  tilemap camera's scaled viewport.
- The final launch command exited successfully. Each game used an empty,
  unrelated working directory and a PATH restricted to Windows system
  directories. Each exited 0 after 150 frames in `second-room` with
  no error logs. The Lua probe checked a hit at the solid tile's top and a
  miss outside the terrain. It logged BASE, UPDATED, and BASE respectively.
  A desktop mouse click fired the XML panel's Lua handler in all three
  runs. The screenshots show the base and updated headings, clipped by
  the sample camera's scale. Logs and screenshots are under `build/p5-w`.
- `python3 build/p5-w/verify.py` verified the runtime manifest hashes,
  shared spritesheet and template, payload markers, and the three launch
  logs. The applied delivery differs from the base in `shared.slepkg`
  and the distribution and release manifests. The two scene packages
  match byte for byte.
- The combined walkthrough is complete on the Windows development host.
  Earlier suite and compatibility results above remain the evidence for
  those checks. Release acceptance remains open for launching relocated
  base and updated deliveries from an unrelated working directory on a
  Windows machine without the checkout or development tools.

### 2026-11-25 — Session 5 clean-machine transfer

- Checked for Windows Sandbox with `cmd.exe /c where WindowsSandbox.exe`
  and queried Hyper-V services and guest availability with PowerShell.
  Windows Sandbox was absent and no usable Windows guest was available.
- `python3 build/p5-w/verify.py` rechecked the original walkthrough
  deliveries and logs. Prepared `build/p5-clean/session5-clean-windows.zip`
  with both complete deliveries, a standalone PowerShell launcher, and
  instructions for the clean Windows desktop. The archive SHA-256 is
  `5cf37c08e610ce9acb5e0198c70099c70454017784eb2f5fef8a0b47c460e57f`.
- `python3 -m zipfile -t build/p5-clean/session5-clean-windows.zip` passed.
  Extracted it with `python3 -m zipfile -e` and used `diff -qr` to confirm
  both copied deliveries matched their walkthrough originals.
- `cmd.exe /c powershell.exe -NoProfile -ExecutionPolicy Bypass -File build/p5-clean/extracted/check.ps1`
  passed on the development host for base, updated, and rollback. Each
  launch used a fresh temporary location with spaces, a separate empty
  working directory, and system-only PATH. Each reached the second room
  and logged its expected probe before exiting at frame 150.
- The next step remains running the extracted ZIP's `check.ps1` on a
  clean Windows x64 desktop with graphics support, without the checkout
  or developer tools, and recording that host's output and logs.
