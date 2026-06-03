#pragma once

#include <sle/engine/ChunkManager.hpp>
#include <sle/scene/Entity.hpp>

#include <vector>

namespace sle::entity {
class Registry;
}

namespace sle {

struct ChunkOwnershipEntry
{
    ChunkCoord coord;
    std::vector<sle::entity::Entity> entities;
};

class ChunkOwnershipIndex
{
public:
    void rebuild(sle::entity::Registry& registry);

    const std::vector<ChunkOwnershipEntry>& getEntries() const { return entries; }
    const ChunkOwnershipEntry* find(const ChunkCoord& coord) const;
    std::vector<sle::entity::Entity> getEntitiesForChunk(const ChunkCoord& coord) const;

private:
    std::vector<ChunkOwnershipEntry> entries;
};

} // namespace sle
