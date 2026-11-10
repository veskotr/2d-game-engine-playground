#include <sle/engine/Runtime.hpp>
#include <sle/scene/components/AnimatorComponent.hpp>
#include <sle/scene/components/RigidBodyComponent.hpp>
#include <sle/scene/components/ScriptComponent.hpp>
#include <sle/scene/components/Transform.hpp>
#include <sle/scene/components/WorldTransformComponent.hpp>

#include <cmath>
#include <iostream>
#include <memory>

namespace {
bool near(float actual, float expected)
{
    return std::fabs(actual - expected) < 0.001f;
}
}

int main()
{
    sle::core::EngineConfig config;
    config.width = 320;
    config.height = 180;
    config.title = "Runtime transform/physics regression";
    config.vsync = false;
    sle::Runtime runtime(config);
    const auto init = runtime.init();
    if (!init.ok())
    {
        if (init.error().find("Failed to create GLFW window") != std::string::npos)
        {
            std::cout << "SKIP: runtime regression requires a graphics environment\n";
            return 125;
        }
        std::cerr << init.error() << "\n";
        return 1;
    }

    auto& scene = runtime.getScene();
    auto& registry = scene.getRegistry();
    const auto parent = scene.createEntity();
    registry.addComponent<sle::components::TransformComponent>(parent);
    registry.addComponent<sle::components::ScriptComponent>(parent).scriptAsset =
        "tests/data/scripts/runtime_transform_update.lua";
    const auto child = scene.createEntity();
    registry.addComponent<sle::components::TransformComponent>(child).setPosition({10.0f, 5.0f});
    scene.setParent(child, parent);

    const auto animated = scene.createEntity();
    registry.addComponent<sle::components::TransformComponent>(animated);
    registry.addComponent<sle::components::RigidBodyComponent>(animated).bodyType =
        sle::components::BodyType::Static;
    auto& animator = registry.addComponent<sle::components::AnimatorComponent>(animated);
    animator.enabled = false;
    animator.speed = 0.0f;
    animator.timeSeconds = 0.5f;
    animator.clip = std::make_shared<sle::components::AnimationClipDefinition>();
    animator.clip->lengthSeconds = 1.0f;
    sle::components::AnimationFloatTrack track;
    track.bindingPath = "self.Transform.position.x";
    track.keyframes.push_back({0.0f, 0.0f});
    track.keyframes.push_back({1.0f, 160.0f});
    animator.clip->floatTracks.push_back(track);

    const auto teleported = scene.createEntity();
    auto& teleportTransform = registry.addComponent<sle::components::TransformComponent>(teleported);
    registry.addComponent<sle::components::RigidBodyComponent>(teleported).bodyType =
        sle::components::BodyType::Static;

    // Warm up: bodies and world-transform caches now exist. Editing an existing
    // body is important: initial body creation alone would hide the lost edit.
    if (runtime.runForFrames(1) != 1)
        return 1;
    animator.enabled = true;
    teleportTransform.setPosition({200.0f, 70.0f});
    teleportTransform.setRotation(0.75f);
    if (runtime.runForFrames(1) != 1)
        return 1;

    bool passed = true;
    const auto* parentWorld = registry.getComponent<sle::components::WorldTransformComponent>(parent);
    const auto* childWorld = registry.getComponent<sle::components::WorldTransformComponent>(child);
    if (!parentWorld || !childWorld || !near(parentWorld->position.x, 120.0f) ||
        !near(parentWorld->position.y, 40.0f) || !near(childWorld->position.x, 130.0f) ||
        !near(childWorld->position.y, 45.0f))
    {
        std::cerr << "Script movement must reach parent and child world transforms in the same frame\n";
        passed = false;
    }

    auto checkBody = [&](sle::entity::Entity entity, float x, float y, float angle, const char* message)
    {
        const auto* body = registry.getComponent<sle::components::RigidBodyComponent>(entity);
        const auto* local = registry.getComponent<sle::components::TransformComponent>(entity);
        const auto* world = registry.getComponent<sle::components::WorldTransformComponent>(entity);
        glm::vec2 bodyPosition;
        float bodyAngle = 0.0f;
        runtime.getPhysicsWorld()->getBodyTransform(body->box2dBodyId, bodyPosition, bodyAngle);
        if (!world || !near(bodyPosition.x, x) || !near(bodyPosition.y, y) || !near(bodyAngle, angle) ||
            !near(local->getPosition().x, x) || !near(local->getPosition().y, y) ||
            !near(local->getRotation(), angle) || !near(world->position.x, x) ||
            !near(world->position.y, y) || !near(world->rotation, angle))
        {
            std::cerr << message << "\n";
            passed = false;
        }
    };
    checkBody(animated, 80.0f, 0.0f, 0.0f, "Animation must update the existing body and render pose in one frame");
    checkBody(teleported, 200.0f, 70.0f, 0.75f, "External teleport/rotation must survive physics and reach the render pose");
    return passed ? 0 : 1;
}
