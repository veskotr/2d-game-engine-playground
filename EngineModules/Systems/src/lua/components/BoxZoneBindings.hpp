#pragma once
#include "ValueValidation.hpp"
#include <sle/scene/components/BoxZoneComponent.hpp>

// Lua-internal: box2dFixtureId: native physics ownership

namespace sle::lua::components {
using namespace sle::components;
inline Json snapshot(const BoxZoneComponent& c) { Json j=Json::object();
 j["offset"]=vec(c.offset);
 j["size"]=vec(c.size);
 j["zoneId"]=c.zoneId;
 j["categoryBits"]=c.categoryBits;
 j["maskBits"]=c.maskBits;
 j["enabled"]=c.enabled;
 return j; }
inline void apply(BoxZoneComponent& c, const Json& p, entity::Registry& registry) {
 for(auto it=p.begin();it!=p.end();++it) { const auto& key=it.key(); const auto& v=it.value();
 if(key=="offset") { validate(v,"v2");
 c.offset=glm::vec2(number(v.at("x")),number(v.at("y")));
 }
 else if(key=="size") { validate(v,"positivev2");
 c.size=glm::vec2(number(v.at("x")),number(v.at("y")));
 }
 else if(key=="zoneId") { validate(v,"str");
 c.zoneId=v.get<decltype(c.zoneId)>();
 }
 else if(key=="categoryBits") { validate(v,"u16");
 c.categoryBits=v.get<decltype(c.categoryBits)>();
 }
 else if(key=="maskBits") { validate(v,"u16");
 c.maskBits=v.get<decltype(c.maskBits)>();
 }
 else if(key=="enabled") { validate(v,"bool");
 c.enabled=v.get<decltype(c.enabled)>();
 }
 else throw std::invalid_argument("Unknown or read-only component field: "+key); }
 (void)registry; }
} // namespace sle::lua::components
