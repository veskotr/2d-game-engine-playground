#pragma once

#include <cstdint>
#include <functional>
#include <sle/scripting/ScriptRuntime.hpp>
#include <string>
#include <unordered_map>
#include <unordered_set>

struct lua_State;

namespace sle::scripting {

class ScriptApi;

struct ScriptInstance
{
    std::string assetPath;
    int tableRef = -2; // LUA_REFNIL
    int initRef = -2;
    int updateRef = -2;
    int destroyRef = -2;
};

class ScriptEngine : public ScriptRuntime
{
public:
    bool init(ScriptApi* api);
    void shutdown();

    bool ensureScript(uint32_t entityId, const std::string& scriptAsset) override;
    bool executeScriptAsset(const std::string& scriptAsset) override;
    void runUpdate(uint32_t entityId, float dt) override;
    void removeScript(uint32_t entityId);
    bool hasScript(uint32_t entityId) const override;
    bool callEntityFunctionByName(uint32_t entityId, const std::string& functionName) override;
    void syncEntities(const std::unordered_set<uint32_t>& activeEntities) override;

    void update(float dt);
    bool callGlobalFunction(const std::string& functionName, uint32_t entityId = 0, const std::string& stringArg = {}) override;
    bool callGlobalBoolFunction(const std::string& functionName, uint32_t entityId = 0, const std::string& stringArg = {}) override;
    bool callEventCallback(
        int luaRef,
        const std::string& eventName,
        uint32_t entityA = 0,
        uint32_t entityB = 0,
        const std::string& zoneId = {},
        uint32_t sourceEntity = 0,
        const std::string& payload = {});

private:
    bool callFunction(int ref, uint32_t entityId, float dt = -1.0f);
    int extractFunctionRef(int tableIndex, const char* functionName);

    lua_State* L = nullptr;
    ScriptApi* api = nullptr;
    std::unordered_map<uint32_t, ScriptInstance> instances;
};

} // namespace sle::scripting
