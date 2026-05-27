#pragma once
#include <sle/engine/Context.hpp>
#include <unordered_map>
#include <unordered_set>
#include <cstdint>

namespace sle {

// Physics system that orchestrates physics simulation each frame.
//
// Responsibilities:
// 1. Create Box2D bodies and fixtures for entities with RigidBody/Collider/Zone components
// 2. Sync transform changes to physics bodies before stepping
// 3. Step the physics world at a fixed timestep
// 4. Sync physics results back to transforms after stepping
// 5. Clean up physics bodies when entities are destroyed
//
// Called between ScriptSystem and RenderSystem in the frame loop.
class PhysicsSystem
{
public:
    void update(Context& ctx);

private:
    // Helper functions for component lifecycle
    void createNewPhysicsBodies(const Context& ctx);
    void syncTransformToPhysics(const Context& ctx);
    void stepPhysics(const Context& ctx);
    void syncPhysicsToTransform(const Context& ctx);
    void destroyStalePhysics(const Context& ctx);

    // Track which entities have physics bodies so we can detect when they're destroyed
    std::unordered_set<uint32_t> activePhysicsEntities;
    // Keep body IDs even if components are removed so cleanup can still destroy Box2D bodies.
    std::unordered_map<uint32_t, uint32_t> entityBodyIds;
    // Track the last physics world we injected the event bus into (for re-injection on scene switches)
    sle::physics::PhysicsWorld* lastInjectedWorld_ = nullptr;
};

} // namespace sle
