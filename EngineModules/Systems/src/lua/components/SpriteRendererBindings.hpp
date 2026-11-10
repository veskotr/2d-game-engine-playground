#pragma once
#include "ValueValidation.hpp"
#include <sle/scene/components/SpriteRenderer.hpp>

namespace sle::lua::components {
using namespace sle::components;
inline Json snapshot(const SpriteRenderer& c) { Json j=Json::object();
 j["region"]={{"uv",vec(c.region.uv)}};
 j["color"]=vec(c.color);
 j["visible"]=c.visible;
 j["layer"]=c.layer;
 return j; }
inline void apply(SpriteRenderer& c, const Json& p, entity::Registry& registry) {
 for(auto it=p.begin();it!=p.end();++it) { const auto& key=it.key(); const auto& v=it.value();
 if(key=="color") { validate(v,"v4");
 c.color=glm::vec4(number(v.at("x")),number(v.at("y")),number(v.at("z")),number(v.at("w")));
 }
 else if(key=="visible") { validate(v,"bool");
 c.visible=v.get<decltype(c.visible)>();
 }
 else if(key=="layer") { validate(v,"int");
 c.layer=v.get<decltype(c.layer)>();
 }
 else if(key=="region") { if(!v.is_object() || v.size()!=1 || !v.contains("uv")) throw std::invalid_argument("Region accepts only uv"); validate(v.at("uv"),"v4"); const auto& uv=v.at("uv"); c.region.uv={number(uv.at("x")),number(uv.at("y")),number(uv.at("z")),number(uv.at("w"))}; }
 else throw std::invalid_argument("Unknown or read-only component field: "+key); }
 (void)registry; }
} // namespace sle::lua::components
