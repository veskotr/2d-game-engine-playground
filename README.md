# SLE (Simple Little Engine)

SLE is a modular C++ 2D game engine with ECS scene data, Lua scripting, command-based rendering, Box2D physics, XML-driven UI, and CTest coverage.

## Highlights

- Layered engine modules with a strict dependency direction.
- ECS scene model with hierarchy and transform propagation.
- Lua scripting through controlled engine bindings.
- Command-driven renderer with batching and streaming uploads.
- Physics, events, audio, animation, and UI integrated through systems.

## Repository Layout

- `EngineModules/` - engine modules (`Core`, `Events`, `Platform`, `Renderer`, `Resources`, `Scene`, `Physics`, `Scripting`, `UI`, `Systems`)
- `examples/` - runnable sample applications
- `assets/` - shared runtime assets
- `tests/` - smoke, integration, and harness tests
- `docs/` - constraints, module docs, topic docs, and historical references
- `cmake/` - dependency setup and CMake helpers
- `tools/entity-editor/` - [Kotlin Compose Desktop editor with an embedded SLE viewport](tools/entity-editor/README.md)

## Build

Prerequisites:

- CMake 3.20+
- Visual Studio 2022
- C++20-capable compiler

Debug build:

```powershell
cmake --preset debug
cmake --build build/debug --config Debug
```

Release build:

```powershell
cmake --preset release
cmake --build build/release --config Release
```

## Run Examples

From the repository root:

```powershell
build/debug/examples/sandbox/Debug/example_sandbox.exe
build/debug/examples/phase1_eventbus/Debug/example_phase1_eventbus.exe
build/debug/examples/phase2_lua_events/Debug/example_phase2_lua_events.exe
```

## Run Standalone App

The repo includes a thin `engine_app` entry point that boots from `engine.json` and registers JSON or packaged `.slescene` scenes through `Runtime::registerSceneFromFile()`.
Use `--config path/to/engine.json` to choose another configuration. The
[scene editor and starter scene](docs/topics/SceneEditor.md) demonstrate portable
scene authoring and engine loading without changing the default startup config.

From the repository root:

```powershell
build/debug/engine_app/Debug/engine_app.exe
```

The default [`engine.json`](engine.json) starts `assets/scenes/npc_zone_demo.json`.

## Run Tests

```powershell
ctest --test-dir build/debug -C Debug -L smoke --output-on-failure
ctest --test-dir build/debug -C Debug -L integration --output-on-failure
ctest --test-dir build/debug -C Debug --output-on-failure
```

## Documentation

Start with [the documentation index](docs/README_DOCUMENTATION.md).
For development setup and validation, read [CONTRIBUTING.md](CONTRIBUTING.md).

AI assistants start at [AGENTS.md](AGENTS.md). Scoped instructions cover
[scripting](EngineModules/Scripting/AGENTS.md) and [tests](tests/AGENTS.md).
Use [PLANS.md](PLANS.md) for substantial work. Historical task notes and roadmap
plans provide background; they do not define a new session's task.

## Development Rules

- Keep module dependencies one-way.
- Put behavior in systems and data in components.
- Keep renderer access command-based.
- Route Lua-facing engine access through the binding/API boundary.
- Update docs in the same task as behavior changes.
