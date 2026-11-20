# Scripting Module

## Responsibility

Lua VM ownership, binding registration, script loading, and script callback execution.

## Owns

- `ScriptEngine`
- `EditorLuaHost`
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

- One gameplay Lua VM per engine instance.
- Lua access should use registered bindings and runtime bridge APIs.
- Scripts must not own raw engine internals.

Entity execution context supplies event-subscription ownership, including nested
callbacks. Script removal releases entity-script subscriptions; UI execution owns
its callbacks independently. Scene commits clear scene-global callbacks too. Failed
initialization rolls back new registrations. See [Events](../topics/Events.md).

## Update This File When

- Lua API, script lifecycle, binding registration, or script resource behavior changes.

ScriptEngine owns entity+attachment-key instances with independent named Lua
environments and generation-scoped event references. Legacy primary scripts retain
their shared-global compatibility. See [behaviours](../topics/Behaviours.md) for
update order and [prefabs](../topics/Prefabs.md) for Lua instantiation.


The host can prepend Lua module roots through `ScriptEngine::prependModulePaths`.
Systems uses this for project Scripts/asset-root `require` patterns before gameplay
starts. Existing Lua search paths remain afterward; scene switches keep the same
VM/module cache. The project context resolves dynamic engine asset arguments;
raw Lua IO keeps ordinary Lua semantics. See [projects](../topics/Projects.md).

`EditorLuaHost` evaluates behavior metadata, stopped Console chunks, and
project command and UI declarations or command callbacks in fresh, bounded Lua states.
The editor bridge supplies detached snapshots and receives diagnostics or edit
lists. See [project editor commands](../topics/SceneEditor.md#project-editor-commands).
