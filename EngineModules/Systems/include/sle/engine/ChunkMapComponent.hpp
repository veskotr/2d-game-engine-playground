#pragma once

#include <sle/engine/ChunkManager.hpp>

#include <string>

namespace sle {

struct ChunkMapComponent
{
    std::string mapId;
    ChunkGridConfig gridConfig{};
    bool enabled = true;
};

} // namespace sle
