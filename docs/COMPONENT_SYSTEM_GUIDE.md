# Component System & Extensibility Guide

## Overview

This document outlines:
- Component design patterns
- How to add new components
- Component serialization patterns
- Future-proofing for physics, audio, animation, etc.

---

## 1. Component Design Principles

### 1.1 Core Component Principles

- **Data-centric**: Components hold data; methods are only allowed to enforce invariants (e.g. dirty-flag setters on `TransformComponent`). No game logic in components.
- **Serializable**: Every component must serialize to/from Lua tables and JSON
- **Independent**: Components don't know about each other
- **Optional**: Entities can have any combination of components
- **Default constructible**: Can be created with default values
- **POD-like**: Prefer simple types (use glm::, std::string, uint32_t, bool, float)

### 1.2 Component Checklist

When designing a new component:

```
[ ] Define struct in namespace sle::components
[ ] Use private fields + public getters/setters only when side-effects (dirty flags, invariants) are needed; otherwise prefer public fields
[ ] Add to namespace sle::components
[ ] Implement Lua serialization (→ LuaTable)
[ ] Implement Lua deserialization (LuaTable →)
[ ] Implement JSON serialization (→ nlohmann::json)
[ ] Implement JSON deserialization (nlohmann::json →)
[ ] Register in ComponentRegistry
[ ] Add to Scene module's public API
```

---

## 2. Existing Components

### 2.1 Transform Component

```cpp
// Scene/include/sle/scene/components/Transform.hpp
namespace sle::components {

struct TransformComponent
{
public:
    const glm::vec2& getPosition() const { return position; }
    float getRotation() const             { return rotation; }
    const glm::vec2& getScale() const    { return scale; }

    void setPosition(const glm::vec2& value) { position = value; dirty = true; }
    void setRotation(float value)            { rotation = value; dirty = true; }
    void setScale(const glm::vec2& value)    { scale    = value; dirty = true; }

    bool isDirty() const  { return dirty; }
    void markDirty()      { dirty = true; }
    void clearDirty()     { dirty = false; }

    sle::entity::Entity getParent() const               { return parent; }
    void setParent(sle::entity::Entity value)            { parent = value; dirty = true; }

private:
    glm::vec2 position{0.0f};
    float rotation = 0.0f;
    glm::vec2 scale{1.0f, 1.0f};
    bool dirty = true;
    sle::entity::Entity parent{};
};

} // namespace sle::components
```

**Serialization to Lua:**
```
{
    x = 100.5,
    y = 200.3,
    rotation = 0.0,
    scaleX = 1.0,
    scaleY = 1.0
}
```

**JSON:**
```json
{
    "position": [100.5, 200.3],
    "rotation": 0.0,
    "scale": [1.0, 1.0]
}
```

### 2.2 SpriteRenderer Component

```cpp
// Scene/include/sle/scene/components/SpriteRenderer.hpp
#include <sle/renderer/TextureRegion.hpp>

namespace sle::components {

struct SpriteRenderer
{
    glm::vec4 color{1.0f, 1.0f, 1.0f, 1.0f}; // RGBA tint; white = no tint
    renderer::TextureRegion region;             // Source region (atlas slice or full texture)
    int layer = 0;                              // Render layer; lower = further back
};

} // namespace sle::components
```

**Note**: `region` holds a `TextureRegion` (texture pointer + UV rect). Use `TextureRegion::fromPixels()` for atlas slices or animation frames. Region.texture == nullptr renders as untextured quad.

### 2.3 ScriptComponent

```cpp
// Scene/include/sle/scene/components/ScriptComponent.hpp
namespace sle::components {

struct ScriptComponent
{
    std::string scriptAsset; // Path to .lua file
    bool enabled = true;     // Enable/disable script execution
};

} // namespace sle::components
```

**Note**: Lua registry refs and lifecycle state are managed entirely by `ScriptEngine`; they are not stored in the component.

---

## 3. Adding New Components

### Example: HealthComponent

#### Step 1: Define Component

```cpp
// Scene/include/sle/scene/components/HealthComponent.hpp
#pragma once

namespace sle::components {

struct HealthComponent
{
    float maxHealth = 100.0f;
    float currentHealth = 100.0f;
    bool invulnerable = false;
    
    // Helper (optional)
    float getHealthPercent() const
    {
        return currentHealth / maxHealth;
    }
};

} // namespace sle::components
```

#### Step 2: Register Serialization in ComponentRegistry

```cpp
// Engine/src/ScriptApiImpl.cpp

LuaTable ScriptApiImpl::getComponent(EntityRef entity, const std::string& componentName)
{
    LuaTable result;
    sle::entity::Entity e(entity.id, nullptr);
    auto& registry = engine->scene.getRegistry();
    
    if (componentName == "HealthComponent")
    {
        auto* health = registry.getComponent<sle::components::HealthComponent>(e);
        if (!health) return result;
        
        result.data["maxHealth"] = std::to_string(health->maxHealth);
        result.data["currentHealth"] = std::to_string(health->currentHealth);
        result.data["invulnerable"] = health->invulnerable ? "true" : "false";
    }
    
    return result;
}

void ScriptApiImpl::setComponent(EntityRef entity, const std::string& componentName, 
                               const LuaTable& data)
{
    sle::entity::Entity e(entity.id, nullptr);
    auto& registry = engine->scene.getRegistry();
    
    if (componentName == "HealthComponent")
    {
        auto* health = registry.getComponent<sle::components::HealthComponent>(e);
        if (!health) return;
        
        if (data.data.count("maxHealth")) 
            health->maxHealth = std::stof(data.data.at("maxHealth"));
        if (data.data.count("currentHealth")) 
            health->currentHealth = std::stof(data.data.at("currentHealth"));
        if (data.data.count("invulnerable")) 
            health->invulnerable = data.data.at("invulnerable") == "true";
    }
}
```

#### Step 3: Add Lua Binding Helper

```cpp
// Optional: add convenience function to EngineAPI
class ScriptApi
{
    // ...
    virtual void damageEntity(EntityRef entity, float amount) = 0;
};

// In ScriptApiImpl
EntityRef ScriptApiImpl::damageEntity(EntityRef entity, float amount)
{
    LuaTable current = getComponent(entity, "HealthComponent");
    float hp = getLuaTableFloat(current, "currentHealth", 100.0f);
    hp = std::max(0.0f, hp - amount);
    
    LuaTable updated = current;
    updated.data["currentHealth"] = std::to_string(hp);
    setComponent(entity, "HealthComponent", updated);
}

// Bind to Lua
lua_pushvalue(L, apiIndex);
lua_pushcclosure(L, [](lua_State* L) -> int {
    ScriptApi* api = (ScriptApi*)lua_touserdata(L, lua_upvalueindex(1));
    EntityRef entity{(uint32_t)luaL_checkinteger(L, 1)};
    float amount = luaL_checknumber(L, 2);
    api->damageEntity(entity, amount);
    return 0;
}, 1);
lua_setfield(L, engineTable, "damageEntity");
```

#### Step 4: Use in Lua Script

```lua
-- assets/scripts/enemy.lua

function init(entity)
    local health = Engine.getComponent(entity, "HealthComponent")
    if health then
        Engine.log("Enemy health: " .. health.currentHealth)
    end
end

function update(entity, dt)
    -- Check if took damage from player collision
    -- Engine.damageEntity(entity, 10)  -- Alternative API
end
```

#### Step 5: Add to Scene JSON

```json
{
    "name": "enemy",
    "components": {
        "Transform": {...},
        "SpriteRenderer": {...},
        "HealthComponent": {
            "maxHealth": 50,
            "currentHealth": 50,
            "invulnerable": false
        },
        "ScriptComponent": {
            "scriptAsset": "assets/scripts/enemy.lua"
        }
    }
}
```

---

## 4. Future Components & Stubs

### 4.1 Physics Component (Stub)

```cpp
// Scene/include/sle/scene/components/PhysicsComponent.hpp
#pragma once

#include <glm/glm.hpp>

namespace sle::components {

struct PhysicsComponent
{
    // Body properties
    float mass = 1.0f;
    float friction = 0.5f;
    float restitution = 0.2f;  // Bounciness
    
    // Current state
    glm::vec2 velocity{0.0f};
    glm::vec2 acceleration{0.0f};
    
    // Flags
    bool useGravity = true;
    bool isKinematic = false;  // If true, not affected by forces
    
    // Shape (for now, assume circle)
    float radius = 0.5f;
};

} // namespace sle::components
```

**Lua API (Future):**
```lua
Engine.setPhysicsVelocity(entity, {x = 10, y = 0})
Engine.applyForce(entity, {x = 5, y = 0})
Engine.setPhysicsGravity(entity, 0)  -- Disable gravity for this entity
```

### 4.2 Audio Component (Stub)

```cpp
// Scene/include/sle/scene/components/AudioComponent.hpp
#pragma once

#include <string>

namespace sle::components {

struct AudioComponent
{
    std::string audioAsset;   // Path to .wav or .ogg
    
    bool playing = false;
    bool loop = false;
    float volume = 1.0f;
    float pitch = 1.0f;
    
    // Internal: audio handle from miniaudio
    uint32_t audioHandle = 0;
};

} // namespace sle::components
```

**Lua API (Future):**
```lua
Engine.playAudio(entity, volume = 1.0, loop = false)
Engine.stopAudio(entity)
Engine.setAudioVolume(entity, 0.5)
```

### 4.3 Animator Component (Stub)

```cpp
// Scene/include/sle/scene/components/AnimatorComponent.hpp
#pragma once

#include <string>

namespace sle::components {

struct AnimatorComponent
{
    std::string animatorAsset;  // Path to .anim or YAML
    
    std::string currentAnimation;
    float animationTime = 0.0f;
    bool playing = false;
    bool loop = true;
    
    // Animation speed multiplier
    float speed = 1.0f;
};

} // namespace sle::components
```

**Lua API (Future):**
```lua
Engine.playAnimation(entity, "run", loop = true)
Engine.stopAnimation(entity)
Engine.getAnimationTime(entity)
```

### 4.4 Collider Component (Stub)

```cpp
// Scene/include/sle/scene/components/ColliderComponent.hpp
#pragma once

#include <glm/glm.hpp>
#include <string>

namespace sle::components {

enum class ColliderShape
{
    Circle,
    Box,
    Polygon
};

struct ColliderComponent
{
    ColliderShape shape = ColliderShape::Circle;
    
    // Box: width, height
    // Circle: radius
    glm::vec2 size{1.0f, 1.0f};
    
    // Offset from Transform position
    glm::vec2 offset{0.0f, 0.0f};
    
    // Physics
    bool isTrigger = false;  // If true, no physics response (detect only)
    std::string tag;         // For collision filtering
    
    // Internal: Box2D fixture handle
    uint32_t fixtureHandle = 0;
};

} // namespace sle::components
```

**Lua API (Future):**
```lua
function onCollisionEnter(entity, other) end
function onCollisionStay(entity, other) end
function onCollisionExit(entity, other) end

Engine.getCollisionTag(entity)
Engine.setColliderSize(entity, {x = 10, y = 10})
```

### 4.5 Tilemap Component (Stub)

```cpp
// Scene/include/sle/scene/components/TilemapComponent.hpp
#pragma once

#include <string>

namespace sle::components {

struct TilemapComponent
{
    std::string tiledMapAsset;  // Path to .tmx (Tiled map)
    
    float tileSize = 32.0f;
    int mapWidth = 0;
    int mapHeight = 0;
    
    // Internal: tileset texture & mesh
    uint32_t textureId = 0;
    uint32_t meshId = 0;
};

} // namespace sle::components
```

**Lua API (Future):**
```lua
Engine.loadTiledMap(entity, "assets/maps/level1.tmx")
Engine.getTile(entity, gridX, gridY)
Engine.setTile(entity, gridX, gridY, tileId)
```

---

## 5. Component Registration Pattern

### 5.1 Centralized Registration (Future Enhancement)

```cpp
// Scene/include/sle/scene/ComponentRegistry.hpp
namespace sle::entity {

class ComponentRegistry
{
public:
    // Register a component type for Lua serialization
    template<typename T>
    void registerComponent(const std::string& name)
    {
        serializers[name] = [](const void* comp) -> LuaTable {
            return serialize<T>((const T*)comp);
        };
        
        deserializers[name] = [](void* comp, const LuaTable& table) {
            deserialize<T>((T*)comp, table);
        };
    }
    
    // Serialize component to Lua table
    LuaTable serialize(const std::string& componentName, const void* component);
    
    // Deserialize Lua table to component
    void deserialize(const std::string& componentName, void* component, 
                    const LuaTable& table);
    
private:
    using Serializer = std::function<LuaTable(const void*)>;
    using Deserializer = std::function<void(void*, const LuaTable&)>;
    
    std::unordered_map<std::string, Serializer> serializers;
    std::unordered_map<std::string, Deserializer> deserializers;
    
    // Template specializations for each component
    template<typename T>
    LuaTable serialize(const T* comp) { /* specialize for each type */ }
    
    template<typename T>
    void deserialize(T* comp, const LuaTable& table) { /* specialize */ }
};

} // namespace sle::entity
```

**Usage:**
```cpp
// In Engine::init()
componentRegistry.registerComponent<Transform>("Transform");
componentRegistry.registerComponent<SpriteRenderer>("SpriteRenderer");
componentRegistry.registerComponent<HealthComponent>("HealthComponent");
componentRegistry.registerComponent<PhysicsComponent>("PhysicsComponent");
```

---

## 6. JSON Serialization Pattern

### 6.1 Scene JSON Structure

```json
{
  "version": "1.0",
  "name": "Level1",
  
  "entities": [
    {
      "id": 1,
      "name": "player",
      "active": true,
      "tags": ["player", "protagonist"],
      
      "components": {
        "Transform": {
          "position": [100, 100],
          "rotation": 0,
          "scale": [1, 1]
        },
        "SpriteRenderer": {
          "color": [1, 1, 1, 1],
          "size": [32, 32],
          "texture": "assets/textures/player.png"
        },
        "PhysicsComponent": {
          "mass": 1.0,
          "velocity": [0, 0],
          "useGravity": true
        },
        "ScriptComponent": {
          "script": "assets/scripts/player.lua"
        }
      }
    },
    
    {
      "id": 2,
      "name": "tilemap",
      "active": true,
      
      "components": {
        "Transform": {
          "position": [0, 0],
          "rotation": 0,
          "scale": [1, 1]
        },
        "TilemapComponent": {
          "tiledMap": "assets/maps/level1.tmx"
        }
      }
    }
  ],
  
  "metadata": {
    "camera": {
      "position": [240, 135],
      "zoom": 1.0
    },
    "music": "assets/audio/level1_theme.ogg"
  }
}
```

### 6.2 JSON Loader Pattern

```cpp
// Engine/include/sle/engine/SceneLoader.hpp
#pragma once

#include <nlohmann/json.hpp>
#include <sle/core/Result.hpp>
#include <string>

namespace sle {

class SceneLoader
{
public:
    Result<bool> loadScene(const std::string& jsonPath);
    Result<bool> saveScene(const std::string& jsonPath);
    
private:
    void loadEntity(const nlohmann::json& entityJson);
    void loadComponent(sle::entity::Entity entity, 
                      const std::string& componentName,
                      const nlohmann::json& componentJson);
    
    nlohmann::json saveEntity(sle::entity::Entity entity);
    nlohmann::json saveComponent(sle::entity::Entity entity,
                                const std::string& componentName);
};

} // namespace sle
```

---

## 7. Dependency Diagram: Components & Systems

```
             Runtime (Engine)
                   │
         ┌─────────┼─────────┐
         │         │         │
      Renderer  Scene      ScriptEngine
        │        │          │
        │      Registry    Lua VM
        │        │         (Single)
        │      Entity    (Shared state)
        │        │
        │    Components:
        │    ├─ Transform
        │    ├─ SpriteRenderer
        │    ├─ ScriptComponent
        │    ├─ PhysicsComponent (future)
        │    ├─ AudioComponent (future)
        │    ├─ AnimatorComponent (future)
        │    ├─ ColliderComponent (future)
        │    └─ TilemapComponent (future)
        │
        └─→ Renders SpriteRenderer
            (Transform + SpriteRenderer)

Lua Scripts
    ↓
Engine API
    ↓
ScriptApiImpl (Runtime)
    ↓
Scene (ECS)
    ↓
Components update
    ↓
Next frame render
```

---

## 8. Performance Considerations

### 8.1 Component Storage

**Current approach: Sparse-set (ComponentPool)**
```cpp
template<typename T>
class ComponentPool
{
    std::unordered_map<uint32_t, T> data;  // Entity ID → Component
};
```

**Pros:**
- Easy to iterate all entities with component
- Add/remove O(1)
- Supports sparse components

**Cons:**
- Cache misses (unordered_map pointer chasing)
- Memory scattered

**Future optimization: AoS (Array of Structures)**
```cpp
template<typename T>
class ComponentPool
{
    std::vector<T> data;
    std::unordered_map<uint32_t, size_t> indices;  // Entity ID → position in vector
};
```

Better cache locality but more complex.

### 8.2 Lua Overhead

- **Lua tables**: Converted to/from C++ on each getComponent call (accept this for now)
- **Script updates**: O(n) where n = active scripts
- **Single Lua VM**: Shared state is good, but need to isolate per-script state

**Future: Consider per-scene Lua contexts if needed**

### 8.3 Serialization Performance

- JSON loading: Done at scene load time (acceptable)
- Component serialization: Done every getComponent call (add caching if bottleneck)

---

## 9. Adding Physics System (Complete Example)

### Step 1: Stubs & Components

```cpp
// Scene/include/sle/scene/components/PhysicsComponent.hpp
struct PhysicsComponent
{
    float mass = 1.0f;
    glm::vec2 velocity{0.0f};
    bool useGravity = true;
};

// Core/include/sle/core/Physics.hpp (NEW MODULE)
class PhysicsWorld
{
public:
    void update(float dt);
    void addRigidbody(Entity entity, const PhysicsComponent& comp);
    void removeRigidbody(Entity entity);
};
```

### Step 2: Runtime Integration

```cpp
// Engine/src/Engine.cpp

void Engine::tick(float dt)
{
    // ... scripts ...
    
    // Physics update AFTER scripts (scripts can apply forces)
    physicsWorld.update(dt);
    
    // ... render ...
}
```

### Step 3: Lua API Additions

```cpp
// ScriptApiImpl

void ScriptApiImpl::applyForce(EntityRef entity, glm::vec2 force)
{
    // Apply force to PhysicsComponent
    // Engine broadcasts force event or direct modification
}

glm::vec2 ScriptApiImpl::getVelocity(EntityRef entity)
{
    // Get current velocity
}

void ScriptApiImpl::setVelocity(EntityRef entity, glm::vec2 velocity)
{
    // Set velocity directly
}
```

### Step 4: Lua Usage

```lua
-- assets/scripts/projectile.lua

function init(entity)
    Engine.setVelocity(entity, {x = 500, y = 0})
end

function update(entity, dt)
    local pos = Engine.getComponent(entity, "Transform")
    
    -- Check if out of bounds
    if pos.x > 1000 then
        Engine.destroyEntity(entity)
    end
end
```

---

## 10. Extension Roadmap

```
Phase 1: ✅ Core ECS + Script lifecycle
Phase 2: ✅ Lua integration + basic components
Phase 3: Physics (Box2D)
         ├─ PhysicsComponent
         ├─ ColliderComponent
         └─ Lua API: applyForce, setVelocity, collision callbacks
Phase 4: Audio (miniaudio)
         ├─ AudioComponent
         └─ Lua API: playSound, playMusic, setVolume
Phase 5: Animation
         ├─ AnimatorComponent
         └─ Lua API: playAnimation, getAnimationTime
Phase 6: Tiled Integration
         ├─ TilemapComponent
         └─ Lua API: getTile, setTile, getTileMap
Phase 7: Particles (custom)
         ├─ ParticleEmitterComponent
         └─ Lua API: emit, stop
Phase 8: UI (custom or dear-imgui)
         ├─ UIComponent
         └─ Lua API: createButton, createText, etc.
Phase 9: Networking (netcode or Steam)
Phase 10: Editor (Godot-like built on Qt)
```

---

## 11. Checklist for Adding New Component

Copy this when adding a component:

```markdown
### New Component: MyComponent

- [ ] Define struct in `Scene/include/sle/scene/components/MyComponent.hpp`
  - [ ] Make it POD (no methods, plain members)
  - [ ] Add default values
  
- [ ] Update `Scene` module exports (public header)

- [ ] Add Lua serialization in `ScriptApiImpl::getComponent()`
  - [ ] Convert C++ struct → LuaTable

- [ ] Add Lua deserialization in `ScriptApiImpl::setComponent()`
  - [ ] Convert LuaTable → C++ struct

- [ ] Add JSON serialization in `SceneLoader`
  - [ ] Convert nlohmann::json → MyComponent

- [ ] Update Scene JSON schema documentation

- [ ] Add optional Lua API helpers (if component needs special access)
  - [ ] Register in LuaBindings if needed

- [ ] Update example scene file

- [ ] Document in ARCHITECTURE.md component list

- [ ] Test:
  - [ ] Create entity with component via JSON
  - [ ] Access from Lua script via Engine API
  - [ ] Modify from Lua script
  - [ ] Serialize back to JSON
```

