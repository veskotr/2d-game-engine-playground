#include <sle/engine/ChunkOwnershipIndex.hpp>

#include <sle/scene/Registry.hpp>
#include <sle/scene/components/ChunkActivationComponent.hpp>

#include <algorithm>
#include <utility>

namespace sle {

void ChunkOwnershipIndex::rebuild(sle::entity::Registry& registry)
{
    entries.clear();

    registry.view<sle::components::ChunkActivationComponent>(
        [this](sle::entity::Entity entity, sle::components::ChunkActivationComponent& chunk)
        {
            const ChunkCoord coord{chunk.homeChunkX, chunk.homeChunkY};
            auto it = std::find_if(
                entries.begin(),
                entries.end(),
                [&coord](const ChunkOwnershipEntry& entry) { return entry.coord == coord; });

            if (it == entries.end())
            {
                entries.push_back(ChunkOwnershipEntry{coord, {entity}});
                return;
            }

            it->entities.push_back(entity);
        });

    std::sort(
        entries.begin(),
        entries.end(),
        [](const ChunkOwnershipEntry& lhs, const ChunkOwnershipEntry& rhs)
        {
            return lhs.coord < rhs.coord;
        });
}

const ChunkOwnershipEntry* ChunkOwnershipIndex::find(const ChunkCoord& coord) const
{
    auto it = std::find_if(
        entries.begin(),
        entries.end(),
        [&coord](const ChunkOwnershipEntry& entry) { return entry.coord == coord; });

    return it == entries.end() ? nullptr : &(*it);
}

std::vector<sle::entity::Entity> ChunkOwnershipIndex::getEntitiesForChunk(const ChunkCoord& coord) const
{
    const ChunkOwnershipEntry* entry = find(coord);
    return entry ? entry->entities : std::vector<sle::entity::Entity>{};
}

} // namespace sle
