#pragma once

#include <sle/renderer/TextureRegion.hpp>

#include <cstdint>
#include <map>
#include <string>
#include <utility>
#include <vector>

namespace sle::entity {

using TileCoordinate = std::pair<std::int32_t, std::int32_t>;
inline constexpr int TileChunkSize = 32;

constexpr int tileChunkIndex(std::int32_t coordinate)
{
    return coordinate / TileChunkSize - (coordinate % TileChunkSize < 0 ? 1 : 0);
}

struct TileCell
{
    TileCoordinate coordinate;
    std::string tileId;
    glm::vec2 lowerLeft{};
    glm::vec2 size{1};
    renderer::TextureRegion region;
    bool missing = false;
};

struct TileChunk
{
    std::vector<TileCell> cells;
    glm::vec2 lowerLeft{};
    glm::vec2 upperRight{};
};

struct TileCollision
{
    bool fullCell = false;
    std::vector<std::vector<glm::vec2>> pieces; // Tile-local world units.
};

struct TileLayer
{
    std::string id;
    std::string name;
    std::string tileset;
    glm::vec2 origin{};
    glm::vec2 cellSize{1};
    float pixelsPerUnit = 1;
    std::uint32_t drawOrder = 0;
    bool visible = true;
    bool locked = false;
    bool collisionEnabled = true;
    std::map<TileCoordinate, TileChunk> chunks;
    std::map<std::string, TileCollision> collisionByTile;
};

} // namespace sle::entity
