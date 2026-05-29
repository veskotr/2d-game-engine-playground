#include <sle/engine/AnimationSystem.hpp>

#include <sle/core/Log.hpp>
#include <sle/resources/Resources.hpp>
#include <sle/scene/Scene.hpp>
#include <sle/scene/Registry.hpp>
#include <sle/scene/components/AnimationClipResource.hpp>
#include <sle/scene/components/AnimatorComponent.hpp>
#include <sle/scene/components/SpriteRenderer.hpp>
#include <sle/scene/components/StateMachineComponent.hpp>
#include <sle/scene/components/Transform.hpp>

#include <algorithm>
#include <cmath>
#include <string>
#include <string_view>

namespace sle {

namespace {

// ============================================================
// Easing
// ============================================================

float applyEasing(float alpha, sle::components::AnimationInterpolation interp)
{
    alpha = std::clamp(alpha, 0.0f, 1.0f);

    switch (interp)
    {
        case sle::components::AnimationInterpolation::Step:
            return 0.0f;
        case sle::components::AnimationInterpolation::Linear:
            return alpha;
        case sle::components::AnimationInterpolation::EaseIn:
            return alpha * alpha;
        case sle::components::AnimationInterpolation::EaseOut:
            return 1.0f - (1.0f - alpha) * (1.0f - alpha);
        case sle::components::AnimationInterpolation::EaseInOut:
            return (alpha < 0.5f)
                ? (2.0f * alpha * alpha)
                : (1.0f - std::pow(-2.0f * alpha + 2.0f, 2.0f) * 0.5f);
        case sle::components::AnimationInterpolation::Smoothstep:
            return alpha * alpha * (3.0f - 2.0f * alpha);
        case sle::components::AnimationInterpolation::Exp:
        {
            constexpr float eMinus1 = 1.7182818f;
            return (std::exp(alpha) - 1.0f) / eMinus1;
        }
        case sle::components::AnimationInterpolation::Log:
        {
            constexpr float eMinus1 = 1.7182818f;
            return std::log1p(alpha * eMinus1);
        }
    }
    return alpha;
}

// ============================================================
// Keyframe samplers
// ============================================================

float sampleFloat(const sle::components::AnimationFloatTrack& track, float t)
{
    if (track.keyframes.empty()) return 0.0f;
    if (track.keyframes.size() == 1) return track.keyframes.front().value;
    if (t <= track.keyframes.front().timeSeconds) return track.keyframes.front().value;

    for (std::size_t i = 1; i < track.keyframes.size(); ++i)
    {
        const auto& prev = track.keyframes[i - 1];
        const auto& next = track.keyframes[i];
        if (t > next.timeSeconds) continue;
        const float dur = next.timeSeconds - prev.timeSeconds;
        if (dur <= 0.0f) return next.value;
        const float a = applyEasing((t - prev.timeSeconds) / dur, next.interpolation);
        return prev.value + (next.value - prev.value) * a;
    }
    return track.keyframes.back().value;
}

glm::vec2 sampleVec2(const sle::components::AnimationVec2Track& track, float t)
{
    if (track.keyframes.empty()) return {};
    if (track.keyframes.size() == 1) return track.keyframes.front().value;
    if (t <= track.keyframes.front().timeSeconds) return track.keyframes.front().value;

    for (std::size_t i = 1; i < track.keyframes.size(); ++i)
    {
        const auto& prev = track.keyframes[i - 1];
        const auto& next = track.keyframes[i];
        if (t > next.timeSeconds) continue;
        const float dur = next.timeSeconds - prev.timeSeconds;
        if (dur <= 0.0f) return next.value;
        const float a = applyEasing((t - prev.timeSeconds) / dur, next.interpolation);
        return prev.value + (next.value - prev.value) * a;
    }
    return track.keyframes.back().value;
}

glm::vec4 sampleVec4(const sle::components::AnimationVec4Track& track, float t)
{
    if (track.keyframes.empty()) return {};
    if (track.keyframes.size() == 1) return track.keyframes.front().value;
    if (t <= track.keyframes.front().timeSeconds) return track.keyframes.front().value;

    for (std::size_t i = 1; i < track.keyframes.size(); ++i)
    {
        const auto& prev = track.keyframes[i - 1];
        const auto& next = track.keyframes[i];
        if (t > next.timeSeconds) continue;
        const float dur = next.timeSeconds - prev.timeSeconds;
        if (dur <= 0.0f) return next.value;
        const float a = applyEasing((t - prev.timeSeconds) / dur, next.interpolation);
        return prev.value + (next.value - prev.value) * a;
    }
    return track.keyframes.back().value;
}

// ============================================================
// Binding path resolution helpers
// ============================================================

// Parse "entity:Name.Component.field" -> targetName="Name", remainder="Component.field"
// Returns false when path doesn't start with "entity:" (i.e. it is a self path).
bool parseEntityPrefix(std::string_view path, std::string& outName, std::string_view& outRemainder)
{
    constexpr std::string_view kPrefix = "entity:";
    if (path.substr(0, kPrefix.size()) != kPrefix)
        return false;

    path.remove_prefix(kPrefix.size());
    const auto dot = path.find('.');
    if (dot == std::string_view::npos)
        return false;

    outName = std::string(path.substr(0, dot));
    outRemainder = path.substr(dot + 1);
    return true;
}

// Resolve the entity to apply a binding to:
// "self.*" paths → owning entity
// "entity:Name.*" paths → entity ID from animator.targetEntities map
bool resolveTarget(
    std::string_view bindingPath,
    sle::entity::Entity self,
    const sle::components::AnimatorComponent& animator,
    sle::entity::Registry& registry,
    sle::entity::Entity& outEntity,
    std::string_view& outField)
{
    std::string targetName;
    std::string_view remainder;

    if (parseEntityPrefix(bindingPath, targetName, remainder))
    {
        auto it = animator.targetEntities.find(targetName);
        if (it == animator.targetEntities.end())
            return false;

        outEntity = sle::entity::Entity(it->second);
        if (!registry.hasEntity(outEntity))
            return false;

        outField = remainder;
        return true;
    }

    // "self.*" - strip the leading "self." prefix
    constexpr std::string_view kSelf = "self.";
    if (bindingPath.substr(0, kSelf.size()) != kSelf)
        return false;

    outEntity = self;
    outField = bindingPath.substr(kSelf.size());
    return true;
}

// ============================================================
// Float binding applicator
// ============================================================

bool applyFloat(
    sle::entity::Registry& registry,
    sle::entity::Entity target,
    std::string_view field,
    float value)
{
    using sle::components::SpriteRenderer;
    using sle::components::TransformComponent;

    auto* xfm = registry.getComponent<TransformComponent>(target);
    auto* spr = registry.getComponent<SpriteRenderer>(target);

    if (field == "Transform.position.x" && xfm)
    {
        auto p = xfm->getPosition(); p.x = value; xfm->setPosition(p); return true;
    }
    if (field == "Transform.position.y" && xfm)
    {
        auto p = xfm->getPosition(); p.y = value; xfm->setPosition(p); return true;
    }
    if (field == "Transform.rotation" && xfm)
    {
        xfm->setRotation(value); return true;
    }
    if (field == "Transform.scale.x" && xfm)
    {
        auto s = xfm->getScale(); s.x = value; xfm->setScale(s); return true;
    }
    if (field == "Transform.scale.y" && xfm)
    {
        auto s = xfm->getScale(); s.y = value; xfm->setScale(s); return true;
    }
    if (field == "SpriteRenderer.color.r" && spr) { spr->color.r = value; return true; }
    if (field == "SpriteRenderer.color.g" && spr) { spr->color.g = value; return true; }
    if (field == "SpriteRenderer.color.b" && spr) { spr->color.b = value; return true; }
    if (field == "SpriteRenderer.color.a" && spr) { spr->color.a = value; return true; }
    return false;
}

// ============================================================
// Vec2 binding applicator
// ============================================================

bool applyVec2(
    sle::entity::Registry& registry,
    sle::entity::Entity target,
    std::string_view field,
    glm::vec2 value)
{
    using sle::components::TransformComponent;
    auto* xfm = registry.getComponent<TransformComponent>(target);

    if (field == "Transform.position" && xfm) { xfm->setPosition(value); return true; }
    if (field == "Transform.scale"    && xfm) { xfm->setScale(value);    return true; }
    return false;
}

// ============================================================
// Vec4 binding applicator
// ============================================================

bool applyVec4(
    sle::entity::Registry& registry,
    sle::entity::Entity target,
    std::string_view field,
    glm::vec4 value)
{
    using sle::components::SpriteRenderer;
    auto* spr = registry.getComponent<SpriteRenderer>(target);

    if (field == "SpriteRenderer.color" && spr) { spr->color = value; return true; }
    return false;
}

} // namespace

// ============================================================
// AnimationSystem::update
// ============================================================

void AnimationSystem::update(sle::entity::Scene& scene, float dt)
{
    auto& registry = scene.getRegistry();

    registry.view<sle::components::AnimatorComponent>(
        [dt, &registry](sle::entity::Entity entity, sle::components::AnimatorComponent& animator)
        {
            if (!animator.enabled)
                return;

            // State machine can drive clip selection through stateClipMap.
            if (!animator.stateClipMap.empty())
            {
                if (const auto* sm = registry.getComponent<sle::components::StateMachineComponent>(entity))
                {
                    auto it = animator.stateClipMap.find(sm->currentState);
                    if (it != animator.stateClipMap.end() && !it->second.empty() && animator.clipAsset != it->second)
                    {
                        animator.clipAsset = it->second;
                        animator.clip.reset();
                        animator.clipLoadAttempted = false;
                        animator.timeSeconds = 0.0f;
                        animator.playing = true;
                    }
                }
            }

            if (!animator.playing)
                return;

            // Lazy clip load
            if (!animator.clip && !animator.clipAsset.empty() && !animator.clipLoadAttempted)
            {
                animator.clipLoadAttempted = true;
                auto resource = sle::core::Resources::create<sle::components::AnimationClipResource>(
                    animator.clipAsset, animator.clipAsset);
                if (resource && resource->getClip())
                    animator.clip = resource->getClip();
                else
                    sle::core::Log::warn(
                        "Failed to load animation clip '{}' for entity {}",
                        animator.clipAsset, entity.getID());
            }

            if (!animator.clip || animator.clip->lengthSeconds <= 0.0f)
                return;

            animator.timeSeconds += dt * animator.speed;

            const auto clipLoop = animator.overrideLoopMode
                ? animator.loopMode
                : animator.clip->loopMode;

            if (clipLoop == sle::components::AnimationLoopMode::Loop)
            {
                animator.timeSeconds = std::fmod(animator.timeSeconds, animator.clip->lengthSeconds);
                if (animator.timeSeconds < 0.0f)
                    animator.timeSeconds += animator.clip->lengthSeconds;
            }
            else
            {
                if (animator.timeSeconds <= 0.0f) animator.timeSeconds = 0.0f;
                if (animator.timeSeconds >= animator.clip->lengthSeconds)
                {
                    animator.timeSeconds = animator.clip->lengthSeconds;
                    animator.playing = false;
                }
            }

            const float t = animator.timeSeconds;

            auto warn = [&](const std::string& path)
            {
                if (animator.invalidBindingsLogged.insert(path).second)
                    sle::core::Log::warn(
                        "Animation binding '{}' could not be resolved for entity {}",
                        path, entity.getID());
            };

            // ----- float tracks -----
            for (const auto& track : animator.clip->floatTracks)
            {
                sle::entity::Entity target{};
                std::string_view field;
                if (!resolveTarget(track.bindingPath, entity, animator, registry, target, field))
                {
                    warn(track.bindingPath); continue;
                }
                if (!applyFloat(registry, target, field, sampleFloat(track, t)))
                    warn(track.bindingPath);
            }

            // ----- vec2 tracks -----
            for (const auto& track : animator.clip->vec2Tracks)
            {
                sle::entity::Entity target{};
                std::string_view field;
                if (!resolveTarget(track.bindingPath, entity, animator, registry, target, field))
                {
                    warn(track.bindingPath); continue;
                }
                if (!applyVec2(registry, target, field, sampleVec2(track, t)))
                    warn(track.bindingPath);
            }

            // ----- vec4 tracks -----
            for (const auto& track : animator.clip->vec4Tracks)
            {
                sle::entity::Entity target{};
                std::string_view field;
                if (!resolveTarget(track.bindingPath, entity, animator, registry, target, field))
                {
                    warn(track.bindingPath); continue;
                }
                if (!applyVec4(registry, target, field, sampleVec4(track, t)))
                    warn(track.bindingPath);
            }
        });
}

} // namespace sle
