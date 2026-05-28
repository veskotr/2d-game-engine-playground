#include <sle/engine/ScriptSystem.hpp>
#include <sle/engine/Context.hpp>
#include <sle/core/Log.hpp>
#include <sle/events/StateMachineEvents.hpp>
#include <sle/scene/Entity.hpp>
#include <sle/scene/Registry.hpp>
#include <sle/scene/components/ScriptComponent.hpp>
#include <sle/scripting/ScriptEngine.hpp>

namespace sle {

void ScriptSystem::ensureStateMachineSubscription(sle::events::EventBus& eventBus)
{
    if (&eventBus == subscribedEventBus_ && stateMachineTransitionSubscription_)
        return;

    stateMachineTransitionSubscription_.reset();
    subscribedEventBus_ = &eventBus;
    stateMachineTransitionSubscription_ = sle::events::ScopedSubscription(
        &eventBus,
        eventBus.subscribe<sle::events::StateMachineTransitionEvent>(
            [this](const sle::events::StateMachineTransitionEvent& event)
            {
                if (!scriptEngine)
                    return;

                if (!event.onExitCallback.empty())
                    (void)scriptEngine->callEntityFunctionByName(event.entity.getID(), event.onExitCallback);

                if (!event.onEnterCallback.empty())
                    (void)scriptEngine->callEntityFunctionByName(event.entity.getID(), event.onEnterCallback);
            }));
}

void ScriptSystem::update(Context& ctx)
{
    if (!scriptEngine)
        return;

    ensureStateMachineSubscription(ctx.eventBus);

    activeScriptEntities.clear();

    ctx.registry.view<components::ScriptComponent>(
        [this, dt = ctx.dt](sle::entity::Entity ent, components::ScriptComponent& script)
        {
            if (!script.enabled || script.scriptAsset.empty())
                return;

            activeScriptEntities.insert(ent.getID());

            if (!scriptEngine->hasScript(ent.getID()))
            {
                if (!scriptEngine->ensureScript(ent.getID(), script.scriptAsset))
                {
                    sle::core::Log::error("Failed to initialize script {} for entity {}", script.scriptAsset, ent.getID());
                    script.enabled = false;
                    return;
                }
            }

            scriptEngine->runUpdate(ent.getID(), dt);
        });

    scriptEngine->syncEntities(activeScriptEntities);
}

} // namespace sle
