#include <sle/engine/ScriptSystem.hpp>
#include <sle/engine/Context.hpp>
#include <sle/core/Log.hpp>
#include <sle/scene/Entity.hpp>
#include <sle/scene/Registry.hpp>
#include <sle/scene/components/ScriptComponent.hpp>
#include <sle/scripting/ScriptEngine.hpp>

namespace sle {

void ScriptSystem::update(Context& ctx)
{
    if (!scriptEngine)
        return;

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
