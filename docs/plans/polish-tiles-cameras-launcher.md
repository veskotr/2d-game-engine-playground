# Polish pass: tiles, activity, cameras, launcher, editor UI

Status: **Phases 0, 1 and 2 implemented** (Phase 2 validated 2026-11-27, uncommitted). Phases 3–10 are planned.
This plan sets the structure so each track can land gradually, one phase at a
time. The user's notes from real use go in the feedback log at the end.

## Why

- The user could not create a tilemap because they could not find how to start one.
  The cause is real: nothing in the editor creates a tileset (you have to
  hand-edit JSON). The only way to add a tile layer is a "Tile Palette" button
  near the end of the Scene toolbar. That button opens a hidden-by-default tab
  in the bottom dock.
- Each tile layer can use only one tileset. Tile layers are stuck inside one
  scene. Chunk streaming only works on the old Tiled `.tmj` path, through a
  read-only `ChunkMapComponent`.
- Entity activation is chunk-based with static home chunks. Its config is never
  parsed, so in practice nothing sleeps. There is no sleep, wake or low-power
  update.
- Cameras have inconsistent semantics: zoom means opposite things in Lua and in
  the component, and Lua moves a camera that a component camera then
  overwrites.
- With no project open, the editor shows an empty in-memory scene. There is no
  picker and no file associations.
- The editor's visual tokens are centralized. Dialogs are split between themed
  Swing (`JOptionPane`/`JFileChooser`, about 55 call sites) and Compose windows.
  Forms, toolbars and menus are not laid out by shared rules.

## Current state (evidence)

| Area | Today | Key code |
|---|---|---|
| Tileset asset | `*.tileset.json` v1: spritesheet + tile size + `tiles[{id,name,region,collision}]`; strict keys; margin/spacing not stored | `TileLayerLoader.cpp:160`, `TilePaintingControls.kt:66` |
| Tileset authoring | none; only `TilesetCollisionDialog` (modal, vertex text field); `SpritesheetDialog` slices grid regions but makes no tileset | `project/assets/TilesetCollisionDialog.kt`, `SpritesheetDialog.kt:148` |
| Tile layers | scene-level `tileLayers[]`, one `tileset` each, sparse cells, 32×32 render chunks; not in Hierarchy; fixed origin, `pixelsPerUnit` 1 by default | `TileLayer.hpp:44-58`, `SceneLoader.cpp:59-67`, `SceneWorkspaceState.kt:175-191` |
| Layer creation | Tile Palette panel → "Add Tile Layer"; T key activates tool but doesn't open panel; panel is temporary, opens in bottom strip | `Main.kt:915-932`, `ToolCommands.kt:18-20`, `PanelRegistry.kt:32`, `DockLayout.kt:65` |
| Create menus | Entity, Sprite, colliders, event areas, Camera; nothing tile-related | `EditorContextMenu.kt:258-274`, `EditorMenuStrip.kt:136` |
| Palette | inline column, 36dp horizontal thumbnail row, 1-row stamps | `TilePaintingControls.kt:148-256` |
| Tile render/collision | quad per cell per frame; collision bodies only within view + 1 chunk margin | `RenderSystem.cpp:118-143`, `TileCollisionSystem.cpp:72-104` |
| Activation | grid only from Tiled `ChunkMapComponent`; focus = last frame's camera lower-left; static home chunks; `ChunkActivationConfig` never parsed | `ChunkSceneRuntimeState.cpp:14-38`, `Runtime.cpp:800`, `EngineConfig.hpp:29` |
| Entity lifecycle | Lua `init`/`update`/`destroy` + named callbacks; suspended entities just skip `update` | `ScriptEngineBehaviour.cpp:280-299`, `ScriptSystem.cpp:29,82` |
| Zones | Lua `zone.enter/exit` events; no activation role | `ScriptApiImpl.cpp:732-742` |
| Cameras | `Camera2DComponent{enabled,primary,viewSize,zoom}`; lowest runtime id wins; no follow/bounds/pixel-perfect; Lua API drives a separate built-in camera | `CameraSystem.cpp:11-34`, `Runtime.cpp:184-207` |
| Settings | `.sleproject` `settings` object with strict whitelist, parsed in 3 places + Kotlin | `ProjectSettings.hpp:110-117`, `ProjectRuntime.cpp:141`, `GamePackage.cpp:144`, `Runtime.cpp:338`, `ProjectDocument.kt:19-115` |
| Startup | `--project`/`--scene` flags only; Recent menu; no picker; no file association; no single instance | `Main.kt:93-187`, `build.gradle.kts:148-172` |
| UI | Compose Desktop + Material 1; `EditorTokens/Metrics/Typography` exist; Swing menus/dialogs | `ui/theme/*`, `ui/controls/EditorControls.kt` |

## Decisions

| # | Question | Decision |
|---|---|---|
| D1 | Where tilemaps live | Each tilemap is a **separate `*.tilemap.json` asset**, so several scenes can reference the same map. |
| D2 | Tilemap position | A tilemap is **movable**. It is placed in a scene by an entity with a `Tilemap` component and a Transform. |
| D3 | Chunk loading config | It is a **setting on the tilemap asset**, not a separate component. |
| D4 | Activation model | Entities get an **activity level**. The developer defines the levels in project settings, each with a distance from the focus (the camera by default). |
| D5 | Zones and activity | Event zones can **override the level** of the entities inside them. A dungeon's enemies can keep moving that way. |
| D6 | Low-power behaviour | Lua `sleep`, `wake` and `idle` callbacks, where `idle` is a low-frequency, MCU-style tick. |
| D7 | Camera zoom | Standardize on the common convention: `zoom > 1` zooms **in** everywhere, and positions are the camera center. |
| D8 | No-project state | A separate **project picker** window, not the editor. |
| D9 | What "modern" means | Not clunky. Standard placements, grids and button variants; tighter menus; form fields with aligned labels. |

Defaults I chose; change them if they're wrong:

- **Tilemap transforms:** a tilemap honours translation only. Rotation and
  scale are locked in the editor and ignored at runtime, with a warning,
  because tile grids stay axis-aligned.
- **Shared-map edits:** editing a tilemap from any scene edits the shared
  asset. All scenes that reference it see the change; there are no per-scene
  overrides for now.
- **Zone override timing:** a zone's override applies **while the focus is
  inside the zone**, so the dungeon is alive while you're in it. A zone can
  instead say `always`.
- **Backward compatibility:** existing projects default every entity to an
  `Always` level, so their behaviour doesn't change. New projects get the
  standard level set.

## Design

### T1. Tilemap asset and placement

```jsonc
// Assets/Maps/World.tilemap.json
{
  "format": "sle-tilemap", "version": 1, "name": "World",
  "grid": { "cellWidth": 16, "cellHeight": 16, "pixelsPerUnit": 16 },
  "tilesets": [ { "key": 0, "asset": "project-asset://…" }, { "key": 1, "asset": "project-asset://…" } ],
  "streaming": { "mode": "always" | "aroundFocus", "chunkCells": 32, "loadRadius": 2, "unloadRadius": 3 },
  "layers": [{ "id": "…", "name": "Ground", "drawOrder": 0, "visible": true, "locked": false,
               "collision": true, "cells": [ { "x": 0, "y": 0, "set": 0, "tile": "…tile uuid…" } ] }]
}
```

```jsonc
// in a scene: an ordinary entity
{ "name": "World map", "components": {
    "Transform": { "position": [0, 0] },
    "Tilemap":   { "asset": "project-asset://…", "sortingOffset": 0 } } }
```

- **Multiple tilesets per layer:** each cell names a map-local tileset `key`
  plus a stable tile UUID. This avoids Tiled-style `firstgid` arithmetic, which
  breaks when a tileset grows.
- **Streaming lives in the asset** (D3). One `chunkCells` value drives the
  render, collision and streaming chunks. `ChunkMapComponent` is no longer
  needed for native maps.
- **Several placements of one map**, in the same scene or in different scenes,
  are allowed. Each placement streams on its own.
- **The `Tilemap` component holds only the asset reference** and draw-order
  offset. It shows up in the Hierarchy and Inspector like any entity, which
  fixes the discoverability problem for free.
- **Movement:**
  - Moving the entity in the editor moves the map.
  - At runtime, moving it repositions the chunk collision bodies. Box2D static
    bodies can be moved with `SetTransform`.
  - Moving tile platforms with proper kinematic contact is a later follow-up.
- **Undo and save:** painting marks the tilemap asset dirty, and saving the
  scene also saves the dirty tilemaps. One undo history covers both scene and
  map edits.
- **Compatibility:** the runtime keeps loading scene v1 inline `tileLayers[]`.
  The editor offers to convert them, writing one `.tilemap.json` and adding a
  placing entity. The Tiled `.tmj` path is unchanged.

### T2. Tileset v2 and Tileset Editor (Tiled-style tool window)

- **New Tileset…** appears in the Asset Browser and on a selected texture. The
  flow: pick an image, set the tile size, margin and spacing, preview the grid,
  then Create. This writes the sidecar regions and `.tileset.json` v2 in one
  step.
- The Tileset Editor is a **dockable, detachable panel**, not a modal dialog.
  Its parts:
  - Center: the atlas grid with zoom, and single or rectangle selection.
  - Side: properties for the selected tile or tiles: name, tags, custom
    properties (key → string/number/bool) and an animation slot, which is
    reserved in the format now and editable later.
  - Collision: draggable polygon handles and Full Cell / From Alpha / None.
    This absorbs `TilesetCollisionDialog`.
  - Actions: Reslice, which keeps tile UUIDs matched by grid position, plus add
    or remove tiles.
- Format v2 stores the `grid {margin, spacing, columns}` and the per-tile
  `properties` and `tags`. v1 still loads.

### T3. Tile Palette and map painting

- **Create → Tilemap** goes in the menu bar, the viewport context menu and the
  Hierarchy context menu. It asks for a new or existing `.tilemap.json`,
  creates the placing entity, selects it and opens the palette.
- The **Tile Palette** is a tool window, docked right by default. Its parts:
  - A tileset selector listing the map's tilesets, with "+ Add tileset to map"
  - A grid atlas picker; dragging a rectangle makes a multi-tile stamp
  - The active map's layer list, with add, rename, reorder, hide, lock and
    collision controls
- Selecting a tilemap entity, pressing **T** or choosing Tools → Tiles opens
  and focuses the palette. Empty states always offer the next action.
- Painting works on the selected placement in its own local space, so moved
  maps paint correctly.
- Rendering and collision cache per-chunk geometry and rebuild only on edit or
  stream-in, instead of every frame.

### A1. Activity levels (replaces chunk-home-based activation)

Project setting `settings.activity`:

```jsonc
"activity": {
  "focus": "camera",                 // or "tag:Player"
  "defaultLevel": "Normal",
  "hysteresis": 4,                   // world units, avoids flapping at the edge
  "wakeBudgetPerFrame": 64,
  "levels": [
    { "name": "Always",  "distance": null },               // never sleeps
    { "name": "Near",    "distance": 48, "idleRate": 2 },
    { "name": "Normal",  "distance": 24, "idleRate": 1 },
    { "name": "Ambient", "distance": "view", "idleRate": 0 }  // only while on screen
  ]
}
```

- **Per-entity setting:** the `Activity` component, a successor to
  `ChunkActivationComponent`, holds:
  - `level`
  - an optional `idleRate` override
  - the keep-rendering, keep-physics and keep-scripts flags
  - optional `wakeTogether`, which makes children share their parent's state
- **Zone override:** an Event Area gets an optional
  `activityOverride { level, when: "whileFocusInside" | "always" }`.
- **Effective level** comes from the first of these that applies:
  1. the strongest override among the zones that contain the entity
  2. the entity's own level
  3. `defaultLevel`
- **Distance** is measured from the focus to the entity's world position each
  frame, after transforms. The evaluation runs over a spatial grid and is
  staggered, so it doesn't spike. This replaces static home chunks, and moving
  entities just work.
- **Parsing:** the setting is read in all three settings parsers and in
  `ProjectDocument.kt`. The editor gets a **Project Settings → Activity** page
  for editing the levels.
- **Tiled compatibility:** the old Tiled `ChunkActivation` modes map onto
  levels, so the Tiled path keeps working.
- **Tile collision** follows tilemap streaming, not just the view, so off-screen
  bodies keep their terrain.
- **Two stages:**
  1. **Sleep:** the entity stays in memory and its systems are suspended. This
     stage comes first.
  2. **Unload:** serialize, destroy and later restore. This is a later
     follow-up.

### A2. sleep / wake / idle (Lua lifecycle)

These follow the existing callback naming (`init`/`update`/`destroy`):

- `sleep(entity)`: runs once when the effective level puts the entity out of
  range, or when script calls `Engine.Activity.sleep(entity)`.
- `wake(entity, sleptSeconds)`: runs once when the entity comes back, so it can
  catch up on timers, growth or AI state.
- `idle(entity, dt)`: the low-power tick while asleep, at the level's
  `idleRate` or the entity's override. `dt` is the time since the last `idle`.
  Ticks are staggered across frames, and entities without `idle` cost nothing.
- Lua can also call `Engine.Activity.setLevel(entity, name)` and
  `Engine.Activity.isAwake(entity)`, and subscribe to `activity.sleep` and
  `activity.wake` events.

### C1. Cameras

- **Standard zoom (D7):** `zoom > 1` zooms in everywhere. Lua `Engine.Camera.*`
  operates on the **active camera entity**, using center-based positions. The
  built-in camera remains only as a fallback when a scene has no camera.
  Changing the Lua zoom direction is documented in the guide.
- The active camera is chosen explicitly with a `priority` field: the highest
  enabled priority wins, and `primary` maps to the top priority for
  compatibility. Add `Engine.Camera.setActive(entity)`.
- New component fields:
  - `follow` (entity reference)
  - `damping`
  - `deadZone`
  - `bounds`: none, a rectangle, or a tilemap entity's extents
  - `pixelPerfect`: integer scale with snapped position, with letterbox fallback
- The camera resets on scene switch. The streaming focus is the current
  frame's camera center.
- Later: shake (`Engine.Camera.shake`), then split-screen.

### L1. Project picker, file associations, single instance

- The **project picker** is its own small Compose window. It appears when the
  editor starts without a project, and after File → Close Project. It offers:
  - Recent projects with path, last opened time and a missing-file state
  - New Project (name, location, art style, template: Empty or Tilemap starter)
  - Open…
  - A "Reopen last project on start" option

  Opening a project closes the picker and opens the editor window.
- **Opening files from the OS:**
  - `main` accepts a positional `.sleproject` path.
  - Register `.sleproject` through Compose `nativeDistributions` file
    associations: Windows MSI/EXE, plus a Linux `.desktop` `MimeType=` entry,
    a mime XML file and `Exec=sle-editor %f`.
  - Portable ZIP and AppImage builds get "Register as default for .sleproject"
    in editor settings. On Windows it writes HKCU classes, so no admin rights
    are needed.
  - `.tilemap.json` and `.scene.json` can't be associated: Windows keys on the
    last extension, so the association would claim all `.json` files.
- **Single instance:** a lock file plus a localhost socket in the user data
  dir. A second launch forwards its path to the running editor, which asks
  before switching away from a dirty project.

### U1. UI standardization (D9)

- **Form layout:**
  - One `EditorForm`/`EditorPropertyRow` grid: a fixed label column with
    aligned labels and fields on a shared baseline.
  - Each field has one height per density.
  - Groups use section headers rather than boxes.
- **Button variants:** exactly four, each with defined uses:
  - primary
  - secondary
  - ghost/icon
  - danger
- **Placement rules:**
  - Dialog footers: primary on the right, Cancel next to it.
  - Panel actions live in the panel header.
  - Tools go in the left toolbar group, view options in the right one.
  - Overflow goes into a `⋯` menu instead of wrapping rows.
- **Spacing:** a 4dp grid. All gaps and paddings come from `EditorMetrics`,
  with no ad-hoc `dp` literals.
- **Tighter menus:** smaller row height, shortcut column aligned, grouped
  separators, consistent naming ("Tile Palette", not "Tiles…").
- **One dialog shell** (`EditorDialog`): title, body, footer, Esc/Enter. It
  replaces the `JOptionPane` prompts. File pickers use the native OS dialog
  instead of `JFileChooser`.
- **States:** empty states always name the next action. Disabled controls
  explain why in a tooltip. All lists and trees share one row style.
- **Audit first:** capture every panel and dialog in both themes through smokes
  or `-Dsle.workspaceData`, so the real layout stays untouched. Re-capture
  after the sweep for comparison.

## Phases

Each phase can land on its own. Engine phases (5–7) can run in parallel with
editor phases (2–4, 8–9).

| # | Phase | Scope | Acceptance |
|---|---|---|---|
| 0 | **Unblock tile authoring** | Quick wins on today's model; see the Phase 0 steps below | Fresh project → tileset → layer → paint without editing JSON (editor smoke) |
| 1 | **UI foundation** | `EditorDialog`, form grid with aligned labels, 4 button variants, menu density, toolbar groups + overflow, native file picker, screenshot audit | No `JOptionPane` in project flows; form rows aligned; before/after screenshots |
| 2 | **Tileset v2 + Tileset Editor** | T2 | Create, reslice, edit collision/tags/properties in the tool window; v1 still loads |
| 3 | **Tilemap asset + `Tilemap` component** | T1: loader, render, collision via placement transform, v1 conversion | Two scenes reference one map; moved placement renders/collides correctly; v1 scene converts; integration tests |
| 4 | **Tile Palette window + streaming** | T3, tilemap `streaming`, chunk geometry cache | Paint from two tilesets on one layer; chunks stream around focus; perf check |
| 5 | **Activity levels** | A1: settings, `Activity` component, distance evaluation, zone overrides, Project Settings page | Entities sleep/wake by level and distance; zone keeps its enemies active while focus is inside |
| 6 | **sleep / wake / idle** | A2 + Lua API + events | Integration test: idle rate, staggering, `sleptSeconds`, zone override |
| 7 | **Cameras** | C1 | Camera integration tests; guide documents the Lua zoom change |
| 8 | **Project picker** | L1 picker + New Project form + templates + Close Project | Launch without a project → picker; open/create/recent work (smoke) |
| 9 | **File association + single instance** | L1 association, positional arg, HKCU register, forwarding | Double-click `.sleproject` opens it (installed build); second launch forwards |
| 10 | **UI sweep** | Remaining punch list from the Phase 1 audit | Screenshot comparison reviewed |

## Phase 0: unblock tile authoring (refined)

**Goal.** In a fresh project with only a PNG, the user can do all of the
following without touching JSON and without hunting for buttons:

1. make a tileset
2. add a tile layer
3. paint
4. save and reopen

**Constraint.** Use today's v1 formats, and build only what later phases keep:

- The slicer button, the tileset writer and the palette placement survive.
- The menu entry turns into Create → Tilemap in Phase 3.
- Dialogs stay `EditorDialogs` until Phase 1 replaces them.

**Out of scope.** These are covered by later phases:

- Tile layers in the Hierarchy (Phase 3 tilemap entities)
- Atlas grid picker (Phase 4)
- Multi-tileset layers (Phase 3)
- Compose dialogs (Phase 1)

There is no layer `pixelsPerUnit` or origin editing either. The default of 1
already matches the 480×270 pixel-unit camera, and Phase 3 gives maps a
Transform.

### Steps

**0.1 Tileset writer and shared parser (model only).**
- Move the v1 parse logic out of `readTilePalettes`
  (`ui/TilePaintingControls.kt:58-102`) into `model/TilesetDocument.kt` as a
  `parseTileset(...)`.
- Add a `writeTileset(spritesheetUuid, tileWidth, tileHeight, regions): ByteArray`.
  It writes exactly the v1 keys the native loader accepts
  (`TileLayerLoader.cpp:160,168`):
  - One tile per region, ordered row-major by the region's (y, x), so the
    palette order matches the sheet.
  - A new UUID per tile, with the tile name taken from the region name.
- Unit test: write, then parse, round-trips. Also test with an empty region
  list and with a duplicate name.

**0.2 "Create Tileset…" in the spritesheet slicer.**
- In grid mode (`project/assets/SpritesheetDialog.kt:137-149`), add a primary
  **Create Tileset…** button next to "Add grid regions".
- It works as one action:
  1. Add any missing grid regions, through the existing `editRegions` path.
  2. Ask for a file name (`EditorDialogs.showInputDialog`), defaulting to
     `<texture name>.tileset.json` in the texture's folder.
  3. Create the file through `ProjectAssetOperations.createFile`, which also
     registers its UUID.
- Tile size is the grid cell size. The tiles are the regions that match the
  current grid cells, so "Skip fully transparent cells" carries through.
- Plumbing: a new `ProjectAssetCommands.createTileset` field
  (`ProjectAssetControls.kt:15-30`), implemented like `createStateMachine`
  (`:105-119`).
- Afterwards, show "Created X · open the Tile Palette to paint" and make the
  new tileset the palette's selected tileset for new layers.

**0.3 Entry points to the slicer.**
- **Asset Browser:** a texture selected in the details pane shows **New
  Tileset…** next to "Open spritesheet…" (`SpriteRegionControls.kt:18`). It
  opens the slicer in grid mode.
- **Asset Browser actions** (`AssetBrowserPanel.kt:135-136`): add **New
  Tileset…**. With no texture selected, it first asks for one through the
  existing `browse(imageOnly = true)`, which can also import.
- **Palette empty state:** see 0.7.

**0.4 Create → Tile Layer.**
- Add a **Tile Layer** submenu to the `Create` list in `sceneCreationMenu`
  (`ui/EditorContextMenu.kt:258-274`). That one list feeds three places:
  - the menu bar Create menu (`EditorMenuStrip.kt:136`)
  - the viewport context menu (`Main.kt:655`)
  - the Hierarchy and empty-scene context menus (`SceneTree.kt:119-131`,
    `SceneWorkspace.kt:30`)
- The submenu lists each valid project tileset by name, then a separator,
  then **New Tileset…**.
- Picking a tileset does three things:
  1. Calls `addTileLayer(reference)` (`SceneWorkspaceState.kt:175`).
  2. Activates the Tiles tool and the new layer.
  3. Opens the palette.
- With no project scene open, the entry is disabled with a reason.
- Plumbing: add an optional `tileLayers: TileLayerCreation?` parameter to
  `sceneCreationMenu`, holding the tileset list and a create callback. It is
  passed from `Main.kt`, where `tileOptions` and `manager` already live.
  Existing tests pass `null`.

**0.5 T, the Tools menu and the toolbar open the palette.**
- `ToolCommands.tiles` (`command/ToolCommands.kt:18-20`) currently only
  activates the tool. Add an `openTilePalette()` action to the command
  actions, and have the Tiles command call it too.
- In the toolbar, move Tiles into the Q/W/E/R tool group
  (`ViewportToolControls`) as an icon tool with a "Tiles (T)" tooltip. The
  separate labeled "Tile Palette" button (`Main.kt:915-932`) and the hidden
  state labels are kept next to it.
- Add a tiles glyph to `EditorIcons.kt`; the command currently has
  `icon = null`.

**0.6 Palette docks on the right.**
- The default placement goes into the `inspector` tab group instead of
  `assets`:
  - `workspace/DockLayout.kt:65`
  - the codec fallback neighbour (`WorkspaceLayoutCodec.kt:113`)
- Saved layouts that already placed the palette keep their placement.
- Raise the panel's minimum width only if 360 px doesn't fit next to Details.
  Check this in the smoke screenshot.

**0.7 Palette empty states lead forward** (`TilePaintingControls.kt:148-158,248-255`).
- **No tilesets:** a short explanation plus a **New Tileset…** button, which
  runs the 0.3 flow.
- **Tilesets but no layers:** the tileset select and **Add Tile Layer** come
  first, with the primary style.
- Tilesets that fail to parse are listed one per line, not only the first.
- Rename the "Tiles…" wording to "Tile Palette" in `tools/editor/README.md`
  and the plan references.

**0.8 Tests.**
- **Unit:**
  - the 0.1 writer/parser round-trip
  - `sceneCreationMenu` has Create → Tile Layer with the tileset entries, plus
    New Tileset…, and is disabled when `tileLayers` is null
  - the default `DockLayout` places `tilePalette` in the inspector group
  - the Tiles command calls `openTilePalette`
- **Smoke** (extend `TilePaintingSmoke` or add a `FreshTilemapSmoke`), on a
  temp copy of a new fixture `tests/data/tilemap-fresh/` that holds only a
  project and one PNG:
  1. Create the tileset through the 0.2 command path.
  2. Create → Tile Layer through the menu entry's action.
  3. Paint a few cells with the Robot, then save.
  4. Reopen the scene.
  5. Assert the cells persist and the native viewport loads the scene with no
     error, since the native loader is the strict check on the written file.
- Run smokes with `-Dsle.workspaceData` (or the smoke harness) so the user's
  real layout is untouched.

**0.9 Docs.**
- `docs/guide/tilemaps.html:44-55`: replace the copy-and-hand-edit step with
  the slicer → Create Tileset flow and Create → Tile Layer.
- Also update `examples/tilemap_room/README.md` step 3 and
  `tools/editor/README.md`.

### Phase 0 defaults (change if wrong)

- New tilesets are saved **next to their texture**. Phase 2 can add a
  `Tilesets` folder to the project layout.
- Tile names come from the region names the slicer generates.
- Tiles becomes a **tool icon** in the Q/W/E/R group, as in other editors'
  paint tools.

### Phase 0 validation (run 2026-11-26 on Windows)

- `tools/editor/gradle.ps1 test --offline --no-daemon -Pkotlin.incremental=false`: 490 tests,
  3 skipped, 0 failures. New: `TilesetDocumentTest`, `TileAuthoringEntryTest`, and two tile-palette
  cases in `DockLayoutTest`.
- `run --args=--fresh-tilemap-smoke`: `FRESH_TILEMAP_SMOKE_PASS` (fixture `tests/data/tilemap-fresh/`).
- Regression smokes, all passing: `--tile-painting-smoke` (now waits for T to open the palette),
  `--asset-browser-smoke`, `--context-menu-smoke`, `--docking-workspace-smoke`, `--scene-smoke`.
- Not run: the manual check with a restart of the real editor.

### Phase 0 validation plan

- `tools/editor`: `gradlew test`, plus the tile smoke task, run with a
  workspace-data override.
- Manual: a fresh project → import a PNG → New Tileset… → Create → Tile Layer
  → paint → save → restart the editor → reopen.

## Phase 1: UI foundation (refined)

**Goal.** Shared building blocks that later phases reuse. These are one dialog shell, one
form row and four button variants. Menus get tighter and the Scene toolbar gets an overflow
menu instead of wrapping. Project flows stop using `JOptionPane`.

**Survey (2026-11-26).**
- `EditorDialogs` (`ui/EditorDialogAppearance.kt`) themes `JOptionPane`. It has about 25
  production call sites, which pass `JOptionPane` option and result constants.
- Smokes answer prompts by finding the `JOptionPane` in the window tree.
- `EditorFileChooser` is a themed `JFileChooser` with 12 call sites.
- `EditorButtonStyle` has Primary, Secondary and Ghost.
- `EditorPropertyRow` sizes its label as a fraction of the width.
- Menu rows have 7 px vertical padding.
- `EditorToolbar` is a `FlowRow`, so the Scene toolbar wraps onto a second row.

### Steps

**1.0 Audit.** Capture panels in both themes with `--editor-appearance-smoke`, and dialogs
with the auxiliary captures in `--context-menu-smoke`. Copy them to
`tools/editor/build/reports/ui-audit/before/`. Repeat into `after/` at the end.

**1.1 `EditorDialog` shell** (`ui/dialog/EditorDialog.kt`).
- One composable layout: body, then a footer.
- Footer: actions on the right, primary rightmost, Cancel to its left.
- Esc runs the cancel action. Enter runs the default action, except in multi-line text.

**1.2 Blocking prompts on the shell** (`EditorPrompts`, which replaces `EditorDialogs`).
- They run in a modal `ComposeDialog` owned by the editor window.
- Typed calls: `confirm`, `message`, `choose`, `input` (with inline validation), `select`
  (pick one value) and `pickFromList`. No `JOptionPane` types reach callers.
- Smoke seam: the open prompt is exposed as `EditorPrompts.current`, with its labels and an
  `answer(...)` call. Smokes still see a real dialog on screen but answer it without pixel
  hunting.
- Migrate every production call site. The Keyboard Shortcuts and About dialogs move to the
  shell too.

**1.3 Native file picker** (`ui/EditorFilePicker.kt`).
- `openFile` and `saveFile` use `java.awt.FileDialog`. That is the OS dialog on Windows and
  macOS, and GTK on Linux.
- Pickers that must return a folder keep the themed `EditorFileChooser` in folder mode. A
  native folder dialog would need a new dependency (for example LWJGL `nfd`); the user chose to
  keep the themed chooser for folders.
- Import… keeps the themed chooser, because it picks files and folders together and no
  native dialog does.

**1.4 Form grid.**
- `EditorFormRow(label)` has a fixed label column (`EditorMetrics.formLabel`), and its field
  is one `field` height per density.
- `EditorSectionHeader` groups rows, without boxes.
- Apply it to the prompt inputs, the spritesheet slicer fields, Configure Snap and the Tile
  Palette's layer settings.
- The Inspector keeps `EditorPropertyRow` until the Phase 10 sweep.

**1.5 Four button variants.**
- `EditorButtonStyle` becomes exactly Primary, Secondary, Ghost and Danger.
- Primary: the one action a surface exists for.
- Secondary: other actions.
- Ghost: low-emphasis or toolbar actions.
- Danger: destructive confirmations.

**1.6 Menu density.**
- Menu rows get less vertical padding (7 → 4 px, scaled).
- The shortcut column stays right-aligned.

**1.7 Toolbar groups and overflow.**
- `EditorToolbarRow` lays out a left group (tools) and a right group (view options) on one
  line.
- Items that don't fit move into a `⋯` menu, so the row never wraps.
- Apply it to the Scene and Game view toolbar.

**1.8 Tests and docs.**
- Unit tests: the footer action order and keys, the prompt result mapping, and the overflow
  split.
- Update the smokes that answered `JOptionPane`.
- Re-run the affected smokes and the audit.
- Update `tools/editor/README.md`.

### Phase 1 validation (run 2026-11-26 on Windows)

- `tools/editor/gradle.ps1 test --offline --no-daemon -Pkotlin.incremental=false`: 501 tests, 3 skipped,
  0 failures. New: `EditorPromptsTest`, `EditorToolbarRowTest`. Updated: `KeymapTest`,
  `EditorDialogAppearanceTest`.
- Desktop smokes, all passing after the toolbar fix: `--project-distribution-smoke` (with the runtime
  smoke), `--scene-smoke`, `--tile-painting-smoke`, `--fresh-tilemap-smoke`, `--docking-workspace-smoke`,
  `--project-documents-smoke` (with the prefab workflow), `--context-menu-smoke`, `--asset-browser-smoke`,
  `--project-smoke` and `--editor-appearance-smoke` (dialogs and panels in both themes).
- Audit screenshots are in `tools/editor/build/reports/ui-audit/before/` and `after/`. The `after/default/scene-dark.png`
  capture was taken before the Scene view fitted its content; `scene-light.png` shows the same view rendered.
- Not covered: native file dialogs are answered through `EditorFilePicker.answerNext` in smokes, so the OS
  dialogs themselves were not exercised; no manual pass in the real editor.

## Validation (filled in as work lands)

- Engine: `cmake --build` plus the focused `tests/integration` targets for the
  tilemap, activity and camera paths (see `CONTRIBUTING.md`).
- Editor: `gradlew test` plus the relevant smokes (`TilePaintingSmoke`,
  `CameraPlaySmoke`, and new picker and tileset-editor smokes). Never run a
  plain `gradlew run` against the real workspace layout.

## Progress

- 2026-11-26: Surveyed code; drafted plan; recorded decisions D1–D9; wrote phases and Phase 0 draft. No implementation.
- 2026-11-26: Refined Phase 0 against the code (0.1–0.9). Dropped the `pixelsPerUnit` step: the default of 1 matches the pixel-unit camera.

- 2026-11-26: Implemented Phase 0 (0.1–0.9).
  - `model/TilesetDocument.kt` holds `parseTileset`/`writeTileset`; the palette reads tilesets through it.
  - The slicer's Grid mode has **Create Tileset…** (`ProjectAssetCommands.createTileset`): it adds missing
    grid regions, then writes the v1 tileset next to the texture and selects it in the palette
    (`TilePaintingState.newLayerTileset`).
  - **New Tileset…** sits in the texture details, the Assets toolbar and the empty palette. It opens the
    slicer for the selected texture, or asks for one first. The main window hosts that slicer
    (`ProjectAssetCommands.tilesetTexture`).
  - **Create → Tile Layer** comes from `sceneCreationMenu(tileLayers = TileLayerCreation)`; the tree and
    Scene workspace read it from `LocalTileLayerCreation`.
  - The Tiles command (T, Tools menu, new toolbar icon) calls `EditorActions.openTilePalette()`.
  - The palette defaults to the Details tab group, for new layouts and for saved layouts without it.
  - Deviations: the Tiles glyph reuses the existing `tilemap` icon (no new art). The palette keeps its
    360 px minimum; it fits beside Details and widens that column while shown (see
    `tools/editor/build/reports/fresh-tilemap-palette.png`). The smoke creates the scene with
    `ProjectDocuments.save`, because the fixture holds only a project and a PNG. It calls the
    `createTileset` command directly (the button's file-name prompt is a modal Swing dialog), after
    checking that New Tileset… opens the slicer.
  - Follow-ups: `docs/guide/images/tiles-and-collision.png` still shows the palette below Scene and
    needs a re-capture. Tile names come from region names ("Slice N" unless renamed first).

- 2026-11-26: Implemented Phase 1 (1.0–1.8).
  - `ui/dialog/EditorDialog.kt` has the shell (`EditorDialogLayout`, `footerOrder`, `dialogKeyAction`)
    and the typed blocking `EditorPrompts`. `PromptWindow.kt` hosts a prompt in a modal `ComposeDialog`.
  - Every production `EditorDialogs` call moved to `EditorPrompts`, and `EditorDialogs` was deleted.
    Help → Keyboard Shortcuts and About use the shell too (`keyboardShortcutTable` replaces the HTML).
  - Destructive confirmations use Danger buttons named for the action: Discard, Delete, Move to Trash,
    Replace, Assign New UUID. Trusting editor scripts keeps Keep Disabled as the Enter default.
  - `ui/EditorFilePicker.kt` (native `FileDialog`) replaces `EditorFileChooser` for every file pick.
    Folder picks and Import… stay on the themed chooser.
  - `EditorButtonStyle.Danger`, `EditorFormRow`, `EditorSectionHeader` and `EditorMetrics.formLabel` were
    added. Form rows are applied to prompts, the slicer, Configure Snap (also on the shell) and the Tile
    Palette.
  - Menu rows went from 7 to 4 px vertical padding (6 in Comfortable), and separators from 9 to 7 px.
  - `EditorToolbarRow` replaces the wrapping Scene and Game toolbar. Groups are run, play or sandbox,
    tools, tile palette and gizmo on the left, view on the right, and each group has overflow menu
    entries.
  - The row is a plain `Layout`; hidden groups are measured but not placed. A first version used
    `SubcomposeLayout`. Run Project switches to the Game tab, which disposed the Scene panel while a
    subcomposed tooltip popup was still rendering. That raised "RootNodeOwner is already disposed" and
    took down the main window composition. `--project-runtime-smoke` caught it.
  - Smokes answer prompts through `smoke/PromptAnswers.kt` and file picks through
    `EditorFilePicker.answerNext`.
  - Decided 2026-11-26: folder picks and Import… keep the themed `EditorFileChooser`; no new dependency
    (LWJGL `nfd`) for a native folder dialog.

- 2026-11-27: Implemented and validated Phase 2 (T2). See
  [the Phase 2 implementation and validation record](tileset-editor-phase2.md).
  - Native and editor loaders accept strict tileset v2 metadata and retain v1 support.
  - New Tileset writes grid metadata and opens the dockable/detachable Tileset Editor.
  - The tool supports atlas rectangle selection, metadata, collision handles and alpha
    generation, UUID-preserving reslice, add/remove, history, explicit Save and Revert.
  - Drafts survive hiding, detaching and project switches. Saves guard source bytes,
    asset identities, changed atlas bounds, and referenced scene collision scales.
  - Editor: 531 tests, 3 skipped, 0 failures. Tileset Editor smoke passed in Dark
    and Light, including a post-reslice collision-handle regression. Fresh tilemap
    and docking workspace regression smokes passed. Windows native tilemap tests:
    3/3 passed; Linux non-windowed integration: 64/64 passed.
  - Engine version is 0.34.0; editor version is 0.35.0. No commits or publication.

## Feedback log (user's pointers from real use)

<!-- Add notes as "- YYYY-MM-DD [area] what happened / what you expected". -->
