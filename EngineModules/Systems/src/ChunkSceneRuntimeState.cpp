#include <sle/engine/ChunkSceneRuntimeState.hpp>

#include <sle/engine/ChunkMapComponent.hpp>
#include <sle/scene/Scene.hpp>
#include <sle/scene/components/ChunkActivationComponent.hpp>

namespace sle {

ChunkSceneRuntimeState::ChunkSceneRuntimeState(sle::core::ChunkActivationConfig config)
    : controller(std::move(config))
{
}

void ChunkSceneRuntimeState::refreshFromScene(sle::entity::Scene& scene)
{
    scene.getRegistry().view<sle::components::ChunkActivationComponent>(
        [](sle::entity::Entity, sle::components::ChunkActivationComponent& activation)
        {
            activation.suspendedSubsystems = 0;
        });
    tracker.reset();
    ownershipIndex = {};

    auto& registry = scene.getRegistry();
    registry.view<sle::ChunkMapComponent>(
        [this](sle::entity::Entity, sle::ChunkMapComponent& chunkMap)
        {
            if (tracker || !chunkMap.enabled)
                return;

            tracker.emplace(ChunkManager(chunkMap.gridConfig), chunkMap.mapId);
        });

    if (!tracker)
        return;

    ownershipIndex.rebuild(registry);
}

void ChunkSceneRuntimeState::update(sle::entity::Scene& scene, const glm::vec2& focusWorldPosition)
{
    if (!tracker)
        return;

    ownershipIndex.rebuild(scene.getRegistry());
    const ChunkActivityUpdate activity = tracker->updateFocusPosition(focusWorldPosition, &scene.getEventBus());
    controller.applyUpdate(activity, ownershipIndex, scene.getRegistry());
}

} // namespace sle
