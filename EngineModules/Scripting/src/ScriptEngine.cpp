#include <sle/scripting/ScriptEngine.hpp>
#include <sle/core/Log.hpp>
#include <sle/resources/Resources.hpp>
#include <sle/scripting/LuaBindings.hpp>
#include <sle/scripting/ScriptApi.hpp>
#include <sle/scripting/ScriptResource.hpp>

#include <vector>

extern "C" {
#include <lua.h>
#include <lauxlib.h>
#include <lualib.h>
}

namespace sle::scripting {

namespace {
constexpr int kLuaNoRef = -2; // LUA_REFNIL

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
    registerLuaBindings(L, api);

    return true;
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
    if (api)
        api->clearAllEventSubscriptions();
}

void ScriptEngine::shutdown()
{
    clearSceneScripts();
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
        sle::core::Log::error("Failed to resolve UI script resource: {}", scriptAsset);
        return false;
    }

    const int checkpoint = api ? api->eventSubscriptionCheckpoint() : 0;
    const std::string& scriptSource = scriptResource->getSource();
    if (luaL_loadbuffer(L, scriptSource.c_str(), scriptSource.size(), scriptAsset.c_str()) != LUA_OK)
    {
        sle::core::Log::error("Lua load error [{}]: {}", scriptAsset, lua_tostring(L, -1));
        lua_pop(L, 1);
        if (api) api->rollbackEventSubscriptions(entityId, checkpoint, false);
        return false;
    }

    if (lua_pcall(L, 0, 0, 0) != LUA_OK)
    {
        sle::core::Log::error("Lua runtime error [{}]: {}", scriptAsset, lua_tostring(L, -1));
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

    if (lua_pcall(L, argCount, 0, 0) != LUA_OK)
    {
        sle::core::Log::error("Lua global callback error [{}]: {}", functionName, lua_tostring(L, -1));
        lua_pop(L, 1);
        return false;
    }

    return true;
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

    if (lua_pcall(L, argCount, 1, 0) != LUA_OK)
    {
        sle::core::Log::error("Lua global callback error [{}]: {}", functionName, lua_tostring(L, -1));
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

    if (lua_pcall(L, 1, 0, 0) != LUA_OK)
    {
        sle::core::Log::error("Lua event callback error [{}]: {}", eventName, lua_tostring(L, -1));
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

bool ScriptEngine::callFunction(int ref, uint32_t entityId, float dt)
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

    if (lua_pcall(L, argCount, 0, 0) != LUA_OK)
    {
        sle::core::Log::error("Lua callback error: {}", lua_tostring(L, -1));
        lua_pop(L, 1);
        return false;
    }

    return true;
}

} // namespace sle::scripting
