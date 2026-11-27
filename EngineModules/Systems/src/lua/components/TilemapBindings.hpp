#pragma once
#include "ValueValidation.hpp"
#include <sle/scene/components/TilemapComponent.hpp>

namespace sle::lua::components {
using sle::components::TilemapComponent;
inline Json snapshot(const TilemapComponent& map)
{
    return {{"asset", map.asset}, {"sortingOffset", map.sortingOffset}};
}
inline void apply(TilemapComponent& map, const Json& patch, entity::Registry&)
{
    for (const auto& [field, value] : patch.items()) {
        if (field == "asset") { validate(value, "str"); map.asset = value.get<std::string>(); }
        else if (field == "sortingOffset") { validate(value, "int"); map.sortingOffset = value.get<int>(); }
        else throw std::invalid_argument("Unknown Tilemap field: " + field);
    }
}
}
