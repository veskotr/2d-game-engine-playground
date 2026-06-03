#include <sle/engine/SceneLoader.hpp>
#include <sle/scene/Scene.hpp>
#include <sle/scene/components/ChunkActivationComponent.hpp>
#include <sle/scene/components/ScriptComponent.hpp>
#include <sle/scene/components/BoxZoneComponent.hpp>
#include <sle/scene/components/BoxColliderComponent.hpp>

#include <iostream>

int main()
{
    sle::entity::Scene scene;
    auto loadResult = sle::SceneLoader::load("tests/data/scenes/test_tiled_scene.json", scene);
    if (!loadResult.ok())
    {
        std::cerr << "SceneLoader failed for chunk assignment scene: " << loadResult.error() << "\n";
        return 1;
    }

    auto& registry = scene.getRegistry();
    int chunkTaggedCount = 0;
    bool sawZoneChunk = false;
    bool sawColliderChunk = false;
    bool sawScriptChunk = false;

    registry.view<sle::components::ChunkActivationComponent>(
        [&](sle::entity::Entity entity, sle::components::ChunkActivationComponent& chunk)
        {
            ++chunkTaggedCount;

            if (registry.hasComponent<sle::components::BoxZoneComponent>(entity))
                sawZoneChunk = (chunk.homeChunkX == 0 && chunk.homeChunkY == 0);
            if (registry.hasComponent<sle::components::BoxColliderComponent>(entity))
                sawColliderChunk = (chunk.homeChunkX == 0 && chunk.homeChunkY == 0);
            if (registry.hasComponent<sle::components::ScriptComponent>(entity))
                sawScriptChunk = (chunk.homeChunkX == 0 && chunk.homeChunkY == 0);
        });

    if (chunkTaggedCount < 3)
    {
        std::cerr << "Expected imported Tiled object entities to receive chunk metadata\n";
        return 1;
    }

    if (!sawZoneChunk || !sawColliderChunk || !sawScriptChunk)
    {
        std::cerr << "Expected Tiled object entities to map into chunk (0,0)\n";
        return 1;
    }

    return 0;
}
