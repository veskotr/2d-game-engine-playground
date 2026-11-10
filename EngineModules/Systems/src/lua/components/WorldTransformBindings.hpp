#pragma once
#include "ValueValidation.hpp"
#include <sle/scene/components/WorldTransformComponent.hpp>

namespace sle::lua::components {
using namespace sle::components;
inline Json snapshot(const WorldTransformComponent& c) { Json j=Json::object();
 j["position"]=vec(c.position);
 j["scale"]=vec(c.scale);
 j["rotation"]=c.rotation;
 return j; }
} // namespace sle::lua::components
