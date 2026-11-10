#pragma once
#include "ValueValidation.hpp"
#include <sle/scene/components/AnimatorComponent.hpp>

// Lua-internal: clipLoadAttempted: resource load cache
// Lua-internal: clip: native shared resource ownership
// Lua-internal: invalidBindingsLogged: runtime diagnostic deduplication

namespace sle::lua::components {
using namespace sle::components;
inline Json snapshot(const AnimatorComponent& c) { Json j=Json::object();
 j["clipAsset"]=c.clipAsset;
 j["enabled"]=c.enabled;
 j["playing"]=c.playing;
 j["timeSeconds"]=c.timeSeconds;
 j["speed"]=c.speed;
 j["overrideLoopMode"]=c.overrideLoopMode;
 j["loopMode"]=c.loopMode==AnimationLoopMode::Once ? "Once" : "Loop";
 j["stateClipMap"]=c.stateClipMap;
 j["targetEntities"]=c.targetEntities;
 j["floatParameters"]=c.floatParameters;
 return j; }
inline void apply(AnimatorComponent& c, const Json& p, entity::Registry& registry) {
 for(auto it=p.begin();it!=p.end();++it) { const auto& key=it.key(); const auto& v=it.value();
 if(key=="clipAsset") { validate(v,"str");
 c.clipAsset=v.get<decltype(c.clipAsset)>();
 }
 else if(key=="enabled") { validate(v,"bool");
 c.enabled=v.get<decltype(c.enabled)>();
 }
 else if(key=="playing") { validate(v,"bool");
 c.playing=v.get<decltype(c.playing)>();
 }
 else if(key=="timeSeconds") { validate(v,"nonneg");
 c.timeSeconds=number(v);
 }
 else if(key=="speed") { validate(v,"float");
 c.speed=number(v);
 }
 else if(key=="overrideLoopMode") { validate(v,"bool");
 c.overrideLoopMode=v.get<decltype(c.overrideLoopMode)>();
 }
 else if(key=="loopMode") { validate(v,"loop");
 c.loopMode=v=="Once" ? AnimationLoopMode::Once : AnimationLoopMode::Loop;
 }
 else if(key=="stateClipMap") { validate(v,"strmap");
 c.stateClipMap=v.get<decltype(c.stateClipMap)>();
 }
 else if(key=="targetEntities") { validate(v,"idmap");
 c.targetEntities=v.get<decltype(c.targetEntities)>();
 for(const auto& [name,id]:c.targetEntities) if(!registry.hasEntity(entity::Entity(id))) throw std::invalid_argument("Animator target must be alive");
 }
 else if(key=="floatParameters") { validate(v,"floatmap");
 c.floatParameters=v.get<decltype(c.floatParameters)>();
 }
 else throw std::invalid_argument("Unknown or read-only component field: "+key); }
 (void)registry; }

inline void invalidate(AnimatorComponent& next,const AnimatorComponent* old) { if(old && next.clipAsset!=old->clipAsset) { next.clip.reset(); next.clipLoadAttempted=false; next.invalidBindingsLogged.clear(); } }
} // namespace sle::lua::components
