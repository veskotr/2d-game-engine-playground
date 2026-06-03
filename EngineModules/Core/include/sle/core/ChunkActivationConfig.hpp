#pragma once

#include <cstdint>

namespace sle::core {

enum class ChunkActivationMode : std::uint8_t
{
    Disabled = 0,
    EventsOnly = 1,
    AutoToggle = 2,
};

struct ChunkActivationConfig
{
    ChunkActivationMode mode = ChunkActivationMode::EventsOnly;

    bool affectScripts = true;
    bool affectStateMachines = true;
    bool affectAnimation = true;
    bool affectPhysics = true;
    bool affectCollision = true;
    bool affectZones = true;
    bool affectUi = true;
};

} // namespace sle::core
