#include <sle/engine/AnimationSystem.hpp>
#include <sle/scene/Scene.hpp>
#include <sle/scene/components/AnimationClipResource.hpp>
#include <sle/scene/components/AnimatorComponent.hpp>
#include <sle/scene/components/SpriteRenderer.hpp>
#include <sle/scene/components/Transform.hpp>

#include <cmath>
#include <iostream>
#include <memory>

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
    auto& sprite = registry.addComponent<sle::components::SpriteRenderer>(entity);
    auto& animator = registry.addComponent<sle::components::AnimatorComponent>(entity);

    auto clip = std::make_shared<sle::components::AnimationClipDefinition>();
    clip->name = "integration_clip";
    clip->lengthSeconds = 1.0f;
    clip->loopMode = sle::components::AnimationLoopMode::Loop;

    sle::components::AnimationFloatTrack posX;
    posX.bindingPath = "self.Transform.position.x";
    posX.keyframes.push_back({0.0f, 0.0f, sle::components::AnimationInterpolation::Linear});
    posX.keyframes.push_back({1.0f, 10.0f, sle::components::AnimationInterpolation::Linear});

    sle::components::AnimationFloatTrack posY;
    posY.bindingPath = "self.Transform.position.y";
    posY.keyframes.push_back({0.0f, 0.0f, sle::components::AnimationInterpolation::Linear});
    posY.keyframes.push_back({1.0f, 8.0f, sle::components::AnimationInterpolation::EaseIn});

    sle::components::AnimationFloatTrack alpha;
    alpha.bindingPath = "self.SpriteRenderer.color.a";
    alpha.keyframes.push_back({0.0f, 1.0f, sle::components::AnimationInterpolation::Linear});
    alpha.keyframes.push_back({1.0f, 0.5f, sle::components::AnimationInterpolation::Linear});

    clip->floatTracks.push_back(posX);
    clip->floatTracks.push_back(posY);
    clip->floatTracks.push_back(alpha);

    animator.clip = clip;
    animator.playing = true;
    animator.enabled = true;
    animator.speed = 1.0f;

    sle::AnimationSystem system;

    system.update(scene, 0.5f);

    if (!almostEqual(transform.getPosition().x, 5.0f))
    {
        std::cerr << "Expected linear x at t=0.5 to be 5.0, got " << transform.getPosition().x << "\n";
        return 1;
    }

    if (!almostEqual(transform.getPosition().y, 2.0f))
    {
        std::cerr << "Expected ease-in y at t=0.5 to be 2.0, got " << transform.getPosition().y << "\n";
        return 1;
    }

    if (!almostEqual(sprite.color.a, 0.75f))
    {
        std::cerr << "Expected alpha at t=0.5 to be 0.75, got " << sprite.color.a << "\n";
        return 1;
    }

    animator.timeSeconds = 0.0f;
    animator.playing = true;
    animator.overrideLoopMode = false;
    system.update(scene, 1.25f);

    if (!almostEqual(transform.getPosition().x, 2.5f))
    {
        std::cerr << "Expected looped x at t=1.25 to wrap to 2.5, got " << transform.getPosition().x << "\n";
        return 1;
    }

    animator.timeSeconds = 0.9f;
    animator.playing = true;
    animator.overrideLoopMode = true;
    animator.loopMode = sle::components::AnimationLoopMode::Once;
    system.update(scene, 0.2f);

    if (animator.playing)
    {
        std::cerr << "Expected once mode to stop playback at clip end\n";
        return 1;
    }

    if (!almostEqual(animator.timeSeconds, 1.0f))
    {
        std::cerr << "Expected once mode to clamp time to 1.0, got " << animator.timeSeconds << "\n";
        return 1;
    }

    return 0;
}
