#pragma once
#include "ValueValidation.hpp"
#include <sle/scene/components/Transform.hpp>

// Lua-field: position -> position
// Lua-field: rotation -> rotation
// Lua-field: scale -> scale
// Lua-internal: dirty: Transform setters maintain propagation
// Lua-internal: parent: use the hierarchy API

namespace sle::lua::components {
using namespace sle::components;
inline Json snapshot(const TransformComponent& c) { Json j=Json::object();
 j["position"]=vec(c.getPosition());
 j["scale"]=vec(c.getScale());
 j["rotation"]=c.getRotation();
 return j; }
inline void apply(TransformComponent& c, const Json& p, entity::Registry& registry) {
 for(auto it=p.begin();it!=p.end();++it) { const auto& key=it.key(); const auto& v=it.value();
 if(key=="position") { validate(v,"v2");
 c.setPosition(glm::vec2(number(v.at("x")),number(v.at("y"))));
 }
 else if(key=="scale") { validate(v,"v2");
 c.setScale(glm::vec2(number(v.at("x")),number(v.at("y"))));
 }
 else if(key=="rotation") { validate(v,"float");
 c.setRotation(number(v));
 }
 else throw std::invalid_argument("Unknown or read-only component field: "+key); }
 (void)registry; }
} // namespace sle::lua::components
