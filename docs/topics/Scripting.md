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


## Keyboard input actions

Configure named bindings in [Project Settings](Projects.md#keyboard-input-actions).
Use `Engine.Input.isActionDown(name)`, `isActionPressed(name)`, and
`isActionReleased(name)` from Lua. Names are case-sensitive. Unknown actions
and actions with no keys return `false` for every query.

```lua
if Engine.Input.isActionPressed("Jump") then
    jump()
end
```

An action is down while any of its keys is down. Pressed and released are
transitions between consecutive simulation samples and last for one simulation
frame. A second bound key held alongside the first produces no extra press.
A tap wholly between samples produces no action edge. Raw key queries remain
available through `Engine.Input`.

## Entity tag queries

`Engine.findEntitiesByTag("Enemy")` returns a dense Lua array of live entity
IDs in ascending numeric order. It resolves the exact display name in the
active project. An unknown name, a removed name, or a run without an active
project returns an empty array. Entity keys remain available through
`Engine.findEntityByKey`.

Each call scans the current scene. Destroyed entities and entities in replaced
scenes are absent from subsequent calls. Query again after a scene switch
before using previously returned IDs. Renaming a tag preserves its membership
and requires updating Lua callers to use the new name.

## Exposed behavior parameters

A named Script attachment can declare Inspector fields on its returned table.
Declare each field with a `type` and matching `default`. The editor supports
finite numbers, booleans and UTF-8 strings. An optional `label` supplies the
display name and an optional integer `order` controls field order.

```lua
return {
    properties = {
        speed = { type = "number", default = 3, label = "Speed" },
        patrol = { type = "boolean", default = true },
    },
    init = function(entity, parameters)
        local speed = parameters.speed
        -- Use speed to configure this behavior instance.
    end,
}
```

The editor evaluates the top-level declaration in a separate bounded Lua
environment. Keep engine calls, module loading, and gameplay side effects
inside callbacks. When a behavior declares fields, its init callback receives
a fresh table of effective values, including after reload. Authored overrides
apply by name when their type matches the declaration. Removed fields and
incompatible overrides stay in the document so the author can recover them.

See the [Scene editor](SceneEditor.md#behavior-parameters) for editing controls.
