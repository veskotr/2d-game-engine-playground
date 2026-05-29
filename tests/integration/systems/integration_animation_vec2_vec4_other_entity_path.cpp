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

bool almostEqual(float a, float b, float eps = 0.001f)
{
    return std::fabs(a - b) <= eps;
}

bool almostEqual2(glm::vec2 a, glm::vec2 b, float eps = 0.001f)
{
    return almostEqual(a.x, b.x, eps) && almostEqual(a.y, b.y, eps);
}

bool almostEqual4(glm::vec4 a, glm::vec4 b, float eps = 0.001f)
{
    return almostEqual(a.r, b.r, eps)
        && almostEqual(a.g, b.g, eps)
        && almostEqual(a.b, b.b, eps)
        && almostEqual(a.a, b.a, eps);
}

} // namespace

int main()
{
    sle::entity::Scene scene;
    auto& registry = scene.getRegistry();

    // ---- Entity A: animator that drives its own transform via vec2 + vec4 tracks ----
    const sle::entity::Entity entityA = scene.createEntity();
    auto& transformA = registry.addComponent<sle::components::TransformComponent>(entityA);
    auto& spriteA = registry.addComponent<sle::components::SpriteRenderer>(entityA);
    auto& animatorA = registry.addComponent<sle::components::AnimatorComponent>(entityA);

    // ---- Entity B: a separate entity that entity A's animator will target ----
    const sle::entity::Entity entityB = scene.createEntity();
    auto& transformB = registry.addComponent<sle::components::TransformComponent>(entityB);

    auto clip = std::make_shared<sle::components::AnimationClipDefinition>();
    clip->name = "vec2_vec4_clip";
    clip->lengthSeconds = 1.0f;
    clip->loopMode = sle::components::AnimationLoopMode::Once;

    // Vec2 track: self.Transform.position — drives both x and y at once
    sle::components::AnimationVec2Track selfPos;
    selfPos.bindingPath = "self.Transform.position";
    selfPos.keyframes.push_back({0.0f, {0.0f, 0.0f}, sle::components::AnimationInterpolation::Linear});
    selfPos.keyframes.push_back({1.0f, {10.0f, 6.0f}, sle::components::AnimationInterpolation::Linear});
    clip->vec2Tracks.push_back(selfPos);

    // Vec4 track: self.SpriteRenderer.color
    sle::components::AnimationVec4Track selfColor;
    selfColor.bindingPath = "self.SpriteRenderer.color";
    selfColor.keyframes.push_back({0.0f, {1.0f, 1.0f, 1.0f, 1.0f}, sle::components::AnimationInterpolation::Linear});
    selfColor.keyframes.push_back({1.0f, {0.0f, 0.5f, 0.0f, 0.0f}, sle::components::AnimationInterpolation::Linear});
    clip->vec4Tracks.push_back(selfColor);

    // Float track targeting entity B via "entity:Target.Transform.position.x"
    sle::components::AnimationFloatTrack targetPosX;
    targetPosX.bindingPath = "entity:Target.Transform.position.x";
    targetPosX.keyframes.push_back({0.0f, 0.0f, sle::components::AnimationInterpolation::Linear});
    targetPosX.keyframes.push_back({1.0f, 20.0f, sle::components::AnimationInterpolation::Linear});
    clip->floatTracks.push_back(targetPosX);

    animatorA.clip = clip;
    animatorA.playing = true;
    animatorA.enabled = true;
    animatorA.speed = 1.0f;
    animatorA.targetEntities["Target"] = entityB.getID();

    sle::AnimationSystem system;
    system.update(scene, 0.5f);  // t = 0.5

    // --- vec2 binding test ---
    if (!almostEqual2(transformA.getPosition(), {5.0f, 3.0f}))
    {
        std::cerr << "vec2 position at t=0.5: expected (5,3), got ("
                  << transformA.getPosition().x << "," << transformA.getPosition().y << ")\n";
        return 1;
    }

    // --- vec4 binding test ---
    if (!almostEqual4(spriteA.color, {0.5f, 0.75f, 0.5f, 0.5f}))
    {
        std::cerr << "vec4 color at t=0.5: expected (0.5,0.75,0.5,0.5), got ("
                  << spriteA.color.r << "," << spriteA.color.g << "," << spriteA.color.b << "," << spriteA.color.a << ")\n";
        return 1;
    }

    // --- other-entity binding test ---
    if (!almostEqual(transformB.getPosition().x, 10.0f))
    {
        std::cerr << "Other-entity float position.x at t=0.5: expected 10.0, got "
                  << transformB.getPosition().x << "\n";
        return 1;
    }

    return 0;
}
