#include <sle/engine/ScriptSystem.hpp>
#include <sle/scene/ChunkActivity.hpp>
#include <sle/engine/Context.hpp>
#include <sle/core/Log.hpp>
#include <sle/events/StateMachineEvents.hpp>
#include <sle/scene/Entity.hpp>
#include <sle/scene/Registry.hpp>
#include <sle/scene/components/ScriptComponent.hpp>
#include <sle/scripting/ScriptRuntime.hpp>
#include "ScriptBehaviours.hpp"
#include <algorithm>
#include <tuple>
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

    struct Actor { sle::entity::Entity entity; std::vector<scripting::ScriptBehaviourSpec> specs; };
    struct Task { sle::entity::Entity entity; scripting::ScriptBehaviourSpec spec; std::size_t index; uint64_t generation=0; bool ready=false; };
    std::vector<Actor> actors;
    std::vector<Task> tasks;
    // Snapshot component data before any Lua callback can mutate ECS storage.
    ctx.registry.view<components::ScriptComponent>([&](auto entity,const auto& script) {
        if(script.enabled) actors.push_back({entity,detail::scriptSpecs(script)});
    });
    std::sort(actors.begin(),actors.end(),[](const auto& a,const auto& b){return a.entity.getID()<b.entity.getID();});
    for(const auto& actor:actors) {
        scriptRuntime->configureBehaviours(actor.entity.getID(),actor.specs);
        auto& failures=failedAssets[actor.entity.getID()];
        for(auto it=failures.begin();it!=failures.end();) {
            const auto found=std::find_if(actor.specs.begin(),actor.specs.end(),[&](const auto& spec){return spec.key==it->first && spec.scriptAsset==it->second && spec.enabled;});
            if(found==actor.specs.end()) it=failures.erase(it); else ++it;
        }
        for(std::size_t i=0;i<actor.specs.size();++i)
            if(actor.specs[i].enabled) tasks.push_back({actor.entity,actor.specs[i],i,
                scriptRuntime->getBehaviourGeneration(actor.entity.getID(),actor.specs[i].key)});
    }
    std::stable_sort(tasks.begin(),tasks.end(),[](const auto& a,const auto& b) {
        return std::tuple(a.spec.order,a.entity.getID(),a.index)<std::tuple(b.spec.order,b.entity.getID(),b.index);
    });
    auto matches=[&](const Task& task) {
        const auto* script=ctx.registry.getComponent<components::ScriptComponent>(task.entity);
        if(!script || !script->enabled || !sle::entity::isChunkActive(ctx.registry,task.entity,components::ChunkSubsystem::Scripts)) return false;
        for(const auto& spec:detail::scriptSpecs(*script))
            if(spec.key==task.spec.key) return spec.enabled && spec.scriptAsset==task.spec.scriptAsset;
        return false;
    };
    // Initialize every due attachment before updating any of them.
    for(auto& task:tasks) {
        if(!matches(task) || failedAssets[task.entity.getID()].contains(task.spec.key)) continue;
        if(scriptRuntime->getBehaviourGeneration(task.entity.getID(),task.spec.key)!=task.generation) continue;
        if(!scriptRuntime->hasBehaviour(task.entity.getID(),task.spec.key) &&
           !scriptRuntime->ensureBehaviour(task.entity.getID(),task.spec.key,task.spec.scriptAsset)) {
            failedAssets[task.entity.getID()][task.spec.key]=task.spec.scriptAsset;
            core::Log::error("Failed to initialize behaviour '{}' ({}) for entity {}",task.spec.key,task.spec.scriptAsset,task.entity.getID());
            continue;
        }
        task.generation=scriptRuntime->getBehaviourGeneration(task.entity.getID(),task.spec.key);
        task.ready=matches(task) && scriptRuntime->hasBehaviour(task.entity.getID(),task.spec.key);
    }
    for(const auto& task:tasks)
        if(task.ready && matches(task) && scriptRuntime->getBehaviourGeneration(task.entity.getID(),task.spec.key)==task.generation)
            scriptRuntime->runBehaviourUpdate(task.entity.getID(),task.spec.key,ctx.dt);

    // Include paused entries/chunks in lifetime ownership, but never dead owners.
    activeScriptEntities.clear();
    ctx.registry.view<components::ScriptComponent>([&](auto entity,const auto& script) {
        if(script.enabled && !detail::scriptSpecs(script).empty()) activeScriptEntities.insert(entity.getID());
    });
    for(auto it=failedAssets.begin();it!=failedAssets.end();)
        if(!activeScriptEntities.contains(it->first)) it=failedAssets.erase(it); else ++it;

    scriptRuntime->syncEntities(activeScriptEntities);
}

} // namespace sle
