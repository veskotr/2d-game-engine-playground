---
Title: SLE v1 Final Stage — Implementation Plan
Status: Awaiting Review
Created: May 2026
---

# SLE v1 Final Stage — Implementation Plan

This document covers the four remaining work streams between the current state (Phases 0–5 complete)
and a declared v1 release. It is written for review before any code is written.

The four streams are independent enough to be tackled in order but each stream is a dependency
for the one after it:

```
Stream A: JSON Scene Serialization
    ↓
Stream B: Standalone Engine Entry Point
    ↓
Stream C: Tiled Map Integration          (parallel to B after A lands)
    ↓
Stream D: Particle System                (standalone, can start after A)
```

---

## Stream A — JSON Scene & Entity Serialization

### Problem

Every scene is defined in C++. Adding or changing a scene means recompiling the engine.
No data-driven workflow is possible until entities and their components can be expressed
as plain data files.

### Goal

A scene can be fully described in a JSON file. The engine reads the file, creates entities,
and attaches the correct components with correct field values. No C++ changes are needed
to add new content.

### Scope

**In scope for v1:**
- `from_json` deserializers for every concrete component (see table below)
- A `SceneLoader` that reads a scene JSON and populates a `sle::entity::Scene`
- Integration with `SceneManager::registerScene` so a JSON scene is registered the same
  way as a code-built scene
- Error handling: missing keys log a warning and use the component default; unknown
  component type names log a warning and are skipped; malformed JSON is a fatal error

**Out of scope for v1:**
- `to_json` / save-to-disk (round-trip serialization is post-v1)
- Hot-reload while the engine is running
- Scene graph nesting / prefab inheritance beyond flat entity lists

### JSON File Format (proposed)

```json
{
  "scene": "npc_zone_demo",
  "entities": [
    {
      "name": "Player",
      "components": {
        "Transform": {
          "position": [0, 0],
          "rotation": 0,
          "scale": [1, 1]
        },
        "SpriteRenderer": {
          "texture": "assets/textures/player.png",
          "color": [1, 1, 1, 1],
          "layer": 1
        },
        "RigidBody": {
          "bodyType": "Dynamic",
          "mass": 1.0,
          "fixedRotation": true,
          "gravityScale": 0
        },
        "BoxCollider": {
          "size": [32, 32],
          "offset": [0, 0]
        },
        "Script": {
          "asset": "assets/scripts/player_move.lua"
        },
        "Audio": {
          "asset": "assets/sounds/gravel.ogg",
          "volume": 0.6
        },
        "StateMachine": {
          "definition": "assets/statemachines/player.json"
        },
        "Animator": {
          "clip": "assets/animations/player_walk.json"
        }
      }
    }
  ]
}
```

Design notes:
- Component keys match short, human-readable names (`Transform`, `SpriteRenderer`, `RigidBody`,
  `BoxCollider`, `CircleCollider`, `BoxZone`, `CircleZone`, `Script`, `Audio`, `UI`,
  `StateMachine`, `Animator`)
- All paths are relative to the project root (resolved the same way `resolveAssetPath` works)
- `SpriteRenderer.texture` is a path string; the loader calls `Resources::create<Texture>()`
  if the texture is not already cached
- `color` is always a 4-element float array `[r, g, b, a]`
- `position` and `scale` are 2-element float arrays `[x, y]`
- `bodyType` accepts the strings `"Static"`, `"Dynamic"`, `"Kinematic"`
- `UIComponent` fields: `layout`, `font`, `behavior`, `spaceMode` (`"Screen"` or `"World"`),
  `layer`, `bindingScope`

### Component Deserializer Table

| Component             | Key in JSON     | Notable fields needing special handling          |
|-----------------------|-----------------|--------------------------------------------------|
| TransformComponent    | `Transform`     | position→vec2, scale→vec2                        |
| SpriteRenderer        | `SpriteRenderer`| texture path → `Resources::create<Texture>()`   |
| RigidBodyComponent    | `RigidBody`     | bodyType string → enum                           |
| BoxColliderComponent  | `BoxCollider`   | offset→vec2, size→vec2                           |
| CircleColliderComponent| `CircleCollider`| offset→vec2, radius→float                       |
| BoxZoneComponent      | `BoxZone`       | offset→vec2, size→vec2                           |
| CircleZoneComponent   | `CircleZone`    | offset→vec2, radius→float                       |
| ScriptComponent       | `Script`        | asset path                                       |
| AudioComponent        | `Audio`         | asset path (resolved to absolute), volume, loop  |
| UIComponent           | `UI`            | layout/font/behavior paths, spaceMode enum       |
| StateMachineComponent | `StateMachine`  | definition asset path                            |
| AnimatorComponent     | `Animator`      | clip asset path, stateClipMap (optional object)  |

### Where the Code Lives

The `SceneLoader` should live in `EngineModules/Systems/` because Systems already depends on
all other modules and can see every component type. This avoids creating a new module and keeps
the dependency graph clean.

Proposed files:
```
EngineModules/Systems/include/sle/engine/SceneLoader.hpp
EngineModules/Systems/src/SceneLoader.cpp
```

The `from_json` helpers for each component can be co-located in the component headers as
`static fromJson(const nlohmann::json&)` factory functions following the pattern already
established in `AnimationClipResource.hpp` and `StateMachineDefinitionResource.hpp`.

### Integration Points

`Runtime` gains a new method:
```cpp
bool registerSceneFromFile(const std::string& sceneName, const std::string& jsonPath);
```
This calls `SceneLoader::load(jsonPath, scene)` and registers the result exactly like a
code-built scene builder.

### Done When

- Every component in the table above can be round-trip created from JSON with correct field values
- A test scene JSON file produces the correct entity count and component field values in an
  integration test
- The sandbox demo (npc_zone_demo) can be defined entirely in JSON without any code changes
  to entity setup
- Unknown component keys and missing optional fields do not crash; they log and continue

---

## Stream B — Standalone Engine Entry Point

### Problem

There is no way to run a project without recompiling. The only executable is `example_sandbox`,
which is hard-coded in C++ for a specific game scenario. A developer who wants to ship a game
must fork the sandbox and edit its `main.cpp`.

### Goal

A standalone `engine_app` executable that reads an `engine.json` config file, boots the engine,
and loads all scenes from JSON definitions. No C++ required to run a complete game.

### Config File Format (proposed)

`engine.json` in the project root or next to the executable:

```json
{
  "window": {
    "title": "My Game",
    "width": 480,
    "height": 270,
    "vsync": true,
    "mode": "Windowed"
  },
  "startScene": "main",
  "scenes": [
    { "name": "main",        "file": "assets/scenes/main.json" },
    { "name": "level_01",    "file": "assets/scenes/level_01.json" },
    { "name": "ui_test",     "file": "assets/scenes/ui_test.json" }
  ]
}
```

### Entry Point Behaviour

1. Locate `engine.json` using the same `resolveAssetPath` walk (CWD → parent directories)
2. Parse window config → populate `EngineConfig`
3. Call `Runtime::init()`
4. For each entry in `scenes`, call `runtime.registerSceneFromFile(name, file)` (Stream A)
5. Call `runtime.loadScene(startScene)` then `runtime.run()`

The entry point is approximately 50 lines of C++ — thin glue over what already exists.

### New CMake Target

```cmake
add_executable(engine_app main.cpp)
target_link_libraries(engine_app PRIVATE sle::systems)
```

Location: `engine_app/main.cpp` at project root (not under `examples/`).

The sandbox example continues to exist for tests and as a reference.

### Done When

- `engine_app.exe` runs with an `engine.json` and a scene JSON alongside it without any
  sandbox-specific code involved
- A "hello world" game (single entity with a sprite and a Lua script that logs a message)
  works from pure JSON + Lua with zero C++ written by the game developer

---

## Stream C — Tiled Map Integration

### Overview

Tiled (mapeditor.org) is an open-source tile map editor that exports maps in a well-documented
JSON format. For v1, Tiled becomes the level editor. The engine reads Tiled JSON export files
and creates tile and object entities automatically.

### Tiled JSON Format (brief summary)

A Tiled map file contains:
- `width` / `height` — map dimensions in tiles
- `tilewidth` / `tileheight` — tile size in pixels
- `tilesets` — array of tileset descriptors (image path, tile size, first GID)
- `layers` — array of layer descriptors:
  - `type: "tilelayer"` — flat grid of tile GIDs
  - `type: "objectgroup"` — list of objects with position, size, optional type, and properties
  - `type: "imagelayer"` — background image (simple to support, optional for v1)

### What the Engine Does with a Tiled Map

**Tile layers**: Each non-empty tile cell becomes an entity with:
- `TransformComponent` at the correct world position (tileX * tileWidth, tileY * tileHeight)
- `SpriteRenderer` with a `TextureRegion` sliced from the correct tileset image

For performance, tiles in a single layer are batched — they share the same `layer` value in
`SpriteRenderer` so the existing GPU batch rendering handles them efficiently.

**Object layers**: Each object becomes an entity. The `type` property on the Tiled object maps
to the component set to attach:
- `type: "StaticCollider"` → Transform + BoxCollider (Static RigidBody)
- `type: "Zone"` → Transform + BoxZoneComponent
- `type: "SpawnPoint"` → plain entity, no components (used by Lua to query spawn positions)
- `type: "Script"` → Transform + ScriptComponent (script path from custom property `script`)
- Custom types: the object's Tiled custom properties are passed to `ScriptComponent` as
  key-value strings that Lua can read via `Engine.getEntityProperty(entity, key)`

### Integration with Scene JSON

A scene JSON can reference a Tiled map as a special top-level entry:

```json
{
  "scene": "level_01",
  "tiledMap": "assets/maps/level_01.tmj",
  "entities": [
    {
      "name": "Player",
      "components": { ... }
    }
  ]
}
```

The `SceneLoader` processes `tiledMap` before `entities`. Entities defined in `entities` are
created on top of the tile world, allowing the player, NPCs, and other non-tile things to
coexist with the map.

### New Module: `EngineModules/Systems/src/TiledMapLoader.cpp`

The Tiled loader is not a separate module — it lives in Systems alongside SceneLoader, since
it needs to create entities and attach components. It exposes one function:

```cpp
// Returns false and logs on error. On success, adds tile and object entities to scene.
bool loadTiledMap(const std::string& tmjPath,
                  sle::entity::Scene& scene,
                  sle::core::Resources& resources);
```

### Tileset Handling

Tilesets reference image files by path relative to the map file. The loader resolves these
paths, loads the texture via `Resources::create<Texture>()`, and computes UV rectangles using
`TextureRegion::fromPixels()`.

Embedded tilesets (stored inside the `.tmj` file) are supported. External tileset files (`.tsj`)
are also supported for v1 since they use the same JSON structure.

### What Is NOT Supported for v1

- Rotated / flipped tiles (Tiled encodes these in high bits of the GID — skip for v1)
- Infinite maps (chunked storage — out of scope)
- Tiled `template` object types
- Multiple tilesets with overlapping GID ranges that use different tile sizes
- Hex maps and isometric maps

### Done When

- A Tiled JSON map with at least one tile layer and one object layer loads without error
- Tile entities render at correct world positions with correct sprite regions
- Static collider objects create working Box2D fixtures
- Zone objects fire enter/exit events when the player walks into them
- Integration test: load a small test map (3×3 tiles, 1 zone object), verify entity count
  and component presence

---

## Stream D — Particle System

### Goal

A lightweight CPU-side particle emitter component that drives visual effects (dust,
sparks, smoke, rain) without a new rendering backend. Particles are rendered as small
quads via the existing `RenderSystem` command queue.

### Design Principles

- No GPU compute. CPU simulation only. Up to ~1 000 live particles per emitter is the target
  budget for v1.
- Particles are not entities in the ECS — they are owned by the emitter component and
  live in a fixed-size pool inside it.
- The emitter component is pure data; all simulation runs in a new `ParticleSystem`.
- Lua can start, stop, and configure emitters via the existing script API extension pattern.

### ParticleEmitterComponent Fields

```cpp
struct ParticleEmitterComponent {
    // Emission
    bool          emitting     = true;
    float         emitRate     = 10.0f;   // particles per second
    uint32_t      maxParticles = 256;

    // Particle lifetime
    float         lifetimeMin  = 0.5f;    // seconds
    float         lifetimeMax  = 1.0f;

    // Initial velocity
    glm::vec2     direction     = {0.0f, -1.0f};  // normalized base direction
    float         spread        = 45.0f;           // degrees either side
    float         speedMin      = 50.0f;
    float         speedMax      = 150.0f;

    // Appearance
    glm::vec4     colorStart    = {1, 1, 1, 1};
    glm::vec4     colorEnd      = {1, 1, 1, 0};   // fades to transparent
    float         sizeStart     = 4.0f;            // pixels
    float         sizeEnd       = 0.0f;
    int           renderLayer   = 10;

    // Optional texture (nullptr = white quad)
    std::string   textureAsset;

    // Runtime state (managed by ParticleSystem — do not touch)
    struct Particle {
        glm::vec2 position;
        glm::vec2 velocity;
        float     age;
        float     lifetime;
    };
    std::vector<Particle> pool;
    float                 emitAccumulator = 0.0f;
};
```

### Simulation Loop (ParticleSystem::update)

For each entity with a `ParticleEmitterComponent`:

1. Age all live particles by `dt`. Remove expired ones (swap-with-last for O(1) removal).
2. If `emitting`, accumulate `emitRate * dt`. Spawn floor(accumulator) new particles
   each frame; subtract the integer part from the accumulator.
3. For each new particle: pick random age=0, random direction within spread, random speed
   within range, set position to entity world position.
4. Update each live particle: position += velocity * dt.

### Rendering

`ParticleSystem` submits one `RenderCommand` per live particle into the render queue (same as
`RenderSystem` does for sprites). Because particles share a texture, the existing batcher
naturally groups them. No new render path needed.

### Lua API

```lua
Engine.setParticleEmitting(entity, true/false)
Engine.setParticleEmitRate(entity, ratePerSecond)
Engine.setParticleDirection(entity, dx, dy, spreadDegrees)
Engine.setParticleColors(entity, r1,g1,b1,a1, r2,g2,b2,a2)
Engine.burstParticles(entity, count)   -- one-shot burst regardless of emitting flag
```

### JSON Deserializer

```json
"Particles": {
  "emitting": true,
  "emitRate": 20,
  "maxParticles": 128,
  "lifetimeMin": 0.4,
  "lifetimeMax": 0.8,
  "direction": [0, -1],
  "spread": 30,
  "speedMin": 60,
  "speedMax": 120,
  "colorStart": [1, 0.8, 0.3, 1],
  "colorEnd":   [1, 0.2, 0.0, 0],
  "sizeStart": 6,
  "sizeEnd": 0,
  "renderLayer": 5,
  "texture": "assets/textures/particle_spark.png"
}
```

### Where the Code Lives

New module following the existing pattern:

```
EngineModules/Systems/include/sle/engine/ParticleSystem.hpp
EngineModules/Systems/src/ParticleSystem.cpp
```

`ParticleEmitterComponent` lives in `Scene/include/sle/scene/components/`.

The `ParticleSystem` update call is inserted in `Runtime`'s frame loop after `AnimationSystem`
and before `RenderSystem`.

### Done When

- An entity with `ParticleEmitterComponent` emits visible quads that fade and die correctly
- `Engine.burstParticles` works from a Lua script
- A sandbox demo shows at least one emitter (e.g., dust on player footsteps or a torch flame)
- Integration test: run emitter for N frames, assert live particle count is within expected range
  and no expired particles remain in the pool

---

## Implementation Order

The natural sequence, respecting dependencies and minimizing rework:

| Order | Stream | Why this position |
|-------|--------|-------------------|
| 1st   | A — JSON Serialization | Everything else depends on being able to load data |
| 2nd   | B — Engine Entry Point | Thin layer; can be done as soon as A lands |
| 3rd   | C — Tiled Map | Builds on SceneLoader from A; can use engine_app from B to test |
| 4th   | D — Particle System | Self-contained; last because it needs JSON support (A) and is pure polish |

Streams C and D have no dependency on each other. If there are two developers, they can run
in parallel after A and B are done.

---

## Component Additions Summary

| New Component            | Module       | Needed for |
|--------------------------|--------------|------------|
| `ParticleEmitterComponent` | Scene      | Stream D   |

No other new components. Tiled tile entities use existing `Transform` + `SpriteRenderer`
(and optionally `RigidBody` + `BoxCollider` for collision objects).

---

## New Files Summary

### Stream A
```
EngineModules/Systems/include/sle/engine/SceneLoader.hpp
EngineModules/Systems/src/SceneLoader.cpp
assets/scenes/npc_zone_demo.json          (ported from main.cpp)
tests/integration/serialization/integration_scene_loader_roundtrip.cpp
tests/data/scenes/test_basic_scene.json
```

### Stream B
```
engine_app/main.cpp
engine_app/CMakeLists.txt
engine.json                               (project-root config)
```

### Stream C
```
EngineModules/Systems/include/sle/engine/TiledMapLoader.hpp
EngineModules/Systems/src/TiledMapLoader.cpp
assets/maps/                              (Tiled .tmj files here)
tests/integration/tiled/integration_tiled_map_load.cpp
tests/data/maps/test_map_3x3.tmj
```

### Stream D
```
EngineModules/Scene/include/sle/scene/components/ParticleEmitterComponent.hpp
EngineModules/Systems/include/sle/engine/ParticleSystem.hpp
EngineModules/Systems/src/ParticleSystem.cpp
tests/integration/particles/integration_particle_lifecycle.cpp
```

---

## Open Questions for Review

1. **Scene JSON path for `SpriteRenderer.texture`**: should the loader auto-create the texture
   resource immediately, or defer until `RenderSystem` first encounters it? Immediate loading
   is simpler and gives clearer error messages. Deferred loading lets scenes load faster on
   large projects. Recommend **immediate** for v1.

2. **Tiled coordinate system**: Tiled uses Y-down screen coordinates. The engine uses Y-down
   for world coordinates too (Box2D is Y-up internally but the physics wrapper converts).
   Confirm whether we need a Y-flip on tile positions or if they already align.

3. **`engine.json` search strategy**: should the engine look for `engine.json` only next to the
   executable, or use the same parent-directory walk as `resolveAssetPath`? The walk is more
   developer-friendly (works from any build output directory) but is slightly less predictable.

4. **Particle texture**: should `ParticleEmitterComponent` support one texture per emitter, or
   should v1 be white-quad only (simpler, no asset dependency, still looks fine with color
   gradients)? Recommend **optional texture** as specified — it's a one-liner in the loader.

5. **`engine_app` target location**: `engine_app/` at project root vs `examples/engine_app/`.
   Keeping it at root emphasises it is the *real* entry point, not an example. Review
   preference?
