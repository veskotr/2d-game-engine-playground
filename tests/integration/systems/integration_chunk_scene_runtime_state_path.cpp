#include <sle/engine/ChunkSceneRuntimeState.hpp>
#include <sle/engine/SceneLoader.hpp>
#include <sle/events/ChunkEvents.hpp>
#include <sle/scene/Scene.hpp>
#include <sle/scene/ChunkActivity.hpp>
#include <sle/scene/components/BoxColliderComponent.hpp>
#include <sle/scene/components/BoxZoneComponent.hpp>
#include <sle/scene/components/ScriptComponent.hpp>

#include <iostream>
#include <string>
#include <vector>

int main()
{
    sle::entity::Scene scene;
    auto loadResult = sle::SceneLoader::load("tests/data/scenes/test_tiled_scene.json", scene);
    if (!loadResult.ok())
    {
        std::cerr << "SceneLoader failed for chunk runtime scene: " << loadResult.error() << "\n";
        return 1;
    }

    std::vector<std::string> eventLog;
    scene.getEventBus().subscribe<sle::events::ChunkActivatedEvent>(
        [&eventLog](const sle::events::ChunkActivatedEvent& event)
        {
            eventLog.push_back("activate:" + std::to_string(event.chunkX) + ":" + std::to_string(event.chunkY));
        });
    scene.getEventBus().subscribe<sle::events::ChunkDeactivatedEvent>(
        [&eventLog](const sle::events::ChunkDeactivatedEvent& event)
        {
            eventLog.push_back("deactivate:" + std::to_string(event.chunkX) + ":" + std::to_string(event.chunkY));
        });

    sle::ChunkSceneRuntimeState runtimeState({
        .mode = sle::core::ChunkActivationMode::AutoToggle
    });
    runtimeState.refreshFromScene(scene);

    if (!runtimeState.isEnabled())
    {
        std::cerr << "Expected chunk runtime state to enable itself for a Tiled chunk-backed scene\n";
        return 1;
    }

    runtimeState.update(scene, {10.0f, 10.0f});

    auto& registry = scene.getRegistry();
    bool sawEnabledScript = false;
    bool sawEnabledZone = false;
    bool sawEnabledCollider = false;

    registry.view<sle::components::ScriptComponent>(
        [&](sle::entity::Entity entity, sle::components::ScriptComponent& script)
        {
            sawEnabledScript = (script.enabled && sle::entity::isChunkActive(registry, entity, sle::components::ChunkSubsystem::Scripts));
        });
    registry.view<sle::components::BoxZoneComponent>(
        [&](sle::entity::Entity entity, sle::components::BoxZoneComponent& zone)
        {
            sawEnabledZone = (zone.enabled && sle::entity::isChunkActive(registry, entity, sle::components::ChunkSubsystem::Zones));
        });
    registry.view<sle::components::BoxColliderComponent>(
        [&](sle::entity::Entity entity, sle::components::BoxColliderComponent& collider)
        {
            sawEnabledCollider = (collider.enabled && sle::entity::isChunkActive(registry, entity, sle::components::ChunkSubsystem::Collision));
        });

    if (!sawEnabledScript || !sawEnabledZone || !sawEnabledCollider)
    {
        std::cerr << "Expected origin-chunk entities to be active when focus is near the map origin\n";
        return 1;
    }

    runtimeState.update(scene, {1300.0f, 10.0f});

    bool sawDisabledScript = false;
    bool sawDisabledZone = false;
    bool sawDisabledCollider = false;

    registry.view<sle::components::ScriptComponent>(
        [&](sle::entity::Entity entity, sle::components::ScriptComponent& script)
        {
            sawDisabledScript = !(script.enabled && sle::entity::isChunkActive(registry, entity, sle::components::ChunkSubsystem::Scripts));
        });
    registry.view<sle::components::BoxZoneComponent>(
        [&](sle::entity::Entity entity, sle::components::BoxZoneComponent& zone)
        {
            sawDisabledZone = !(zone.enabled && sle::entity::isChunkActive(registry, entity, sle::components::ChunkSubsystem::Zones));
        });
    registry.view<sle::components::BoxColliderComponent>(
        [&](sle::entity::Entity entity, sle::components::BoxColliderComponent& collider)
        {
            sawDisabledCollider = !(collider.enabled && sle::entity::isChunkActive(registry, entity, sle::components::ChunkSubsystem::Collision));
        });

    if (!sawDisabledScript || !sawDisabledZone || !sawDisabledCollider)
    {
        std::cerr << "Expected origin-chunk entities to disable when focus moves beyond the active chunk window\n";
        return 1;
    }

    runtimeState.update(scene, {10.0f, 10.0f});
    bool sawReenabledScript = false;
    registry.view<sle::components::ScriptComponent>(
        [&](sle::entity::Entity entity, sle::components::ScriptComponent& script)
        {
            sawReenabledScript = (script.enabled && sle::entity::isChunkActive(registry, entity, sle::components::ChunkSubsystem::Scripts));
        });

    if (!sawReenabledScript)
    {
        std::cerr << "Expected chunk-managed entities to re-enable when focus returns\n";
        return 1;
    }

    if (eventLog.size() < 3)
    {
        std::cerr << "Expected chunk runtime state updates to emit lifecycle events\n";
        return 1;
    }

    return 0;
}
