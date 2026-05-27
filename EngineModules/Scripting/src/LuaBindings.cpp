#include <sle/scripting/LuaBindings.hpp>
#include <sle/scripting/ScriptApi.hpp>
#include "LuaBindingsInternal.hpp"

extern "C" {
#include <lua.h>
}

namespace sle::scripting {

void registerLuaBindings(lua_State* L, ScriptApi* api)
{
    lua_newtable(L);
    const int engineTable = lua_gettop(L);

    registerEngineFunctions(L, engineTable, api);
    registerPhysicsTable(L, engineTable, api);
    registerInputTable(L, engineTable, api);
    registerCameraTable(L, engineTable, api);
    registerEventsTable(L, engineTable, api);
    registerConstantsTables(L, engineTable);

    lua_setglobal(L, "Engine");
}

} // namespace sle::scripting
