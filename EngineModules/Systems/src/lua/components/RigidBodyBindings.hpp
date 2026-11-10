#pragma once
#include "ValueValidation.hpp"
#include <sle/scene/components/RigidBodyComponent.hpp>

// Lua-internal: mass: physics derives mass from fixture density
// Lua-internal: box2dBodyId: native physics ownership

namespace sle::lua::components {
using namespace sle::components;
inline Json snapshot(const RigidBodyComponent& c) { Json j=Json::object();
 j["bodyType"]=c.bodyType==BodyType::Static ? "Static" : c.bodyType==BodyType::Dynamic ? "Dynamic" : "Kinematic";
 j["linearDamping"]=c.linearDamping;
 j["angularDamping"]=c.angularDamping;
 j["gravityScale"]=c.gravityScale;
 j["velocity"]=vec(c.velocity);
 j["angularVelocity"]=c.angularVelocity;
 j["fixedRotation"]=c.fixedRotation;
 j["enabled"]=c.enabled;
 return j; }
inline void apply(RigidBodyComponent& c, const Json& p, entity::Registry& registry) {
 for(auto it=p.begin();it!=p.end();++it) { const auto& key=it.key(); const auto& v=it.value();
 if(key=="bodyType") { validate(v,"body");
 c.bodyType=v=="Static" ? BodyType::Static : v=="Dynamic" ? BodyType::Dynamic : BodyType::Kinematic;
 }
 else if(key=="linearDamping") { validate(v,"nonneg");
 c.linearDamping=number(v);
 }
 else if(key=="angularDamping") { validate(v,"nonneg");
 c.angularDamping=number(v);
 }
 else if(key=="gravityScale") { validate(v,"float");
 c.gravityScale=number(v);
 }
 else if(key=="velocity") { validate(v,"v2");
 c.velocity=glm::vec2(number(v.at("x")),number(v.at("y")));
 }
 else if(key=="angularVelocity") { validate(v,"float");
 c.angularVelocity=number(v);
 }
 else if(key=="fixedRotation") { validate(v,"bool");
 c.fixedRotation=v.get<decltype(c.fixedRotation)>();
 }
 else if(key=="enabled") { validate(v,"bool");
 c.enabled=v.get<decltype(c.enabled)>();
 }
 else throw std::invalid_argument("Unknown or read-only component field: "+key); }
 (void)registry; }
} // namespace sle::lua::components
