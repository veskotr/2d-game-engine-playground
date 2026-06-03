#include <sle/engine/ChunkOwnershipIndex.hpp>
#include <sle/engine/SceneLoader.hpp>
#include <sle/scene/Scene.hpp>

#include <iostream>

int main()
{
    sle::entity::Scene scene;
    auto loadResult = sle::SceneLoader::load("tests/data/scenes/test_tiled_scene.json", scene);
    if (!loadResult.ok())
    {
        std::cerr << "SceneLoader failed for chunk ownership index scene: " << loadResult.error() << "\n";
        return 1;
    }

    sle::ChunkOwnershipIndex index;
    index.rebuild(scene.getRegistry());

    const auto* originEntry = index.find({0, 0});
    if (!originEntry)
    {
        std::cerr << "Expected chunk ownership entry for chunk (0,0)\n";
        return 1;
    }

    if (originEntry->entities.size() < 3)
    {
        std::cerr << "Expected at least three imported Tiled object entities in chunk (0,0)\n";
        return 1;
    }

    const auto emptyChunkEntities = index.getEntitiesForChunk({9, 9});
    if (!emptyChunkEntities.empty())
    {
        std::cerr << "Expected no entities in unrelated chunk (9,9)\n";
        return 1;
    }

    return 0;
}
