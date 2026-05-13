#pragma once
#include <cstdint>
#include <memory>
#include <unordered_map>

namespace sle::entity {

struct IComponentPool
{
    virtual ~IComponentPool() = default;
    virtual void erase(uint32_t entityID) = 0;
};

template<typename T>
class ComponentPool : public IComponentPool
{
public:
    std::unordered_map<uint32_t, T> data;

    void erase(uint32_t entityID) override
    {
        data.erase(entityID);
    }
};

} // namespace sle::entity
