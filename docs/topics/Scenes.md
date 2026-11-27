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

## Tilemap assets and placement

New native maps are registered `*.tilemap.json` assets. Their format is
`sle-tilemap`, version 1, with `name`, `grid`, `tilesets`, `streaming`, and
`layers`. `grid` contains integer pixel `cellWidth`/`cellHeight` and numeric
`pixelsPerUnit`. Each tileset entry has a map-local integer `key` and an
`asset` reference (`project-asset://<uuid>`). Layers contain UUID `id`, `name`,
nonnegative unique `drawOrder`, `visible`, `locked`, `collision`, and sparse
`cells`. A cell has signed integer `x`/`y`, integer tileset `set`, and tile UUID
`tile`; several tilesets may contribute to the same layer.

Place a map using an ordinary entity with `Transform` and
`Tilemap: {"asset": "project-asset://<uuid>", "sortingOffset": 0}`. Only the
map moves. Every layer starts at map-local `(0, 0)`; layers have no transforms.
The hierarchy-composed entity position translates map geometry. Rotation and
scale are ignored with a runtime warning, and locked in the editor. The signed
sorting offset is added to each layer order and clamped to the renderer's
unsigned range. Collision bodies follow translation without replacing unchanged
fixtures. Repeated placements have independent collision identities.

`streaming` stores `mode`, `chunkCells`, `loadRadius`, and `unloadRadius`.
Phase 3 uses `always`: all native-map collision remains active outside the camera
view. `aroundFocus` is retained but currently warns and loads the whole map;
focus streaming and caching are phase 4. The configured positive `chunkCells`
is used for runtime partitioning, including negative grid coordinates.

Create → Tilemap offers New Tilemap, Place Existing Tilemap, and explicit
conversion of legacy inline layers. Selecting a placement exposes its shared
layers in the existing palette. Painting uses map-local coordinates; scene Undo
and Redo include map edits, and scene Save writes dirty referenced maps first.
Draft previews use immutable temporary map snapshots. Saves reject changed source
bytes or asset identities. Export follows scene → map → tileset → atlas references.
Standalone scene/prefab packages cannot bundle map dependencies; use project export.

Legacy conversion moves a common origin to the placing entity. Integral offsets
can be rebased into cell coordinates, preserving the single map origin. Different
grids/scales, nonintegral offsets, and overflowing coordinates are rejected without
changing the legacy scene. Runtime loading of inline layers and Tiled maps remains
available. Maps are bounded to 16 MiB, 256 tilesets, 4096 layers, and 262144 cells.

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

A registered `*.tileset.json` asset uses `format` set to `"sle-tileset"` and
`version` 1 or 2. Both versions require positive integer `tileWidth` and
`tileHeight` in pixels and a `spritesheet` project asset reference of the form
`project-asset://<uuid>`. This UUID identifies the image and its spritesheet
sidecar. Each entry in `tiles` declares a stable UUID `id`, a display `name`,
and a `region` UUID from the sidecar. Use the same `project-asset://<uuid>` form
for the layer's `tileset` reference.

Version 2 requires a `grid` object. Its `margin` and `spacing` are two-element
arrays of nonnegative integers in X, Y order, and `columns` is a positive integer.
Each tile can include `tags` (unique nonblank strings), `properties` (an object
with string, finite number or boolean values), and `animation` (reserved, with
only `null` accepted). Tile names, tags and property names must be nonblank
and are limited to 256 UTF-8 bytes. Each tile can have up to 256 tags and 256
properties. Property strings are limited to 4096 UTF-8 bytes. Both versions
allow up to 4096 tiles and 4 MiB per file.
The editor and runtime reject unsupported fields and invalid value types.

Double-click a tileset in Assets or use **Open Tileset Editor…** in its details.
Click or drag in the atlas to select tiles, then edit their metadata or collision.
Enter tags as a comma-separated list or a JSON string array. The editor displays
applied tags as an array so commas within a tag remain part of the tag.
Changes are staged in the tool. Use **Undo** or **Redo** to step through edits,
**Save** to write them, or **Revert** to reload the tileset from disk. The editor
retains drafts when you hide or move the tool, or switch assets or projects. It
prompts you about unsaved tilesets when you exit.

**Reslice** matches tiles by grid row and column and preserves their UUIDs and
metadata. New slices append needed regions to the spritesheet sidecar on save.
Use **Add tile…** for an existing spritesheet region. Removing tiles can break
scene cells that reference their UUIDs. Reslicing or applying metadata upgrades
a version 1 tileset to version 2 when you save.

An optional tile `collision` object stores `mode` as `none`, `fullCell` or
`custom` and a `pieces` array. The array is empty for None and Full Cell.
Custom pieces use arrays of 3 to 8 vertex pairs in lower-left tile-local
pixels. Tiles without a collision object default to None.

Choose **From Alpha** to generate custom pieces. The threshold defaults to 128,
and zero simplification follows pixel boundaries. Positive tolerance can
simplify a single outline to a convex slope when all shortcuts fit within the
tolerance. Masks with holes or separate islands retain their exact coverage.
Select a tile and drag its collision vertex handles to adjust the shape. When
staging collision edits or saving a tileset, the editor checks the geometry at
each pixels-per-unit scale used by a project scene layer that references it.

During Play, collision-enabled layers attach static Box2D fixtures around the
game camera, with one chunk of padding on each side. Full Cell uses the layer's
grid dimensions and merges adjacent cells within each chunk. Custom pieces use
the same origin and pixel scale as the rendered tile. Layer visibility affects
drawing independently of collision. The physics debug overlay outlines the
active native fixtures. Terrain contacts affect simulation and carry no entity
IDs for Lua collision or zone events.

Make a tileset with **New Tileset…** (texture details or the Assets toolbar)
and **Create Tileset…** in the spritesheet slicer. The editor writes a v2
`.tileset.json` next to the texture and opens the Tileset Editor. Add a layer
with **Create → Tile Layer** (menu bar, Scene view or Hierarchy), which also
opens the **Tile Palette** beside Details. The palette, also under **Windows**,
adds layers from a registered tileset and searches its named thumbnails. Use it
to rename, reorder, show, hide, lock, or change collision on a layer. Press
**T** with the Scene viewport focused, or click the Tiles tool icon, to
activate tile painting and open the palette.

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
