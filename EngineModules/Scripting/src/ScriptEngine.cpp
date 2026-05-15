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

void ScriptEngine::shutdown()
{
    std::vector<uint32_t> ids;
    ids.reserve(instances.size());
    for (const auto& [entityId, _] : instances)
        ids.push_back(entityId);

    for (uint32_t id : ids)
        removeScript(id);

    if (L)
    {
        lua_close(L);
        L = nullptr;
    }

    api = nullptr;
}

bool ScriptEngine::ensureScript(uint32_t entityId, const std::string& scriptAsset)
{
    if (!L || scriptAsset.empty())
        return false;

    if (hasScript(entityId))
        return true;

    auto scriptResource = sle::core::Resources::get<ScriptResource>(scriptAsset);
    if (!scriptResource)
        scriptResource = sle::core::Resources::create<ScriptResource>(scriptAsset, scriptAsset);

    if (!scriptResource)
    {
        sle::core::Log::error("Failed to resolve script resource: {}", scriptAsset);
        return false;
    }

    const std::string& scriptSource = scriptResource->getSource();
    if (luaL_loadbuffer(L, scriptSource.c_str(), scriptSource.size(), scriptAsset.c_str()) != LUA_OK)
    {
        sle::core::Log::error("Lua load error [{}]: {}", scriptAsset, lua_tostring(L, -1));
        lua_pop(L, 1);
        return false;
    }

    if (lua_pcall(L, 0, 1, 0) != LUA_OK)
    {
        sle::core::Log::error("Lua runtime error [{}]: {}", scriptAsset, lua_tostring(L, -1));
        lua_pop(L, 1);
        return false;
    }

    ScriptInstance instance;
    instance.assetPath = scriptAsset;

    if (lua_istable(L, -1))
    {
        lua_pushvalue(L, -1);
        instance.tableRef = luaL_ref(L, LUA_REGISTRYINDEX);

        instance.initRef = extractFunctionRef(-1, "init");
        instance.updateRef = extractFunctionRef(-1, "update");
        instance.destroyRef = extractFunctionRef(-1, "destroy");
        lua_pop(L, 1);
    }
    else
    {
        // Allow scripts that define global init/update/destroy without returning a table.
        lua_pop(L, 1);

        lua_getglobal(L, "init");
        instance.initRef = lua_isfunction(L, -1) ? luaL_ref(L, LUA_REGISTRYINDEX) : (lua_pop(L, 1), kLuaNoRef);

        lua_getglobal(L, "update");
        instance.updateRef = lua_isfunction(L, -1) ? luaL_ref(L, LUA_REGISTRYINDEX) : (lua_pop(L, 1), kLuaNoRef);

        lua_getglobal(L, "destroy");
        instance.destroyRef = lua_isfunction(L, -1) ? luaL_ref(L, LUA_REGISTRYINDEX) : (lua_pop(L, 1), kLuaNoRef);
    }

    instances[entityId] = instance;

    if (instance.initRef != kLuaNoRef)
        return callFunction(instance.initRef, entityId);

    return true;
}

void ScriptEngine::runUpdate(uint32_t entityId, float dt)
{
    auto it = instances.find(entityId);
    if (it == instances.end())
        return;

    if (it->second.updateRef != kLuaNoRef)
        (void)callFunction(it->second.updateRef, entityId, dt);
}

void ScriptEngine::removeScript(uint32_t entityId)
{
    if (!L)
        return;

    auto it = instances.find(entityId);
    if (it == instances.end())
        return;

    ScriptInstance& instance = it->second;

    if (instance.destroyRef != kLuaNoRef)
        (void)callFunction(instance.destroyRef, entityId);

    if (instance.initRef != kLuaNoRef)
        luaL_unref(L, LUA_REGISTRYINDEX, instance.initRef);
    if (instance.updateRef != kLuaNoRef)
        luaL_unref(L, LUA_REGISTRYINDEX, instance.updateRef);
    if (instance.destroyRef != kLuaNoRef)
        luaL_unref(L, LUA_REGISTRYINDEX, instance.destroyRef);
    if (instance.tableRef != kLuaNoRef)
        luaL_unref(L, LUA_REGISTRYINDEX, instance.tableRef);

    instances.erase(it);
}

bool ScriptEngine::hasScript(uint32_t entityId) const
{
    return instances.find(entityId) != instances.end();
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
}

void ScriptEngine::update(float)
{
}

int ScriptEngine::extractFunctionRef(int tableIndex, const char* functionName)
{
    lua_getfield(L, tableIndex, functionName);
    if (!lua_isfunction(L, -1))
    {
        lua_pop(L, 1);
        return kLuaNoRef;
    }

    return luaL_ref(L, LUA_REGISTRYINDEX);
}

bool ScriptEngine::callFunction(int ref, uint32_t entityId, float dt)
{
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
