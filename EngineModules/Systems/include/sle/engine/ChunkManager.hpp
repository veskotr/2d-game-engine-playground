#pragma once

#include <glm/vec2.hpp>

#include <cstddef>
#include <cstdint>
#include <vector>

namespace sle {

struct ChunkCoord
{
    int x = 0;
    int y = 0;

    bool operator==(const ChunkCoord& other) const = default;
    bool operator<(const ChunkCoord& other) const
    {
        return (y < other.y) || (y == other.y && x < other.x);
    }
};

struct ChunkBounds
{
    glm::vec2 min{0.0f};
    glm::vec2 max{0.0f};
};

struct ChunkGridConfig
{
    int chunkWidthTiles = 16;
    int chunkHeightTiles = 16;
    int tileWidth = 32;
    int tileHeight = 32;
    int activeRadiusX = 1;
    int activeRadiusY = 1;
};

class ChunkManager
{
public:
    explicit ChunkManager(ChunkGridConfig config = {});

    const ChunkGridConfig& getConfig() const { return config; }

    bool isValid() const;
    int getChunkWidthWorld() const;
    int getChunkHeightWorld() const;

    ChunkCoord worldToChunk(const glm::vec2& worldPosition) const;
    ChunkBounds getChunkBounds(const ChunkCoord& coord) const;
    std::vector<ChunkCoord> computeActiveWindow(const glm::vec2& focusWorldPosition) const;

private:
    ChunkGridConfig config;
};

} // namespace sle
