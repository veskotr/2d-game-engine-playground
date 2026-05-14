#include <sle/engine/TransformSystem.hpp>
#include <sle/scene/Scene.hpp>
#include <sle/scene/components/Transform.hpp>
#include <cmath>

namespace sle {

using namespace sle::entity;
using namespace sle::components;

// Rotates a 2D vector by the given angle (radians).
static glm::vec2 rotate2D(const glm::vec2& v, float radians)
{
    float c = std::cos(radians);
    float s = std::sin(radians);
    return { v.x * c - v.y * s,
             v.x * s + v.y * c };
}

void TransformSystem::update(Context& ctx)
{
    Registry& registry = ctx.registry;

    // Identity world transform used as the starting point for every root entity.
    const WorldTransformComponent identity{};

    for (Entity root : ctx.scene.getRoots())
        processEntity(root, identity, registry, ctx.scene);
}

void TransformSystem::processEntity(
    Entity                             entity,
    const WorldTransformComponent&     parentWorld,
    Registry&                          registry,
    Scene&                             scene)
{
    // Compute this entity's contribution to the accumulated world transform.
    // If the entity has no local TransformComponent it is transparent:
    // it passes the parent world transform unchanged to its children.
    WorldTransformComponent world = parentWorld;

    const auto* local = registry.getComponent<TransformComponent>(entity);
    if (local)
    {
        // Scale: parent scale applied component-wise to local scale.
        world.scale = parentWorld.scale * local->scale;

        // Rotation: parent and local rotations add.
        world.rotation = parentWorld.rotation + local->rotation;

        // Position: local position is scaled by parent scale, then rotated
        // by parent rotation, then offset by parent world position.
        world.position = parentWorld.position
                       + rotate2D(parentWorld.scale * local->position, parentWorld.rotation);

        // Write the resolved world transform into the Registry so other
        // systems can read it without re-walking the hierarchy.
        registry.addComponent<WorldTransformComponent>(entity, world);
    }

    for (Entity child : scene.getChildren(entity))
        processEntity(child, world, registry, scene);
}

} // namespace sle
