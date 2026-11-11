#pragma once

#include <cstdint>
#include <functional>
#include <sle/scripting/ScriptRuntime.hpp>
#include <string>
#include <unordered_map>
#include <unordered_set>
#include <utility>

struct lua_State;

namespace sle::scripting {

class ScriptApi;

struct ScriptInstance
{
    std::string assetPath;
    uint64_t generation = 0;
    int envRef = -2;
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
    void clearSceneScripts();
    uint32_t getExecutingEntity() const { return executingEntity; }
    bool isExecutingEntityScript() const { return executingEntityScript; }
    const std::string& getExecutingBehaviourKey() const { return executingBehaviourKey; }
    uint64_t getExecutingBehaviourGeneration() const { return executingBehaviourGeneration; }
    void releaseEventCallback(int luaRef);
    void configureBehaviours(uint32_t entity, const std::vector<ScriptBehaviourSpec>& specs) override;
    bool ensureBehaviour(uint32_t entity, const std::string& key, const std::string& asset) override;
    bool hasBehaviour(uint32_t entity, const std::string& key) const override;
    void runBehaviourUpdate(uint32_t entity, const std::string& key, float dt) override;
    void removeBehaviour(uint32_t entity, const std::string& key) override;
    uint64_t getBehaviourGeneration(uint32_t entity, const std::string& key) const override;
    bool callEntityBoolFunction(uint32_t entity, const std::string& name) override;

    bool ensureScript(uint32_t entityId, const std::string& scriptAsset) override;
    bool executeScriptAsset(const std::string& scriptAsset) override;
    bool executeScriptAssetForEntity(const std::string& scriptAsset, uint32_t entityId) override;
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
        const std::string& payload = {}, uint32_t ownerEntity = 0, bool ownerIsScript = false,
        const std::string& ownerBehaviour = {}, uint64_t ownerGeneration = 0);

private:
    bool callFunction(int ref, uint32_t entityId, float dt = -1.0f);
    std::vector<std::pair<std::string, uint64_t>> orderedBehaviours(uint32_t entity, bool includeDisabled = false) const;
    int behaviourFunctionRef(const ScriptInstance& instance, const std::string& name);
    bool behaviourEnabled(uint32_t entity, const std::string& key) const;
    void releaseInstance(const ScriptInstance& instance);
    bool callEntityCallbacks(uint32_t entity, const std::string& name, bool boolean);
    int extractFunctionRef(int tableIndex, const char* functionName);

    lua_State* L = nullptr;
    ScriptApi* api = nullptr;
    uint32_t executingEntity = 0;
    bool executingEntityScript = false;
    std::string executingBehaviourKey;
    uint64_t executingBehaviourGeneration = 0;
    uint64_t nextGeneration = 0;
    std::unordered_map<uint32_t, std::unordered_map<std::string, ScriptInstance>> instances;
    std::unordered_map<uint32_t, std::vector<ScriptBehaviourSpec>> configurations;
};

} // namespace sle::scripting
