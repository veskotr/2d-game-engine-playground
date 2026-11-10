#include <sle/engine/ChunkActivationController.hpp>

#include <sle/engine/ChunkActivityTracker.hpp>
#include <sle/scene/Registry.hpp>
#include <sle/scene/components/ChunkActivationComponent.hpp>

#include <algorithm>

namespace sle {
namespace {

bool containsChunk(const std::vector<ChunkCoord>& activeChunks, const ChunkCoord& coord)
{
    return std::find(activeChunks.begin(), activeChunks.end(), coord) != activeChunks.end();
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
    // Recompute rather than restore snapshots: gameplay may edit flags while
    // suspended, and policy changes must release any old suspension immediately.
    registry.view<components::ChunkActivationComponent>(
        [](sle::entity::Entity, components::ChunkActivationComponent& activation)
        {
            activation.suspendedSubsystems = 0;
        });

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
    auto* activation = registry.getComponent<components::ChunkActivationComponent>(entity);
    if (!activation || active)
        return;

    using Subsystem = components::ChunkSubsystem;
    auto suspend = [&](Subsystem subsystem, bool affected)
    {
        if (affected)
            activation->suspendedSubsystems |= static_cast<std::uint8_t>(subsystem);
    };
    suspend(Subsystem::Scripts, config.affectScripts && !activation->keepScriptsWhenInactive);
    suspend(Subsystem::StateMachines, config.affectStateMachines);
    suspend(Subsystem::Animation, config.affectAnimation);
    suspend(Subsystem::Physics, config.affectPhysics && !activation->keepPhysicsWhenInactive);
    suspend(Subsystem::Collision, config.affectCollision && !activation->keepPhysicsWhenInactive);
    suspend(Subsystem::Zones, config.affectZones && !activation->keepPhysicsWhenInactive);
    suspend(Subsystem::Ui, config.affectUi && !activation->keepRenderingWhenInactive);
    suspend(Subsystem::Rendering, config.affectRendering && !activation->keepRenderingWhenInactive);
}

} // namespace sle
