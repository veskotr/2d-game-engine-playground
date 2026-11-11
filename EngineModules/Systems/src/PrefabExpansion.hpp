#pragma once
#include <sle/core/Result.hpp>
#include <nlohmann/json.hpp>
#include <string>

namespace sle::detail {
core::Result<nlohmann::json> expandScenePrefabs(const nlohmann::json& source, const std::string& path);
} // namespace sle::detail
