#include <sle/engine/TransformSystem.hpp>
#include <sle/scene/Scene.hpp>
#include <sle/scene/TransformMath.hpp>
#include <sle/scene/components/Transform.hpp>
#include <cmath>
#include <vector>

namespace sle {

using namespace sle::entity;
using namespace sle::components;

void TransformSystem::update(Context& ctx)
{
    Registry& registry = ctx.registry;
    Scene& scene = ctx.scene;

    // Identity world transform used as the starting point for every root entity.
    const WorldTransformComponent identity{};

    struct StackEntry
    {
        Entity entity;
        WorldTransformComponent parentWorld;
        bool parentDirty;
    };

    std::vector<StackEntry> stack;
    stack.reserve(scene.getRoots().size());

    for (auto it = scene.getRoots().rbegin(); it != scene.getRoots().rend(); ++it)
        stack.push_back({*it, identity, false});

    while (!stack.empty())
    {
        StackEntry entry = stack.back();
        stack.pop_back();

        // Compute this entity's contribution to the accumulated world transform.
        // If the entity has no local TransformComponent it is transparent:
        // it passes the parent world transform unchanged to its children.
        WorldTransformComponent world = entry.parentWorld;
        bool worldDirty = entry.parentDirty;

        auto* local = registry.getComponent<TransformComponent>(entry.entity);
        if (local)
        {
            const auto* cachedWorld = registry.getComponent<WorldTransformComponent>(entry.entity);
            const bool needsRecompute = entry.parentDirty || local->isDirty() || !cachedWorld;

            if (needsRecompute)
            {
                world = transformMath::compose(entry.parentWorld, *local);

                // Write the resolved world transform into the Registry so other
                // systems can read it without re-walking the hierarchy.
                registry.addComponent<WorldTransformComponent>(entry.entity, world);
                local->clearDirty();
                worldDirty = true;
            }
            else
            {
                world = *cachedWorld;
                worldDirty = false;
            }
        }

        const auto& children = scene.getChildren(entry.entity);
        if (!worldDirty && children.empty())
            continue;

        for (auto it = children.rbegin(); it != children.rend(); ++it)
            stack.push_back({*it, world, worldDirty});
    }
}

} // namespace sle
