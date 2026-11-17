# Packaged game builds (`.slepkg` + `config.json`)

## Goal and acceptance

**File → Build Game…** produces a delivery folder:

```text
My Build/
  engine_app.exe, *.dll, assets/, notices/, sle-runtime.json   # prepared runtime bundle
  config.json            # engine config: window, targetFPS, packages
  game.slepkg            # project assets, scenes, prefabs, UUID sidecars, main scene
  sle-distribution.json  # delivery inventory
```

- `config.json` is ordinary engine configuration (the `engine.json` `window`
  object plus `targetFPS`) with a `packages` array. It does not list scenes.
- A `.slepkg` carries scene data and the main scene. Several packages may be
  listed; they share one UUID/scene namespace. The first listed package that
  declares `mainScene` provides the startup scene.
- `engine_app` with no options uses `config.json` beside the executable;
  `--config config.json` works from any cwd. Legacy `engine.json`
  (`startScene`/`scenes`) and `--project` launches are unchanged.
- The existing folder **Export Game…** stays available.

## Format (version 1)

ZIP with STORED entries (same constraints as prefab packages):
`manifest.json` = `{format: "sle-package", version: 1, id, name, mainScene}`,
plus `assets/<asset-root-relative path>` for every file beneath the project
asset root, including `.slemeta` sidecars. Entry paths are validated (no
`..`, drive, backslash, control or reserved characters, case-insensitive
duplicates). Limits: 65000 entries, 256 MiB per entry, 1 GiB total.

Runtime unpacks each package into an immutable content-keyed cache
(`<temp>/sle-package-cache/<key>`), verified exactly before reuse; a damaged
shared cache falls back to a private per-launch extraction. Unpacked roots are
indexed by the same code as folder projects (`ProjectRuntime`). Derived prefab
files go to a per-launch temp folder.

## Steps

- [x] Shared STORED-ZIP reader (`Systems/src/StoredZip.*`), used by prefab and game packages.
- [x] `GameConfig` loader (moved from `engine_app/main.cpp`) with `packages`/`targetFPS`.
- [x] `ProjectRuntime::openPackages`, multi-root indexing/resolution, Lua paths for all roots.
- [x] `engine_app`: package configs and executable-relative `config.json` discovery.
- [x] JNI `prepareGame` for staged-build validation.
- [x] Kotlin `GameBuild` (package writer + build) and File → Build Game….
- [x] Native integration test, Kotlin unit test, docs.

## Validation

See the ledger below for commands actually run.

## Ledger

Decisions: Export Game (folder) is kept; Build Game is the packaged alternative.
Packages mirror the project asset tree instead of SHA-named entries, so UUID
sidecars, Lua `require` and relative paths keep working unchanged. A config with
`packages` rejects `startScene`/`scenes`. No compiler is involved in a build:
games are data and the runtime bundle is prebuilt.

Validation actually run (2026-11-16, Windows):
- `cmake --build build/debug --config Debug --parallel 8`, then
  `ctest --test-dir build/debug -C Debug`: **76/76 passed**, including new
  `integration_game_package_path`, `integration_package_discovery_path` and
  windowed `integration_package_launch_path`; prefab/project tests unchanged.
- `gradlew.bat build`: passed, 195 tests, 0 failures, 2 skipped (existing symlink
  fixtures), including new `GameBuildTest` (3) and `ProjectDistributionTest` (7).
- `cmake --build build/debug --config Release --target sle_runtime_bundle`: passed.
- One-off end-to-end (temporary test, removed afterwards): real `GameBuild` against
  that Release bundle, JNI `prepareGame` validation, then the built Release
  `engine_app.exe --frames 8` from an unrelated cwd with no options. It loaded
  `game.slepkg`, required a Lua module and switched scenes (`Completed 8 frames;
  scene: second`).

Not run: a desktop smoke that clicks **File → Build Game…** and its choosers
(the controls share Export Game's chooser/worker code, which its smoke covers).

Follow-up round (same day, requested): the built executable is renamed after the
project (`GameBuild.executableName`: Windows-forbidden characters become `_`, device
names get a `_` suffix, collisions with runtime files are refused). Runtime-bundle
metadata (`sle-runtime.json`, `RUNNING.txt`) is not delivered. The packaged Windows
editor ships `build/release/runtime/Release` as `resources/runtime`. Build/Export Game
use it without a chooser; dev runs keep the chooser. Packaged editor icons:
`packaging/sle.ico` (16–256 px, generated from `sle-256.png`) for Windows, the PNG
for Linux.
- `cmake --build build/release --config Release --target sle_runtime_bundle sle_editor_native`: passed.
- `gradlew.bat createDistributable`: passed. The output has `resources/runtime/`, and
  the icon extracted from `SLE Editor.exe` is the mascot.
- Temporary end-to-end with that packaged runtime: built `Forest Quest.exe` launched
  from an unrelated cwd, `Completed 8 frames; scene: second`. Test removed afterwards.
- `gradlew.bat build`: 196 tests, 0 failures, 2 skipped (`GameBuildTest` now 4).
- Not run: Linux packaging (icon setting unverified there), packaged-editor UI click.

Remaining work / follow-ups: a game-specific executable icon (the built game exe
still has the default icon); a Linux runtime bundle
(`BuildRuntimeBundle.cmake` is Windows-only); optional DEFLATE compression;
editor-driven splitting of a project into several packages.

Tooling round (2026-11-16, requested): `.vscode/tasks.json` / `launch.json` cover
engine build/test/run, runtime bundle, built-game launch, editor run/test/package and
packaged-app launch. `tools/entity-editor/find-jdk.ps1` (shared JDK 21 lookup) and
`gradle.ps1` (any Gradle task). Versions: engine `0.16.0` from root `project(VERSION)`
(generated `sle/core/Version.hpp`, `engine_app --version`, startup log, runtime and
distribution manifests); editor `0.6.0` from `build.gradle.kts` (package version,
window title with the engine version from JNI `engineVersion`).
- Debug build + `ctest`: 76/76 passed (discovery test now checks `--version`).
- `gradle.ps1 build`: passed in 35 s.
- Release chain timed: configure 16 s, editor bridge 100 s (engine recompile),
  runtime bundle 5 s, `gradle.ps1 createDistributable` 5 s. jpackage accepted 0.6.0;
  the packaged runtime prints `SLE engine 0.16.0`.
- Not run: the VS Code tasks from inside VS Code (their commands were run directly),
  the packaged editor window (title text unverified on screen).
