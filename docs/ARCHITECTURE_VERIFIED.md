---
Title: SLE 2D Engine - Comprehensive Architecture & Implementation Verification
Version: Current (May 2026)
Status: Verified Against Source Code
Last Verified: May 2026 — Phases 0–5 complete (Events, State Machine, Animation, Audio, UI Polish)
---

# SLE (Simple Little Engine) - Complete Architecture Reference

## Executive Summary

SLE is a **modular, data-driven 2D game engine** built in C++20 with:
- **Strict one-way module dependencies** (no circular imports)
- **Pure Entity-Component-System (ECS) architecture**
- **Single Lua VM per engine** for scripting
- **Dedicated Physics module** wrapping Box2D world/body/fixture management
- **Command-based rendering** with GPU batching optimization
- **Transform hierarchy** with dirty-flag optimization
- **Generic state machine system** driving animation, behavior, and gameplay flow
- **Generic property-animation system** with JSON clip assets and multi-track keyframe interpolation
- **Audio system** (miniaudio + stb_vorbis) supporting WAV, MP3, FLAC, OGG
- **UI system** with XML layouts, reactive bindings, screen/world-space modes, text alignment, word wrap, and anchor-based positioning
- **Dependency injection** via Context struct for cross-module communication

**Development Status**: Phases 0–5 complete. All core engine systems implemented and integration-tested. Ready for v1 planning. Production-ready for 2D games with Lua scripting, Box2D physics, sprite rendering, physics debug overlays, and zone/area triggers.

---

## Part 1: Module Architecture

### 1.1 Module Dependency Chain (Verified)

```
┌─────────────────────────────────────────────────────────────────┐
│                         Sandbox (Game)                          │
│                     ↑ (game application)                        │
└─────────────────────────────────────────────────────────────────┘
                            ↑
┌─────────────────────────────────────────────────────────────────┐
│                        Systems                                  │
│    (Runtime, game loop, orchestration, all per-frame systems)   │
│   Knows About: Everything below (orchestrator role)             │
└─────────────────────────────────────────────────────────────────┘
                            ↑
                 ┌──────────────┐
                 ↑              ↑
             ┌───────────┐  ┌──────┐
             │Scripting  │  │  UI  │
             │(Lua VM,   │  │(XML  │
             │BindLua)   │  │ docs)│
             └───────────┘  └──────┘
                 ↑              ↑
             ┌────────────────────┐
             │      Physics       │
             │ (Box2D world,      │
             │  fixtures, contacts│
             │  zones/areas)      │
             └────────────────────┘
                 ↑
          ┌──────────────────────────┐
          ↑              ↑           ↑
      ┌──────────────┐  ┌──────────┐  ┌────────┐
      │ Scene (ECS)  │  │Resources │  │ Events │
      │(Entities,    │  │(Asset    │  │(EventBus│
      │Components,   │  │ loading, │  │ types) │
      │Hierarchy)    │  │pooling)  │  └────────┘
      └──────────────┘  └──────────┘
          ↑                   ↑
          └───────────────────┘
                            ↑
┌─────────────────────────────────────────────────────────────────┐
│                        Renderer                                 │
│      (OpenGL, shaders, batching, GPU, debug overlays,          │
│       Camera2D)                                                 │
│   Knows About: Core, Platform, nothing about Scene/Lua          │
└─────────────────────────────────────────────────────────────────┘
                            ↑
┌─────────────────────────────────────────────────────────────────┐
│                        Platform                                 │
│              (GLFW, Input, Window)                              │
│   Knows About: Core only (strict isolation)                     │
└─────────────────────────────────────────────────────────────────┘
                            ↑
┌─────────────────────────────────────────────────────────────────┐
│                         Core                                    │
│           (Log, Timer, Result, EngineConfig)                    │
│   Knows About: Standard library only                            │
└─────────────────────────────────────────────────────────────────┘
```

**Golden Rule**: Lower layers NEVER depend on higher layers. This is enforced at compile time via CMakeLists.txt target_link_libraries ordering.

**Verified module order in CMake**: `Core -> Platform -> Renderer -> Resources -> Scene -> Events -> Physics -> Scripting -> UI -> Systems -> Sandbox`.

### 1.2 Module Responsibilities (Verified Against Code)

| Module | Responsibilities | Owns | Does NOT Own | External Dependencies |
|--------|------------------|------|--------------|----------------------|
| **Core** | Foundational utilities | Log, Result<T>, Timer, EngineConfig | Nothing | stdlib |
| **Events** | Engine-wide event primitives | EventBus, ScopedSubscription, CancellableEvent, event structs | Nothing event-dispatch-wise above Core | stdlib |
| **Platform** | OS integration, input, window | Window, Input, event polling | Rendering, entities, camera | GLFW, OpenGL headers |
| **Renderer** | GPU state, draw batching, shader ops, debug primitives, camera math | Shader, Texture, Camera2D, QuadCommand, LineCommand, PointCommand, batch state | Scene, Lua, input | OpenGL, Core, Platform |
| **Resources** | Asset loading, caching, pooling | ResourcePool<T>, asset loading logic | Lifetime of entities/scripts | Core, Renderer |
| **Scene** | Entity model, ECS, hierarchy | Entity, Registry, Components, Scene, hierarchy | Rendering pipeline, Lua | Core, Events, Resources |
| **Physics** | Box2D integration, body/fixture lifecycle, contact callbacks | PhysicsWorld, ContactListener, fixture zone mapping | ECS orchestration order, rendering | Box2D, Core, Scene |
| **Scripting** | Lua VM lifecycle, bindings, script state | ScriptEngine, Lua VM, EngineAPI bindings | Scene data directly | Lua library, Core, Scene, Physics, Resources |
| **UI** | XML layout documents, binding context, UI rendering | UISystem, UIDocument, UIBindingContext, UIResources | Game logic, physics | Events, Platform, Renderer, Resources, Scene, Scripting |
| **Systems** | Game loop, orchestration, frame systems | TransformSystem, ScriptSystem, RenderSystem, PhysicsSystem, AudioSystem, AnimationSystem, StateMachineSystem, Runtime | Direct scene modification (via systems) | Everything (orchestrator) |

---

## Part 2: Detailed Component & System Design

### 2.1 Entity-Component-System (ECS) Architecture

#### Entities
- **What**: Lightweight integer IDs wrapping entt::entity
- **Created By**: `Scene::createEntity()` or Lua `Engine.createEntity()`
- **Lifetime**: Tracks creation frame, valid until destroyed
- **Hierarchy**: Belongs to parent/children lists in Scene

#### Components
All components follow these rules:
1. **Encapsulated data** - fields are private where setter-side-effects (e.g. dirty flags) must be maintained; use public getters/setters in those cases
2. **No logic** - behavior lives in Systems
3. **Default-constructible** - `Component c = Component();` works
4. **Serializable** - to/from Lua tables and JSON
5. **POD-like** - prefer glm types, primitives, std::string

#### Registry
Central component storage owned by Scene.

**Supported Operations:**
```cpp
// Entity management
Entity entity = registry.create();
registry.destroy(entity);
bool exists = registry.valid(entity);

// Component operations
registry.emplace<T>(entity, ...args);
registry.remove<T>(entity);
T& comp = registry.get<T>(entity);
bool has = registry.has<T>(entity);

// Queries
auto view = registry.view<TransformComponent>();
auto view2 = registry.view<TransformComponent, SpriteRenderer>();
for (auto entity : view) { ... }
```

#### Hierarchy Model
```
Entity Relationships:
- Stored in Scene, not in components
- Parent/child maps maintained by Scene
- Destroying parent recursively destroys children
- Reparenting updates both maps

Transform Hierarchy:
- Local transform in TransformComponent
- World transform computed by TransformSystem each frame
- Iterative DFS walk (not recursive) for performance
- Dirty flag optimization: only recompute changed subtrees
```

### 2.2 Component Types (Currently Implemented)

**TransformComponent**
```cpp
namespace sle::components {
struct TransformComponent {
    // Private: use setters to maintain invariants
    
    // Getters/Setters
    glm::vec2 getPosition() const;
    void setPosition(glm::vec2 pos);
    
    float getRotation() const;
    void setRotation(float rot);
    
    glm::vec2 getScale() const;
    void setScale(glm::vec2 s);
    
    Entity getParent() const;
    
private:
    glm::vec2 position{0.0f};
    float rotation = 0.0f;
    glm::vec2 scale{1.0f, 1.0f};
    Entity parent = entt::null;
    bool isDirty = true; // Recompute on change
};
}
```

**WorldTransformComponent**
```cpp
namespace sle::components {
struct WorldTransformComponent {
    glm::mat4 matrix = glm::mat4(1.0f);
    // This is READ-ONLY, computed by TransformSystem each frame
};
}
```

**SpriteRenderer**
```cpp
namespace sle::components {
struct SpriteRenderer {
    glm::vec4 color{1.0f};           // RGBA tint
    glm::vec4 uvRect{0.0f, 0.0f, 1.0f, 1.0f}; // texture coords
    uint32_t renderLayer = 0;        // Sort key (0 = back)
    std::string textureAsset;        // Path to texture
    std::string shaderAsset;         // Path to shader
};
}
```

**ScriptComponent**
```cpp
namespace sle::components {
struct ScriptComponent {
    std::string scriptAsset;             // Path to .lua file
    bool enabled = true;                 // Enable/disable
    
    // Internal: Lua callback references (NOT user-facing)
    uint32_t luaRefInit = LUA_NOREF;    
    uint32_t luaRefUpdate = LUA_NOREF;  
    uint32_t luaRefDestroy = LUA_NOREF; 
    uint32_t luaRefData = LUA_NOREF;    // Script's userdata table
    
    bool initialized = false;
};
}
```

**RigidBodyComponent**
```cpp
namespace sle::components {
enum class BodyType : uint8_t { Static, Dynamic, Kinematic };

struct RigidBodyComponent {
    BodyType bodyType = BodyType::Dynamic;
    float mass = 1.0f;
    float linearDamping = 0.0f;
    float angularDamping = 0.0f;
    float gravityScale = 1.0f;
    glm::vec2 velocity{0.0f, 0.0f};
    float angularVelocity = 0.0f;
    bool fixedRotation = false;
    bool enabled = true;
    uint32_t box2dBodyId = 0; // Internal Box2D handle
};
}
```

**Collider/Zone Components**
```cpp
// Solid collision
struct BoxColliderComponent { glm::vec2 offset, size; float friction, restitution, density; uint16_t categoryBits, maskBits; bool enabled; uintptr_t box2dFixtureId; };
struct CircleColliderComponent { glm::vec2 offset; float radius, friction, restitution, density; uint16_t categoryBits, maskBits; bool enabled; uintptr_t box2dFixtureId; };

// Sensor triggers (no physical response)
struct BoxZoneComponent { glm::vec2 offset, size; std::string zoneId; bool enabled; uint16_t categoryBits, maskBits; uintptr_t box2dFixtureId; };
struct CircleZoneComponent { glm::vec2 offset; float radius; std::string zoneId; bool enabled; uint16_t categoryBits, maskBits; uintptr_t box2dFixtureId; };
```

---

### 2.3 Transform System (Verified Implementation)

**Responsibility**: Compute world-space transforms from local transforms.

**Algorithm**:
```
1. Iterate over all root entities (no parent)
2. For each root: recursive walk (iterative DFS)
   - If dirty: recompute local matrix
   - Multiply by parent's world matrix
   - Write to WorldTransformComponent
   - Add children to stack if dirty (propagate)
3. Only recompute changed branches (dirty flag optimization)
```

**Performance Note**: Recently optimized from recursive DFS to iterative using explicit stack. Reduces function call overhead for large hierarchies.

**Files**: `Systems/include/sle/systems/TransformSystem.hpp`, `Systems/src/TransformSystem.cpp`

---

### 2.4 Render System (Verified Implementation)

**Responsibility**: Convert scene data into render commands.

**Pipeline**:
```
1. Query all entities with (WorldTransformComponent, SpriteRenderer)
2. For each entity:
   a. Frustum cull against camera viewport
   b. If visible:
      - Build model matrix from WorldTransform
      - Extract texture/shader/layer/color from SpriteRenderer
      - Create QuadCommand
      - Submit to Renderer
3. Renderer batches commands by (layer, shader, texture)
4. GPU uploads and draws in batch order
5. If physics debug is enabled:
    - Query collider and zone/area components
    - Submit line/point debug commands for outlines + centers
```

**Command Structure**:
```cpp
struct QuadCommand {
    glm::mat4 modelMatrix;
    glm::vec4 color;
    glm::vec4 uvRect;
    uint32_t textureHandle;
    uint32_t shaderHandle;
};

struct BatchKey {
    uint32_t layer;
    uint32_t shaderHandle;
    uint32_t textureHandle;
    
    bool operator<(const BatchKey& other) const {
        // Sort: layer → shader → texture
    }
};
```

**Culling**: Conservative frustum test before submission (reduces GPU load).

**Physics Debug Rendering (implemented)**:
- Toggled at runtime (`F3`) and exposed through Script API
- Draws collider outlines in cyan (`BoxColliderComponent`, `CircleColliderComponent`)
- Draws zone/area outlines in amber (`BoxZoneComponent`, `CircleZoneComponent`)
- Uses high debug layers so overlays render above gameplay sprites

**Files**: `Systems/include/sle/systems/RenderSystem.hpp`, `Systems/src/RenderSystem.cpp`

---

### 2.5 Script System (Verified Implementation)

**Responsibility**: Execute Lua update callbacks each frame.

**Lifecycle**:
```
1. Entity created with ScriptComponent
   → ScriptComponent.initialized = false

2. First frame (ScriptSystem::update):
   → Load script asset from disk (via Resources)
   → Extract init(entity), update(entity, dt), destroy(entity) functions
   → Call init(entity) immediately
   → ScriptComponent.initialized = true

3. Each frame (if enabled):
   → Call update(entity, deltaTime)

4. Entity destroyed:
   → Call destroy(entity) if set
   → Release Lua references
```

**ScriptApi Boundary**:
```
Lua Scripts ← (EngineAPI functions) ← ScriptApiImpl
                                         ↑
                                    (implements)
                                         ↑
                                    ScriptApi (abstract)
                                         ↑
                                    (called by)
                                    Runtime (owns Context)
```

**Files**: `Systems/include/sle/systems/ScriptSystem.hpp`, `Systems/src/ScriptSystem.cpp`

---

### 2.6 Physics System (Verified Implementation)

**Responsibility**: Synchronize ECS transforms/components with Box2D and step simulation.

**Update Flow**:
```
1. Create bodies/fixtures for entities that gained RigidBody/collider/zone components
2. Sync dirty TransformComponent -> Box2D body transform
3. Step PhysicsWorld using fixed timestep accumulator
4. Sync Box2D body state -> TransformComponent and RigidBody velocity fields
5. Destroy stale Box2D bodies for removed entities/components
```

**PhysicsWorld Features (implemented)**:
- Fixed timestep stepping with accumulator
- World<->physics unit conversion helpers (`pixelsPerMeter = 50`)
- Body creation/destruction and velocity/force/impulse APIs
- Collider fixtures (box/circle) and sensor zones (box/circle)
- Contact listener dispatching collision and zone enter/exit events via EventBus

**Files**: `Physics/include/sle/physics/PhysicsWorld.hpp`, `Physics/src/PhysicsWorld.cpp`, `Physics/src/ContactListener.cpp`, `Systems/src/PhysicsSystem.cpp`

---

## Part 3: Lua Scripting Integration

### 3.1 Single Lua VM Model (Verified)

**Why One VM?**
- Industry standard (Godot, LÖVE, etc.)
- Scripts can call shared helper functions
- Simpler state management
- No per-entity overhead

**VM Lifetime:**
```
Engine Init
  → ScriptEngine::init() creates lua_State
  → luaL_openlibs() loads standard library
  → registerLuaBindings() registers Engine API
  → ScriptEngine::run() executes scripts each frame
Engine Shutdown
  → ScriptEngine::shutdown() destroys lua_State
```

**Files**: `Scripting/include/sle/scripting/ScriptEngine.hpp`, `Scripting/src/ScriptEngine.cpp`

### 3.2 ScriptApi Interface (Verified)

**Pattern**: Abstract interface in Scripting module, concrete implementation in Systems/Runtime.

**Purpose**: Isolate Lua bindings from implementation details; allow different backends (future).

**Current Implementation** (`ScriptApiImpl`):

```cpp
class ScriptApiImpl : public ScriptApi {
public:
    // Entity management
    Entity createEntity() override;
    void destroyEntity(Entity entity) override;
    bool isEntityAlive(Entity entity) override;
    
    // Transform access
    glm::vec2 getTransformPosition(Entity entity) override;
    void setTransformPosition(Entity entity, glm::vec2 pos) override;
    glm::vec2 getTransformScale(Entity entity) override;
    void setTransformScale(Entity entity, glm::vec2 s) override;
    
    // Hierarchy
    void setParent(Entity child, Entity parent) override;
    Entity getParent(Entity child) override;
    uint32_t getChildCount(Entity parent) override;
    void destroyChildren(Entity parent) override;
    
    // Input queries
    bool isKeyPressed(int keyCode) override;
    bool isKeyReleased(int keyCode) override;
    bool isMouseButtonPressed(int button) override;
    
    // Camera
    void setCameraPosition(glm::vec2 pos) override;
    glm::vec2 getCameraPosition() override;
    
    // Resources
    void attachTexture(Entity entity, const std::string& assetPath) override;
    void attachShader(Entity entity, const std::string& assetPath) override;
    
    // Scene switching
    void switchScene(const std::string& sceneName) override;
    
    // Logging
    void log(const std::string& message) override;
    
private:
    Runtime* runtime;  // Access to all services
};
```

**Files**: `Scripting/include/sle/scripting/ScriptApi.hpp`, `Systems/src/ScriptApiImpl.cpp`

### 3.3 Lua Bindings (Verified)

**File**: `Scripting/src/LuaBindings.cpp`

**Registration Pattern**:
```cpp
lua_register(L, "Engine.createEntity", lua_createEntity);
lua_register(L, "Engine.destroyEntity", lua_destroyEntity);
// ... ~25+ functions exposed
```

**Lua Access Pattern**:
```lua
-- In scripts
local entity = Engine.createEntity()
Engine.setTransformPosition(entity, 100, 200)
Engine.attachTexture(entity, "assets/textures/player.png")
Engine.attachShader(entity, "assets/shaders/quad.vert")
```

**Script Lifecycle Example**:
```lua
-- assets/scripts/player.lua

local state = {
    speed = 100,
    health = 100
}

function init(entity)
    -- Called once at entity creation
    Engine.attachTexture(entity, "assets/textures/player.png")
    Engine.setTransformScale(entity, 2.0, 2.0)
end

function update(entity, dt)
    -- Called every frame
    if Engine.Input.isKeyPressed(Engine.Keys.W) then
        local x, y = Engine.getTransformPosition(entity)
        Engine.setTransformPosition(entity, x, y - state.speed * dt)
    end
end

function destroy(entity)
    -- Called when entity is destroyed
    LOG("Entity destroyed: " .. entity)
end
```

---

## Part 4: Rendering Pipeline (Verified)

### 4.1 Two-Layer Rendering Architecture

**Layer 1: RenderSystem (Scene Aware)**
- Queries entities with `(WorldTransformComponent, SpriteRenderer)`
- Frustum culls against camera
- Builds `QuadCommand` objects
- Submits to Renderer
- Optionally submits physics debug overlays (colliders + zones/areas) as line/point commands

**Layer 2: Renderer (Decoupled)**
- Accepts commands (no scene knowledge)
- Batches by `(layer, shader, texture)`
- Streams to GPU via ping-pong VBOs
- Submits batches to OpenGL

**Benefit**: Renderer is reusable; can be tested without Scene/Lua.

### 4.4 Physics Debug Overlay (Verified)

When debug rendering is enabled, the render pass includes additional command streams:

```
- Collider overlays (solid collision):
    BoxColliderComponent + CircleColliderComponent -> cyan outlines

- Area overlays (sensor zones):
    BoxZoneComponent + CircleZoneComponent -> amber outlines

- Layering:
    Collider debug layer < Zone/area debug layer
    Both debug layers render above regular sprite layers
```

This provides immediate visual validation of collision geometry and trigger-area boundaries.

### 4.2 GPU Optimization Strategy

**Instanced Rendering**:
```
- All quads use same VAO and mesh (2 triangles)
- Instance data (model matrix, color, UVs) streamed each frame
- Reduces draw calls dramatically
```

**Batch Ordering**:
```
Sort batches by: Layer (ascending) → Shader → Texture
Result: Minimize state changes, maximize GPU efficiency
```

**Ping-Pong VBO Upload**:
```
Frame N-1:
  CPU → VBO_A (double buffer)
         ↓
       GPU reads VBO_B
       
Frame N:
  CPU → VBO_B (swap)
         ↓
       GPU reads VBO_A
       
Benefit: No stall; CPU and GPU work in parallel
```

**Frustum Culling**:
```
Before submission:
  if (sprite.worldBounds outside camera.viewport) {
      skip submission  // Reduces GPU load
  }
```

### 4.3 Renderer API (Verified)

**Files**: `Renderer/include/sle/renderer/Renderer.hpp`, `Renderer/src/Renderer.cpp`

```cpp
class Renderer {
public:
    bool init(uint32_t screenWidth, uint32_t screenHeight);
    void shutdown();
    
    void beginFrame();
    void submitQuad(const QuadCommand& cmd);
    void endFrame();  // Batches and uploads
    
    void setCamera(const Camera2D& camera);
    void setViewport(uint32_t width, uint32_t height);
};
```

---

## Part 5: Game Loop (Verified)

### 5.1 Frame Execution Order (Current Implementation)

```cpp
// Runtime::run() main loop (simplified)

while (isRunning) {
    // 1. Scene management
    SceneManager::processPendingSwitch();

    // 2. Input (clears pressed/released state before new polling)
    Input::update();

    // 3. Timing
    Timer::tick();
    float dt = Timer::getDeltaTime();

    // 4. Window events + resize handling
    Window::pollEvents();
    if (windowResized) camera.setViewport(width, height);
    if (escapePressed || windowClosed) break;

    // 5. Build per-frame Context
    Context ctx{ scene, registry, eventBus, globalBus, renderer, camera, physicsWorld, dt };

    // 6. Flush deferred event queues from previous frame
    ctx.eventBus.flushQueue();
    ctx.globalBus.flushQueue();

    // === LOGIC UPDATE PHASE ===
    // 7. Animation update (before transform so animated locals reflect in same frame)
    AnimationSystem::update(scene, dt);

    // 8. Audio update (play/stop request processing)
    AudioSystem::update(scene);

    // 9. Transform update (compute world-space matrices)
    TransformSystem::update(ctx);

    // 10. Script update (run Lua update() callbacks)
    ScriptSystem::update(ctx);

    // 11. State machine update (evaluate transitions and Lua guards)
    StateMachineSystem::update(scene, dt, &scriptEngine);

    // 12. Physics update (step Box2D, sync transforms, drain contact events)
    PhysicsSystem::update(ctx);

    // === RENDER PHASE ===
    // 13. Begin GPU frame
    renderer.beginFrame();

    // 14. Submit sprite/physics-debug render commands
    RenderSystem::update(ctx);

    // 15. Submit UI documents
    UISystem::update(uiCtx);

    // 16. Flush batches to GPU
    renderer.endFrame();

    // 17. Present
    window.swapBuffers();
}
```

**Frame Time Budget** (typical):
- Input: ~0.1ms
- TransformSystem: ~1ms (500 entities)
- ScriptSystem: ~2ms (100 scripts)
- PhysicsSystem: ~1ms
- RenderSystem: ~0.5ms (culling + command generation)
- GPU Upload: ~2-5ms (depends on sprite count, batching efficiency)
- **Total**: 7-11ms per frame @ 60 FPS (leaves headroom)

---

## Part 6: Context & Dependency Injection

### 6.1 Context Struct

All systems receive a single `Context` struct with shared frame services (references + physics pointer):

```cpp
struct Context {
    sle::entity::Scene& scene;
    sle::entity::Registry& registry;
    sle::events::EventBus& eventBus;        // Per-scene event bus
    sle::events::EventBus& globalBus;       // Engine-wide event bus (scene load/unload)
    sle::renderer::Renderer& renderer;
    const sle::core::Camera2D& camera;
    sle::physics::PhysicsWorld* physicsWorld;
    float dt;
};
```

**Benefits**:
- Systems don't need to know how to construct dependencies
- Easy to test (mock Context)
- Decouples systems from Runtime initialization order
- Can swap implementations (e.g., mock camera for testing)

### 6.2 Module Communication Patterns

**Pattern 1: Via Context**
```cpp
// RenderSystem just receives context
void RenderSystem::update(const Context& ctx) {
    auto view = ctx.registry.view<SpriteRenderer>();
    for (auto entity : view) {
        ctx.renderer.submitQuad(cmd);
    }
}
```

**Pattern 2: Via Abstract Interface**
```cpp
// ScriptSystem calls abstract ScriptApi
class ScriptSystem {
    void update(const Context& ctx) {
        // Script engine is owned by Runtime and injected into ScriptSystem.
        // Context still carries frame data and ECS access needed by updates.
    }
};
```

**Pattern 3: Via Public Module API**
```cpp
// Scene::createEntity() is public; called by Runtime or Lua
Entity entity = scene.createEntity();
scene.addComponent<TransformComponent>(entity);
```

---

## Part 7: Asset Management & Caching

### 7.1 Resources Module (Verified)

**Pattern**: Type-indexed, path-based deduplication.

```cpp
class Resources {
public:
    // Load or get from cache
    Texture* getTexture(const std::string& path);
    Shader* getShader(const std::string& path);
    
    // Called by ScriptComponent init
    ScriptAsset* getScript(const std::string& path);
    
private:
    ResourcePool<Texture> textures;
    ResourcePool<Shader> shaders;
    ResourcePool<ScriptAsset> scripts;
};

template<typename T>
class ResourcePool {
private:
    std::unordered_map<std::string, std::unique_ptr<T>> cache;
    
public:
    T* get(const std::string& path) {
        if (cache.count(path)) return cache[path].get();
        return load(path);  // Load and cache
    }
};
```

**Benefit**: No duplicate asset loads; all references share same GPU memory.

---

## Part 8: Verification Against Actual Code

### 8.1 Documentation Accuracy Checks

| Aspect | Documented | Code Matches | Notes |
|--------|-----------|--------------|-------|
| Module chain | Core→Platform→Renderer→Resources→Scene→Physics→Scripting→Systems→Sandbox | ✅ Yes | EngineModules/CMakeLists.txt verifies |
| ECS pattern | Pure data components, Registry views | ✅ Yes | Uses entt library |
| Transform pipeline | Local (component) → World (computed) → System reads | ✅ Yes | Iterative DFS implementation |
| Lua VM | Single global per engine | ✅ Yes | ScriptEngine owns lua_State |
| Frame loop order | Input→Transform→Script→Physics→Render | ✅ Yes | Runtime::run() implementation |
| Physics debug toggle | Runtime toggle + script exposure | ✅ Yes | F3 in Runtime + Script API methods |
| Batching | (layer, shader, texture) ordering | ✅ Yes | Renderer::endFrame() sorts |
| GPU upload | Ping-pong VBOs, stream strategy | ✅ Yes | Verified in Renderer code |
| Scripting API | ~25 functions via ScriptApi interface | ✅ Yes | LuaBindings.cpp registration |
| Physics module | Box2D world/body/fixture integration | ✅ Yes | PhysicsWorld + ContactListener |
| Physics ECS sync | RigidBody + collider/zone creation and sync | ✅ Yes | PhysicsSystem::update() |
| Physics script API | force/impulse/velocity/raycast/debug bindings | ✅ Yes | ScriptApi + LuaBindingsPhysics |
| Physics debug rendering | Collider + zone/area overlay pass | ✅ Yes | RenderSystem debug views + line/point commands |
| Area events | Zone enter/exit event dispatch | ✅ Yes | ContactListener emits ZoneEnterEvent/ZoneExitEvent |
| Hierarchy | Parent/child maps, reparenting support | ✅ Yes | Scene::setParent() maintains |
| Culling | Frustum test before render submit | ✅ Yes | RenderSystem::update() |

### 8.2 Known Limitations & TODOs

| Item | Status | Plan |
|------|--------|------|
| Physics integration | Implemented (Box2D world, colliders, zones, events, Lua API) | Future: joints, constraints extensions, richer debug tooling |
| Physics debug rendering | Implemented (collider + area overlays, runtime toggle) | Future: labels, normals, contact point visualization |
| Audio | Headers present (miniaudio) but not integrated | Audio system design pending |
| Animation | No component yet | AnimationComponent + AnimationSystem design needed |
| Serialization | Entity data serializable to JSON | Scene save/load not yet exposed |
| Hot-reload scripts | Framework present, not exposed in Sandbox | Hot-reload on file change future work |
| Multi-scene | Scene switching logic present | Full scene preloading WIP |

---

## Part 9: Key Decision Rationale

### 9.1 Strict Layering Over Circular Dependencies

**Decision**: Enforce one-way dependencies.

**Rationale**:
- Enables independent testing (Core tested without Renderer)
- Prevents tangled design (Renderer + Scene + Lua tightly coupled if circular)
- Supports reuse (Core/Renderer usable in other projects)
- Clear ownership (no ambiguity about who owns a feature)

**Cost**: Dependency injection required; slightly more code boilerplate.

### 9.2 Pure ECS Over OOP Hierarchies

**Decision**: No behavior on entities; all logic in systems.

**Rationale**:
- Data locality improves cache performance
- Composability: any entity can have any component combination
- Easy parallelization: systems iterate independent component views
- Easy serialization: components are just data structs

**Cost**: More explicit loops; less OOP-style polymorphism.

### 9.3 Single Lua VM Over Per-Entity VMs

**Decision**: One global VM, per-entity state in Lua userdata.

**Rationale**:
- Standard approach (industry norm)
- Shared helper functions across scripts
- Lower memory overhead
- Easier debugging (one VM state to inspect)

**Cost**: Less isolation between scripts; one bad script can crash the VM.

### 9.4 Command-Based Rendering Over Direct API Calls

**Decision**: RenderSystem emits QuadCommand objects; Renderer consumes them.

**Rationale**:
- Decoupling: Renderer doesn't know about Scene/Lua
- Late batching: Sort commands before GPU upload (better optimization)
- Extensibility: New render types = new command types
- Testing: Commands are inspectable/replayable

**Cost**: Extra indirection; commands allocated each frame (mitigated by pooling).

---

## Part 10: Development Workflows

### 10.1 Adding a New Component

**Checklist**:
1. Define struct in `Scene/include/sle/scene/components/`
   - Data-only, public members
   - Default-constructible
   - Add Lua serialization function (if needed in scripts)
2. Update `Scene/include/sle/scene/Scene.hpp` (if component public API)
3. Add to any systems that interact with it (via Registry view)
4. Test in Sandbox script
5. Update `COMPONENT_SYSTEM_GUIDE.md` with serialization pattern

**Example**: Adding `VelocityComponent` for physics

```cpp
// Scene/include/sle/scene/components/Velocity.hpp
namespace sle::components {
struct VelocityComponent {
    glm::vec2 velocity{0.0f};
    glm::vec2 acceleration{0.0f};
};
}

// Scene/include/sle/scene/Scene.hpp (public API)
void addVelocity(Entity entity, glm::vec2 v) {
    registry.emplace<VelocityComponent>(entity).velocity = v;
}

// Systems/src/PhysicsSystem.cpp (integrate in frame loop)
void PhysicsSystem::update(const Context& ctx) {
    auto view = ctx.scene->getRegistry().view<TransformComponent, VelocityComponent>();
    for (auto entity : view) {
        auto& pos = ctx.scene->getComponent<TransformComponent>(entity);
        auto& vel = view.get<VelocityComponent>(entity);
        pos.setPosition(pos.getPosition() + vel.velocity * dt);
    }
}
```

### 10.2 Adding a Lua API Function

**Checklist**:
1. Add method to `ScriptApi` interface (`Scripting/include/sle/scripting/ScriptApi.hpp`)
2. Implement in `ScriptApiImpl` (`Systems/src/ScriptApiImpl.cpp`)
3. Bind in `LuaBindings.cpp` via `lua_register()`
4. Document in `SCRIPTING_CURRENT.md` API reference
5. Test in Sandbox script

**Example**: Adding `Engine.getEntityHealth(entity)`

```cpp
// Scripting/include/sle/scripting/ScriptApi.hpp
class ScriptApi {
public:
    virtual float getEntityHealth(Entity entity) = 0;
};

// Systems/src/ScriptApiImpl.cpp
float ScriptApiImpl::getEntityHealth(Entity entity) {
    if (!runtime->getScene().hasEntity(entity)) return 0.0f;
    auto& health = runtime->getScene().getComponent<HealthComponent>(entity);
    return health.currentHealth;
}

// Scripting/src/LuaBindings.cpp
static int lua_getEntityHealth(lua_State* L) {
    Entity entity = (Entity)lua_tonumber(L, 1);
    float health = api->getEntityHealth(entity);
    lua_pushnumber(L, health);
    return 1;
}

lua_register(L, "Engine.getEntityHealth", lua_getEntityHealth);
```

### 10.3 Creating a New System

**Checklist**:
1. Define in `Systems/include/sle/systems/`
   - Receive `Context` parameter
   - Query via Registry views (don't iterate entities directly)
   - Issue commands (Render, Input, etc.), don't modify Scene mid-iteration
2. Implement in `Systems/src/`
3. Call from `Runtime::run()` at appropriate point in frame
4. Update `IMPLEMENTATION_OVERVIEW.md` frame loop section
5. Test with Sandbox

**Example**: Adding `AnimationSystem`

```cpp
// Systems/include/sle/systems/AnimationSystem.hpp
namespace sle::systems {
class AnimationSystem {
public:
    void update(const Context& ctx);
};
}

// Systems/src/AnimationSystem.cpp
void AnimationSystem::update(const Context& ctx) {
    auto view = ctx.scene->getRegistry().view<AnimationComponent>();
    for (auto entity : view) {
        auto& anim = view.get<AnimationComponent>(entity);
        anim.currentFrame += (anim.frameRate * ctx.timer->getDeltaTime());
        if (anim.currentFrame >= anim.totalFrames) {
            anim.currentFrame = 0;  // Loop
        }
    }
}

// Systems/src/Runtime.cpp
// In Runtime::run():
AnimationSystem::update(ctx);  // After TransformSystem, before RenderSystem
```

---

## Part 11: Performance Characteristics

### 11.1 Measured Performance (From OPTIMIZATIONS_CURRENT.md)

| Scenario | Time | CPU/GPU | Notes |
|----------|------|---------|-------|
| 1,000 entities (static) | ~2ms | CPU | Transform walk, no scripts |
| 100 scripted entities | ~3ms | CPU | Lua update() calls |
| 2,000 sprite render | ~5ms | GPU upload | Batching, ping-pong VBO |
| Frustum culling (50% off-screen) | ~0.3ms | CPU | Reduces GPU load 50% |

### 11.2 Optimization Techniques Applied

1. **Dirty Flag Transforms**: Only recompute changed subtrees
2. **Iterative DFS**: Replace recursive with explicit stack (reduce call overhead)
3. **Frustum Culling**: Skip off-screen sprites before GPU submission
4. **Batch Sorting**: Group by layer/shader/texture (minimize state changes)
5. **Ping-Pong VBOs**: Parallel CPU/GPU execution (no stalls)
6. **Resource Pooling**: No duplicate GPU memory for same asset

---

## Part 12: Extending the Engine

### 12.1 Common Extensions

**Adding a New Render Effect**:
- New command type (or extend QuadCommand)
- New shader
- Update RenderSystem to populate new fields
- Update Renderer batching if needed

**Adding a Physics System**:
- New component: `RigidbodyComponent` with Box2D shape/mass
- New system: `PhysicsSystem` steps Box2D world
- New Lua API functions: `Engine.applyForce()`, etc.
- ScriptApi bridge: Connect Lua calls to Box2D operations

**Adding an Audio System**:
- New component: `AudioSourceComponent` with sound asset
- New system: `AudioSystem` plays/stops sounds
- New Lua API: `Engine.playSound()`, `Engine.stopSound()`
- External: miniaudio.h already in External/

**Adding an Animation System**:
- New component: `AnimationComponent` with frame rate, frame count
- New system: `AnimationSystem` advances frame each tick
- Integrate into SpriteRenderer UV animation

### 12.2 Extension Points (Verified)

| Point | How to Extend | Example |
|-------|---------------|---------|
| Components | Add struct, register in Registry | HealthComponent |
| Systems | Create system, call from Runtime::run() | AnimationSystem |
| Lua API | Extend ScriptApi interface, bind in LuaBindings.cpp | Engine.playSound() |
| Shaders | Add .vert/.vert in assets/shaders/, load via Resources | Custom post-process |
| Rendering | New QuadCommand variant or new batch strategy | 3D perspective overlay |

---

## Part 13: Testing Strategy

### 13.1 Test Architecture Goals

Testing is architecture, not a final checklist item.

Goals:

- Provide deterministic verification for feature logic across module boundaries.
- Keep fast feedback loops for daily development.
- Prevent regressions in runtime orchestration order (Input -> Transform -> Script -> Physics -> Render).
- Keep tests aligned to strict dependency layering.

### 13.2 Test Layers and Priority

Primary confidence should come from integration coverage, with targeted unit checks.

- Integration tests: primary investment (~70%).
- Smoke tests: runtime startup/shutdown and key behavior sanity (~20%).
- Unit tests: pure logic and high-risk edge behavior (~10%).

Rationale:

- Most regressions in this engine occur at subsystem boundaries.
- Integration tests validate real orchestration without requiring full manual playtesting.
- Unit tests stay focused and cheap by targeting deterministic pure logic.

### 13.2.1 Unit Test Applicability by Module

Use this as the default rule when deciding whether to write unit tests or integration tests.

Prefer unit tests:

- Core: math/time/result utilities and deterministic helpers
- Platform: input state transitions and camera math helpers
- Renderer: sort keys, batch grouping logic, deterministic command helpers
- Resources: cache/path resolution rules
- Scene: hierarchy and registry invariants
- Events: queue ordering and subscription lifetime semantics
- UI: layout/focus/binding helper logic

Prefer integration tests:

- Physics: Box2D stepping and contact/sensor behavior
- Scripting: full Lua lifecycle and runtime entity interaction
- Systems/Runtime: frame orchestration order across subsystems
- Renderer GPU correctness and driver-facing behavior

### 13.3 Canonical Test Structure

Use one path for all new tests:

- `tests/harness/` shared fixture helpers (scene setup, fixed-step frames, log capture)
- `tests/integration/` subsystem interaction tests
- `tests/smoke/` minimal end-to-end runtime checks
- `tests/unit/` focused pure-logic tests
- `tests/data/` deterministic test assets/scripts/layout docs

Naming:

- `integration_<subsystem>_<scenario>.cpp`
- `smoke_<feature>.cpp`
- `unit_<module>_<behavior>.cpp`

### 13.4 CMake/CTest Contract

Wire tests through CMake with CTest labels so execution is predictable.

- Labels: `unit`, `integration`, `smoke`.
- Subsystem labels: `physics`, `scripting`, `events`, `ui`, `renderer`.

Expected command flow:

- integration gate: `ctest -L integration --output-on-failure`
- smoke gate: `ctest -L smoke --output-on-failure`
- full regression: `ctest --output-on-failure`

### 13.5 Determinism Rules

To keep tests stable and CI-friendly:

- Step fixed frame counts instead of wall-clock sleeps.
- Assert on stable state changes, event delivery, and structured log markers.
- Keep test assets minimal and versioned under `tests/data/`.
- Avoid fragile exact string snapshots of large logs.

### 13.6 Required Integration Matrix

Physics:

1. fixed-step simulation yields expected body state transitions
2. zone/sensor enter and exit events occur exactly once per transition
3. create-destroy lifecycle leaves no stale body/fixture handles

Scripting:

1. script lifecycle order `init -> update -> destroy` is correct
2. `ScriptApi` read/write component operations mutate expected scene state
3. script runtime error path reports failure without crashing frame loop

Events:

1. physics callback emissions are deferred and dispatched safely
2. listeners persist across frames unless explicitly unsubscribed
3. scene teardown clears subscriptions intentionally and prevents leaks

UI:

1. XML document load and data binding initialize expected runtime values
2. keyboard focus navigation order is deterministic
3. UI interaction callbacks bridge correctly to scripting/native handlers

Renderer:

1. render submission ordering respects layer plus stable tie-break behavior
2. fallback shader/texture paths are non-crashing and observable
3. debug overlay command emission toggles correctly with debug state

### 13.7 Smoke Baselines

Smoke tests should validate at least:

1. engine boot + clean shutdown path
2. one script-enabled scene update for N deterministic frames
3. one physics-enabled scene update for N deterministic frames
4. one UI-enabled scene load and interaction path
5. one renderer command path with no fatal errors

### 13.8 Feature Definition of Done (Testing)

A feature is not complete until:

- one happy-path integration test exists
- one edge/failure integration test exists
- smoke coverage is updated if runtime-visible behavior changed
- tests are label-runnable through CTest commands above

---

## Summary: Compliance Checklist for AI & Contributors

When extending SLE:

- [ ] **No new backward dependencies** — verify CMakeLists.txt dependency order
- [ ] **Components are data-only** — no methods on component structs
- [ ] **Systems via Registry views** — `auto view = registry.view<T>();`
- [ ] **Lua through ScriptApi** — extend interface, implement in ScriptApiImpl
- [ ] **Render via QuadCommand** — systems emit commands, Renderer batches
- [ ] **Context injection** — systems receive Context, not Runtime*
- [ ] **Documentation updated** — corresponding .md file matches code
- [ ] **No circular includes** — modules depend downward only
- [ ] **Hierarchy maintained** — Scene owns parent/child, not components
- [ ] **Transform read-only** — WorldTransformComponent not directly mutated
- [ ] **Required tests added** — integration plus smoke impact validated

---

**Version**: Current (May 2026)  
**Last Verified**: Full code exploration + documentation reconciliation  
**Status**: ✅ Ready for AI & human-driven development  
**Maintainer**: Architecture Authority (see ARCHITECTURE.md)
