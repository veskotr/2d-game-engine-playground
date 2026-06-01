#include <sle/engine/SceneLoader.hpp>
#include <sle/scene/Scene.hpp>
#include <sle/scene/components/AnimatorComponent.hpp>
#include <sle/scene/components/AudioComponent.hpp>
#include <sle/scene/components/BoxColliderComponent.hpp>
#include <sle/scene/components/CircleZoneComponent.hpp>
#include <sle/scene/components/RigidBodyComponent.hpp>
#include <sle/scene/components/ScriptComponent.hpp>
#include <sle/scene/components/SpriteRenderer.hpp>
#include <sle/scene/components/StateMachineComponent.hpp>
#include <sle/scene/components/Transform.hpp>
#include <sle/scene/components/UIComponent.hpp>

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
    auto loadResult = sle::SceneLoader::load("tests/data/scenes/test_basic_scene.json", scene);
    if (!loadResult.ok())
    {
        std::cerr << "SceneLoader failed: " << loadResult.error() << "\n";
        return 1;
    }

    auto& registry = scene.getRegistry();

    int transformCount = 0;
    int spriteCount = 0;
    int rigidBodyCount = 0;
    int boxColliderCount = 0;
    int circleZoneCount = 0;
    int scriptCount = 0;
    int audioCount = 0;
    int stateMachineCount = 0;
    int animatorCount = 0;
    int uiCount = 0;

    bool validatedPrimaryEntity = false;
    bool validatedZoneEntity = false;
    bool failed = false;

    registry.view<sle::components::TransformComponent>(
        [&](sle::entity::Entity entity, sle::components::TransformComponent& transform)
        {
            ++transformCount;

            if (registry.hasComponent<sle::components::SpriteRenderer>(entity))
            {
                validatedPrimaryEntity =
                    nearlyEqual(transform.getPosition().x, 10.0f) &&
                    nearlyEqual(transform.getPosition().y, 20.0f) &&
                    nearlyEqual(transform.getScale().x, 2.0f) &&
                    nearlyEqual(transform.getScale().y, 3.0f) &&
                    nearlyEqual(transform.getRotation(), 0.5f);
            }

            if (registry.hasComponent<sle::components::CircleZoneComponent>(entity))
            {
                validatedZoneEntity =
                    nearlyEqual(transform.getPosition().x, 100.0f) &&
                    nearlyEqual(transform.getPosition().y, 50.0f);
            }
        });

    registry.view<sle::components::SpriteRenderer>(
        [&](sle::entity::Entity, sle::components::SpriteRenderer& sprite)
        {
            ++spriteCount;
            if (!nearlyEqual(sprite.color.g, 0.5f) || sprite.layer != 3)
            {
                std::cerr << "SpriteRenderer field mismatch\n";
                failed = true;
            }
        });

    registry.view<sle::components::RigidBodyComponent>(
        [&](sle::entity::Entity, sle::components::RigidBodyComponent& rigidBody)
        {
            ++rigidBodyCount;
            if (rigidBody.bodyType != sle::components::BodyType::Kinematic ||
                !nearlyEqual(rigidBody.mass, 2.0f) ||
                !rigidBody.fixedRotation)
            {
                std::cerr << "RigidBody field mismatch\n";
                failed = true;
            }
        });

    registry.view<sle::components::BoxColliderComponent>(
        [&](sle::entity::Entity, sle::components::BoxColliderComponent& boxCollider)
        {
            ++boxColliderCount;
            if (!nearlyEqual(boxCollider.size.x, 8.0f) || !nearlyEqual(boxCollider.size.y, 12.0f))
            {
                std::cerr << "BoxCollider field mismatch\n";
                failed = true;
            }
        });

    registry.view<sle::components::CircleZoneComponent>(
        [&](sle::entity::Entity, sle::components::CircleZoneComponent& circleZone)
        {
            ++circleZoneCount;
            if (!nearlyEqual(circleZone.radius, 16.0f) || circleZone.zoneId != "trigger_zone")
            {
                std::cerr << "CircleZone field mismatch\n";
                failed = true;
            }
        });

    registry.view<sle::components::ScriptComponent>(
        [&](sle::entity::Entity, sle::components::ScriptComponent& script)
        {
            ++scriptCount;
            if (script.scriptAsset.find("assets/scripts/player_move.lua") == std::string::npos)
            {
                std::cerr << "Script asset path mismatch\n";
                failed = true;
            }
        });

    registry.view<sle::components::AudioComponent>(
        [&](sle::entity::Entity, sle::components::AudioComponent& audio)
        {
            ++audioCount;
            if (!audio.loop || !nearlyEqual(audio.volume, 0.6f))
            {
                std::cerr << "Audio field mismatch\n";
                failed = true;
            }
        });

    registry.view<sle::components::StateMachineComponent>(
        [&](sle::entity::Entity, sle::components::StateMachineComponent& stateMachine)
        {
            ++stateMachineCount;
            if (stateMachine.definitionAsset.find("integration_state_machine_definition.json") == std::string::npos)
            {
                std::cerr << "StateMachine asset mismatch\n";
                failed = true;
            }
        });

    registry.view<sle::components::AnimatorComponent>(
        [&](sle::entity::Entity, sle::components::AnimatorComponent& animator)
        {
            ++animatorCount;
            if (animator.stateClipMap.find("Idle") == animator.stateClipMap.end())
            {
                std::cerr << "Animator stateClipMap missing expected key\n";
                failed = true;
            }
        });

    registry.view<sle::components::UIComponent>(
        [&](sle::entity::Entity, sle::components::UIComponent& ui)
        {
            ++uiCount;
            if (ui.spaceMode != sle::components::UISpaceMode::World || ui.bindingScope != "entity.player")
            {
                std::cerr << "UI field mismatch\n";
                failed = true;
            }
        });

    if (failed)
        return 1;

    if (transformCount != 2 || spriteCount != 1 || rigidBodyCount != 1 || boxColliderCount != 1 ||
        circleZoneCount != 1 || scriptCount != 1 || audioCount != 1 || stateMachineCount != 1 ||
        animatorCount != 1 || uiCount != 1)
    {
        std::cerr << "Unexpected component counts after scene load\n";
        return 1;
    }

    if (!validatedPrimaryEntity || !validatedZoneEntity)
    {
        std::cerr << "Transform validation failed\n";
        return 1;
    }

    return 0;
}
