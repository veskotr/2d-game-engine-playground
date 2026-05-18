#pragma once
#include <glm/glm.hpp>
#include <cstdint>

namespace sle::components {

// Defines the behavior type of a physics body
enum class BodyType : uint8_t
{
    Static = 0,     // Doesn't move, affected by nothing (e.g., ground, walls)
    Dynamic = 1,    // Affected by forces, gravity, and collisions
    Kinematic = 2   // Moves, not affected by forces or gravity but can have velocity
};

// Physics body component for entities with physics simulation.
// When combined with a collider component (BoxCollider, CircleCollider),
// this enables physics simulation for the entity.
struct RigidBodyComponent
{
    // Body type determines how the body behaves
    BodyType bodyType = BodyType::Dynamic;

    // Mass in kg (only used for dynamic bodies)
    float mass = 1.0f;

    // Damping factors (0-1, higher = more resistance)
    float linearDamping = 0.0f;   // Reduces velocity over time
    float angularDamping = 0.0f;  // Reduces rotational velocity

    // Multiplier on gravity for this body (0 = no gravity, 1 = normal)
    float gravityScale = 1.0f;

    // Velocity state (read/written by physics system)
    glm::vec2 velocity{0.0f, 0.0f};
    float angularVelocity = 0.0f;

    // Constraints
    bool fixedRotation = false;  // If true, body won't rotate

    // Enable/disable physics simulation without removing the component
    bool enabled = true;

    // Internal: Handle to Box2D body (set by physics system)
    // Not user-facing; don't modify directly
    uint32_t box2dBodyId = 0;
};

} // namespace sle::components
