# Systems Module

## Responsibility

Runtime orchestration and per-frame systems.

## Owns

- `Runtime`
- `Context`
- `SceneManager`
- `SceneLoader`
- standalone `engine_app` bootstrap path via `Runtime::registerSceneFromFile()`
- transform, script, state-machine, animation, audio, physics, render, and UI orchestration systems
- `ScriptApiImpl`

## Dependencies

- Core
- Events
- Platform
- Renderer
- Resources
- Scene
- Physics
- Scripting
- UI

## Important Paths

- `EngineModules/Systems/include/sle/engine/`
- `EngineModules/Systems/src/`
- `EngineModules/Systems/CMakeLists.txt`
- `docs/topics/Runtime.md`

Chunk activation uses independent runtime subsystem gates; see [Chunks](../topics/Chunks.md).

## Rules

- Systems coordinate modules through `Context`, public APIs, and registry views.
- Keep frame order documented. Resolve world transforms after simulation writes
  and before rendering/UI; physics consumes local dirty flags before this pass.
- Keep the JSON scene/bootstrap path documented when runtime startup changes.
- File-registered scenes load into staging before replacing the live scene.
  Return parse/I/O/type errors to the caller; failures preserve active entities,
  hierarchy, scene name, and event state. Existing `void(Runtime&)` C++ builders
  retain their imperative behavior and do not have this rollback guarantee.
- Add tests for behavior that crosses module boundaries.
- Commit-time Lua teardown follows successful staging, with candidate entity IDs
  reserved before destroy callbacks and the live allocation cursor retained.
- Runtime releases UI caches/default shader references before window/context teardown.

## Update This File When

- Runtime ownership, frame order, scene loading, or system integration changes.
