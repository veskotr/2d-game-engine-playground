#pragma once
#include "ValueValidation.hpp"
#include <unordered_set>
#include <sle/scene/components/ScriptComponent.hpp>

namespace sle::lua::components {
using namespace sle::components;
inline Json snapshot(const ScriptComponent& c) { Json j=Json::object();
 j["scriptAsset"]=c.scriptAsset;
 j["enabled"]=c.enabled;
 j["behaviours"]=Json::array();
 for(const auto& b:c.behaviours) j["behaviours"].push_back({{"key",b.key},{"scriptAsset",b.scriptAsset},{"enabled",b.enabled},{"order",b.order}});
 return j; }
inline void apply(ScriptComponent& c, const Json& p, entity::Registry& registry) {
 for(auto it=p.begin();it!=p.end();++it) { const auto& key=it.key(); const auto& v=it.value();
 if(key=="scriptAsset") { validate(v,"str");
 c.scriptAsset=v.get<decltype(c.scriptAsset)>();
 }
 else if(key=="enabled") { validate(v,"bool");
 c.enabled=v.get<decltype(c.enabled)>();
 }
 else if(key=="behaviours") {
  if(!v.is_array() && !(v.is_object() && v.empty())) throw std::invalid_argument("Expected behaviour array");
  std::vector<ScriptBehaviour> next;
  std::unordered_set<std::string> keys;
  for(const auto& entry:v) {
   if(!entry.is_object() || !entry.contains("key") || !entry.contains("scriptAsset")) throw std::invalid_argument("Behaviour requires key and scriptAsset");
   ScriptBehaviour b;
   for(const auto& [field,value]:entry.items()) {
    if(field=="key") { validate(value,"str"); b.key=value.get<std::string>(); }
    else if(field=="scriptAsset") { validate(value,"str"); b.scriptAsset=value.get<std::string>(); }
    else if(field=="enabled") { validate(value,"bool"); b.enabled=value.get<bool>(); }
    else if(field=="order") { validate(value,"int"); b.order=value.get<int>(); }
    else throw std::invalid_argument("Unknown behaviour field: "+field);
   }
   if(b.key.empty() || b.scriptAsset.empty() || !keys.insert(b.key).second) throw std::invalid_argument("Behaviour keys/assets must be nonempty and keys unique");
   next.push_back(std::move(b));
  }
  c.behaviours=std::move(next);
 }
 else throw std::invalid_argument("Unknown or read-only component field: "+key); }
 (void)registry; }
} // namespace sle::lua::components
