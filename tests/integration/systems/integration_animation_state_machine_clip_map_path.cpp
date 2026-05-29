#include <sle/engine/AnimationSystem.hpp>
#include <sle/scene/Scene.hpp>
#include <sle/scene/components/AnimatorComponent.hpp>
#include <sle/scene/components/StateMachineComponent.hpp>
#include <sle/scene/components/Transform.hpp>

#include <cmath>
#include <iostream>

namespace {

bool almostEqual(float a, float b, float epsilon = 0.001f)
{
    return std::fabs(a - b) <= epsilon;
}

} // namespace

int main()
{
    sle::entity::Scene scene;
    auto& registry = scene.getRegistry();

    const sle::entity::Entity entity = scene.createEntity();
    auto& transform = registry.addComponent<sle::components::TransformComponent>(entity);
    auto& animator = registry.addComponent<sle::components::AnimatorComponent>(entity);
    auto& stateMachine = registry.addComponent<sle::components::StateMachineComponent>(entity);

    animator.enabled = true;
    animator.playing = false;
    animator.stateClipMap["Idle"] = "tests/data/animations/state_idle.clip.json";
    animator.stateClipMap["Run"] = "tests/data/animations/state_run.clip.json";

    stateMachine.currentState = "Idle";
    stateMachine.initialized = true;

    sle::AnimationSystem animationSystem;

    animationSystem.update(scene, 0.5f);

    if (animator.clipAsset != "tests/data/animations/state_idle.clip.json")
    {
        std::cerr << "Expected Idle state to select idle clip asset\n";
        return 1;
    }

    if (!almostEqual(transform.getPosition().x, 1.0f))
    {
        std::cerr << "Expected idle clip x at t=0.5 to be 1.0, got " << transform.getPosition().x << "\n";
        return 1;
    }

    stateMachine.currentState = "Run";
    animationSystem.update(scene, 0.5f);

    if (animator.clipAsset != "tests/data/animations/state_run.clip.json")
    {
        std::cerr << "Expected Run state to select run clip asset\n";
        return 1;
    }

    if (!almostEqual(transform.getPosition().x, 10.0f))
    {
        std::cerr << "Expected run clip x at t=0.5 to be 10.0 after state switch, got "
                  << transform.getPosition().x << "\n";
        return 1;
    }

    return 0;
}
