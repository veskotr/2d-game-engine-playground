#pragma once
#include <sle/scene/Registry.hpp>
#include <sle/scene/Entity.hpp>
#include <sle/events/EventBus.hpp>
#include <unordered_map>
#include <vector>
#include <memory>
#include <string>

namespace sle::entity {

// Scene is purely structural: it owns entity lifetime and the parent-child
// hierarchy. All component data lives in Registry.
class Scene
{
public:
    Scene() = default;
    ~Scene();

    // Optional entity-like scene root. Created lazily; ordinary components/scripts.
    Entity ensureSceneEntity();
    Entity getSceneEntity() const { return sceneEntity; }

    // Entity lifetime
    Entity createEntity();
    void destroyEntity(Entity entity);  // destroys entity and all its descendants

    // Optional authored identity, independent of registry-local runtime IDs.
    // A nonempty key belongs to one live entity in this scene. Renaming is atomic.
    bool setEntityKey(Entity entity, const std::string& key);
    Entity findEntityByKey(const std::string& key) const;

    // Hierarchy
    // Pass an invalid Entity as parent to make the child a root.
    // Returns false for dead entities, self-parenting, or hierarchy cycles; no mutation.
    bool setParent(Entity child, Entity parent);
    Entity getParent(Entity entity) const;
    const std::vector<Entity>& getChildren(Entity entity) const;
    const std::vector<Entity>& getRoots() const { return roots; }

    // ECS component access
    Registry& getRegistry() { return registry; }
    const Registry& getRegistry() const { return registry; }

    // Event bus for decoupled communication between systems within this scene.
    sle::events::EventBus& getEventBus() { return eventBus; }

    template <typename T, typename Func>
    void view(Func func)
    {
        registry.view<T>(func);
    }

    template <typename T1, typename T2, typename Func>
    void view(Func func)
    {
        registry.view<T1, T2>(func);
    }

    // Destroys all entities and clears all hierarchy data.
    void destroy();

    // Stage file-loaded data with fresh IDs, leaving this scene untouched.
    std::unique_ptr<Scene> createStagingScene() const;
    // Commit staged data. The live event bus keeps its address/token sequence,
    // but old subscriptions and deferred scene events are cleared on success.
    void replaceWith(Scene& staged);
    // Reserve staging IDs before teardown callbacks can create live entities.
    void reserveStagedEntityIds(const Scene& staged);

private:
    void destroyEntityInternal(Entity entity);
    void detachFromParent(Entity entity);
    void markTransformBranchDirty(Entity entity);

    std::unordered_map<std::string, Entity> keyedEntities;
    std::unordered_map<uint32_t, std::string> entityKeys;
    Entity sceneEntity;
    Registry registry;
    sle::events::EventBus eventBus;
    std::unordered_map<uint32_t, std::vector<Entity>> childrenMap;     // entityID -> children
    std::vector<Entity> roots;

    inline static const std::vector<Entity> emptyChildren{};
};

} // namespace sle::entity
