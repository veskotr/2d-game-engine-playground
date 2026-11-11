#pragma once

#include <string>
#include <vector>

namespace sle::components {

struct ScriptBehaviour
{
    std::string key;
    std::string scriptAsset;
    bool enabled = true;
    int order = 0;
};

struct ScriptComponent
{
    // Legacy primary behaviour; named behaviours are independent attachments.
    std::string scriptAsset;
    bool enabled = true;
    std::vector<ScriptBehaviour> behaviours;
};
} // namespace sle::components
