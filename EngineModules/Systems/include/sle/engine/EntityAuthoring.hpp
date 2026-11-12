#pragma once

#include <sle/core/Result.hpp>
#include <nlohmann/json.hpp>

namespace sle {
// VM/graphics-independent authoring for one entity in the existing prefab format.
class EntityAuthoring
{
public:
    static nlohmann::json componentCatalog();
    static core::Result<bool> validatePrefab(const nlohmann::json& document);
    static nlohmann::json canonicalPrefab(const nlohmann::json& document);
    static nlohmann::json canonicalPrefab(const nlohmann::json& document, const nlohmann::json& catalog);
};
}
