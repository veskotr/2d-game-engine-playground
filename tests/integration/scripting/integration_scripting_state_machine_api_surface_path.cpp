#include <sle/scripting/ScriptApi.hpp>
#include <sle/scripting/ScriptEngine.hpp>

#include <glm/vec2.hpp>

#include <cstdint>
#include <iostream>
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
    bool getStateMachineCurrentState(sle::scripting::ScriptEntityRef entity, std::string& outState) const override
    {
        outState = "Run";
        lastCurrentStateEntity = entity.id;
        return true;
    }

    bool forceStateMachineState(sle::scripting::ScriptEntityRef entity, const std::string& stateName) override
    {
        forceStateCalled = true;
        forcedEntity = entity.id;
        forcedStateName = stateName;
        return true;
    }

    bool isStateMachineInState(sle::scripting::ScriptEntityRef entity, const std::string& stateName) const override
    {
        isStateCalled = true;
        isStateEntity = entity.id;
        isStateName = stateName;
        return stateName == "Run";
    }

    bool sendStateMachineEvent(sle::scripting::ScriptEntityRef entity, const std::string& eventName) override
    {
        sendEventCalled = true;
        sendEventEntity = entity.id;
        sendEventName = eventName;
        return true;
    }

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
    bool playSound(sle::scripting::ScriptEntityRef, const std::string&, bool) override { return false; }
    bool stopSound(sle::scripting::ScriptEntityRef) override { return false; }
    bool setSoundVolume(sle::scripting::ScriptEntityRef, float) override { return false; }
    bool setSoundPitch(sle::scripting::ScriptEntityRef, float) override { return false; }
    bool isSoundPlaying(sle::scripting::ScriptEntityRef) const override { return false; }
    bool setUIBinding(const std::string&, const std::string&) override { return false; }

    mutable bool isStateCalled = false;
    mutable uint32_t isStateEntity = 0;
    mutable std::string isStateName;
    mutable uint32_t lastCurrentStateEntity = 0;

    bool forceStateCalled = false;
    uint32_t forcedEntity = 0;
    std::string forcedStateName;

    bool sendEventCalled = false;
    uint32_t sendEventEntity = 0;
    std::string sendEventName;

    std::vector<std::string> logs;
};

} // namespace

int main()
{
    DummyScriptApi api;
    sle::scripting::ScriptEngine engine;

    if (!engine.init(&api))
    {
        std::cerr << "ScriptEngine failed to initialize\n";
        return 1;
    }

    if (!engine.executeScriptAsset("tests/data/scripts/state_machine_api_surface.lua"))
    {
        std::cerr << "Failed to execute state_machine_api_surface.lua\n";
        engine.shutdown();
        return 1;
    }

    if (!engine.callGlobalFunction("runStateMachineApi", 77))
    {
        std::cerr << "Failed to run runStateMachineApi callback\n";
        engine.shutdown();
        return 1;
    }

    if (!api.forceStateCalled || api.forcedEntity != 77 || api.forcedStateName != "Run")
    {
        std::cerr << "Engine.setState binding did not call ScriptApi correctly\n";
        engine.shutdown();
        return 1;
    }

    if (!api.isStateCalled || api.isStateEntity != 77 || api.isStateName != "Run")
    {
        std::cerr << "Engine.isState binding did not call ScriptApi correctly\n";
        engine.shutdown();
        return 1;
    }

    if (!api.sendEventCalled || api.sendEventEntity != 77 || api.sendEventName != "jump")
    {
        std::cerr << "Engine.sendStateEvent binding did not call ScriptApi correctly\n";
        engine.shutdown();
        return 1;
    }

    if (api.lastCurrentStateEntity != 77)
    {
        std::cerr << "Engine.getState binding did not call ScriptApi correctly\n";
        engine.shutdown();
        return 1;
    }

    bool sawOkLog = false;
    for (const auto& msg : api.logs)
    {
        if (msg == "state_api_ok")
        {
            sawOkLog = true;
            break;
        }
    }

    if (!sawOkLog)
    {
        std::cerr << "Expected state_api_ok log marker from Lua script\n";
        engine.shutdown();
        return 1;
    }

    engine.shutdown();
    return 0;
}
