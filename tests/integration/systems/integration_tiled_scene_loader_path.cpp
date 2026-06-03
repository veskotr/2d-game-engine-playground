#include <sle/engine/SceneLoader.hpp>
#include <sle/scene/Scene.hpp>
#include <sle/scene/components/BoxColliderComponent.hpp>
#include <sle/scene/components/BoxZoneComponent.hpp>
#include <sle/scene/components/RigidBodyComponent.hpp>
#include <sle/scene/components/ScriptComponent.hpp>
#include <sle/scene/components/Transform.hpp>

#include <cmath>
#include <iostream>

namespace {

bool nearlyEqual(float a, float b)
{
    return std::fabs(a - b) < 0.0001f;
}

} // namespace

int main()
{
    sle::entity::Scene scene;
    auto loadResult = sle::SceneLoader::load("tests/data/scenes/test_tiled_scene.json", scene);
    if (!loadResult.ok())
    {
        std::cerr << "SceneLoader failed for Tiled-backed scene: " << loadResult.error() << "\n";
        return 1;
    }

    auto& registry = scene.getRegistry();

    int transformCount = 0;
    int rigidBodyCount = 0;
    int boxColliderCount = 0;
    int boxZoneCount = 0;
    int scriptCount = 0;

    bool sawExtraEntity = false;
    bool sawZone = false;
    bool sawStaticCollider = false;
    bool sawScriptEntity = false;
    bool failed = false;

    registry.view<sle::components::TransformComponent>(
        [&](sle::entity::Entity entity, sle::components::TransformComponent& transform)
        {
            ++transformCount;

            if (registry.hasComponent<sle::components::BoxZoneComponent>(entity))
            {
                sawZone =
                    nearlyEqual(transform.getPosition().x, 48.0f) &&
                    nearlyEqual(transform.getPosition().y, 48.0f);
            }

            if (registry.hasComponent<sle::components::BoxColliderComponent>(entity))
            {
                sawStaticCollider =
                    nearlyEqual(transform.getPosition().x, 80.0f) &&
                    nearlyEqual(transform.getPosition().y, 16.0f);
            }

            if (registry.hasComponent<sle::components::ScriptComponent>(entity))
            {
                sawScriptEntity =
                    nearlyEqual(transform.getPosition().x, 16.0f) &&
                    nearlyEqual(transform.getPosition().y, 80.0f);
            }

            if (!registry.hasComponent<sle::components::BoxZoneComponent>(entity) &&
                !registry.hasComponent<sle::components::BoxColliderComponent>(entity) &&
                !registry.hasComponent<sle::components::ScriptComponent>(entity))
            {
                sawExtraEntity =
                    nearlyEqual(transform.getPosition().x, 5.0f) &&
                    nearlyEqual(transform.getPosition().y, 7.0f) &&
                    nearlyEqual(transform.getScale().x, 2.0f) &&
                    nearlyEqual(transform.getScale().y, 2.0f);
            }
        });

    registry.view<sle::components::RigidBodyComponent>(
        [&](sle::entity::Entity, sle::components::RigidBodyComponent& body)
        {
            ++rigidBodyCount;
            if (body.bodyType != sle::components::BodyType::Static)
            {
                std::cerr << "Expected Tiled StaticCollider to create a static rigid body\n";
                failed = true;
            }
        });

    registry.view<sle::components::BoxColliderComponent>(
        [&](sle::entity::Entity, sle::components::BoxColliderComponent& collider)
        {
            ++boxColliderCount;
            if (!nearlyEqual(collider.size.x, 32.0f) || !nearlyEqual(collider.size.y, 32.0f))
            {
                std::cerr << "StaticCollider size mismatch\n";
                failed = true;
            }
        });

    registry.view<sle::components::BoxZoneComponent>(
        [&](sle::entity::Entity, sle::components::BoxZoneComponent& zone)
        {
            ++boxZoneCount;
            if (zone.zoneId != "map_zone" || !nearlyEqual(zone.size.x, 32.0f) || !nearlyEqual(zone.size.y, 32.0f))
            {
                std::cerr << "Zone mapping mismatch\n";
                failed = true;
            }
        });

    registry.view<sle::components::ScriptComponent>(
        [&](sle::entity::Entity, sle::components::ScriptComponent& script)
        {
            ++scriptCount;
            if (script.scriptAsset.find("assets/scripts/player_move.lua") == std::string::npos)
            {
                std::cerr << "ScriptEntity script path mismatch\n";
                failed = true;
            }
        });

    if (failed)
        return 1;

    if (transformCount != 4 || rigidBodyCount != 1 || boxColliderCount != 1 || boxZoneCount != 1 || scriptCount != 1)
    {
        std::cerr << "Unexpected component counts for Tiled-backed scene\n";
        return 1;
    }

    if (!sawExtraEntity || !sawZone || !sawStaticCollider || !sawScriptEntity)
    {
        std::cerr << "Tiled object/world transform validation failed\n";
        return 1;
    }

    return 0;
}
