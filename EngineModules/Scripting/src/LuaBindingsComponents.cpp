#include "LuaBindingsInternal.hpp"
#include "LuaBindingsCommon.hpp"

namespace sle::scripting {
namespace {
int l_getSceneEntity(lua_State* L)
{
    const auto entity=detail::getApi(L)->getSceneEntity();
    if(entity.id) lua_pushinteger(L,entity.id); else lua_pushnil(L);
    return 1;
}
int l_getBehaviourKey(lua_State* L)
{
    const auto key=detail::getApi(L)->getExecutingBehaviourKey();
    if(key.empty()) lua_pushnil(L); else lua_pushlstring(L,key.data(),key.size());
    return 1;
}
int l_instantiatePrefab(lua_State* L)
{
    const auto* path=luaL_checkstring(L,1);
    const auto* key=luaL_checkstring(L,2);
    const auto parent=lua_isnoneornil(L,4)?0:detail::checkEntity(L,4);
    try {
        std::unordered_set<const void*> visited;
        const auto overrides=lua_isnoneornil(L,3)?nlohmann::json::object():detail::readJson(L,3,visited);
        std::string error;
        const auto entity=detail::getApi(L)->instantiatePrefab(path,key,overrides,{parent},error);
        if(entity.id) {lua_pushinteger(L,entity.id);return 1;}
        lua_pushnil(L); lua_pushlstring(L,error.data(),error.size()); return 2;
    } catch(const std::exception& ex) {lua_pushnil(L);lua_pushstring(L,ex.what());return 2;}
}
int l_findEntityByKey(lua_State* L)
{
    luaL_checktype(L, 1, LUA_TSTRING);
    std::size_t length = 0;
    const char* key = lua_tolstring(L, 1, &length);
    const auto entity = detail::getApi(L)->findEntityByKey(std::string(key, length));
    if (entity.id == 0) lua_pushnil(L);
    else lua_pushinteger(L, entity.id);
    return 1;
}
int l_registerScene(lua_State* L)
{
    const char* name = luaL_checkstring(L, 1);
    std::string builder;
    if (lua_isfunction(L, 2)) {
        // Factories live for the VM lifetime, independently of scene-owned events.
        builder = std::string("__sle_scene_factory_") + name;
        lua_pushvalue(L, 2);
        lua_setglobal(L, builder.c_str());
    } else {
        builder = luaL_checkstring(L, 2);
    }
    lua_pushboolean(L, detail::getApi(L)->registerScene(name, builder));
    return 1;
}
int l_registerSceneFromFile(lua_State* L)
{
    const char* name = luaL_checkstring(L, 1);
    const char* path = luaL_checkstring(L, 2);
    lua_pushboolean(L, detail::getApi(L)->registerSceneFromFile(name, path));
    return 1;
}
int l_hasComponent(lua_State* L)
{
    const auto id = detail::checkEntity(L, 1);
    const auto* name = luaL_checkstring(L, 2);
    lua_pushboolean(L, detail::getApi(L)->hasComponent({id}, name));
    return 1;
}
int l_getComponent(lua_State* L)
{
    const auto id = detail::checkEntity(L, 1);
    const auto* name = luaL_checkstring(L, 2);
    detail::pushJson(L, detail::getApi(L)->getComponent({id}, name));
    return 1;
}
int l_setComponent(lua_State* L)
{
    const auto id = detail::checkEntity(L, 1);
    const auto* name = luaL_checkstring(L, 2);
    try {
        std::unordered_set<const void*> visited;
        const auto patch = detail::readJson(L, 3, visited);
        std::string error;
        if (detail::getApi(L)->setComponent({id}, name, patch, error)) {
            lua_pushboolean(L, true);
            return 1;
        }
        lua_pushboolean(L, false);
        lua_pushlstring(L, error.data(), error.size());
        return 2;
    } catch (const std::exception& ex) {
        lua_pushboolean(L, false);
        lua_pushstring(L, ex.what());
        return 2;
    }
}
int l_removeComponent(lua_State* L)
{
    const auto id = detail::checkEntity(L, 1);
    const auto* name = luaL_checkstring(L, 2);
    std::string error;
    if (detail::getApi(L)->removeComponent({id}, name, error)) {
        lua_pushboolean(L, true);
        return 1;
    }
    lua_pushboolean(L, false);
    lua_pushlstring(L, error.data(), error.size());
    return 2;
}
int l_findComponents(lua_State* L)
{
    const auto* name = luaL_checkstring(L, 1);
    const auto entities = detail::getApi(L)->findEntitiesWithComponent(name);
    lua_createtable(L, static_cast<int>(entities.size()), 0);
    lua_Integer index = 1;
    for (auto entity : entities) { lua_pushinteger(L, entity); lua_rawseti(L, -2, index++); }
    return 1;
}
} // namespace

void registerComponentsTable(lua_State* L, int engineTable, ScriptApi* api)
{
    detail::setEngineFunction(L, engineTable, api, "getSceneEntity", l_getSceneEntity);
    detail::setEngineFunction(L, engineTable, api, "getBehaviourKey", l_getBehaviourKey);
    detail::setEngineFunction(L, engineTable, api, "instantiatePrefab", l_instantiatePrefab);
    detail::setEngineFunction(L, engineTable, api, "findEntityByKey", l_findEntityByKey);
    detail::setEngineFunction(L, engineTable, api, "registerScene", l_registerScene);
    detail::setEngineFunction(L, engineTable, api, "registerSceneFromFile", l_registerSceneFromFile);
    lua_newtable(L);
    const int componentsTable = lua_gettop(L);
    detail::setTableFunction(L, componentsTable, api, "has", l_hasComponent);
    detail::setTableFunction(L, componentsTable, api, "get", l_getComponent);
    detail::setTableFunction(L, componentsTable, api, "set", l_setComponent);
    detail::setTableFunction(L, componentsTable, api, "remove", l_removeComponent);
    detail::setTableFunction(L, componentsTable, api, "find", l_findComponents);
    lua_setfield(L, engineTable, "Components");
}
} // namespace sle::scripting
