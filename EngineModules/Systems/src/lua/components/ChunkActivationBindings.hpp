#pragma once
#include "ValueValidation.hpp"
#include <sle/scene/components/ChunkActivationComponent.hpp>

namespace sle::lua::components {
using namespace sle::components;
inline Json snapshot(const ChunkActivationComponent& c) { Json j=Json::object();
 j["homeChunkX"]=c.homeChunkX;
 j["homeChunkY"]=c.homeChunkY;
 j["affectsActivation"]=c.affectsActivation;
 j["mode"]=c.mode==ChunkEntityActivationMode::Inherit ? "Inherit" : c.mode==ChunkEntityActivationMode::AlwaysActive ? "AlwaysActive" : c.mode==ChunkEntityActivationMode::EventsOnly ? "EventsOnly" : "AutoToggle";
 j["keepRenderingWhenInactive"]=c.keepRenderingWhenInactive;
 j["keepPhysicsWhenInactive"]=c.keepPhysicsWhenInactive;
 j["keepScriptsWhenInactive"]=c.keepScriptsWhenInactive;
 j["suspendedSubsystems"]=c.suspendedSubsystems;
 return j; }
inline void apply(ChunkActivationComponent& c, const Json& p, entity::Registry& registry) {
 for(auto it=p.begin();it!=p.end();++it) { const auto& key=it.key(); const auto& v=it.value();
 if(key=="homeChunkX") { validate(v,"int");
 c.homeChunkX=v.get<decltype(c.homeChunkX)>();
 }
 else if(key=="homeChunkY") { validate(v,"int");
 c.homeChunkY=v.get<decltype(c.homeChunkY)>();
 }
 else if(key=="affectsActivation") { validate(v,"bool");
 c.affectsActivation=v.get<decltype(c.affectsActivation)>();
 }
 else if(key=="mode") { validate(v,"mode");
 c.mode=v=="Inherit" ? ChunkEntityActivationMode::Inherit : v=="AlwaysActive" ? ChunkEntityActivationMode::AlwaysActive : v=="EventsOnly" ? ChunkEntityActivationMode::EventsOnly : ChunkEntityActivationMode::AutoToggle;
 }
 else if(key=="keepRenderingWhenInactive") { validate(v,"bool");
 c.keepRenderingWhenInactive=v.get<decltype(c.keepRenderingWhenInactive)>();
 }
 else if(key=="keepPhysicsWhenInactive") { validate(v,"bool");
 c.keepPhysicsWhenInactive=v.get<decltype(c.keepPhysicsWhenInactive)>();
 }
 else if(key=="keepScriptsWhenInactive") { validate(v,"bool");
 c.keepScriptsWhenInactive=v.get<decltype(c.keepScriptsWhenInactive)>();
 }
 else throw std::invalid_argument("Unknown or read-only component field: "+key); }
 (void)registry; }
} // namespace sle::lua::components
