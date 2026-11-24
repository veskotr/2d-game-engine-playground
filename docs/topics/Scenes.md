# Data-driven scenes

The [desktop Scene Editor](SceneEditor.md) now saves flat authored scenes as
self-contained `.slescene` packages. `SceneLoader` and runtime scene registration
accept those packages as well as JSON. Packaged asset IDs are resolved before
ordinary scene loading. The first editor version explicitly rejects hierarchy,
Tiled and scene-root metadata; those remain supported by existing engine JSON
loading paths.

`engine.json` registers scene files and chooses `startScene`. A scene file can
contain `entities`, a `tiledMap` path relative to the scene file, top-level
`components`, and/or `prefabs`. Each
entity has a `components` object; the existing component field names are unchanged.

## Native project tile layers

The [tilemap room walkthrough](../../examples/tilemap_room/README.md) covers
PNG import, slicing, collision authoring, painting and export.

A project scene can include a top-level `tileLayers` array alongside `entities`.
A layer has a lowercase UUID `id`, `name`, one `tileset` UUID reference, a
unique nonnegative `drawOrder` and a `cells` array. Each cell has signed integer
`x` and `y` coordinates and a tile UUID in `tile`. Empty cells are omitted.
The editor's native scene validator sorts layers by draw order and cells by Y
then X. The loader groups occupied cells into 32×32 chunks using floor division.

`origin` defaults to `[0, 0]` in world units. `pixelsPerUnit` defaults to 1 and
converts both the grid spacing and the sprite rectangle size. Each rectangle is
anchored at the cell's lower-left corner. Draw order uses the existing sprite
sorting layers. `visible` defaults to true. `locked` defaults to false and
`collisionEnabled` defaults to true. The editor uses the lock flag to prevent painting and erasing.

A registered `*.tileset.json` asset uses `format: "sle-tileset"` and `version: 1`.
It declares positive integer `tileWidth` and `tileHeight` in pixels and a
`spritesheet` project asset reference of the form `project-asset://<uuid>`. This
UUID identifies the image and its spritesheet sidecar. Each entry in `tiles`
declares a stable UUID `id`, a display `name`, and a `region` UUID from the
sidecar. Use the same `project-asset://<uuid>` form for the layer's `tileset`
reference.

In the Asset Browser, select a tileset and open **Tile Collision…** in its
details. Choose a tile to preview, and toggle the tiles you want in the batch.
**None**, **Full Cell**, **Generate from Alpha**, and **Preview vertices**
stage changes in the dialog. **Apply** saves all staged tiles as one edit.
Use **Undo** and **Redo** in the dialog to restore the saved values.

Alpha generation includes pixels at the threshold, which defaults to 128.
Zero simplification follows pixel boundaries. Positive tolerance can simplify
a single outline to a convex slope when all shortcuts fit within the tolerance.
Masks with holes or separate islands retain their exact coverage. Set
**Pixels per world unit** to match the target layer to preview dimensions
and validate fixture size at that scale. To edit vertices, enter one
counterclockwise polygon per line as space-separated `x,y` pairs.

An optional tile `collision` object stores `mode` as `none`, `fullCell` or
`custom` and a `pieces` array. The array is empty for None and Full Cell.
Custom pieces use arrays of 3 to 8 vertex pairs in lower-left tile-local
pixels. Tiles without a collision object default to None.

During Play, collision-enabled layers attach static Box2D fixtures around the
game camera, with one chunk of padding on each side. Full Cell uses the layer's
grid dimensions and merges adjacent cells within each chunk. Custom pieces use
the same origin and pixel scale as the rendered tile. Layer visibility affects
drawing independently of collision. The physics debug overlay outlines the
active native fixtures. Terrain contacts affect simulation and carry no entity
IDs for Lua collision or zone events.

Open **Tile Palette** from the Scene viewport or the **Windows** menu. Add a
layer from a registered tileset, then search its named thumbnails. Use the
palette to rename, reorder, show, hide, lock, or change collision on a layer.
Press **T** with the Scene viewport focused to activate tile painting.

The viewport keys **1–6** select Brush, Erase, Rectangle, Eyedropper, Fill, and
Select. Brush and Erase follow fast strokes across grid cells. Rectangle fills
the dragged area, and Eyedropper picks an existing tile. For a stamp, enable
**Build Stamp** and choose tiles in left-to-right order. **Use Copied Stamp**
keeps the layout of a selection, including gaps.

Select a rectangle to bound a four-way fill of empty space. A fill of existing
tiles follows connected cells with the same tile ID. Drag inside a selection
to move the painted cells. **Ctrl+C** copies the selection, and **Ctrl+V**
pastes at the last pointed cell or the selection's lower-left corner. Gaps in
a stamp or pasted selection leave the destination intact.

The grid, selection or fill bounds, and translucent stamp show where edits
will land. Release the pointer to commit one undoable edit. Escape, focus loss,
changing tools, and switching documents cancel pending edits. Save the map as a
project scene to keep the layers and stable tile IDs.

Missing dependencies and cells
appear as magenta placeholders with diagnostics in the log. Standalone project
loading and game package validation report missing tilesets, images, tiles and
regions as errors. Use Export Game to package these project assets together.

See the [two-layer test project](../../tests/data/tilemap/Tiles.sleproject) for
a working example with a foreground tile at a negative coordinate.

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
The editor saves authored scene documents; arbitrary runtime snapshot saving and
transactional imperative Lua/C++ builders are not implemented.
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


The [desktop entity editor](../../tools/editor/README.md) can now author
single-entity prefab JSON for a scene's `prefabs` list. This is authored document
saving in the tool, distinct from serializing a running Runtime scene snapshot.
