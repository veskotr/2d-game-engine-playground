#pragma once
#include "ComponentRegistry.hpp"
#include "lua/components/ValueValidation.hpp"
#include <optional>

namespace sle::detail {
struct SceneComponentPatch
{
    componentdata::Json values;
    std::optional<std::string> texture;
    std::optional<float> legacyMass;
};

// Compatibility syntax only. Accepted component fields/defaults/ranges belong to
// the shared per-component codecs, not to this JSON-to-canonical adapter.
inline SceneComponentPatch adaptSceneComponent(const std::string& name, const componentdata::Json& source)
{
    using Json = componentdata::Json;
    if (!source.is_object()) throw std::invalid_argument("Component patch must be an object");
    SceneComponentPatch result{source,{},{}};
    auto& patch=result.values;
    auto alias=[&](const char* oldName, const char* canonical) {
        if (!patch.contains(oldName)) return;
        if (patch.contains(canonical)) throw std::invalid_argument(std::string(oldName)+" conflicts with "+canonical);
        patch[canonical]=patch[oldName];
        patch.erase(oldName);
    };
    if (name=="Script") {
        alias("asset","scriptAsset");
        if (patch.contains("behaviours") && patch["behaviours"].is_array())
            for (auto& entry : patch["behaviours"])
                if (entry.is_object() && entry.contains("asset")) {
                    if(entry.contains("scriptAsset")) throw std::invalid_argument("Behaviour asset conflicts with scriptAsset");
                    entry["scriptAsset"]=entry["asset"]; entry.erase("asset");
                }
    }
    else if (name=="Audio") alias("asset","assetPath");
    else if (name=="UI") { alias("layout","layoutAsset"); alias("font","fontAsset"); alias("behavior","behaviorAsset"); }
    else if (name=="Animator") alias("clip","clipAsset");
    else if (name=="StateMachine") alias("definition","definitionAsset");

    auto vector=[&](Json& value, int dimensions, const std::string& field) {
        if (!value.is_array()) return;
        if (value.size()!=static_cast<std::size_t>(dimensions))
            throw std::invalid_argument(field+": expected exactly "+std::to_string(dimensions)+" vector values");
        const char* keys[]={"x","y","z","w"};
        Json canonical=Json::object();
        for (int i=0;i<dimensions;++i) canonical[keys[i]]=value[i];
        value=std::move(canonical);
    };
    for (const auto* field : {"position","scale","velocity","size","offset"})
        if (patch.contains(field)) vector(patch[field],2,field);
    if (patch.contains("color")) vector(patch["color"],4,"color");
    if (patch.contains("region") && patch["region"].is_object() && patch["region"].contains("uv"))
        vector(patch["region"]["uv"],4,"region.uv");
    if (name=="SpriteRenderer" && patch.contains("texture")) {
        lua::components::validate(patch["texture"],"str");
        result.texture=componentdata::resolveAssetPath(patch["texture"].get<std::string>());
        patch.erase("texture");
    }
    if (name=="RigidBody" && patch.contains("mass")) {
        // Historical serialized metadata; Box2D still derives mass from fixtures.
        lua::components::validate(patch["mass"],"positive");
        result.legacyMass=lua::components::number(patch["mass"]);
        patch.erase("mass");
    }
    patch=componentdata::normalizePatch(name,patch);
    return result;
}
} // namespace sle::detail
