#pragma once
#include <cstdint>

namespace sle::entity {

class Entity
{
public:
    Entity() = default;
    explicit Entity(uint32_t id) : m_id(id) {}

    uint32_t getID() const { return m_id; }
    bool valid() const { return m_id != 0; }

    bool operator==(const Entity&) const = default;

private:
    uint32_t m_id = 0;
};

} // namespace sle::entity
