#include "LuaDiagnostics.hpp"
#include <nlohmann/json.hpp>
#include <sle/scripting/ScriptEngine.hpp>
#include <sle/core/Log.hpp>
#include <sle/resources/Resources.hpp>
#include <sle/scripting/LuaBindings.hpp>
#include <sle/scripting/ScriptApi.hpp>
#include <sle/scripting/ScriptResource.hpp>

#include <algorithm>
#include <cstring>
#include <vector>

extern "C" {
#include <lua.h>
#include <lauxlib.h>
#include <lualib.h>
}

namespace sle::scripting {

namespace {
constexpr int kLuaNoRef = -2; // LUA_REFNIL

int logConsolePrint(lua_State* state)
{
    char output[4096];
    std::size_t used = 0;
    const auto count = lua_gettop(state);
    for (int i = 1; i <= count && used < sizeof(output); ++i)
    {
        std::size_t size = 0;
        const auto* value = luaL_tolstring(state, i, &size);
        if (i > 1) output[used++] = '\t';
        size = std::min(size, sizeof(output) - used);
        std::memcpy(output + used, value, size);
        used += size;
        lua_pop(state, 1);
    }
    core::Log::info("{}", std::string_view(output, used));
    return 0;
}

int prepareConsoleChunk(lua_State* state)
{
    lua_newtable(state);
    lua_pushcfunction(state, logConsolePrint);
    lua_setfield(state, -2, "print");
    lua_newtable(state);
    lua_pushglobaltable(state);
    lua_setfield(state, -2, "__index");
    lua_pushglobaltable(state);
    lua_setfield(state, -2, "__newindex");
    lua_setmetatable(state, -2);
    lua_setupvalue(state, 1, 1);
    return 1;
}

void interruptConsole(lua_State* state, lua_Debug*)
{
    luaL_error(state, "Play Lua command exceeded the instruction limit");
}

class ExecutionScope
{
public:
    ExecutionScope(uint32_t& current, bool& source, std::string& key, uint64_t& generation,
                   uint32_t entity, bool script = false, const std::string& newKey = {}, uint64_t newGeneration = 0)
        : current(current), source(source), key(key), generation(generation),
          previous(current), previousSource(source), previousKey(key), previousGeneration(generation)
    { current = entity; source = script; key = newKey; generation = newGeneration; }
    ~ExecutionScope()
    { current = previous; source = previousSource; key = previousKey; generation = previousGeneration; }
private:
    uint32_t& current;
    bool& source;
    std::string& key;
    uint64_t& generation;
    uint32_t previous;
    bool previousSource;
    std::string previousKey;
    uint64_t previousGeneration;
};
}

bool ScriptEngine::init(ScriptApi* inApi)
{
    if (!inApi)
        return false;

    api = inApi;
    L = luaL_newstate();
    if (!L)
        return false;

    luaL_openlibs(L);
    initializeReloadEnvironment();
    registerLuaBindings(L, api);
    installProfileGc();

    return true;
}

void ScriptEngine::prependModulePaths(const std::vector<std::string>& roots)
{
    if (!L) return;
    lua_getglobal(L,"package");
    lua_getfield(L,-1,"path");
    std::string paths;
    for (const auto& root : roots) paths += root+"/?.lua;"+root+"/?/init.lua;";
    const char* previous=lua_tostring(L,-1);
    if (previous) paths += previous;
    lua_pop(L,1);
    lua_pushlstring(L,paths.data(),paths.size());
    lua_setfield(L,-2,"path");
    lua_pop(L,1);
}

void ScriptEngine::clearSceneScripts()
{
    std::vector<uint32_t> ids;
    ids.reserve(instances.size());
    for (const auto& [entityId, _] : instances)
        ids.push_back(entityId);
    for (uint32_t id : ids)
        removeScript(id);
    configurations.clear();
    if (!profileScope) clearProfileFrame();
    if (api)
        api->clearAllEventSubscriptions();
}

void ScriptEngine::shutdown()
{
    clearSceneScripts();
    reloadSources.clear();
    setProfiling(false);
    if (L)
    {
        lua_close(L);
        L = nullptr;
    }
    api = nullptr;
    executingEntity = 0;
    executingEntityScript = false;
    executingBehaviourKey.clear();
    executingBehaviourGeneration = 0;
}

void ScriptEngine::releaseEventCallback(int luaRef)
{
    if (L && luaRef >= 0)
        luaL_unref(L, LUA_REGISTRYINDEX, luaRef);
}

bool ScriptEngine::ensureScript(uint32_t entityId, const std::string& scriptAsset)
{
    return ensureBehaviour(entityId, {}, scriptAsset);
}

bool ScriptEngine::runConsoleSource(std::string_view source)
{
    if (!L || source.size() > 256 * 1024) return false;
    const int top = lua_gettop(L);
    ExecutionScope execution(executingEntity, executingEntityScript, executingBehaviourKey,
        executingBehaviourGeneration, 0);
    const int checkpoint = api ? api->eventSubscriptionCheckpoint() : 0;
    int status = luaL_loadbufferx(L, source.data(), source.size(), "=Play Lua console", "t");
    if (status == LUA_OK)
    {
        lua_pushcfunction(L, prepareConsoleChunk);
        lua_insert(L, -2);
        status = lua_pcall(L, 1, 1, 0);
    }
    if (status == LUA_OK)
    {
        const auto hook = lua_gethook(L);
        const auto mask = lua_gethookmask(L);
        const auto count = lua_gethookcount(L);
        lua_sethook(L, interruptConsole, LUA_MASKCOUNT, 200000);
        status = callLua(0, 0, ProfileCall::Chunk);
        lua_sethook(L, hook, mask, count);
    }
    if (status != LUA_OK)
    {
        detail::logLuaError(L, "Play Lua console error", 0, {});
        if (api) api->rollbackEventSubscriptions(0, checkpoint, false);
    }
    lua_settop(L, top);
    return status == LUA_OK;
}

bool ScriptEngine::executeScriptAsset(const std::string& scriptAsset)
{
    return executeScriptAssetForEntity(scriptAsset, 0);
}

bool ScriptEngine::executeScriptAssetForEntity(const std::string& scriptAsset, uint32_t entityId)
{
    ExecutionScope execution(executingEntity, executingEntityScript, executingBehaviourKey, executingBehaviourGeneration, entityId);
    if (!L || scriptAsset.empty())
        return false;

    auto scriptResource = sle::core::Resources::get<ScriptResource>(scriptAsset);
    if (!scriptResource)
        scriptResource = sle::core::Resources::create<ScriptResource>(scriptAsset, scriptAsset);

    if (!scriptResource)
    {
        core::Log::luaError(fmt::format("Failed to resolve UI script resource: {}", scriptAsset),
            {.entity = entityId});
        return false;
    }

    const int checkpoint = api ? api->eventSubscriptionCheckpoint() : 0;
    const std::string& scriptSource = scriptResource->getSource();
    if (luaL_loadbuffer(L, scriptSource.c_str(), scriptSource.size(), ("@" + scriptResource->getPath()).c_str()) != LUA_OK)
    {
        detail::logLuaError(L, fmt::format("Lua load error [{}]", scriptAsset), executingEntity, executingBehaviourKey, scriptResource->getPath());
        lua_pop(L, 1);
        if (api) api->rollbackEventSubscriptions(entityId, checkpoint, false);
        return false;
    }

    if (callLua(0, 0, ProfileCall::Chunk) != LUA_OK)
    {
        detail::logLuaError(L, fmt::format("Lua runtime error [{}]", scriptAsset), executingEntity, executingBehaviourKey);
        lua_pop(L, 1);
        if (api) api->rollbackEventSubscriptions(entityId, checkpoint, false);
        return false;
    }

    return true;
}

void ScriptEngine::runUpdate(uint32_t entityId, float dt)
{
    runBehaviourUpdate(entityId, {}, dt);
}

void ScriptEngine::removeScript(uint32_t entityId)
{
    const auto all = orderedBehaviours(entityId, true);
    for (const auto& [key, generation] : all)
        if (getBehaviourGeneration(entityId, key) == generation)
            removeBehaviour(entityId, key);
    configurations.erase(entityId);
    if (api) api->clearScriptEventSubscriptions(entityId);
}

bool ScriptEngine::hasScript(uint32_t entityId) const
{
    const auto it = instances.find(entityId);
    return it != instances.end() && !it->second.empty();
}

bool ScriptEngine::callEntityFunctionByName(uint32_t entityId, const std::string& functionName)
{
    return callEntityCallbacks(entityId, functionName, false);
}

bool ScriptEngine::callEntityBoolFunction(uint32_t entityId, const std::string& functionName)
{
    return callEntityCallbacks(entityId, functionName, true);
}

void ScriptEngine::syncEntities(const std::unordered_set<uint32_t>& activeEntities)
{
    std::vector<uint32_t> toRemove;
    toRemove.reserve(instances.size());

    for (const auto& [entityId, _] : instances)
    {
        if (!activeEntities.contains(entityId))
            toRemove.push_back(entityId);
    }

    for (uint32_t entityId : toRemove)
        removeScript(entityId);
    std::erase_if(configurations, [&](const auto& item) { return !activeEntities.contains(item.first); });
    if (api) api->pruneEventSubscriptions();
}

void ScriptEngine::update(float)
{
}

bool ScriptEngine::callGlobalFunction(const std::string& functionName, uint32_t entityId, const std::string& stringArg)
{
    return invokeGlobalFunction(functionName, entityId, stringArg) == GlobalCallResult::Succeeded;
}

ScriptEngine::GlobalCallResult ScriptEngine::invokeGlobalFunction(const std::string& functionName, uint32_t entityId, const std::string& stringArg)
{
    const bool inheritedScript = executingEntityScript && executingEntity == entityId;
    ExecutionScope execution(executingEntity, executingEntityScript, executingBehaviourKey, executingBehaviourGeneration,
        entityId, inheritedScript, inheritedScript ? executingBehaviourKey : std::string{},
        inheritedScript ? executingBehaviourGeneration : 0);
    if (!L || functionName.empty())
        return GlobalCallResult::Missing;

    lua_getglobal(L, functionName.c_str());
    if (!lua_isfunction(L, -1))
    {
        lua_pop(L, 1);
        return GlobalCallResult::Missing;
    }

    int argCount = 0;
    if (entityId != 0)
    {
        lua_pushinteger(L, entityId);
        ++argCount;
    }

    if (!stringArg.empty())
    {
        lua_pushstring(L, stringArg.c_str());
        ++argCount;
    }

    if (callLua(argCount, 0) != LUA_OK)
    {
        detail::logLuaError(L, fmt::format("Lua global callback error [{}]", functionName), executingEntity, executingBehaviourKey);
        lua_pop(L, 1);
        return GlobalCallResult::ErrorReported;
    }

    return GlobalCallResult::Succeeded;
}

bool ScriptEngine::callGlobalBoolFunction(const std::string& functionName, uint32_t entityId, const std::string& stringArg)
{
    const bool inheritedScript = executingEntityScript && executingEntity == entityId;
    ExecutionScope execution(executingEntity, executingEntityScript, executingBehaviourKey, executingBehaviourGeneration,
        entityId, inheritedScript, inheritedScript ? executingBehaviourKey : std::string{},
        inheritedScript ? executingBehaviourGeneration : 0);
    if (!L || functionName.empty())
        return false;

    lua_getglobal(L, functionName.c_str());
    if (!lua_isfunction(L, -1))
    {
        lua_pop(L, 1);
        return false;
    }

    int argCount = 0;
    if (entityId != 0)
    {
        lua_pushinteger(L, entityId);
        ++argCount;
    }

    if (!stringArg.empty())
    {
        lua_pushstring(L, stringArg.c_str());
        ++argCount;
    }

    if (callLua(argCount, 1) != LUA_OK)
    {
        detail::logLuaError(L, fmt::format("Lua global callback error [{}]", functionName), executingEntity, executingBehaviourKey);
        lua_pop(L, 1);
        return false;
    }

    if (!lua_isboolean(L, -1))
    {
        lua_pop(L, 1);
        return false;
    }

    const bool result = lua_toboolean(L, -1) != 0;
    lua_pop(L, 1);
    return result;
}

bool ScriptEngine::callEventCallback(
    int luaRef,
    const std::string& eventName,
    uint32_t entityA,
    uint32_t entityB,
    const std::string& zoneId,
    uint32_t sourceEntity,
    const std::string& payload, uint32_t ownerEntity, bool ownerIsScript,
    const std::string& ownerBehaviour, uint64_t ownerGeneration)
{
    ExecutionScope execution(executingEntity, executingEntityScript, executingBehaviourKey, executingBehaviourGeneration,
        ownerEntity, ownerIsScript, ownerBehaviour, ownerGeneration);
    if (!L || luaRef == kLuaNoRef)
        return false;
    if (ownerIsScript && ownerGeneration != 0 &&
        (getBehaviourGeneration(ownerEntity, ownerBehaviour) != ownerGeneration ||
         !behaviourEnabled(ownerEntity, ownerBehaviour)))
        return false;

    lua_rawgeti(L, LUA_REGISTRYINDEX, luaRef);
    if (!lua_isfunction(L, -1))
    {
        lua_pop(L, 1);
        return false;
    }

    lua_newtable(L);

    lua_pushstring(L, eventName.c_str());
    lua_setfield(L, -2, "name");

    if (entityA != 0)
    {
        lua_pushinteger(L, entityA);
        lua_setfield(L, -2, "entityA");
    }

    if (entityB != 0)
    {
        lua_pushinteger(L, entityB);
        lua_setfield(L, -2, "entityB");
    }

    if (!zoneId.empty())
    {
        lua_pushstring(L, zoneId.c_str());
        lua_setfield(L, -2, "zoneId");
    }

    if (sourceEntity != 0)
    {
        lua_pushinteger(L, sourceEntity);
        lua_setfield(L, -2, "sourceEntity");
    }

    if (!payload.empty())
    {
        lua_pushstring(L, payload.c_str());
        lua_setfield(L, -2, "payload");
    }

    if (callLua(1, 0) != LUA_OK)
    {
        detail::logLuaError(L, fmt::format("Lua event callback error [{}]", eventName), executingEntity, executingBehaviourKey);
        lua_pop(L, 1);
        return false;
    }

    return true;
}

int ScriptEngine::extractFunctionRef(int tableIndex, const char* functionName)
{
    tableIndex = lua_absindex(L, tableIndex);
    lua_pushstring(L, functionName);
    lua_rawget(L, tableIndex);
    if (!lua_isfunction(L, -1))
    {
        lua_pop(L, 1);
        return kLuaNoRef;
    }

    return luaL_ref(L, LUA_REGISTRYINDEX);
}

bool ScriptEngine::callFunction(int ref, uint32_t entityId, float dt, ProfileCall kind,
    const nlohmann::json* parameters)
{
    ExecutionScope execution(executingEntity, executingEntityScript, executingBehaviourKey, executingBehaviourGeneration,
        entityId, true, executingBehaviourKey, executingBehaviourGeneration);
    if (!L || ref == kLuaNoRef)
        return false;

    lua_rawgeti(L, LUA_REGISTRYINDEX, ref);
    if (!lua_isfunction(L, -1))
    {
        lua_pop(L, 1);
        return false;
    }

    lua_pushinteger(L, entityId);
    int argCount = 1;

    if (dt >= 0.0f)
    {
        lua_pushnumber(L, dt);
        argCount = 2;
    }

    if (parameters)
    {
        lua_createtable(L, 0, static_cast<int>(parameters->size()));
        for (const auto& [name, value] : parameters->items())
        {
            lua_pushlstring(L, name.data(), name.size());
            if (value.is_boolean()) lua_pushboolean(L, value.get<bool>());
            else if (value.is_number()) lua_pushnumber(L, value.get<double>());
            else
            {
                const auto& text = value.get_ref<const std::string&>();
                lua_pushlstring(L, text.data(), text.size());
            }
            lua_rawset(L, -3);
        }
        ++argCount;
    }

    if (callLua(argCount, 0, kind) != LUA_OK)
    {
        detail::logLuaError(L, "Lua callback error", executingEntity, executingBehaviourKey);
        lua_pop(L, 1);
        return false;
    }

    return true;
}

} // namespace sle::scripting
