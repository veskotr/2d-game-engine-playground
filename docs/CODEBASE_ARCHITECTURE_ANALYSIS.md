# SLE (Simple Little Engine) - Comprehensive Codebase Analysis

**Date:** May 18, 2026  
**Engine Type:** 2D Data-Driven Game Engine with Lua Scripting  
**Core Architecture:** ECS + Component System + Single Global Lua VM

---

## Table of Contents

1. [Module Structure & Dependencies](#module-structure--dependencies)
2. [Module Breakdown & Responsibilities](#module-breakdown--responsibilities)
3. [Key Classes & Data Structures](#key-classes--data-structures)
4. [Design Patterns Used](#design-patterns-used)
5. [Main Engine Loop](#main-engine-loop)
6. [Component System](#component-system)
7. [Transform Pipeline](#transform-pipeline)
8. [Lua Scripting Integration](#lua-scripting-integration)
9. [Rendering Pipeline](#rendering-pipeline)
10. [Resource Management](#resource-management)
11. [Dependency Flow Analysis](#dependency-flow-analysis)
12. [Entry Point & Execution Model](#entry-point--execution-model)

---

## 1. Module Structure & Dependencies

### 1.1 Strict One-Way Dependency Chain

```
┌─────────┐
│  Core   │  (utilities, logging, timers, config)
└────┬────┘
     │
┌────▼──────────┐
│   Platform    │  (GLFW, input, camera, window)
└────┬──────────┘
     │
┌────▼──────────┐
│   Renderer    │  (OpenGL, shaders, textures, batch rendering)
└────┬──────────┘
     │
┌────▼──────────┐
│   Resources   │  (asset management, resource pooling)
└────┬──────────┘
     │
┌────▼──────────┐
│    Scene      │  (ECS, entities, components, registry, hierarchy)
└────┬──────────┘
     │
┌────▼──────────┐
│  Scripting    │  (Lua VM, EngineAPI, script lifecycle)
└────┬──────────┘
     │
┌────▼──────────┐
│   Systems     │  (Runtime, orchestration, game loop)
└────┬──────────┘
     │
┌────▼──────────┐
│   Sandbox     │  (game application, scene builders)
└──────────────┘
```

**Key Rule:** Lower layers do NOT depend on higher layers. For example:
- Renderer does NOT know about Lua or Scene
- Scene does NOT know about Rendering or Scripting
- Platform does NOT know about anything except Core

---

## 2. Module Breakdown & Responsibilities

### 2.1 **Core Module** (`EngineModules/Core/`)

**Files:**
- `EngineConfig.hpp`: Engine configuration (width, height, vsync, etc.)
- `Log.hpp`: Logging system with format string support
- `Result.hpp`: Error handling with Result<T> type
- `Timer.hpp`: Frame timing and delta time calculation
- `EventBus.hpp`: Decoupled event communication between systems

**Responsibilities:**
- Provide utilities used by all other layers
- Central logging facility
- Error propagation via Result types
- Frame timing calculations
- Event bus for inter-system communication

**Key Types:**
```cpp
- EngineConfig: Global engine configuration
- Log: Static logging interface
- Result<T>: Success/error result type
- Timer: Frame delta time tracking
- EventBus: Publish-subscribe event system
```

---

### 2.2 **Platform Module** (`EngineModules/Platform/`)

**Files:**
- `Window.hpp`: GLFW window management
- `Input.hpp`: Keyboard and mouse input handling

**Responsibilities:**
- Create and manage game window (via GLFW)
- Handle OS input events (keyboard, mouse)
- Provide input state queries (key down, key pressed, key released)
- Track mouse position and delta movement

**Key Types:**
```cpp
- Window: Manages GLFW window lifecycle
- Input: Static input state management
- KeyState: Per-key state (down, pressed, released)
- MouseState: Mouse position and button states
- Input::Key enum: Enumerated key codes
```

**Important Pattern:** Input state is cleared BEFORE event polling, ensuring edge-triggered state (pressed/released) is consistent within a frame.

---

### 2.3 **Renderer Module** (`EngineModules/Renderer/`)

**Files:**
- `Renderer.hpp`: Main rendering orchestrator
- `RendererCommand.hpp`: Command definitions (Quad, Line, Text)
- `Shader.hpp`: OpenGL shader program wrapper
- `Texture.hpp`: OpenGL texture wrapper
- `Camera2D.hpp`: 2D orthographic camera
- `GLDebug.hpp`: OpenGL debug utilities
- `TextureRegion.hpp`: Texture atlas region definition

**Responsibilities:**
- Initialize and manage OpenGL context
- Accept render commands from systems
- Batch commands by layer, shader, and texture
- Stream instance data to GPU using ping-pong VBOs
- Submit batches to OpenGL in order
- Manage camera for view-projection matrices

**Key Types:**
```cpp
- QuadCommand: Sprite quad with transform, color, UV, shader, texture, layer
- LineCommand: Debug line drawing
- TextCommand: Text rendering (placeholder)
- Renderer: Command submission and batching
- Camera2D: Orthographic camera with viewport and zoom
- Shader: OpenGL shader program wrapper
- Texture: OpenGL texture wrapper
```

**Rendering Pipeline:**
1. Systems submit QuadCommands to Renderer
2. Renderer collects commands into batches (keyed by layer, shader, texture)
3. Batches are sorted by key
4. Each batch's quads are converted to instances (model matrix, color, UV rect)
5. Instances are uploaded to GPU via streaming VBO
6. Batches are drawn in order

**GPU Optimization:**
- Ping-pong instance VBOs: alternates between two VBOs each frame
- Minimizes GPU-CPU stall by not stalling on write
- Per-batch culling before submission reduces unnecessary uploads

---

### 2.4 **Resources Module** (`EngineModules/Resources/`)

**Files:**
- `Resources.hpp`: Type-indexed resource pooling system

**Responsibilities:**
- Cache and manage loaded assets (textures, shaders, scripts)
- Provide deduplication of identical assets
- Handle asset lifecycle and cleanup

**Key Types:**
```cpp
- Resources: Static resource management template
- ResourcePool<T>: Type-specific pool storage
- Cached as: std::unordered_map<string, std::shared_ptr<T>>
```

**Resource Creation Pattern:**
```cpp
// Create or get cached resource
auto texture = Resources::create<Texture>("tile2", "assets/textures/tile2.png");
// If "tile2" already exists, returns existing instance
// Otherwise, creates new Texture, calls loadFromFiles("assets/textures/tile2.png"), caches it
```

**Supported Resource Types:**
- Texture (loaded via image file)
- Shader (loaded via vertex + fragment shader files)
- ScriptResource (loaded via Lua file)

---

### 2.5 **Scene Module** (`EngineModules/Scene/`)

**Files:**
- `Scene.hpp`: Entity and hierarchy management
- `Registry.hpp`: Component storage and ECS core
- `Entity.hpp`: Lightweight entity ID
- `components/Transform.hpp`: Local transformation data
- `components/WorldTransformComponent.hpp`: Computed world transform
- `components/SpriteRenderer.hpp`: Rendering data
- `components/ScriptComponent.hpp`: Lua script binding

**Responsibilities:**
- Manage entity lifetime and creation/destruction
- Maintain parent-child hierarchy
- Store and access components via Registry
- Provide iteration over entities with specific components
- Emit events for entity creation/destruction
- Track dirty flags for transform propagation

**Key Types:**
```cpp
- Entity: Lightweight ID (uint32_t wrapper)
- Scene: Entity and hierarchy manager
- Registry: Component storage with sparse-set pools
- ComponentPool<T>: Type-specific component storage
- TransformComponent: Local transform with dirty flag
- WorldTransformComponent: Computed world transform (read-only)
- SpriteRenderer: Rendering data (color, texture, layer)
- ScriptComponent: Script asset path and Lua callback references
```

**Hierarchy Model:**
- Each entity can have one parent (or be a root)
- Each entity can have multiple children
- Scene::setParent() maintains parent/child maps
- Scene::destroyEntity() recursively destroys all descendants
- Hierarchy is separate from component data (stored in Scene)

**Component System Design:**
- All components are pure data (no methods)
- Components are default-constructible and copy-able
- ComponentPool uses type erasure for generic storage
- Registry::view<T>() iterates all entities with component T
- Registry::view<T1, T2>() iterates all entities with both T1 and T2

---

### 2.6 **Scripting Module** (`EngineModules/Scripting/`)

**Files:**
- `ScriptEngine.hpp`: Lua VM ownership and management
- `ScriptApi.hpp`: Abstract interface for Lua-accessible functions
- `LuaBindings.hpp`: Lua C API bindings (upvalue pattern)
- `Script.hpp`: Script component definition
- `ScriptResource.hpp`: Lua script as a resource

**Responsibilities:**
- Own and manage single global Lua VM
- Load Lua scripts as resources
- Maintain per-entity script instances with separate state
- Call init(), update(dt), destroy() callbacks
- Expose EngineAPI to Lua scripts
- Handle Lua errors with proper stack traces

**Key Types:**
```cpp
- ScriptEngine: Manages Lua VM lifecycle
- ScriptApi: Abstract interface (Lua-facing functions)
- ScriptApiImpl: Concrete implementation (Systems/Runtime)
- ScriptResource: Cached Lua script file
- LuaBindings: C++ functions exposed to Lua
- ScriptInstance: Per-entity script state and references
```

**Lua VM Strategy:**
- Single global VM (not per-scene, not per-entity)
- Simplifies implementation and debugging
- Standard in game industry (Godot, LÖVE)
- Per-entity script instances maintain separate state tables

**Script Lifecycle:**
1. Entity created with ScriptComponent
2. ScriptEngine loads .lua file as ScriptResource
3. Lua script is executed, functions extracted
4. init(entityID) is called once
5. Each frame: update(entityID, dt) is called
6. On entity destruction: destroy(entityID) is called

**EngineAPI Exposure:**
- All engine functions exposed via global "Engine" table
- Includes: entity creation, component access, input, camera, scenes, logging
- Type-safe crossing of Lua ↔ C++ boundary
- Lua can create/destroy entities, access components, manipulate hierarchy

---

### 2.7 **Systems Module** (`EngineModules/Systems/`)

**Files:**
- `Runtime.hpp/cpp`: Main game loop orchestrator
- `Context.hpp`: Per-frame execution context
- `TransformSystem.hpp/cpp`: Compute world transforms
- `ScriptSystem.hpp/cpp`: Script lifecycle management
- `PhysicsSystem.hpp/cpp`: Physics simulation (placeholder for Box2D)
- `RenderSystem.hpp/cpp`: Build render commands
- `SceneManager.hpp/cpp`: Scene loading and switching
- `ScriptApiImpl.hpp/cpp`: Concrete EngineAPI implementation

**Responsibilities:**
- Orchestrate game loop and system updates
- Resolve world-space transforms from hierarchy
- Execute Lua update callbacks
- Generate render commands
- Manage scene loading and switching
- Provide engine services to Lua scripts

**Key Types:**
```cpp
- Runtime: Main orchestrator and entry point
- Context: Per-frame context (scene, registry, renderer, camera, dt)
- TransformSystem: Hierarchy traversal and transform computation
- ScriptSystem: Script entity tracking and initialization
- RenderSystem: Entity → QuadCommand conversion
- PhysicsSystem: Placeholder for physics (Box2D integration)
- SceneManager: Scene builders and scene switching
- ScriptApiImpl: Concrete EngineAPI implementation
```

**Systems Coordination:**
- All systems receive the same Context object
- Context provides unified access to scene, registry, renderer, camera, delta time
- No global state; all dependencies passed explicitly
- Systems are stateless except for initialization parameters

---

## 3. Key Classes & Data Structures

### 3.1 Entity & Registry (ECS Core)

```cpp
// Entity: Lightweight ID
struct Entity {
    uint32_t id = 0;
    bool valid() const { return id != 0; }
};

// Registry: Component storage
class Registry {
    Entity createEntity();                      // Returns new entity with unique ID
    void destroyEntity(Entity entity);
    
    // Component management
    T& addComponent<T>(Entity entity, ...);     // Add component, returns ref
    T* getComponent<T>(Entity entity);          // Get pointer (or nullptr)
    void removeComponent<T>(Entity entity);     // Remove component
    
    // Iteration
    void view<T>(Func func);                    // For each entity with T
    void view<T1, T2>(Func func);               // For each entity with both
};
```

**Storage Implementation:**
- ComponentPool<T> per component type
- Type index-based lookup (std::type_index)
- Sparse-set pattern: maps entity ID → component data
- Lazy pool creation (created on first use)

---

### 3.2 Transform System

```cpp
// Input: Local transform data
struct TransformComponent {
    glm::vec2 position{0};
    float rotation = 0;
    glm::vec2 scale{1};
    bool dirty = true;                          // Marks branch for recompute
    Entity parent;                              // Parent entity (invalid = root)
    
    // Getters/setters (setters mark dirty=true)
    void setPosition(const glm::vec2& pos);
    void setRotation(float rot);
    void setScale(const glm::vec2& s);
};

// Output: Computed world transform
struct WorldTransformComponent {
    glm::vec2 position;
    float rotation;
    glm::vec2 scale;
};

// Algorithm: Iterative DFS traversal
// For each root entity:
//   If dirty: recompute world transform
//   For each child: propagate world transform, recurse
```

**Why Separate Components?**
- TransformComponent is mutable (entity moves, rotates, scales)
- WorldTransformComponent is read-only output (computed by system)
- Systems only read WorldTransformComponent
- Rendering reads WorldTransformComponent + SpriteRenderer
- Separation makes data flow explicit

---

### 3.3 Rendering Command System

```cpp
// Render command definition
struct QuadCommand {
    glm::mat4 modelMatrix;                      // Local-to-world transform
    glm::vec4 color{1,1,1,1};                   // RGBA tint
    glm::vec4 uvRect{0,0,1,1};                  // Texture coordinates
    uint32_t texture_id = 0;                    // OpenGL texture ID
    uint32_t shader_id = 0;                     // OpenGL shader ID
    uint32_t layer = 0;                         // Sort key (lower = back)
};

// Batch key for sorting
struct BatchKey {
    uint32_t layer;                             // Primary sort: layer
    uint32_t shader_id;                         // Secondary: shader
    uint32_t texture_id;                        // Tertiary: texture
};

// GPU instance data (minimal for streaming)
struct QuadInstance {
    glm::mat4 model;
    glm::vec4 color;
    glm::vec4 uvRect;
};
```

**Batching Strategy:**
- Commands sorted by BatchKey (layer → shader → texture)
- Each batch converted to instance buffer
- Instance data streamed to GPU using ping-pong VBOs
- All quads use same VAO and static mesh (unit square)
- Instance transforms applied in vertex shader

---

### 3.4 Lua Script State

```cpp
// Per-entity script state
struct ScriptInstance {
    std::string assetPath;                      // .lua file path
    int tableRef = LUA_REFNIL;                  // Reference to entity's state table
    int initRef = LUA_REFNIL;                   // Reference to init() function
    int updateRef = LUA_REFNIL;                 // Reference to update(dt) function
    int destroyRef = LUA_REFNIL;                // Reference to destroy() function
};

// Script lifecycle in Lua:
// 1. Asset loaded: scriptAsset = "assets/scripts/player.lua"
// 2. File executed: init(entity), update(entity, dt), destroy(entity) extracted
// 3. init() called: initializes entity for this script
// 4. Each frame: update(entity, dt) called
// 5. On destruction: destroy(entity) called, refs unref'd
```

---

## 4. Design Patterns Used

### 4.1 **Entity Component System (ECS)**

**What it is:**
- Separate data (components) from behavior (systems)
- Compose entity behavior by adding components
- Iterate over all entities with specific component combinations

**Why:** Flexible, cache-friendly, easy to extend without hierarchy

**Example:**
```cpp
// Define entity as composition of components
Entity player = scene.createEntity();
registry.addComponent<TransformComponent>(player, ...);
registry.addComponent<SpriteRenderer>(player, ...);
registry.addComponent<ScriptComponent>(player, ...);

// Render all visible entities
registry.view<WorldTransformComponent, SpriteRenderer>([](Entity e, auto& wt, auto& sr) {
    // Build render command for this entity
});
```

---

### 4.2 **Component Pattern**

**Characteristics:**
- Pure data (no methods)
- Default-constructible
- Copyable
- Serializable
- Independent (don't reference each other directly)

**Benefit:** Maximizes data locality, reduces coupling, easy to inspect

---

### 4.3 **System Pattern**

**Characteristics:**
- Stateless (or minimal state)
- Takes Context with all dependencies
- Iterates over entities with specific components
- Reads and writes components via registry
- No global state

**Example:**
```cpp
void TransformSystem::update(Context& ctx) {
    // Read TransformComponent, write WorldTransformComponent
    ctx.registry.view<TransformComponent>([](Entity e, auto& transform) {
        // Compute world transform and write to WorldTransformComponent
    });
}
```

---

### 4.4 **Dependency Injection via Context**

**What it is:**
- Each frame, create a Context struct with all system dependencies
- Pass Context to each system
- Systems access scene, registry, renderer, camera, dt through Context
- Eliminates global state

**Benefit:** Clear dependencies, easy to mock for testing, no hidden globals

```cpp
// Per-frame context creation
Context ctx{scene, registry, eventBus, renderer, camera, dt};

// Pass to systems
transformSystem.update(ctx);
scriptSystem.update(ctx);
physicsSystem.update(ctx);
renderSystem.update(ctx);
```

---

### 4.5 **Resource Pooling**

**What it is:**
- Cache loaded assets by type and ID
- Return same instance for duplicate asset requests
- Type-indexed pool storage

**Benefit:** No duplicate asset loading, centralized lifecycle

```cpp
// First call: loads from disk
auto tex = Resources::create<Texture>("tile2", "assets/textures/tile2.png");

// Second call: returns cached instance
auto tex2 = Resources::get<Texture>("tile2");  // Same object as tex
```

---

### 4.6 **Single Global Lua VM**

**What it is:**
- One Lua VM for entire engine
- Per-entity script instances maintain separate state tables
- Scripts can access shared functions via VM global table

**Why Single VM:**
- Simpler to implement and debug
- Industry standard (Godot, LÖVE)
- Script can call helper functions written in Lua
- No per-entity VM overhead
- Can add entity/scene isolation later if needed

---

### 4.7 **Upvalue Pattern for Lua Bindings**

**What it is:**
- C++ function bound to Lua with upvalues
- Upvalues allow closure-like behavior in Lua
- Can access C++ object via upvalue without global state

**Benefit:** Type-safe C++ ↔ Lua boundary, no globals needed

---

### 4.8 **Batch Command Pattern**

**What it is:**
- Render commands accumulated per frame
- Sorted by render state (layer, shader, texture)
- Submitted to GPU as batches
- Eliminates redundant state changes

**Benefit:** Reduced draw calls, better GPU utilization

---

## 5. Main Engine Loop

### 5.1 Loop Flow

```cpp
void Runtime::run() {
    while (!window.shouldClose()) {
        // === PRE-LOOP ===
        sceneManager.processPendingSwitch();    // Handle scene loading
        Input::update();                         // Clear pressed/released, poll events
        timer.tick();                            // Update delta time
        float dt = timer.getDeltaTime();
        
        window.pollEvents();                    // GLFW event polling
        
        if (Input::isKeyDown(GLFW_KEY_ESCAPE)) break;
        
        // === CREATE CONTEXT ===
        Context ctx{scene, registry, eventBus, renderer, camera, dt};
        ctx.eventBus.clear();                   // Clear events from previous frame
        
        // === LOGIC PHASE ===
        transformSystem.update(ctx);            // Compute world transforms (phase 1)
        scriptSystem.update(ctx);               // Run Lua update callbacks (phase 2)
        physicsSystem.update(ctx);              // Step physics simulation (phase 3)
        
        // === RENDER PHASE ===
        renderer.beginFrame();
        renderSystem.update(ctx);               // Build render commands (phase 4)
        renderer.endFrame();                    // Batch and submit to GPU (phase 5)
        
        window.swapBuffers();                   // Display (phase 6)
        
        // === PROFILING ===
        // Log average ms per phase every 1 second
    }
}
```

### 5.2 System Update Order

| Order | System | Purpose | Dependencies |
|-------|--------|---------|--------------|
| 1 | TransformSystem | Compute world transforms | LocalTransform → WorldTransform |
| 2 | ScriptSystem | Run Lua update callbacks | Entities must exist, transforms computed |
| 3 | PhysicsSystem | Step physics simulation | WorldTransforms, physics state |
| 4 | RenderSystem | Generate render commands | WorldTransforms, SpriteRenderer |
| 5 | Renderer.endFrame() | Batch and submit to GPU | Render commands collected |

**Key Insight:** Transform system runs FIRST because rendering depends on world transforms.

---

### 5.3 Input State Management

**Important Pattern:**
1. Input state is CLEARED at frame START (in Input::update())
2. GLFW events are POLLED after clearing (in window.pollEvents())
3. Events update state (pressed/released edge-triggered)
4. This ensures pressed/released state is valid only for the current frame

---

## 6. Component System

### 6.1 Current Components

| Component | Owner | Purpose | Mutability |
|-----------|-------|---------|-----------|
| **TransformComponent** | Entity | Local position, rotation, scale | Mutable |
| **WorldTransformComponent** | System | Computed world transform | Read-only (computed) |
| **SpriteRenderer** | Entity | Color, texture, layer | Mutable |
| **ScriptComponent** | Entity | Script asset path, Lua refs | Mostly immutable |

### 6.2 Component Patterns

**Data-Only Principle:**
```cpp
struct TransformComponent {
    // Data members only
    glm::vec2 position{0};
    float rotation = 0;
    glm::vec2 scale{1};
    bool dirty = true;
    Entity parent;
    
    // Simple getters/setters (no logic)
    void setPosition(const glm::vec2& p) { position = p; dirty = true; }
};

// NOT this (logic in component):
struct BadComponent {
    void update() { /* logic */ }  // ❌ Components should not have update logic
};
```

**Serialization Pattern:**
```cpp
// Lua representation
{
    x = 100,
    y = 200,
    rotation = 0,
    scaleX = 1,
    scaleY = 1
}

// JSON representation
{
    "position": [100, 200],
    "rotation": 0,
    "scale": [1, 1]
}
```

---

## 7. Transform Pipeline

### 7.1 Data Flow

```
TransformComponent (local data)
         ↓
   [TransformSystem.update()]
    ↓ (iterative DFS)
    - For each root: traverse hierarchy
    - Compute: parent.world × local = child.world
    - Mark branches dirty only if needed
         ↓
WorldTransformComponent (computed data)
         ↓
Used by: Rendering, Scripts, Physics
```

### 7.2 Dirty Flag Optimization

**Purpose:** Avoid recomputing transforms every frame if unchanged

**Implementation:**
1. TransformComponent has `dirty` flag
2. Setters mark `dirty = true`
3. TransformSystem only recomputes dirty branches
4. Batch update clears dirty flag after compute
5. Parent-to-child propagation applies dirty flag

**Example:**
```
Frame 1: Entity moves (setPosition) → dirty = true
         TransformSystem recomputes this entity + all descendants
         dirty = false after compute
Frame 2: Entity doesn't move
         TransformSystem skips this entity (not dirty)
         Compute cost: 0 (no branching, no matrix ops)
```

### 7.3 Hierarchy Traversal

**Algorithm: Iterative DFS**
```cpp
for (auto root : scene.getRoots()) {
    stack.push({root, parent_world_transform});
    
    while (!stack.empty()) {
        auto [entity, parentWorld] = stack.pop();
        
        if (auto transform = getComponent<TransformComponent>(entity)) {
            if (transform.isDirty()) {
                // Compute: worldTransform = parentWorld × localTransform
                auto world = parentWorld * toMatrix(transform);
                addComponent<WorldTransformComponent>(entity, world);
                transform.clearDirty();
            }
        }
        
        // Push children for traversal
        for (auto child : scene.getChildren(entity)) {
            stack.push({child, computed_world_transform});
        }
    }
}
```

**Why Iterative DFS?**
- No recursion depth limit (supports large hierarchies)
- No function call overhead (faster than recursive descent)
- Maintains hierarchy traversal correctness

---

## 8. Lua Scripting Integration

### 8.1 EngineAPI Specification

**Exposed to Lua as global "Engine" table:**

```lua
-- Engine State
Engine.getDeltaTime()           -- Returns float
Engine.getWindowSize()          -- Returns {x, y}

-- Entity Lifetime
Engine.createEntity()           -- Returns entity ID
Engine.isEntityAlive(entity)    -- Returns bool
Engine.destroyEntity(entity)    -- void
Engine.getChildCount(parent)    -- Returns uint32_t
Engine.destroyChildren(parent)  -- Returns count destroyed
Engine.setParent(child, parent) -- Returns bool
Engine.getParent(entity)        -- Returns parent entity

-- Transform
Engine.getTransformPosition(entity)  -- Returns {x, y}
Engine.setTransformPosition(entity, {x, y})
Engine.getTransformScale(entity)     -- Returns {x, y}

-- Input
Engine.Input.isKeyDown(key)
Engine.Input.isKeyPressed(key)
Engine.Input.isKeyReleased(key)
Engine.Input.getMousePosition()

-- Camera
Engine.Camera.getPosition()
Engine.Camera.setPosition({x, y})
Engine.Camera.moveCamera({dx, dy})
Engine.Camera.getZoom()
Engine.Camera.setZoom(zoom)

-- Resources
Engine.loadTexture(path)
Engine.setSpriteTexture(entity, path)

-- Scenes
Engine.hasScene(name)
Engine.switchScene(name)
Engine.getCurrentSceneName()

-- Logging
Engine.log(message)
Engine.warn(message)
Engine.error(message)

-- Key constants
Engine.Keys.W, Engine.Keys.A, Engine.Keys.S, Engine.Keys.D, etc.
Engine.MouseButtons.Left, Engine.MouseButtons.Right, etc.
```

### 8.2 Script Lifecycle

**Example Lua Script:**
```lua
-- assets/scripts/player.lua

-- Local state (persistent per entity instance)
local state = {
    speed = 120,
    health = 100
}

-- Called once when entity is created
function init(entity)
    print("Player init, entity ID: " .. entity)
    local pos = Engine.getTransformPosition(entity)
    print("Starting position: " .. pos.x .. ", " .. pos.y)
end

-- Called every frame with delta time
function update(entity, dt)
    if Engine.Input.isKeyDown(Engine.Keys.W) then
        local pos = Engine.getTransformPosition(entity)
        pos.y = pos.y + state.speed * dt
        Engine.setTransformPosition(entity, pos)
    end
    
    state.health = state.health - 0.5 * dt
end

-- Called when entity is destroyed
function destroy(entity)
    print("Player destroyed")
end

-- Return public API (optional)
return {
    getHealth = function() return state.health end,
    takeDamage = function(amount) state.health = state.health - amount end
}
```

### 8.3 Script Instance Storage

**Internal Lua Structure:**
```lua
-- Managed by ScriptEngine
sle_scripts = {
    [entity_id_1] = {
        asset = "assets/scripts/player.lua",
        init_ref = <Lua registry ref>,
        update_ref = <Lua registry ref>,
        destroy_ref = <Lua registry ref>,
        state = { /* script's local variables */ }
    },
    [entity_id_2] = { ... },
}
```

**Per-entity isolation:**
- Each entity running same script has separate state table
- Script can maintain local variables (health, ammo, etc.)
- Multiple player instances won't share health

---

## 9. Rendering Pipeline

### 9.1 Render Flow

```
1. RenderSystem iterates:
   For each entity with (WorldTransformComponent, SpriteRenderer):
     - Compute model matrix from world transform
     - Create QuadCommand with texture, color, layer
     - Submit to renderer.submit(command)

2. Renderer collects commands:
   - Store in unordered_map<BatchKey, vector<QuadCommand>>
   - BatchKey = (layer, shader, texture)

3. Sort and batch:
   - For each batch key in sorted order:
     - Convert quads to instances (model matrix, color, UV)
     - Stream instances to GPU ping-pong VBO

4. Draw batches:
   - For each batch in order:
     - Bind shader, bind texture
     - Draw instances with instanced rendering

5. Buffer swap:
   - window.swapBuffers() displays to screen
```

### 9.2 Render Culling

**Purpose:** Skip off-screen sprites before command submission

**Implementation in RenderSystem:**
```cpp
// Check if sprite is visible in camera frustum
if (isInViewport(worldTransform, spriteRenderer, camera)) {
    // Submit render command
}
```

**Benefit:** Reduces commands to renderer, reduces GPU submissions

### 9.3 GPU Instance Streaming

**Ping-Pong VBO Strategy:**
- Two VBOs: activeVBO and inactiveVBO
- Write to activeVBO
- Draw from activeVBO (GPU still working on frame N-1)
- Swap for next frame
- Avoids GPU stall waiting for data upload

**Memory:**
- Only two frame's worth of instance data allocated
- Streaming upload every frame
- No permanent large allocations

---

## 10. Resource Management

### 10.1 Resource Pooling Architecture

```cpp
// Template-based generic pooling
class Resources {
    template<typename T, typename... Args>
    static std::shared_ptr<T> create(const std::string& id, Args&&... args);
    
    template<typename T>
    static std::shared_ptr<T> get(const std::string& id);
    
    static void clear();  // Unload all resources
};

// Internal storage
std::unordered_map<std::type_index, std::unique_ptr<IResourcePool>> pools;
// Each pool: std::unordered_map<string, std::shared_ptr<T>> data;
```

### 10.2 Asset Types

| Type | Loading | Usage |
|------|---------|-------|
| **Texture** | `loadFromFiles(path)` | Sprite rendering, texture cache |
| **Shader** | `loadFromFiles(vert, frag)` | Rendering, batch grouping |
| **ScriptResource** | `loadFromFiles(path)` | Lua script loading |

### 10.3 Example: Loading a Texture

```cpp
// First call: loads from disk
auto texture = Resources::create<Texture>(
    "player_sprite",           // Resource ID
    "assets/textures/player.png"  // File path
);

// Internally:
// 1. Create new Texture object
// 2. Call texture.loadFromFiles("assets/textures/player.png")
// 3. Store in pool: pools[typeid(Texture)]["player_sprite"] = texture
// 4. Return shared_ptr

// Later call: returns cached instance
auto same = Resources::get<Texture>("player_sprite");  // Same object

// Cleanup at engine shutdown
Resources::clear();  // All textures, shaders, scripts unloaded
```

---

## 11. Dependency Flow Analysis

### 11.1 Data Flow Per Frame

```
┌─────────────────────┐
│   Window Events     │
│   Input Polling     │
└──────────┬──────────┘
           │
┌──────────▼──────────┐
│  Update Input State │
│  (pressed/released) │
└──────────┬──────────┘
           │
┌──────────▼──────────┐
│   Timer.tick()      │
│   Calculate dt      │
└──────────┬──────────┘
           │
┌──────────▼──────────┐
│  Create Frame       │
│  Context            │
└──────────┬──────────┘
           │
      ┌────▼────────────────────┐
      │                         │
┌─────▼──────┐      ┌──────────▼─────────┐
│  Transform │      │ Script & Physics   │
│  System    │      │ Systems            │
└─────┬──────┘      └──────────┬─────────┘
      │                        │
      └────────┬───────────────┘
               │
        ┌──────▼──────┐
        │ Render      │
        │ System      │
        └──────┬──────┘
               │
        ┌──────▼──────┐
        │ Renderer    │
        │ Batch & GPU │
        └──────┬──────┘
               │
        ┌──────▼──────┐
        │  Buffer     │
        │  Swap       │
        └─────────────┘
```

### 11.2 Module Dependencies at Runtime

```
┌────────────┐
│  Sandbox   │ (Application entry point)
└─────┬──────┘
      │
      ↓
┌────────────────────────┐
│  Runtime               │
│  - Window              │
│  - Renderer            │
│  - Scene               │
│  - Systems (4)         │
│  - ScriptEngine        │
│  - Camera              │
└─────┬──────────────────┘
      │
      ├─► Platform (Window, Input)
      ├─► Renderer (OpenGL, Shaders, Textures)
      ├─► Resources (Asset cache)
      ├─► Scene (Registry, Entities, Components)
      ├─► Scripting (Lua VM, EngineAPI)
      └─► Core (Logging, Timer, Config)
```

### 11.3 System Communication Flow

```
Runtime (main loop) ──┐
                      ├─► TransformSystem  ──┐
                      ├─► ScriptSystem     ├─ All receive Context
                      ├─► PhysicsSystem    ├─ (scene, registry, renderer, etc)
                      ├─► RenderSystem     ──┤
                      └─► Renderer.flush() ──┘

Context struct provides all systems with:
- scene (entity management)
- registry (component access)
- eventBus (event communication)
- renderer (render submission)
- camera (view transform)
- dt (frame delta time)
```

---

## 12. Entry Point & Execution Model

### 12.1 Startup Sequence

```cpp
// 1. Create configuration
EngineConfig config;
config.width = 480;
config.height = 270;
config.title = "My Game";

// 2. Create runtime
Runtime runtime(config);

// 3. Register scenes (lambda builders)
runtime.registerScene("main", [config](Runtime& runtime) {
    // Scene builder: create entities and components
    auto entity = runtime.getScene().createEntity();
    auto& registry = runtime.getScene().getRegistry();
    
    registry.addComponent<TransformComponent>(entity, ...);
    registry.addComponent<SpriteRenderer>(entity, ...);
    registry.addComponent<ScriptComponent>(entity, ...);
});

// 4. Initialize engine
auto result = runtime.init();
if (!result.ok()) { /* error */ }

// 5. Load startup scene
auto loadResult = runtime.loadScene("main");
if (!loadResult.ok()) { /* error */ }

// 6. Run main loop
runtime.run();
```

### 12.2 Application Initialization

**In Runtime::init():**
1. Create GLFW window
2. Initialize OpenGL context
3. Create Renderer
4. Load default shaders and resources
5. Initialize ScriptEngine (Lua VM)
6. Setup Input system
7. Return success

### 12.3 Scene Loading

**Scene builders are deferred lambdas:**
```cpp
// Registration: builder is stored
sceneManager.registerScene("main", builder);

// Loading: builder is called
loadScene("main") {
    // Call registered builder
    // Builder creates entities and components in the scene
}
```

**Why Builders?**
- Scenes can be loaded/unloaded dynamically
- Scene initialization logic is encapsulated
- Can load multiple scenes if needed

### 12.4 Main Loop Execution

**Per-frame:**
1. Handle pending scene switches
2. Process input events
3. Update delta time
4. Build execution context
5. Update all systems in order
6. Render and buffer swap
7. Log profiling data every 1 second

---

## Summary: Architecture Strengths

1. **Clear Layering**: Strict one-way dependency chain prevents circular dependencies
2. **Modularity**: Each module has clear responsibilities with minimal coupling
3. **ECS Design**: Flexible entity composition without deep hierarchies
4. **Data Locality**: Components stored efficiently, iteration is cache-friendly
5. **Extensibility**: New components and systems easy to add
6. **Scripting Integration**: Single Lua VM balances simplicity with flexibility
7. **Performance**: Command batching, transform culling, GPU streaming
8. **Profiling**: Per-phase timing built into main loop
9. **No Global State**: Context injection makes dependencies explicit
10. **Error Handling**: Result types for error propagation across layers

---

## Future Extension Points

- **Physics System**: Integrate Box2D via PhysicsSystem
- **Animation**: Add AnimatorComponent and AnimationSystem
- **Audio**: Add AudioComponent and AudioSystem
- **Collision Detection**: Add ColliderComponent and collision callbacks
- **Networking**: Add network synchronization layer
- **Editor**: Scene editor with entity/component manipulation
- **Debugging**: In-game profiler and entity inspector

