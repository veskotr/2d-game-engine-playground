#include <sle/engine/ChunkManager.hpp>

#include <algorithm>
#include <cmath>

namespace sle {
namespace {

int floorDiv(int numerator, int denominator)
{
    const float value = static_cast<float>(numerator) / static_cast<float>(denominator);
    return static_cast<int>(std::floor(value));
}

} // namespace

ChunkManager::ChunkManager(ChunkGridConfig configIn)
    : config(std::move(configIn))
{
    config.chunkWidthTiles = std::max(1, config.chunkWidthTiles);
    config.chunkHeightTiles = std::max(1, config.chunkHeightTiles);
    config.tileWidth = std::max(1, config.tileWidth);
    config.tileHeight = std::max(1, config.tileHeight);
    config.activeRadiusX = std::max(0, config.activeRadiusX);
    config.activeRadiusY = std::max(0, config.activeRadiusY);
}

bool ChunkManager::isValid() const
{
    return config.chunkWidthTiles > 0 &&
        config.chunkHeightTiles > 0 &&
        config.tileWidth > 0 &&
        config.tileHeight > 0;
}

int ChunkManager::getChunkWidthWorld() const
{
    return config.chunkWidthTiles * config.tileWidth;
}

int ChunkManager::getChunkHeightWorld() const
{
    return config.chunkHeightTiles * config.tileHeight;
}

ChunkCoord ChunkManager::worldToChunk(const glm::vec2& worldPosition) const
{
    const int chunkWidthWorld = getChunkWidthWorld();
    const int chunkHeightWorld = getChunkHeightWorld();

    return {
        floorDiv(static_cast<int>(std::floor(worldPosition.x)), chunkWidthWorld),
        floorDiv(static_cast<int>(std::floor(worldPosition.y)), chunkHeightWorld)
    };
}

ChunkBounds ChunkManager::getChunkBounds(const ChunkCoord& coord) const
{
    const float width = static_cast<float>(getChunkWidthWorld());
    const float height = static_cast<float>(getChunkHeightWorld());

    const glm::vec2 min{
        static_cast<float>(coord.x) * width,
        static_cast<float>(coord.y) * height
    };

    return {min, {min.x + width, min.y + height}};
}

std::vector<ChunkCoord> ChunkManager::computeActiveWindow(const glm::vec2& focusWorldPosition) const
{
    const ChunkCoord center = worldToChunk(focusWorldPosition);
    std::vector<ChunkCoord> result;
    result.reserve(static_cast<std::size_t>((config.activeRadiusX * 2 + 1) * (config.activeRadiusY * 2 + 1)));

    for (int y = center.y - config.activeRadiusY; y <= center.y + config.activeRadiusY; ++y)
    {
        for (int x = center.x - config.activeRadiusX; x <= center.x + config.activeRadiusX; ++x)
            result.push_back({x, y});
    }

    return result;
}

} // namespace sle
