#pragma once
#include <sle/scene/Registry.hpp>
#include <sle/scene/Entity.hpp>
#include <unordered_map>
#include <vector>
#include <sle/core/EventBus.hpp>

namespace sle::entity {

// Scene is purely structural: it owns entity lifetime and the parent-child
// hierarchy. All component data lives in Registry.
class Scene
{
public:
    Scene() = default;
    ~Scene();

    // Entity lifetime
    Entity createEntity();
    void destroyEntity(Entity entity);  // destroys entity and all its descendants

    // Hierarchy
    // Pass an invalid Entity as parent to make the child a root.
    void setParent(Entity child, Entity parent);
    Entity getParent(Entity entity) const;
    const std::vector<Entity>& getChildren(Entity entity) const;
    const std::vector<Entity>& getRoots() const { return roots; }

    // ECS component access
    Registry& getRegistry() { return registry; }

    // Event bus for decoupled communication between systems within this scene.
    core::EventBus& getEventBus() { return eventBus; }

    template <typename T1, typename T2, typename Func>
    void view(Func func)
    {
        registry.view<T1, T2>(func);
    }

    // Destroys all entities and clears all hierarchy data.
    void destroy();

private:
    void destroyEntityInternal(Entity entity);
    void detachFromParent(Entity entity);

    Registry registry;
    core::EventBus eventBus;
    std::unordered_map<uint32_t, Entity> parentMap;                    // entityID -> parent (absent = root)
    std::unordered_map<uint32_t, std::vector<Entity>> childrenMap;     // entityID -> children
    std::vector<Entity> roots;

    inline static const std::vector<Entity> emptyChildren{};
};

} // namespace sle::entity
