#pragma once
#include "ValueValidation.hpp"
#include <sle/scene/components/Camera2DComponent.hpp>
namespace sle::lua::components {
using namespace sle::components;
inline Json snapshot(const Camera2DComponent& c) {
 return {{"enabled",c.enabled},{"primary",c.primary},{"viewSize",vec(c.viewSize)},{"zoom",c.zoom}};
}
inline void apply(Camera2DComponent& c, const Json& p, entity::Registry&) {
 for (const auto& [key,v] : p.items()) {
  if (key=="enabled") { validate(v,"bool"); c.enabled=v.get<bool>(); }
  else if (key=="primary") { validate(v,"bool"); c.primary=v.get<bool>(); }
  else if (key=="viewSize") { validate(v,"positivev2"); c.viewSize={number(v.at("x")),number(v.at("y"))}; }
  else if (key=="zoom") { validate(v,"positive"); c.zoom=number(v); }
  else throw std::invalid_argument("Unknown or read-only component field: "+key);
 }
}
}
