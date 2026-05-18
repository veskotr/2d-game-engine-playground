#include "LuaBindingsInternal.hpp"
#include "LuaBindingsCommon.hpp"

namespace sle::scripting {

namespace {

int l_isKeyDown(lua_State* L)
{
    const int key = static_cast<int>(luaL_checkinteger(L, 1));
    lua_pushboolean(L, detail::getApi(L)->isKeyDown(key));
    return 1;
}

int l_isKeyPressed(lua_State* L)
{
    const int key = static_cast<int>(luaL_checkinteger(L, 1));
    lua_pushboolean(L, detail::getApi(L)->isKeyPressed(key));
    return 1;
}

int l_isKeyReleased(lua_State* L)
{
    const int key = static_cast<int>(luaL_checkinteger(L, 1));
    lua_pushboolean(L, detail::getApi(L)->isKeyReleased(key));
    return 1;
}

int l_getMousePosition(lua_State* L)
{
    const auto pos = detail::getApi(L)->getMousePosition();
    detail::pushVec2(L, static_cast<float>(pos.x), static_cast<float>(pos.y));
    return 1;
}

} // namespace

void registerInputTable(lua_State* L, int engineTable, ScriptApi* api)
{
    lua_newtable(L);
    const int inputTable = lua_gettop(L);
    detail::setTableFunction(L, inputTable, api, "isKeyDown", l_isKeyDown);
    detail::setTableFunction(L, inputTable, api, "isKeyPressed", l_isKeyPressed);
    detail::setTableFunction(L, inputTable, api, "isKeyReleased", l_isKeyReleased);
    detail::setTableFunction(L, inputTable, api, "getMousePosition", l_getMousePosition);
    lua_setfield(L, engineTable, "Input");
}

} // namespace sle::scripting
