#pragma once
#include <sle/scene/ComponentPool.hpp>
#include <sle/scene/Entity.hpp>
#include <memory>
#include <unordered_set>
#include <typeindex>
#include <unordered_map>

namespace sle::entity {

class Registry
{
public:
    Entity createEntity();
    void destroyEntity(Entity entity);
    bool hasEntity(Entity entity) const;

    // Construct or replace component T on entity. Returns a reference to the component.
    template <typename T, typename... Args>
    T& addComponent(Entity entity, Args&&... args);

    // Returns a pointer to component T, or nullptr if the entity does not have one.
    template <typename T>
    T* getComponent(Entity entity);

    template <typename T>
    const T* getComponent(Entity entity) const;

    template <typename T>
    bool hasComponent(Entity entity) const;

    // Removes component T from entity. No-op if the entity does not have one.
    template <typename T>
    void removeComponent(Entity entity);

    // Iterates all entities that have component T, calling func(Entity, T&).
    template <typename T, typename Func>
    void view(Func func);

    // Iterates all entities that have both T1 and T2, calling func(Entity, T1&, T2&).
    template <typename T1, typename T2, typename Func>
    void view(Func func);

private:
    uint32_t nextEntityID = 1;
    std::unordered_set<uint32_t> aliveEntities;
    std::unordered_map<std::type_index, std::unique_ptr<IComponentPool>> pools;

    // Returns the pool for T, creating it if it does not yet exist.
    template <typename T>
    ComponentPool<T>& ensurePool();

    // Returns a pointer to the pool for T, or nullptr if it has never been created.
    template <typename T>
    ComponentPool<T>* findPool();

    template <typename T>
    const ComponentPool<T>* findPool() const;
};

// --- ensurePool ---

template <typename T>
ComponentPool<T>& Registry::ensurePool()
{
    auto key = std::type_index(typeid(T));
    auto it = pools.find(key);
    if (it == pools.end())
    {
        auto p = std::make_unique<ComponentPool<T>>();
        auto* ptr = p.get();
        pools.emplace(key, std::move(p));
        return *ptr;
    }
    return *static_cast<ComponentPool<T>*>(it->second.get());
}

// --- findPool ---

template <typename T>
ComponentPool<T>* Registry::findPool()
{
    auto it = pools.find(std::type_index(typeid(T)));
    if (it == pools.end())
        return nullptr;
    return static_cast<ComponentPool<T>*>(it->second.get());
}

template <typename T>
const ComponentPool<T>* Registry::findPool() const
{
    auto it = pools.find(std::type_index(typeid(T)));
    if (it == pools.end())
        return nullptr;
    return static_cast<const ComponentPool<T>*>(it->second.get());
}

// --- addComponent ---

template <typename T, typename... Args>
T& Registry::addComponent(Entity entity, Args&&... args)
{
    return ensurePool<T>().emplace(entity.getID(), std::forward<Args>(args)...);
}

// --- getComponent ---

template <typename T>
T* Registry::getComponent(Entity entity)
{
    auto* p = findPool<T>();
    return p ? p->get(entity.getID()) : nullptr;
}

template <typename T>
const T* Registry::getComponent(Entity entity) const
{
    const auto* p = findPool<T>();
    return p ? p->get(entity.getID()) : nullptr;
}

// --- hasComponent ---

template <typename T>
bool Registry::hasComponent(Entity entity) const
{
    const auto* p = findPool<T>();
    return p && p->has(entity.getID());
}

// --- removeComponent ---

template <typename T>
void Registry::removeComponent(Entity entity)
{
    auto* p = findPool<T>();
    if (p)
        p->erase(entity.getID());
}

// --- view ---

template <typename T, typename Func>
void Registry::view(Func func)
{
    auto* p = findPool<T>();
    if (!p)
        return;

    for (auto& [entityID, comp] : p->entries())
        func(Entity(entityID), comp);
}

template <typename T1, typename T2, typename Func>
void Registry::view(Func func)
{
    auto* p1 = findPool<T1>();
    auto* p2 = findPool<T2>();
    if (!p1 || !p2)
        return;

    for (auto& [entityID, comp1] : p1->entries())
    {
        auto* comp2 = p2->get(entityID);
        if (!comp2)
            continue;

        func(Entity(entityID), comp1, *comp2);
    }
}

} // namespace sle::entity
