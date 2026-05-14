#pragma once
#include <sle/scene/Scene.hpp>

namespace sle {

// Lightweight per-frame context passed to every system's update call.
struct SystemContext
{
    sle::entity::Scene& scene;
    float               dt;
};

} // namespace sle
