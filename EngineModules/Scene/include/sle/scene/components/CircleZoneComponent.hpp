#pragma once
#include <glm/glm.hpp>
#include <string>
#include <cstdint>

namespace sle::components {

// Circle trigger area (sensor) for entity interactions and zone detection.
// Unlike colliders, zones don't cause physical responses but still detect overlaps.
// Dispatches ZoneEnterEvent and ZoneExitEvent through the event bus.
struct CircleZoneComponent
{
    // Geometry (in entity's local space)
    glm::vec2 offset{0.0f, 0.0f};  // Offset from entity position
    float radius = 0.5f;            // Radius of the zone

    // Zone identification
    std::string zoneId;  // Unique identifier for this zone (used in events)

    // Collision filtering (same as colliders)
    uint16_t categoryBits = 0x0001;  // Which category does this zone belong to?
    uint16_t maskBits = 0xFFFF;      // Which entities can enter this zone?

    // Enable/disable without removing the component
    bool enabled = true;

    // Internal: Handle to Box2D fixture/sensor (set by physics system)
    // Not user-facing; don't modify directly
    uintptr_t box2dFixtureId = 0;
};

} // namespace sle::components
