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

## Validation

Build any changed targets before running CTest; CTest does not rebuild them.
For a specific integration test:

```powershell
cmake --build build/debug --config Debug --target integration_scene_loader_roundtrip_path
ctest --test-dir build/debug -C Debug -R '^integration_scene_loader_roundtrip_path$' --output-on-failure
```

CI runs smoke and integration tests excluding the `windowed` label:

```powershell
ctest --test-dir build/debug -C Debug -L smoke --output-on-failure
ctest --test-dir build/debug -C Debug -L integration -LE windowed --output-on-failure
```

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
