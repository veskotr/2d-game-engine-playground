#include <sle/engine/ScriptSystem.hpp>
#include <sle/scene/ChunkActivity.hpp>
#include <sle/engine/Context.hpp>
#include <sle/core/Log.hpp>
#include <sle/events/StateMachineEvents.hpp>
#include <sle/scene/Entity.hpp>
#include <sle/scene/Registry.hpp>
#include <sle/scene/components/ScriptComponent.hpp>
#include <sle/scripting/ScriptRuntime.hpp>
#include <string>
#include <vector>

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
    // Lua callbacks can add/remove components or destroy entities. Never retain a
    // pool iterator or a component reference while invoking user code.
    std::vector<sle::entity::Entity> entities;
    ctx.registry.view<components::ScriptComponent>(
        [&](sle::entity::Entity ent, const components::ScriptComponent&) { entities.push_back(ent); });

    for (const auto ent : entities)
    {
        auto* script = ctx.registry.getComponent<components::ScriptComponent>(ent);
        if (!script || !script->enabled || script->scriptAsset.empty())
            continue;

        activeScriptEntities.insert(ent.getID());
        // Preserve Lua state without loading/updating scripts in suspended chunks.
        if (!sle::entity::isChunkActive(ctx.registry, ent, components::ChunkSubsystem::Scripts))
            continue;

        const std::string asset = script->scriptAsset;
        if (!scriptRuntime->hasScript(ent.getID()))
        {
            const bool initialized = scriptRuntime->ensureScript(ent.getID(), asset);
            script = ctx.registry.getComponent<components::ScriptComponent>(ent);
            if (!initialized)
            {
                sle::core::Log::error("Failed to initialize script {} for entity {}", asset, ent.getID());
                if (script && script->scriptAsset == asset) script->enabled = false;
                continue;
            }
        }
        script = ctx.registry.getComponent<components::ScriptComponent>(ent);
        if (script && script->enabled && script->scriptAsset == asset &&
            sle::entity::isChunkActive(ctx.registry, ent, components::ChunkSubsystem::Scripts))
            scriptRuntime->runUpdate(ent.getID(), ctx.dt);
    }

    // An update can remove another entity processed earlier in this frame.
    for (auto it = activeScriptEntities.begin(); it != activeScriptEntities.end();)
    {
        const auto* script = ctx.registry.getComponent<components::ScriptComponent>(sle::entity::Entity(*it));
        if (!script || !script->enabled || script->scriptAsset.empty())
            it = activeScriptEntities.erase(it);
        else
            ++it;
    }

    scriptRuntime->syncEntities(activeScriptEntities);
}

} // namespace sle
