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

struct ChunkActivationComponent
{
    int homeChunkX = 0;
    int homeChunkY = 0;
    bool affectsActivation = true;
    ChunkEntityActivationMode mode = ChunkEntityActivationMode::Inherit;
    bool keepRenderingWhenInactive = false;
    bool keepPhysicsWhenInactive = false;
    bool keepScriptsWhenInactive = false;
};

} // namespace sle::components
