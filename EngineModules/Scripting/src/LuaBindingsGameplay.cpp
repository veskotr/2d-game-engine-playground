#include "LuaBindingsInternal.hpp"
#include "LuaBindingsCommon.hpp"

#include <limits>

namespace sle::scripting {
namespace {

int l_setTransformScale(lua_State* L)
{
    const auto id = detail::checkEntity(L, 1);
    const float x = detail::checkFiniteNumber(L, 2);
    const float y = detail::checkFiniteNumber(L, 3);
    lua_pushboolean(L, detail::getApi(L)->setTransformScale({id}, {x, y}));
    return 1;
}

int l_getTransformRotation(lua_State* L)
{
    const auto id = detail::checkEntity(L, 1);
    float rotation = 0;
    if (detail::getApi(L)->getTransformRotation({id}, rotation))
        lua_pushnumber(L, rotation);
    else
        lua_pushnil(L);
    return 1;
}

int l_setTransformRotation(lua_State* L)
{
    const auto id = detail::checkEntity(L, 1);
    const float rotation = detail::checkFiniteNumber(L, 2);
    lua_pushboolean(L, detail::getApi(L)->setTransformRotation({id}, rotation));
    return 1;
}

int l_getWorldTransform(lua_State* L)
{
    const auto id = detail::checkEntity(L, 1);
    detail::pushJson(L, detail::getApi(L)->getWorldTransform({id}));
    return 1;
}

int l_getChildren(lua_State* L)
{
    const auto id = detail::checkEntity(L, 1);
    const auto children = detail::getApi(L)->getChildren({id});
    lua_createtable(L, static_cast<int>(children.size()), 0);
    lua_Integer index = 1;
    for (const auto child : children)
    {
        lua_pushinteger(L, child);
        lua_rawseti(L, -2, index++);
    }
    return 1;
}

int l_getMouseDelta(lua_State* L)
{
    const auto delta = detail::getApi(L)->getMouseDelta();
    lua_newtable(L);
    lua_pushnumber(L, delta.x);
    lua_setfield(L, -2, "x");
    lua_pushnumber(L, delta.y);
    lua_setfield(L, -2, "y");
    return 1;
}

int checkMouseButton(lua_State* L)
{
    const auto button = luaL_checkinteger(L, 1);
    // Unsupported integers are safe false queries, including values outside int.
    if (button < std::numeric_limits<int>::min() || button > std::numeric_limits<int>::max())
        return -1;
    return static_cast<int>(button);
}

int l_isMouseButtonDown(lua_State* L)
{
    lua_pushboolean(L, detail::getApi(L)->isMouseButtonDown(checkMouseButton(L)));
    return 1;
}

int l_isMouseButtonPressed(lua_State* L)
{
    lua_pushboolean(L, detail::getApi(L)->isMouseButtonPressed(checkMouseButton(L)));
    return 1;
}

int l_isMouseButtonReleased(lua_State* L)
{
    lua_pushboolean(L, detail::getApi(L)->isMouseButtonReleased(checkMouseButton(L)));
    return 1;
}

int l_getGravityScale(lua_State* L)
{
    const auto id = detail::checkEntity(L, 1);
    float scale = 0;
    if (detail::getApi(L)->getGravityScale({id}, scale))
        lua_pushnumber(L, scale);
    else
        lua_pushnil(L);
    return 1;
}

} // namespace

void registerGameplayFunctions(lua_State* L, int engineTable, ScriptApi* api)
{
    detail::setEngineFunction(L, engineTable, api, "setTransformScale", l_setTransformScale);
    detail::setEngineFunction(L, engineTable, api, "getTransformRotation", l_getTransformRotation);
    detail::setEngineFunction(L, engineTable, api, "setTransformRotation", l_setTransformRotation);
    detail::setEngineFunction(L, engineTable, api, "getWorldTransform", l_getWorldTransform);
    detail::setEngineFunction(L, engineTable, api, "getChildren", l_getChildren);

    lua_getfield(L, engineTable, "Input");
    const int inputTable = lua_gettop(L);
    detail::setTableFunction(L, inputTable, api, "getMouseDelta", l_getMouseDelta);
    detail::setTableFunction(L, inputTable, api, "isMouseButtonDown", l_isMouseButtonDown);
    detail::setTableFunction(L, inputTable, api, "isMouseButtonPressed", l_isMouseButtonPressed);
    detail::setTableFunction(L, inputTable, api, "isMouseButtonReleased", l_isMouseButtonReleased);
    lua_pop(L, 1);

    lua_getfield(L, engineTable, "Physics");
    const int physicsTable = lua_gettop(L);
    detail::setTableFunction(L, physicsTable, api, "getGravityScale", l_getGravityScale);
    lua_pop(L, 1);
}

} // namespace sle::scripting
