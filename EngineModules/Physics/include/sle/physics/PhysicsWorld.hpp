#pragma once
#include <glm/glm.hpp>
#include <sle/scene/components/RigidBodyComponent.hpp>
#include <sle/scene/components/BoxColliderComponent.hpp>
#include <sle/scene/components/CircleColliderComponent.hpp>
#include <sle/scene/components/BoxZoneComponent.hpp>
#include <sle/scene/components/CircleZoneComponent.hpp>
#include <memory>
#include <unordered_map>
#include <functional>
#include <cstdint>

// Forward declarations
class b2World;
class b2Body;

namespace sle::events {
class EventBus;
}

namespace sle::physics {

class ContactListener;

// Central physics world manager wrapping Box2D.
// Owns the b2World instance and manages all physics bodies and fixtures.
// Handles fixed timestep physics stepping and event dispatching.
class PhysicsWorld
{
public:
    // Initialize with gravity (default: (0, -9.81))
    explicit PhysicsWorld(const glm::vec2& gravity = {0.0f, -9.81f});
    ~PhysicsWorld();

    // Non-copyable
    PhysicsWorld(const PhysicsWorld&) = delete;
    PhysicsWorld& operator=(const PhysicsWorld&) = delete;

    // ============ World Management ============

    // Step the physics simulation by the given delta time.
    // Uses accumulator pattern for fixed timestep (60 Hz by default).
    void step(float deltaTime);

    // Set gravity for the entire world
    void setGravity(const glm::vec2& gravity);
    glm::vec2 getGravity() const;

    // Configure fixed timestep (default: 1/60)
    void setFixedTimestep(float timestep) { fixedTimestep_ = timestep; }
    float getFixedTimestep() const { return fixedTimestep_; }

    // Unit conversion helpers between engine world units and Box2D meters.
    // World space in this engine is pixel-like, while Box2D expects meter-like units.
    float worldToPhysicsLength(float worldValue) const;
    float physicsToWorldLength(float physicsValue) const;
    glm::vec2 worldToPhysicsVec2(const glm::vec2& worldValue) const;
    glm::vec2 physicsToWorldVec2(const glm::vec2& physicsValue) const;

    // ============ Body Management ============

    // Create a physics body for an entity.
    // Returns the Box2D body ID (uintptr_t cast of b2Body pointer, stored as uint32_t).
    uint32_t createBody(const glm::vec2& position, const sle::components::RigidBodyComponent& rigidBody);

    // Store the entity ID in the body's Box2D user data.
    // Must be called after createBody so ContactListener can identify entities in collision callbacks.
    void setBodyEntityId(uint32_t bodyId, uint32_t entityId);

    // Destroy a physics body and all its fixtures
    void destroyBody(uint32_t bodyId);

    // Update body's transform (position and rotation)
    void setBodyTransform(uint32_t bodyId, const glm::vec2& position, float rotation);

    // Get body's current transform
    void getBodyTransform(uint32_t bodyId, glm::vec2& outPosition, float& outRotation) const;

    // Set body's linear velocity
    void setBodyVelocity(uint32_t bodyId, const glm::vec2& velocity);

    // Get body's current linear velocity
    glm::vec2 getBodyVelocity(uint32_t bodyId) const;

    // Set body's angular velocity
    void setBodyAngularVelocity(uint32_t bodyId, float angularVelocity);

    // Get body's current angular velocity
    float getBodyAngularVelocity(uint32_t bodyId) const;

    // Set/get per-body gravity scale multiplier.
    void setBodyGravityScale(uint32_t bodyId, float gravityScale);
    float getBodyGravityScale(uint32_t bodyId) const;

    // Apply force to body (accumulated over the frame)
    void applyForce(uint32_t bodyId, const glm::vec2& force);

    // Apply instantaneous impulse to body
    void applyImpulse(uint32_t bodyId, const glm::vec2& impulse);

    // Set the EventBus so ContactListener can dispatch collision/zone events.
    // Should be called once per scene load with the scene's EventBus.
    void setEventBus(sle::events::EventBus* eventBus);

    // ============ Fixture Management ============

    // Create a box collider fixture on a body
    uintptr_t createBoxFixture(uint32_t bodyId, const sle::components::BoxColliderComponent& collider);

    // Create a circle collider fixture on a body
    uintptr_t createCircleFixture(uint32_t bodyId, const sle::components::CircleColliderComponent& collider);

    // Create a box trigger (sensor) on a body
    uintptr_t createBoxZone(uint32_t bodyId, const sle::components::BoxZoneComponent& zone);

    // Create a circle trigger (sensor) on a body
    uintptr_t createCircleZone(uint32_t bodyId, const sle::components::CircleZoneComponent& zone);

    // Destroy a fixture
    void destroyFixture(uint32_t bodyId, uintptr_t fixtureId);

    // Return the zone ID string for a sensor fixture, or empty string if not found.
    const std::string& getFixtureZoneId(uintptr_t fixtureId) const;

    // ============ Debug Access ============

    // Get raw Box2D world pointer (for advanced usage)
    b2World* getRawWorld() { return world_.get(); }

private:
    // Helper to get b2Body from ID
    b2Body* getBodyPtr(uint32_t bodyId) const;

    std::unique_ptr<b2World> world_;
    std::unique_ptr<ContactListener> contactListener_;
    std::unordered_map<uint32_t, b2Body*> bodyMap_;
    std::unordered_map<uintptr_t, std::string> fixtureZoneIds_;  // sensor fixture -> zoneId

    // Fixed timestep configuration
    float fixedTimestep_ = 1.0f / 60.0f;
    float accumulator_ = 0.0f;

    // How many world units correspond to one Box2D meter.
    // 50 is a good default for pixel-art style worlds.
    float pixelsPerMeter_ = 50.0f;

    friend class ContactListener;
};

} // namespace sle::physics
