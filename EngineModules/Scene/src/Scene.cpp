#include <sle/scene/Scene.hpp>
#include <algorithm>

namespace sle::entity {

Scene::~Scene()
{
    destroy();
}

EngineObject* Scene::createObject()
{
    return createObject(nullptr);
}

EngineObject* Scene::createObject(EngineObject* parent)
{
    Entity entity = registry.createEntity();
    auto object = std::make_unique<EngineObject>(this, &registry, entity);
    auto* ptr = object.get();

    ptr->parent = parent;

    if (parent)
    {
        parent->children.emplace_back(std::move(object));
    }
    else
    {
        roots.emplace_back(std::move(object));
    }

    objects[entity.getID()] = ptr;

    if (initialized)
    {
        initializeObject(ptr, parent ? parent->effectiveEnabled : true);
    }
    else
    {
        ptr->initialized = false;
        ptr->effectiveEnabled = (parent ? parent->effectiveEnabled : true) && ptr->enabled;
    }

    return ptr;
}

void Scene::initializeObject(EngineObject* object, bool parentEffective)
{
    if (!object || object->initialized)
        return;

    object->initialized = true;
    object->effectiveEnabled = parentEffective && object->enabled;

    if (object->onCreate)
        object->onCreate(*object);

    if (object->effectiveEnabled && object->onEnable)
        object->onEnable(*object);

    for (auto& child : object->children)
    {
        initializeObject(child.get(), object->effectiveEnabled);
    }
}

void Scene::refreshEnabledState(EngineObject* object, bool parentEffective)
{
    if (!object)
        return;

    bool previousEffective = object->effectiveEnabled;
    object->effectiveEnabled = parentEffective && object->enabled;

    if (object->initialized && previousEffective != object->effectiveEnabled)
    {
        if (object->effectiveEnabled)
        {
            if (object->onEnable)
                object->onEnable(*object);
        }
        else
        {
            if (object->onDisable)
                object->onDisable(*object);
        }
    }

    for (auto& child : object->children)
    {
        refreshEnabledState(child.get(), object->effectiveEnabled);
    }
}

bool Scene::isObjectActive(const EngineObject* object) const
{
    return object && object->isActive();
}

void Scene::unregisterObject(EngineObject* object)
{
    if (!object)
        return;

    objects.erase(object->getEntity().getID());
}

void Scene::destroyObject(EngineObject* object)
{
    if (!object)
        return;

    while (!object->children.empty())
    {
        destroyObject(object->children.back().get());
    }

    if (object->initialized && object->onDestroy)
        object->onDestroy(*object);

    registry.destroyEntity(object->getEntity());
    unregisterObject(object);

    auto removeFrom = [object](std::vector<std::unique_ptr<EngineObject>>& container)
    {
        auto it = std::remove_if(container.begin(), container.end(), [object](const std::unique_ptr<EngineObject>& candidate)
        {
            return candidate.get() == object;
        });

        if (it != container.end())
        {
            container.erase(it, container.end());
            return true;
        }

        return false;
    };

    if (object->parent)
    {
        removeFrom(object->parent->children);
    }
    else
    {
        removeFrom(roots);
    }
}

void Scene::setEnabled(EngineObject* object, bool value)
{
    if (!object)
        return;

    bool previousEffective = object->effectiveEnabled;
    object->enabled = value;
    object->effectiveEnabled = (object->parent ? object->parent->effectiveEnabled : true) && object->enabled;

    if (object->initialized && previousEffective != object->effectiveEnabled)
    {
        if (object->effectiveEnabled)
        {
            if (object->onEnable)
                object->onEnable(*object);
        }
        else
        {
            if (object->onDisable)
                object->onDisable(*object);
        }
    }

    for (auto& child : object->children)
    {
        refreshEnabledState(child.get(), object->effectiveEnabled);
    }
}

void Scene::init()
{
    if (initialized)
        return;

    for (auto& obj : roots)
    {
        initializeObject(obj.get(), true);
    }

    initialized = true;
}

void Scene::destroy()
{
    if (!initialized && roots.empty())
        return;

    while (!roots.empty())
    {
        destroyObject(roots.back().get());
    }

    objects.clear();
    initialized = false;
}

} // namespace sle::entity
