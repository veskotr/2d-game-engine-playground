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

    // Store the Lua function in the registry and get its reference
    int luaRef = luaL_ref(L, LUA_REGISTRYINDEX);
    
    // Subscribe through ScriptApi
    // For now, we use entity ID 0 as a placeholder for global event subscriptions
    // In a more complete implementation, this would track the entity doing the subscription
    int subId = detail::getApi(L)->subscribeEvent(eventName, 0, luaRef);
    
    lua_pushinteger(L, subId);
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
    
    lua_setfield(L, engineTable, "Events");
}

} // namespace sle::scripting
