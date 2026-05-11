#pragma once

#include <memory>
#include <string>
#include <unordered_map>
#include <typeindex>

struct IResourcePool
{
    virtual ~IResourcePool() = default;
    virtual void clear() = 0;
};

template<typename T>
class ResourcePool : public IResourcePool
{
public:
    std::unordered_map<std::string, std::shared_ptr<T>> data;

    void clear() override
    {
        data.clear();
    }
};

class Resources
{
public:
    template<typename T, typename... Args>
    static std::shared_ptr<T> create(const std::string& id, Args&&... args);

    template<typename T>
    static std::shared_ptr<T> get(const std::string& id);

    template<typename T>
    static void unload(const std::string& id);

    static void clear();

private:
    template<typename T>
    static ResourcePool<T>& pool();

    static std::unordered_map<std::type_index, std::unique_ptr<IResourcePool>> pools;
};

template<typename T>
ResourcePool<T>& Resources::pool()
{
    std::type_index id(typeid(T));

    auto it = pools.find(id);
    if (it == pools.end())
    {
        auto newPool = std::make_unique<ResourcePool<T>>();
        auto* ptr = newPool.get();
        pools[id] = std::move(newPool);
        return *ptr;
    }

    return *static_cast<ResourcePool<T>*>(it->second.get());
}

template<typename T, typename... Args>
std::shared_ptr<T> Resources::create(const std::string& id, Args&&... args)
{
    auto& p = pool<T>();

    if (auto it = p.data.find(id); it != p.data.end())
        return it->second;

    auto resource = std::make_shared<T>();

    if constexpr (requires(T t, Args&&... a) { t.loadFromFiles(std::forward<Args>(a)...); })
    {
        if (!resource->loadFromFiles(std::forward<Args>(args)...))
            return nullptr;
    }

    p.data[id] = resource;
    return resource;
}