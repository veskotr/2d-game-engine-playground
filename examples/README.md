# Examples Module

Each folder in this module is a standalone sample app with its own `CMakeLists.txt`, source files, and local scripts where needed.

## Resource layout convention

Each example owns its resources under its own `assets/` directory.

- Use `examples/<example_name>/assets/...` for all example-specific files.
- If an example uses scripts, place them in `examples/<example_name>/assets/scripts/...`.
- If an example uses UI, textures, fonts, or shaders, place them under that same example `assets/` tree.

## Included examples

- `sandbox/`: full gameplay sandbox sample (migrated from the old root `Sandbox/` folder)
- `phase1_eventbus/`: Phase 1 event system validation app
- `phase2_lua_events/`: Phase 2 Lua event API + RAII subscription validation app

## Build

From project root:

```powershell
cmake -S . -B build/debug
cmake --build build/debug --config Debug
```

## Run

```powershell
build/debug/examples/phase1_eventbus/Debug/example_phase1_eventbus.exe
build/debug/examples/phase2_lua_events/Debug/example_phase2_lua_events.exe
build/debug/examples/sandbox/Debug/example_sandbox.exe
```
