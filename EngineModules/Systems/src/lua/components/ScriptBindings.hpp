#pragma once
#include "ValueValidation.hpp"
#include <sle/scene/components/ScriptComponent.hpp>

namespace sle::lua::components {
using namespace sle::components;
inline Json snapshot(const ScriptComponent& c) { Json j=Json::object();
 j["scriptAsset"]=c.scriptAsset;
 j["enabled"]=c.enabled;
 return j; }
inline void apply(ScriptComponent& c, const Json& p, entity::Registry& registry) {
 for(auto it=p.begin();it!=p.end();++it) { const auto& key=it.key(); const auto& v=it.value();
 if(key=="scriptAsset") { validate(v,"str");
 c.scriptAsset=v.get<decltype(c.scriptAsset)>();
 }
 else if(key=="enabled") { validate(v,"bool");
 c.enabled=v.get<decltype(c.enabled)>();
 }
 else throw std::invalid_argument("Unknown or read-only component field: "+key); }
 (void)registry; }
} // namespace sle::lua::components
