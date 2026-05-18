#include "LuaBindingsInternal.hpp"
#include "LuaBindingsCommon.hpp"

namespace sle::scripting {

namespace {

int l_getCameraPosition(lua_State* L)
{
    const auto pos = detail::getApi(L)->getCameraPosition();
    detail::pushVec2(L, pos.x, pos.y);
    return 1;
}

int l_setCameraPosition(lua_State* L)
{
    const float x = static_cast<float>(luaL_checknumber(L, 1));
    const float y = static_cast<float>(luaL_checknumber(L, 2));
    detail::getApi(L)->setCameraPosition({x, y});
    return 0;
}

int l_moveCamera(lua_State* L)
{
    const float dx = static_cast<float>(luaL_checknumber(L, 1));
    const float dy = static_cast<float>(luaL_checknumber(L, 2));
    detail::getApi(L)->moveCamera({dx, dy});
    return 0;
}

int l_getCameraZoom(lua_State* L)
{
    lua_pushnumber(L, detail::getApi(L)->getCameraZoom());
    return 1;
}

int l_setCameraZoom(lua_State* L)
{
    const float zoom = static_cast<float>(luaL_checknumber(L, 1));
    detail::getApi(L)->setCameraZoom(zoom);
    return 0;
}

} // namespace

void registerCameraTable(lua_State* L, int engineTable, ScriptApi* api)
{
    lua_newtable(L);
    const int cameraTable = lua_gettop(L);
    detail::setTableFunction(L, cameraTable, api, "getPosition", l_getCameraPosition);
    detail::setTableFunction(L, cameraTable, api, "setPosition", l_setCameraPosition);
    detail::setTableFunction(L, cameraTable, api, "move", l_moveCamera);
    detail::setTableFunction(L, cameraTable, api, "getZoom", l_getCameraZoom);
    detail::setTableFunction(L, cameraTable, api, "setZoom", l_setCameraZoom);
    lua_setfield(L, engineTable, "Camera");
}

} // namespace sle::scripting
