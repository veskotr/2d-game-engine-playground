#pragma once

struct lua_State;

namespace sle::scripting {

class ScriptApi;

void registerEngineFunctions(lua_State* L, int engineTable, ScriptApi* api);
void registerPhysicsTable(lua_State* L, int engineTable, ScriptApi* api);
void registerInputTable(lua_State* L, int engineTable, ScriptApi* api);
void registerCameraTable(lua_State* L, int engineTable, ScriptApi* api);
void registerEventsTable(lua_State* L, int engineTable, ScriptApi* api);
void registerConstantsTables(lua_State* L, int engineTable);

} // namespace sle::scripting
