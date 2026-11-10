#include <sle/engine/Runtime.hpp>
#include <sle/engine/ScriptApiImpl.hpp>
#include <sle/scene/components/RigidBodyComponent.hpp>
#include <sle/scene/components/Transform.hpp>
#include <sle/scripting/ScriptEngine.hpp>
#include <iostream>

int main()
{
    sle::core::EngineConfig config;
    sle::Runtime runtime(config);
    sle::ScriptApiImpl api(runtime);
    const auto parent = api.createEntity();
    const auto child = api.createEntity();
    api.setParent(child, parent);
    runtime.getScene().getRegistry().addComponent<sle::components::RigidBodyComponent>(
        sle::entity::Entity(child.id));
    runtime.getScene().getRegistry().getComponent<sle::components::RigidBodyComponent>(
        sle::entity::Entity(child.id))->gravityScale = 0.375f;
    const auto noTransform = runtime.getScene().createEntity();
    float rotation = 0;
    if (api.getTransformRotation({noTransform.getID()}, rotation) ||
        !api.getWorldTransform({noTransform.getID()}).is_null() ||
        api.setTransformScale({noTransform.getID()}, {2, 3}))
    {
        std::cerr << "Missing Transform must return nil/false\n";
        return 1;
    }
    sle::scripting::ScriptEngine engine;
    if (!engine.init(&api) ||
        !engine.executeScriptAsset("tests/data/scripts/lua_gameplay_controls.lua") ||
        !engine.callGlobalFunction("runGameplayControls", parent.id))
    {
        std::cerr << "Lua gameplay controls contract failed\n";
        engine.shutdown();
        return 1;
    }
    engine.shutdown();
    return 0;
}
