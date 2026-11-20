#include <sle/scripting/EditorLuaHost.hpp>

#include <lua.hpp>

#include <algorithm>
#include <array>
#include <cstring>
#include <chrono>
#include <cmath>
#include <cstdlib>
#include <memory>
#include <stdexcept>
#include <utility>

namespace sle::scripting {
namespace {
using Json = nlohmann::json;
using Clock = std::chrono::steady_clock;
constexpr std::size_t memoryLimit = 8 * 1024 * 1024;
constexpr std::size_t sourceLimit = 256 * 1024;
constexpr std::size_t stringLimit = 4096;
constexpr int hookInterval = 1000;

struct Budget
{
    std::size_t bytes = 0;
    int instructions = 200000;
    Clock::time_point deadline = Clock::now() + std::chrono::milliseconds(100);
};

void* allocate(void* context, void* ptr, std::size_t oldSize, std::size_t newSize)
{
    auto& budget = *static_cast<Budget*>(context);
    if (!ptr) oldSize = 0;
    if (!newSize)
    {
        std::free(ptr);
        budget.bytes -= oldSize;
        return nullptr;
    }
    if (newSize > memoryLimit - (budget.bytes - oldSize)) return nullptr;
    auto* result = std::realloc(ptr, newSize);
    if (result) budget.bytes = budget.bytes - oldSize + newSize;
    return result;
}

void checkBudget(lua_State* state, lua_Debug*)
{
    void* context = nullptr;
    lua_getallocf(state, &context);
    auto& budget = *static_cast<Budget*>(context);
    budget.instructions -= hookInterval;
    if (budget.instructions <= 0 || Clock::now() >= budget.deadline)
        luaL_error(state, "Behavior declaration exceeded the evaluation budget");
}

// The string library also exposes its methods through the string metatable.
int openDiscoveryLibraries(lua_State* state)
{
    luaL_requiref(state, "_G", luaopen_base, 1);
    for (const auto* name : {"collectgarbage", "dofile", "getmetatable", "load", "loadfile",
             "pcall", "print", "rawequal", "rawget", "rawlen", "rawset", "setmetatable",
             "warn", "xpcall", "_VERSION"})
    {
        lua_pushnil(state);
        lua_setfield(state, -2, name);
    }
    lua_pop(state, 1);
    luaL_requiref(state, LUA_STRLIBNAME, luaopen_string, 1);
    for (const auto* name : {"dump", "find", "gmatch", "gsub", "match"})
    {
        lua_pushnil(state);
        lua_setfield(state, -2, name);
    }
    lua_pop(state, 1);
    luaL_requiref(state, LUA_TABLIBNAME, luaopen_table, 1);
    // table.move can loop over an enormous sparse range without allocations or hook calls.
    lua_pushnil(state);
    lua_setfield(state, -2, "move");
    lua_pop(state, 1);
    luaL_requiref(state, LUA_MATHLIBNAME, luaopen_math, 1);
    lua_pop(state, 1);
    luaL_requiref(state, LUA_UTF8LIBNAME, luaopen_utf8, 1);
    lua_pop(state, 1);
    return 0;
}

struct RestoreStack
{
    lua_State* state;
    int top;
    ~RestoreStack() { lua_settop(state, top); }
};

std::string getString(lua_State* state, int index)
{
    if (lua_type(state, index) != LUA_TSTRING) throw std::runtime_error("Expected a string");
    std::size_t size = 0;
    const auto* value = lua_tolstring(state, index, &size);
    if (size > stringLimit) throw std::runtime_error("Behavior property string is too long");
    std::string text(value, size);
    (void)Json(text).dump(); // JSON bridge and Lua must interpret the same UTF-8 text.
    return text;
}

// Lookup through iteration avoids allocating Lua strings outside a protected call.
void rawField(lua_State* state, int table, std::string_view name)
{
    table = lua_absindex(state, table);
    lua_pushnil(state);
    while (lua_next(state, table))
    {
        std::size_t size = 0;
        if (lua_type(state, -2) == LUA_TSTRING)
        {
            const auto* key = lua_tolstring(state, -2, &size);
            if (name == std::string_view(key, size))
            {
                lua_remove(state, -2);
                return;
            }
        }
        lua_pop(state, 1);
    }
    lua_pushnil(state);
}

Json decodeField(lua_State* state, const std::string& name, Json& defaults)
{
    const auto table = lua_gettop(state);
    if (name.empty() || !lua_istable(state, table))
        throw std::runtime_error("Expected a named behavior property declaration");
    rawField(state, table, "type");
    const auto type = getString(state, -1);
    lua_pop(state, 1);
    rawField(state, table, "default");
    if (type == "boolean" && lua_isboolean(state, -1))
        defaults[name] = lua_toboolean(state, -1) != 0;
    else if (type == "number" && lua_type(state, -1) == LUA_TNUMBER &&
             std::isfinite(lua_tonumber(state, -1)))
        defaults[name] = lua_tonumber(state, -1);
    else if (type == "string" && lua_type(state, -1) == LUA_TSTRING)
        defaults[name] = getString(state, -1);
    else
        throw std::runtime_error("Invalid type or default for behavior property " + name);
    lua_pop(state, 1);

    Json field = {{"path", Json::array({name})}, {"type", type}};
    rawField(state, table, "label");
    if (!lua_isnil(state, -1)) field["label"] = getString(state, -1);
    lua_pop(state, 1);
    rawField(state, table, "order");
    if (!lua_isnil(state, -1))
    {
        if (!lua_isinteger(state, -1) || !std::in_range<int>(lua_tointeger(state, -1)))
            throw std::runtime_error("Invalid order for behavior property " + name);
        field["order"] = static_cast<int>(lua_tointeger(state, -1));
    }
    lua_pop(state, 1);
    return field;
}
constexpr std::size_t consoleOutputLimit = 16 * 1024;

struct ConsoleRun
{
    const Json& snapshot;
    std::string_view mode;
    std::string_view commandId;
    std::array<char, consoleOutputLimit> output{};
    std::size_t size = 0;
};

void appendOutput(ConsoleRun& run, const char* text, std::size_t size)
{
    size = std::min(size, run.output.size() - run.size);
    std::memcpy(run.output.data() + run.size, text, size);
    run.size += size;
}

int consolePrint(lua_State* state)
{
    auto& run = *static_cast<ConsoleRun*>(lua_touserdata(state, lua_upvalueindex(1)));
    const auto count = lua_gettop(state);
    for (int i = 1; i <= count && run.size < run.output.size(); ++i)
    {
        std::size_t size = 0;
        const auto* text = luaL_tolstring(state, i, &size);
        if (i > 1) appendOutput(run, "\t", 1);
        appendOutput(run, text, size);
        lua_pop(state, 1);
    }
    appendOutput(run, "\n", 1);
    return 0;
}

void pushJson(lua_State* state, const Json& value, int depth)
{
    if (depth > 32 || !lua_checkstack(state, 4)) luaL_error(state, "Snapshot is too deeply nested");
    if (value.is_array() || value.is_object())
    {
        lua_newtable(state);
        lua_Integer index = 1;
        for (auto entry = value.begin(); entry != value.end(); ++entry)
        {
            if (value.is_array())
            {
                pushJson(state, *entry, depth + 1);
                lua_rawseti(state, -2, index++);
            }
            else
            {
                lua_pushlstring(state, entry.key().data(), entry.key().size());
                pushJson(state, *entry, depth + 1);
                lua_rawset(state, -3);
            }
        }
    }
    else if (value.is_string())
    {
        const auto& text = value.get_ref<const std::string&>();
        lua_pushlstring(state, text.data(), text.size());
    }
    else if (value.is_boolean()) lua_pushboolean(state, value.get<bool>());
    else if (value.is_number_integer()) lua_pushinteger(state, value.get<lua_Integer>());
    else if (value.is_number()) lua_pushnumber(state, value.get<lua_Number>());
    else lua_pushnil(state);
}

int openConsole(lua_State* state)
{
    auto* run = static_cast<ConsoleRun*>(lua_touserdata(state, 1));
    openDiscoveryLibraries(state);
    pushJson(state, run->snapshot, 0);
    lua_setglobal(state, "snapshot");
    if (!run->mode.empty())
    {
        lua_pushlstring(state, run->mode.data(), run->mode.size());
        lua_setglobal(state, "_editorMode");
        lua_pushlstring(state, run->commandId.empty() ? "" : run->commandId.data(), run->commandId.size());
        lua_setglobal(state, "_editorCommandId");
    }
    lua_pushlightuserdata(state, run);
    lua_pushcclosure(state, consolePrint, 1);
    lua_setglobal(state, "print");
    return 0;
}

// Loaded independently of project source so the chunk cannot replace the driver's helpers.
constexpr char commandDriver[] = R"lua(
    local chunk = ...
    local assert, type, pairs, error = assert, type, pairs, error
    local snapshot, mode, target = snapshot, _editorMode, _editorCommandId
    _editorMode, _editorCommandId = nil, nil

    local function validText(text, limit)
        return type(text) == "string" and #text > 0 and #text <= limit
    end

    local function validateList(list, name)
        assert(type(list) == "table" and #list <= 32, "Expected a list of up to 32 " .. name)
        for key in pairs(list) do
            assert(type(key) == "number" and key % 1 == 0 and key >= 1 and key <= #list,
                "Expected a dense list of " .. name)
        end
    end

    local declarations = chunk()
    assert(type(declarations) == "table", "Expected editor declarations")
    local commands = declarations
    local hasExtensions = declarations.commands ~= nil or declarations.drawers ~= nil or declarations.panels ~= nil
    if hasExtensions then
        for key in pairs(declarations) do
            assert(key == "commands" or key == "drawers" or key == "panels", "Unknown editor declaration group")
        end
        commands = declarations.commands
        if commands == nil then commands = {} end
    end
    validateList(commands, "editor commands")

    local seen, response, selected = {}, {}, nil
    for index = 1, #commands do
        local command = commands[index]
        assert(type(command) == "table", "Expected an editor command descriptor")
        local id, label, menu, shortcut = command.id, command.label, command.menu, command.shortcut
        assert(validText(id, 80) and validText(label, 120), "Expected a command id and label")
        assert(menu == nil or validText(menu, 120), "Invalid editor command menu")
        assert(shortcut == nil or validText(shortcut, 64), "Invalid editor command shortcut")
        assert(type(command.run) == "function", "Expected a command run function")
        assert(command.enabled == nil or type(command.enabled) == "function", "Expected an enabled function")
        assert(not seen[id], "Duplicate editor command id: " .. id)
        seen[id] = true
        if mode == "discover" then
            response[index] = {id = id, label = label, menu = menu, shortcut = shortcut,
                predicate = command.enabled ~= nil}
        elseif id == target then
            selected = command
        end
    end

    if mode == "discover" then
        local function append(list, kind)
            if list == nil then return end
            validateList(list, kind .. " declarations")
            for index = 1, #list do
                response[#response + 1] = {kind = kind, descriptor = list[index]}
            end
        end
        if hasExtensions then
            append(declarations.drawers, "drawer")
            append(declarations.panels, "panel")
        end
        return response
    end
    assert(selected, "Unknown editor command: " .. target)
    local enabled = true
    if selected.enabled then enabled = selected.enabled(snapshot) end
    assert(type(enabled) == "boolean", "The enabled callback must return a boolean")
    if mode == "enabled" then return {{enabled = enabled}} end
    assert(enabled, "Editor command is disabled")
    return selected.run(snapshot)
)lua";

Json readEditValue(lua_State* state, int index, int depth, std::size_t& remaining)
{
    if (remaining == 0 || depth > 24 || !lua_checkstack(state, 4))
        throw std::runtime_error("Lua console result exceeds the size limit");
    --remaining;
    index = lua_absindex(state, index);
    switch (lua_type(state, index))
    {
    case LUA_TBOOLEAN: return lua_toboolean(state, index) != 0;
    case LUA_TSTRING: return getString(state, index);
    case LUA_TNUMBER:
        if (lua_isinteger(state, index)) return lua_tointeger(state, index);
        if (std::isfinite(lua_tonumber(state, index))) return lua_tonumber(state, index);
        break;
    case LUA_TTABLE:
    {
        auto result = Json::object();
        const auto count = lua_rawlen(state, index);
        if (count > remaining) throw std::runtime_error("Lua console result exceeds the size limit");
        if (count > 0 || depth == 0) result = Json::array();
        if (result.is_array()) result.get_ref<Json::array_t&>().resize(count);
        std::size_t visited = 0;
        lua_pushnil(state);
        while (lua_next(state, index))
        {
            if (result.is_array())
            {
                const auto key = lua_tointeger(state, -2);
                if (!lua_isinteger(state, -2) || key < 1 || static_cast<std::size_t>(key) > count)
                    throw std::runtime_error("Expected a dense Lua array");
                result[static_cast<std::size_t>(key - 1)] = readEditValue(state, -1, depth + 1, remaining);
            }
            else
            {
                const auto key = getString(state, -2);
                result[key] = readEditValue(state, -1, depth + 1, remaining);
            }
            ++visited;
            lua_pop(state, 1);
        }
        if (result.is_array() && visited != count) throw std::runtime_error("Expected a dense Lua array");
        return result;
    }
    default: break;
    }
    throw std::runtime_error("Unsupported value in Lua console edits");
}

} // namespace

BehaviourSchema readBehaviourSchema(lua_State* state, int index)
{
    BehaviourSchema result;
    if (!lua_istable(state, index)) return result;
    index = lua_absindex(state, index);
    const RestoreStack stack{state, lua_gettop(state)};
    if (!lua_checkstack(state, 6)) return {.error = "Unable to read behavior properties"};
    try
    {
        rawField(state, index, "properties");
        if (lua_isnil(state, -1)) return result;
        if (!lua_istable(state, -1)) return {.error = "Expected a behavior properties table"};
        const auto table = lua_gettop(state);
        lua_pushnil(state);
        while (lua_next(state, table))
        {
            if (result.fields.size() == 64) return {.error = "Too many behavior properties"};
            result.fields.push_back(decodeField(state, getString(state, -2), result.defaults));
            lua_pop(state, 1);
        }
        std::sort(result.fields.begin(), result.fields.end(), [](const Json& a, const Json& b) {
            if (a.value("order", 0) != b.value("order", 0)) return a.value("order", 0) < b.value("order", 0);
            return a.at("path").at(0) < b.at("path").at(0);
        });
        return result;
    }
    catch (const std::exception& error)
    {
        return {.error = error.what()};
    }
}

BehaviourSchema inspectBehaviourSource(std::string_view source, std::string_view filename)
{
    if (source.size() > sourceLimit) return {.error = "Behavior source exceeds the size limit"};
    Budget budget;
    std::unique_ptr<lua_State, decltype(&lua_close)> state(lua_newstate(allocate, &budget), lua_close);
    if (!state) return {.error = "Unable to allocate the editor Lua state"};
    auto* lua = state.get();
    lua_pushcfunction(lua, openDiscoveryLibraries);
    const auto chunkName = "@" + std::string(filename.substr(0, stringLimit));
    if (lua_pcall(lua, 0, 0, 0) == LUA_OK &&
        luaL_loadbufferx(lua, source.data(), source.size(), chunkName.c_str(), "t") == LUA_OK)
    {
        lua_sethook(lua, checkBudget, LUA_MASKCOUNT, hookInterval);
        if (lua_pcall(lua, 0, 1, 0) == LUA_OK) return readBehaviourSchema(lua, -1);
    }
    if (lua_type(lua, -1) != LUA_TSTRING) return {.error = "Behavior discovery failed"};
    std::size_t length = 0;
    const auto* diagnostic = lua_tolstring(lua, -1, &length);
    return {.error = length == 0 ? "Behavior discovery failed" : std::string(diagnostic, std::min(length, stringLimit))};
}

static EditorLuaConsoleResult runEditorLua(std::string_view source, const Json& snapshot,
    const std::string& chunkName, std::string_view mode, std::string_view commandId)
{
    if (source.size() > sourceLimit) return {.error = "Lua console source exceeds the size limit"};
    Budget budget;
    ConsoleRun run{snapshot, mode, commandId};
    std::unique_ptr<lua_State, decltype(&lua_close)> owner(lua_newstate(allocate, &budget), lua_close);
    if (!owner) return {.error = "Unable to allocate the editor Lua state"};
    auto* state = owner.get();
    lua_pushcfunction(state, openConsole);
    lua_pushlightuserdata(state, &run);
    EditorLuaConsoleResult result;
    if (lua_pcall(state, 1, 0, 0) == LUA_OK &&
        (mode.empty() || luaL_loadbufferx(state, commandDriver, sizeof(commandDriver) - 1,
            "=editor command", "t") == LUA_OK) &&
        luaL_loadbufferx(state, source.data(), source.size(), chunkName.c_str(), "t") == LUA_OK)
    {
        lua_sethook(state, checkBudget, LUA_MASKCOUNT, hookInterval);
        if (lua_pcall(state, mode.empty() ? 0 : 1, 1, 0) == LUA_OK)
        {
            try
            {
                std::size_t remaining = 4096;
                if (!lua_isnil(state, -1))
                {
                    if (!lua_istable(state, -1)) throw std::runtime_error("Expected an edit list or nil");
                    result.edits = readEditValue(state, -1, 0, remaining);
                }
            }
            catch (const std::exception& error) { result.error = error.what(); }
        }
        else result.error = "Lua console execution failed";
    }
    else result.error = "Lua console could not load the command";
    if (!result.error.empty() && lua_type(state, -1) == LUA_TSTRING)
    {
        std::size_t size = 0;
        const auto* diagnostic = lua_tolstring(state, -1, &size);
        if (size > 0) result.error.assign(diagnostic, std::min(size, stringLimit));
    }
    result.output.assign(run.output.data(), run.size);
    return result;
}

EditorLuaConsoleResult runEditorLuaConsole(std::string_view source, const Json& snapshot)
{
    return runEditorLua(source, snapshot, "=Lua console", {}, {});
}

EditorLuaConsoleResult runEditorLuaCommands(std::string_view source, std::string_view filename,
    const Json& snapshot, std::string_view mode, std::string_view commandId)
{
    if (mode != "discover" && mode != "enabled" && mode != "run")
        return {.error = "Unknown editor command operation"};
    return runEditorLua(source, snapshot, "@" + std::string(filename.substr(0, stringLimit)), mode, commandId);
}

} // namespace sle::scripting
