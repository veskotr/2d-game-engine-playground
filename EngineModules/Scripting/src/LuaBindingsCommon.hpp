#pragma once

#include <sle/scripting/ScriptApi.hpp>

extern "C" {
#include <lua.h>
#include <lauxlib.h>
}

namespace sle::scripting::detail {

inline ScriptApi* getApi(lua_State* L)
{
    return static_cast<ScriptApi*>(lua_touserdata(L, lua_upvalueindex(1)));
}

inline void pushVec2(lua_State* L, float x, float y)
{
    lua_newtable(L);
    lua_pushnumber(L, x);
    lua_setfield(L, -2, "x");
    lua_pushnumber(L, y);
    lua_setfield(L, -2, "y");
}

inline void pushRaycastHit(lua_State* L, const PhysicsRaycastHit& hit)
{
    lua_newtable(L);

    lua_pushinteger(L, hit.entityId);
    lua_setfield(L, -2, "entityId");

    pushVec2(L, hit.point.x, hit.point.y);
    lua_setfield(L, -2, "point");

    pushVec2(L, hit.normal.x, hit.normal.y);
    lua_setfield(L, -2, "normal");

    lua_pushnumber(L, hit.fraction);
    lua_setfield(L, -2, "fraction");
}

inline void setEngineFunction(lua_State* L, int engineTable, ScriptApi* api, const char* name, lua_CFunction fn)
{
    lua_pushlightuserdata(L, api);
    lua_pushcclosure(L, fn, 1);
    lua_setfield(L, engineTable, name);
}

inline void setTableFunction(lua_State* L, int tableIndex, ScriptApi* api, const char* name, lua_CFunction fn)
{
    lua_pushlightuserdata(L, api);
    lua_pushcclosure(L, fn, 1);
    lua_setfield(L, tableIndex, name);
}

inline void setTableInt(lua_State* L, int tableIndex, const char* name, int value)
{
    lua_pushinteger(L, value);
    lua_setfield(L, tableIndex, name);
}

} // namespace sle::scripting::detail
