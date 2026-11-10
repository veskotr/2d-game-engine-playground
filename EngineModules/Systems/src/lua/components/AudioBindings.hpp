#pragma once
#include "ValueValidation.hpp"
#include <sle/scene/components/AudioComponent.hpp>

// Lua-internal: runtimeHandle: native audio ownership

namespace sle::lua::components {
using namespace sle::components;
inline Json snapshot(const AudioComponent& c) { Json j=Json::object();
 j["assetPath"]=c.assetPath;
 j["loop"]=c.loop;
 j["volume"]=c.volume;
 j["pitch"]=c.pitch;
 j["playRequested"]=c.playRequested;
 j["stopRequested"]=c.stopRequested;
 j["state"]=c.state==AudioPlaybackState::Playing?"Playing":c.state==AudioPlaybackState::Paused?"Paused":"Stopped";
 return j; }
inline void apply(AudioComponent& c, const Json& p, entity::Registry& registry) {
 for(auto it=p.begin();it!=p.end();++it) { const auto& key=it.key(); const auto& v=it.value();
 if(key=="assetPath") { validate(v,"str");
 c.assetPath=v.get<decltype(c.assetPath)>();
 }
 else if(key=="loop") { validate(v,"bool");
 c.loop=v.get<decltype(c.loop)>();
 }
 else if(key=="volume") { validate(v,"unit");
 c.volume=number(v);
 }
 else if(key=="pitch") { validate(v,"positive");
 c.pitch=number(v);
 }
 else if(key=="playRequested") { validate(v,"bool");
 c.playRequested=v.get<decltype(c.playRequested)>();
 }
 else if(key=="stopRequested") { validate(v,"bool");
 c.stopRequested=v.get<decltype(c.stopRequested)>();
 }
 else throw std::invalid_argument("Unknown or read-only component field: "+key); }
 (void)registry; }
} // namespace sle::lua::components
