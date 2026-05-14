#include <algorithm>
#include <sle/scene/Scene.hpp>

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
        if (!entity.valid())
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

        registry.destroyEntity(entity);

        detachFromParent(entity);
        parentMap.erase(entity.getID());
    }

    void Scene::detachFromParent(Entity entity)
    {
        auto parentIt = parentMap.find(entity.getID());
        if (parentIt != parentMap.end())
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

    void Scene::setParent(Entity child, Entity parent)
    {
        if (!child.valid())
            return;

        detachFromParent(child);

        if (parent.valid())
        {
            parentMap[child.getID()] = parent;
            childrenMap[parent.getID()].push_back(child);
        }
        else
        {
            parentMap.erase(child.getID());
            roots.push_back(child);
        }
    }

    Entity Scene::getParent(Entity entity) const
    {
        auto it = parentMap.find(entity.getID());
        if (it == parentMap.end())
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
        parentMap.clear();
        childrenMap.clear();
    }

} // namespace sle::entity
