# Editor game-making workflows — roadmap and UX rules

**Status:** Planning only (2026-11-17); decisions recorded in §8, including editor Lua scripting (L6). Nothing here is implemented or validated.
Each phase below becomes its own `docs/plans/<name>.md` (per [PLANS.md](../../PLANS.md))
when we decide to start it. [editor-polish.md](editor-polish.md) sessions 6–7
(prefab drag, temporary prefab tabs, Apply to Prefab) remain the active editor
work and are assumed done before Phase 2 here.

**Goal:** Shorten the loop *idea → something playable → tweak → share* for a
solo/indie developer making a 2D game in SLE, by borrowing proven workflows from
Unity, Godot and Unreal — only the parts that fit a small 2D editor — and by
fixing interaction rules now so new features stay cheap to add.

Visual rules (tokens, density, controls) already exist in
[tools/editor/UI_DESIGN.md](../../tools/editor/UI_DESIGN.md) and
[HOW_TO_ADD_UI_FEATURES.md](../../tools/editor/HOW_TO_ADD_UI_FEATURES.md).
This plan adds the **interaction** rules those documents do not cover yet.

---

## 1. Where the editor is today

Evidence: `tools/editor/README.md`, `docs/topics/SceneEditor.md`, editor sources.

| Area | Have | Missing (relevant to making games) |
|---|---|---|
| Project | New/Open/Recent, startup scene, last-scene recovery, Export Game, Run Project | Templates/starter content, input map, layer names |
| Hierarchy | Tree, create/duplicate/delete subtree, parent via Inspector, context menus, arrow selection | Search/filter, multi-select, inline rename (F2), drag-to-reparent/reorder, hide/lock toggles |
| Viewport | Editor camera, pan/zoom, Fit, move entity, right-click create at point, asset drop, collider/sprite handles (Entity tab), Snap 0.1 | Rotate/scale gizmos, configurable grid snap, frame selected (F), marquee select, Alt-drag duplicate, overlay toggles (colliders, grid, physics debug) |
| Inspector | Catalog-driven rows, numeric edit contract, add/remove component, Fit, texture thumbnail | Multi-object edit, reset-to-default, copy/paste values & components, ranges/tooltips/units metadata, prefab override markers, Lua script parameters |
| Prefabs | Linked instances, subtree prefabs, merge with overrides (session 5) | Drag-to-create, prefab tab, Apply (session 6); overrides list, revert, unpack, variants |
| Assets | Real disk tree, import, rename/move/delete with UUID sidecars, trash, details | Quick open, thumbnails/grid, find references, per-asset import settings (filter, slicing, pivot) |
| Play | Play/Pause/Stop, Scene+Game side by side, Run Project | Play tint, frame step, live inspection of running entities, keep-play-changes, Lua hot reload, pause on error |
| Scripting | VS Code as external editor, behaviours array | New-script templates, open-at-line, Console with click-to-source, exposed script parameters |
| 2D content | Sprite size/offset/region, Animator/StateMachine components, Tiled loader in engine | Sprite sheet slicing, flipbook/animation editor, tilemap authoring/preview, collision-layer matrix, sorting-layer names |
| Feedback | Notices, smokes | Console/log panel, status-bar hints, background task progress |
| Commands | Menu strip, context menus, Ctrl+S/Z/Y/D, Delete | Central command registry, shortcut editor, command palette |

Engine facts that constrain the plan: `sle::core::Log` prints to stdout only (no
sink the editor can read); no input-action map (scripts read key codes);
`Texture` already has a Nearest/Linear `Filter`; `TiledMapLoader` exists;
SpriteRenderer `layer` and collider `categoryBits/maskBits` are raw numbers.

---

## 2. What we borrow (and from whom)

Only ideas that are cheap for us and high value for 2D games. Source docs are
listed in §7.

| Idea | Best source | Why it fits SLE | SLE adaptation |
|---|---|---|---|
| Tool modes Q/W/E/R + rect tool | Unity | Universal muscle memory; maps cleanly to 2D | `ViewportTool` registry; one active tool owns gizmo, input and status hint |
| Grid/pixel snapping toggled by modifier | Godot 2D | 2D games live on a grid | Project-level grid size; Ctrl inverts snapping while dragging |
| Frame selected (F), marquee, Alt-drag duplicate | Unity / Godot | Speeds up every edit | Reuse existing Fit/picking/duplicate commands |
| Hierarchy search, hide/lock (editor-only) | Unity scene visibility & pickability | Big scenes stay workable | Stored in project-local `.sle/` state, never in scene files |
| Multi-object Inspector editing with mixed values | Unity / Godot | Tweak 20 coins at once | Shared selection set; one undo transaction across entities |
| Per-property Reset / Copy / Paste; component Copy/Paste values | Unity | Cheap, used constantly | Context menu on `EditorPropertyRow` / section header |
| Prefab override markers + per-property Revert/Apply; Overrides list | Unity prefab overrides | We already store explicit overrides | Accent bar in the row's reserved slot; instance header "Overrides ▾" |
| Isolated prefab editing tab with breadcrumb | Unity Prefab Mode / Godot "open scene" | Already planned as session 6 | Temporary tab, breadcrumb back to scene |
| Prefab variants (inherit from prefab) | Unity variants / Godot inherited scenes | Enemy types, palette swaps | Later: prefab whose source is another prefab + overrides |
| Exposed script parameters | Godot `@export` / Unity public fields | Designers tune without opening Lua | Behaviour declares `properties` table; Inspector shows them per behaviour (engine change) |
| Script from template, open at line | Godot / Unity | Removes boilerplate and friction | `File → New → Lua Behaviour`; Console links open VS Code `file:line` |
| Console with filters, collapse, click-to-source | Unity Console / Godot Output+Debugger | Lua errors are the #1 friction | Engine log sink → JNI drain → Console panel |
| Play-mode tint, pause + step frame | Unity | Prevents losing edits made while playing | Tinted chrome during Play; Step button |
| Live inspection during play | Godot Remote tree / Unity play-mode Inspector | See what scripts actually did | Read-only live Inspector of runtime entities first; editing later |
| Keep changes made during play | UE "Keep Simulation Changes" | Fixes the classic "tuned it in play, lost it" problem | Per-component "Keep after Stop" → written into authored doc on Stop as one undo step |
| Lua hot reload on save | Godot / Unity | Fast iteration | Reload behaviour environments on file change while running |
| Quick Open (Ctrl+P) and Command Palette | Godot Quick Open / UE / VS Code | Discoverability without menu hunting | Views over the asset index and the command registry |
| Lua console | Roblox Studio command bar / UE Output Log console | Bulk edits and debugging in the game's own language | Panel beside the palette. When stopped, each run is one undo step on the scene; while playing it runs in the game runtime and its effects end at Stop |
| Script properties declared as data | Defold `go.property` / Godot `@export` | Lua has no annotations; a returned table needs no Lua parser | Behaviour returns `properties = { … }`; the engine reads it in a sandbox and converts it to L2 descriptors |
| Editor scripting in the game language | Defold editor scripts / Unity editor scripting | Unity's approach, without needing a Kotlin/JVM toolchain | `Editor/*.editor.lua` registers commands; `run` returns edits that the editor applies as one undo step |
| Texture import settings in sidecar | Godot Import dock / Unity importer | Pixel art needs Nearest; sheets need slicing | Extend `.slemeta` with filter, pivot, slices |
| Sprite sheet slicing + flipbook animation | Unity Sprite Editor / Godot SpriteFrames | Most 2D games animate frames | Slices become named regions; flipbook editor writes Animator clips |
| Native tilemaps: TileSet asset + paint tools | Godot TileSet/TileMap editor, Unity Tile Palette | Level building is core 2D work; decided to do natively | Engine TileMap component + TileSet asset; brush/rect/fill/erase/pick; per-tile collision; terrain autotiling later. Tiled stays an import path |
| Named collision layers + matrix | Unity Physics2D matrix / Godot layer names | Raw bit masks are error-prone | Project settings names 16 layers; Inspector shows checkboxes |
| Project templates | All three | First 10 minutes matter most | Empty / Platformer / Top-down using the starter assets |
| Unique names and groups/tags for script lookup | Godot `%Name`, groups | Scripts stop hard-coding IDs | Entity data `tags[]`/unique name; Lua `find`/`group` (engine) |
| Smart snap + ruler guides, pivot/center toggle | Godot 2D | Alignment without math | Later step of the snapping service |
| Hold-to-snap modifier per gizmo | UE viewport snapping | Snap only when wanted | Same modifier inverts snapping in every tool |
| Slider range separate from hard clamp; edit conditions | UE `UIMin/ClampMin`, `EditCondition` | Fewer invalid values, tidier inspector | Descriptor fields `uiMin/uiMax`, `min/max`, `visibleWhen` |
| Prefab editing *in context* with the scene greyed | Unity Prefab Mode in Context | Edit an enemy where it actually sits | Option on the prefab tab; isolation stays default |
| Named, mergeable undo steps → History panel | UE transactions / Godot History | Drags become one step; history becomes inspectable | Merge key on transactions (R3) |

---

## 3. UX and architecture rules for new features

These extend UI_DESIGN.md. They are short on purpose; each rule names the one
place a feature plugs in so nobody invents a parallel mechanism.

**R1 — One selection model.** A document-scoped `Selection` (ordered set of entity
keys + one *active* key) is the only selection state. Hierarchy, viewport,
Inspector, context menus and commands read and write it. Design every new feature
for N selected items, even if it first supports one (disable with a tooltip when
N≠1).

**R2 — Everything is a command.** Each user action is registered once:
`id`, label, icon, default shortcut, *context* (scene, prefab, asset, playing,
stopped) and an `enabled(selection, context)` predicate. Menus, context menus,
toolbars, shortcuts, the command palette and tooltips are *views* of that
registry. No action logic lives only inside a menu lambda. (Existing menus migrate
gradually; new commands start there.)

**R3 — Every authored mutation is a named transaction.** Views emit intents; the
model applies them inside an undo transaction with a human label ("Move 3
entities"). Drags, multi-edits and numeric edits group into one step (already the
numeric contract) via a merge key or explicit begin/end group. Runtime changes
during Play never enter authoring history.

**R4 — Same target, same menu.** Context menus are built from the registry by
target type (entity, component, property, asset, empty viewport). An entity menu
looks identical in Hierarchy and viewport. Every drag gesture has a menu or
command equivalent (no hidden-only gestures).

**R5 — Panels register, they don't own.** New panels go through `PanelRegistry`
with id, title, icon, default dock location and an `EditorEmptyState`. Panels do
no file I/O and never create engine sessions; they observe services.

**R6 — Inspector is data-driven, with one extension point.** Field kind, range,
step, unit, tooltip, group and visibility come from native catalog metadata, plus
one editor-side metadata table for things C++ cannot express. A component that
truly needs a custom UI registers a *drawer* for its type (Unity PropertyDrawer /
Godot EditorInspectorPlugin idea) — never a hand-built inspector branch in the
workspace code. Every row supports the same slots: reset, override marker,
validation, read-only.

**R7 — Viewport tools are modes.** One active `ViewportTool` (Select, Move,
Rotate, Scale, Rect, Shape edit, later Tile paint). A tool owns its gizmo drawing,
pointer handling, cursor and status-bar hint. Escape cancels the gesture; switching
tools finishes it. Snapping is a shared service every tool uses.

**R8 — One feedback channel per lifetime.** Transient info → status bar (auto
clears). Persistent messages and errors → Console with source links. Blocking
modal dialogs only for decisions (unsaved changes, destructive operations without
undo). Long work → status-bar progress with cancel.

**R9 — Safe by default.** Prefer undoable actions over confirmations. Destructive
actions that cannot be undone confirm and name what is lost. File writes stay
atomic. Play mode is visibly different, and anything edited during Play is shown
as temporary until explicitly kept.

**R10 — Each setting has one home.** Before adding a setting, choose exactly one:
scene/prefab file (game data), `.sleproject` (shared project settings), project
`.sle/` (per-user project state: layout, hidden/locked, last scene), or
user `appearance.json`/prefs (editor-wide). Editor-only state never goes into
game data.

**R11 — Discoverable.** Every command shows its shortcut in tooltip and menu.
Empty panels say what to do next. New concepts get one line in Help and the
topic doc.

**R12 — Definition of done for a slice.** Model unit tests; a smoke when it
adds desktop interaction; both themes, both densities, narrow dock and detached
window checked; command registered with shortcut; docs updated; new controls in
the theme gallery.

**R13 — Build in layers; partial features are fine.** SLE has no public
releases yet, so features land as working slices on top of shared base layers
(§5) rather than all-at-once. A partial feature may ship as long as what is
visible works end to end; nothing looks clickable without doing something.
Missing parts are listed in the feature's topic doc under "Current limits".
Engine pieces are built once as general services (descriptors, runtime
inspection, asset metadata) so several features reuse them.

---

## 4. Roadmap

Effort: **S** ≈ one session, **M** ≈ 2–3, **L** ≈ 4+. "Engine" marks native C++
changes (need CTest coverage and an engine version bump per CONTRIBUTING).

### Phase 0 — Foundations (make later features cheap)

| # | Feature | Borrowed from | Effort | Notes |
|---|---|---|---|---|
| 0.1 | Command registry + shortcut map; migrate File/Edit/Create menus | UE / Godot shortcuts | M | R2, R4. Unblocks palette and shortcut editor |
| 0.2 | `Selection` service with multi-select (Ctrl/Shift in Hierarchy, Shift-click viewport) | All | M | R1. Delete/Duplicate work on sets |
| 0.3 | Console panel: engine log sink + Lua error capture with `file:line`, filters, collapse, clear on Play | Unity Console | M, Engine | `sle::core::Log` needs a sink; JNI drain per frame |
| 0.4 | Status bar: tool hints, transient notices, background progress | Unity / Godot | S | R8 |

### Phase 1 — Everyday authoring speed

| # | Feature | Borrowed from | Effort | Notes |
|---|---|---|---|---|
| 1.1 | Viewport tools W/E/R (move/rotate/scale gizmos) + Q select, T rect | Unity | M | R7. Rotation snap 15° with modifier |
| 1.2 | Snapping service: move/rotate/scale steps in one "Configure Snap" dialog, Ctrl toggles while dragging, pixel snap, grid overlay matches | Godot 2D / UE | S–M | Replaces fixed "Snap 0.1". Defaults come from the project's art style (see 6.0). Smart snap to other sprites and ruler guides come later |
| 1.3 | Frame selected (F), marquee select, Alt-drag duplicate, game-camera bounds overlay | Unity / Godot | S | Uses 0.2 |
| 1.4 | Hierarchy: search filter, F2 inline rename, drag to reparent/reorder, hide/lock toggles | Unity | M | Hide/lock in `.sle/` (R10) |
| 1.5 | Inspector: property context menu (Reset to default, Copy, Paste), component menu (Copy/Paste values, Reset, Move up/down) | Unity | S | Defaults already in catalog |
| 1.6 | Multi-object Inspector with mixed-value display | Unity / Godot | M | One transaction per edit |
| 1.7 | Field metadata: hard range, slider range, step, units, tooltip, group, `visibleWhen` | Godot `@export_range` / Unity `[Range]` / UE `EditCondition` | S–M, Engine | Extend catalog `ComponentDefinition`; the same descriptor schema later serves Lua parameters (3.3) |
| 1.8 | Quick Open (Ctrl+P) for scenes/prefabs/assets/entities with prefixes (`t:` type, `@` entity, `>` command); Command Palette (Ctrl+Shift+P) | Godot / Unity Search / VS Code | S | Needs 0.1 |

### Phase 2 — Prefab workflow (after editor-polish session 6)

| # | Feature | Borrowed from | Effort | Notes |
|---|---|---|---|---|
| 2.1 | Override markers on rows + per-property Revert / Apply to Prefab | Unity | M | Rows already reserve the slot |
| 2.2 | Instance header: "Open Prefab", "Select Asset", "Overrides ▾" (list, Apply all, Revert all) | Unity | S–M | |
| 2.3 | Unpack prefab instance | Unity / Godot "Make Local" | S | |
| 2.3b | Show overrides whose target no longer exists as "missing", with Remove, instead of dropping them silently | Lesson from Godot editable children | S | Overrides are already keyed by stable local keys |
| 2.3c | Prefab tab option "in context": the scene stays visible, greyed and locked | Unity Prefab Mode in Context | M | Isolation remains the default |
| 2.4 | Prefab variants | Unity variants / Godot inherited scenes | L, maybe Engine | Decide data model first |

### Phase 3 — Scripting loop

| # | Feature | Borrowed from | Effort | Notes |
|---|---|---|---|---|
| 3.1 | New Lua behaviour from template; attach in one step from Inspector | Godot / Unity | S | Templates in project `ScriptTemplates/` override built-ins |
| 3.2 | Open script / Console entry in external editor at line (VS Code default, configurable) | Unity external tools | S | User pref (R10) |
| 3.3 | Exposed behaviour parameters shown and edited per entity | Godot `@export` / Defold `go.property` | M–L, Engine | Behaviour returns a `properties` table, which the L6 host reads without running `init`. Optional LuaLS `---@` comments are only for IDE hints. Per-entity values are stored in the Script component per behaviour |
| 3.4 | Lua hot reload while playing, with a "reloaded"/"error" toast; "Pause on error" with entity highlight | Godot / Unity | M, Engine | Incremental and error-contained; keep state where possible, else restart the behaviour |
| 3.5 | Entity tags/groups and unique names, queried from Lua | Godot groups, `%Name` | M, Engine | Stored as entity data in scene files |
| 3.6 | Lua debugger through an external DAP adapter (breakpoints in VS Code) | Godot "Debug with External Editor" | L | Not a built-in code editor |
| 3.7 | Lua console panel: multi-line input, history, output. Stopped: edits the scene as one undo step per run. Playing: runs in the game runtime. Palette entry "Run Lua…" opens it | Roblox command bar / UE console | M, Engine | Editor side uses L6; play side uses L4 |
| 3.8 | Project editor scripts: `Editor/*.editor.lua` register commands (id, label, menu, shortcut, `enabled`, `run`) into the L1 registry; `run` returns an edit list applied as one undo step | Defold editor scripts / Unity `MenuItem` | M, Engine | Workspace-trust prompt on first open of a project with editor scripts |
| 3.9 | Editor scripts add inspector drawers and small panels from descriptors and the fixed widget set (no arbitrary UI) | Unity PropertyDrawer / Godot inspector plugins | M | Keeps one control library (R6) |
| 3.10 | Lua API performance pass, driven by 4.6 measurements: allocation-free accessors for hot fields (e.g. Transform position/rotation as plain numbers, alongside today's copied `Engine.Components` snapshots); coarse queries returning lists (overlap box/circle beside the existing `raycastFirst/All`); per-entity callbacks so scripts react instead of polling (contact and zone events exist through `Engine.Events`; audit animation/state and timer events) | Defold / LÖVE practice | M, Engine | Keep the C++ "verbs", Lua "composition" split. Add native systems (e.g. projectiles) when profiling shows mass per-entity Lua |

### Phase 4 — Play-mode iteration

| # | Feature | Borrowed from | Effort | Notes |
|---|---|---|---|---|
| 4.1 | Play-mode tint (token) + "edits are temporary" banner | Unity | S | R9 |
| 4.2 | Step one frame while paused | Unity / UE | S, Engine? | Runtime already pauses |
| 4.3 | Live Inspector: select runtime entities, view values (read-only first) | Godot Remote tree | M, Engine | JNI snapshot of runtime components |
| 4.4 | Keep changes made during Play (per component / per entity) | UE Keep Simulation Changes | M | Needs 4.3 and stable runtime↔authored key mapping |
| 4.5 | Viewport overlays: colliders, zones, physics debug, camera bounds toggles | Godot / UE show flags | S | |
| 4.6 | Lua profiler panel: time per behaviour per frame (update/init/callbacks), call counts, allocations and GC time, sorted worst-first, with click-to-entity and click-to-source; frame-time graph while playing | Unity Profiler / Godot Profiler | M, Engine | Native instrumentation around behaviour calls and the Lua allocator; data through L4. Shows when a feature should move to C++ |

### Phase 5 — 2D content tools

| # | Feature | Borrowed from | Effort | Notes |
|---|---|---|---|---|
| 5.1 | Texture import settings in `.slemeta` (filter Nearest/Linear, pivot) + Inspector for selected asset | Godot Import dock | M, Engine | `Texture::Filter` exists |
| 5.2 | Sprite sheet slicer (grid / by cell size / manual) producing named regions | Unity Sprite Editor | M | Picker for `region` |
| 5.3 | Flipbook animation editor: drag frames, set fps, preview, writes Animator clip | Godot SpriteFrames | M–L | Reuses Animator clip format |
| 5.4 | Named collision layers + matrix in Project Settings; checkboxes instead of bit masks | Unity / Godot | S–M | Names in `.sleproject`; files keep bits |
| 5.5 | Named sorting layers for SpriteRenderer `layer` (plus order-in-layer if the engine needs it) | Unity sorting layers | S | Same pattern as 5.4 |
| 5.5b | Collider handles in the Scene viewport (box/circle now; polygon only if the engine adds polygon colliders) | UE Paper2D Edit Collision / Godot | S–M | Today they only exist in the Entity tab |
| 5.6 | **Engine:** `TileMap` component (layers of tile IDs, chunked storage and batched rendering, merged per-tile colliders) + `TileSet` asset (atlas, tile size, per-tile collision/properties) | Godot TileMapLayer / TileSet | L, Engine | Must fit the existing chunk activation work. Design note first: how it relates to `TiledMapLoader` output |
| 5.7 | TileSet editor: pick atlas, auto-create tiles from grid (reuses 5.2 slicer), draw per-tile collision, paint properties across tiles | Godot TileSet editor | M | Uses 5.1 metadata |
| 5.8 | Tile paint tool (a `ViewportTool`, R7): palette panel, brush, rectangle, bucket fill, erase, eyedropper, layer switch; one undo step per stroke | Unity Tile Palette / Godot | M | Needs 1.1 tools and 1.2 grid |
| 5.9 | Terrain autotiling (corner/side matching) and random/scene tiles | Godot terrains | L | After 5.8 is in use |
| 5.10 | Tiled import converts `.tmj` into native TileMap + TileSet | UE Paper2D Tiled import | M | Keeps existing Tiled content usable |

### Phase 6 — Project, assets and onboarding

| # | Feature | Borrowed from | Effort | Notes |
|---|---|---|---|---|
| 6.0 | Project art style setting (Pixel art / Smooth), chosen at New Project and changeable later: sets default texture filter, pixel snap, pixels-per-unit and grid size | Godot project defaults / Unity 2D templates | S | Lives in `.sleproject` (R10); both styles equally supported |
| 6.1 | Project templates: Empty, Platformer, Top-down (starter assets, sample scripts), bundled offline; later "Save project as template" | All / Unity Hub custom templates | M | Uses starter asset pack |
| 6.2 | Asset grid view with thumbnails; favourites | Unity / UE Content Browser | M | |
| 6.3 | Find references / select dependencies for an asset | UE Reference Viewer / Unity | M | UUID index already exists |
| 6.4 | Input actions map ("jump" → Space, gamepad A) + Project Settings editor | Godot Input Map | L, Engine | Scripts read actions instead of key codes |
| 6.5 | Undo history panel | Godot History / UE Undo History | S | Needs transaction labels (R3) |

### Explicitly not now

Visual scripting, curve/timeline animation editor, Kotlin/JVM editor plugins
(project editor scripting is Lua, L6), layout presets beyond the existing
docking, 3D anything.

---

## 5. Build order: shared base layers, then feature tracks

Decision (R13): build each shared base once, then grow features on it in
slices. The phases in §4 are a catalogue; this section is the order.

### Base layers (each used by several features)

| Layer | Contents | Engine? | Used by |
|---|---|---|---|
| **L1 Editor core** | Command registry + shortcuts (0.1), `Selection` (0.2), labelled/mergeable transactions (R3), status bar (0.4), `ViewportTool` framework + snapping service (1.1, 1.2) | No | Every feature |
| **L2 Property descriptors** | One schema: type, hard range, slider range, step, unit, enum, group, tooltip, `visibleWhen`, default. Native catalog exports it; Lua behaviours declare it; editor renders it with drawer registration (R6) | Yes | 1.5–1.7 Inspector, 3.3 script parameters, 5.1 import settings, 5.4/5.5/6.0/6.4 settings pages, 5.7 per-tile properties |
| **L3 Asset metadata** | `.slemeta` extension: import settings, named regions/slices, thumbnails cache (in `.sle/`), reverse reference index | Yes (loader reads settings/regions) | 1.8 Quick Open, 5.1–5.3, 5.7, 6.2, 6.3 |
| **L4 Runtime bridge** | Engine log sink + Lua errors with `file:line`; runtime entity snapshots; step frame; pause on error; script reload hook; runtime→authored value copy; per-behaviour timing, allocation and GC counters | Yes | 0.3 Console, 3.4 hot reload, 3.10 API pass, 4.2–4.4 play iteration, 4.6 profiler |
| **L5 Project settings registry** | Named sections in `.sleproject`: art style, collision layers, sorting layers, tags, input actions; one Project Settings window built from L2 | Yes (runtime reads names/actions) | 3.5, 5.4, 5.5, 6.0, 6.4 |
| **L6 Editor Lua host** | One sandboxed Lua 5.4 environment in the engine, separate from the game runtime, reached through the existing native bridge. Jobs: (1) read behaviour `properties` tables without running game code; (2) run console code while stopped; (3) load `Editor/*.editor.lua`. Scripts read a snapshot (selection, documents, assets) and return edit lists, which the editor applies through L1 commands and transactions. Never mutates editor state directly. No `os`/`io` unless allowed; trust prompt per project | Yes | 3.3, 3.7, 3.8, 3.9, later automation (batch import, level generators) |
| **L7 Lua script authoring** | Shared external IDE launcher and user preference, script source actions, behavior templates and create-and-attach in Details | No | 3.1, 3.2, Console and Lua Profiler source navigation |

### Feature tracks (each delivered in working slices)

- **A — Authoring speed** (L1, L2): tools/gizmos/snapping → frame/marquee/
  Alt-duplicate → Hierarchy search/rename/hide/lock → property menu and
  copy/paste → multi-object editing → Quick Open/palette.
- **B — Scripting and play** (L2, L4, L6, L7): Console → [L7 script templates
  and Open in IDE](editor-script-workflow-l7.md) → play tint and step → exposed script parameters → Lua console →
  editor-script commands → hot reload → live Inspector → keep play changes →
  Lua profiler → Lua API performance pass → tags/groups → editor-script
  drawers/panels.
- **C — 2D content and tilemaps** (L1, L2, L3): import settings + art style →
  sprite slicer → TileSet asset/editor → engine TileMap → tile paint tool →
  flipbook editor → Tiled import → terrain autotiling.
- **D — Prefabs** (L1, L2; after editor-polish session 6): override markers +
  revert/apply → overrides list → unpack → missing-override display → in-context
  editing → variants.
- **E — Project and onboarding** (L3, L5): layer names → input actions →
  thumbnails/find references → templates (last, so they show everything).

### Sequence

1. **L1** (editor only, plus a small grid bridge). First visible slice: W/E/R tools with snapping. Detailed plan: [editor-core-l1.md](editor-core-l1.md).
2. **L2** with Inspector reset/copy/paste and catalog ranges as first consumers.
3. **L4 part 1** (log sink, Lua errors) → Console.
4. Rest of **Track A**.
5. **L3 + L5**, first consumers: art style (6.0), texture import settings
   (5.1), collision/sorting layer names (5.4, 5.5).
6. **Track C** up to the tile paint tool (tilemaps prioritised per decision),
   with the flipbook editor next.
7. **L6** (needs L1 commands and transactions, and L2 descriptors). First
   consumers: exposed script parameters (3.3), then the Lua console (3.7), then
   editor-script commands (3.8).
8. **L7** (uses L1, L3 and L4). Deliver the shared IDE launcher, source actions,
   built-in create-and-attach, and project template overrides in that order.
   See the [L7 plan](editor-script-workflow-l7.md).
9. **Track B** remainder, then **Track D**, then **Track E**.

Each base layer and each engine-touching item (3.3, 3.4, 3.7, 3.8, 3.10, 4.3,
4.4, 4.6, 5.6, 6.4) gets a short design note before its implementation plan. The L6 note
must settle: the sandbox library set, the editor API snapshot shape, the
edit-list vocabulary (set field, add/remove component, create/delete/reparent
entity, create asset), the trust prompt, and how the console reaches the play
runtime (through L4).

---

## 6. Things not to copy

- **Silent loss of Play-mode edits** (Unity's long-standing complaint): tint and
  keep-changes instead (R9, 4.1, 4.4).
- **Two parallel UI systems / per-panel conventions** (IMGUI vs UI Toolkit, Slate
  customizations everywhere): one control library and one command registry.
- **Settings scattered across many windows**: one home per setting (R10); Project
  Settings and Editor Preferences only.
- **Opaque reimport/serialization churn** (Unity `.meta` diffs, UE binary assets):
  keep JSON sources, stable sidecars, no editor-only state in game files.
- **Feature-flag maze and overwhelming menus** (Unreal): keep menus shallow and
  rely on palette/search for depth; partial features show only their working
  parts (R13), not toggles for things that don't work.
- **Rename redirectors that need "Fix Up"** (Unreal): keep references by UUID so
  rename/move needs no fix-up step.
- **Long blocking reloads** (Unity domain reload): Lua hot reload stays
  incremental, non-blocking and error-contained.
- **Tools that stall half-finished** (Paper2D tile maps are still marked
  Experimental): partial slices are fine pre-release, but each track keeps
  going until its listed slices are done before a new track starts.

---

## 7. References

Official documentation consulted 2026-11-17. Old `docs.unity3d.com/Manual`
links now redirect to `docs.unity.com`.

**Unity**
- Hierarchy (visibility, pickability, reparenting): https://docs.unity.com/en-us/engine/current/manual/unity-editor/editor-windows-views-reference/hierarchy-window/hierarchy
- Grid snapping: https://docs.unity.com/en-us/engine/current/manual/working-with-scenes/scenes-manage-gameobjects/grid-snapping
- Prefab instance overrides: https://docs.unity.com/en-us/engine/current/manual/working-with-gameobjects/prefabs/override/prefab-instance-overrides
- Prefab Mode (isolation / in context): https://docs.unity.com/en-us/engine/current/manual/working-with-gameobjects/prefabs/editing-in-prefab-mode
- Presets: https://docs.unity.com/en-us/engine/current/manual/assets-and-media/presets
- Sprite Editor: https://docs.unity.com/en-us/engine/current/manual/unity2d/sprite/use-editor
- Tilemaps: https://docs.unity.com/en-us/engine/current/manual/unity2d/tilemaps
- Console: https://docs.unity.com/en-us/engine/current/manual/unity-editor/editor-windows-views-reference/console-window/console
- Configurable Enter Play Mode: https://docs.unity3d.com/Manual/configurable-enter-play-mode.html
- Shortcuts Manager: https://docs.unity.com/en-us/engine/current/manual/unity-editor/editor-navigating-managing/shortcuts-manager
- Search: https://docs.unity.com/en-us/engine/current/manual/unity-editor/editor-navigating-managing/editor-searching/search-overview
- Property drawers: https://docs.unity.com/en-us/engine/current/manual/uitoolkits/ui-imgui/extending-the-editor/editor-property-drawers
- Hub templates: https://docs.unity.com/en-us/hub/templates

**Godot 4**
- Unique names / groups: https://docs.godotengine.org/en/stable/tutorials/scripting/scene_unique_nodes.html , https://docs.godotengine.org/en/stable/tutorials/scripting/groups.html
- 2D editor and snapping: https://docs.godotengine.org/en/stable/tutorials/2d/introduction_to_2d.html
- Exports: https://docs.godotengine.org/en/stable/tutorials/scripting/gdscript/gdscript_exports.html
- Instancing / editable children: https://docs.godotengine.org/en/stable/getting_started/step_by_step/instancing.html , https://docs.godotengine.org/en/stable/tutorials/scripting/nodes_and_scene_instances.html
- Import process: https://docs.godotengine.org/en/stable/tutorials/assets_pipeline/import_process.html
- Sprite animation / TileSets: https://docs.godotengine.org/en/stable/tutorials/2d/2d_sprite_animation.html , https://docs.godotengine.org/en/stable/tutorials/2d/using_tilesets.html
- Debugging tools / game embedding: https://docs.godotengine.org/en/stable/tutorials/scripting/debug/overview_of_debugging_tools.html , https://docs.godotengine.org/en/latest/tutorials/editor/game_embedding.html
- Script templates: https://docs.godotengine.org/en/stable/tutorials/scripting/creating_script_templates.html
- Key mapping / command palette: https://docs.godotengine.org/en/stable/tutorials/editor/default_key_mapping.html
- Plugins / inspector plugins / layouts: https://docs.godotengine.org/en/stable/tutorials/plugins/editor/making_plugins.html , https://docs.godotengine.org/en/stable/tutorials/plugins/editor/inspector_plugins.html , https://docs.godotengine.org/en/stable/tutorials/editor/customizing_editor.html
- Project Manager: https://docs.godotengine.org/en/stable/tutorials/editor/project_manager.html

**Unreal Engine 5**
- Outliner: https://dev.epicgames.com/documentation/en-us/unreal-engine/outliner-in-unreal-engine
- Viewport controls / snapping: https://dev.epicgames.com/documentation/en-us/unreal-engine/viewport-controls-in-unreal-engine
- UPROPERTY metadata / edit conditions: https://dev.epicgames.com/documentation/unreal-engine/unreal-engine-uproperties , https://dev.epicgames.com/documentation/unreal-engine/edit-conditions-for-properties-in-the-details-panel-in-unreal-engine
- Level instancing: https://dev.epicgames.com/documentation/en-us/unreal-engine/level-instancing-in-unreal-engine
- Asset redirectors: https://dev.epicgames.com/documentation/en-us/unreal-engine/asset-redirectors-in-unreal-engine
- Paper2D flipbooks / tile maps: https://dev.epicgames.com/documentation/unreal-engine/paper-2d-flipbooks-in-unreal-engine , https://dev.epicgames.com/documentation/en-us/unreal-engine/paper-2d-tile-sets-and-tile-maps-in-unreal-engine
- Play and Simulate (Eject, Keep Simulation Changes): https://dev.epicgames.com/documentation/unreal-engine/playing-and-simulating-in-unreal-engine
- Live Coding: https://dev.epicgames.com/documentation/unreal-engine/using-live-coding-to-recompile-unreal-engine-applications-at-runtime
- Editor Python transactions: https://dev.epicgames.com/documentation/en-us/unreal-engine/scripting-the-unreal-editor-using-python
- Details panel customizations: https://dev.epicgames.com/documentation/en-us/unreal-engine/details-panel-customizations-in-unreal-engine
- New project / templates: https://dev.epicgames.com/documentation/en-us/unreal-engine/creating-a-new-project-in-unreal-engine

---

## 8. Decisions (2026-11-17)

1. **Art style: both, equally.** A per-project art style setting (6.0) picks
   defaults for texture filter, pixel snap, pixels-per-unit and grid; neither
   style is second-class.
2. **Tilemaps: native painting, sooner.** Engine `TileMap` + `TileSet` and an
   in-editor paint tool (5.6–5.9) are in Track C ahead of the flipbook editor.
   Tiled becomes an import path (5.10). The engine design note must cover the
   relationship to `TiledMapLoader` and chunk activation.
3. **Engine and Lua API changes are in scope.** They are planned together with
   the editor work and follow CONTRIBUTING (CTest coverage, engine version bump).
4. **Partial features are fine; build shared bases first** (R13, §5). No
   Experimental menu is needed for now. What is visible must work.
5. **Shortcuts: Unity-style defaults** for scene work (Q/W/E/R, F, Ctrl+D); play is F5/F6/Shift+F5 and Ctrl+P is Quick Open (see editor-core-l1.md), chosen as
   the most widely known; rebindable once the command registry (L1) exists.
6. **Lua runs in a console panel, not inside the palette.** The palette links
   to it ("Run Lua…"). Console runs while stopped are undoable scene edits;
   while playing they act on the game runtime and end at Stop.
7. **Editor-visible script variables are declared as data.** Lua has no
   annotations, so a behaviour returns a `properties` table. The engine reads it
   in a sandbox, so no Lua parser is needed in Kotlin. LuaLS `---@` comments are
   optional, for IDE hints only.
8. **The editor is scripted in Lua (layer L6), Defold-style.** Scripts register
   commands into the L1 registry and return edit lists that the editor applies
   as one undo step. No Kotlin/JVM plugin API for now.
9. **Lua is the gameplay language; C++ provides the building blocks.**
   Heavy, generic and per-element work stays native: physics, queries,
   rendering, animation, tilemaps, pathfinding, timers and serialization. Lua
   combines them into game rules. The profiler (4.6) decides when something
   moves to C++. The Lua API favours coarse calls, events and allocation-free
   hot accessors (3.10).
