#pragma once
#include <glm/glm.hpp>
#include <cstdint>

namespace sle::components {

// Box collider for rectangular collision shapes (axis-aligned).
// Requires an entity to also have a RigidBodyComponent to be physically simulated.
// Can be used for collision detection (solid objects) or as triggers (sensors).
struct BoxColliderComponent
{
    // Geometry (in entity's local space)
    glm::vec2 offset{0.0f, 0.0f};   // Offset from entity position
    glm::vec2 size{1.0f, 1.0f};     // Width and height of the box

    // Physics material properties
    float friction = 0.4f;        // Friction coefficient (0-1+)
    float restitution = 0.0f;     // Bounciness (0-1, 0 = no bounce, 1 = perfect bounce)
    float density = 1.0f;         // Mass per unit area (used to calculate mass)

    // Collision filtering (bitmasks for Layer-based collision detection)
    uint16_t categoryBits = 0x0001;  // Which category does this collider belong to?
    uint16_t maskBits = 0xFFFF;      // Which categories can this collider collide with?

    // Enable/disable without removing the component
    bool enabled = true;

    // Internal: Handle to Box2D fixture (set by physics system)
    // Not user-facing; don't modify directly
    uintptr_t box2dFixtureId = 0;
};

} // namespace sle::components
