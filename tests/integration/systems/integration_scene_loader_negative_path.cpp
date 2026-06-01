#include <sle/engine/SceneLoader.hpp>
#include <sle/scene/Scene.hpp>
#include <sle/scene/components/Transform.hpp>

#include <iostream>

int main()
{
    {
        sle::entity::Scene scene;
        auto result = sle::SceneLoader::load("tests/data/scenes/test_unknown_component_scene.json", scene);
        if (!result.ok())
        {
            std::cerr << "Expected unknown-component scene to load successfully: " << result.error() << "\n";
            return 1;
        }

        auto& registry = scene.getRegistry();
        int transformCount = 0;
        registry.view<sle::components::TransformComponent>(
            [&](sle::entity::Entity, sle::components::TransformComponent&)
            {
                ++transformCount;
            });

        if (transformCount != 1)
        {
            std::cerr << "Expected one transform component in unknown-component scene\n";
            return 1;
        }
    }

    {
        sle::entity::Scene scene;
        auto result = sle::SceneLoader::load("tests/data/scenes/test_malformed_scene.json", scene);
        if (result.ok())
        {
            std::cerr << "Expected malformed scene JSON to fail\n";
            return 1;
        }
    }

    return 0;
}
