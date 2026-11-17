# Contributing to SLE

## Setup

The checked-in presets and CI target Windows with Visual Studio 2022, its C++
workload, and CMake 3.20+. Install Python 3 and `jinja2` for GLAD generation:

```powershell
python -m pip install jinja2
cmake --preset debug
cmake --build build/debug --config Debug --parallel
```

Dependencies formerly tracking `master` are pinned to their previously cached commit
revisions for reproducible configuration. The root enables both C and C++ so GLAD
uses the same MSVC runtime as engine targets.

Initial configuration downloads dependencies declared in
[Dependencies.cmake](cmake/Dependencies.cmake). Python also enables the Lua API
stub generation target. Run executables from the repository root so asset paths resolve.
See [README.md](README.md) for app and example commands.

The supplied presets use the Visual Studio generator. They cannot run directly
in a Linux/WSL shell. Use Windows tooling for the supported workflow; a Linux
port needs a separate build directory, generator, and system graphics dependencies.
Do not reuse a Windows CMake cache from Linux.

## VS Code

`.vscode/tasks.json` (**Terminal → Run Task…**) and `.vscode/launch.json`
(**Run and Debug**, F5) cover every build and launch step. They need CMake and
Visual Studio 2022, plus JDK 21 for the editor (found the same way as
`tools/entity-editor/run-editor.ps1`, through `find-jdk.ps1`).

| Task | What it does |
|---|---|
| Engine: Build Debug (Ctrl+Shift+B) | Configure and build `build/debug`, including the Debug editor bridge |
| Engine: Build Release | Everything in `build/release` |
| Engine: Run All Tests / Smoke Tests / Tests Matching… | CTest on `build/debug` |
| Engine: Run engine_app / Project… / Built Game Config… | Debug engine with `engine.json`, a `.sleproject`, or a built game's `config.json` |
| Game: Build Runtime Bundle (Release) | Prebuilt engine exe + DLLs + engine assets in `build/release/runtime/Release` |
| Game: Run Built Game… | Runs a Build Game output (`My Game.exe`) |
| Editor: Run / Run Tests | `gradle run` with the Debug bridge / `gradle build` |
| Editor: Package Windows App | Release bridge + runtime bundle, then `createDistributable` |
| Editor: Package Installers (MSI/EXE) | Same, then `packageDistributionForCurrentOS` |
| Editor: Run Packaged App | Starts the packaged `SLE Editor.exe` |

To produce the Windows editor executable yourself, run **Editor: Package Windows App**,
or the same steps from a terminal:

```powershell
cmake --preset release -DSLE_BUILD_EDITOR=ON
cmake --build build/release --config Release --target sle_editor_native sle_runtime_bundle --parallel
tools/entity-editor/gradle.ps1 createDistributable
```

The result is `tools/entity-editor/build/compose/binaries/main/app/SLE Editor/SLE Editor.exe`.
Copy the whole `SLE Editor` folder to distribute it. The debug configurations
(**Debug engine_app …**) use the Visual Studio debugger from the C/C++ extension.

The first Release build after engine changes is the slow step: it recompiles the
engine with full optimization and relinks the editor bridge and `engine_app`
(minutes). Later runs only rebuild what changed. The editor scripts use their own
Gradle home (`tools/entity-editor/.gradle-user-home`), so the first Gradle run there
starts a fresh daemon.

## Versions

The engine and the editor have separate `0.MINOR.PATCH` versions while pre-1.0. Bump
MINOR for each added feature and PATCH for fixes.

- **Engine:** `project(GameEngine VERSION …)` in the root [CMakeLists.txt](CMakeLists.txt).
  It generates `sle/core/Version.hpp` (`sle::core::EngineVersion`), is printed by
  `engine_app --version` and at startup, and is recorded in the runtime bundle
  (`sle-runtime.json`) and in Build Game output (`sle-distribution.json`).
- **Editor:** `version` in [build.gradle.kts](tools/entity-editor/build.gradle.kts).
  It sets the packaged app version and the window title (`SLE Editor 0.6.0 · engine 0.16.0`).

## Validation

Build any changed targets before running CTest; CTest does not rebuild them.
For a specific integration test:

```powershell
cmake --build build/debug --config Debug --target integration_scene_loader_roundtrip_path
ctest --test-dir build/debug -C Debug -R '^integration_scene_loader_roundtrip_path$' --output-on-failure
```

CI uses `windows-2022` to match the Visual Studio 2022 presets and runs smoke
and integration tests excluding the `windowed` label:

```powershell
ctest --test-dir build/debug -C Debug -L smoke --output-on-failure
ctest --test-dir build/debug -C Debug -L integration -LE windowed --output-on-failure
```

A separate CI job uses JDK 21 and runs the editor JUnit suite with
`.\gradlew.bat test --no-daemon --console=plain` from `tools/entity-editor/`. It uploads HTML and XML test reports
even on failure. These unit tests do not require the native editor bridge;
interactive and JNI smoke checks still require a graphics environment.

With a working graphics environment, run the complete suite:

```powershell
ctest --test-dir build/debug -C Debug --output-on-failure
```

Renderer/window/input changes also need interactive validation where appropriate.
State skipped checks and their reason. Documentation-only changes need link and
content checks rather than an engine rebuild.

## Changes and reviews

Keep each change focused and preserve unrelated worktree edits. Follow
[AGENTS.md](AGENTS.md) and the relevant module's conventions. Include regression
coverage for changed behavior, relevant documentation updates, and actual
validation results in the pull request. Do not edit generated Lua API output
by hand; see [scripting guidance](EngineModules/Scripting/AGENTS.md).

Release tests must execute setup and verification independently of `assert` or
`NDEBUG`. The audio Lua API regression uses explicit failure exit codes in every
configuration.

For distribution changes, build the Windows Release bundle before the editor's
export regression:

```powershell
cmake --build build/debug --config Release --target sle_runtime_bundle --parallel 4
cd tools/entity-editor
./gradlew.bat build run --args=--project-distribution-smoke
```

The smoke launches the copied Release executable after relocation, without an
explicit config/project and from an unrelated cwd. Native launcher discovery and
explicit-config precedence also have `integration_distribution_launch_path`
CTest coverage; build `engine_app` before running it.
