# SLE (Simple Little Engine)

SLE is a modular 2D game engine in C++ with strict module layering, ECS-based scene data, Lua scripting, command-based rendering, and a systems-driven runtime loop.

## Highlights

- Strict one-way architecture: `Core -> Platform -> Renderer -> Resources -> Scene -> Scripting -> Systems -> Runtime -> Sandbox`
- ECS scene model with hierarchy and transform propagation
- Lua scripting through a controlled `ScriptApi` boundary
- Command-driven renderer with batching and streaming uploads
- Physics and events integrated as dedicated modules
- UI module with XML layouts and runtime document integration

## Repository Layout

- `EngineModules/`: engine modules (`Core`, `Platform`, `Renderer`, `Resources`, `Scene`, `Scripting`, `Systems`, `Physics`, `Events`, `UI`)
- `examples/`: runnable sample applications
- `assets/`: shared runtime assets
- `docs/`: architecture, implementation, and planning documentation
- `cmake/`: dependency setup and CMake helpers

## Prerequisites

- CMake 3.20+
- Visual Studio 2022 (generator in `CMakePresets.json` is `Visual Studio 17 2022`)
- A C++20-capable compiler (project is configured with `CMAKE_CXX_STANDARD 20`)

## Build

### Option A: CMake Presets (recommended)

```powershell
cmake --preset debug
cmake --build build/debug --config Debug
```

Release:

```powershell
cmake --preset release
cmake --build build/release --config Release
```

### Option B: Explicit configure/build

```powershell
cmake -S . -B build/debug
cmake --build build/debug --config Debug
```

## Run Examples

From repository root:

```powershell
build/debug/examples/sandbox/Debug/example_sandbox.exe
build/debug/examples/phase1_eventbus/Debug/example_phase1_eventbus.exe
build/debug/examples/phase2_lua_events/Debug/example_phase2_lua_events.exe
```

## Run Tests

After configuring and building with presets, run CTest from repository root.

Smoke tests:

```powershell
ctest --test-dir build/debug -C Debug -L smoke --output-on-failure
```

Current smoke suite includes harness and runtime sentinel checks.

Integration tests:

```powershell
ctest --test-dir build/debug -C Debug -L integration --output-on-failure
```

All tests:

```powershell
ctest --test-dir build/debug -C Debug --output-on-failure
```

## Documentation

Start here:

1. `docs/README_DOCUMENTATION.md`
2. `docs/AGENT_ARCHITECTURE_CONTEXT.md`
3. `docs/ARCHITECTURE_VERIFIED.md`
4. `docs/IMPLEMENTATION_OVERVIEW.md`
5. `docs/ENGINE_MASTER_PLAN.md`

## Development Workflow

- Keep module boundaries strict; lower layers must not depend on higher layers.
- Put behavior in systems, data in components.
- Route Lua access through `ScriptApi` only.
- If runtime or architecture behavior changes, update docs in `docs/` as part of the same task.

## Current Status

The project is under active development with architecture, docs, and planning aligned around the `docs/` folder. See `docs/ENGINE_MASTER_PLAN.md` for the active roadmap.
