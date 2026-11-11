#pragma once

#include <sle/scene/Registry.hpp>
#include <nlohmann/json.hpp>
#include <functional>
#include <map>
#include <string>
#include <vector>

namespace sle::componentdata {
using Json = nlohmann::json;

// Shared, VM-independent component codecs. Runtime mutation hooks stay in Runtime.
struct Descriptor
{
    std::function<Json(entity::Registry&, entity::Entity)> get;
    std::function<Json(entity::Registry&, entity::Entity, const Json&)> validate;
    std::function<void(entity::Registry&, entity::Entity, const Json&)> set;
    std::function<void(entity::Registry&, entity::Entity)> remove;
    std::function<void(entity::Registry&, std::vector<uint32_t>&)> find;
};

const std::map<std::string, Descriptor>& descriptors();
// Canonical field names only; scene compatibility syntax is adapted separately.
Json normalizePatch(const std::string& component, const Json& patch);
std::string resolveAssetPath(const std::string& path);
} // namespace sle::componentdata
