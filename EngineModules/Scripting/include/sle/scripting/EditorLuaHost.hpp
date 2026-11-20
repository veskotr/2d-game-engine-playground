#pragma once

#include <nlohmann/json.hpp>
#include <string>
#include <string_view>

struct lua_State;

namespace sle::scripting {

struct BehaviourSchema
{
    nlohmann::json fields = nlohmann::json::array();
    nlohmann::json defaults = nlohmann::json::object();
    std::string error;
};

struct EditorLuaConsoleResult
{
    nlohmann::json edits = nlohmann::json::array();
    std::string output;
    std::string error;
};

EditorLuaConsoleResult runEditorLuaConsole(std::string_view source, const nlohmann::json& snapshot);

// Discover descriptors, evaluate enabled predicates, or run one project command.
EditorLuaConsoleResult runEditorLuaCommands(std::string_view source, std::string_view filename,
    const nlohmann::json& snapshot, std::string_view mode, std::string_view commandId);

// Reads a behavior's returned value and leaves the Lua stack unchanged.
BehaviourSchema readBehaviourSchema(lua_State* state, int index);
BehaviourSchema inspectBehaviourSource(std::string_view source, std::string_view filename);

} // namespace sle::scripting
