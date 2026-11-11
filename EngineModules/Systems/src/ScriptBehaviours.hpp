#pragma once
#include <sle/scene/components/ScriptComponent.hpp>
#include <sle/scripting/ScriptRuntime.hpp>

namespace sle::detail {
inline std::vector<scripting::ScriptBehaviourSpec> scriptSpecs(const components::ScriptComponent& component)
{
    std::vector<scripting::ScriptBehaviourSpec> result;
    if (!component.scriptAsset.empty()) result.push_back({"",component.scriptAsset,true,0});
    for (const auto& entry:component.behaviours)
        result.push_back({entry.key,entry.scriptAsset,entry.enabled,entry.order});
    return result;
}
inline bool behaviourEnabled(const components::ScriptComponent& component,const std::string& key)
{
    if(!component.enabled) return false;
    if(key.empty()) return !component.scriptAsset.empty();
    for(const auto& entry:component.behaviours) if(entry.key==key) return entry.enabled;
    return false;
}
} // namespace sle::detail
