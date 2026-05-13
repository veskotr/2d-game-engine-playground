#pragma once
#include <sle/scene/Registry.hpp>
#include <sle/scene/EngineObject.hpp>
#include <memory>
#include <unordered_map>
#include <vector>

namespace sle::entity {

class Scene
{
public:
    Scene() = default;
    ~Scene();

    // create high-level engine object
    EngineObject* createObject();

    // internal ECS access
    Registry& getRegistry() { return registry; }

    template <typename T1, typename T2, typename Func>
    void view(Func func)
    {
        registry.view<T1, T2>([this, &func](Entity entity, T1& first, T2& second)
        {
            auto it = objects.find(entity.getID());
            if (it == objects.end() || !isObjectActive(it->second))
                return;

            func(entity, first, second);
        });
    }

    // lifecycle
    void init();
    void destroy();

private:
    friend class EngineObject;

    EngineObject* createObject(EngineObject* parent);
    void destroyObject(EngineObject* object);
    void setEnabled(EngineObject* object, bool value);

    void initializeObject(EngineObject* object, bool parentEffective);
    void refreshEnabledState(EngineObject* object, bool parentEffective);
    bool isObjectActive(const EngineObject* object) const;
    void unregisterObject(EngineObject* object);

    Registry registry;
    std::vector<std::unique_ptr<EngineObject>> roots;
    std::unordered_map<uint32_t, EngineObject*> objects;
    bool initialized = false;
};

} // namespace sle::entity
