#include <sle/scripting/ScriptApi.hpp>
#include <sle/scripting/ScriptEngine.hpp>

#include <glm/vec2.hpp>

#include <cmath>
#include <cstdint>
#include <iostream>
#include <string>
#include <vector>

namespace {

class DummyScriptApi final : public sle::scripting::ScriptApi {
public:
    float getDeltaTime() const override { return 0.016f; }
    glm::vec2 getWindowSize() const override { return {320.0f, 180.0f}; }

    sle::scripting::ScriptEntityRef createEntity() override { return {nextEntityId++}; }
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
    bool getStateMachineCurrentState(sle::scripting::ScriptEntityRef, std::string& outState) const override { outState = {}; return false; }
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
    bool emitEvent(const std::string&, uint32_t, const std::string&) override { return false; }

    // Animator spy implementations
    bool playAnimation(sle::scripting::ScriptEntityRef e, const std::string& asset) override
    {
        playCalledEntity = e.id;
        playCalledAsset = asset;
        return true;
    }
    bool stopAnimation(sle::scripting::ScriptEntityRef e) override
    {
        stopCalledEntity = e.id;
        return true;
    }
    bool pauseAnimation(sle::scripting::ScriptEntityRef e) override
    {
        pauseCalledEntity = e.id;
        return true;
    }
    bool resumeAnimation(sle::scripting::ScriptEntityRef e) override
    {
        resumeCalledEntity = e.id;
        return true;
    }
    bool setAnimationSpeed(sle::scripting::ScriptEntityRef e, float s) override
    {
        speedEntity = e.id;
        speedValue = s;
        return true;
    }
    bool setAnimationTime(sle::scripting::ScriptEntityRef e, float t) override
    {
        timeSetEntity = e.id;
        timeSetValue = t;
        return true;
    }
    bool isAnimationPlaying(sle::scripting::ScriptEntityRef) const override { return true; }
    float getAnimationTime(sle::scripting::ScriptEntityRef) const override { return 0.5f; }
    bool setAnimationTarget(sle::scripting::ScriptEntityRef e, const std::string& name, sle::scripting::ScriptEntityRef target) override
    {
        targetEntity = e.id;
        targetName = name;
        targetId = target.id;
        return true;
    }
    bool setAnimatorFloat(sle::scripting::ScriptEntityRef e, const std::string& name, float value) override
    {
        animatorFloatEntity = e.id;
        animatorFloatName = name;
        animatorFloatValue = value;
        return true;
    }
    bool getAnimatorFloat(sle::scripting::ScriptEntityRef, const std::string& name, float& outValue) const override
    {
        if (name != "speedBlend")
            return false;
        outValue = 0.75f;
        return true;
    }
    bool setUIBinding(const std::string&, const std::string&) override { return true; }

    // Spy state
    uint32_t playCalledEntity = 0;
    std::string playCalledAsset;
    uint32_t stopCalledEntity = 0;
    uint32_t pauseCalledEntity = 0;
    uint32_t resumeCalledEntity = 0;
    uint32_t speedEntity = 0;
    float speedValue = 0.0f;
    uint32_t timeSetEntity = 0;
    float timeSetValue = 0.0f;
    uint32_t targetEntity = 0;
    std::string targetName;
    uint32_t targetId = 0;
    uint32_t animatorFloatEntity = 0;
    std::string animatorFloatName;
    float animatorFloatValue = 0.0f;

    std::vector<std::string> logs;

private:
    uint32_t nextEntityId = 1;
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

    if (!engine.executeScriptAsset("tests/data/scripts/animator_api_surface.lua"))
    {
        std::cerr << "Failed to load animator_api_surface.lua\n";
        engine.shutdown();
        return 1;
    }

    // Verify playAnimation was called with the correct clip asset
    if (api.playCalledAsset != "assets/animations/walk.clip")
    {
        std::cerr << "playAnimation: expected asset 'assets/animations/walk.clip', got '" << api.playCalledAsset << "'\n";
        engine.shutdown();
        return 1;
    }

    // Verify speed was set to 2.0
    if (std::fabs(api.speedValue - 2.0f) > 0.001f)
    {
        std::cerr << "setAnimationSpeed: expected 2.0, got " << api.speedValue << "\n";
        engine.shutdown();
        return 1;
    }

    // Verify time was set to 0.5
    if (std::fabs(api.timeSetValue - 0.5f) > 0.001f)
    {
        std::cerr << "setAnimationTime: expected 0.5, got " << api.timeSetValue << "\n";
        engine.shutdown();
        return 1;
    }

    // Verify setAnimationTarget was called with "Weapon"
    if (api.targetName != "Weapon")
    {
        std::cerr << "setAnimationTarget: expected 'Weapon', got '" << api.targetName << "'\n";
        engine.shutdown();
        return 1;
    }

    if (api.animatorFloatName != "speedBlend" || std::fabs(api.animatorFloatValue - 0.75f) > 0.001f)
    {
        std::cerr << "setAnimatorFloat: expected speedBlend=0.75, got "
                  << api.animatorFloatName << "=" << api.animatorFloatValue << "\n";
        engine.shutdown();
        return 1;
    }

    // Verify log was written
    bool logFound = false;
    for (const auto& msg : api.logs)
    {
        if (msg == "animator_api_ok") { logFound = true; break; }
    }
    if (!logFound)
    {
        std::cerr << "Expected 'animator_api_ok' in logs\n";
        engine.shutdown();
        return 1;
    }

    engine.shutdown();
    return 0;
}
