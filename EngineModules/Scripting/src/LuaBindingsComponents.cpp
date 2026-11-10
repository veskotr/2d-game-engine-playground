#include "LuaBindingsInternal.hpp"
#include "LuaBindingsCommon.hpp"

namespace sle::scripting {
namespace {
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
