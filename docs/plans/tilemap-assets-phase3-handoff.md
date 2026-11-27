# Phase 3 checkpoint and resume handoff

Date: 2026-11-27. The user requested saving progress, committing, and pushing the
current changes before phase 3 is complete. Resume only when requested.

## Binding scope

Implement [the phase 3 plan](tilemap-assets-phase3.md) and T1 of
[the larger design](polish-tiles-cameras-launcher.md). User clarification:
**Only the tilemap is movable. Every layer starts at map-local (0, 0).**
Do not introduce independently movable layer entities. Streaming/new palette
redesign remains phase 4.

## Implemented so far

- `TilemapComponent` exposes asset and sortingOffset; catalog metadata and shared
  component codecs support it. Native map parser and Kotlin TilemapDocument validate
  `sle-tilemap` v1: map grid, keyed tilesets, qualified cells, layers and streaming settings.
- Runtime scene-owned placement resources preserve placement identities. Rendering,
  preview bounds and terrain collision apply current hierarchy-composed translation.
  Static bodies move through SetTransform; new native maps keep off-camera collision.
  Rotation/scale are ignored with a warning; editor Transform fields are locked.
- Native Lua map component replacement stages resources before committing, rejecting
  failed replacement without changing the existing reference. Scene/entity teardown
  releases resolved placement data.
- Editor map drafts are shared by asset path across placements. Existing tile tools use
  a projection adapter with qualified IDs (`set:tileUUID`), map grid and translated origin.
  Mixed tilesets, shared history, source/identity conflict checks, explicit Reload,
  immutable temporary preview snapshots, and map-first scene saves are implemented.
- Create -> Tilemap supports new assets, existing placements, and explicit legacy
  conversion. Conversion rebases integral offsets onto one shared grid and rejects
  incompatible grids/scales or nonintegral offsets. Map placement/conversion is grouped
  in history. Project export inventory follows map -> tileset v1/v2 -> atlas references.
- Existing fresh-project smoke was changed to exercise Create -> Tilemap, local painting,
  save/reopen, movement and repeated placement. It currently fails; see below.
- Guide, scene/schema/module docs, coverage inventory and editor README were updated.
  Engine version is 0.35.0; editor version is 0.36.0.

## Confirmed remaining correctness issue — fix first

`SceneWorkspaceState.kt` currently computes dirty maps and save candidates using
`referencedTilemaps()` (roughly lines 103 and 117–121 before subsequent edits).
Reproduction: paint a shared map in scene A; delete its last placement from A,
while scene B still uses that map; save A. The modified draft is no longer in
referencedTilemaps(), so map save skips it and the scene can report clean.
Restarting loses the shared-map edits, including for B.

Retain pending map drafts independently of current placement reachability, with
explicit project/discard ownership. Add regression coverage for last-placement
removal, scene switches, save, reopen, and undo. Do not merely broaden saves to every
historical project: drafts must not write across project switches.

This was reported by the independent reviewer. The review then stopped because the
reviewer hit its usage limit; **the independent review is incomplete**. No fix has
been attempted for this finding at the user's checkpoint request.

## Desktop smoke failure — investigate next

Windows `--fresh-tilemap-smoke` compiles and launches, but fails with:

```
java.lang.IllegalStateException: Scene viewport did not reload
```

Log: `/tmp/sle-phase3-windows-smoke.log` (line 65 at checkpoint).
Build/reports: `build/phase3-editor-windows/`.
Read the surrounding smoke await and native/editor diagnostics to locate the failed
reload; do not claim the creation/painting/placement desktop workflow is validated.
Legacy tile-painting and docking regression smokes still need a current run.

## Validation actually run

- Full editor unit suite: **544 tests, 0 failures/errors/skips**. Executed with installed
  Linux Gradle 9.5.1, `/tmp/sle-cross-gradle` dependency cache, separate project cache,
  and `/tmp/sle-phase3-editor-build` output. Latest full log:
  `/tmp/sle-phase3-editor-full.log`; JUnit XML under that build's test-results/test.
- Linux full native build: `cmake --build /tmp/sle-runtime-session1 -j 4` passed.
- `ctest --test-dir /tmp/sle-runtime-session1 -L integration -LE windowed --output-on-failure`:
  **64/64 passed**. Log `/tmp/sle-phase3-native-full-tests.log`.
- Windows native bridge plus integration_tilemap_scene_path,
  integration_tilemap_collision_path and integration_tilemap_render_path built.
  `ctest --test-dir build/debug -C Debug -R '^integration_tilemap_(scene|collision|render)_path$' --output-on-failure`:
  **3/3 passed**, including actual pixel rendering.
  Log `/tmp/sle-phase3-windows-native.log`.
- Linux render test skipped because EGL could not create a context; the Windows run
  supplies the actual rendering evidence.
- `python3 docs/guide/check_links.py`: **573 references, 0 broken**.
- The Windows fresh-map desktop smoke **FAILED**, as above. End-to-end conversion,
  shared two-scene export/relocation and multi-project draft isolation require more work.

Re-run relevant checks after fixes. These are checkpoint results, not future validation.

## Local execution details

Windows Java: `C:\java\jdk-21.0.2`. Windows native CMake: installed under
`C:\Program Files\CMake\bin`. Existing native build: `build/debug`.
Ignored helper scripts are in `.superpowers/sdd/tilemap-assets-phase3/`, including
validate-native.ps1, validate-editor.ps1 and progress.md. Their essential state is
recorded here because ignored files are not committed.

Linux Gradle invocation from tools/editor:

```
bash /home/veselin/.gradle/wrapper/dists/gradle-9.5.1-bin/iq79hdu3mqx29lgffhp8bfmx/gradle-9.5.1/bin/gradle -g /tmp/sle-cross-gradle test --offline --no-daemon --console=plain --project-cache-dir /tmp/sle-phase3-project-cache -PsleBuildDir=/tmp/sle-phase3-editor-build
```

Gradle requires local socket access outside the sandbox. The Windows helper sets
JAVA_HOME and GRADLE_USER_HOME to the existing Windows cache and invokes gradlew.bat
with `-PsleBuildDir=C:\projects\engine\build\phase3-editor-windows`.

## Decisions and review cautions

- Work stayed in the existing feature checkout to preserve uncommitted phase 2
  dependencies. The checkpoint also contains pre-existing workflow/editor/physics
  changes; do not attribute all git diff changes to phase 3.
- Native runtime layers privately partition by tileset to reuse existing geometry
  validation. Authored layers remain single layers at zero local origin. Equal-order
  renderer behavior has a native pixel test.
- Unsaved map previews use content-addressed temporary files instead of a JNI protocol
  change. They are scheduled for deletion at JVM exit; long editing sessions can
  accumulate preview files.
- Undo guards prevent a scene's old map snapshot from overwriting newer map changes
  made in another scene. Further cross-scene/project ownership testing is needed.
- Save conflict recovery uses the palette's explicit Reload action; it discards the
  current map draft after confirmation and records the document transition in history.
- Check remaining plan acceptance criteria and review save/reload/history and asset
  reference traversal before declaring phase 3 complete.
