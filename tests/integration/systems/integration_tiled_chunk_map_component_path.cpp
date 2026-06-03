#include <sle/engine/ChunkMapComponent.hpp>
#include <sle/engine/SceneLoader.hpp>
#include <sle/scene/Scene.hpp>

#include <iostream>

int main()
{
    sle::entity::Scene scene;
    auto loadResult = sle::SceneLoader::load("tests/data/scenes/test_tiled_scene.json", scene);
    if (!loadResult.ok())
    {
        std::cerr << "SceneLoader failed for chunk map component scene: " << loadResult.error() << "\n";
        return 1;
    }

    int chunkMapCount = 0;
    bool sawExpectedConfig = false;

    scene.getRegistry().view<sle::ChunkMapComponent>(
        [&](sle::entity::Entity, sle::ChunkMapComponent& chunkMap)
        {
            ++chunkMapCount;
            sawExpectedConfig =
                !chunkMap.mapId.empty() &&
                chunkMap.gridConfig.chunkWidthTiles == 16 &&
                chunkMap.gridConfig.chunkHeightTiles == 16 &&
                chunkMap.gridConfig.tileWidth == 32 &&
                chunkMap.gridConfig.tileHeight == 32 &&
                chunkMap.gridConfig.activeRadiusX == 1 &&
                chunkMap.gridConfig.activeRadiusY == 1;
        });

    if (chunkMapCount != 1)
    {
        std::cerr << "Expected exactly one chunk map metadata component for the loaded Tiled scene\n";
        return 1;
    }

    if (!sawExpectedConfig)
    {
        std::cerr << "Expected Tiled chunk map metadata to preserve the map chunk grid config\n";
        return 1;
    }

    return 0;
}
