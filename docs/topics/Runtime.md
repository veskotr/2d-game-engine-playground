# Runtime

## Responsibility

Runtime owns the application loop and coordinates all systems.

It now also supports data-driven startup through `Runtime::registerSceneFromFile()`, which the
root `engine_app` executable uses to boot from `engine.json`.

## Current Frame Flow

1. process pending scene switches
2. update input
3. tick timer
4. handle window resize and exit input
5. update animation
6. update audio requests
7. update transforms
8. update scripts
9. update state machines
10. update physics
11. begin renderer frame
12. submit render commands
13. update UI
14. end renderer frame
15. swap buffers

## Main Files

- `EngineModules/Systems/src/Runtime.cpp`
- `EngineModules/Systems/include/sle/engine/Runtime.hpp`
- `EngineModules/Systems/src/SceneLoader.cpp`
- `engine_app/main.cpp`
- `EngineModules/Systems/include/sle/engine/Context.hpp`

## Update This File When

- Frame order changes.
- Runtime ownership changes.
- A system is added to or removed from the loop.
- The `engine_app` bootstrap path or `engine.json` contract changes.
