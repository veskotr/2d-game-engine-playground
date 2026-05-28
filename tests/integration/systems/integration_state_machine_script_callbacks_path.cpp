#include <sle/engine/Context.hpp>
#include <sle/engine/ScriptSystem.hpp>
#include <sle/engine/StateMachineSystem.hpp>
#include <sle/events/EventBus.hpp>
#include <sle/renderer/Camera2D.hpp>
#include <sle/renderer/Renderer.hpp>
#include <sle/scene/Scene.hpp>
#include <sle/scene/components/ScriptComponent.hpp>
#include <sle/scene/components/StateMachineComponent.hpp>
#include <sle/scene/components/StateMachineDefinition.hpp>
#include <sle/scripting/ScriptApi.hpp>
#include <sle/scripting/ScriptEngine.hpp>

#include <glm/vec2.hpp>

#include <iostream>
#include <string>
#include <vector>

namespace {

class DummyScriptApi final : public sle::scripting::ScriptApi {
public:
    float getDeltaTime() const override { return 0.016f; }
    glm::vec2 getWindowSize() const override { return {320.0f, 180.0f}; }

    sle::scripting::ScriptEntityRef createEntity() override { return {}; }
    bool isEntityAlive(sle::scripting::ScriptEntityRef) const override { return false; }
    void destroyEntity(sle::scripting::ScriptEntityRef) override {}
    uint32_t getChildCount(sle::scripting::ScriptEntityRef) const override { return 0; }
    uint32_t destroyChildren(sle::scripting::ScriptEntityRef) override { return 0; }
    bool setParent(sle::scripting::ScriptEntityRef, sle::scripting::ScriptEntityRef) override { return false; }
    sle::scripting::ScriptEntityRef getParent(sle::scripting::ScriptEntityRef) const override { return {}; }

    bool getTransformPosition(sle::scripting::ScriptEntityRef, glm::vec2&) const override { return false; }
    bool setTransformPosition(sle::scripting::ScriptEntityRef, const glm::vec2&) override { return false; }
    bool getTransformScale(sle::scripting::ScriptEntityRef, glm::vec2&) const override { return false; }

    bool isKeyDown(int) const override { return false; }
    bool isKeyPressed(int) const override { return false; }
    bool isKeyReleased(int) const override { return false; }
    glm::dvec2 getMousePosition() const override { return {0.0, 0.0}; }

    glm::vec2 getCameraPosition() const override { return {0.0f, 0.0f}; }
    void setCameraPosition(const glm::vec2&) override {}
    void moveCamera(const glm::vec2&) override {}
    float getCameraZoom() const override { return 1.0f; }
    void setCameraZoom(float) override {}

    uint32_t loadTexture(const std::string&) override { return 0; }
    bool setSpriteTexture(sle::scripting::ScriptEntityRef, const std::string&) override { return false; }

    bool hasScene(const std::string&) const override { return false; }
    bool switchScene(const std::string&) override { return false; }
    std::string getCurrentSceneName() const override { return {}; }

    bool setStateMachineBool(sle::scripting::ScriptEntityRef, const std::string&, bool) override { return false; }
    bool setStateMachineTrigger(sle::scripting::ScriptEntityRef, const std::string&) override { return false; }
    bool getStateMachineCurrentState(sle::scripting::ScriptEntityRef, std::string&) const override { return false; }
    bool forceStateMachineState(sle::scripting::ScriptEntityRef, const std::string&) override { return false; }
    bool isStateMachineInState(sle::scripting::ScriptEntityRef, const std::string&) const override { return false; }
    bool sendStateMachineEvent(sle::scripting::ScriptEntityRef, const std::string&) override { return false; }

    void log(const std::string& message) override { logs.push_back(message); }
    void warn(const std::string&) override {}
    void error(const std::string&) override {}

    bool addForce(sle::scripting::ScriptEntityRef, float, float) override { return false; }
    bool addImpulse(sle::scripting::ScriptEntityRef, float, float) override { return false; }
    bool setVelocity(sle::scripting::ScriptEntityRef, float, float) override { return false; }
    bool getVelocity(sle::scripting::ScriptEntityRef, glm::vec2&) const override { return false; }
    bool setAngularVelocity(sle::scripting::ScriptEntityRef, float) override { return false; }
    float getAngularVelocity(sle::scripting::ScriptEntityRef) const override { return 0.0f; }
    bool setGravityScale(sle::scripting::ScriptEntityRef, float) override { return false; }
    bool isTouching(sle::scripting::ScriptEntityRef) const override { return false; }
    bool raycastFirst(const glm::vec2&, const glm::vec2&, sle::scripting::PhysicsRaycastHit&) const override { return false; }
    uint32_t raycastAll(const glm::vec2&, const glm::vec2&, std::vector<sle::scripting::PhysicsRaycastHit>&) const override { return 0; }
    void setPhysicsDebugEnabled(bool) override {}
    bool isPhysicsDebugEnabled() const override { return false; }

    int subscribeEvent(const std::string&, uint32_t, int) override { return 0; }
    void unsubscribeEvent(int) override {}

    std::vector<std::string> logs;
};

} // namespace

int main() {
    sle::entity::Scene scene;
    auto& registry = scene.getRegistry();

    auto definition = std::make_shared<sle::components::StateMachineDefinition>();
    definition->initialState = "Idle";

    sle::components::StateDefinition idle;
    idle.name = "Idle";
    idle.onExitCallback = "on_exit_idle";
    idle.transitions.push_back({
        .toState = "Run",
        .type = sle::components::StateTransitionType::Trigger,
        .key = "start",
        .expectedBool = true,
        .minTimeSeconds = 0.0f,
        .consumeTrigger = true,
    });

    sle::components::StateDefinition run;
    run.name = "Run";
    run.onEnterCallback = "on_enter_run";

    definition->states.emplace(idle.name, idle);
    definition->states.emplace(run.name, run);

    const auto entity = scene.createEntity();

    auto& script = registry.addComponent<sle::components::ScriptComponent>(entity);
    script.scriptAsset = "tests/data/scripts/state_machine_callbacks.lua";

    auto& sm = registry.addComponent<sle::components::StateMachineComponent>(entity);
    sm.definition = definition;

    DummyScriptApi api;
    sle::scripting::ScriptEngine scriptEngine;
    if (!scriptEngine.init(&api)) {
        std::cerr << "Script engine init failed\n";
        return 1;
    }

    sle::renderer::Renderer renderer;
    sle::core::Camera2D camera(320.0f, 180.0f);
    sle::events::EventBus globalBus;

    sle::Context ctx{
        scene,
        registry,
        scene.getEventBus(),
        globalBus,
        renderer,
        camera,
        nullptr,
        1.0f / 60.0f,
    };

    sle::ScriptSystem scriptSystem;
    scriptSystem.setScriptEngine(&scriptEngine);

    sle::StateMachineSystem stateMachineSystem;

    scriptSystem.update(ctx);
    if (api.logs.empty()) {
        std::cerr << "Expected script init log after script load\n";
        scriptEngine.shutdown();
        return 1;
    }

    stateMachineSystem.update(scene, ctx.dt);
    sm.triggers.insert("start");
    stateMachineSystem.update(scene, ctx.dt);

    const std::size_t logsBeforeFlush = api.logs.size();
    scriptSystem.update(ctx);

    if (api.logs.size() != logsBeforeFlush) {
        std::cerr << "State machine callbacks should not run before deferred event flush\n";
        scriptEngine.shutdown();
        return 1;
    }

    scene.getEventBus().flushQueue();

    bool sawExit = false;
    bool sawEnter = false;
    for (const auto& line : api.logs) {
        if (line.find("exit_idle:") == 0) {
            sawExit = true;
        }
        if (line.find("enter_run:") == 0) {
            sawEnter = true;
        }
    }

    if (!sawExit || !sawEnter) {
        std::cerr << "Expected both on_exit_idle and on_enter_run callbacks to execute after flush\n";
        scriptEngine.shutdown();
        return 1;
    }

    scriptEngine.shutdown();
    return 0;
}
