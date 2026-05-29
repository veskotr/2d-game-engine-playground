#include <sle/engine/StateMachineSystem.hpp>
#include <sle/scene/Scene.hpp>
#include <sle/scene/components/StateMachineComponent.hpp>
#include <sle/scene/components/StateMachineDefinition.hpp>
#include <sle/scripting/ScriptApi.hpp>
#include <sle/scripting/ScriptEngine.hpp>

#include <glm/vec2.hpp>

#include <iostream>
#include <memory>
#include <string>
#include <vector>

namespace {

class DummyScriptApi final : public sle::scripting::ScriptApi {
public:
    float getDeltaTime() const override { return 0.016f; }
    glm::vec2 getWindowSize() const override { return {320.0f, 180.0f}; }

    sle::scripting::ScriptEntityRef createEntity() override { return {}; }
    bool isEntityAlive(sle::scripting::ScriptEntityRef) const override { return true; }
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

    void log(const std::string&) override {}
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
    bool emitEvent(const std::string&, uint32_t, const std::string&) override { return false; }
    bool playAnimation(sle::scripting::ScriptEntityRef, const std::string&) override { return false; }
    bool stopAnimation(sle::scripting::ScriptEntityRef) override { return false; }
    bool pauseAnimation(sle::scripting::ScriptEntityRef) override { return false; }
    bool resumeAnimation(sle::scripting::ScriptEntityRef) override { return false; }
    bool setAnimationSpeed(sle::scripting::ScriptEntityRef, float) override { return false; }
    bool setAnimationTime(sle::scripting::ScriptEntityRef, float) override { return false; }
    bool isAnimationPlaying(sle::scripting::ScriptEntityRef) const override { return false; }
    float getAnimationTime(sle::scripting::ScriptEntityRef) const override { return 0.0f; }
    bool setAnimationTarget(sle::scripting::ScriptEntityRef, const std::string&, sle::scripting::ScriptEntityRef) override { return false; }
    bool setAnimatorFloat(sle::scripting::ScriptEntityRef, const std::string&, float) override { return false; }
    bool getAnimatorFloat(sle::scripting::ScriptEntityRef, const std::string&, float&) const override { return false; }
    bool setUIBinding(const std::string&, const std::string&) override { return false; }
};

} // namespace

int main()
{
    DummyScriptApi api;
    sle::scripting::ScriptEngine scriptEngine;
    if (!scriptEngine.init(&api))
    {
        std::cerr << "ScriptEngine failed to initialize\n";
        return 1;
    }

    if (!scriptEngine.executeScriptAsset("tests/data/scripts/state_machine_guard.lua"))
    {
        std::cerr << "Failed to load state_machine_guard.lua\n";
        scriptEngine.shutdown();
        return 1;
    }

    sle::entity::Scene scene;
    auto& registry = scene.getRegistry();

    auto defAllow = std::make_shared<sle::components::StateMachineDefinition>();
    defAllow->initialState = "Idle";

    sle::components::StateDefinition allowIdle;
    allowIdle.name = "Idle";
    allowIdle.transitions.push_back({
        .toState = "Run",
        .type = sle::components::StateTransitionType::LuaGuard,
        .key = "",
        .expectedBool = true,
        .minTimeSeconds = 0.0f,
        .consumeTrigger = true,
        .luaGuardFunction = "canTransitionToRun",
    });

    sle::components::StateDefinition allowRun;
    allowRun.name = "Run";

    defAllow->states.emplace(allowIdle.name, allowIdle);
    defAllow->states.emplace(allowRun.name, allowRun);

    auto defBlock = std::make_shared<sle::components::StateMachineDefinition>();
    defBlock->initialState = "Idle";

    sle::components::StateDefinition blockIdle;
    blockIdle.name = "Idle";
    blockIdle.transitions.push_back({
        .toState = "Run",
        .type = sle::components::StateTransitionType::LuaGuard,
        .key = "",
        .expectedBool = true,
        .minTimeSeconds = 0.0f,
        .consumeTrigger = true,
        .luaGuardFunction = "cannotTransition",
    });

    sle::components::StateDefinition blockRun;
    blockRun.name = "Run";

    defBlock->states.emplace(blockIdle.name, blockIdle);
    defBlock->states.emplace(blockRun.name, blockRun);

    const sle::entity::Entity allowEntity = scene.createEntity();
    auto& allowSm = registry.addComponent<sle::components::StateMachineComponent>(allowEntity);
    allowSm.definition = defAllow;

    const sle::entity::Entity blockEntity = scene.createEntity();
    auto& blockSm = registry.addComponent<sle::components::StateMachineComponent>(blockEntity);
    blockSm.definition = defBlock;

    sle::StateMachineSystem system;

    system.update(scene, 1.0f / 60.0f, &scriptEngine);
    if (!allowSm.initialized || allowSm.currentState != "Idle" ||
        !blockSm.initialized || blockSm.currentState != "Idle")
    {
        std::cerr << "State machines should initialize to Idle\n";
        scriptEngine.shutdown();
        return 1;
    }

    system.update(scene, 1.0f / 60.0f, &scriptEngine);

    if (allowSm.currentState != "Run")
    {
        std::cerr << "Expected Lua guard true transition Idle -> Run\n";
        scriptEngine.shutdown();
        return 1;
    }

    if (blockSm.currentState != "Idle")
    {
        std::cerr << "Expected Lua guard false transition to be blocked\n";
        scriptEngine.shutdown();
        return 1;
    }

    scriptEngine.shutdown();
    return 0;
}
