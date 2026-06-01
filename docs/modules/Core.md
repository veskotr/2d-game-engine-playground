# Core Module

## Responsibility

Foundational utilities shared by the rest of the engine.

## Owns

- Logging helpers
- `Result` / error utility types
- Timer support
- Engine configuration types

## Dependencies

- Standard library

## Important Paths

- `EngineModules/Core/include/sle/core/`
- `EngineModules/Core/src/`
- `EngineModules/Core/CMakeLists.txt`

## Rules

- Core must not depend on higher engine modules.
- Keep Core small and general-purpose.
- Do not place gameplay, rendering, ECS, Lua, or platform-specific ownership here.

## Update This File When

- Core utility APIs change.
- Shared error/logging/config behavior changes.
- A lower-level invariant is added or removed.
