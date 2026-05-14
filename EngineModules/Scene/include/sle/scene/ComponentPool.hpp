#pragma once
#include <cstdint>
#include <unordered_map>

namespace sle::entity {

struct IComponentPool
{
    virtual ~IComponentPool() = default;
    virtual void erase(uint32_t entityID) = 0;
};

template <typename T>
class ComponentPool : public IComponentPool
{
public:
    // Construct or replace the component for entityID in-place.
    template <typename... Args>
    T& emplace(uint32_t entityID, Args&&... args)
    {
        auto [it, _] = data.insert_or_assign(entityID, T(std::forward<Args>(args)...));
        return it->second;
    }

    // Returns a pointer to the component, or nullptr if not present.
    T* get(uint32_t entityID)
    {
        auto it = data.find(entityID);
        return it != data.end() ? &it->second : nullptr;
    }

    const T* get(uint32_t entityID) const
    {
        auto it = data.find(entityID);
        return it != data.end() ? &it->second : nullptr;
    }

    bool has(uint32_t entityID) const
    {
        return data.contains(entityID);
    }

    void erase(uint32_t entityID) override
    {
        data.erase(entityID);
    }

    // Raw entry access for iteration (e.g. view queries).
    std::unordered_map<uint32_t, T>& entries() { return data; }
    const std::unordered_map<uint32_t, T>& entries() const { return data; }

private:
    std::unordered_map<uint32_t, T> data;
};

} // namespace sle::entity
