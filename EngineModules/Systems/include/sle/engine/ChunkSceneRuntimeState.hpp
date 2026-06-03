#pragma once

#include <sle/core/ChunkActivationConfig.hpp>
#include <sle/engine/ChunkActivationController.hpp>
#include <sle/engine/ChunkActivityTracker.hpp>
#include <sle/engine/ChunkOwnershipIndex.hpp>

#include <glm/vec2.hpp>

#include <optional>

namespace sle::entity {
class Scene;
}

namespace sle {

class ChunkSceneRuntimeState
{
public:
    explicit ChunkSceneRuntimeState(sle::core::ChunkActivationConfig config = {});

    void refreshFromScene(sle::entity::Scene& scene);
    void update(sle::entity::Scene& scene, const glm::vec2& focusWorldPosition);

    bool isEnabled() const { return tracker.has_value(); }
    const ChunkActivityTracker* getTracker() const { return tracker ? &(*tracker) : nullptr; }

private:
    ChunkActivationController controller;
    std::optional<ChunkActivityTracker> tracker;
    ChunkOwnershipIndex ownershipIndex;
};

} // namespace sle
