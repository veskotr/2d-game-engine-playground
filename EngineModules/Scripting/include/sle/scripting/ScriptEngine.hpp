#pragma once

#include <array>
#include <nlohmann/json_fwd.hpp>
#include <cstddef>
#include <cstdint>
#include <map>
#include <functional>
#include <sle/scripting/ScriptRuntime.hpp>
#include <string>
#include <string_view>
#include <tuple>
#include <unordered_map>
#include <unordered_set>
#include <utility>

struct lua_State;

namespace sle::scripting {

class ScriptApi;

enum class BehaviourReloadResult { Restarted, StageFailed, InitFailed, AttachmentChanged };

struct ScriptInstance
{
    std::string assetPath;
    uint64_t generation = 0;
    int envRef = -2;
    int tableRef = -2; // LUA_REFNIL
    int initRef = -2;
    int updateRef = -2;
    int destroyRef = -2;
    bool privateReloadEnvironment = false;
};

struct LuaProfileRow
{
    uint32_t entity;
    std::string behaviour;
    std::string source;
    std::array<uint64_t, 4> calls{}; // init, update, callback, chunk
    std::array<uint64_t, 4> elapsedNs{};
    uint64_t allocatedBytes = 0;
    uint64_t freedBytes = 0;
};

struct LuaProfileFrame
{
    uint64_t elapsedNs = 0;
    uint64_t explicitGcNs = 0;
    uint64_t memoryBytes = 0;
    size_t omittedRows = 0;
    std::vector<LuaProfileRow> rows;
};

class ScriptEngine : public ScriptRuntime
{
public:
    bool init(ScriptApi* api);
    void shutdown();
    void setProfiling(bool enabled);
    bool isProfiling() const { return profileAllocator != nullptr; }
    LuaProfileFrame finishProfileFrame();
    bool hasProfileActivity() const { return profileElapsedNs || !profileRows.empty(); }
    // Host-owned search paths; set before executing project scripts.
    void prependModulePaths(const std::vector<std::string>& roots);
    void clearSceneScripts();
    uint32_t getExecutingEntity() const { return executingEntity; }
    bool isExecutingEntityScript() const { return executingEntityScript; }
    const std::string& getExecutingBehaviourKey() const { return executingBehaviourKey; }
    uint64_t getExecutingBehaviourGeneration() const { return executingBehaviourGeneration; }
    void releaseEventCallback(int luaRef);
    void configureBehaviours(uint32_t entity, const std::vector<ScriptBehaviourSpec>& specs) override;
    bool ensureBehaviour(uint32_t entity, const std::string& key, const std::string& asset) override;
    BehaviourReloadResult reloadBehaviour(uint32_t entity, const std::string& key,
        const std::string& asset, const std::string& source, const std::function<bool()>& stillAttached, const std::string& sourcePath = {});
    bool hasBehaviour(uint32_t entity, const std::string& key) const override;
    void runBehaviourUpdate(uint32_t entity, const std::string& key, float dt) override;
    void removeBehaviour(uint32_t entity, const std::string& key) override;
    uint64_t getBehaviourGeneration(uint32_t entity, const std::string& key) const override;
    bool callEntityBoolFunction(uint32_t entity, const std::string& name) override;

    bool ensureScript(uint32_t entityId, const std::string& scriptAsset) override;
    bool runConsoleSource(std::string_view source);
    bool executeScriptAsset(const std::string& scriptAsset) override;
    bool executeScriptAssetForEntity(const std::string& scriptAsset, uint32_t entityId) override;
    void runUpdate(uint32_t entityId, float dt) override;
    void removeScript(uint32_t entityId);
    bool hasScript(uint32_t entityId) const override;
    bool callEntityFunctionByName(uint32_t entityId, const std::string& functionName) override;
    void syncEntities(const std::unordered_set<uint32_t>& activeEntities) override;

    void update(float dt);
    bool callGlobalFunction(const std::string& functionName, uint32_t entityId = 0, const std::string& stringArg = {}) override;
    enum class GlobalCallResult { Missing, Succeeded, ErrorReported };
    GlobalCallResult invokeGlobalFunction(const std::string& functionName, uint32_t entityId = 0, const std::string& stringArg = {});
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
    enum class ProfileCall { Init, Update, Callback, Chunk };
    int callLua(int arguments, int results, ProfileCall kind = ProfileCall::Callback);
    bool callFunction(int ref, uint32_t entityId, float dt = -1.0f, ProfileCall kind = ProfileCall::Callback,
        const nlohmann::json* parameters = nullptr);
    void clearProfileFrame();
    void installProfileGc();
    static int profileGc(lua_State* state);
    int (*collectGarbage)(lua_State*) = nullptr;
    static void* allocateProfiled(void* user, void* pointer, size_t oldSize, size_t newSize);
    struct ProfileScope
    {
        uint64_t childNs = 0;
        uint64_t allocatedBytes = 0;
        uint64_t freedBytes = 0;
    };
    ProfileScope* profileScope = nullptr;
    void* (*profileAllocator)(void*, void*, size_t, size_t) = nullptr;
    void* profileAllocatorData = nullptr;
    uint64_t profileElapsedNs = 0;
    uint64_t profileGcNs = 0;
    std::map<std::tuple<uint32_t, std::string, uint64_t>, LuaProfileRow> profileRows;
    std::map<std::string, std::string, std::less<>> profileSourcePaths;
    std::vector<std::pair<std::string, uint64_t>> orderedBehaviours(uint32_t entity, bool includeDisabled = false) const;
    int behaviourFunctionRef(const ScriptInstance& instance, const std::string& name);
    bool behaviourEnabled(uint32_t entity, const std::string& key) const;
    void initializeReloadEnvironment();
    BehaviourReloadResult restartBehaviourFromSource(uint32_t entity, const std::string& key,
        const std::string& asset, const std::string& source, const std::function<bool()>& stillAttached,
        const std::string& sourcePath, bool rememberSource);
    void captureLifecycle(ScriptInstance& instance);
    bool initializeInstance(uint32_t entity, const std::string& key, const ScriptInstance& instance);
    void releaseInstance(const ScriptInstance& instance);
    bool callEntityCallbacks(uint32_t entity, const std::string& name, bool boolean);
    int extractFunctionRef(int tableIndex, const char* functionName);

    struct ReloadSource { std::string bytes; std::string path; };
    std::unordered_map<std::string, ReloadSource> reloadSources;
    int reloadGlobalsRef = -2;
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
