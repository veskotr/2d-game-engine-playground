#pragma once
#include <integer.hpp>

struct BatchKey
{
    uint32_t layer;
    uint32_t shader_id;
    uint32_t texture_id;

    bool operator==(const BatchKey& other) const
    {
        return layer == other.layer &&
               shader_id == other.shader_id &&
               texture_id == other.texture_id;
    }
};
struct BatchKeyHash
{
    std::size_t operator()(const BatchKey& k) const
    {
        return (std::hash<uint32_t>()(k.layer) ^
               (std::hash<uint32_t>()(k.shader_id) << 1)) ^
               (std::hash<uint32_t>()(k.texture_id) << 2);
    }
};