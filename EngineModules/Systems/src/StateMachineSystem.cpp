#include <sle/engine/StateMachineSystem.hpp>

#include <sle/core/Log.hpp>
#include <sle/events/StateMachineEvents.hpp>
#include <sle/resources/Resources.hpp>
#include <sle/scene/Scene.hpp>
#include <sle/scene/Registry.hpp>
#include <sle/scene/components/StateMachineComponent.hpp>
#include <sle/scene/components/StateMachineDefinitionResource.hpp>
#include <sle/scripting/ScriptEngine.hpp>

namespace sle {

namespace {

bool isTransitionSatisfied(
    const sle::components::StateTransition& transition,
    const sle::components::StateMachineComponent& sm,
    sle::entity::Entity entity,
    sle::scripting::ScriptEngine* scriptEngine)
{
    switch (transition.type)
    {
        case sle::components::StateTransitionType::Always:
            return true;
        case sle::components::StateTransitionType::Trigger:
            return sm.triggers.contains(transition.key);
        case sle::components::StateTransitionType::BoolEquals:
        {
            auto it = sm.boolParameters.find(transition.key);
            if (it == sm.boolParameters.end())
                return false;
            return it->second == transition.expectedBool;
        }
        case sle::components::StateTransitionType::MinTimeInState:
            return sm.stateTimeSeconds >= transition.minTimeSeconds;
        case sle::components::StateTransitionType::LuaGuard:
        {
            if (!scriptEngine || transition.luaGuardFunction.empty())
                return false;
            // Guard functions must return a boolean; false or invalid return blocks transition.
            return scriptEngine->callGlobalBoolFunction(transition.luaGuardFunction, entity.getID());
        }
    }

    return false;
}

} // namespace

void StateMachineSystem::update(sle::entity::Scene& scene, float dt, sle::scripting::ScriptEngine* scriptEngine)
{
    auto& registry = scene.getRegistry();
    auto& eventBus = scene.getEventBus();

    registry.view<sle::components::StateMachineComponent>(
        [dt, &eventBus, scriptEngine](sle::entity::Entity entity, sle::components::StateMachineComponent& sm)
        {
            if (!sm.enabled)
                return;

            if (!sm.definition && !sm.definitionAsset.empty() && !sm.definitionAssetLoadAttempted)
            {
                sm.definitionAssetLoadAttempted = true;
                auto resource = sle::core::Resources::create<sle::components::StateMachineDefinitionResource>(
                    sm.definitionAsset,
                    sm.definitionAsset);
                if (resource && resource->getDefinition())
                {
                    sm.definition = resource->getDefinition();
                }
                else
                {
                    sle::core::Log::warn(
                        "Failed to load state machine definition asset '{}' for entity {}",
                        sm.definitionAsset,
                        entity.getID());
                }
            }

            if (!sm.definition)
                return;

            if (!sm.initialized)
            {
                if (!sm.definition->initialState.empty() &&
                    sm.definition->findState(sm.definition->initialState))
                {
                    sm.currentState = sm.definition->initialState;
                    sm.previousState.clear();
                    sm.stateTimeSeconds = 0.0f;
                    sm.initialized = true;
                }
                return;
            }

            const auto* state = sm.definition->findState(sm.currentState);
            if (!state)
                return;

            sm.stateTimeSeconds += dt;

            for (const auto& transition : state->transitions)
            {
                if (!isTransitionSatisfied(transition, sm, entity, scriptEngine))
                    continue;

                if (transition.type == sle::components::StateTransitionType::Trigger && transition.consumeTrigger)
                {
                    sm.triggers.erase(transition.key);
                }

                const auto* toState = sm.definition->findState(transition.toState);
                if (transition.toState.empty() || !toState)
                    break;

                if (transition.toState == sm.currentState)
                    break;

                sm.previousState = sm.currentState;
                sm.currentState = transition.toState;
                sm.stateTimeSeconds = 0.0f;

                // Queue transition notifications to avoid re-entrant state changes
                // from handlers running during system iteration.
                eventBus.queue(sle::events::StateMachineTransitionEvent{
                    entity,
                    sm.previousState,
                    sm.currentState,
                    state->onExitCallback,
                    toState->onEnterCallback,
                });
                break;
            }
        });
}

} // namespace sle
