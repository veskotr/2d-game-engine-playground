#include <sle/scene/Scene.hpp>
#include <sle/scene/components/Transform.hpp>

#include <iostream>
#include <stdexcept>
#include <type_traits>

namespace {
using sle::entity::Entity;
using sle::entity::Scene;
using sle::components::TransformComponent;

bool check(bool condition, const char* message)
{
    if (!condition)
        std::cerr << message << '\n';
    return condition;
}

template <typename SceneType>
bool checkHierarchy(SceneType& scene)
{
    // Avoid invoking the old recursive cycle path before validation exists.
    if constexpr (!std::is_same_v<decltype(scene.setParent(Entity{}, Entity{})), bool>)
        return check(false, "Scene::setParent must report rejection before hierarchy mutation");
    else
    {
        auto& registry = scene.getRegistry();
        const auto root = scene.createEntity();
        const auto child = scene.createEntity();
        const auto leaf = scene.createEntity();
        const auto dead = scene.createEntity();
        scene.destroyEntity(dead);
        registry.template addComponent<TransformComponent>(child);
        registry.template addComponent<TransformComponent>(leaf);
        if (!check(scene.setParent(child, root) && scene.setParent(leaf, child), "Valid hierarchy rejected"))
            return false;
        registry.template getComponent<TransformComponent>(child)->clearDirty();
        registry.template getComponent<TransformComponent>(leaf)->clearDirty();
        if (!check(!scene.setParent(root, root) && !scene.setParent(root, leaf)
                   && !scene.setParent(child, dead) && !scene.setParent(dead, root)
                   && !scene.setParent(Entity{}, root), "Invalid hierarchy accepted"))
            return false;
        if (!check(scene.getRoots().size() == 1 && scene.getRoots()[0] == root
                   && scene.getParent(child) == root && scene.getParent(leaf) == child
                   && scene.getChildren(root).size() == 1 && scene.getChildren(child).size() == 1,
                   "Rejected parent change mutated hierarchy"))
            return false;
        if (!check(registry.template getComponent<TransformComponent>(child)->getParent() == root
                   && !registry.template getComponent<TransformComponent>(child)->isDirty()
                   && !registry.template getComponent<TransformComponent>(leaf)->isDirty(),
                   "Transform parent or dirty state disagrees with hierarchy"))
            return false;
        if (!check(scene.setParent(child, Entity{})
                   && !registry.template getComponent<TransformComponent>(child)->getParent().valid()
                   && registry.template getComponent<TransformComponent>(leaf)->isDirty(),
                   "Detach failed to synchronize transform branch"))
            return false;
        scene.destroyEntity(child);
        scene.destroyEntity(child);
        return check(!registry.hasEntity(child) && !registry.hasEntity(leaf)
                     && registry.hasEntity(root) && scene.getRoots().size() == 1,
                     "Recursive or repeated destruction corrupted surviving roots");
    }
}
}

int main()
{
    Scene scene;
    auto& registry = scene.getRegistry();
    const auto dead = scene.createEntity();
    scene.destroyEntity(dead);
    for (const auto invalid : {Entity{}, dead, Entity{999999}})
    {
        bool rejected = false;
        try { registry.addComponent<TransformComponent>(invalid); }
        catch (const std::invalid_argument&) { rejected = true; }
        if (!check(rejected && !registry.hasComponent<TransformComponent>(invalid),
                   "Adding a component to a dead entity must reject without ghost storage"))
            return 1;
    }
    int componentCount = 0;
    registry.view<TransformComponent>([&](Entity, TransformComponent&) { ++componentCount; });
    if (!check(componentCount == 0, "Dead entity appeared in component view"))
        return 1;
    if (!checkHierarchy(scene))
        return 1;

    auto staged = scene.createStagingScene();
    const auto parent = staged->createEntity();
    const auto child = staged->createEntity();
    staged->setParent(child, parent);
    staged->getRegistry().addComponent<TransformComponent>(child);
    if (!check(staged->getRegistry().getComponent<TransformComponent>(child)->getParent() == parent,
               "Late-added transform must inherit scene parent"))
        return 1;
    scene.replaceWith(*staged);
    registry.removeComponent<TransformComponent>(child);
    registry.addComponent<TransformComponent>(child);
    if (!check(scene.getParent(child) == parent
               && registry.getComponent<TransformComponent>(child)->getParent() == parent,
               "Staging commit lost hierarchy initialization for replacement transform"))
        return 1;
    scene.destroyEntity(parent);
    return check(!registry.hasEntity(child) && scene.getRoots().empty(),
                 "Staged hierarchy did not destroy descendants") ? 0 : 1;
}
