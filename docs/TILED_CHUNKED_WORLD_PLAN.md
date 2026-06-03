# SLE Chunked World Plan

Status: In Progress
Created: June 2, 2026

This document defines the next v1-facing feature block after the current JSON scene/bootstrap work:

- `Tiled` map integration as the primary level-authoring workflow
- chunked tilemap loading/runtime management
- tilemap-scoped `A*` pathfinding for entities
- a simple particle system for gameplay feedback

The goal is to give the engine a practical solo-developer workflow for building a small 2D game in `Tiled` without hand-authoring every level in C++.

Important scope rule:

- `Tiled` is a first-class workflow for map-driven games, not a mandatory engine foundation.
- chunking is an optional runtime subsystem for map-backed scenes.
- tilemap `A*` is an optional navigation service for map-backed scenes.
- particles are a core engine feature and must remain usable in non-map games.

Current implementation snapshot (June 2, 2026):

- Done: scene JSON can reference a top-level `tiledMap`
- Done: first `TiledMapLoader` implementation loads finite orthogonal `.tmj` files
- Done: object layers import into existing engine entities for `Zone`, `StaticCollider`, `ScriptEntity`, `SpawnPoint`, and `Marker` first-slice behavior
- Done: integration coverage for the scene-level `tiledMap` path and object-layer import
- Done: imported Tiled object-layer entities now receive initial home-chunk metadata
- Done: Tiled scenes now carry scene-level chunk grid metadata for later runtime hookup
- Done: first chunk-runtime primitive exists for chunk coordinate math, chunk bounds, and camera/focus active-window calculation
- Done: first chunk lifecycle event scaffold exists through `ChunkEvents` + `ChunkActivityTracker`
- Done: first chunk ownership index can group chunk-tagged entities by chunk coordinate
- Not done: richer chunk runtime state transitions, more efficient chunk-owned tile/runtime data, tilemap `A*`, and the integrated authored demo

## 1. Why These Features Belong Together

These features are more valuable as one world pipeline than as isolated systems.

- `Tiled` provides the source-of-truth map and object data.
- chunking provides the runtime streaming and optimization boundary.
- pathfinding uses chunk-derived tile walkability.
- particles provide immediate feedback for movement, impacts, environment, and scripted events inside authored maps.

This is the smallest set that turns the engine from "can run a scene" into "can author and play a level-driven game."

## 2. v1 Scope Definition

For v1, the world pipeline should support:

- one or more finite orthogonal `Tiled` maps exported as JSON (`.tmj`)
- tile layers, object layers, and custom properties
- chunked runtime management of tile content
- chunk load/unload notifications
- entity sleeping/activation decisions driven by chunk residency
- grid-based `A*` pathfinding using tile walkability
- a simple CPU particle emitter system that can be attached to entities and triggered from scripts

The engine must also still support non-map games cleanly:

- plain scene JSON without any `Tiled` map reference
- code-built scenes
- script-driven spawning gameplay loops
- particle-heavy arcade or arena-style games that do not use chunking at all

Out of scope for this v1 block:

- isometric, hex, or infinite `Tiled` maps
- navmeshes
- fully dynamic obstacle avoidance
- threaded streaming
- GPU particles
- editor-time live sync with `Tiled`

## 3. Guiding Decisions

### 3.1 Tiled Is the Authoring Tool

`Tiled` is the primary level editor for map-driven v1 games. Scene JSON still matters, but mainly for:

- startup scene registration
- non-map entities
- global scene configuration
- overlay/UI/bootstrap content

The level itself should increasingly come from `Tiled`.

This does not mean every game must use `Tiled`. Games such as arena shooters, survival loops, or scripted spawn-wave games should still work perfectly with normal scenes and runtime spawning only.

### 3.2 Chunking Is a Runtime Primitive, Not Just a Render Optimization

Chunks should not only reduce tile rendering cost. They should also become the runtime boundary for:

- tile visibility
- entity activation
- pathfinding neighborhood lookups
- gameplay events around world streaming

Chunks apply only when a scene opts into map-backed chunked world behavior. Non-map scenes should not pay for this subsystem.

### 3.3 Chunk State Must Be Observable

Systems and scripts should be able to react when a chunk becomes active or inactive.

This enables:

- AI sleeping
- ambient particle emitters pausing while unloaded
- scripted spawn/despawn rules
- world simulation throttling

### 3.4 Pathfinding Is Tilemap-Scoped First

The first pathfinding version is intentionally narrow:

- grid-based only
- built directly from `Tiled` walkability
- meant for entity navigation on authored maps

No attempt should be made to solve general navigation yet.

This is intentionally not a universal engine navigation system yet. Non-map games can ignore it entirely.

### 3.5 Particles Stay Core And Map-Agnostic

Particles should not depend on `Tiled`, chunking, or map-backed scenes.

They must work for:

- scripted enemy-wave games
- arena shooters
- effects attached to spawned projectiles
- UI or non-world feedback if needed later

If a map-backed scene uses chunk activation, particles should respect it. If a scene does not use chunking, particles should behave like a normal core engine system.

## 4. Feature Breakdown

## 4.1 Stream C1 - Tiled Map Import Foundation

Goal: load a `Tiled` map and create a world from it.

Status: In Progress

### In Scope

- parse `.tmj` JSON
- resolve tilesets and tile images
- import tile layers
- import object layers
- import custom properties
- allow scene JSON to reference one `tiledMap`

### Object Layer Conventions

Use object `type` and properties to declare intent. Initial types:

- `SpawnPoint`
- `StaticCollider`
- `Zone`
- `ScriptEntity`
- `Marker`

Keep this mapping simple and explicit. Avoid a complicated generic component factory in the first pass.

### Done When

- a small authored `Tiled` map renders correctly
- object layers create usable engine entities
- scene JSON can include map + extra entities together

Current slice status (June 2, 2026):

- Done: scene JSON can include `tiledMap` plus normal `entities`
- Done: object layers create usable engine entities in tests
- Done: first `.tmj` loader implementation is wired into `SceneLoader`
- Done: imported object-layer entities receive initial chunk assignment metadata
- Done: chunk-tagged entities can now be grouped into chunk ownership buckets
- Remaining: real tileset/tile-layer validation in a windowed/demo path and the eventual chunk-backed tile runtime

## 4.2 Stream C2 - Chunked Tilemap Runtime

Goal: divide the map into runtime chunks for rendering and simulation control.

### Chunk Model

Each map is divided into fixed-size tile chunks.

Recommended starting size:

- `16x16` tiles per chunk

Each chunk should track:

- chunk coordinate `(cx, cy)`
- world bounds
- resident tile render data
- object/entity membership
- loaded/active state
- optional walkability cache

Current slice status (June 2, 2026):

- Done: reusable chunk grid config, world-to-chunk conversion, chunk bounds, and active-window calculation
- Done: integration coverage for camera/focus-driven active-window behavior
- Done: imported object entities can be tagged with home-chunk metadata
- Done: a reusable ownership index can bucket chunk-tagged entities by home chunk
- Remaining: actual runtime state transitions, activation policy, and richer event publication/consumption
- Done: configurable chunk activation modes now exist at the engine-config level (`Disabled`, `EventsOnly`, `AutoToggle`)
- Done: first reusable chunk activation controller can auto-toggle existing entity component flags from active chunk sets
- Done: per-entity chunk activation overrides now support inherit/events-only/always-active/auto-toggle plus keep-* preservation flags
- Done: map-backed scenes now preserve chunk grid metadata as part of the loaded scene state
- Done: runtime wiring now feeds live camera-driven chunk windows into the controller for map-backed scenes
- Done: imported tile-layer entities now participate in chunk activation through render visibility toggling
- Remaining: richer loaded/sleeping state transitions and more efficient chunk-owned tile/runtime behavior

### Why 16x16 First

It is small enough for selective activation and large enough to avoid excessive bookkeeping. It is also easy to reason about in tests and debug overlays.

### Chunk Responsibilities

- hold or reference the tiles that belong to that region
- hold or reference entities spawned from that region
- expose activation/deactivation boundaries
- provide cheap lookup for "which chunk contains this world position?"

### Active Chunk Window

The runtime should support a simple active window around one or more focus targets.

For v1:

- one focus target is enough
- load radius and active radius can initially be the same

Recommended first version:

- active chunk center = camera location converted to chunk coordinates
- active set = center chunk plus 1 chunk in each direction

Important engine rule:

- chunk activation is not tied to a special "player entity"
- the engine must not assume a player object exists
- the camera is the default focus source for chunk activation

This supports:

- games with no explicit player avatar
- RTS or multi-unit control schemes
- scripted cameras
- arena or survival games where gameplay is camera-centered rather than actor-centered

### Chunk Lifecycle

Each chunk should move through a small state model:

1. `Unloaded`
2. `Loaded`
3. `Active`
4. `Sleeping`

For v1, `Loaded` and `Sleeping` can be represented cheaply if full asset streaming is too much. The important behavior is that inactive chunks stop expensive processing.

### Done When

- only nearby chunks submit tile render data
- chunk transitions are deterministic
- a debug view can show active chunk bounds
- camera movement alone is enough to drive chunk activation correctly

## 4.3 Stream C3 - Chunk Events and Entity Activation

Goal: allow gameplay systems and entities to react to chunk state changes.

### Event Types

Add native events similar to other engine events:

- `ChunkLoadedEvent`
- `ChunkActivatedEvent`
- `ChunkDeactivatedEvent`
- `ChunkUnloadedEvent`

Each event should at minimum include:

- map identifier
- chunk coordinate
- world bounds

### Entity Participation

Entities should be able to opt into chunk-based activation rules.

Recommended first component:

- `ChunkResidentComponent` or `ChunkActivationComponent`

Suggested fields:

- `sleepWhenInactive = true`
- `keepRenderingWhenInactive = false`
- `keepPhysicsWhenInactive = false`
- `keepScriptsWhenInactive = false`

This keeps the logic explicit and prevents hidden behavior changes for every entity in the project.

### Runtime Behavior

When a chunk becomes inactive:

- mark participating entities as sleeping/inactive
- skip script updates for them
- optionally skip physics sync/update participation
- optionally skip animation/particle updates

When a chunk becomes active again:

- reactivate those entities
- emit a wake event if useful

### Important Constraint

Do not destroy and recreate entities on every boundary crossing in the first version.

Prefer:

- persistent entities
- activation flags
- system-side skipping

This is simpler and much safer for v1.

### Done When

- an entity in an inactive chunk stops expensive updates
- entities reactivate cleanly when the player returns
- chunk transitions do not break scene ownership or event subscriptions

Current slice status (June 2, 2026):

- Done: chunk activation/deactivation event types are defined
- Done: a reusable tracker can diff active windows and publish activation/deactivation through the existing event bus
- Done: integration coverage proves event order/content for camera/focus-driven chunk transitions
- Done: runtime/system integration now consumes ownership + chunk windows inside live scenes for activation toggling
- Remaining: richer loaded/sleeping state transitions and broader chunk participation beyond the current entity-and-visibility activation path

## 4.4 Stream C4 - Tilemap A* Pathfinding

Goal: provide practical pathfinding for entities moving through `Tiled` levels.

### First-Version Rules

- grid-based `A*` only
- orthogonal movement first
- optional diagonal movement later if needed
- path graph derived from chunk/tile walkability

### Walkability Source

Walkability should come from one of these, in this order:

1. explicit tile property like `walkable=false`
2. collision/object layer declarations
3. default walkable if no blocking marker exists

This keeps map authors in control while remaining easy to inspect in `Tiled`.

### API Shape

Keep the first API narrow and explicit.

Native side:

- `findPath(startWorldPos, endWorldPos)`
- `findPath(startTile, endTile)`

Script side:

- `Engine.findTilePath(entity, targetX, targetY)`

Return:

- ordered tile/world points
- empty path if unreachable

### Chunk Interaction

Pathfinding does not need full-world streaming complexity on day one.

First version may:

- use the whole currently loaded map walkability grid
- optionally query chunk caches for neighborhood data

But the design should leave room for:

- per-chunk cached walkability
- dirty chunk path cache invalidation later

### Done When

- an NPC can navigate around blocked tiles in an authored test map
- unreachable destinations fail cleanly
- test coverage proves deterministic paths on a known grid

## 4.5 Stream D - Simple Particle System

Goal: add a lightweight feedback system that works with authored maps and entities.

### Scope

- CPU particles only
- simple quad rendering
- burst and looping emitters
- color/size over lifetime
- spawn from entity transform
- script-triggerable

### World Pipeline Relevance

Particles matter here because they should also respect chunk activation.

For v1:

- inactive chunk emitters stop simulating
- active chunk emitters resume
- one-shot bursts can still be spawned by active gameplay events

### Tiled Tie-In

Allow `Tiled` object layers or scene JSON to create entities with particle emitters.

But keep `Tiled` optional:

- particles must be spawnable in plain scene JSON
- particles must be usable on entities created at runtime from Lua or native code
- no emitter feature should require map metadata to function

Initial examples:

- torch flame
- dust puff
- impact spark
- ambient motes near a marker

### Done When

- particles are visible in the sandbox or map demo
- emitter behavior can be authored from data plus scripts
- inactive chunks do not keep simulating hidden ambient emitters

## 5. Recommended Build Order

Build this feature block in the following order:

1. `Tiled` import foundation
2. chunked tilemap runtime
3. chunk events and entity activation
4. tilemap walkability extraction
5. tilemap `A*`
6. simple particle system
7. one integrated authored demo level

Why this order:

- chunking depends on map structure existing first
- chunk activation depends on chunk ownership existing
- pathfinding depends on map walkability existing
- particles are more useful once the authored world exists

## 6. Suggested File Targets

### Tiled + Chunking

- `EngineModules/Systems/include/sle/engine/TiledMapLoader.hpp`
- `EngineModules/Systems/src/TiledMapLoader.cpp`
- `EngineModules/Systems/include/sle/engine/ChunkManager.hpp`
- `EngineModules/Systems/src/ChunkManager.cpp`
- `EngineModules/Events/include/sle/events/ChunkEvents.hpp`
- `EngineModules/Scene/include/sle/scene/components/ChunkActivationComponent.hpp`

### Pathfinding

- `EngineModules/Systems/include/sle/engine/TilePathfinding.hpp`
- `EngineModules/Systems/src/TilePathfinding.cpp`
- `EngineModules/Scripting/src/ScriptApiImpl.cpp`
- `EngineModules/Scripting/src/LuaBindings.cpp`

### Particles

- `EngineModules/Scene/include/sle/scene/components/ParticleEmitterComponent.hpp`
- `EngineModules/Systems/include/sle/engine/ParticleSystem.hpp`
- `EngineModules/Systems/src/ParticleSystem.cpp`

### Data / Tests

- `assets/maps/`
- `tests/data/maps/`
- `tests/integration/tiled/`
- `tests/integration/pathfinding/`
- `tests/integration/particles/`

## 7. Test Plan

### Tiled

- load a `3x3` test map with one tileset and one object layer
- verify tile entity count
- verify object type mapping
- verify tileset UV selection is correct

### Chunking

- verify world position to chunk coordinate mapping
- verify active chunk window updates deterministically as the player moves
- verify inactive chunks stop tile submission or simulation participation

### Chunk Events

- verify load/activate/deactivate event order
- verify subscribed gameplay objects receive the expected chunk events

### Pathfinding

- verify `A*` returns the expected route on a deterministic blocked grid
- verify unreachable target returns empty result
- verify entity-followed path reaches the expected tile

### Particles

- verify emitter spawns and retires particles within expected bounds
- verify inactive chunk emitters stop updating
- verify burst call produces deterministic count changes

## 8. Integrated v1 Demo Definition

This feature block is not done until one authored level proves the pipeline end-to-end.

The demo should include:

- one `Tiled` map loaded from data
- chunk debug overlay or logging
- one sleeping/waking AI or scripted entity based on chunk activation
- one pathfinding NPC moving around blocked tiles
- one particle effect triggered by movement or interaction

If that demo works, the engine is much closer to "small game ready" instead of merely "engine systems available."

Separately, the engine should still be able to prove a non-map gameplay loop:

- one plain scene without `Tiled`
- runtime entity spawning
- particles used for hits/explosions
- no dependency on chunk activation or tilemap systems

That keeps the engine honest as a general 2D game engine rather than accidentally turning it into a tilemap-only engine.

## 9. Open Questions

1. Should chunk size be fixed globally for v1, or per-map configurable in scene/map metadata?
Recommended: fixed global default with optional metadata override later.

2. Should tile layers become individual entities, or should chunk render data bypass per-tile entity creation for performance?
Recommended: do not create one ECS entity per visual tile in the chunked version. Prefer chunk-owned render data.

3. Should chunk activation affect physics bodies immediately, or should physics continue for nearby sleeping chunks?
Recommended: keep v1 conservative and disable expensive logic first, then only reduce physics scope where clearly safe.

4. Should pathfinding see only static map walkability, or also dynamic entity blockers?
Recommended: static map walkability only for v1.

5. Should particles be authorable directly from `Tiled` object properties in v1, or only via scene JSON/script-created entities?
Recommended: allow both eventually, but scene JSON plus script triggers is enough for the first pass.
