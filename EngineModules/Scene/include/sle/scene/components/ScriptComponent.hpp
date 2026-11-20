#pragma once

#include <map>
#include <string>
#include <variant>
#include <vector>

namespace sle::components {

using BehaviourParameters = std::map<std::string, std::variant<bool, double, std::string>>;

struct ScriptBehaviour
{
    std::string key;
    std::string scriptAsset;
    bool enabled = true;
    int order = 0;
    BehaviourParameters parameters;
};

struct ScriptComponent
{
    // Legacy primary behaviour; named behaviours are independent attachments.
    std::string scriptAsset;
    bool enabled = true;
    std::vector<ScriptBehaviour> behaviours;
};
} // namespace sle::components
