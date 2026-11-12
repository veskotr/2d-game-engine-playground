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
still contain the previous frame's pose. Local transform APIs remain local;
`Engine.getWorldTransform` and physics use the shared live hierarchy composition
helper instead of this cache. Physics snapshots native body poses and writes
locals back parent-first, so independently simulated parent/child bodies are not
transformed twice. New simulation consumers must account for this
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

## Externally hosted edit preview

The optional desktop editor uses `initEmbedded()` and `renderEmbeddedFrame()`.
The host owns the current OpenGL context, target, framebuffer size and buffer
presentation. Preview frames resolve world transforms and submit render commands;
they do not advance gameplay or run UI behaviours. This is distinct from the
standalone loop above, which remains responsible for simulation and GLFW events.
Destroy the Runtime before releasing the host context. See
[editor setup](../../tools/entity-editor/README.md) for build and smoke commands.


## Embedded gameplay and playback control

The editor sandbox uses `tickEmbeddedFrame(width, height, dt)`, which calls the
same `advanceAndRender()` system sequence as the standalone loop, including Lua,
physics, animation, state machines, audio and XML UI. Host dt must be finite and
nonnegative; the editor bounds its wall-clock dt to 0.1 seconds after stalls.
The host clears transient Input state, transfers focused canvas events, makes its
context current and presents the result. Pending scene switches follow the same
staging path as standalone execution.

`setEmbeddedPaused(true)` suspends audio and makes hosted ticks draw the current
scene/UI without running gameplay, events or UI click/behavior callbacks. Stop is
implemented by destroying the old Runtime in context, loading a new one and
pausing it before its first gameplay tick. Stopped UI documents prepare layout
and bindings without executing behaviors; deferred behaviors load when running
resumes. The original render-only API remains available for that presentation.


## Live entity preview

`registerSceneFromData()` snapshots in-memory JSON and uses the same staged load
and commit path as file scenes. `loadEntityPreview()` validates a single-entity
prefab before loading that data, suppresses gameplay lifecycle events for preview
loads, and pauses simulation. Rejected updates preserve the previous scene.
`renderEntityPreview()` resolves transforms, fits the logical camera when needed,
and submits engine sprites/debug geometry plus EntityPreviewSystem's grid,
origin and sprite bounds. Zoom changes the preview camera, not authored values.
`loadEntityPreview(document, true)` preserves the existing preview camera for
interactive edits; initial loads still fit. `getCameraViewportSize()` exposes
logical bounds alongside position for editor picking. Handles and hit testing
belong to the editor; scene rendering remains in the engine. Same-entity editor
updates preserve the view, while explicit Fit requests and viewport aspect changes
can reframe it.

The editor holds one native canvas across preview/sandbox tab changes. Normal
sandbox loads restore its configured camera/clear color. `initEmbedded(false)`
skips device initialization; `enableEmbeddedAudio()` initializes audio once after
the host has presented a sandbox frame. Standalone `init()` and the default
`initEmbedded()` keep their existing audio initialization behavior.
