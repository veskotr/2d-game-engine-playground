#include <sle/engine/Runtime.hpp>
#include <sle/engine/ScriptSystem.hpp>
#include <sle/scripting/ScriptEngine.hpp>
#include <iostream>
#include <sle/scene/components/AnimatorComponent.hpp>
#include <sle/scene/components/ScriptComponent.hpp>

int main()
{
    sle::Runtime runtime(sle::core::EngineConfig{});
    sle::ScriptApiImpl api(runtime);
    auto& engine = runtime.getScriptEngine();
    if (!engine.init(&api)) return 1;
    bool passed = engine.executeScriptAsset("tests/data/scripts/lua_components.lua") &&
                        engine.callGlobalBoolFunction("components_passed");
    auto& registry = runtime.getScene().getRegistry();
    const auto entity = runtime.getScene().createEntity();
    auto& animator = registry.addComponent<sle::components::AnimatorComponent>(entity);
    animator.clipAsset = "old.clip";
    animator.clipLoadAttempted = true;
    animator.invalidBindingsLogged.insert("existing");
    std::string error;
    passed = api.setComponent({entity.getID()}, "Animator", {{"speed", 2.0f}}, error) && passed;
    passed = animator.clipLoadAttempted && animator.invalidBindingsLogged.contains("existing") && passed;
    passed = api.setComponent({entity.getID()}, "Animator", {{"clipAsset", "new.clip"}}, error) && passed;
    passed = !animator.clipLoadAttempted && animator.invalidBindingsLogged.empty() && passed;
    auto* script = &registry.addComponent<sle::components::ScriptComponent>(entity);
    passed = api.setComponent({entity.getID()}, "Script", {{"enabled", false}}, error) && passed;
    passed = script == registry.getComponent<sle::components::ScriptComponent>(entity) && !script->enabled && passed;
    const auto self=api.createEntity();
    registry.addComponent<sle::components::ScriptComponent>(sle::entity::Entity(self.id)).scriptAsset="tests/data/scripts/lua_self_remove.lua";
    sle::renderer::Renderer renderer;
    sle::core::Camera2D camera(320,180);
    sle::Context ctx{runtime.getScene(),registry,runtime.getScene().getEventBus(),runtime.getGlobalBus(),renderer,camera,nullptr,0.01f};
    sle::ScriptSystem scripts;
    scripts.setScriptRuntime(&engine);
    scripts.update(ctx);
    if (engine.hasScript(self.id)) { std::cerr << "Self-removal during init must not leave a script instance\n"; passed=false; }
    engine.shutdown();
    if (!passed) std::cerr << "Lua component creation, atomic validation, queries and removal failed\n";
    return passed ? 0 : 1;
}
