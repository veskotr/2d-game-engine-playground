#pragma once

#include <sle/scene/components/StateMachineDefinition.hpp>

#include <memory>
#include <string>
#include <unordered_map>
#include <unordered_set>

namespace sle::components {

struct StateMachineComponent
{
    std::string definitionAsset;
    bool definitionAssetLoadAttempted = false;

    std::shared_ptr<StateMachineDefinition> definition;
    bool enabled = true;

    std::string currentState;
    std::string previousState;
    float stateTimeSeconds = 0.0f;
    bool initialized = false;

    std::unordered_map<std::string, bool> boolParameters;
    std::unordered_set<std::string> triggers;
};

} // namespace sle::components
