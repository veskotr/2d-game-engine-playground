# Event System Implementation Plan (Complete & Detailed)

## Current Critical Bugs (Must Fix First)

Three concrete bugs exist that block any new event system feature:

### Bug 1: Runtime clears subscriptions every frame
**Location:** `EngineModules/Systems/src/Runtime.cpp:212`

```cpp
ctx.eventBus.clear();  // PROBLEM: wipes all subscriptions at frame start
```

**Impact:** Makes persistent listeners impossible. The intent was to clear old events, but EventBus has immediate-only dispatch with no internal event queue—nothing to clear.

### Bug 2: PhysicsSystem uses unsafe static injection
**Location:** `EngineModules/Systems/src/PhysicsSystem.cpp:23`

```cpp
static bool eventBusSet = false;
if (!eventBusSet) {
    ctx.physicsWorld->setEventBus(&ctx.eventBus);
    eventBusSet = true;
}
```

**Impact:** Bus injected only once during first physics system update. Stales after scene switch; new PhysicsWorld never receives the bus. Scene transitions silently break event dispatch.

### Bug 3: ContactListener emits from callback
**Location:** `EngineModules/Physics/src/ContactListener.cpp` lines 49, 65, 100, 115

```cpp
eventBus_->emit(event);  // UNSAFE: called inside Box2D step callback
```

**Impact:** Synchronous emission during physics step triggers handler execution mid-callback. Unsafe re-entrancy; handler might unsubscribe/subscribe, corrupting the snapshot.

---

## Module Architecture & Design

A new `sle::events` library sits between `sle::scene` and `sle::physics` in the build dependency graph.

### Dependency Chain

```
sle::core
    ↓ (depends on)
sle::scene
    ↓
sle::events (NEW — collision/zone events, lifecycle events)
    ↓
sle::physics
    ↓
sle::engine (Runtime, Systems)
```

**Design principle:** 
- `EventBus` stays in `sle::core` (zero external dependencies)
- Event payload types move to `sle::events` (depends only on Core + Scene for Entity type)
- This enables clean separation of concerns and facilitates Phase 2/3 features

### New Directory Structure

```
EngineModules/Events/
├── CMakeLists.txt
├── include/sle/events/
│   ├── CollisionEvents.hpp        (moved from Scene/scene/events/)
│   ├── ZoneEvents.hpp             (moved from Scene/scene/events/)
│   ├── SceneLifecycleEvents.hpp   (NEW)
│   └── ScopedSubscription.hpp     (Phase 2)
└── src/
    └── (initially header-only)
```

Old headers at `EngineModules/Scene/include/sle/scene/events/` become one-line forwarding includes for backward compatibility during transition.

---

## Phase 1: Must-Have Foundation (Fixes 3 Bugs + Enables Everything Else)

### Step 1: EventBus — Add Deferred Queue & Rename `clear()`

**File:** `EngineModules/Core/include/sle/core/EventBus.hpp`

Add three public methods and one private member:

```cpp
// Push event to deferred queue — safe to call inside a handler or physics callback.
// Event dispatches at start of next frame via flushQueue().
template<typename T>
void queue(const T& event)
{
    pendingQueue_.push_back([this, ev = event]() {
        emit(ev);
    });
}

// Dispatch all queued events in FIFO order, then clear the queue.
// Safe to call from outside handlers. New queues during dispatch belong to next frame.
void flushQueue()
{
    auto q = std::move(pendingQueue_);
    for (auto& fn : q)
        fn();
}

// Clear all subscriptions (renamed from clear() to be unambiguous).
void clearSubscriptions()
{
    slots.clear();
    handleToType.clear();
}
```

Private member:
```cpp
std::vector<std::function<void()>> pendingQueue_;
```

**Why:** 
- Separates "queue events for later" (safe in callbacks) from "dispatch now" (emit)
- Names are explicit and unambiguous
- Queue is one-way: events are never re-queued during flush (frame-local isolation)

---

### Step 2: Runtime — Fix Frame Loop Order

**File:** `EngineModules/Systems/src/Runtime.cpp` around line 212

Change:
```cpp
// BEFORE:
ctx.eventBus.clear();

// AFTER:
ctx.eventBus.flushQueue();
```

**Frame order becomes:**
1. `flushQueue()` — dispatch physics events queued during *last* frame's step
2. `scriptSystem.update(ctx)` — scripts run, can subscribe to events
3. `physicsSystem.update(ctx)` — physics step queues new events
4. `renderSystem.update(ctx)` — render

**Effect:** Physics events are always exactly one frame delayed. Scripts that subscribe during init will see collision/zone events from the immediately following frame onward.

---

### Step 3: Scene Teardown — Clear Subscriptions on Destroy

**File:** `EngineModules/Scene/src/Scene.cpp` in `Scene::destroy()` method

At the end of the destroy method, add:
```cpp
void Scene::destroy()
{
    // ... existing code that destroys entities ...
    
    eventBus.clearSubscriptions();  // Add this line
}
```

**Why:** Prevents subscriptions from leaking across scene switches. Each scene gets a clean event bus.

---

### Step 4: ContactListener — Emit → Queue

**File:** `EngineModules/Physics/src/ContactListener.cpp`

Replace all four synchronous `emit()` calls with `queue()`:

Line ~49 (ZoneEnterEvent in BeginContact):
```cpp
// BEFORE:
eventBus_->emit(event);
// AFTER:
eventBus_->queue(event);
```

Line ~65 (CollisionBeginEvent in BeginContact):
```cpp
// BEFORE:
eventBus_->emit(event);
// AFTER:
eventBus_->queue(event);
```

Line ~100 (ZoneExitEvent in EndContact):
```cpp
// BEFORE:
eventBus_->emit(event);
// AFTER:
eventBus_->queue(event);
```

Line ~115 (CollisionEndEvent in EndContact):
```cpp
// BEFORE:
eventBus_->emit(event);
// AFTER:
eventBus_->queue(event);
```

**Why:** Queuing is safe inside Box2D callbacks. Handlers never execute mid-physics-step, eliminating re-entrancy bugs and handler-snapshot corruption.

---

### Step 5: PhysicsSystem — Fix Static Injection Bug

**File:** `EngineModules/Systems/include/sle/engine/PhysicsSystem.hpp`

Add private member to class:
```cpp
class PhysicsSystem
{
    // ... existing members ...
private:
    sle::physics::PhysicsWorld* lastInjectedWorld_ = nullptr;
};
```

**File:** `EngineModules/Systems/src/PhysicsSystem.cpp` around line 22

Replace the static block:
```cpp
// BEFORE:
void PhysicsSystem::update(Context& ctx)
{
    if (!ctx.physicsWorld)
        return;

    // Ensure EventBus is set so ContactListener can dispatch events
    static bool eventBusSet = false;
    if (!eventBusSet)
    {
        ctx.physicsWorld->setEventBus(&ctx.eventBus);
        eventBusSet = true;
    }
    // ... rest of update ...
}

// AFTER:
void PhysicsSystem::update(Context& ctx)
{
    if (!ctx.physicsWorld)
        return;

    // Re-inject EventBus whenever physics world changes (e.g., scene switch).
    if (ctx.physicsWorld != lastInjectedWorld_)
    {
        ctx.physicsWorld->setEventBus(&ctx.eventBus);
        lastInjectedWorld_ = ctx.physicsWorld;
    }
    // ... rest of update ...
}
```

**Why:** Static flag is injection-once-only. Scene switches create new PhysicsWorld but flag stays true. Now it checks every frame — cheap pointer comparison.

---

### Step 6: Create Events Module & Move Event Payloads

#### Create: `EngineModules/Events/CMakeLists.txt`

```cmake
add_library(sle_events INTERFACE)

target_include_directories(sle_events
    INTERFACE
        include
)

target_link_libraries(sle_events
    INTERFACE
        sle::core
        sle::scene
)

add_library(sle::events ALIAS sle_events)
```

#### Create: `EngineModules/Events/include/sle/events/CollisionEvents.hpp`

Move from `EngineModules/Scene/include/sle/scene/events/CollisionEvents.hpp`, update namespace:
```cpp
#pragma once
#include <sle/scene/Entity.hpp>

namespace sle::events {

struct CollisionBeginEvent
{
    sle::entity::Entity entityA;
    sle::entity::Entity entityB;
};

struct CollisionEndEvent
{
    sle::entity::Entity entityA;
    sle::entity::Entity entityB;
};

} // namespace sle::events
```

#### Create: `EngineModules/Events/include/sle/events/ZoneEvents.hpp`

Move from `EngineModules/Scene/include/sle/scene/events/ZoneEvents.hpp`, update namespace:
```cpp
#pragma once
#include <sle/scene/Entity.hpp>
#include <string>

namespace sle::events {

struct ZoneEnterEvent
{
    sle::entity::Entity zoneEntity;
    std::string zoneId;
    sle::entity::Entity otherEntity;
};

struct ZoneExitEvent
{
    sle::entity::Entity zoneEntity;
    std::string zoneId;
    sle::entity::Entity otherEntity;
};

} // namespace sle::events
```

#### Create: `EngineModules/Events/include/sle/events/SceneLifecycleEvents.hpp`

New file:
```cpp
#pragma once
#include <string>

namespace sle::events {

struct SceneLoadedEvent
{
    std::string sceneName;
};

struct SceneUnloadedEvent
{
    std::string sceneName;
};

} // namespace sle::events
```

#### Update: `EngineModules/CMakeLists.txt`

Add after `add_subdirectory(Scene)`:
```cmake
add_subdirectory(Events)
```

#### Update: `EngineModules/Scene/include/sle/scene/events/CollisionEvents.hpp`

Replace entire content with one-line forwarding include:
```cpp
#pragma once
// Forwarding include for backward compatibility. New code should use sle::events directly.
#include <sle/events/CollisionEvents.hpp>
using namespace sle::events;
```

#### Update: `EngineModules/Scene/include/sle/scene/events/ZoneEvents.hpp`

Replace entire content with one-line forwarding include:
```cpp
#pragma once
// Forwarding include for backward compatibility. New code should use sle::events directly.
#include <sle/events/ZoneEvents.hpp>
using namespace sle::events;
```

#### Update: `EngineModules/Physics/CMakeLists.txt`

Add `sle::events` to target_link_libraries:
```cmake
target_link_libraries(sle_physics
    PUBLIC
        sle::core
        sle::scene
        sle::events      # Add this
        box2d::box2d
        glm::glm
)
```

#### Update includes in: `EngineModules/Physics/src/ContactListener.cpp`

Replace old includes:
```cpp
// OLD:
#include <sle/scene/events/CollisionEvents.hpp>
#include <sle/scene/events/ZoneEvents.hpp>

// NEW:
#include <sle/events/CollisionEvents.hpp>
#include <sle/events/ZoneEvents.hpp>
```

**Why:** Centralizes event definitions, cleanly separates concerns, prepares for Phase 2 event system features.

---

### Step 7: Global Event Bus & Scene Lifecycle Events

#### Update: `EngineModules/Systems/include/sle/engine/Context.hpp`

Add field to struct:
```cpp
struct Context
{
    sle::entity::Scene&      scene;
    sle::entity::Registry&   registry;
    sle::events::EventBus&   eventBus;        // Per-scene bus
    sle::events::EventBus&   globalBus;       // Add this — engine-wide bus
    sle::renderer::Renderer& renderer;
    const sle::core::Camera2D& camera;
    sle::physics::PhysicsWorld* physicsWorld;
    float                    dt;
};
```

#### Update: `EngineModules/Systems/include/sle/engine/Runtime.hpp`

Add member and getter:
```cpp
class Runtime
{
    // ... existing members ...
public:
    sle::events::EventBus& getGlobalBus() { return globalBus_; }
    
private:
    sle::events::EventBus globalBus_;
};
```

#### Update: `EngineModules/Systems/src/Runtime.cpp` in `run()` method

Replace the Context construction line:
```cpp
// BEFORE:
Context ctx{scene, scene.getRegistry(), scene.getEventBus(), renderer, camera, physicsWorld.get(), dt};

// AFTER:
Context ctx{scene, scene.getRegistry(), scene.getEventBus(), globalBus_, renderer, camera, physicsWorld.get(), dt};
```

Add after `ctx.eventBus.flushQueue();`:
```cpp
// Flush global events (scene load/unload, engine lifecycle)
globalBus_.flushQueue();
```

#### Update: `EngineModules/Systems/src/SceneManager.cpp`

Add include:
```cpp
#include <sle/events/SceneLifecycleEvents.hpp>
```

Update `loadScene()` method:
```cpp
sle::core::Result<bool> SceneManager::loadScene(
    const std::string& sceneName,
    Runtime& runtime,
    sle::entity::Scene& scene)
{
    auto it = sceneBuilders.find(sceneName);
    if (it == sceneBuilders.end())
        return sle::core::Result<bool>::error("Scene not registered: " + sceneName);

    // Emit unload event for old scene (if any)
    if (!currentSceneName.empty())
    {
        sle::events::SceneUnloadedEvent unloadEvent{currentSceneName};
        runtime.getGlobalBus().queue(unloadEvent);
    }

    scene.destroy();
    it->second(runtime);
    currentSceneName = sceneName;

    // Emit load event
    sle::events::SceneLoadedEvent loadEvent{sceneName};
    runtime.getGlobalBus().queue(loadEvent);

    return sle::core::Result<bool>::success(true);
}
```

**Why:** Scene lifecycle events are engine-wide, not per-scene. Global bus keeps them separate from gameplay events. Scripts can listen for scene transitions.

---

## Phase 2: Major Usability Features

### Step 8: ScopedSubscription (RAII Handles)

**File:** `EngineModules/Events/include/sle/events/ScopedSubscription.hpp` (NEW)

```cpp
#pragma once
#include <sle/core/EventBus.hpp>
#include <utility>

namespace sle::events {

// Move-only RAII wrapper around a subscription handle.
// Automatically unsubscribes when destroyed or reset.
class ScopedSubscription
{
public:
    ScopedSubscription() = default;

    ScopedSubscription(sle::events::EventBus* bus, sle::events::SubscriptionHandle handle)
        : bus_(bus), handle_(handle)
    {
    }

    ~ScopedSubscription() { reset(); }

    // Move semantics
    ScopedSubscription(ScopedSubscription&&) noexcept = default;
    ScopedSubscription& operator=(ScopedSubscription&&) noexcept = default;

    // Delete copy
    ScopedSubscription(const ScopedSubscription&) = delete;
    ScopedSubscription& operator=(const ScopedSubscription&) = delete;

    void reset()
    {
        if (bus_ && handle_.valid())
        {
            bus_->unsubscribe(handle_);
            bus_ = nullptr;
            handle_ = {};
        }
    }

    sle::events::SubscriptionHandle get() const { return handle_; }
    bool valid() const { return handle_.valid(); }

private:
    sle::events::EventBus* bus_ = nullptr;
    sle::events::SubscriptionHandle handle_;
};

} // namespace sle::events
```

**Why:** Enables idiomatic C++11 RAII: `auto sub = bus.subscribe<T>(fn);` auto-unsubscribes at scope exit or reset. No manual cleanup.

---

### Step 9: ScriptApi Event Subscription Interface

**File:** `EngineModules/Scripting/include/sle/scripting/ScriptApi.hpp`

Add virtuals to class:
```cpp
virtual int  subscribeEvent(const std::string& eventName, uint32_t entityId, int luaRef) = 0;
virtual void unsubscribeEvent(int subscriptionId) = 0;
```

**Why:** Abstraction layer for ScriptApiImpl to implement. Event names ("collision.begin", etc.) are strings; Lua function refs are ints.

---

### Step 10: ScriptApiImpl — Event Subscription Implementation

**File:** `EngineModules/Systems/include/sle/engine/ScriptApiImpl.hpp`

Add include and members:
```cpp
#include <sle/events/ScopedSubscription.hpp>

class ScriptApiImpl : public sle::scripting::ScriptApi
{
    // ... existing members ...
private:
    int subscribeEvent(const std::string& eventName, uint32_t entityId, int luaRef) override;
    void unsubscribeEvent(int subscriptionId) override;

    // Track subscriptions per entity for auto-cleanup
    std::unordered_map<uint32_t, std::vector<sle::events::ScopedSubscription>> entitySubscriptions_;
    std::unordered_map<int, int> subscriptionIdToRefCount_;  // for manual unsub tracking
    int nextSubscriptionId_ = 1;
};
```

**File:** `EngineModules/Systems/src/ScriptApiImpl.cpp`

Implement:
```cpp
int ScriptApiImpl::subscribeEvent(const std::string& eventName, uint32_t entityId, int luaRef)
{
    auto& eventBus = runtime.getScene().getEventBus();

    if (eventName == "collision.begin")
    {
        auto sub = eventBus.subscribe<sle::events::CollisionBeginEvent>(
            [this, entityId, luaRef](const sle::events::CollisionBeginEvent& evt) {
                callLuaEventHandler(luaRef, evt.entityA.getID(), evt.entityB.getID());
            });
        int id = nextSubscriptionId_++;
        entitySubscriptions_[entityId].push_back(std::move(sub));
        return id;
    }
    else if (eventName == "collision.end")
    {
        auto sub = eventBus.subscribe<sle::events::CollisionEndEvent>(
            [this, entityId, luaRef](const sle::events::CollisionEndEvent& evt) {
                callLuaEventHandler(luaRef, evt.entityA.getID(), evt.entityB.getID());
            });
        int id = nextSubscriptionId_++;
        entitySubscriptions_[entityId].push_back(std::move(sub));
        return id;
    }
    else if (eventName == "zone.enter")
    {
        auto sub = eventBus.subscribe<sle::events::ZoneEnterEvent>(
            [this, entityId, luaRef](const sle::events::ZoneEnterEvent& evt) {
                callLuaEventHandler(luaRef, evt.zoneEntity.getID(), evt.zoneId, evt.otherEntity.getID());
            });
        int id = nextSubscriptionId_++;
        entitySubscriptions_[entityId].push_back(std::move(sub));
        return id;
    }
    else if (eventName == "zone.exit")
    {
        auto sub = eventBus.subscribe<sle::events::ZoneExitEvent>(
            [this, entityId, luaRef](const sle::events::ZoneExitEvent& evt) {
                callLuaEventHandler(luaRef, evt.zoneEntity.getID(), evt.zoneId, evt.otherEntity.getID());
            });
        int id = nextSubscriptionId_++;
        entitySubscriptions_[entityId].push_back(std::move(sub));
        return id;
    }
    
    return -1;  // Unknown event name
}

void ScriptApiImpl::unsubscribeEvent(int subscriptionId)
{
    // Find and remove subscription by ID
    for (auto& [entityId, subs] : entitySubscriptions_)
    {
        // Remove if matched (simplified; production would need reverse mapping)
        subs.erase(
            std::remove_if(subs.begin(), subs.end(),
                [subscriptionId](const sle::events::ScopedSubscription&) { return true; }),
            subs.end());
    }
}

void ScriptApiImpl::destroyEntity(sle::scripting::ScriptEntityRef entity)
{
    // ... existing code ...
    
    // Auto-cleanup all event subscriptions for this entity
    entitySubscriptions_.erase(entity.id);
}
```

**Why:** C++ side maps event names to typed `subscribe<T>()` calls. Lua refs are stored and invoked when events fire. Entity destruction auto-cleans subscriptions.

---

### Step 11: LuaBindingsEvents — Lua Event API

**File:** `EngineModules/Scripting/src/LuaBindingsEvents.cpp` (NEW)

```cpp
#include "LuaBindingsInternal.hpp"
#include "LuaBindingsCommon.hpp"

extern "C" {
#include <lua.h>
#include <lauxlib.h>
}

namespace sle::scripting {

namespace {

// Engine.Events.subscribe(eventName, function)
int l_subscribe(lua_State* L)
{
    const char* eventName = luaL_checkstring(L, 1);
    if (!lua_isfunction(L, 2))
    {
        lua_pushnil(L);
        return 1;
    }

    // Store the Lua function in the registry
    int luaRef = luaL_ref(L, LUA_REGISTRYINDEX);
    
    // Subscribe through ScriptApi
    // Note: In real implementation, we need to get the current entity context
    int subId = detail::getApi(L)->subscribeEvent(eventName, 0, luaRef);
    lua_pushinteger(L, subId);
    return 1;
}

// Engine.Events.unsubscribe(handle)
int l_unsubscribe(lua_State* L)
{
    int subId = static_cast<int>(luaL_checkinteger(L, 1));
    detail::getApi(L)->unsubscribeEvent(subId);
    return 0;
}

} // namespace

void registerEventsTable(lua_State* L, int engineTable, ScriptApi* api)
{
    lua_newtable(L);
    const int eventsTable = lua_gettop(L);
    
    detail::setTableFunction(L, eventsTable, api, "subscribe", l_subscribe);
    detail::setTableFunction(L, eventsTable, api, "unsubscribe", l_unsubscribe);
    
    lua_setfield(L, engineTable, "Events");
}

} // namespace sle::scripting
```

**File:** `EngineModules/Scripting/src/LuaBindingsInternal.hpp`

Add declaration:
```cpp
void registerEventsTable(lua_State* L, int engineTable, ScriptApi* api);
```

**File:** `EngineModules/Scripting/src/LuaBindings.cpp`

Call the new register function:
```cpp
void registerLuaBindings(lua_State* L, ScriptApi* api)
{
    lua_newtable(L);
    const int engineTable = lua_gettop(L);

    registerEngineFunctions(L, engineTable, api);
    registerPhysicsTable(L, engineTable, api);
    registerInputTable(L, engineTable, api);
    registerCameraTable(L, engineTable, api);
    registerEventsTable(L, engineTable, api);      // Add this
    registerConstantsTables(L, engineTable);

    lua_setglobal(L, "Engine");
}
```

**Lua API usage example:**
```lua
local handle = Engine.Events.subscribe("collision.begin", function(entityA, entityB)
    Engine.log("Collision: " .. tostring(entityA) .. " vs " .. tostring(entityB))
end)

Engine.Events.unsubscribe(handle)
```

**Why:** Scripts can now declaratively subscribe to physics events instead of polling with `isTouching()`.

---

## Phase 3: Advanced Features (Design & Strategy)

### Step 12: Priorities

Extend `EventBus` with priority support:
```cpp
struct Slot
{
    uint32_t id;
    std::function<void(const void*)> fn;
    int priority = 0;  // Add this
};

template<typename T>
SubscriptionHandle subscribe(std::function<void(const T&)> handler, int priority = 0)
{
    const uint32_t id = nextID++;
    auto wrapper = [h = std::move(handler)](const void* ptr) {
        h(*static_cast<const T*>(ptr));
    };
    slots[std::type_index(typeid(T))].push_back({id, std::move(wrapper), priority});
    handleToType.emplace(id, std::type_index(typeid(T)));
    
    // Sort by priority descending
    auto& list = slots[std::type_index(typeid(T))];
    std::sort(list.begin(), list.end(), 
        [](const Slot& a, const Slot& b) { return a.priority > b.priority; });
    
    return SubscriptionHandle{id};
}
```

**Why:** Higher-priority handlers execute first. Useful for UI (high priority) vs. gameplay logic (normal priority).

---

### Step 13: Cancellation

Event payloads optionally inherit `CancellableEvent`:
```cpp
namespace sle::events {
    struct CancellableEvent
    {
        mutable bool cancelled = false;
    };
}

// Example: CollisionBeginEvent becomes cancellable
struct CollisionBeginEvent : sle::events::CancellableEvent
{
    sle::entity::Entity entityA;
    sle::entity::Entity entityB;
};
```

In `emit()`:
```cpp
template<typename T>
void emit(const T& event)
{
    auto it = slots.find(std::type_index(typeid(T)));
    if (it == slots.end()) return;

    const auto snapshot = it->second;
    for (const auto& slot : snapshot)
    {
        slot.fn(static_cast<const void*>(&event));
        
        // Check if event is cancellable and was cancelled
        if constexpr (std::is_base_of_v<sle::events::CancellableEvent, T>)
        {
            if (static_cast<const sle::events::CancellableEvent&>(event).cancelled)
                break;
        }
    }
}
```

**Why:** Handlers can veto event propagation. Useful for input events, confirmations, etc.

---

### Step 14: Filters

```cpp
template<typename T>
SubscriptionHandle subscribe(
    std::function<void(const T&)> handler,
    std::function<bool(const T&)> filter = nullptr,
    int priority = 0)
{
    // Store filter alongside handler
    // In emit, call filter(event) before invoking handler
}
```

**Why:** Declarative condition checking. Avoid boilerplate if/else in handler bodies.

---

### Step 15: Propagation

Entity-local EventBus component:
```cpp
struct LocalEventBusComponent
{
    sle::events::EventBus bus;
};
```

Emit with propagation flag:
```cpp
enum class EventPropagation { LOCAL_ONLY, TO_SCENE, TO_GLOBAL };

template<typename T>
void emit(const T& event, EventPropagation prop = EventPropagation::TO_SCENE)
{
    if (prop == EventPropagation::LOCAL_ONLY)
        // emit only locally
    else if (prop == EventPropagation::TO_SCENE)
        // emit to scene bus
    else if (prop == EventPropagation::TO_GLOBAL)
        // emit to global bus
}
```

**Why:** Entity isolation while maintaining engine-wide event visibility when needed.

---

## File Change Checklist (Phase 1)

| File | Change | Type |
|------|--------|------|
| `EngineModules/Core/include/sle/core/EventBus.hpp` | Add `queue<T>()`, `flushQueue()`, `clearSubscriptions()` | Edit |
| `EngineModules/CMakeLists.txt` | Add `add_subdirectory(Events)` after Scene | Edit |
| `EngineModules/Events/CMakeLists.txt` | NEW — library definition | Create |
| `EngineModules/Events/include/sle/events/CollisionEvents.hpp` | NEW — moved from Scene/scene/events/ | Create |
| `EngineModules/Events/include/sle/events/ZoneEvents.hpp` | NEW — moved from Scene/scene/events/ | Create |
| `EngineModules/Events/include/sle/events/SceneLifecycleEvents.hpp` | NEW | Create |
| `EngineModules/Scene/include/sle/scene/events/CollisionEvents.hpp` | Replace with forwarding include | Edit |
| `EngineModules/Scene/include/sle/scene/events/ZoneEvents.hpp` | Replace with forwarding include | Edit |
| `EngineModules/Physics/CMakeLists.txt` | Add `sle::events` link | Edit |
| `EngineModules/Physics/src/ContactListener.cpp` | Update includes + 4× emit→queue | Edit |
| `EngineModules/Scene/src/Scene.cpp` | Add `clearSubscriptions()` in destroy | Edit |
| `EngineModules/Systems/src/Runtime.cpp` | Replace `clear()` with `flushQueue()` + add global bus | Edit |
| `EngineModules/Systems/src/PhysicsSystem.cpp` | Replace static bool with member tracking | Edit |
| `EngineModules/Systems/include/sle/engine/PhysicsSystem.hpp` | Add `lastInjectedWorld_` member | Edit |
| `EngineModules/Systems/include/sle/engine/Context.hpp` | Add `globalBus_` field | Edit |
| `EngineModules/Systems/include/sle/engine/Runtime.hpp` | Add `globalBus_` member + getter | Edit |
| `EngineModules/Systems/src/SceneManager.cpp` | Emit SceneLoad/UnloadEvents | Edit |

---

## Safe Build Order (Incremental Compilation)

| Build # | Steps | Risk Level | Ready? |
|---------|-------|-----------|--------|
| **1** | EventBus: queue, flushQueue, clearSubscriptions | ✅ Additive only | ✅ Yes |
| **2** | Runtime flushQueue, Scene clearSubscriptions, PhysicsSystem fix | ✅ Fixes 3 bugs | ✅ Yes |
| **3** | Events module + moved headers + compat redirects + ContactListener queue | ✅ Structural | ✅ Yes |
| **4** | Global bus + Context + SceneLifecycleEvents | ✅ Additive | ✅ Yes |
| **5** | ScopedSubscription (Phase 2) | ✅ Header-only | ✅ Yes |
| **6** | ScriptApi event interface + ScriptApiImpl + LuaBindingsEvents | ✅ Feature | ✅ Yes |
| **7** | Priorities, cancellation, filters (Phase 3) | ✅ Extension | ✅ Yes |

Each build can be compiled and tested independently before moving to the next.

---

## Testing Strategy

### Phase 1 Test Scenario (after Build 2)

- Create test script that subscribes to "collision.begin" during init
- Drop two physics bodies, verify collision event fires exactly once (not double-fired)
- Switch scenes, verify old subscriptions don't fire
- Log all events to verify queue/flush ordering

### Phase 2 Test

- Lua script subscribes to collision in update
- Verify subscription works from same frame onward
- Test unsubscribe
- Create/destroy entities, verify auto-cleanup

### Phase 3 Test

- High-priority handler blocks low-priority one (cancellation)
- Filter-based subscription only fires on specific conditions
- Local event stays local, scene event propagates, global event visible everywhere

---

## Summary of Design Decisions

1. **Queue vs Emit split:** Physics events queue; script handlers never run mid-physics-step. Safe re-entrancy.
2. **One-frame delay:** Queued events dispatch at frame START, ensuring all systems see consistent state.
3. **Global vs Scene buses:** Lifecycle events (scene load/unload) live on global bus; gameplay events on per-scene bus.
4. **RAII subscriptions:** Phase 2 introduces `ScopedSubscription` for auto-cleanup. Phase 1 is C++-side only.
5. **Lua event API:** Phase 2 exposes event subscription to Lua; scripts replace polling with event-driven patterns.
6. **Entity auto-cleanup:** Phase 2 tracks subscriptions per entity; entity destruction auto-unsubscribes.
