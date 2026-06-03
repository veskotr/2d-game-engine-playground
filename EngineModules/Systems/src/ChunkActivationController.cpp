#include <sle/engine/ChunkActivationController.hpp>

#include <sle/engine/ChunkActivityTracker.hpp>
#include <sle/scene/Registry.hpp>
#include <sle/scene/components/AnimatorComponent.hpp>
#include <sle/scene/components/BoxColliderComponent.hpp>
#include <sle/scene/components/BoxZoneComponent.hpp>
#include <sle/scene/components/ChunkActivationComponent.hpp>
#include <sle/scene/components/CircleColliderComponent.hpp>
#include <sle/scene/components/CircleZoneComponent.hpp>
#include <sle/scene/components/RigidBodyComponent.hpp>
#include <sle/scene/components/ScriptComponent.hpp>
#include <sle/scene/components/StateMachineComponent.hpp>
#include <sle/scene/components/UIComponent.hpp>

#include <algorithm>

namespace sle {
namespace {

bool containsChunk(const std::vector<ChunkCoord>& activeChunks, const ChunkCoord& coord)
{
    return std::find(activeChunks.begin(), activeChunks.end(), coord) != activeChunks.end();
}

template <typename T>
void setOptionalEnabled(sle::entity::Registry& registry, sle::entity::Entity entity, bool enabled)
{
    if (auto* component = registry.getComponent<T>(entity))
        component->enabled = enabled;
}

} // namespace

ChunkActivationController::ChunkActivationController(sle::core::ChunkActivationConfig configIn)
    : config(std::move(configIn))
{
}

void ChunkActivationController::applyUpdate(
    const ChunkActivityUpdate& update,
    const ChunkOwnershipIndex& ownershipIndex,
    sle::entity::Registry& registry) const
{
    applyActiveChunks(update.active, ownershipIndex, registry);
}

void ChunkActivationController::applyActiveChunks(
    const std::vector<ChunkCoord>& activeChunks,
    const ChunkOwnershipIndex& ownershipIndex,
    sle::entity::Registry& registry) const
{
    if (config.mode == sle::core::ChunkActivationMode::Disabled ||
        config.mode == sle::core::ChunkActivationMode::EventsOnly)
    {
        return;
    }

    for (const auto& entry : ownershipIndex.getEntries())
    {
        const bool chunkIsActive = containsChunk(activeChunks, entry.coord);
        for (sle::entity::Entity entity : entry.entities)
        {
            auto* activation = registry.getComponent<sle::components::ChunkActivationComponent>(entity);
            if (!activation || !activation->affectsActivation)
                continue;

            const auto mode = resolveMode(*activation);
            if (mode == sle::core::ChunkActivationMode::Disabled ||
                mode == sle::core::ChunkActivationMode::EventsOnly)
            {
                continue;
            }

            if (activation->mode == sle::components::ChunkEntityActivationMode::AlwaysActive)
            {
                applyEntityState(registry, entity, true);
                continue;
            }

            applyEntityState(registry, entity, chunkIsActive);
        }
    }
}

sle::core::ChunkActivationMode ChunkActivationController::resolveMode(
    const components::ChunkActivationComponent& activation) const
{
    using EntityMode = sle::components::ChunkEntityActivationMode;
    using EngineMode = sle::core::ChunkActivationMode;

    switch (activation.mode)
    {
    case EntityMode::AlwaysActive:
        return EngineMode::AutoToggle;
    case EntityMode::EventsOnly:
        return EngineMode::EventsOnly;
    case EntityMode::AutoToggle:
        return EngineMode::AutoToggle;
    case EntityMode::Inherit:
    default:
        return config.mode;
    }
}

void ChunkActivationController::applyEntityState(
    sle::entity::Registry& registry,
    sle::entity::Entity entity,
    bool active) const
{
    const auto* activation = registry.getComponent<sle::components::ChunkActivationComponent>(entity);
    if (!activation)
        return;

    if (config.affectScripts && (!activation->keepScriptsWhenInactive || active))
        setOptionalEnabled<sle::components::ScriptComponent>(registry, entity, active);

    if (config.affectStateMachines)
        setOptionalEnabled<sle::components::StateMachineComponent>(registry, entity, active);

    if (config.affectAnimation)
        setOptionalEnabled<sle::components::AnimatorComponent>(registry, entity, active);

    if (!activation->keepPhysicsWhenInactive || active)
    {
        if (config.affectPhysics)
            setOptionalEnabled<sle::components::RigidBodyComponent>(registry, entity, active);

        if (config.affectCollision)
        {
            setOptionalEnabled<sle::components::BoxColliderComponent>(registry, entity, active);
            setOptionalEnabled<sle::components::CircleColliderComponent>(registry, entity, active);
        }

        if (config.affectZones)
        {
            setOptionalEnabled<sle::components::BoxZoneComponent>(registry, entity, active);
            setOptionalEnabled<sle::components::CircleZoneComponent>(registry, entity, active);
        }
    }

    if (config.affectUi && (!activation->keepRenderingWhenInactive || active))
    {
        if (auto* ui = registry.getComponent<sle::components::UIComponent>(entity))
            ui->visible = active;
    }
}

} // namespace sle
