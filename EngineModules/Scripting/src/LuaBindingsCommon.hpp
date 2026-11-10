#pragma once

#include <sle/scripting/ScriptApi.hpp>
#include <cmath>
#include <limits>
#include <map>
#include <stdexcept>
#include <unordered_set>

extern "C" {
#include <lua.h>
#include <lauxlib.h>
}

namespace sle::scripting::detail {

inline uint32_t checkEntity(lua_State* L, int index)
{
    if (lua_type(L, index) != LUA_TNUMBER || !lua_isinteger(L, index))
        luaL_argerror(L, index, "expected integer entity ID");
    const auto value = lua_tointeger(L, index);
    if (value < 0 || static_cast<unsigned long long>(value) > std::numeric_limits<uint32_t>::max())
        luaL_argerror(L, index, "entity ID is outside uint32 range");
    return static_cast<uint32_t>(value);
}

inline float checkFiniteNumber(lua_State* L, int index)
{
    const auto value = luaL_checknumber(L, index);
    if (!std::isfinite(value) || std::abs(value) > std::numeric_limits<float>::max())
        luaL_argerror(L, index, "number must be finite and fit float");
    return static_cast<float>(value);
}

inline void pushJson(lua_State* L, const nlohmann::json& value)
{
    if (value.is_null()) lua_pushnil(L);
    else if (value.is_boolean()) lua_pushboolean(L, value.get<bool>());
    else if (value.is_number_integer()) lua_pushinteger(L, value.get<lua_Integer>());
    else if (value.is_number()) lua_pushnumber(L, value.get<lua_Number>());
    else if (value.is_string()) {
        const auto& text = value.get_ref<const std::string&>();
        lua_pushlstring(L, text.data(), text.size());
    } else if (value.is_array()) {
        lua_createtable(L, static_cast<int>(value.size()), 0);
        lua_Integer index = 1;
        for (const auto& item : value) { pushJson(L, item); lua_rawseti(L, -2, index++); }
    } else {
        lua_createtable(L, 0, static_cast<int>(value.size()));
        for (const auto& [key, item] : value.items()) { lua_pushlstring(L, key.data(), key.size()); pushJson(L, item); lua_rawset(L, -3); }
    }
}

inline nlohmann::json readJson(lua_State* L, int index,
    std::unordered_set<const void*>& visited, int depth = 0)
{
    if (depth > 32) throw std::invalid_argument("component data is too deeply nested");
    index = lua_absindex(L, index);
    switch (lua_type(L, index)) {
    case LUA_TNIL: return nullptr;
    case LUA_TBOOLEAN: return lua_toboolean(L, index) != 0;
    case LUA_TNUMBER:
        if (!std::isfinite(lua_tonumber(L, index))) throw std::invalid_argument("number must be finite");
        if (lua_isinteger(L, index)) return lua_tointeger(L, index);
        return lua_tonumber(L, index);
    case LUA_TSTRING: {
        size_t size = 0; const char* text = lua_tolstring(L, index, &size);
        return std::string(text, size);
    }
    case LUA_TTABLE: {
        const auto* identity = lua_topointer(L, index);
        if (!visited.insert(identity).second) throw std::invalid_argument("cyclic component data");
        struct StackRestore { lua_State* L; int top; ~StackRestore() { lua_settop(L, top); } } restore{L, lua_gettop(L)};
        auto object = nlohmann::json::object();
        std::map<lua_Integer, nlohmann::json> array;
        bool numeric = false, named = false;
        size_t count = 0;
        lua_pushnil(L);
        while (lua_next(L, index)) {
            if (++count > 10000) throw std::invalid_argument("component table is too large");
            auto item = readJson(L, -1, visited, depth + 1);
            if (lua_type(L, -2) == LUA_TSTRING) {
                named = true; size_t size = 0; const auto* key = lua_tolstring(L, -2, &size);
                object[std::string(key, size)] = std::move(item);
            } else if (lua_isinteger(L, -2) && lua_tointeger(L, -2) > 0) {
                numeric = true; array.emplace(lua_tointeger(L, -2), std::move(item));
            } else throw std::invalid_argument("table keys must be strings or positive integers");
            lua_pop(L, 1);
        }
        visited.erase(identity);
        if (numeric && named) throw std::invalid_argument("mixed array and object keys are unsupported");
        if (!numeric) return object;
        auto result = nlohmann::json::array(); lua_Integer expected = 1;
        for (auto& [key, item] : array) {
            if (key != expected++) throw std::invalid_argument("arrays must be dense and start at 1");
            result.push_back(std::move(item));
        }
        return result;
    }
    default: throw std::invalid_argument("unsupported Lua value in component data");
    }
}

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
