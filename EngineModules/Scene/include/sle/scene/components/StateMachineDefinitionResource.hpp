#pragma once

#include <sle/scene/components/StateMachineDefinition.hpp>

#include <nlohmann/json.hpp>

#include <fstream>
#include <memory>
#include <string>

namespace sle::components {

class StateMachineDefinitionResource
{
public:
    bool loadFromFiles(const std::string& definitionPath)
    {
        std::ifstream file(definitionPath);
        if (!file.is_open())
            return false;

        nlohmann::json json;
        try
        {
            file >> json;
        }
        catch (...)
        {
            return false;
        }

        auto parsed = std::make_shared<StateMachineDefinition>();
        parsed->initialState = json.value("initialState", "");

        if (json.contains("states") && json["states"].is_array())
        {
            for (const auto& stateJson : json["states"])
            {
                StateDefinition state;
                state.name = stateJson.value("name", "");
                state.onEnterCallback = stateJson.value("onEnter", "");
                state.onExitCallback = stateJson.value("onExit", "");

                if (state.name.empty())
                    continue;

                if (stateJson.contains("transitions") && stateJson["transitions"].is_array())
                {
                    for (const auto& transitionJson : stateJson["transitions"])
                    {
                        StateTransition transition;
                        transition.toState = transitionJson.value("to", "");

                        const std::string type = transitionJson.value("type", "always");
                        if (type == "trigger")
                            transition.type = StateTransitionType::Trigger;
                        else if (type == "bool_equals")
                            transition.type = StateTransitionType::BoolEquals;
                        else if (type == "min_time_in_state")
                            transition.type = StateTransitionType::MinTimeInState;
                        else if (type == "lua_guard")
                            transition.type = StateTransitionType::LuaGuard;
                        else
                            transition.type = StateTransitionType::Always;

                        transition.key = transitionJson.value("key", "");
                        transition.luaGuardFunction = transitionJson.value("guardFunction", "");
                        transition.expectedBool = transitionJson.value("expectedBool", true);
                        transition.minTimeSeconds = transitionJson.value("minTimeSeconds", 0.0f);
                        transition.consumeTrigger = transitionJson.value("consumeTrigger", true);

                        if (transition.type == StateTransitionType::LuaGuard && transition.luaGuardFunction.empty())
                            transition.luaGuardFunction = transition.key;

                        if (!transition.toState.empty())
                            state.transitions.push_back(std::move(transition));
                    }
                }

                parsed->states.emplace(state.name, std::move(state));
            }
        }

        if (parsed->initialState.empty() || !parsed->findState(parsed->initialState))
            return false;

        path_ = definitionPath;
        definition_ = std::move(parsed);
        return true;
    }

    const std::string& getPath() const { return path_; }
    const std::shared_ptr<StateMachineDefinition>& getDefinition() const { return definition_; }

private:
    std::string path_;
    std::shared_ptr<StateMachineDefinition> definition_;
};

} // namespace sle::components
