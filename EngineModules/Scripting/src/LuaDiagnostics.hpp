#pragma once

#include <sle/core/Log.hpp>
#include <algorithm>
#include <charconv>
#include <filesystem>
#include <string_view>

extern "C" {
#include <lua.h>
#include <lauxlib.h>
}

namespace sle::scripting::detail {

inline int captureLuaError(lua_State* L)
{
    lua_newtable(L);
    if (lua_tostring(L, 1)) lua_pushvalue(L, 1);
    else lua_pushfstring(L, "Lua raised a %s value", luaL_typename(L, 1));
    lua_setfield(L, -2, "message");

    luaL_traceback(L, L, nullptr, 1);
    lua_setfield(L, -2, "traceback");
    lua_Debug frame{};
    for (int depth = 1; lua_getstack(L, depth, &frame); ++depth)
    {
        if (lua_getinfo(L, "Sl", &frame) && frame.currentline > 0 && frame.source[0] == '@')
        {
            lua_pushlstring(L, frame.source + 1, frame.srclen - 1);
            lua_setfield(L, -2, "file");
            lua_pushinteger(L, frame.currentline);
            lua_setfield(L, -2, "line");
            break;
        }
    }
    return 1;
}

inline int protectedCall(lua_State* L, int arguments, int results)
{
    const int function = lua_gettop(L) - arguments;
    lua_pushcfunction(L, captureLuaError);
    lua_insert(L, function);
    const int status = lua_pcall(L, arguments, results, function);
    lua_remove(L, function);
    return status;
}

inline std::string absoluteLuaSource(const std::string& file)
{
    if (file.empty()) return {};
    // The embedded runtime can change its native cwd after the JVM has started.
    try
    {
        const std::filesystem::path source(std::u8string(file.begin(), file.end()));
        std::error_code error;
        const auto absolute = std::filesystem::absolute(source, error);
        if (!error)
        {
            const auto path = absolute.lexically_normal().generic_u8string();
            return {reinterpret_cast<const char*>(path.data()), path.size()};
        }
    }
    catch (const std::filesystem::filesystem_error&)
    {
        return {};
    }
    return file;
}

inline void logLuaError(lua_State* L, std::string_view context, uint32_t entity,
                        const std::string& behaviour, const std::string& loadPath = {})
{
    core::LuaDiagnostic diagnostic;
    diagnostic.entity = entity;
    diagnostic.behaviour = behaviour;
    std::string message;
    if (lua_istable(L, -1))
    {
        const auto read = [&](const char* key) {
            lua_getfield(L, -1, key);
            size_t size = 0;
            const char* value = lua_tolstring(L, -1, &size);
            std::string result = value ? std::string(value, size) : std::string{};
            lua_pop(L, 1);
            return result;
        };
        message = read("message");
        diagnostic.traceback = read("traceback");
        diagnostic.file = read("file");
        lua_getfield(L, -1, "line");
        diagnostic.line = static_cast<int>(lua_tointeger(L, -1));
        lua_pop(L, 1);
    }
    else
    {
        const char* value = lua_tostring(L, -1);
        message = value ? value : "Unknown Lua error";
        if (!loadPath.empty())
        {
            diagnostic.file = loadPath;
            // Lua's parser gives the line after its display name, which can truncate long paths.
            const auto start = std::min(loadPath.size(), size_t{LUA_IDSIZE - 1});
            const auto end = message.find(": ", start + 1);
            if (end != std::string::npos && message[start] == ':')
            {
                int line = 0;
                const auto parsed = std::from_chars(message.data() + start + 1, message.data() + end, line);
                if (parsed.ec == std::errc{} && parsed.ptr == message.data() + end)
                    diagnostic.line = line;
            }
        }
    }
    diagnostic.file = absoluteLuaSource(diagnostic.file);
    core::Log::luaError(fmt::format("{}: {}", context, message), std::move(diagnostic));
}

} // namespace sle::scripting::detail
