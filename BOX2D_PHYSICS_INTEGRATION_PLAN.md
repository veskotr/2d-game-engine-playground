# Box2D Physics Integration Plan

## Executive Summary

Integrate Box2D for collision detection and 2D physics into SLE. The physics system will follow the existing modular architecture with a new **Physics** module positioned between Scene and Systems layers. Physics will operate as a discrete system that reads transforms, simulates the physics world, and writes results back to entities.

---

## 1. Architecture Overview

### 1.1 Physics Module Placement

**Module Dependency Update:**
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
Physics (NEW) ← Box2D world, body management, collision callbacks
  ↓
Scripting (Lua VM, EngineAPI, script lifecycle)
  ↓
Systems (Transform, Script, Physics, Render systems)
  ↓
Runtime (orchestration, game loop)
  ↓
Sandbox (game application)
```

### 1.2 Physics System Ownership

The **Physics** module will own:
- Box2D world instance (`b2World`)
- Body pool mapping entities to Box2D bodies
- Fixture/shape management
- Collision callbacks and contact listeners
- Joint management (for future extensibility)

The **Systems** module (specifically `PhysicsSystem`) will own:
- Per-frame physics stepping
- Synchronization between transforms and physics bodies
- Collision event dispatching

### 1.3 Design Principles

1. **Transform as Source of Truth** (initially)
   - When an entity's Transform changes, sync to physics body
   - Physics simulation updates body, results sync back to transform

2. **Optional Physics**
   - Not every entity needs physics; only those with RigidBody component
   - Default behavior: physics disabled unless explicitly enabled

3. **Fixed Timestep (Single-Threaded)**
   - Physics runs at fixed 60 Hz independent of render frame rate
   - Deterministic and easier to debug than multi-threaded approach
   - Threading deferred until profiling shows it's beneficial
   - All transforms synced before physics step, results written after

4. **Collision Events**
   - Physics module dispatches collision events to event bus
   - Scripts and systems can subscribe to collision callbacks
   - Sensors (zones) use same contact listener for consistency

5. **Zones (Trigger Areas)**
   - Separate component system for non-physical trigger areas
   - Uses Box2D sensors (fixtures with `isSensor = true`)
   - Detects overlaps but causes no physical response
   - Integrated with event bus for localized zoned events

6. **Deterministic & Debuggable**
   - Fixed timestep for consistency
   - Optional debug rendering (will be future enhancement)

---

## 2. Components Required

### 2.1 RigidBodyComponent

Defines physics properties for an entity.

```cpp
// Scene/include/sle/scene/components/RigidBodyComponent.hpp
namespace sle::components {

enum class BodyType : uint8_t
{
    Static = 0,     // Doesn't move, affected by nothing
    Dynamic = 1,    // Affected by forces, gravity, collisions
    Kinematic = 2   // Moves, not affected by forces
};

struct RigidBodyComponent
{
    // Physics body properties
    BodyType bodyType = BodyType::Dynamic;
    float mass = 1.0f;
    float linearDamping = 0.0f;      // Air resistance
    float angularDamping = 0.0f;     // Rotational air resistance
    float gravityScale = 1.0f;       // Multiplier on gravity
    
    // Velocity state
    glm::vec2 velocity{0.0f};
    float angularVelocity = 0.0f;
    
    // Constraints
    bool fixedRotation = false;       // Prevent rotation
    bool constraints = 0;             // Bitfield for freeze x/y/rotation
    
    // Internal (not user-facing)
    uint32_t box2dBodyId = 0;         // Handle to b2Body
    bool enabled = true;              // Enable/disable without removing
};

} // namespace sle::components
```

### 2.2 BoxColliderComponent

Axis-aligned box collider for rectangular shapes.

```cpp
// Scene/include/sle/scene/components/BoxColliderComponent.hpp
namespace sle::components {

struct BoxColliderComponent
{
    // Collider geometry (in local space)
    glm::vec2 offset{0.0f};          // Offset from entity position
    glm::vec2 size{1.0f, 1.0f};      // Width and height
    
    // Physics material
    float friction = 0.4f;            // Friction coefficient
    float restitution = 0.0f;         // Bounciness (0-1)
    float density = 1.0f;             // Mass per unit volume
    
    // Collision filtering
    uint16_t categoryBits = 0x0001;   // What is this?
    uint16_t maskBits = 0xFFFF;       // What can it collide with?
    
    // Internal (not user-facing)
    uint32_t box2dFixtureId = 0;      // Handle to b2Fixture
    bool enabled = true;              // Enable/disable without removing
    
    // Collision state (read-only, set by physics system)
    bool isTouching = false;          // Is in contact with something?
};

} // namespace sle::components
```

### 2.3 CircleColliderComponent

Circular collider for round shapes.

```cpp
// Scene/include/sle/scene/components/CircleColliderComponent.hpp
namespace sle::components {

struct CircleColliderComponent
{
    // Collider geometry (in local space)
    glm::vec2 offset{0.0f};           // Offset from entity position
    float radius = 0.5f;              // Radius of circle
    
    // Physics material
    float friction = 0.4f;
    float restitution = 0.0f;
    float density = 1.0f;
    
    // Collision filtering
    uint16_t categoryBits = 0x0001;
    uint16_t maskBits = 0xFFFF;
    
    // Internal
    uint32_t box2dFixtureId = 0;
    bool enabled = true;
    bool isTouching = false;
};

} // namespace sle::components
```

### 2.4 BoxZoneComponent

Trigger area (sensor) for entity interactions and events. No physical collision response.

```cpp
// Scene/include/sle/scene/components/BoxZoneComponent.hpp
namespace sle::components {

struct BoxZoneComponent
{
    // Zone geometry (in local space)
    glm::vec2 offset{0.0f};          // Offset from entity position
    glm::vec2 size{1.0f, 1.0f};      // Width and height
    
    // Zone identity
    std::string zoneId;              // Unique identifier for event dispatch
    bool enabled = true;             // Enable/disable without removing
    
    // Collision filtering (same as colliders)
    uint16_t categoryBits = 0x0001;  // What is this zone?
    uint16_t maskBits = 0xFFFF;      // What can enter this zone?
    
    // Internal (not user-facing)
    uint32_t box2dFixtureId = 0;     // Handle to b2Fixture (sensor)
};

} // namespace sle::components
```

### 2.5 CircleZoneComponent

Circular trigger area (sensor).

```cpp
// Scene/include/sle/scene/components/CircleZoneComponent.hpp
namespace sle::components {

struct CircleZoneComponent
{
    // Zone geometry (in local space)
    glm::vec2 offset{0.0f};          // Offset from entity position
    float radius = 0.5f;             // Radius of circle
    
    // Zone identity
    std::string zoneId;              // Unique identifier for event dispatch
    bool enabled = true;
    
    // Collision filtering
    uint16_t categoryBits = 0x0001;
    uint16_t maskBits = 0xFFFF;
    
    // Internal
    uint32_t box2dFixtureId = 0;
};

} // namespace sle::components
```

---

## 3. Event System Integration

### 3.1 Zone Events

Zones dispatch two types of events via the event bus:

```cpp
// Scene/include/sle/scene/events/ZoneEvents.hpp
namespace sle::events {

struct ZoneEnterEvent
{
    uint32_t zoneEntityId;     // Entity with the zone
    std::string zoneId;        // Zone identifier
    uint32_t otherEntityId;    // Entity entering the zone
};

struct ZoneExitEvent
{
    uint32_t zoneEntityId;
    std::string zoneId;
    uint32_t otherEntityId;    // Entity exiting the zone
};

} // namespace sle::events
```

### 3.2 Collision Events

Collisions also dispatch events:

```cpp
// Scene/include/sle/scene/events/CollisionEvents.hpp
namespace sle::events {

struct CollisionBeginEvent
{
    uint32_t entityA;
    uint32_t entityB;
};

struct CollisionEndEvent
{
    uint32_t entityA;
    uint32_t entityB;
};

} // namespace sle::events
```

---

## 3. Physics Module Structure

### 4.1 Directory Layout

```
EngineModules/
  Physics/
    CMakeLists.txt
    include/
      sle/physics/
        PhysicsWorld.hpp          // World and body management
        ContactListener.hpp       // Collision & zone callbacks
        PhysicsTypes.hpp          // Enums, constants
        PhysicsApi.hpp            // Public API
    src/
      PhysicsWorld.cpp
      ContactListener.cpp
      PhysicsApi.cpp
```

### 4.2 PhysicsWorld Class

Central manager for Box2D world and bodies, including zone sensors.

```cpp
// Physics/include/sle/physics/PhysicsWorld.hpp
namespace sle::physics {

class PhysicsWorld
{
public:
    PhysicsWorld(const glm::vec2& gravity = {0.0f, -9.81f});
    ~PhysicsWorld();
    
    // World management
    void step(float deltaTime);  // Fixed timestep: 1/60 or user-configurable
    void setGravity(const glm::vec2& gravity);
    glm::vec2 getGravity() const;
    
    // Body creation/destruction (for physics)
    uint32_t createBody(const glm::vec2& position, const components::RigidBodyComponent& rigidBody);
    void destroyBody(uint32_t bodyId);
    void setBodyTransform(uint32_t bodyId, const glm::vec2& position, float rotation);
    void getBodyTransform(uint32_t bodyId, glm::vec2& position, float& rotation);
    void setBodyVelocity(uint32_t bodyId, const glm::vec2& velocity);
    glm::vec2 getBodyVelocity(uint32_t bodyId);
    
    // Fixture creation (colliders)
    uint32_t createBoxFixture(uint32_t bodyId, const components::BoxColliderComponent& collider);
    uint32_t createCircleFixture(uint32_t bodyId, const components::CircleColliderComponent& collider);
    void destroyFixture(uint32_t bodyId, uint32_t fixtureId);
    
    // Zone/Sensor creation (triggers - no physical response)
    uint32_t createBoxZone(uint32_t bodyId, const components::BoxZoneComponent& zone);
    uint32_t createCircleZone(uint32_t bodyId, const components::CircleZoneComponent& zone);
    void destroyZone(uint32_t bodyId, uint32_t zoneId);
    
    // Event dispatching
    using ContactCallback = std::function<void(uint32_t, uint32_t)>;
    using ZoneCallback = std::function<void(uint32_t, uint32_t)>;
    void onCollisionBegin(ContactCallback cb);
    void onCollisionEnd(ContactCallback cb);
    void onZoneEnter(ZoneCallback cb);
    void onZoneExit(ZoneCallback cb);
    
    // Debug
    b2World* getRawWorld() { return world_.get(); }
    
private:
    std::unique_ptr<b2World> world_;
    std::unordered_map<uint32_t, b2Body*> bodyMap_;
    std::unique_ptr<ContactListener> contactListener_;
    float fixedTimestep_ = 1.0f / 60.0f;  // 60 Hz default
    float accumulator_ = 0.0f;             // For fixed timestep
    
    friend class ContactListener;
};

} // namespace sle::physics
```

### 4.3 ContactListener Class

Handles Box2D collision and zone overlap callbacks.

```cpp
// Physics/include/sle/physics/ContactListener.hpp
namespace sle::physics {

class ContactListener : public b2ContactListener
{
public:
    explicit ContactListener(sle::scene::EventBus* eventBus);
    
    void BeginContact(b2Contact* contact) override;
    void EndContact(b2Contact* contact) override;
    void PreSolve(b2Contact* contact, const b2Manifold* oldManifold) override;
    void PostSolve(b2Contact* contact, const b2ContactImpulse* impulse) override;
    
private:
    sle::scene::EventBus* eventBus_;
    
    // Helper to determine if a contact involves sensors (zones)
    bool isSensorContact(b2Contact* contact);
};

} // namespace sle::physics
```

**Behavior:**
- If both fixtures are sensors → zone enter/exit events
- If neither is a sensor → collision begin/end events
- If one is sensor, one isn't → ???  (TBD; recommend both fire)

---

## 4. Integration Points

### 5.1 Scene Module Changes

**Add components to Scene's public API:**

```cpp
// Scene/include/sle/scene/Scene.hpp
namespace sle::scene {

class Scene
{
    // Existing API...
    
    // Component registration (add to existing registry system)
    // These are included via ComponentRegistry
    // - RigidBodyComponent
    // - BoxColliderComponent
    // - CircleColliderComponent
    // - BoxZoneComponent (NEW)
    // - CircleZoneComponent (NEW)
};

} // namespace sle::scene
```

**Components must be registered in ComponentRegistry alongside Transform, SpriteRenderer, etc.**

**Add event types to Scene's event bus:**

```cpp
// Scene/include/sle/scene/events/
// - ZoneEvents.hpp (ZoneEnterEvent, ZoneExitEvent)
// - CollisionEvents.hpp (CollisionBeginEvent, CollisionEndEvent)
```

### 5.2 Systems Module Changes

**Create PhysicsSystem that processes physics and zones each frame:**

```cpp
// Systems/include/sle/systems/PhysicsSystem.hpp
namespace sle::systems {

class PhysicsSystem
{
public:
    explicit PhysicsSystem(sle::physics::PhysicsWorld* world);
    
    void update(const Context& ctx);
    
private:
    sle::physics::PhysicsWorld* world_;
    
    // Helper methods
    void syncTransformToPhysics(const Context& ctx);
    void syncPhysicsToTransform(const Context& ctx);
    void createPhysicsBodies(const Context& ctx);      // NEW: OnCreate bodies
    void createZoneSensors(const Context& ctx);        // NEW: OnCreate zones
    void destroyStalePhysics(const Context& ctx);      // NEW: OnDestroy cleanup
};

} // namespace sle::systems
```

**PhysicsSystem responsibilities:**
1. Each frame (before physics step):
   - Read new RigidBody/Zone components → create Box2D bodies/sensors
   - Read Transform and RigidBody components → sync to Box2D
   
2. Step physics (fixed timestep):
   - Call `world_->step(fixedDt)` multiple times if needed (accumulator pattern)
   - This updates all bodies and triggers contact callbacks
   
3. Each frame (after physics step):
   - Read body transforms from Box2D
   - Write back to TransformComponent
   - Handle destroyed entities → clean up Box2D bodies

### 5.3 Runtime Changes

**Integrate PhysicsSystem into main loop:**

```cpp
// Runtime/src/Runtime.cpp
void Runtime::run()
{
    while (windowOpen)
    {
        SceneManager::processPendingSwitch();
        Input::update();
        Timer::tick();
        
        auto ctx = buildContext();
        
        TransformSystem::update(ctx);  // Compute world transforms
        ScriptSystem::update(ctx);     // Run scripts (may move entities)
        PhysicsSystem::update(ctx);    // Physics simulation + zone detection
        RenderSystem::update(ctx);     // Build render commands
        
        renderer_.beginFrame();
        renderer_.flush();
        renderer_.endFrame();
        window_.swapBuffers();
    }
}
```

**Create physics world in Runtime with fixed timestep:**

```cpp
// Runtime/include/sle/runtime/Runtime.hpp
namespace sle::runtime {

class Runtime
{
private:
    std::unique_ptr<sle::physics::PhysicsWorld> physicsWorld_;
    std::unique_ptr<PhysicsSystem> physicsSystem_;
    float physicsFixedTimestep_ = 1.0f / 60.0f;  // 60 Hz physics
};

} // namespace sle::runtime
```

### 5.4 Scripting API Changes

**Expose physics and zones to Lua:**

```cpp
// New Lua API functions
Engine.Physics = {
    -- Body manipulation
    addForce(entity, forceX, forceY),
    addImpulse(entity, impulseX, impulseY),
    setVelocity(entity, velX, velY),
    getVelocity(entity),  -- Returns velX, velY
    setAngularVelocity(entity, angVel),
    getAngularVelocity(entity),
    setGravityScale(entity, scale),
    
    -- Query physics state
    isTouching(entity),    -- Returns true if in contact
    
    -- Zone queries (future: raycasting)
    -- getRayCastHits(startX, startY, endX, endY),
}

-- Event subscription (via event bus)
Engine.Events.subscribe("ZoneEnter", function(zoneEntity, zoneId, otherEntity)
    -- Handle zone entry
end)

Engine.Events.subscribe("ZoneExit", function(zoneEntity, zoneId, otherEntity)
    -- Handle zone exit
end)

Engine.Events.subscribe("CollisionBegin", function(entityA, entityB)
    -- Handle collision
end)

Engine.Events.subscribe("CollisionEnd", function(entityA, entityB)
    -- Handle separation
end)
```

### 4.5 CMakeLists.txt Changes

**Link Box2D dependency:**

```cmake
# EngineModules/Physics/CMakeLists.txt
target_link_libraries(Physics PUBLIC 
    Core 
    Resources
    Scene
    box2d::box2d
)
```

---

## 7. Implementation Steps

### Phase 1: Setup & Components (Steps 1-5)

- **Step 1:** Create Physics module directory structure
- **Step 2:** Add RigidBodyComponent, BoxColliderComponent, CircleColliderComponent to Scene
- **Step 3:** Add BoxZoneComponent, CircleZoneComponent to Scene
- **Step 4:** Add ZoneEvents and CollisionEvents to Scene event bus
- **Step 5:** Register all components in ComponentRegistry with Lua/JSON serialization

### Phase 2: Core Physics (Steps 6-8)

- **Step 6:** Implement PhysicsWorld wrapper around Box2D with fixed timestep
- **Step 7:** Implement ContactListener for collisions and zones
- **Step 8:** Set up CMakeLists.txt linking to Box2D

### Phase 3: Systems Integration (Steps 9-11)

- **Step 9:** Implement PhysicsSystem with body/zone creation and sync
- **Step 10:** Integrate PhysicsSystem into Runtime loop
- **Step 11:** Handle transform synchronization (Transform ↔ Physics/Zones)

### Phase 4: Event Integration (Steps 12-13)

- **Step 12:** Wire ContactListener to dispatch events to event bus
- **Step 13:** Test zone enter/exit and collision begin/end events

### Phase 5: Scripting & Testing (Steps 14-15)

- **Step 14:** Expose Physics API to Lua scripting
- **Step 15:** Create test scene with physics interactions and zone triggers

### Phase 6: Polish & Optimization (Steps 16-17)

- **Step 16:** Collision filtering and layer management
- **Step 17:** Debug visualization (optional future work)

---

## 6. Data Flow Diagram

```
Per Frame:

┌─────────────────────────────────┐
│ 1. Input & Scene Updates        │
│    (Transform changes, etc.)    │
└────────────┬────────────────────┘
             │
             ▼
┌─────────────────────────────────┐
│ 2. TransformSystem::update()    │
│    (Compute world transforms)   │
└────────────┬────────────────────┘
             │
             ▼
┌─────────────────────────────────┐
│ 3. ScriptSystem::update()       │
│    (Run Lua scripts, may move)  │
└────────────┬────────────────────┘
             │
             ▼
┌─────────────────────────────────┐
│ 4. PhysicsSystem::update()      │
│                                 │
│  a) Create new bodies/zones     │
│                                 │
│  b) Sync Transform→Physics      │
│     (Read all transforms)       │
│                                 │
│  c) Fixed Timestep Loop:        │
│     while (accumulator >= dt) { │
│         world->step(dt)         │
│         accumulator -= dt       │
│     }                           │
│                                 │
│  d) Sync Physics→Transform      │
│     (Write body results)        │
│                                 │
│  e) Dispatch Events             │
│     - CollisionBegin/End        │
│     - ZoneEnter/Exit            │
│                                 │
│  f) Cleanup destroyed entities  │
└────────────┬────────────────────┘
             │
             ▼
┌─────────────────────────────────┐
│ 5. RenderSystem::update()       │
│    (Build render commands)      │
└─────────────────────────────────┘
```

---

## 8. Serialization Format

### 8.1 Lua Serialization

**RigidBodyComponent:**
```lua
{
    bodyType = "dynamic",  -- "static", "dynamic", "kinematic"
    mass = 1.0,
    linearDamping = 0.0,
    angularDamping = 0.0,
    gravityScale = 1.0,
    velocity = { x = 0, y = 0 },
    angularVelocity = 0,
    fixedRotation = false
}
```

**BoxColliderComponent:**
```lua
{
    offset = { x = 0, y = 0 },
    size = { x = 1, y = 1 },
    friction = 0.4,
    restitution = 0.0,
    density = 1.0,
    categoryBits = 1,
    maskBits = 65535
}
```

**BoxZoneComponent:**
```lua
{
    offset = { x = 0, y = 0 },
    size = { x = 1, y = 1 },
    zoneId = "spawn_area",     -- Unique identifier
    enabled = true,
    categoryBits = 1,
    maskBits = 65535
}
```

### 8.2 JSON Serialization

**RigidBodyComponent:**
```json
{
    "bodyType": "dynamic",
    "mass": 1.0,
    "linearDamping": 0.0,
    "gravityScale": 1.0,
    "fixedRotation": false
}
```

**BoxZoneComponent:**
```json
{
    "offset": [0, 0],
    "size": [1, 1],
    "zoneId": "spawn_area",
    "enabled": true
}
```

---

## 9. Known Constraints & Decisions

1. **Fixed Timestep (Single-Threaded)**
   - Physics runs at fixed 60 Hz (configurable)
   - Uses accumulator pattern to handle variable frame rates
   - Deterministic and simplifies debugging
   - Threading deferred to Phase N if profiling shows benefit

2. **Zones as Box2D Sensors**
   - BoxZoneComponent and CircleZoneComponent use `isSensor = true` fixtures
   - Reuses contact listener for event dispatch
   - No separate geometry library needed
   - Clean integration with collision filtering

3. **Body Pooling**: Box2D bodies are created/destroyed with entities; no pooling initially

4. **Scale**: Assume Box2D units ≈ pixels for simplicity (can be tuned via conversion factor)

5. **Collision Events**: Dispatched via Scene's event bus; scripts and systems can subscribe with entity IDs

6. **Zone Events**: Dispatched with zone entity ID, zone ID string, and other entity ID for precise localization

7. **Gravity**: Global per world; entities can override with gravityScale

8. **Constraints**: Will not initially support joints or complex constraints (future work)

9. **Event System Readiness**: Assumes Scene module already has an EventBus (verify in implementation)

---

## 9. Testing Strategy

### 9.1 Manual Test Scene

Create a scene with:
- Static ground platform
- Dynamic falling box
- Kinematic moving platform
- Player character with WASD movement
- Collision event logging

### 9.2 Verification Checklist

- [ ] Entities with RigidBody spawn correctly
- [ ] Transform changes sync to physics bodies
- [ ] Physics bodies update transforms after simulation
- [ ] Collisions are detected and reported
- [ ] Lua can apply forces and query physics state
- [ ] Gravity applies correctly
- [ ] Friction and restitution work as expected
- [ ] Performance is acceptable (profile physics step time)

---

## 10. Future Enhancements

### Phase N (Later): Threading & Optimization

- **Physics Threading**: Move physics to separate thread with fixed timestep
- **Double Buffering**: Separate read/write physics state for thread safety
- **Job Queue**: Async physics commands from main thread

### Phase N+1: Advanced Physics

- Debug visualization (render Box2D shapes)
- Polygonal and complex shapes (PolygonColliderComponent)
- Joints (distance, revolute, prismatic, etc.)
- Raycasting API
- Broad-phase optimization tuning
- Physics constraints and motors

### Phase N+2: Advanced Zones

- Hierarchical zone queries (zones containing zones)
- Zone priority/layering
- Complex zone shapes
- Soft body simulation (if needed)

---

## Appendix A: Box2D Integration Checklist

- [ ] Box2D library is linked in CMakeLists.txt
- [ ] b2World initialization with correct gravity and settings
- [ ] Body type conversions (Static → b2_staticBody, etc.)
- [ ] Shape creation (b2PolygonShape, b2CircleShape)
- [ ] Fixture creation and attachment to bodies
- [ ] Contact listener implementation
- [ ] Collision callback registration and dispatching
- [ ] Handle entity destruction (clean up Box2D bodies)
- [ ] Handle component removal (clean up fixtures)

