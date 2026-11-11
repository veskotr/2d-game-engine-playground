# Scripting

## Model

Scripting uses one Lua VM per engine instance. C++ exposes a global `Engine` table through registered bindings.

## Main Pieces

- `ScriptEngine` owns the Lua state and script callback lifecycle.
- `ScriptApi` / runtime bridge APIs expose safe engine operations.
- `ScriptApiImpl` connects Lua-facing requests to runtime services.
- `ScriptSystem` updates script components during the frame.

## Rules

- Do not let Lua hold raw ownership of engine internals.
- Add new Lua operations through the binding layer and runtime API boundary.
- Keep script errors contained and logged.

Chunk suspension pauses entity updates while preserving Lua instances. Subscriptions infer entity/UI ownership from the current execution context; see
[Events](Events.md) and [Chunks](Chunks.md).

## Update This File When

- Lua API changes.
- Script lifecycle changes.
- Binding ownership or runtime bridge behavior changes.

## Deep Reference

- `docs/SCRIPTING_CURRENT.md`
- `docs/LUA_IMPLEMENTATION_QUICKSTART.md`

Scene-defined keys can be resolved with `Engine.findEntityByKey(key)` (ID or nil).
Resolve them again after scene replacement; see [scene authoring](Scenes.md).

Multiple keyed attachments and scene scripts share one scheduler; see
[behaviour ordering](Behaviours.md). Lua can instantiate [prefabs](Prefabs.md).
