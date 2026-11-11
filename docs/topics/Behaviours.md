# Script behaviours and execution order

`Script` keeps the legacy `scriptAsset`/`enabled` fields and adds a `behaviours`
array. Each attachment has a unique, nonempty `key`, a nonempty `scriptAsset`,
optional `enabled` (true) and integer `order` (0). JSON also accepts legacy `asset`
inside entries. Both JSON and Lua use the same validation.

```json
{
  "Script": {
    "behaviours": [
      {"key":"input", "scriptAsset":"assets/scripts/input.lua", "order":-20},
      {"key":"movement", "scriptAsset":"assets/scripts/movement.lua", "order":0},
      {"key":"effects", "scriptAsset":"assets/scripts/effects.lua", "order":20}
    ]
  }
}
```

Attach the same asset more than once using different keys. Each named attachment
gets its own Lua environment, closures and lifecycle callbacks. Ordinary script
globals are isolated; `_G` is available for deliberate shared application state.
Returning a table with `init(entity)`, `update(entity, dt)` and `destroy(entity)`
is recommended; named environments also support global-format lifecycle methods.
The legacy primary script retains its historical shared-global behavior.
`Engine.getBehaviourKey()` returns the current named attachment key, or nil outside
a named callback. UI/global execution does not borrow an attachment's identity.

## Frame contract

The scheduler snapshots eligible work and sorts by:

1. `order`, ascending (negative values run earlier).
2. Runtime entity ID, ascending.
3. Authored attachment position on that entity.

The legacy primary script has order 0 and precedes named entries on its entity
when tied. All due initializations finish before the update pass; updates use the
same sorted snapshot. New work added during callbacks begins next frame. Current
entity/component/attachment state and instance generation are rechecked before
calls, so removal or replacement cannot run stale work from that snapshot.

An entry's `enabled=false` pauses updates, events and named callbacks while keeping
its local state. Re-enabling resumes that instance. Whole-component disabling
retains the legacy teardown-at-lifetime-sync behavior. Chunk suspension pauses
without loading new instances or destroying existing ones. Removing a key or
changing its asset tears down only that attachment and its subscriptions.
A failed initialization releases its event references and does not disable siblings;
the scheduler does not retry unchanged failing entries every frame. Changing/removing
the entry, or disabling it for a frame before re-enabling, allows another attempt.
Update errors are logged/contained and other behaviours continue.

Entity callback names (such as state-machine enter/exit methods) fan out in the
configured attachment order. Matching boolean guards must all return true; errors
or nonboolean values block the guard. If no local callback exists, legacy global
fallback remains available. Paused entries do not participate. Event delivery uses
EventBus subscription order, not the update sort.

## Editing from Lua

`Engine.Components.set` replaces a supplied behaviour array atomically:

```lua
local script = Engine.Components.get(entity, "Script")
table.insert(script.behaviours, {
    key = "effects", scriptAsset = "assets/scripts/effects.lua", order = 20
})
assert(Engine.Components.set(entity, "Script", {behaviours = script.behaviours}))
```

Send an empty array/table to clear named entries. Other component fields remain
unchanged. Stable keys preserve local state when entries are reordered or their
order values change. Use a new key for a new attachment lifetime.

## Scenes use the same entity model

Top-level scene JSON `components` creates an optional scene-root entity:

```json
{
  "components": {
    "Script": {"behaviours": [
      {"key":"world", "scriptAsset":"assets/scripts/world.lua", "order":-100},
      {"key":"missions", "scriptAsset":"assets/scripts/missions.lua", "order":-50}
    ]}
  },
  "entities": [{"key":"player", "components": {"Transform": {"position":[20,30]}}}]
}
```

`Engine.getSceneEntity()` returns/creates this ordinary ECS entity. Its reserved
key is `@scene`; `Scene::ensureSceneEntity()` and `getSceneEntity()` are the C++
accessors. Existing roots and newly created entities become its children. Scene
scripts use the exact same scheduler and ownership as entity scripts, including
per-entry order. Scene components can include transforms; descendants inherit
them. Rigid bodies resolve through the same hierarchy; see [physics](Physics.md) for
world-space simulation and inherited fixture scaling.

Scenes without top-level components retain their previous structure until the
root is explicitly requested. Deleting the scene root deletes its descendants;
scene replacement swaps its identity with the staged scene. Runtime entity IDs
still change on replacement. The reserved key cannot be assigned to another entity.
