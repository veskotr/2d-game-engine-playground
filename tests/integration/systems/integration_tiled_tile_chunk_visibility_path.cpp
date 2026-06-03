#include <sle/engine/ChunkSceneRuntimeState.hpp>
#include <sle/engine/SceneLoader.hpp>
#include <sle/scene/Scene.hpp>
#include <sle/scene/components/ChunkActivationComponent.hpp>
#include <sle/scene/components/SpriteRenderer.hpp>

#include <iostream>

int main()
{
    sle::entity::Scene scene;
    auto loadResult = sle::SceneLoader::load("tests/data/scenes/test_tiled_tile_chunks_scene.json", scene);
    if (!loadResult.ok())
    {
        std::cerr << "SceneLoader failed for tiled tile chunk visibility scene: " << loadResult.error() << "\n";
        return 1;
    }

    sle::ChunkSceneRuntimeState runtimeState({
        .mode = sle::core::ChunkActivationMode::AutoToggle
    });
    runtimeState.refreshFromScene(scene);
    if (!runtimeState.isEnabled())
    {
        std::cerr << "Expected chunk runtime state to enable itself for the tile chunk test scene\n";
        return 1;
    }

    runtimeState.update(scene, {10.0f, 10.0f});

    bool sawOriginTile = false;
    bool sawFarTile = false;
    bool originVisibleAtOrigin = false;
    bool farHiddenAtOrigin = false;

    auto& registry = scene.getRegistry();
    registry.view<sle::components::ChunkActivationComponent, sle::components::SpriteRenderer>(
        [&](sle::entity::Entity, sle::components::ChunkActivationComponent& chunk, sle::components::SpriteRenderer& sprite)
        {
            if (chunk.homeChunkX == 0)
            {
                sawOriginTile = true;
                originVisibleAtOrigin = sprite.visible;
            }
            else if (chunk.homeChunkX == 2)
            {
                sawFarTile = true;
                farHiddenAtOrigin = !sprite.visible;
            }
        });

    if (!sawOriginTile || !sawFarTile)
    {
        std::cerr << "Expected both near and far chunk-tagged tile entities in the tile visibility scene\n";
        return 1;
    }

    if (!originVisibleAtOrigin || !farHiddenAtOrigin)
    {
        std::cerr << "Expected only nearby chunk tiles to remain visible at the origin focus position\n";
        return 1;
    }

    runtimeState.update(scene, {1100.0f, 10.0f});

    bool originHiddenFarAway = false;
    bool farVisibleFarAway = false;
    registry.view<sle::components::ChunkActivationComponent, sle::components::SpriteRenderer>(
        [&](sle::entity::Entity, sle::components::ChunkActivationComponent& chunk, sle::components::SpriteRenderer& sprite)
        {
            if (chunk.homeChunkX == 0)
                originHiddenFarAway = !sprite.visible;
            else if (chunk.homeChunkX == 2)
                farVisibleFarAway = sprite.visible;
        });

    if (!originHiddenFarAway || !farVisibleFarAway)
    {
        std::cerr << "Expected tile visibility to follow the active chunk window as focus moves across the map\n";
        return 1;
    }

    return 0;
}
