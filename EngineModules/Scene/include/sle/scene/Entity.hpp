#pragma once
#include <cstdint>

namespace sle::entity {

class Registry;

class Entity
{
public:
    Entity() = default;
    Entity(uint32_t id, Registry* registry)
        : id(id), registry(registry) {}

    uint32_t getID() const { return id; }
    bool valid() const { return registry != nullptr; }

private:
    uint32_t id = 0;
    Registry* registry = nullptr;
};

} // namespace sle::entity
