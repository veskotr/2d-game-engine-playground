#pragma once
#include "ValueValidation.hpp"
#include <sle/scene/components/StateMachineComponent.hpp>

// Lua-internal: definitionAssetLoadAttempted: resource load cache
// Lua-internal: definition: native shared resource ownership
// Lua-internal: initialized: runtime state-machine lifecycle

namespace sle::lua::components {
using namespace sle::components;
inline Json snapshot(const StateMachineComponent& c) { Json j=Json::object();
 j["definitionAsset"]=c.definitionAsset;
 j["enabled"]=c.enabled;
 j["boolParameters"]=c.boolParameters;
 j["triggers"]=c.triggers;
 j["currentState"]=c.currentState; j["previousState"]=c.previousState; j["stateTimeSeconds"]=c.stateTimeSeconds;
 return j; }
inline void apply(StateMachineComponent& c, const Json& p, entity::Registry& registry) {
 for(auto it=p.begin();it!=p.end();++it) { const auto& key=it.key(); const auto& v=it.value();
 if(key=="definitionAsset") { validate(v,"str");
 c.definitionAsset=v.get<decltype(c.definitionAsset)>();
 }
 else if(key=="enabled") { validate(v,"bool");
 c.enabled=v.get<decltype(c.enabled)>();
 }
 else if(key=="boolParameters") { validate(v,"boolmap");
 c.boolParameters=v.get<decltype(c.boolParameters)>();
 }
 else if(key=="triggers") { validate(v,"strings");
 if(v.is_object() && v.empty()) c.triggers.clear();
 else c.triggers=v.get<decltype(c.triggers)>();
 }
 else throw std::invalid_argument("Unknown or read-only component field: "+key); }
 (void)registry; }

inline void invalidate(StateMachineComponent& next,const StateMachineComponent* old) { if(old && next.definitionAsset!=old->definitionAsset) { next.definition.reset(); next.definitionAssetLoadAttempted=false; next.initialized=false; next.currentState.clear(); next.previousState.clear(); next.stateTimeSeconds=0; } }
} // namespace sle::lua::components
