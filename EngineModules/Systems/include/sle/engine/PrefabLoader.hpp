#pragma once
#include <sle/core/Result.hpp>
#include <sle/scene/Scene.hpp>
#include <nlohmann/json.hpp>
#include <string>

namespace sle {
class PrefabLoader
{
public:
    static core::Result<entity::Entity> instantiate(const std::string& path, entity::Scene& scene,
        const std::string& key, const nlohmann::json& overrides = nlohmann::json::object(),
        entity::Entity parent = {});
};
} // namespace sle
