#pragma once

#include <string>
#include <unordered_map>
#include <vector>

namespace sle::components {

enum class StateTransitionType
{
    Always,
    Trigger,
    BoolEquals,
    MinTimeInState,
    LuaGuard,
};

struct StateTransition
{
    std::string toState;
    StateTransitionType type = StateTransitionType::Always;
    std::string key;
    bool expectedBool = true;
    float minTimeSeconds = 0.0f;
    bool consumeTrigger = true;
    std::string luaGuardFunction; // used only when type == LuaGuard
};

struct StateDefinition
{
    std::string name;
    std::string onEnterCallback;
    std::string onExitCallback;
    std::vector<StateTransition> transitions;
};

struct StateMachineDefinition
{
    std::string initialState;
    std::unordered_map<std::string, StateDefinition> states;

    const StateDefinition* findState(const std::string& stateName) const
    {
        auto it = states.find(stateName);
        return it != states.end() ? &it->second : nullptr;
    }
};

} // namespace sle::components
