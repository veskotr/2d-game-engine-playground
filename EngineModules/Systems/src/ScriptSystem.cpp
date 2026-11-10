#include <sle/engine/ScriptSystem.hpp>
#include <sle/scene/ChunkActivity.hpp>
#include <sle/engine/Context.hpp>
#include <sle/core/Log.hpp>
#include <sle/events/StateMachineEvents.hpp>
#include <sle/scene/Entity.hpp>
#include <sle/scene/Registry.hpp>
#include <sle/scene/components/ScriptComponent.hpp>
#include <sle/scripting/ScriptRuntime.hpp>

namespace sle {

void ScriptSystem::ensureStateMachineSubscription(sle::events::EventBus& eventBus, sle::entity::Registry& registry)
{
    if (&eventBus == subscribedEventBus_ && stateMachineTransitionSubscription_)
        return;

    stateMachineTransitionSubscription_.reset();
    subscribedEventBus_ = &eventBus;
    stateMachineTransitionSubscription_ = sle::events::ScopedSubscription(
        &eventBus,
        eventBus.subscribe<sle::events::StateMachineTransitionEvent>(
            [this, &registry](const sle::events::StateMachineTransitionEvent& event)
            {
                if (!scriptRuntime || !sle::entity::isChunkActive(registry, event.entity, components::ChunkSubsystem::Scripts))
                    return;

                if (!event.onExitCallback.empty())
                    (void)scriptRuntime->callEntityFunctionByName(event.entity.getID(), event.onExitCallback);

                if (!event.onEnterCallback.empty())
                    (void)scriptRuntime->callEntityFunctionByName(event.entity.getID(), event.onEnterCallback);
            }));
}

void ScriptSystem::update(Context& ctx)
{
    if (!scriptRuntime)
        return;

    ensureStateMachineSubscription(ctx.eventBus, ctx.registry);

    activeScriptEntities.clear();

    ctx.registry.view<components::ScriptComponent>(
        [this, &registry = ctx.registry, dt = ctx.dt](sle::entity::Entity ent, components::ScriptComponent& script)
        {
            if (!script.enabled || script.scriptAsset.empty())
                return;

            activeScriptEntities.insert(ent.getID());
            // Keep existing Lua state during chunk suspension, without loading or updating it.
            if (!sle::entity::isChunkActive(registry, ent, components::ChunkSubsystem::Scripts))
                return;

            if (!scriptRuntime->hasScript(ent.getID()))
            {
                if (!scriptRuntime->ensureScript(ent.getID(), script.scriptAsset))
                {
                    sle::core::Log::error("Failed to initialize script {} for entity {}", script.scriptAsset, ent.getID());
                    script.enabled = false;
                    return;
                }
            }

            scriptRuntime->runUpdate(ent.getID(), dt);
        });

    scriptRuntime->syncEntities(activeScriptEntities);
}

} // namespace sle
