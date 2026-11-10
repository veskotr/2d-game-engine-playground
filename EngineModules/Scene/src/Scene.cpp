#include <algorithm>
#include <utility>
#include <sle/scene/Scene.hpp>
#include <sle/scene/components/Transform.hpp>

namespace sle::entity
{

    Scene::~Scene()
    {
        destroy();
    }

    Entity Scene::createEntity()
    {
        Entity entity = registry.createEntity();
        roots.push_back(entity);
        return entity;
    }

    void Scene::destroyEntity(Entity entity)
    {
        if (!registry.hasEntity(entity))
            return;

        destroyEntityInternal(entity);
    }

    void Scene::destroyEntityInternal(Entity entity)
    {
        // Destroy all descendants first (copy to avoid modifying the map while iterating)
        auto it = childrenMap.find(entity.getID());
        if (it != childrenMap.end())
        {
            std::vector<Entity> childrenCopy = it->second;
            for (Entity child : childrenCopy)
                destroyEntityInternal(child);

            childrenMap.erase(entity.getID());
        }

        detachFromParent(entity);
        registry.destroyEntity(entity);
    }

    void Scene::detachFromParent(Entity entity)
    {
        auto parentIt = registry.parentMap.find(entity.getID());
        if (parentIt != registry.parentMap.end())
        {
            // Remove from parent's children list
            auto& siblings = childrenMap[parentIt->second.getID()];
            siblings.erase(
                std::remove_if(siblings.begin(), siblings.end(),
                    [&entity](Entity e) { return e.getID() == entity.getID(); }),
                siblings.end());
        }
        else
        {
            // Remove from roots
            roots.erase(
                std::remove_if(roots.begin(), roots.end(),
                    [&entity](Entity e) { return e.getID() == entity.getID(); }),
                roots.end());
        }
    }

    bool Scene::setParent(Entity child, Entity parent)
    {
        if (!registry.hasEntity(child) || (parent.valid() && !registry.hasEntity(parent)))
            return false;

        // Validate before detaching: rejecting a cycle must preserve both structure
        // and transform dirty state. Every accepted link keeps the hierarchy acyclic.
        for (Entity ancestor = parent; ancestor.valid(); ancestor = getParent(ancestor))
        {
            if (ancestor == child)
                return false;
        }

        detachFromParent(child);

        if (parent.valid())
        {
            registry.parentMap[child.getID()] = parent;
            childrenMap[parent.getID()].push_back(child);
        }
        else
        {
            registry.parentMap.erase(child.getID());
            roots.push_back(child);
        }

        if (auto* transform = registry.getComponent<components::TransformComponent>(child))
            transform->setParent(parent);
        markTransformBranchDirty(child);
        return true;
    }

    Entity Scene::getParent(Entity entity) const
    {
        auto it = registry.parentMap.find(entity.getID());
        if (it == registry.parentMap.end())
            return Entity{};

        return it->second;
    }

    const std::vector<Entity>& Scene::getChildren(Entity entity) const
    {
        auto it = childrenMap.find(entity.getID());
        if (it == childrenMap.end())
            return emptyChildren;

        return it->second;
    }

    void Scene::destroy()
    {
        // Work from a snapshot so destroyEntityInternal can mutate roots safely
        std::vector<Entity> rootsCopy = roots;
        for (Entity root : rootsCopy)
            destroyEntityInternal(root);

        roots.clear();
        registry.parentMap.clear();
        childrenMap.clear();

        // Scene teardown should fully reset per-scene event state.
        // Clearing queued events prevents stale deferred events from reaching
        // listeners registered by the next scene.
        eventBus.clearAll();
    }

    std::unique_ptr<Scene> Scene::createStagingScene() const
    {
        auto staged = std::make_unique<Scene>();
        staged->registry = registry.makeEmpty();
        return staged;
    }

    void Scene::reserveStagedEntityIds(const Scene& staged)
    {
        registry.nextEntityID = std::max(registry.nextEntityID, staged.registry.nextEntityID);
    }

    void Scene::replaceWith(Scene& staged)
    {
        if (&staged == this)
            return;

        staged.registry.nextEntityID = std::max(staged.registry.nextEntityID, registry.nextEntityID);
        destroy();
        // Never move EventBus: queued callbacks capture its address and scoped
        // subscriptions refer to it. Only ECS and hierarchy storage is swapped.
        using std::swap;
        swap(registry, staged.registry);
        swap(roots, staged.roots);
        swap(childrenMap, staged.childrenMap);
    }

    void Scene::markTransformBranchDirty(Entity entity)
    {
        if (auto* transform = registry.getComponent<components::TransformComponent>(entity))
            transform->markDirty();

        for (Entity child : getChildren(entity))
            markTransformBranchDirty(child);
    }

} // namespace sle::entity
