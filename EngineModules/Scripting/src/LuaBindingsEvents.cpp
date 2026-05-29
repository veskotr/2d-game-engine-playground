#include "LuaBindingsInternal.hpp"
#include "LuaBindingsCommon.hpp"

extern "C" {
#include <lua.h>
#include <lauxlib.h>
}

namespace sle::scripting {

namespace {

// Engine.Events.subscribe(eventName, callback)
// Returns a subscription handle for later unsubscribe
int l_subscribe(lua_State* L)
{
    const char* eventName = luaL_checkstring(L, 1);
    if (!lua_isfunction(L, 2))
    {
        lua_pushnil(L);
        return 1;
    }

    // Copy callback to top before luaL_ref so argument stack remains valid.
    lua_pushvalue(L, 2);
    int luaRef = luaL_ref(L, LUA_REGISTRYINDEX);
    
    // Subscribe through ScriptApi
    // For now, we use entity ID 0 as a placeholder for global event subscriptions
    // In a more complete implementation, this would track the entity doing the subscription
    int subId = detail::getApi(L)->subscribeEvent(eventName, 0, luaRef);
    
    lua_pushinteger(L, subId);
    return 1;
}

// Engine.Events.emit(eventName, payload?, sourceEntity?)
int l_emit(lua_State* L)
{
    const char* eventName = luaL_checkstring(L, 1);
    const char* payload = lua_isnoneornil(L, 2) ? "" : luaL_checkstring(L, 2);
    const uint32_t sourceEntity = lua_isnoneornil(L, 3)
        ? 0
        : static_cast<uint32_t>(luaL_checkinteger(L, 3));

    lua_pushboolean(L, detail::getApi(L)->emitEvent(eventName, sourceEntity, payload));
    return 1;
}

// Engine.Events.unsubscribe(handle)
// Unsubscribes from a previously subscribed event
int l_unsubscribe(lua_State* L)
{
    int subId = static_cast<int>(luaL_checkinteger(L, 1));
    detail::getApi(L)->unsubscribeEvent(subId);
    return 0;
}

} // namespace

void registerEventsTable(lua_State* L, int engineTable, ScriptApi* api)
{
    lua_newtable(L);
    const int eventsTable = lua_gettop(L);
    
    detail::setTableFunction(L, eventsTable, api, "subscribe", l_subscribe);
    detail::setTableFunction(L, eventsTable, api, "unsubscribe", l_unsubscribe);
    detail::setTableFunction(L, eventsTable, api, "emit", l_emit);
    
    lua_setfield(L, engineTable, "Events");
}

} // namespace sle::scripting
