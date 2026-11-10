#include <sle/engine/Runtime.hpp>
#include <sle/engine/ScriptSystem.hpp>
#include <sle/engine/ChunkActivationController.hpp>
#include <sle/scene/components/ScriptComponent.hpp>
#include <sle/scene/components/Transform.hpp>
#include <sle/events/ScriptEvents.hpp>
#include <iostream>

int main()
{
    sle::Runtime runtime(sle::core::EngineConfig{});
    sle::ScriptApiImpl api(runtime);
    auto& engine = runtime.getScriptEngine();
    if (!engine.init(&api)) return 1;
    struct Shutdown { sle::scripting::ScriptEngine& engine; ~Shutdown() { engine.shutdown(); } } shutdown{engine};
    auto& scene = runtime.getScene();
    auto& registry = scene.getRegistry();
    sle::entity::Entity owner;
    runtime.registerScene("owned", [&](sle::Runtime& rt) {
        owner = rt.getScene().createEntity();
        registry.addComponent<sle::components::TransformComponent>(owner);
        registry.addComponent<sle::components::ScriptComponent>(owner).scriptAsset = "tests/data/scripts/event_owned.lua";
        registry.addComponent<sle::components::ChunkActivationComponent>(owner);
    });
    if (!runtime.loadScene("owned").ok()) return 1;
    sle::renderer::Renderer renderer;
    sle::core::Camera2D camera(320.0f, 180.0f);
    sle::Context ctx{scene, registry, scene.getEventBus(), runtime.getGlobalBus(), renderer, camera, nullptr, 0.01f};
    sle::ScriptSystem scripts;
    scripts.setScriptRuntime(&engine);
    scripts.update(ctx);
    auto tick = [&]() { scene.getEventBus().emit(sle::events::ScriptEvent{"owned.tick", {}, {}}); };
    auto x = [&]() { return registry.getComponent<sle::components::TransformComponent>(owner)->getPosition().x; };
    bool passed = true;
    auto check = [&](bool value, const char* message) {
        if (!value) { std::cerr << message << "\n"; passed = false; }
    };
    tick();
    check(x() == 10.0f, "Unsubscribing earlier handles must not invalidate later subscription handles");
    sle::ChunkOwnershipIndex index;
    index.rebuild(registry);
    sle::ChunkActivationController chunks({.mode = sle::core::ChunkActivationMode::AutoToggle});
    chunks.applyActiveChunks({}, index, registry);
    const float before = x();
    tick();
    check(x() == before, "Entity-owned Lua events must pause with the script's chunk");
    chunks.applyActiveChunks({{0, 0}}, index, registry);
    tick();
    check(x() == before + 10.0f, "Entity-owned events must resume without duplicate listeners");
    auto* script = registry.getComponent<sle::components::ScriptComponent>(owner);
    script->enabled = false;
    const float disabled = x();
    tick();
    check(x() == disabled, "Gameplay-disabled scripts must not receive entity-owned events");
    script->enabled = true;
    engine.callEntityFunctionByName(owner.getID(), "unsubscribe_all");
    const float unsubscribed = x();
    tick();
    check(x() == unsubscribed, "Unsubscribe must stop delivery");
    check(engine.callGlobalBoolFunction("event_callbacks_collected"), "Unsubscribe must release Lua callback references");

    engine.callEntityFunctionByName(owner.getID(), "event_fallback_subscribe");
    engine.removeScript(owner.getID());
    check(engine.callGlobalBoolFunction("event_fallback_collected"), "Global-format entity callbacks must retain script ownership");
    scripts.update(ctx);
    bool intercepted = false;
    const auto interceptor = scene.getEventBus().subscribe<sle::events::ScriptEvent>(
        [&](const auto& event) {
            if (event.name == "owned.tick" && !intercepted) {
                intercepted = true;
                engine.removeScript(owner.getID());
                scripts.update(ctx);
            }
        }, nullptr, 100);
    const float snapshot = x();
    tick();
    check(x() == snapshot, "Snapshot callbacks must not invoke released/reused Lua registry references");
    scene.getEventBus().unsubscribe(interceptor);
    tick();
    check(x() == snapshot + 10.0f, "Replacement subscriptions must receive the next event exactly once");
    registry.removeComponent<sle::components::ScriptComponent>(owner);
    scripts.update(ctx);
    check(engine.callGlobalBoolFunction("event_callbacks_collected"), "Removing a script must release its callbacks");
    registry.addComponent<sle::components::ScriptComponent>(owner).scriptAsset = "tests/data/scripts/event_owned.lua";
    scripts.update(ctx);
    scene.destroyEntity(owner);
    scripts.update(ctx);
    check(engine.callGlobalBoolFunction("event_callbacks_collected"), "Direct entity destruction must release callbacks at lifecycle synchronization");

    if (!runtime.loadScene("owned").ok()) return 1;
    scripts.update(ctx);
    runtime.registerSceneFromFile("bad", "tests/data/scenes/test_malformed_scene.json");
    check(!runtime.loadScene("bad").ok(), "Malformed replacement must fail");
    tick();
    check(x() == 10.0f, "Rejected replacement must preserve active script subscriptions");
    if (!engine.executeScriptAsset("tests/data/scripts/event_global.lua")) return 1;
    api.destroyEntity({0}); // invalid destroy must not clear scene-global listeners
    tick();
    check(engine.callGlobalBoolFunction("global_event_received"), "Top-level global scripts can subscribe within the scene lifetime");
    runtime.registerScene("empty", [](sle::Runtime&) {});
    check(runtime.loadScene("empty").ok(), "Successful scene switch must work");
    check(engine.callGlobalBoolFunction("event_callbacks_collected"), "Scene replacement must release entity-owned callbacks immediately");
    check(engine.callGlobalBoolFunction("global_event_collected"), "Scene replacement must release scene-global callbacks");
    const auto uiOwner = scene.createEntity();
    registry.addComponent<sle::components::TransformComponent>(uiOwner);
    registry.addComponent<sle::components::ScriptComponent>(uiOwner); // intentionally empty
    check(engine.executeScriptAssetForEntity("tests/data/scripts/event_ui_owned.lua", uiOwner.getID()), "Owned UI behavior must load");
    scene.getEventBus().emit(sle::events::ScriptEvent{"ui.owned", uiOwner, {}});
    check(registry.getComponent<sle::components::TransformComponent>(uiOwner)->getPosition().y == 1.0f,
        "UI-owned callbacks must work independently of an empty ScriptComponent");
    check(!engine.executeScriptAssetForEntity("tests/data/scripts/event_ui_failed.lua", uiOwner.getID()), "Failed UI initialization must report failure");
    check(engine.callGlobalBoolFunction("ui_failed_collected"), "Failed UI asset loading must roll back only its newly registered callbacks");
    scene.getEventBus().emit(sle::events::ScriptEvent{"ui.owned", uiOwner, {}});
    check(registry.getComponent<sle::components::TransformComponent>(uiOwner)->getPosition().y == 2.0f,
        "Rolling back failed initialization must preserve prior successful UI subscriptions");
    scene.destroyEntity(uiOwner);
    scripts.update(ctx);
    check(engine.callGlobalBoolFunction("ui_event_collected"), "UI-only owner destruction must release callbacks during synchronization");
    const auto failed = scene.createEntity();
    registry.addComponent<sle::components::ScriptComponent>(failed).scriptAsset = "tests/data/scripts/event_failed_init.lua";
    scripts.update(ctx);
    check(engine.callGlobalBoolFunction("failed_event_collected"), "Failed top-level initialization must release subscribed Lua callbacks");

    const auto spawning = scene.createEntity();
    registry.addComponent<sle::components::ScriptComponent>(spawning).scriptAsset = "tests/data/scripts/event_destroy_spawn.lua";
    scripts.update(ctx);
    runtime.registerSceneFromFile("replacement", "tests/data/scenes/test_replacement_scene.json");
    check(runtime.loadScene("replacement").ok(), "Replacement with destroy callbacks must load");
    check(!engine.callGlobalBoolFunction("saved_destroy_entity_alive"), "Destroy callbacks must not allocate IDs that alias staged replacement entities");
    scene.createEntity();
    check(!engine.callGlobalBoolFunction("saved_destroy_entity_alive"), "Replacement allocator must not reuse IDs issued during old-scene teardown");
    return passed ? 0 : 1;
}
