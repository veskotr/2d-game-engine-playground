# Runtime

## Responsibility

Runtime owns the application loop and coordinates all systems.

It now also supports data-driven startup through `Runtime::registerSceneFromFile()`, which the
root `engine_app` executable uses to boot from `engine.json`.

## Current Frame Flow

1. process pending scene switches
2. clear transient input state and tick the timer
3. poll window events; handle resize, debug toggle, and exit input
4. flush deferred scene and global events
5. update independent chunk suspension gates (see [Chunks](Chunks.md))
6. update animation
7. update audio requests
8. update scripts
9. update state machines
10. synchronize local transforms to physics, step physics, and write results back
11. resolve world transforms through the hierarchy
12. begin renderer frame and submit render commands
13. update UI
14. end renderer frame and swap buffers

## Transform synchronization contract

Animation, scripts, and external setters write local transforms and mark them dirty.
Physics consumes these edits before stepping and writes its results back to local
transforms. Only then does TransformSystem resolve world transforms and clear their
dirty flags. Rendering, culling, debug geometry, and world-space UI consume the
final world transforms from the same frame, including affected descendants.

World transforms are a derived presentation snapshot; during simulation they can
still contain the previous frame's pose. Current scripting transform APIs use local
transforms. New simulation consumers of world transforms must account for this
phase boundary rather than inserting an early pass that consumes physics edits.

The windowed `integration_runtime_transform_physics_path` regression exercises the
actual runtime loop, Lua movement, animation on an existing body, and external
position/rotation edits. It skips with code 125 if a GLFW window cannot be created.

## File-based scene replacement

`registerSceneFromFile()` records the path; file contents are read at load time.
Immediate loads and deferred switches share the same staged-load path. Missing
files, malformed JSON, invalid typed component data, and Tiled loader errors return
an error identifying the requested scene/path. The current scene stays intact and
no lifecycle events are emitted for the rejected replacement.

On success, staged ECS/hierarchy data replaces the old scene with fresh entity IDs.
Global unload/load events remain deferred. Existing imperative C++ scene builders
keep their prior behavior; arbitrary side effects inside those builders cannot be
rolled back. Staging can populate the shared resource cache; the cache itself is
not transactional. `SceneLoader::load()` remains a low-level append loader; callers
needing preservation on failure should use the Runtime file-registration path.

`integration_events_scene_load_failure_path` covers failed direct/deferred loads,
partial construction, old scene event/state preservation, and successful replacement
without old entity-ID reuse. This test needs no graphics initialization.

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
