# Prefabs

A prefab is a JSON entity subtree, loaded through the same component codecs as a
scene. Each entity needs a unique, nonempty key. `root` names its single root;
every other entity must reach that root through its `parent` reference.

Self-contained [`.sleprefab` packages](PrefabPackages.md) bundle this JSON with
textures and other assets. Use a package anywhere a prefab file is accepted;
package assets resolve internally without relying on their original paths.

```json
{
  "root": "root",
  "entities": [
    {"key":"root", "components":{"Transform":{"position":[0,0]}}},
    {"key":"weapon", "parent":"root", "components":{"Transform":{"position":[12,0]}}}
  ]
}
```

A scene includes instances with a `prefabs` array:

```json
{
  "entities": [{"key":"world"}],
  "prefabs": [{
    "file":"../prefabs/actor.prefab.json",
    "key":"enemyA",
    "parent":"world",
    "overrides":{"root":{"Transform":{"position":[100,50]}}}
  }]
}
```

Prefab file paths are relative to the including scene. Component asset paths keep
the shared scene loader's asset-path rules. The root becomes `enemyA`; the weapon
becomes `enemyA/weapon`. Internal parent and Animator target references are
rewritten per instance, including legacy 1-based prefab entity positions.
Expanded entities follow authored scene entities so existing numeric scene
references keep their positions. Use keyed references for new content.

Overrides map local entity keys to component patches. Objects merge recursively;
arrays replace whole values. A null component removes it, for example
`{"weapon":{"Audio":null}}`. Overrides cannot change identity or hierarchy.
Unknown entity keys and malformed patches fail with source context.

`PrefabLoader::instantiate(path, scene, key, overrides, parent)` validates a staged
instance, appends it, and returns its root entity. The key must be nonempty and
all resulting keys must be unused; the optional parent must be live. Failure
removes only entities added by that call. Runtime scene replacement remains
transactional through SceneManager; low-level SceneLoader append calls are not.

Prefab assets can include their own `prefabs` array with the same declarations.
Nested files resolve relative to the including prefab file. A nested `weapon`
instance becomes `enemyA/weapon`; its `child` becomes `enemyA/weapon/child`.
Without an explicit parent, nested instances attach to the enclosing prefab root.
An explicit parent names an enclosing local entity key, including a forward
reference to another nested instance such as `weapon/socket`.

Internal references remain isolated for repeated instances of the same asset.
Numeric references address only their owning file's original `entities` array;
appending nested entities does not extend that numeric range. Prefer keyed
references for cross-instance links within an enclosing prefab.

Nested overrides apply first. Outer overrides may address a descendant such as
`{"weapon/child":{"Transform":{"position":{"x":100}}}}`; those patches
merge after nested overrides, preserving other object fields. Arrays still replace
whole values. All expanded entities must form one subtree under the declared root.
Duplicate keys, unknown references and hierarchy cycles fail validation. `@scene`
is reserved and cannot be a prefab entity or instance key.

Import cycles are detected through canonical absolute file paths on the active
import stack, including `..` and symlink aliases. Sibling imports of the same file
are allowed. Expansion is limited to 32 active prefab files and 16,384 total
expanded entities per dynamic instance or scene prefab expansion operation.
Exceeding either limit returns a contextual error before live entities are added.

Prefabs support entity subtrees, not Tiled map imports or editor inheritance.
Script behaviours and all other supported component data use the same schema as
ordinary scene entities.

Lua can instantiate a prefab directly:

```lua
local root, error = Engine.instantiatePrefab("assets/prefabs/actor.prefab.json", "enemyA", {
    root = {Transform = {position = {100, 50}}}
}, Engine.getSceneEntity())
assert(root, error)
```

The fourth argument is an optional parent ID; without it the instance follows the
scene's normal root/scene-entity parenting. The call returns the root ID or
`nil, error`; newly attached behaviours initialize on the next scheduler snapshot.
A prefab can carry multiple [script behaviours](Behaviours.md), and an override of
`Script.behaviours` replaces that attachment array.


## Desktop entity authoring

The [entity editor](../../tools/entity-editor/README.md) creates and edits the
single-entity subset of this format, adds components from engine defaults and
saves `.prefab.json` files after native validation. These exports load through
`PrefabLoader::instantiate`, Lua `Engine.instantiatePrefab`, and scene prefab
inclusions. The editor currently rejects multi-entity/nested prefab documents
rather than dropping content; those formats remain supported by the engine.


New desktop-authored prefabs receive UUID keys automatically; the user edits the
name. Renaming preserves identity, and opening older named-key files preserves
those keys. The editor visualizes the single entity through the engine renderer
without running its scripts/physics. The full document JSON is kept out of the UI.
