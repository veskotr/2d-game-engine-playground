#pragma once
#include "ComponentRegistry.hpp"
#include <algorithm>
#include <cctype>

namespace sle::componentdata {
inline void normalizeAssetFields(const std::string& component, Json& patch)
{
    auto field=[&](const char* name, bool trim=false) {
        if (!patch.contains(name) || !patch[name].is_string()) return;
        auto path=patch[name].get<std::string>();
        if (trim) {
            path.erase(path.begin(),std::find_if(path.begin(),path.end(),[](unsigned char c){return !std::isspace(c);}));
            path.erase(std::find_if(path.rbegin(),path.rend(),[](unsigned char c){return !std::isspace(c);}).base(),path.end());
        }
        patch[name]=resolveAssetPath(path);
    };
    if (component=="Script") {
        field("scriptAsset");
        if (patch.contains("behaviours") && patch["behaviours"].is_array())
            for (auto& entry : patch["behaviours"])
                if (entry.is_object() && entry.contains("scriptAsset") && entry["scriptAsset"].is_string())
                    entry["scriptAsset"]=resolveAssetPath(entry["scriptAsset"].get<std::string>());
    }
    else if (component=="Audio") field("assetPath");
    else if (component=="UI") { field("layoutAsset",true); field("fontAsset",true); field("behaviorAsset",true); }
    else if (component=="StateMachine") field("definitionAsset");
    else if (component=="Animator") {
        field("clipAsset");
        if (patch.contains("stateClipMap") && patch["stateClipMap"].is_object())
            for (auto& value : patch["stateClipMap"])
                if (value.is_string()) value=resolveAssetPath(value.get<std::string>());
    }
}
} // namespace sle::componentdata
