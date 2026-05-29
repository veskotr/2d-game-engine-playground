#include <sle/events/CollisionEvents.hpp>
#include <sle/events/EventBus.hpp>
#include <sle/events/ScopedSubscription.hpp>
#include <sle/scripting/LuaBindings.hpp>
#include <sle/scripting/ScriptApi.hpp>

extern "C" {
#include <lua.h>
#include <lauxlib.h>
#include <lualib.h>
}

#include <glm/vec2.hpp>

#include <cstdint>
#include <filesystem>
#include <iostream>
#include <string>
#include <vector>

namespace {

class ScriptApiStub final : public sle::scripting::ScriptApi
{
public:
    float getDeltaTime() const override { return 0.0f; }
    glm::vec2 getWindowSize() const override { return {0.0f, 0.0f}; }

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

    int subscribeEvent(const std::string& eventName, uint32_t entityId, int luaRef) override
    {
        subscribedEventNames.push_back(eventName);
        subscribedEntityIds.push_back(entityId);
        subscribedLuaRefs.push_back(luaRef);
        return nextSubId++;
    }

    void unsubscribeEvent(int subscriptionId) override
    {
        unsubscribedIds.push_back(subscriptionId);
    }

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

    std::vector<std::string> subscribedEventNames;
    std::vector<uint32_t> subscribedEntityIds;
    std::vector<int> subscribedLuaRefs;
    std::vector<int> unsubscribedIds;

private:
    int nextSubId = 1;
};

bool check(bool condition, const std::string& message)
{
    if (!condition)
    {
        std::cerr << "[FAIL] " << message << "\n";
        return false;
    }

    std::cout << "[PASS] " << message << "\n";
    return true;
}

bool runScopedSubscriptionTest()
{
    sle::events::EventBus bus;
    int calls = 0;

    {
        auto handle = bus.subscribe<sle::events::CollisionBeginEvent>(
            [&calls](const sle::events::CollisionBeginEvent&) { ++calls; });

        sle::events::ScopedSubscription scoped(&bus, handle);
        bus.emit(sle::events::CollisionBeginEvent{});

        if (!check(calls == 1, "ScopedSubscription keeps handler active while alive"))
            return false;
    }

    bus.emit(sle::events::CollisionBeginEvent{});
    return check(calls == 1, "ScopedSubscription unsubscribes on destruction");
}

bool runLuaBindingsEventsTest()
{
    ScriptApiStub api;

    lua_State* L = luaL_newstate();
    if (!L)
        return check(false, "luaL_newstate succeeded");

    luaL_openlibs(L);
    sle::scripting::registerLuaBindings(L, &api);

    const std::vector<std::filesystem::path> scriptCandidates = {
        "assets/scripts/phase2_lua_events_sample.lua",
        "examples/phase2_lua_events/assets/scripts/phase2_lua_events_sample.lua",
        "../examples/phase2_lua_events/assets/scripts/phase2_lua_events_sample.lua",
        "../../examples/phase2_lua_events/assets/scripts/phase2_lua_events_sample.lua"
    };

    std::filesystem::path scriptPath;
    for (const auto& candidate : scriptCandidates)
    {
        if (std::filesystem::exists(candidate))
        {
            scriptPath = candidate;
            break;
        }
    }

    if (scriptPath.empty())
    {
        lua_close(L);
        return check(false, "Lua sample script is discoverable");
    }

    if (luaL_dofile(L, scriptPath.string().c_str()) != LUA_OK)
    {
        const char* err = lua_tostring(L, -1);
        std::cerr << "[FAIL] Lua execution error: " << (err ? err : "unknown") << "\n";
        lua_close(L);
        return false;
    }

    bool ok = true;
    ok = check(api.subscribedEventNames.size() == 1, "Engine.Events.subscribe forwards one valid subscription") && ok;

    if (!api.subscribedEventNames.empty())
    {
        ok = check(api.subscribedEventNames[0] == "collision.begin", "Engine.Events.subscribe forwards event name") && ok;
        ok = check(api.subscribedEntityIds[0] == 0, "Engine.Events.subscribe uses default global entity id") && ok;
    }

    ok = check(api.unsubscribedIds.size() == 1, "Engine.Events.unsubscribe forwards handle") && ok;

    lua_close(L);
    return ok;
}

} // namespace

int main()
{
    bool ok = true;
    ok = runScopedSubscriptionTest() && ok;
    ok = runLuaBindingsEventsTest() && ok;

    if (!ok)
    {
        std::cerr << "Phase 2 Lua/RAII sample failed\n";
        return 1;
    }

    std::cout << "Phase 2 Lua/RAII sample passed\n";
    return 0;
}
