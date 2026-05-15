#pragma once

struct lua_State;

namespace sle::scripting {

class ScriptApi;

void registerLuaBindings(lua_State* L, ScriptApi* api);

} // namespace sle::scripting
