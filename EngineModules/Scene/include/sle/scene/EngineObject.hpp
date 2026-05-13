#pragma once
#include <sle/scene/Entity.hpp>
#include <vector>
#include <memory>
#include <functional>

namespace sle::entity {

class Registry;
class Scene;

class EngineObject
{
public:
    EngineObject(Scene* scene, Registry* registry, Entity entity);
    ~EngineObject();

    // hierarchy
    EngineObject* createChild();
    void destroy();

    EngineObject* getParent() const { return parent; }
    const std::vector<std::unique_ptr<EngineObject>>& getChildren() const { return children; }

    Entity getEntity() const { return entity; }

    bool isEnabled() const { return enabled; }
    bool isActive() const { return initialized && effectiveEnabled; }
    void setEnabled(bool value);

    // lifecycle callbacks
    std::function<void(EngineObject&)> onCreate;
    std::function<void(EngineObject&)> onEnable;
    std::function<void(EngineObject&)> onDisable;
    std::function<void(EngineObject&)> onDestroy;

private:
    friend class Scene;

    Scene* scene;
    Registry* registry;
    Entity entity;

    EngineObject* parent = nullptr;
    std::vector<std::unique_ptr<EngineObject>> children;

    bool enabled = true;
    bool initialized = false;
    bool effectiveEnabled = true;
};

} // namespace sle::entity
