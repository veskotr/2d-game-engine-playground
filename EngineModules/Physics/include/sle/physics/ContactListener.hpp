#pragma once
#include <box2d/b2_world_callbacks.h>
#include <functional>
#include <cstdint>
#include <unordered_map>
#include <string>

namespace sle::core {
class EventBus;
}

namespace sle::physics {

// Handles Box2D collision and zone trigger callbacks.
// Distinguishes between solid collisions and sensor overlaps, dispatching
// appropriate events to the event bus.
class ContactListener : public b2ContactListener
{
public:
    explicit ContactListener(sle::core::EventBus* eventBus, std::unordered_map<uintptr_t, std::string>* fixtureZoneIds = nullptr);
    virtual ~ContactListener() = default;

    // Set or update the event bus for event dispatching
    void setEventBus(sle::core::EventBus* eventBus) { eventBus_ = eventBus; }

    // Called when two fixtures begin touching
    void BeginContact(b2Contact* contact) override;

    // Called when two fixtures stop touching
    void EndContact(b2Contact* contact) override;

    // Called after collision resolution to allow custom behavior
    void PreSolve(b2Contact* contact, const b2Manifold* oldManifold) override;

    // Called after all collision response has been processed
    void PostSolve(b2Contact* contact, const b2ContactImpulse* impulse) override;

private:
    sle::core::EventBus* eventBus_;
    std::unordered_map<uintptr_t, std::string>* fixtureZoneIds_;  // ref to zone ID map in PhysicsWorld

    // Helper method to determine if a contact involves a sensor (zone trigger)
    bool isSensorContact(b2Contact* contact) const;
};

} // namespace sle::physics
