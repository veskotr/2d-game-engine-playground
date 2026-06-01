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

## Rules

- Systems coordinate modules through `Context`, public APIs, and registry views.
- Keep frame order documented.
- Keep the JSON scene/bootstrap path documented when runtime startup changes.
- Add tests for behavior that crosses module boundaries.

## Update This File When

- Runtime ownership, frame order, scene loading, or system integration changes.
