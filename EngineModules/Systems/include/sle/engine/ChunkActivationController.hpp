#pragma once

#include <sle/core/ChunkActivationConfig.hpp>
#include <sle/engine/ChunkOwnershipIndex.hpp>
#include <sle/scene/components/ChunkActivationComponent.hpp>

#include <vector>

namespace sle::entity {
class Registry;
}

namespace sle {

struct ChunkActivityUpdate;

class ChunkActivationController
{
public:
    explicit ChunkActivationController(sle::core::ChunkActivationConfig config = {});

    const sle::core::ChunkActivationConfig& getConfig() const { return config; }

    void applyUpdate(
        const ChunkActivityUpdate& update,
        const ChunkOwnershipIndex& ownershipIndex,
        sle::entity::Registry& registry) const;

    void applyActiveChunks(
        const std::vector<ChunkCoord>& activeChunks,
        const ChunkOwnershipIndex& ownershipIndex,
        sle::entity::Registry& registry) const;

private:
    sle::core::ChunkActivationMode resolveMode(const components::ChunkActivationComponent& activation) const;
    void applyEntityState(sle::entity::Registry& registry, sle::entity::Entity entity, bool active) const;

    sle::core::ChunkActivationConfig config;
};

} // namespace sle
