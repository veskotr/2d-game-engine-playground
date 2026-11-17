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
`{"weapon":{"Audio":null}}`; a null field removes that field, for example
`{"root":{"Animator":{"targetEntities":{"aim":null}}}}`. Overrides cannot change
identity or hierarchy.
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

The [desktop editor](../../tools/editor/README.md) preserves self-contained
subtree prefab documents, their parent links and internal Animator references.
The temporary Prefab tab's Inspector edits the root. Single-entity documents
remain compatible.
Nested prefab declarations inside an editable prefab are still rejected explicitly.
JSON exports and `.sleprefab` packages retain the entire subtree and its assets.
Native scene codecs validate subtree component data and references before saving.

Importing a project prefab into a project scene creates a linked instance.
Project scenes persist the standard `prefabs` declarations, with
`file: "project-asset://<UUID>"`, a stable instance key and local-key component
overrides. Expanded entity keys are the instance key for the root and
`<instance-key>/<local-key>` for descendants. Moving/renaming an asset together
with its `.slemeta` retains links. Missing or ambiguous assets fail opening;
legacy expanded scenes remain independent. Standalone imports are independent,
and portable scene package exports contain expanded snapshots.

Saving a project prefab reconciles its instances in the current scene after
validating the complete result. Closed scenes resolve the latest saved prefab
when next opened or launched; their files are never silently rewritten. Shared
component changes and removals propagate. Explicit field overrides (including
component and field removals, persisted as `null`) survive; arrays replace whole
values. When switching documents autosaves both a dirty scene and a dirty prefab,
the scene is saved first and the prefab save still refreshes it. The root Transform
stays local, including position, rotation and scale. Internal reference remapping
keeps repeated instances isolated. Invalid updates retain the asset and scene.
The refreshed scene becomes dirty; replacement clears the scene's undo history
(the notice says "Undo history cleared after prefab update"), retains selection only
for surviving keys, and keeps the field drafts of entities whose content did not change.

Instance overrides cannot change entity identity, names or internal hierarchy.
Currently root Inspector rename and descendant reparent commands can be attempted,
but saving rejects those changes; undo/revert them or edit the prefab source.
Removing an overridden descendant from a source prefab likewise fails the staged
update rather than dropping the override. Root deletion/duplication operates on
the complete subtree and retains link metadata through undo/redo.

### Creating, editing and applying prefabs

- **Create:** drag a scene entity from the Hierarchy, or out of the Scene viewport,
  onto an Assets folder. Folders outline while a drag is active, and the one under
  the pointer is highlighted; Escape cancels. A viewport drop cancels the move in
  progress, so the entity keeps its pose. The subtree is saved as
  `<entity name>.prefab.json` (numbered when the name exists), then linked: the
  root keeps its key as the instance key, descendants become `<root>/<key>`, and
  parent and keyed Animator references elsewhere in the scene follow. Linking is
  one undoable change; undo restores the original keys but leaves the saved asset.
  The drag carries the scene identity and entity key, resolved only at drop time;
  if the scene changed the drop is refused, and if it changes while saving the
  prefab is kept unlinked. Subtrees containing linked instances, Animator targets
  outside the subtree, and numeric targets into it are refused.
- **Edit:** double-click a prefab in Assets to open it in a temporary **Prefab**
  tab. There is no permanent Entity tab; ordinary scene entities are edited in the
  Inspector. Closing the tab asks before discarding unsaved changes (including
  closing a detached window that holds it) and returns to the scene. Saved layouts
  that still show the old tab hide it until a prefab is opened.
- **Apply Changes to Prefab** (scene context menu or **Edit** menu) on any entity
  of a linked instance writes that instance's component edits into the prefab
  through the same validated update as a prefab Save. The instance's placement
  stays local and its link is rebased, so the applied values are not kept as
  overrides. Other instances in the open scene refresh, keeping their explicit
  overrides; the scene becomes dirty. Apply is refused while the open Prefab tab
  has unsaved edits to the same asset; a clean open tab reloads.

New desktop-authored prefabs receive UUID entity keys automatically; asset UUIDs
live separately in `.slemeta`. Renaming preserves identity, and opening older
named-key files preserves those keys. The full document JSON stays out of the UI.
