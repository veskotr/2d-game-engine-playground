#pragma once
#include <sle/core/Result.hpp>
#include <nlohmann/json.hpp>
#include <string>

namespace sle {
// Versioned, self-contained prefab ZIP. Materialized asset files live in the
// derived disk cache so scripts/audio may open them throughout a scene's life.
class PrefabPackage {
public:
    static core::Result<nlohmann::json> readScene(const std::string& path);
    static core::Result<nlohmann::json> read(const std::string& path);
};
}
