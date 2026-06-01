# Physics Module

## Responsibility

Box2D integration and physics event production.

## Owns

- `PhysicsWorld`
- contact listener integration
- body and fixture runtime lifecycle
- collision and zone event emission support

## Dependencies

- Core
- Events
- Scene-facing component data through systems integration
- Box2D dependency configured by CMake

## Important Paths

- `EngineModules/Physics/include/sle/physics/`
- `EngineModules/Physics/src/`
- `EngineModules/Physics/CMakeLists.txt`
- `docs/topics/Physics.md`

## Rules

- Defer contact events out of Box2D callbacks.
- Keep physics stepping deterministic where practical.
- Sync physics results back through transform/component paths owned by systems.

## Update This File When

- Physics component behavior, collision/zone event behavior, stepping, or debug rendering changes.
