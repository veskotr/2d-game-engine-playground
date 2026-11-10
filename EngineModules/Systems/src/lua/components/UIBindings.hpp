#pragma once
#include "ValueValidation.hpp"
#include <sle/scene/components/UIComponent.hpp>

namespace sle::lua::components {
using namespace sle::components;
inline Json snapshot(const UIComponent& c) { Json j=Json::object();
 j["layoutAsset"]=c.layoutAsset;
 j["fontAsset"]=c.fontAsset;
 j["behaviorAsset"]=c.behaviorAsset;
 j["spaceMode"]=c.spaceMode==UISpaceMode::Screen ? "Screen" : "World";
 j["visible"]=c.visible;
 j["layer"]=c.layer;
 j["bindingScope"]=c.bindingScope;
 return j; }
inline void apply(UIComponent& c, const Json& p, entity::Registry& registry) {
 for(auto it=p.begin();it!=p.end();++it) { const auto& key=it.key(); const auto& v=it.value();
 if(key=="layoutAsset") { validate(v,"str");
 c.layoutAsset=v.get<decltype(c.layoutAsset)>();
 }
 else if(key=="fontAsset") { validate(v,"str");
 c.fontAsset=v.get<decltype(c.fontAsset)>();
 }
 else if(key=="behaviorAsset") { validate(v,"str");
 c.behaviorAsset=v.get<decltype(c.behaviorAsset)>();
 }
 else if(key=="spaceMode") { validate(v,"space");
 c.spaceMode=v=="Screen" ? UISpaceMode::Screen : UISpaceMode::World;
 }
 else if(key=="visible") { validate(v,"bool");
 c.visible=v.get<decltype(c.visible)>();
 }
 else if(key=="layer") { validate(v,"u32");
 c.layer=v.get<decltype(c.layer)>();
 }
 else if(key=="bindingScope") { validate(v,"str");
 c.bindingScope=v.get<decltype(c.bindingScope)>();
 }
 else throw std::invalid_argument("Unknown or read-only component field: "+key); }
 (void)registry; }
} // namespace sle::lua::components
