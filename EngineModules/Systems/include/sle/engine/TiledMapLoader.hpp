#pragma once

#include <sle/core/Result.hpp>
#include <sle/scene/Scene.hpp>

#include <string>

namespace sle {

class TiledMapLoader
{
public:
    static sle::core::Result<bool> load(const std::string& tmjPath, sle::entity::Scene& scene);
};

} // namespace sle
