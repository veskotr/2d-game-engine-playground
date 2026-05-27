# SLE (Simple Little Engine) Architecture Design

> Historical design note: prefer `ARCHITECTURE_VERIFIED.md` for current architecture truth and `ENGINE_MASTER_PLAN.md` for active roadmap planning.

## Executive Summary

A **data-driven, Lua-scripted 2D game engine** built on modular C++ with strict dependency layering, pure ECS, and safe Lua integration via a controlled EngineAPI.

---

## 1. Module Dependency Architecture

### Dependency Chain (One Direction Only)

```
Core (utilities)
  ↓
Platform (GLFW, input, timing)
  ↓
Renderer (OpenGL, shaders, camera, render commands)
  ↓
Resources (asset management)
  ↓
Scene (ECS: entities, components, registry)
  ↓
Scripting (Lua VM, EngineAPI, script lifecycle)
  ↓
Runtime (orchestration, game loop, scene/renderer/script coordination)
  ↓
Sandbox (game application)
```

### Module Responsibilities

| Module | Owns | Knows About | Constraint |
|--------|------|-------------|-----------|
| **Core** | Log, Result, Timer, EngineConfig | Nothing | No external deps |
| **Platform** | Window, Input, Camera2D | Core only | GLFW, raw GL |
| **Renderer** | Shader, Texture, RenderCommand, Renderer | Core, Platform, Resources | NO Scene/Lua |
| **Resources** | ResourcePool, Texture/Shader loading | Core, Renderer | Asset management only |
| **Scene** | Entity, Registry, ECS, EngineObject, Components | Core, Resources | Pure data model, NO rendering, NO Lua |
| **Scripting** | Lua VM, EngineAPI, Script lifecycle | All layers above | Single global VM |
| **Runtime** | Game loop, updates, scene/renderer/script orchestration | Everything | Main orchestrator |
| **Sandbox** | Game code entry point | Runtime | Application only |

---

## 2. Lua Scripting Architecture

### 2.1 Lua VM Strategy: Single Global VM

**Decision Rationale:**
- Simplest to implement and debug
- Standard approach (Godot, LÖVE, game industry standard)
- Shared Lua state allows scripts to call helper functions
- No per-entity VM overhead
- Easy to add entity/scene isolation if needed later

**VM Ownership:**
- Owned and managed by `sle::scripting::ScriptEngine`
- Created once at engine init
- Destroyed at shutdown
- Thread-safe for single-threaded engine

### 2.2 ScriptComponent Design

```cpp
// Scene/components/ScriptComponent.hpp
namespace sle::components {

struct ScriptComponent
{
    std::string scriptAsset;      // Path to .lua file
    bool enabled = true;          // Enable/disable without unloading
    
    // Internal: NOT user-facing
    uint32_t luaRefInit = LUA_NOREF;    // Reference to init() function
    uint32_t luaRefUpdate = LUA_NOREF;  // Reference to update(dt) function
    uint32_t luaRefDestroy = LUA_NOREF; // Reference to destroy() function
    uint32_t luaRefData = LUA_NOREF;    // Reference to script's userdata table
    
    bool initialized = false;
};

} // namespace sle::components
```

### 2.3 Script Lifecycle

Each Lua script file must follow this pattern:

```lua
-- assets/scripts/player.lua

-- Local state (persistent per-entity instance)
local state = {
    speed = 100,
    health = 100
}

-- Called once when entity is created/scene initialized
function init(entity)
    print("Player initialized, entity ID: " .. entity)
    -- Access EngineAPI: Engine.getComponent, Engine.setComponent, etc.
    local transform = Engine.getComponent(entity, "Transform")
    print("Starting position: ", transform.x, transform.y)
end

-- Called every frame
function update(entity, dt)
    if Engine.Input.isKeyDown(GLFW_KEY_W) then
        local pos = Engine.getComponent(entity, "Transform")
        pos.y = pos.y + state.speed * dt
        Engine.setComponent(entity, "Transform", pos)
    end
    
    state.health = state.health - 0.1 * dt
end

-- Called when entity destroyed
function destroy(entity)
    print("Player destroyed")
end

-- Helper functions (private to this script)
local function handleCollision(other)
    print("Hit: " .. other)
end

-- Exposed to EngineAPI for future use
return {
    getHealth = function() return state.health end,
    takeDamage = function(amount) state.health = state.health - amount end
}
```

### 2.4 ScriptComponent Lifecycle Flow

```
1. Entity created with ScriptComponent
   ↓
2. Runtime detects new ScriptComponent
   ↓
3. ScriptEngine::loadScript(scriptAsset)
   - Load .lua file into Lua VM
   - Extract init, update, destroy function references
   - Store refs in component.luaRefInit, luaRefUpdate, luaRefDestroy
   ↓
4. ScriptEngine::callInit(entity)
   - Push entity ID to Lua
   - Call init(entity)
   - Set component.initialized = true
   ↓
5. Every frame: ScriptEngine::updateScripts(dt)
   - For each entity with ScriptComponent and initialized=true:
     - Push entity ID and dt to Lua
     - Call update(entity, dt)
   ↓
6. Entity destroyed
   ↓
7. ScriptEngine::callDestroy(entity)
   - Push entity ID to Lua
   - Call destroy(entity)
   - Unreference Lua refs (luaL_unref)
```

### 2.5 Script Instance Storage

Each script entity maintains its own **script userdata table** in Lua:

```lua
-- Internal Lua structure (managed by C++)
sle_scripts = {
    [entity_id_1] = {
        asset = "assets/scripts/player.lua",
        init = function_ref,
        update = function_ref,
        destroy = function_ref,
        data = { /* script's local state */ }
    },
    [entity_id_2] = { ... }
}
```

This allows multiple entities to use the same script file while maintaining separate state.

---

## 3. EngineAPI: Lua's Safe Interface to C++

### 3.1 EngineAPI Design Philosophy

**Principles:**
- **Minimal**: Only expose what scripts need (50-100 functions max)
- **Type-safe**: Validate all Lua→C++ data crossing
- **Versioned**: Can evolve without breaking old scripts
- **Sandboxed**: No direct memory access, no system calls
- **Non-blocking**: All operations synchronous, no async yet

### 3.2 EngineAPI Specification

```cpp
// Scripting/ScriptApi.hpp (Lua-facing C++ API)

namespace sle::scripting {

class ScriptApi
{
    // ====== ENTITY MANAGEMENT ======
    virtual EntityRef createEntity() = 0;
    virtual void destroyEntity(EntityRef entity) = 0;
    virtual bool isEntityAlive(EntityRef entity) const = 0;
    
    // ====== COMPONENT ACCESS ======
    // All component access is type-safe via Lua tables
    virtual LuaTable getComponent(EntityRef entity, const std::string& componentName) = 0;
    virtual void setComponent(EntityRef entity, const std::string& componentName, 
                             const LuaTable& data) = 0;
    virtual bool hasComponent(EntityRef entity, const std::string& componentName) const = 0;
    
    // Special case: common components have helpers
    virtual void setTransform(EntityRef entity, glm::vec2 pos, float rotation, 
                             glm::vec2 scale) = 0;
    virtual LuaTable getTransform(EntityRef entity) const = 0;
    
    // ====== RESOURCES ======
    virtual uint32_t loadTexture(const std::string& assetPath) = 0;
    virtual uint32_t loadShader(const std::string& vertPath, 
                               const std::string& fragPath) = 0;
    
    // ====== CAMERA ======
    virtual void setCameraPosition(glm::vec2 pos) = 0;
    virtual void setCameraZoom(float zoom) = 0;
    
    // ====== RENDERING ======
    virtual void drawQuad(glm::vec2 pos, glm::vec2 size, glm::vec4 color, 
                         uint32_t textureId = 0) = 0;
    virtual void drawLine(glm::vec2 from, glm::vec2 to, glm::vec4 color, 
                         float thickness = 1.0f) = 0;
    
    // ====== ENGINE STATE ======
    virtual float getDeltaTime() const = 0;
    virtual glm::vec2 getWindowSize() const = 0;
    virtual float getElapsedTime() const = 0;
    
    // ====== INPUT (immediate mode) ======
    virtual bool isKeyDown(int key) const = 0;
    virtual bool isKeyPressed(int key) const = 0;
    virtual bool isKeyReleased(int key) const = 0;
    virtual glm::dvec2 getMousePosition() const = 0;
    virtual bool isMouseButtonDown(int button) const = 0;
    
    // ====== LOGGING ======
    virtual void log(const std::string& message) = 0;
    virtual void warn(const std::string& message) = 0;
    virtual void error(const std::string& message) = 0;
    
    // ====== SCENE QUERIES ======
    virtual std::vector<EntityRef> getEntitiesByTag(const std::string& tag) = 0;
    virtual EntityRef getEntityByName(const std::string& name) = 0;
    virtual std::vector<EntityRef> getAllEntities() = 0;
    
    // ====== PHYSICS (Future) ======
    // virtual void setRigidbodyVelocity(...) = 0;
    // virtual glm::vec2 getRigidbodyVelocity(...) = 0;
    
    // ====== AUDIO (Future) ======
    // virtual void playSound(const std::string& assetPath) = 0;
};

} // namespace sle::scripting
```

### 3.3 Lua Bindings Layer

**How Lua calls EngineAPI:**

```cpp
// Scripting/LuaBindings.cpp
// Registers C++ functions as Lua globals

void registerLuaBindings(lua_State* L, ScriptApi* api)
{
    // Create Engine global table
    lua_newtable(L);
    
    // Engine.createEntity()
    lua_pushcfunction(L, [](lua_State* L) -> int {
        ScriptApi* api = (ScriptApi*)lua_touserdata(L, lua_upvalueindex(1));
        EntityRef entity = api->createEntity();
        lua_pushinteger(L, entity.id);
        return 1;
    });
    lua_setfield(L, -2, "createEntity");
    
    // Engine.getComponent(entity, name)
    lua_pushcfunction(L, [](lua_State* L) -> int {
        ScriptApi* api = (ScriptApi*)lua_touserdata(L, lua_upvalueindex(1));
        EntityRef entity{(uint32_t)lua_tointeger(L, 1)};
        const char* name = lua_tostring(L, 2);
        
        LuaTable table = api->getComponent(entity, name);
        // Convert LuaTable to Lua table on stack
        // ... implementation details ...
        return 1;
    });
    lua_setfield(L, -2, "getComponent");
    
    // ... repeat for all API functions ...
    
    lua_setglobal(L, "Engine");
}
```

**Usage in Lua scripts:**

```lua
-- In player.lua
function update(entity, dt)
    if Engine.isKeyDown(GLFW_KEY_W) then
        local transform = Engine.getComponent(entity, "Transform")
        transform.y = transform.y + 100 * dt
        Engine.setComponent(entity, "Transform", transform)
    end
end
```

---

## 4. Runtime Layer Orchestration

### 4.1 Runtime (Engine) Responsibilities

The `Runtime` module (currently called `Engine`) is the **orchestrator**:

```cpp
// Engine/Engine.hpp
namespace sle {

class Engine : public ScriptApi  // Implements the EngineAPI
{
public:
    // Initialization
    Result<bool> init(const EngineConfig& config);
    void shutdown();
    
    // Main loop
    void run();
    void tick(float dt);
    
    // Scene management
    void loadScene(const std::string& scenePath);
    void unloadScene();
    
    // Internal access (private, for Runtime only)
    Scene& getScene() { return scene; }
    Renderer& getRenderer() { return renderer; }
    ScriptEngine& getScriptEngine() { return scriptEngine; }
    Resources& getResources() { return resources; }
    
private:
    // Systems
    Window window;
    Renderer renderer;
    Scene scene;
    ScriptEngine scriptEngine;
    Resources resources;
    Camera2D camera;
    Timer timer;
    Input input;
    
    // Game state
    bool running = true;
};

} // namespace sle
```

### 4.2 Main Game Loop

```cpp
// Engine/Engine.cpp

void Engine::run()
{
    while (!window.shouldClose() && running)
    {
        // ====== INPUT PHASE ======
        Input::update();
        window.pollEvents();
        
        // ====== TIMING ======
        timer.tick();
        float dt = timer.getDeltaTime();
        
        // ====== SCRIPT UPDATE PHASE ======
        scriptEngine.updateAllScripts(dt);  // Lua runs here
        
        // ====== ECS LOGIC PHASE (if non-Lua systems exist) ======
        // Example: physics update
        // physicsSystem.update(dt);
        
        // ====== RENDER PHASE ======
        renderer.beginFrame();
        
        // Submit quads from ECS (Transform + SpriteRenderer)
        scene.view<Transform, SpriteRenderer>(
            [this](Entity ent, Transform& t, SpriteRenderer& sprite)
            {
                QuadCommand cmd;
                cmd.position = t.position;
                cmd.size = sprite.size * t.scale;
                cmd.color = sprite.color;
                cmd.texture = sprite.textureId;
                renderer.submit(cmd);
            });
        
        renderer.endFrame();
        window.swapBuffers();
    }
}
```

### 4.3 ScriptEngine: Lifecycle Management

```cpp
// Scripting/ScriptEngine.hpp
namespace sle::scripting {

class ScriptEngine
{
public:
    bool init();
    void shutdown();
    
    // Load and initialize a script asset
    bool loadScript(const std::string& scriptAsset);
    
    // Lifecycle callbacks (called by Runtime)
    void initializeScript(Entity entity, const ScriptComponent& script);
    void updateScripts(float dt);
    void destroyScript(Entity entity, ScriptComponent& script);
    
    // Called when Runtime detects new ScriptComponents
    void onComponentAdded(Entity entity, ScriptComponent& script);
    void onComponentRemoved(Entity entity, ScriptComponent& script);
    
private:
    lua_State* L = nullptr;
    ScriptApiImpl apiImpl;  // Implements ScriptApi interface
    
    // Maps entity → script instance data
    std::unordered_map<uint32_t, ScriptInstance> instances;
    
    int callLuaFunction(uint32_t luaRef, uint32_t entityId, float dt = 0.0f);
};

} // namespace sle::scripting
```

### 4.4 Integration: When ScriptComponent is Added

```cpp
// In Runtime::tick() or Scene::addComponent()

void Runtime::tick(float dt)
{
    // ...
    
    // Check for new ScriptComponents
    scene.view<ScriptComponent>(
        [this](Entity entity, ScriptComponent& script)
        {
            if (!script.initialized && script.enabled)
            {
                // First time seeing this component
                scriptEngine.loadScript(script.scriptAsset);
                scriptEngine.initializeScript(entity, script);
            }
        });
    
    // Update scripts
    scriptEngine.updateScripts(dt);
    
    // ...
}
```

---

## 5. ECS-Lua Interaction Pattern

### 5.1 Data Flow: Lua → Component → Renderer

```
Lua Script
  ↓
Engine.setComponent(entity, "Transform", {x=10, y=20})
  ↓
ScriptApi::setComponent() → Runtime
  ↓
Scene::updateComponent(entity, componentData)
  ↓
Component state changes in ECS
  ↓
Next render frame:
Scene::view<Transform, SpriteRenderer>()
  ↓
Renderer::submit(QuadCommand)
  ↓
OpenGL render
```

### 5.2 Component Type Registry

For Lua to safely access any component, we need a **component type registry**:

```cpp
// Scene/ComponentRegistry.hpp
namespace sle::entity {

class ComponentRegistry
{
public:
    using ComponentGetter = std::function<LuaTable(Entity)>;
    using ComponentSetter = std::function<void(Entity, const LuaTable&)>;
    
    template<typename T>
    void registerComponent(const std::string& name)
    {
        // Register getters/setters for component type T
        getters[name] = [](Entity e) { /* Serialize T to Lua */ };
        setters[name] = [](Entity e, LuaTable t) { /* Deserialize Lua to T */ };
    }
    
    LuaTable getComponent(Entity e, const std::string& name);
    void setComponent(Entity e, const std::string& name, const LuaTable& data);
    
private:
    std::unordered_map<std::string, ComponentGetter> getters;
    std::unordered_map<std::string, ComponentSetter> setters;
};

} // namespace sle::entity
```

### 5.3 Component Serialization to Lua

```cpp
// Pseudocode: Convert Transform to Lua table

LuaTable ComponentRegistry::getComponent(Entity e, const std::string& name)
{
    if (name == "Transform")
    {
        Transform* t = scene.getComponent<Transform>(e);
        if (!t) return LuaTable(); // nil
        
        LuaTable result;
        result["x"] = t->position.x;
        result["y"] = t->position.y;
        result["rotation"] = t->rotation;
        result["scaleX"] = t->scale.x;
        result["scaleY"] = t->scale.y;
        return result;
    }
    // ... other components ...
}

void ComponentRegistry::setComponent(Entity e, const std::string& name, 
                                     const LuaTable& data)
{
    if (name == "Transform")
    {
        Transform* t = scene.getComponent<Transform>(e);
        if (!t) return;
        
        t->position.x = data.getFloat("x");
        t->position.y = data.getFloat("y");
        t->rotation = data.getFloat("rotation");
        t->scale.x = data.getFloat("scaleX");
        t->scale.y = data.getFloat("scaleY");
    }
}
```

---

## 6. Data-Driven Scene Loading (Future-Proof)

### 6.1 Scene JSON Format

```json
{
  "metadata": {
    "name": "Level1",
    "version": "1.0"
  },
  "entities": [
    {
      "id": 1,
      "name": "player",
      "tags": ["player", "controllable"],
      "components": {
        "Transform": {
          "position": [100, 200],
          "rotation": 0,
          "scale": [1, 1]
        },
        "SpriteRenderer": {
          "color": [1, 1, 1, 1],
          "size": [32, 32],
          "textureId": "assets/textures/player.png"
        },
        "ScriptComponent": {
          "scriptAsset": "assets/scripts/player.lua",
          "enabled": true
        }
      }
    },
    {
      "id": 2,
      "name": "enemy_spawner",
      "components": {
        "Transform": {
          "position": [500, 200],
          "rotation": 0,
          "scale": [1, 1]
        },
        "ScriptComponent": {
          "scriptAsset": "assets/scripts/enemy_spawner.lua",
          "enabled": true
        }
      }
    }
  ]
}
```

### 6.2 Scene Loader

```cpp
// Runtime/SceneLoader.hpp
namespace sle {

class SceneLoader
{
public:
    Result<bool> loadScene(const std::string& jsonPath, Scene& scene, 
                          Resources& resources);
    
private:
    Entity createEntityFromJson(const nlohmann::json& jsonEntity, Scene& scene);
    void applyComponent(Entity e, const std::string& componentName,
                       const nlohmann::json& componentData, Scene& scene,
                       Resources& resources);
};

} // namespace sle
```

---

## 7. Safety & Lifetime Management

### 7.1 EntityRef Validity Checking

```cpp
// In Lua binding, all entity refs are validated:

int lua_createEntity(lua_State* L)
{
    ScriptApi* api = getApi(L);
    EntityRef ref = api->createEntity();
    
    lua_pushinteger(L, ref.id);
    return 1;
}

int lua_getComponent(lua_State* L)
{
    ScriptApi* api = getApi(L);
    EntityRef ref{(uint32_t)luaL_checkinteger(L, 1)};
    const char* name = luaL_checkstring(L, 2);
    
    // Safety check: entity must be alive
    if (!api->isEntityAlive(ref))
    {
        return luaL_error(L, "Entity %u is no longer alive", ref.id);
    }
    
    // Now safe to access
    LuaTable table = api->getComponent(ref, name);
    // ...
}
```

### 7.2 Script Instance Cleanup

```cpp
// When entity is destroyed, script refs are released:

void ScriptEngine::destroyScript(Entity entity, ScriptComponent& script)
{
    if (!script.initialized)
        return;
    
    // Call Lua destroy callback
    callLuaFunction(script.luaRefDestroy, entity.getID());
    
    // Unreference all Lua refs
    luaL_unref(L, LUA_REGISTRYINDEX, script.luaRefInit);
    luaL_unref(L, LUA_REGISTRYINDEX, script.luaRefUpdate);
    luaL_unref(L, LUA_REGISTRYINDEX, script.luaRefDestroy);
    luaL_unref(L, LUA_REGISTRYINDEX, script.luaRefData);
    
    script.luaRefInit = LUA_NOREF;
    script.luaRefUpdate = LUA_NOREF;
    script.luaRefDestroy = LUA_NOREF;
    script.luaRefData = LUA_NOREF;
    script.initialized = false;
    
    instances.erase(entity.getID());
}
```

### 7.3 No Lua Memory Leaks

- All Lua refs stored in registry (not stack)
- Refs cleaned up on entity destruction
- VM shutdown clears all state
- Use `luaL_unref()` when removing scripts

---

## 8. Component Architecture Summary

### 8.1 Component Types

```cpp
// Transform: Position, rotation, scale (universal)
struct Transform { glm::vec2 position; float rotation; glm::vec2 scale; };

// SpriteRenderer: Visual representation (ECS-only, no Lua direct access)
struct SpriteRenderer { glm::vec4 color; glm::vec2 size; uint32_t textureId; };

// ScriptComponent: Lua script attachment (bridges ECS and Lua)
struct ScriptComponent 
{
    std::string scriptAsset;
    bool enabled;
    uint32_t luaRefInit;    // Lua registry refs
    uint32_t luaRefUpdate;
    uint32_t luaRefDestroy;
    uint32_t luaRefData;
    bool initialized;
};

// Future components (stubs for now)
// struct RigidBody { /* physics */ };
// struct Collider { /* collision */ };
// struct AudioSource { /* audio */ };
```

### 8.2 Component Lifecycle

```
ECS Frame:
1. Add new ScriptComponent to entity
   ↓
2. ScriptEngine detects new component
   ↓
3. Load script asset, extract Lua functions
   ↓
4. Call Lua init(entity)
   ↓
5. Every frame: call Lua update(entity, dt)
   ↓
6. Remove ScriptComponent or destroy entity
   ↓
7. Call Lua destroy(entity)
   ↓
8. Clean up Lua refs
```

---

## 9. File Structure

```
EngineModules/
├── Core/                          # No changes
├── Platform/                      # No changes
├── Renderer/                      # No changes
├── Resources/                     # No changes
├── Scene/
│   ├── include/sle/scene/
│   │   ├── Entity.hpp
│   │   ├── ComponentPool.hpp
│   │   ├── Registry.hpp
│   │   ├── Scene.hpp
│   │   ├── EngineObject.hpp
│   │   ├── components/
│   │   │   ├── Transform.hpp
│   │   │   ├── SpriteRenderer.hpp
│   │   │   ├── ScriptComponent.hpp
│   │   │   └── (future: RigidBody, Collider, etc.)
│   │   └── ComponentRegistry.hpp   [NEW]
│   └── src/
│       ├── Registry.cpp
│       ├── EngineObject.cpp
│       ├── Scene.cpp
│       └── ComponentRegistry.cpp   [NEW]
├── Scripting/                     [REDESIGNED]
│   ├── include/sle/scripting/
│   │   ├── ScriptApi.hpp          # Abstract interface
│   │   ├── ScriptEngine.hpp       # Lua VM manager
│   │   ├── LuaBindings.hpp        # [NEW] Lua function bindings
│   │   └── ScriptApiImpl.hpp       # [NEW] Implementation in Runtime
│   └── src/
│       ├── ScriptEngine.cpp
│       ├── LuaBindings.cpp        # [NEW]
│       └── (no ScriptApiImpl here)
├── Engine/                        [RENAMED from Runtime/]
│   ├── include/sle/engine/
│   │   ├── Engine.hpp             # Main orchestrator (implements ScriptApi)
│   │   └── SceneLoader.hpp        # [NEW] JSON scene loading
│   └── src/
│       ├── Engine.cpp
│       └── SceneLoader.cpp        # [NEW]
└── Sandbox/
    └── main.cpp
```

---

## 10. Implementation Priority & Roadmap

### Phase 1: Core Architecture (Current)
- ✅ Module structure
- ✅ ECS system
- ✅ Pure Scene (no Lua)
- ✅ Renderer decoupling
- [ ] ComponentRegistry
- [ ] ScriptApi interface

### Phase 2: Lua Integration
- [ ] Lua VM setup (ScriptEngine)
- [ ] Basic Lua bindings (Engine.createEntity, getComponent, setComponent)
- [ ] Script lifecycle (init/update/destroy)
- [ ] ScriptComponent execution
- [ ] Entity/component serialization to Lua tables

### Phase 3: Data-Driven Content
- [ ] Scene JSON format
- [ ] SceneLoader
- [ ] Asset paths resolution
- [ ] Hot-reloading (optional)

### Phase 4: Extended API
- [ ] Camera control from Lua
- [ ] Resource loading from Lua
- [ ] Scene queries (getEntitiesByTag)
- [ ] Entity destruction from Lua

### Phase 5: Physics & Audio (Future)
- [ ] Physics component stubs
- [ ] Audio component stubs
- [ ] Extended Lua API

---

## 11. Code Example: End-to-End

### Step 1: Lua Script (`assets/scripts/player.lua`)

```lua
function init(entity)
    print("Player init, entity: " .. entity)
end

function update(entity, dt)
    if Engine.isKeyDown(GLFW_KEY_W) then
        local t = Engine.getComponent(entity, "Transform")
        t.y = t.y - 200 * dt
        Engine.setComponent(entity, "Transform", t)
    end
end

function destroy(entity)
    print("Player cleanup")
end
```

### Step 2: Scene JSON (`assets/scenes/level1.json`)

```json
{
  "entities": [
    {
      "name": "player",
      "components": {
        "Transform": {"position": [100, 100], "rotation": 0, "scale": [1, 1]},
        "SpriteRenderer": {"color": [1, 1, 1, 1], "size": [32, 32], "textureId": "assets/textures/player.png"},
        "ScriptComponent": {"scriptAsset": "assets/scripts/player.lua", "enabled": true}
      }
    }
  ]
}
```

### Step 3: Runtime (`Engine.cpp`)

```cpp
Result<bool> Engine::init(const EngineConfig& cfg)
{
    // Init all systems
    window.create(cfg);
    renderer.init();
    scriptEngine.init();
    
    // Load scene
    SceneLoader loader;
    loader.loadScene("assets/scenes/level1.json", scene, resources);
    
    return Result<bool>::success(true);
}

void Engine::tick(float dt)
{
    Input::update();
    
    // Update all scripts
    scriptEngine.updateScripts(dt);
    
    // Render from ECS
    renderer.beginFrame();
    scene.view<Transform, SpriteRenderer>([this](Entity e, Transform& t, SpriteRenderer& s) {
        renderer.submit({t.position, s.size, s.color, s.textureId});
    });
    renderer.endFrame();
}
```

### Step 4: Result

Player entity created from JSON, script runs every frame, responds to input, position updates in ECS, renders on screen.

---

## 12. Design Validation Checklist

- ✅ **Strict layering**: Core → Platform → Renderer → Resources → Scene → Scripting → Engine → Sandbox
- ✅ **Scene is pure ECS**: No rendering, no Lua inside Scene
- ✅ **Renderer is decoupled**: Only consumes commands, doesn't know about ECS/Lua
- ✅ **Lua is sandboxed**: Only accesses Engine via EngineAPI
- ✅ **Runtime orchestrates**: Single point of control for all systems
- ✅ **Safe lifetime**: Entity refs validated, Lua refs cleaned up
- ✅ **Extensible**: Easy to add physics, audio, animation later
- ✅ **Data-driven**: Scenes are JSON, scripts are external
- ✅ **Single Lua VM**: Shared state, per-entity script instances
- ✅ **Minimal API**: Only essential functions exposed to Lua
- ✅ **Future-proof**: Component registry allows new components without recompile

---

## 13. Future Enhancements

```cpp
// Physics
Engine.getRigidbodyVelocity(entity);
Engine.setRigidbodyForce(entity, force);

// Audio
Engine.playSound("assets/audio/jump.wav");
Engine.playMusic("assets/audio/theme.ogg", loop = true);

// Animation
Engine.playAnimation(entity, "run", speed = 1.0);

// Tiled Integration
Engine.loadTiledMap("assets/maps/level1.tmx");

// Particles
Engine.createParticleEmitter(position, emitterDef);

// UI (if needed)
Engine.createUIButton(x, y, width, height, callback);
```

