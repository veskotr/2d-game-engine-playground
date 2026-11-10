#pragma once

#include <cstdint>

namespace sle::components {

enum class ChunkEntityActivationMode : std::uint8_t
{
    Inherit = 0,
    AlwaysActive = 1,
    EventsOnly = 2,
    AutoToggle = 3,
};

enum class ChunkSubsystem : std::uint8_t
{
    Scripts = 1 << 0,
    StateMachines = 1 << 1,
    Animation = 1 << 2,
    Physics = 1 << 3,
    Collision = 1 << 4,
    Zones = 1 << 5,
    Ui = 1 << 6,
    Rendering = 1 << 7,
};

struct ChunkActivationComponent
{
    int homeChunkX = 0;
    int homeChunkY = 0;
    bool affectsActivation = true;
    ChunkEntityActivationMode mode = ChunkEntityActivationMode::Inherit;
    bool keepRenderingWhenInactive = false;
    bool keepPhysicsWhenInactive = false;
    bool keepScriptsWhenInactive = false;

    // Runtime-only suspension, independent of gameplay enabled/visible fields.
    std::uint8_t suspendedSubsystems = 0;
};

} // namespace sle::components
