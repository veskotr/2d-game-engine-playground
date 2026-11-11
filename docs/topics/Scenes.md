# Data-driven scenes

`engine.json` registers scene files and chooses `startScene`. A scene file can
contain `entities`, a `tiledMap` path relative to the scene file, top-level
`components`, and/or `prefabs`. Each
entity has a `components` object; the existing component field names are unchanged.

## Identity and hierarchy

Use an optional nonempty, case-sensitive `key` to reference an entity. Keys are
unique within the loaded scene and survive as lookup metadata until that entity
is destroyed or the scene is replaced. `name` is only a diagnostic/display label;
it need not be unique and is not a reference key. Runtime entity IDs are temporary.

```json
{
  "entities": [
    {
      "key": "weapon",
      "parent": "player",
      "components": {
        "Transform": {"position": [12, 0], "rotation": 0, "scale": [1, 1]},
        "Animator": {"targetEntities": {"owner": "player"}}
      }
    },
    {
      "key": "player",
      "components": {
        "Transform": {"position": [100, 50], "rotation": 0, "scale": [1, 1]}
      }
    }
  ]
}
```

The loader creates all JSON entities first, then loads components and resolves
parent/Animator links. Forward references work. Missing or null `parent` means a
root (or a child of the optional scene root). Child transforms are local to the parent; transforms pass through ancestors
without a Transform component. Reparenting preserves local values.

Duplicate/invalid keys, unknown targets, malformed references, self-parenting and
cycles fail loading with the file, entity index and field in the error. References
resolve only within this file's `entities` array, not old scene entities or Tiled
object IDs. Tiled imports continue to use their existing loader.

For compatibility, numeric references mean positive **1-based positions in the
JSON `entities` array**, including any skipped slots. They are translated to this
load's runtime IDs. This fixes legacy animator targets that only worked when IDs
happened to start at 1. They never mean raw runtime IDs; files relying on external
or Tiled runtime IDs must migrate to keyed JSON entities. Prefer keys because
positions change when entities are reordered. Files without keys continue to load.

## Runtime lookup

```lua
local player = Engine.findEntityByKey("player") -- current ID, or nil
if player then
    Engine.setTransformPosition(player, 120, 50)
end
```

Use `Scene::findEntityByKey()` in C++. `Scene::setEntityKey()` gives a live entity
a unique key or renames it; it returns false for empty/duplicate keys or dead
entities without changing the previous lookup. Scene APIs own key lifetime,
including subtree destruction. Lookup does not make an old runtime ID valid:
resolve keys again after loading a scene.

## Replacement and limits

`Runtime::registerSceneFromFile()` / `Engine.registerSceneFromFile()` use staged
loading. Failed validation preserves the current scene, hierarchy, keys and event
state. Successful replacement commits new IDs and key metadata together.
`SceneLoader::load()` is a lower-level append operation and is not transactional;
load into staging or use Runtime when replacing a running scene.

Authored transforms stay local; physics resolves ancestor transforms into world
body poses and inverse-converts simulated results back to local data. Scene roots
and prefab hierarchies can contain rigid bodies. See [physics](Physics.md) for
inherited fixture scaling and geometry limits.
Scene saving and transactional imperative Lua/C++ builders are not implemented.
See [prefabs](Prefabs.md) for reusable entity subtrees and [behaviours](Behaviours.md)
for scene-root components and multiple scripts.

## Component validation shared with Lua

The JSON loader and `Engine.Components.set` now use the same per-component codecs
for writable fields, defaults, enums, numeric bounds and cache invalidation. The
full canonical field list is in [Lua components](LuaComponents.md). JSON now also
supports fields previously ignored by its old readers, including Animator
parameters/loop controls, StateMachine parameters/triggers, SpriteRenderer UVs and
ChunkActivation policy.

Canonical vector objects such as `{"x": 1, "y": 2}` work in JSON. Existing exact
2/4-element arrays remain supported. Missing fields retain component defaults;
malformed vectors/types, invalid ranges/enums, unknown fields on a known component,
and writes to derived/native fields fail the load. Invalid known components must
be objects. Unknown component **types** still warn and skip for compatibility;
Lua rejects unknown types. Fix typos in known component fields rather than relying
on the old silent fallback behavior.

Legacy JSON asset aliases remain accepted:

| Component | Legacy JSON | Canonical JSON/Lua |
| --- | --- | --- |
| Script | asset | scriptAsset |
| Audio | asset | assetPath |
| UI | layout, font, behavior | layoutAsset, fontAsset, behaviorAsset |
| Animator | clip | clipAsset |
| StateMachine | definition | definitionAsset |

Providing both spellings for the same field fails, even when the values match.
Both frontends normalize asset paths consistently. Empty paths remain empty; UI
paths retain whitespace trimming. Animator targets are adapted from file keys or
positions to live runtime IDs before the common codec validates them. Lua target
maps still take live runtime IDs.

Two compatibility exceptions remain: JSON `SpriteRenderer.texture` selects a
resource (Lua uses `Engine.setSpriteTexture`); a missing texture still warns.
Legacy positive finite `RigidBody.mass` is retained as JSON metadata so existing
files keep loading, but physics derives mass from fixture density. Lua rejects
this inert field. Tiled loading remains a separate importer.
