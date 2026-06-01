# Scripting Module

## Responsibility

Lua VM ownership, binding registration, script loading, and script callback execution.

## Owns

- `ScriptEngine`
- Lua binding files
- script resource/runtime types
- Lua API registration

## Dependencies

- Core
- Scene-facing value/API types
- Resources
- Lua dependency configured by CMake

## Important Paths

- `EngineModules/Scripting/include/sle/scripting/`
- `EngineModules/Scripting/src/`
- `EngineModules/Scripting/tools/`
- `docs/topics/Scripting.md`

## Rules

- One Lua VM per engine instance.
- Lua access should use registered bindings and runtime bridge APIs.
- Scripts must not own raw engine internals.

## Update This File When

- Lua API, script lifecycle, binding registration, or script resource behavior changes.
