#pragma once

#include <sle/core/Result.hpp>
#include <sle/scene/Scene.hpp>

#include <string>

namespace sle {

class SceneLoader
{
public:
    static sle::core::Result<bool> load(const std::string& jsonPath, sle::entity::Scene& scene);
};

} // namespace sle
