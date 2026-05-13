#pragma once
#include <sle/scene/ComponentPool.hpp>
#include <sle/scene/Entity.hpp>
#include <memory>
#include <typeindex>
#include <unordered_map>

namespace sle::entity {

class Registry
{
public:
    Entity createEntity();
    void destroyEntity(Entity entity);

    template <typename T, typename... Args>
    T &addComponent(Entity entity, Args &&...args);

    template <typename T>
    T *getComponent(Entity entity);

    template <typename T>
    bool hasComponent(Entity entity);

    template <typename T1, typename T2, typename Func>
    void view(Func func);

private:
    uint32_t nextEntityID = 1;

    std::unordered_map<std::type_index, std::unique_ptr<IComponentPool>> pools;

    template <typename T>
    ComponentPool<T> &pool();
};

template <typename T>
ComponentPool<T> &Registry::pool()
{
    std::type_index id(typeid(T));

    auto it = pools.find(id);
    if (it == pools.end())
    {
        auto pool = std::make_unique<ComponentPool<T>>();
        auto *ptr = pool.get();
        pools[id] = std::move(pool);
        return *ptr;
    }

    return *static_cast<ComponentPool<T> *>(it->second.get());
}

template <typename T, typename... Args>
T &Registry::addComponent(Entity entity, Args &&...args)
{
    auto &p = pool<T>();
    auto &component = p.data[entity.getID()];
    component = T(std::forward<Args>(args)...);
    return component;
}

template <typename T>
T *Registry::getComponent(Entity entity)
{
    auto &p = pool<T>();

    auto it = p.data.find(entity.getID());
    if (it == p.data.end())
        return nullptr;

    return &it->second;
}

template <typename T>
bool Registry::hasComponent(Entity entity)
{
    auto &p = pool<T>();
    return p.data.contains(entity.getID());
}

template <typename T1, typename T2, typename Func>
void Registry::view(Func func)
{
    auto &pool1 = pool<T1>().data;
    auto &pool2 = pool<T2>().data;

    for (auto &[entityID, comp1] : pool1)
    {
        auto it = pool2.find(entityID);
        if (it == pool2.end())
            continue;

        func(Entity(entityID, this), comp1, it->second);
    }
}

} // namespace sle::entity
