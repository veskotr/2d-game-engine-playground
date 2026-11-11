#pragma once

#include <sle/scene/Scene.hpp>
#include <sle/scene/components/Transform.hpp>
#include <sle/scene/components/WorldTransformComponent.hpp>
#include <cmath>
#include <vector>

namespace sle::entity::transformMath {

inline glm::vec2 rotate(glm::vec2 value, float angle)
{
    const float c = std::cos(angle), s = std::sin(angle);
    return {value.x * c - value.y * s, value.x * s + value.y * c};
}

inline components::WorldTransformComponent compose(
    const components::WorldTransformComponent& parent, const components::TransformComponent& local)
{
    return {parent.position + rotate(parent.scale * local.getPosition(), parent.rotation),
            parent.rotation + local.getRotation(), parent.scale * local.getScale()};
}

// Resolve authored data directly; cached presentation output and dirty flags are
// deliberately irrelevant. Transformless hierarchy nodes pass the pose through.
inline components::WorldTransformComponent currentWorld(const Scene& scene, Entity entity)
{
    std::vector<Entity> chain;
    for (; entity.valid(); entity = scene.getParent(entity)) chain.push_back(entity);
    components::WorldTransformComponent world;
    for (auto it = chain.rbegin(); it != chain.rend(); ++it)
        if (const auto* local = scene.getRegistry().getComponent<components::TransformComponent>(*it))
            world = compose(world, *local);
    return world;
}

inline components::WorldTransformComponent currentParentWorld(const Scene& scene, Entity entity)
{
    return currentWorld(scene, scene.getParent(entity));
}

inline bool finite(const components::WorldTransformComponent& pose)
{
    return std::isfinite(pose.position.x) && std::isfinite(pose.position.y) &&
           std::isfinite(pose.rotation) && std::isfinite(pose.scale.x) && std::isfinite(pose.scale.y);
}

inline bool invertible(const components::WorldTransformComponent& pose)
{
    // Keep both inversion and native fixture geometry away from degenerate scales.
    return finite(pose) && std::abs(pose.scale.x) > 1.0e-6f && std::abs(pose.scale.y) > 1.0e-6f;
}

inline glm::vec2 toLocalPosition(const components::WorldTransformComponent& parent, glm::vec2 position)
{
    return rotate(position - parent.position, -parent.rotation) / parent.scale;
}

} // namespace sle::entity::transformMath
