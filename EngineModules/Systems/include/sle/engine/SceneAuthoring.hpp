#pragma once
#include <nlohmann/json.hpp>

namespace sle {
// Context-independent validation and normalization for flat editable scenes.
class SceneAuthoring {
public:
    static nlohmann::json canonicalScene(const nlohmann::json& document);
};
}
