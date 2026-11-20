#pragma once
#include "ValueValidation.hpp"
#include <sle/scene/components/TagsComponent.hpp>
#include <unordered_set>

namespace sle::lua::components {
using sle::components::TagsComponent;

inline Json snapshot(const TagsComponent& tags)
{
    return Json{{"ids", tags.ids}};
}

inline void apply(TagsComponent& tags, const Json& patch, entity::Registry&)
{
    for (const auto& [field, value] : patch.items()) {
        if (field != "ids" || !value.is_array())
            throw std::invalid_argument("Expected a tag ID list");
        std::unordered_set<std::string> seen;
        auto ids = value.get<std::vector<std::string>>();
        for (const auto& id : ids)
            if (id.empty() || !seen.insert(id).second)
                throw std::invalid_argument("Expected unique nonempty tag IDs");
        tags.ids = std::move(ids);
    }
}
}
